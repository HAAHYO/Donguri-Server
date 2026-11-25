#!/usr/bin/env bash
# 스모크: 앱을 띄워 실제 HTTP 호출 결과를 출력하고 반드시 종료한다.
#
# 사용: bash .claude/scripts/smoke.sh [요청 파일]
#   요청 파일은 줄마다 `METHOD PATH [JSON 본문]` (빈 줄, #주석 무시). 예:
#     POST /acorns {"name":"a"}
#     GET /acorns/999
#
# - bootRun 을 쓰는 이유: Gradle toolchain(JDK 21)으로 실행된다. PATH 의 java 버전과 무관.
# - 종료는 이 실행에서만 노출한 actuator shutdown 으로 한다 (설정 파일에는 노출하지 않음).
# - Bash 도구 타임아웃은 300000ms 이상으로 호출할 것 (부팅 대기 최대 150초).
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}" || exit 1
PORT="${SMOKE_PORT:-18080}"
BASE="http://localhost:${PORT}"
LOG="_workspace/smoke.log"
mkdir -p _workspace

if curl -s -o /dev/null "${BASE}/actuator/health"; then
  echo "SMOKE ERROR: 포트 ${PORT} 가 이미 사용 중입니다. 이전 스모크 프로세스가 남아 있는지 확인하세요."
  exit 1
fi

./gradlew bootRun --console=plain -q --args="--server.port=${PORT} \
--management.endpoints.web.exposure.include=health,shutdown \
--management.endpoint.shutdown.access=unrestricted" >"$LOG" 2>&1 &
PID=$!

stop_app() {
  curl -s -o /dev/null -X POST "${BASE}/actuator/shutdown" || true
  for _ in $(seq 1 30); do
    kill -0 "$PID" 2>/dev/null || return 0
    sleep 1
  done
  kill "$PID" 2>/dev/null || true
  # Windows: gradle 클라이언트를 죽여도 앱 JVM 이 남을 수 있어 포트로 찾아 종료한다
  if command -v netstat >/dev/null && command -v taskkill >/dev/null; then
    for pid in $(netstat -ano | grep -E "[:.]${PORT}\s.*LISTEN" | awk '{print $NF}' | sort -u); do
      taskkill //F //PID "$pid" >/dev/null 2>&1 || true
    done
  fi
}
trap stop_app EXIT

up=0
# 반복 횟수가 아니라 경과 시간으로 제한한다 (Windows 에서 연결 거부 시 curl 한 번이 수 초 걸릴 수 있음)
deadline=$((SECONDS + 150))
while [ "$SECONDS" -lt "$deadline" ]; do
  if curl -sf -o /dev/null --connect-timeout 1 --max-time 2 "${BASE}/actuator/health"; then up=1; break; fi
  kill -0 "$PID" 2>/dev/null || break
  sleep 1
done

if [ "$up" -ne 1 ]; then
  echo "SMOKE BOOT FAILED — ${LOG} 마지막 40줄:"
  tail -40 "$LOG"
  exit 1
fi

call() {
  local method="$1" path="$2" body="${3:-}"
  echo "=== ${method} ${path}${body:+ ${body}}"
  if [ -n "$body" ]; then
    curl -s -i -X "$method" "${BASE}${path}" -H 'Content-Type: application/json' -d "$body" | head -c 1500
  else
    curl -s -i -X "$method" "${BASE}${path}" | head -c 1500
  fi
  echo
}

call GET /actuator/health

if [ -n "${1:-}" ]; then
  while read -r method path body || [ -n "${method:-}" ]; do
    [ -z "${method:-}" ] && continue
    case "$method" in \#*) continue ;; esac
    call "$method" "$path" "${body:-}"
  done <"$1"
fi

echo "=== SMOKE DONE"
