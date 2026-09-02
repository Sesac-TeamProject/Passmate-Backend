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

## Swagger Basic Auth

`api.passmate.kr/swagger-ui.html` 은 웹·앱 팀 연동용으로 열어 두되, 인터넷에 그대로
노출하지 않는다(엔드포인트·요청 스키마가 전부 공개된다). nginx 가 Basic Auth 로 가린다.

계정은 SSM `/passmate/prod/SWAGGER_HTPASSWD` 에 **`user:해시` 한 줄**로 넣는다.
`deploy.sh` 가 매 배포마다 `nginx/.htpasswd` 로 써서 컨테이너에 마운트한다.

```bash
# 해시 생성 (평문 비밀번호는 히스토리에 남지 않게 read 로 받는다)
read -rs -p "Swagger 비밀번호: " PW; echo
docker run --rm httpd:2.4-alpine htpasswd -nbB passmate "$PW"
unset PW
```

**값이 비어 있어도 사이트는 정상 동작한다** — Swagger 만 401 이 된다.
`deploy.sh` 가 빈 파일이라도 반드시 만드는 이유는, 파일이 없으면 Docker 가 같은 이름의
디렉터리를 만들어 **nginx 자체가 뜨지 못하기** 때문이다.

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
