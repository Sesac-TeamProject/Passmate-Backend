# deploy/

운영 서버(EC2 한 대)에 nginx · app · mysql 을 올리는 설정 묶음이다.
`.github/workflows/deploy.yml` 이 이 디렉터리를 S3 로 동기화하고, EC2 가 `/opt/passmate/` 로 받아 쓴다.

| 파일 | 하는 일 |
|---|---|
| `docker-compose.yml` | nginx · app · mysql 세 컨테이너. MySQL 포트는 밖으로 열지 않는다 |
| `nginx/passmate.conf` | `passmate.kr` → 정적 파일 / `api.passmate.kr` → app:8080. TLS · `/ws` Upgrade |
| `deploy.sh` | SSM → `.env` → ECR pull → `compose up` → 헬스체크 |
| `backup.sh` | `mysqldump` → S3 (`backups/`). cron 이 부른다 |

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

## 백업 cron

```bash
sudo crontab -e
# UTC 18:00 = KST 03:00
0 18 * * * /opt/passmate/backup.sh >> /var/log/passmate-backup.log 2>&1
```

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
