#!/usr/bin/env bash
# 끝난 작업의 _workspace/ 를 _workspace_archive/<YYYYMMDD_HHMMSS>/ 로 옮기고 빈 _workspace/ 를 만든다.
#
# 사용: bash .claude/scripts/archive-workspace.sh
#
# - 보관본은 루트에 흩어지지 않도록 _workspace_archive/ 한 곳에 모은다 (gitignore 대상).
# - 예전 방식의 루트 _workspace_<timestamp>/ 가 있으면 함께 옮긴다.
# - 지우지 않는다. 옮기기만 한다.
set -euo pipefail

ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
cd "$ROOT"
ARCHIVE="_workspace_archive"
mkdir -p "$ARCHIVE"

for legacy in _workspace_[0-9]*/; do
  [ -d "$legacy" ] || continue
  name="${legacy#_workspace_}"
  name="${name%/}"
  if [ -e "$ARCHIVE/$name" ]; then
    echo "이미 존재해 건너뜀: $ARCHIVE/$name" >&2
    continue
  fi
  mv "$legacy" "$ARCHIVE/$name"
  echo "옮김: ${legacy%/} -> $ARCHIVE/$name"
done

if [ -d _workspace ] && [ -n "$(ls -A _workspace)" ]; then
  ts="$(date +%Y%m%d_%H%M%S)"
  if [ -e "$ARCHIVE/$ts" ]; then
    echo "보관본이 이미 존재함: $ARCHIVE/$ts — 잠시 후 다시 실행하세요" >&2
    exit 1
  fi
  mv _workspace "$ARCHIVE/$ts"
  echo "보관: _workspace -> $ARCHIVE/$ts"
fi
mkdir -p _workspace
