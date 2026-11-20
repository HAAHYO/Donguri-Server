#!/usr/bin/env bash
# guard_git.py 실행 래퍼.
# - python / python3 중 있는 것을 쓴다 (Windows 는 python, CI/macOS 는 python3 인 경우가 많다)
# - 인터프리터가 없으면 git/gh 가 들어간 명령만 차단한다 (가드가 조용히 꺼지지 않게, 그러나 ls 까지 막지는 않게)
input="$(cat)"
py="$(command -v python || command -v python3)"
if [ -z "$py" ]; then
  case "$input" in
    *git*|*gh\ *)
      echo "[guard-git] python 을 찾을 수 없어 git/gh 규칙을 검사할 수 없습니다. 명령을 차단합니다. python 설치/PATH 를 확인하세요." >&2
      exit 2 ;;
  esac
  exit 0
fi
printf '%s' "$input" | "$py" "$(dirname "$0")/guard_git.py"
status=$?
[ "$status" -eq 0 ] || [ "$status" -eq 2 ] || {
  echo "[guard-git] guard_git.py 비정상 종료(exit $status) — 명령을 차단합니다." >&2
  exit 2
}
exit "$status"
