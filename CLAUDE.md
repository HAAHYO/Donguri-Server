# Donguri

Kotlin + Spring Boot 4 (Gradle Kotlin DSL, JDK 21) 백엔드.

## 명령어
- 검증(단일 진실 원천): `./gradlew check` — ktlint + 테스트. CI와 Stop hook이 같은 명령을 사용한다.
- 포맷 자동 수정: `./gradlew ktlintFormat`
- 실행: `./gradlew bootRun` (헬스체크: `GET /actuator/health`)
- 패키징: `./gradlew bootJar` → `build/libs/app.jar`

## 작업 규칙
- 작업을 끝내기 전 `./gradlew check`가 통과해야 한다. Stop hook(`.claude/hooks/verify.sh`)이 강제하며, 실패 시 로그가 되돌아온다.
- 동작을 바꾸면 테스트를 함께 추가/수정한다. 테스트를 삭제하거나 `@Disabled`로 통과시키지 않는다.
- 코드 스타일은 ktlint(`.editorconfig`)가 결정한다. 스타일 논쟁 대신 `ktlintFormat`을 실행한다.
- `gradle/wrapper/**`는 직접 수정하지 않는다 (`./gradlew wrapper --gradle-version X`로만 갱신).
- 비밀값은 커밋하지 않는다. 환경변수 또는 GitHub Secrets를 사용한다.

## 구조
- `src/main/kotlin/com/donguri` — 애플리케이션 코드
- `src/test/kotlin/com/donguri` — 테스트
- `.github/workflows/ci.yml` — PR, main/develop 푸시 시 `./gradlew check`
- `.github/workflows/cd.yml` — main/`v*` 태그 푸시 시 검증 후 Docker 이미지를 GHCR에 푸시
- `.claude/` — 에이전트 하네스 (권한, hook, 에이전트, 스킬)
- `_workspace/` — 작업별 계획/QA/리뷰 기록 (gitignore, 지우지 않는다)

## 하네스: 혼자서도 리뷰받기
팀원이 없으므로 리뷰어 역할을 분리된 에이전트가 맡는다. 한 줄 수정이나 질문이 아닌 모든 백엔드 작업은 `donguri-workflow` 스킬을 따른다.
Kotlin 파일을 쓰거나 리뷰하기 전에는 항상 `kotlin-spring-conventions` 스킬을 읽는다.

계획 → 구현+테스트 → 커밋 → **자가 QA**(`qa-inspector`) → PR → **셀프 리뷰**(`pr-reviewer`, GitHub에 코멘트) → 반영/거절 답글 → 재QA·재리뷰 → 사용자 확인 후 머지

**브랜치 전략:** `origin/develop` 에서 `<type>/<주제>` 브랜치 → PR → `develop`. 기능이 모이면 릴리스 PR `develop` → `main` (main 머지 = CD 배포). main/develop 직접 커밋·푸시는 hook이 막는다.

| 구성 | 역할 |
|------|------|
| `agents/qa-inspector` | 구현 컨텍스트 없이 검증 + 앱 실행 스모크 → `_workspace/qa-report.md` |
| `agents/pr-reviewer` | 없는 팀원 대신 PR 리뷰 → GitHub 코멘트 + `_workspace/review-report.md` |
| `skills/` | `donguri-workflow`, `self-qa`, `pr-review`, `review-response`, `git-commit-convention`, `kotlin-spring-conventions` |
| `hooks/guard_git.py` (PreToolUse, `guard-git.sh` 로 실행) | main/develop 커밋·푸시, 일괄 스테이징, hook 우회, 잘못된 커밋 제목, 브랜치 전략에 맞지 않는 PR base(생성·수정·머지 모두), `gh api` 머지·ref 쓰기·저장소 설정 변경 차단. 현재 HEAD 기준 QA PASS 없이 `gh pr create`, 셀프 리뷰 필수 0건 없이 `gh pr merge` 차단 |
| `scripts/smoke.sh` | QA 스모크: toolchain JDK로 앱 기동 → 요청 → 종료 |
| `hooks/verify.sh` (Stop) | `./gradlew check` 실패 시 종료 차단 |

- QA/리뷰 리포트의 첫 줄 마커는 해당 에이전트만 쓴다. 메인 에이전트가 직접 쓰거나 고치면 하네스가 무의미해진다.
- hook을 바꾸면 `python .claude/hooks/test_guard_git.py` 를 통과시킨다(CI에서도 실행).

## 하네스 개선 원칙
에이전트가 같은 실수를 반복하면 이 문서에 문장을 추가하기보다 먼저 기계적으로 막을 방법(린트 규칙, 테스트, hook)을 찾는다.
