#!/usr/bin/env bash
# EC2 에서 실행된다. GitHub Actions 가 SSM Run Command 로 호출한다.
#
#   sudo /opt/passmate/deploy.sh <ECR_REPOSITORY> <IMAGE_TAG>
#
# 하는 일: SSM 에서 시크릿을 받아 .env 를 만들고 → 이미지를 받아 올린 뒤 → 헬스체크로 판정한다.
# 실패하면 0 이 아닌 코드로 끝나 Actions 가 빨갛게 뜬다.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/passmate}"
REGION="${AWS_REGION:-ap-northeast-2}"
SSM_PATH="${SSM_PATH:-/passmate/prod}"

ECR_REPOSITORY="${1:?사용법: deploy.sh <ECR_REPOSITORY> <IMAGE_TAG>}"
IMAGE_TAG="${2:?사용법: deploy.sh <ECR_REPOSITORY> <IMAGE_TAG>}"

cd "$APP_DIR"

# ── 1. 인증서 확인 ──────────────────────────────────────────────
# nginx 는 인증서가 없으면 아예 뜨지 못한다. 배포를 시작하기 전에 막는다
if [ ! -s /etc/letsencrypt/live/passmate.kr/fullchain.pem ]; then
  echo "인증서가 없다. 먼저 발급한다:" >&2
  echo "  sudo certbot certonly --standalone -d passmate.kr -d api.passmate.kr" >&2
  exit 1
fi

# ── 2. SSM Parameter Store → .env ──────────────────────────────
# 평문 시크릿이 담기므로 파일 권한을 먼저 좁힌다
umask 077
: > .env.new
aws ssm get-parameters-by-path \
      --path "$SSM_PATH" --recursive --with-decryption \
      --region "$REGION" \
      --query 'Parameters[].[Name,Value]' --output text \
  | while IFS=$'\t' read -r name value; do
      # /passmate/prod/JWT_SECRET → JWT_SECRET
      printf '%s=%s\n' "${name##*/}" "$value" >> .env.new
    done

if [ ! -s .env.new ]; then
  echo "SSM $SSM_PATH 에서 파라미터를 하나도 못 읽었다 — 경로나 IAM 권한을 확인한다" >&2
  exit 1
fi

# compose 가 이미지 좌표를 치환할 때 쓴다
{
  echo "ECR_REPOSITORY=${ECR_REPOSITORY}"
  echo "IMAGE_TAG=${IMAGE_TAG}"
} >> .env.new
mv .env.new .env

# ── 3. ECR 로그인 → pull → up ──────────────────────────────────
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${ECR_REPOSITORY%%/*}"

docker compose pull
docker compose up -d --remove-orphans

# ── 4. 헬스체크 ────────────────────────────────────────────────
# app 컨테이너의 healthcheck 상태를 본다(compose 에 정의돼 있다).
# Flyway 마이그레이션까지 끝나야 UP 이라 start_period 를 넉넉히 잡아 뒀다
for _ in $(seq 1 40); do
  status="$(docker inspect --format='{{.State.Health.Status}}' passmate-app 2>/dev/null || echo starting)"
  case "$status" in
    healthy)
      echo "배포 성공 — $IMAGE_TAG"
      # 이전 태그 이미지가 쌓이면 30GB 를 금방 먹는다
      docker image prune -f >/dev/null
      exit 0
      ;;
    unhealthy)
      break
      ;;
  esac
  sleep 5
done

echo "헬스체크 실패 (상태: ${status:-unknown}). 최근 로그:" >&2
docker compose logs --tail=120 app >&2
exit 1
