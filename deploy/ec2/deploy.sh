#!/usr/bin/env bash
# EC2 에서 실행된다: 새 이미지로 컨테이너를 교체하고, 헬스체크에 실패하면 직전 이미지로 되돌린다.
#
# 사용: bash deploy.sh <이미지>   (CD 의 deploy job 이 ssh 로 stdin 에 실어 실행한다)
#   예: bash deploy.sh ghcr.io/haahyo/donguri-server:sha-1a2b3c4
#
# - 운영 비밀값은 EC2 의 env 파일에서만 읽는다. GitHub 를 거치지 않는다.
# - 교체 중 짧은 다운타임이 있다 (단일 인스턴스, 같은 포트).
set -euo pipefail

IMAGE="${1:-}"
NAME="donguri"
ENV_FILE="/opt/donguri/.env"
HOST_PORT=80
HEALTH_URL="http://127.0.0.1:${HOST_PORT}/actuator/health"
HEALTH_TIMEOUT=120

if [ -z "$IMAGE" ]; then
  echo "사용: bash deploy.sh <이미지>" >&2
  exit 1
fi
if [ ! -f "$ENV_FILE" ]; then
  echo "DEPLOY ERROR: ${ENV_FILE} 가 없습니다. deploy/ec2/README.md 의 .env 준비 단계를 먼저 하세요." >&2
  exit 1
fi

docker pull "$IMAGE"
PREV="$(docker inspect --format '{{.Config.Image}}' "$NAME" 2>/dev/null || true)"

start() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  docker run -d --name "$NAME" --restart unless-stopped \
    --env-file "$ENV_FILE" -p "${HOST_PORT}:8080" "$1" >/dev/null
}

healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if curl -sf -o /dev/null --max-time 2 "$HEALTH_URL"; then return 0; fi
    if [ "$(docker inspect --format '{{.State.Running}}' "$NAME" 2>/dev/null)" != "true" ]; then return 1; fi
    sleep 2
  done
  return 1
}

echo "배포: ${PREV:-<없음>} -> ${IMAGE}"
start "$IMAGE"
if healthy; then
  echo "DEPLOY OK: ${IMAGE}"
  docker images "${IMAGE%:*}" --format '{{.Repository}}:{{.Tag}}' \
    | grep -vxF -e "$IMAGE" -e "${PREV:-$IMAGE}" \
    | xargs -r docker rmi >/dev/null 2>&1 || true
  exit 0
fi

echo "DEPLOY FAILED: ${IMAGE} 헬스체크 실패 — 컨테이너 로그 마지막 80줄:" >&2
docker logs --tail 80 "$NAME" >&2 || true
if [ -n "$PREV" ] && [ "$PREV" != "$IMAGE" ]; then
  echo "롤백: ${PREV}" >&2
  start "$PREV"
  if healthy; then
    echo "ROLLBACK OK: ${PREV}" >&2
  else
    echo "ROLLBACK FAILED: ${PREV} 도 헬스체크에 실패했습니다. EC2 에서 직접 확인하세요." >&2
  fi
else
  echo "직전 이미지가 없어 롤백하지 않습니다." >&2
fi
exit 1
