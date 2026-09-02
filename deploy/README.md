# deploy/

운영 서버(EC2 한 대)에 nginx · app · mysql 을 올리는 설정 묶음이다.
`.github/workflows/deploy.yml` 이 이 디렉터리를 S3 로 동기화하고, EC2 가 `/opt/passmate/` 로 받아 쓴다.

| 파일 | 하는 일 |
|---|---|
| `docker-compose.yml` | nginx · app · mysql 세 컨테이너. MySQL 포트는 밖으로 열지 않는다 |
| `nginx/passmate.conf` | `passmate.kr` → 정적 파일 / `api.passmate.kr` → app:8080. TLS · `/ws` Upgrade |
| `deploy.sh` | SSM → `.env` · `nginx/.htpasswd` → ECR pull → `compose up` → app 헬스체크 → nginx 확인 |
| `backup.sh` | `mysqldump` → S3 (`backups/`). systemd 타이머가 부른다 |

## EC2 최초 1회 준비

배포 파이프라인이 돌기 전에 **사람이 한 번** 해야 한다. Session Manager 로 붙어서 실행한다.

```bash
sudo mkdir -p /opt/passmate /var/www/web /var/www/certbot

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

## 롤백

이미지 태그가 커밋 SHA 라, 이전 SHA 로 다시 올리면 끝난다.

```bash
sudo /opt/passmate/deploy.sh <ECR_REPOSITORY> <이전_커밋_SHA>
```

## 인증서 갱신 후 nginx 반영

`certbot renew` 는 파일만 바꾼다. 컨테이너가 다시 읽게 해야 한다.

```bash
sudo certbot renew --deploy-hook \
  'cd /opt/passmate && docker compose exec -T nginx nginx -s reload'
```
