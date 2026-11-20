#!/usr/bin/env bash
# guard_git.py 실행 래퍼.
# - python / python3 중 "실제로 실행되는" 것을 쓴다. Windows 의 WindowsApps python3 스텁은
#   PATH 에 있어도 실행하면 exit 49 로 실패하므로 존재 여부만으로 고르지 않는다.
# - 쓸 수 있는 인터프리터가 없으면 git/gh 가 들어간 명령만 차단한다
#   (가드가 조용히 꺼지지 않게, 그러나 ls 같은 일반 명령까지 막지는 않게).
# - GUARD_PYTHON 으로 인터프리터를 지정할 수 있다 (테스트용).
input="$(cat)"

py=""
for cand in ${GUARD_PYTHON:-python python3}; do
  if "$cand" -c "import sys; sys.exit(0)" >/dev/null 2>&1; then
    py="$cand"
    break
  fi
done

if [ -z "$py" ]; then
  if printf '%s' "$input" | grep -Eq '(^|[^[:alnum:]_])(git|gh)([^[:alnum:]_]|$)'; then
    echo "[guard-git] 실행 가능한 python 을 찾을 수 없어 git/gh 규칙을 검사할 수 없습니다. 명령을 차단합니다. python 설치/PATH 를 확인하세요." >&2
    exit 2
  fi
  exit 0
fi

printf '%s' "$input" | "$py" "$(dirname "$0")/guard_git.py"
status=$?
if [ "$status" -ne 0 ] && [ "$status" -ne 2 ]; then
  echo "[guard-git] guard_git.py 비정상 종료(exit $status) — 명령을 차단합니다." >&2
  exit 2
fi
exit "$status"
