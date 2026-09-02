# deploy/

운영 서버(EC2 한 대)에 nginx · web · app · mysql 을 올리는 설정 묶음이다.
`.github/workflows/deploy.yml` 이 이 디렉터리를 S3 로 동기화하고, EC2 가 `/opt/passmate/` 로 받아 쓴다.

| 파일 | 하는 일 |
|---|---|
| `docker-compose.yml` | nginx · web · app · mysql 네 컨테이너. MySQL 포트는 밖으로 열지 않는다 |
| `nginx/passmate.conf` | `passmate.kr` → web:3000 / `api.passmate.kr` → app:8080. TLS · `/ws` Upgrade |
| `deploy.sh` | SSM → `.env` → ECR pull → `compose up` → app·web 헬스체크 → nginx 확인 |
| `backup.sh` | `mysqldump` → S3 (`backups/`). systemd 타이머가 부른다 |

## 레포가 둘, 서버는 하나

백엔드(`Passmate-Backend`)와 프론트(`Passmate-Frontend`)는 각자 배포하는데 같은 EC2 에 올라간다.
서로를 이전 버전으로 되돌리지 않는 근거는 **이미지 태그가 SSM 에 있다**는 것 하나다.

| SSM 파라미터 | 누가 쓰나 | 누가 읽나 |
|---|---|---|
| `/passmate/prod/APP_IMAGE_TAG` | 백엔드 CI | `deploy.sh` |
| `/passmate/prod/WEB_IMAGE_TAG` | 프론트 CI | `deploy.sh` |
| `/passmate/prod/ECR_REPOSITORY` | 사람(1회) | `deploy.sh` |
| `/passmate/prod/ECR_WEB_REPOSITORY` | 사람(1회) | `deploy.sh` |

각 CI 는 **자기 태그만** SSM 에 쓰고 `deploy.sh` 를 부른다. `deploy.sh` 는 네 값을 모두 다시 읽어
`compose up -d` 를 하므로, 이미지가 바뀌지 않은 쪽 컨테이너는 건드려지지 않는다.

그래서 **롤백도 양쪽이 같다** — SSM 의 태그를 이전 SHA 로 바꾸고 배포를 다시 돌리면 된다.

## EC2 최초 1회 준비

배포 파이프라인이 돌기 전에 **사람이 한 번** 해야 한다. Session Manager 로 붙어서 실행한다.

```bash
sudo mkdir -p /opt/passmate /var/www/certbot

# 인증서 — nginx 가 아직 없으니 standalone 이 가장 간단하다(80 포트를 certbot 이 직접 잡는다)
sudo dnf install -y certbot
sudo certbot certonly --standalone -d passmate.kr -d api.passmate.kr

# 갱신 타이머 확인 (AL2023 은 systemd 타이머가 함께 깔린다)
systemctl list-timers | grep certbot
```

인증서가 없으면 `deploy.sh` 가 시작 단계에서 멈춘다 — nginx 가 뜨지 못하기 때문이다.

## Swagger

`https://api.passmate.kr/swagger-ui.html` 은 **인증 없이 열려 있다.** 웹·앱 팀이 API 를
붙이는 동안 문서가 계속 필요하고, 이 서버의 데이터는 테스트용 방·문제뿐이라
접근 제한보다 연동 속도를 택했다(2026-09-02 결정).

`/v3/api-docs` 는 OpenAPI JSON 이라 앱 팀의 클라이언트 코드 생성에 그대로 쓸 수 있다.

**잠가야 할 상황이 오면**(실사용자 데이터가 들어가거나 스캐너 트래픽이 거슬리면)
nginx 에 아래 블록을 더한다. `location /` 보다 정규식이 먼저 매칭된다.

```nginx
location ~ ^/(swagger-ui|v3/api-docs) {
    auth_basic           "PassMate API Docs";
    auth_basic_user_file /etc/nginx/.htpasswd;
    proxy_pass http://app:8080;
    # ... location / 과 같은 proxy_set_header 들
}
```

계정 파일은 SSM 에 `SWAGGER_HTPASSWD`(`user:해시` 한 줄)를 넣고 `deploy.sh` 가
`nginx/.htpasswd` 로 쓰게 한다. ⚠️ 파일이 없으면 Docker 가 같은 이름의 디렉터리를
만들어 **nginx 자체가 못 뜨므로**, 값이 비어도 빈 파일은 반드시 만들어야 한다.

## 백업 자동화

⚠️ **AL2023 에는 cron 이 기본으로 없다**(`crontab: command not found`). systemd 타이머를 쓴다.

```bash
sudo tee /etc/systemd/system/passmate-backup.service >/dev/null <<'EOF'
[Unit]
Description=PassMate MySQL dump to S3
[Service]
Type=oneshot
ExecStart=/opt/passmate/backup.sh
EOF

sudo tee /etc/systemd/system/passmate-backup.timer >/dev/null <<'EOF'
[Unit]
Description=Run PassMate backup daily
[Timer]
OnCalendar=*-*-* 18:00:00 UTC
Persistent=true
[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now passmate-backup.timer
systemctl list-timers passmate-backup.timer
```

`Persistent=true` 라 인스턴스를 껐다 켠 사이에 지나간 시각이 있으면 부팅 후 한 번 실행한다.

## 메모리 — 스왑을 반드시 켠다

t4g.small 은 2GB 다. JVM 힙 640m + MySQL 버퍼 풀 256m + Next.js 노드까지 얹으면 여유가 얼마 없다.
스왑이 없으면 여유가 바닥나는 순간 커널이 컨테이너 하나를 죽인다(대개 제일 큰 JVM).

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 재부팅 후에도 유지
free -h
```

## 롤백

이미지 태그가 커밋 SHA 다. SSM 의 태그를 이전 SHA 로 바꾸고 배포를 다시 돌린다.

```bash
# 백엔드를 되돌린다 (프론트는 WEB_IMAGE_TAG)
aws ssm put-parameter --name /passmate/prod/APP_IMAGE_TAG \
  --value <이전_커밋_SHA> --type String --overwrite --region ap-northeast-2
sudo /opt/passmate/deploy.sh
```

SSM 을 못 고치는 상황이라면 인자로 한 번만 덮어쓸 수 있다(다음 배포에서 SSM 값으로 돌아간다).

```bash
sudo /opt/passmate/deploy.sh <ECR_REPOSITORY> <이전_커밋_SHA>
```

## 이미지가 쌓이는 것

배포마다 새 태그가 생기므로 정리하지 않으면 EC2 디스크와 ECR 양쪽에 계속 쌓인다.

- **EC2** — `deploy.sh` 끝에서 `docker image prune -af` 로 **지금 떠 있는 것만** 남긴다.
  ⚠️ `-a` 가 없으면 **태그 없는 이미지만** 지워서 아무 효과가 없다(우리 태그는 항상 새 SHA 라
  옛 이미지도 태그가 붙어 있다). 실행 중인 컨테이너가 쓰는 이미지는 `-a` 여도 지워지지 않는다.
  로컬에 옛 이미지를 남길 이유가 없다 — 롤백은 ECR 에서 다시 받는다.
- **ECR** — 여기가 롤백의 원본이다. 수명 주기 정책으로 최근 5개를 남긴다
  (리포지토리마다 따로 걸어야 한다: `passmate-backend` · `passmate-web`).
  1개만 남기면 롤백이 불가능해진다 — 옛 커밋을 다시 빌드해야 한다.
  ⚠️ 태그 없는 항목을 지우는 규칙은 넣지 않는다. buildx 가 만드는 태그 없는 매니페스트는
  태그 붙은 인덱스가 참조하는 **자식**이라, 지우면 태그는 남고 실체가 사라져 pull 이 깨진다.
  애초에 안 생기게 워크플로에서 `provenance: false` 를 쓴다.
- **S3 `backups/`** — 수명 주기 규칙으로 30일 뒤 삭제. `deploy/` 는 `--delete` 동기화라 쌓이지 않는다.

## 인증서 갱신 후 nginx 반영

`certbot renew` 는 파일만 바꾼다. 컨테이너가 다시 읽게 해야 한다.

```bash
sudo certbot renew --deploy-hook \
  'cd /opt/passmate && docker compose exec -T nginx nginx -s reload'
```
