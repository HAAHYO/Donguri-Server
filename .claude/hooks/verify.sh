#!/usr/bin/env bash
# Stop hook: Claude가 작업을 끝내기 전에 CI와 같은 검증(./gradlew check)을 강제한다.
# 실패하면 exit 2 로 종료를 막고, 실패 로그를 Claude에게 돌려줘 스스로 고치게 한다.
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"

input="$(cat)"
attempts_file="build/.claude-verify-attempts"
max_attempts=3

# 하네스 hook 을 바꿨으면 hook 회귀 테스트를 강제한다 (CI 와 같은 명령)
if [ -n "$(git status --porcelain -- .claude/hooks 2>/dev/null)" ]; then
  # guard-git.sh 와 같은 방식: 존재가 아니라 "실제로 실행되는" python 을 고른다 (WindowsApps 스텁 회피)
  py=""
  for cand in ${GUARD_PYTHON:-python python3}; do
    if "$cand" -c "import sys; sys.exit(0)" >/dev/null 2>&1; then
      py="$cand"
      break
    fi
  done
  if [ -z "$py" ]; then
    # 인터프리터 부재를 테스트 실패로 오인해 종료를 막지 않는다. CI 가 같은 테스트를 돌린다
    echo '{"systemMessage": "verify hook: 실행 가능한 python 이 없어 hook 회귀 테스트를 건너뜁니다."}'
  elif ! hook_log="$("$py" .claude/hooks/test_guard_git.py 2>&1)"; then
    # 이미 한 번 차단했는데 여전히 실패하면 루프를 피하고 경고만 남긴다
    if echo "$input" | grep -q '"stop_hook_active":[[:space:]]*true'; then
      echo '{"systemMessage": "verify hook: hook 회귀 테스트가 여전히 실패합니다. 직접 확인이 필요합니다."}'
      exit 0
    fi
    {
      echo "hook 회귀 테스트 실패 (python .claude/hooks/test_guard_git.py). 아래 오류를 고친 뒤 다시 마무리하세요."
      echo "$hook_log" | grep -E "FAIL|ERROR|Error|assert" | tail -20
    } >&2
    exit 2
  fi
fi

# 코드/빌드 파일 변경이 없으면 검증 생략 (대화만 한 턴에서 빌드 비용 절약)
if [ -z "$(git status --porcelain -- src '*.kts' gradle 2>/dev/null)" ]; then
  exit 0
fi

# 무한 루프 방지: 연속 차단 횟수 제한
if echo "$input" | grep -q '"stop_hook_active":[[:space:]]*true'; then
  attempts=$(( $(cat "$attempts_file" 2>/dev/null || echo 0) + 1 ))
else
  attempts=1
fi
mkdir -p build && echo "$attempts" > "$attempts_file"

log="$(./gradlew ktlintFormat check --console=plain -q 2>&1)"
if [ $? -eq 0 ]; then
  rm -f "$attempts_file"
  exit 0
fi

if [ "$attempts" -gt "$max_attempts" ]; then
  echo "{\"systemMessage\": \"verify hook: ./gradlew check 가 ${max_attempts}회 연속 실패해 차단을 해제했습니다. 직접 확인이 필요합니다.\"}"
  rm -f "$attempts_file"
  exit 0
fi

{
  echo "./gradlew check 실패 (시도 ${attempts}/${max_attempts}). 아래 오류를 고친 뒤 다시 마무리하세요."
  echo "$log" | grep -v '^\s*$' | tail -40
} >&2
exit 2
