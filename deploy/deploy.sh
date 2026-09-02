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

# ── 2-b. Swagger Basic Auth 계정 파일 ──────────────────────────
# nginx 가 /swagger-ui · /v3/api-docs 를 가릴 때 쓴다.
# ⚠️ 이 파일이 없으면 Docker 가 같은 이름의 **디렉터리**를 만들고 nginx 가 뜨지 못한다.
#    Swagger 하나 때문에 사이트 전체가 죽으므로, 값이 없어도 빈 파일을 반드시 만든다
#    (빈 파일이면 Swagger 만 401 이 되고 나머지 경로는 정상 동작한다).
# 값은 SSM /passmate/prod/SWAGGER_HTPASSWD — `user:$apr1$...` 한 줄.
mkdir -p nginx
sed -n 's/^SWAGGER_HTPASSWD=//p' .env > nginx/.htpasswd
# nginx 워커(uid 101)가 요청마다 읽는다 — 600 이면 못 읽어 403 이 난다
chmod 644 nginx/.htpasswd

# ── 3. ECR 로그인 → pull → up ──────────────────────────────────
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${ECR_REPOSITORY%%/*}"

docker compose pull
docker compose up -d --remove-orphans

# ── 4. 헬스체크 ────────────────────────────────────────────────
# app 컨테이너의 healthcheck 상태를 본다(compose 에 정의돼 있다).
# Flyway 마이그레이션까지 끝나야 UP 이라 start_period 를 넉넉히 잡아 뒀다
healthy=false
for _ in $(seq 1 40); do
  status="$(docker inspect --format='{{.State.Health.Status}}' passmate-app 2>/dev/null || echo starting)"
  case "$status" in
    healthy)   healthy=true; break ;;
    unhealthy) break ;;
  esac
  sleep 5
done

if [ "$healthy" != true ]; then
  echo "앱 헬스체크 실패 (상태: ${status:-unknown}). 최근 로그:" >&2
  docker compose logs --tail=120 app >&2
  exit 1
fi

# ── 5. nginx 확인 ──────────────────────────────────────────────
# nginx 에는 healthcheck 가 없다. 설정이 한 줄만 틀려도 컨테이너가 못 뜨는데
# app 은 healthy 라 배포가 초록불로 끝난다 — 사이트 전체가 죽은 채로.
if [ "$(docker inspect -f '{{.State.Running}}' passmate-nginx 2>/dev/null)" != "true" ]; then
  echo "nginx 가 뜨지 않았다 — 설정 오류일 가능성이 높다. 최근 로그:" >&2
  docker compose logs --tail=60 nginx >&2
  exit 1
fi

# 밖에서 실제로 닿는지까지 본다 — TLS · server_name · 프록시 경로를 한 번에 검증한다.
# 자기 EIP 로는 되돌아오지 못할 수 있어 루프백에 Host 헤더를 실어 부른다(인증서는 -k 로 건너뛴다)
if ! curl -fsS -k -o /dev/null --max-time 10 \
       -H 'Host: api.passmate.kr' https://127.0.0.1/actuator/health; then
  echo "nginx 를 통한 요청이 실패했다. 최근 로그:" >&2
  docker compose logs --tail=60 nginx >&2
  exit 1
fi

echo "배포 성공 — $IMAGE_TAG"
# 이전 태그 이미지가 쌓이면 30GB 를 금방 먹는다
docker image prune -f >/dev/null
