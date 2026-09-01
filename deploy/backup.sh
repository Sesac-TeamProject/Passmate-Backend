#!/usr/bin/env bash
# MySQL 일일 백업 → S3.  cron 이 부른다.
#
#   sudo crontab -e
#   0 18 * * * /opt/passmate/backup.sh >> /var/log/passmate-backup.log 2>&1
#   (UTC 18:00 = KST 03:00)
#
# RDS 를 안 쓰기로 했으므로 백업은 **EC2 밖**에 있어야 의미가 있다.
# 30일 뒤 삭제는 S3 수명 주기 규칙(backups/ 접두사)이 처리한다.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/passmate}"
REGION="${AWS_REGION:-ap-northeast-2}"

cd "$APP_DIR"
# .env 에 DB_* · S3_BUCKET 이 들어 있다(deploy.sh 가 만든다)
set -a; . ./.env; set +a

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="/tmp/passmate-${STAMP}.sql.gz"

# --single-transaction: InnoDB 를 잠그지 않고 일관된 스냅샷을 뜬다(서비스 중단 없음)
docker compose exec -T mysql \
    mysqldump -u root -p"${DB_ROOT_PASSWORD}" \
      --single-transaction --quick --routines --triggers "${DB_NAME}" \
  | gzip > "$FILE"

# 빈 파일이 올라가면 백업이 있다고 착각하게 된다
if [ ! -s "$FILE" ]; then
  echo "덤프가 비었다 — 업로드하지 않는다" >&2
  rm -f "$FILE"
  exit 1
fi

aws s3 cp "$FILE" "s3://${S3_BUCKET}/backups/$(basename "$FILE")" --region "$REGION"
rm -f "$FILE"
echo "백업 완료: backups/$(basename "$FILE")"
