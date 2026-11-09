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
- `.github/workflows/ci.yml` — PR/main 푸시 시 `./gradlew check`
- `.github/workflows/cd.yml` — main/`v*` 태그 푸시 시 검증 후 Docker 이미지를 GHCR에 푸시
- `.claude/` — 에이전트 하네스 (권한, Stop hook)

## 하네스 개선 원칙
에이전트가 같은 실수를 반복하면 이 문서에 문장을 추가하기보다 먼저 기계적으로 막을 방법(린트 규칙, 테스트, hook)을 찾는다.
