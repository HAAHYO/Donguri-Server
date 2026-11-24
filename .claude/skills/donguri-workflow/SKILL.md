---
name: donguri-workflow
description: "Donguri 백엔드의 작업 전 과정 오케스트레이터 — 계획 → 구현+테스트 → 커밋 → 자가 QA(qa-inspector) → PR → 셀프 리뷰(pr-reviewer) → 리뷰 반영 → 머지 확인. 팀원 없는 개인 프로젝트에서 리뷰어 역할을 분리된 에이전트로 대신하는 루프다. 엔드포인트 추가/변경, 도메인 추가, 엔티티/쿼리 변경, 설정 변경 등 한 줄 수정이 아닌 모든 백엔드 작업에 사용한다. 'PR 올려줘', 'QA 다시 돌려줘', '리뷰 반영해줘', '셀프 리뷰해줘', '이어서 해줘' 같은 후속 요청도 이 스킬로 처리하며 기존 _workspace/ 를 재사용한다. 질문 답변이나 한 줄 수정에는 쓰지 않는다."
---

# Donguri 작업 흐름

혼자 하는 프로젝트의 문제는 "다른 눈"이 없다는 것이다. 이 흐름은 그 눈을 두 개의 분리된 에이전트로 만든다.

| 역할 | 누가 | 왜 분리하는가 |
|------|------|---------------|
| 구현 | 메인 에이전트(당신) | — |
| 자가 QA | `qa-inspector` 서브에이전트 | 구현 컨텍스트가 없어야 "당연히 되겠지"를 믿지 않는다 |
| 셀프 리뷰 | `pr-reviewer` 서브에이전트 | QA와도 분리 — QA는 동작을, 리뷰어는 설계와 유지보수성을 본다 |

리뷰어와 QA는 **기록을 남긴다**(`_workspace/` 리포트 + GitHub PR 코멘트). 나중에 혼자 돌아봤을 때 무엇이 검증됐는지 알 수 있어야 한다.

### 기계적 강제 (PreToolUse hook `guard_git.py`)
말로 하는 규칙이 아니라 hook이 막는다. 우회하려 하지 말고 원인을 해결한다.
- main/develop 에 커밋/푸시 불가 → 항상 기능 브랜치
- `gh pr create` 는 `--base` 필수: 기능 브랜치 → `develop`, `develop` → `main`(릴리스)만 허용
- `gh api` 는 리뷰 스레드 답글/해결 외의 GraphQL mutation, ref 쓰기 금지
- `git add -A` / `.` / `--no-verify` 불가
- 커밋 제목 형식 검사
- `gh pr create` ← `_workspace/qa-report.md` 가 **현재 HEAD** 기준 `PASS` 여야 함
- `gh pr merge` ← 추가로 `_workspace/review-report.md` 가 현재 HEAD·해당 PR 기준 `required_open=0` 이어야 함
- `gh api` 로 머지(`/pulls/N/merge`, `mergePullRequest` 등) 금지 — 게이트 우회 경로
- Stop hook: `./gradlew check` 실패 시 종료 불가

**리포트 마커를 직접 쓰거나 고치지 않는다.** `qa-report.md` 는 qa-inspector만, `review-report.md` 는 pr-reviewer만 작성한다. 메인 에이전트가 마커를 쓰는 순간 이 하네스 전체가 무의미해진다.

## Phase 0: 상태 확인

1. `_workspace/` 존재 여부와 `git status --short`, 현재 브랜치를 확인한다.
2. 분기:
   - `_workspace/` 없음 또는 비어 있음 → **새 작업**. Phase 1.
   - 있음 + 부분 요청("QA 다시", "리뷰 반영", "PR 올려줘") → 해당 Phase부터 재개. 아래 재실행 표 참고.
   - 있음 + 무관한 새 기능 → `bash .claude/scripts/archive-workspace.sh` 로 `_workspace/` 를 `_workspace_archive/<YYYYMMDD_HHMMSS>/` 에 보관하고 Phase 1.

## Phase 1: 계획

1. 최신 develop 에서 브랜치를 만든다: `git fetch origin` → `git switch -c <type>/<kebab-주제> origin/develop` (예: `feat/acorn-api`).
2. `_workspace/01_plan.md` 에 작성 — 이 문서가 QA의 체크리스트가 된다:
   - 요청 요약, 범위 밖인 것
   - 엔드포인트마다: 메서드/경로, 요청/응답 필드(nullable 여부 포함), **실패 케이스 표**(입력 → 상태 코드 → 예외 클래스)
   - 바뀌는 파일 목록, 추가할 테스트 목록
   - 사용자에게 물어야 할 결정 (있으면 진행 전에 묻는다)
3. 기존 코드를 먼저 읽고 `kotlin-spring-conventions` 스킬을 따른다.

## Phase 2: 구현 + 테스트

- 동작을 바꾸면 테스트를 같이 쓴다. 실패 케이스 표의 각 행에 대응하는 테스트가 있어야 한다.
- 수시로 `./gradlew check`. 포맷 문제는 `./gradlew ktlintFormat`.

## Phase 3: 커밋

`git-commit-convention` 스킬대로 작은 단위, 의존성 순서로 커밋한다. 작업 트리를 깨끗하게 만든다 — QA는 커밋된 HEAD만 검증한다.

## Phase 4: 자가 QA

```
Agent(subagent_type: "qa-inspector",
      prompt: "현재 브랜치(<branch>, base develop)의 변경을 self-qa 스킬대로 검증하라. 계획: _workspace/01_plan.md. 리포트: _workspace/qa-report.md")
```

- FAIL → BLOCKER를 고치고 커밋 → 같은 에이전트를 **재검증 모드로** 다시 호출. 최대 3회. 3회 후에도 FAIL이면 멈추고 사용자에게 보고한다.
- QA의 "검증 안 됨" 항목은 PR 본문으로 그대로 옮긴다.

## Phase 5: PR

1. `git push -u origin <branch>`
2. `gh pr create` — 본문은 `.github/pull_request_template.md` 형식을 채운다. 체크박스는 **실제로 확인한 것만** 체크하고, QA 리포트 요약과 "검증 안 됨"을 포함한다. 과장된 체크리스트는 혼자 보는 기록을 거짓으로 만든다.

## Phase 6: 셀프 리뷰

```
Agent(subagent_type: "pr-reviewer",
      prompt: "PR #<번호>를 pr-review 스킬대로 리뷰하고 GitHub에 코멘트를 남겨라. 리포트: _workspace/review-report.md")
```

## Phase 7: 리뷰 반영

`review-response` 스킬대로 각 코멘트를 판단한다 — 반영(커밋 해시와 함께 답글) 또는 거절(사유 답글). 반영 커밋이 생기면:
1. push
2. qa-inspector 재검증 (코드가 바뀌었으니 이전 QA는 무효)
3. pr-reviewer 재리뷰
최대 3라운드. 그 후에도 필수가 남으면 사용자에게 넘긴다.

**사용자 결정이 필요한 항목**은 사용자에게 묻고, 답을 `_workspace/decisions.md` 에 원문 그대로 기록한 뒤 재리뷰를 요청한다. 리뷰어가 그 기록을 확인해야만 항목이 닫힌다 — 그래야 마커를 고치거나 우회하지 않고도 머지 게이트를 통과할 수 있다.

## Phase 8: 머지

`required_open=0` 이고 CI(`gh pr checks`)가 통과하면 사용자에게 머지 여부를 묻는다 (`gh pr merge` 는 권한상 ask). 기능 PR 은 `develop` 으로 머지된다 — 배포는 일어나지 않는다.

## Phase 8b: 릴리스 (develop → main)

사용자가 "릴리스해줘", "main 에 올려줘" 라고 할 때만. main 머지는 CD(GHCR 이미지 배포)를 트리거한다.

1. `git switch develop && git pull --ff-only origin develop` (로컬 develop 은 읽기 전용 — 커밋하지 않는다)
2. `bash .claude/scripts/archive-workspace.sh` 로 `_workspace/` 를 `_workspace_archive/<timestamp>/` 에 보관하고 새로 만든다. `01_plan.md` 에는 `git log --oneline origin/main..origin/develop` 로 이번 릴리스에 포함된 PR 목록을 적는다.
3. qa-inspector — develop HEAD 기준 전체 QA(빌드 + 스모크)
4. `gh pr create --base main --head develop` (hook 이 develop 에서만 main 을 base 로 허용)
5. pr-reviewer — 릴리스 PR 은 이미 리뷰된 변경의 묶음이다. 기능 PR 간 상호작용, 설정/배포 영향(새 필수 환경변수 등)만 본다.
6. 사용자 확인 후 머지 → CD 실행 결과를 `gh run list --workflow cd.yml` 로 확인해 보고한다.

## Phase 9: 마무리 보고 + 하네스 개선

사용자에게 한국어로 보고:

```markdown
## <작업명> — 완료 | 차단됨
**PR:** <url> (머지됨 | 머지 대기)
**검증:** ./gradlew check PASS · QA <판정> (BLOCKER n / MAJOR n / MINOR n) · 셀프 리뷰 필수 n건 반영, n건 거절
### 거절한 리뷰 코멘트
- <코멘트 요약> — <사유>
### 검증 안 됨
- <항목> — <이유>
```

QA나 리뷰어가 **같은 종류의 지적을 두 번째로** 했다면 `_workspace/harness-notes.md` 에 적고, 보고에서 "기계적으로 막을 방법"(ktlint 규칙, 테스트, hook)을 제안한다. CLAUDE.md의 하네스 개선 원칙이다.

`_workspace/` 는 지우지 않는다 — 다음 부분 재실행의 입력이다.

## 재실행 표

| 사용자 요청 | 시작 Phase |
|-------------|-----------|
| "QA 다시 돌려줘" | 4 (재검증 모드) |
| "QA에서 나온 거 고쳐줘" | 2 → 3 → 4 |
| "PR 올려줘" | 3 → 4 → 5 → 6 |
| "셀프 리뷰해줘" | 6 |
| "리뷰 반영해줘" | 7 |
| "머지해줘" | 8 |
| "릴리스해줘", "main 에 올려줘" | 8b |

## 오류 처리

| 상황 | 대응 |
|------|------|
| hook이 명령을 차단 | 메시지가 가리키는 원인을 해결한다. 마커 위조·우회 금지 |
| QA와 메인 에이전트의 판단이 다름 | 양쪽이 인용한 코드를 직접 읽고 결정. 근거를 qa-report에 대한 답으로 `_workspace/02_notes.md` 에 남기고 재검증 |
| 리뷰어와 2라운드 이상 같은 쟁점 | 사용자 결정 필요로 올린다 |
| 계획이 실제 코드와 모순 | 코드가 런타임 진실이다. 01_plan.md 를 고치고 그 사실을 기록 |
| 기존 공개 동작이 바뀜(응답 형식, 상태 코드) | 진행 전 사용자에게 확인 |
