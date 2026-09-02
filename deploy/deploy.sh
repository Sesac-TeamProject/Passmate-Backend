#!/usr/bin/env bash
# EC2 에서 실행된다. GitHub Actions 가 SSM Run Command 로 호출한다.
#
#   sudo /opt/passmate/deploy.sh                      # SSM 에 적힌 태그 그대로 올린다
#   sudo /opt/passmate/deploy.sh <ECR_REPO> <TAG>     # 백엔드 태그만 임시로 덮어쓴다(수동 롤백)
#
# 하는 일: SSM 에서 설정을 받아 .env 를 만들고 → 이미지를 받아 올린 뒤 → 헬스체크로 판정한다.
# 실패하면 0 이 아닌 코드로 끝나 Actions 가 빨갛게 뜬다.
#
# ⚠️ 백엔드(app)와 프론트(web)는 레포가 다르고 각자 배포한다.
# 이 스크립트는 **둘 다** 올리는데, 상대의 태그를 SSM 에서 다시 읽어 쓰기 때문에
# 한쪽 배포가 다른 쪽을 이전 버전으로 되돌리지 않는다. 그래서 태그는 인자가 아니라 SSM 이 원본이다.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/passmate}"
REGION="${AWS_REGION:-ap-northeast-2}"
SSM_PATH="${SSM_PATH:-/passmate/prod}"

# 인자는 선택이다. 주면 SSM 값을 덮어쓴다(SSM 을 못 고치는 상황의 탈출구)
OVERRIDE_REPO="${1:-}"
OVERRIDE_TAG="${2:-}"

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

# 있으면 값을 갈고, 없으면 붙인다. 값에 / 가 들어가므로 sed 구분자는 | 를 쓴다
upsert() {
  if grep -q "^$1=" .env.new; then
    sed -i "s|^$1=.*|$1=$2|" .env.new
  else
    printf '%s=%s\n' "$1" "$2" >> .env.new
  fi
}
[ -n "$OVERRIDE_REPO" ] && upsert ECR_REPOSITORY "$OVERRIDE_REPO"
[ -n "$OVERRIDE_TAG" ]  && upsert APP_IMAGE_TAG  "$OVERRIDE_TAG"

# compose 가 이미지 좌표를 만들 때 쓴다. 비어 있으면 ":" 같은 이름으로 이상하게 실패하므로 먼저 막는다
missing=""
for key in ECR_REPOSITORY APP_IMAGE_TAG ECR_WEB_REPOSITORY WEB_IMAGE_TAG; do
  grep -q "^${key}=." .env.new || missing="$missing $key"
done
if [ -n "$missing" ]; then
  echo "다음 값이 SSM $SSM_PATH 에 없다:$missing" >&2
  echo "콘솔 → Systems Manager → 파라미터 스토어에서 추가한다" >&2
  exit 1
fi

mv .env.new .env

# ── 3. ECR 로그인 → pull → up ──────────────────────────────────
# app 과 web 은 리포지토리가 다르지만 레지스트리(계정)가 같아 로그인은 한 번이면 된다
REGISTRY="$(grep '^ECR_REPOSITORY=' .env | cut -d= -f2-)"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${REGISTRY%%/*}"

docker compose pull
docker compose up -d --remove-orphans

# ── 4. 컨테이너 헬스체크 ───────────────────────────────────────
# compose 에 정의된 healthcheck 상태를 본다.
# app 은 Flyway 마이그레이션까지 끝나야 UP 이라 start_period 를 넉넉히 잡아 뒀다
wait_healthy() {
  local name="$1" tries="$2" status=""
  for _ in $(seq 1 "$tries"); do
    status="$(docker inspect --format='{{.State.Health.Status}}' "$name" 2>/dev/null || echo starting)"
    case "$status" in
      healthy)   return 0 ;;
      unhealthy) break ;;
    esac
    sleep 5
  done
  echo "$name 헬스체크 실패 (상태: ${status:-unknown}). 최근 로그:" >&2
  docker compose logs --tail=120 "${name#passmate-}" >&2
  return 1
}

wait_healthy passmate-app 40
wait_healthy passmate-web 24

# ── 5. nginx 확인 ──────────────────────────────────────────────
# nginx 에는 healthcheck 가 없다. 설정이 한 줄만 틀려도 컨테이너가 못 뜨는데
# app 은 healthy 라 배포가 초록불로 끝난다 — 사이트 전체가 죽은 채로.
if [ "$(docker inspect -f '{{.State.Running}}' passmate-nginx 2>/dev/null)" != "true" ]; then
  echo "nginx 가 뜨지 않았다 — 설정 오류일 가능성이 높다. 최근 로그:" >&2
  docker compose logs --tail=60 nginx >&2
  exit 1
fi

# 밖에서 실제로 닿는지까지 본다 — TLS · server_name · 프록시 경로를 한 번에 검증한다.
# 자기 EIP 로는 되돌아오지 못할 수 있어 루프백에 Host 헤더를 실어 부른다(인증서는 -k 로 건너뛴다).
# 두 도메인을 모두 본다 — server_name 이 갈리므로 한쪽이 살아 있다고 다른 쪽이 산 게 아니다
for host_path in "api.passmate.kr /actuator/health" "passmate.kr /"; do
  set -- $host_path
  if ! curl -fsS -k -o /dev/null --max-time 15 -H "Host: $1" "https://127.0.0.1$2"; then
    echo "nginx 를 통한 https://$1$2 요청이 실패했다. 최근 로그:" >&2
    docker compose logs --tail=60 nginx >&2
    exit 1
  fi
done

echo "배포 성공 — app:$(grep '^APP_IMAGE_TAG=' .env | cut -d= -f2- | cut -c1-7) web:$(grep '^WEB_IMAGE_TAG=' .env | cut -d= -f2- | cut -c1-7)"

# 옛 이미지 정리 — 지금 떠 있는 것만 남긴다.
# ⚠️ `prune -f`(-a 없이)는 **태그 없는(dangling)** 이미지만 지운다. 우리 태그는 커밋 SHA라
# 배포할 때마다 새 태그가 생기고 옛 이미지는 태그를 단 채 남아 하나도 지워지지 않았다.
# -a 는 "컨테이너가 쓰지 않는 이미지" 전부가 대상이다. 실행 중인 컨테이너가 쓰는
# 이미지(app · web · mysql · nginx)는 -a 여도 지워지지 않으니 안전하다.
#
# 로컬에 옛 이미지를 남겨 둘 이유가 없다 — 롤백은 어차피 ECR 에서 다시 받는다.
docker image prune -af >/dev/null
