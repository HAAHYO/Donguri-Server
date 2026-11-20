---
name: self-qa
description: "Donguri 서버 변경에 대한 자가 QA 절차 — 계획 대비 구현, 레이어 경계, 예외→HTTP 상태, 테스트의 실제 검증력, 앱을 띄운 스모크 호출을 확인하고 _workspace/qa-report.md 에 PASS/FAIL 마커와 함께 기록한다. qa-inspector 에이전트의 주 스킬이며, 'QA 해줘', '검증해줘', '제대로 동작하는지 확인해줘', PR 전 점검, 엔드포인트가 예상과 다른 상태 코드를 줄 때 사용한다."
---

# 자가 QA

리뷰어가 없는 프로젝트에서 QA는 "통과했다"고 말할 수 있는 유일한 근거다. 그러므로 통과라고 쓴 모든 줄에는 증거가 있어야 하고, 확인하지 못한 것은 확인하지 못했다고 써야 한다.

## 1. 범위 파악

```bash
git status --short                  # 비어 있어야 함 (아니면 BLOCKER)
git rev-parse HEAD                  # 리포트 마커에 쓸 값
git log --oneline main..HEAD        # 이번 변경의 커밋들
git diff --stat main...HEAD
git diff main...HEAD
```

`_workspace/01_plan.md` 의 엔드포인트와 실패 케이스 표가 체크리스트다.

## 2. 빌드

```bash
./gradlew check --console=plain     # ktlint + 전체 테스트
```

실패하면 그 자체로 BLOCKER. 출력의 핵심 줄을 증거로 인용한다.

## 3. 정적 교차 검사 — 양쪽을 동시에 읽는다

| # | 검사 | 왼쪽 | 오른쪽 |
|---|------|------|--------|
| 1 | 계획 ↔ 구현 | 01_plan.md 의 엔드포인트·필드·실패 표 | 실제 컨트롤러/DTO/throw |
| 2 | 컨트롤러 ↔ 서비스 | 컨트롤러 메서드 본문 | 서비스 시그니처·반환 타입. 컨트롤러에 비즈니스 로직/리포지토리 접근이 없는가 |
| 3 | 예외 ↔ 상태 코드 | 코드 경로의 모든 `throw` | 예외 핸들러 매핑. 매핑 없는 예외는 500이다. 계획의 실패 행마다 throw가 있고, throw마다 계획 행이 있는가 |
| 4 | 응답 DTO ↔ 원천 데이터 nullability | DTO 프로퍼티 타입 | 엔티티/도메인 객체가 가질 수 있는 모든 상태. 기본값(0, "")으로 null을 숨기지 않았는가 |
| 5 | 요청 검증 | `@Valid` + 제약 어노테이션 | 컨트롤러 파라미터에 `@Valid` 가 실제로 붙었는가. 빠지면 제약이 조용히 무시된다 |
| 6 | 테스트 ↔ 주장 | 테스트 이름/단언 | 실제로 실패 케이스를 실행하는가, 상태 코드와 본문을 단언하는가. "예외가 안 나면 통과"하는 공허한 테스트를 찾는다 |
| 7 | 설정 ↔ 코드 | `application.properties`, 프로파일 | 새 설정 키가 기본값 없이 필수가 되었는가(배포 시 부팅 실패). 비밀값이 하드코딩되었는가 |
| 8 | 변경의 파급 | 바뀐 public 메서드/DTO | 그것을 쓰는 다른 호출부 (`Grep` 으로 전수 확인) |

영속성(JPA 등)이 도입된 이후라면 추가로: 파생 쿼리 이름 ↔ 엔티티 프로퍼티, 컬렉션 쿼리의 정렬 지정, LAZY 연관 ↔ DTO 접근(N+1), `@Transactional` 경계.

## 4. 스모크 — 실제로 띄워서 호출한다

이 프로젝트는 외부 의존 없이 부팅된다. 정적 분석이 놓치는 것(빈 와이어링, 직렬화 형식, 실제 상태 코드)을 여기서 잡는다.

1. 계획의 각 엔드포인트에 대해 정상 1회 + 실패 표의 각 행 1회를 `_workspace/smoke-requests.txt` 에 쓴다 (Write 도구). 줄마다 `METHOD PATH [JSON 본문]`:
   ```
   POST /acorns {"name":"도토리"}
   POST /acorns {"name":""}
   GET /acorns/999
   ```
2. 실행 — **Bash 타임아웃 300000ms 이상**으로:
   ```bash
   bash .claude/scripts/smoke.sh _workspace/smoke-requests.txt
   ```
   스크립트가 Gradle toolchain(JDK 21)으로 `bootRun` 을 띄우고, health 대기 → 각 요청 호출 → 종료까지 한다. `java -jar` 를 직접 쓰지 않는다 — PATH 의 java 가 21이 아닐 수 있다.

- 각 호출의 **상태 코드와 본문 일부**를 증거로 리포트에 붙인다.
- `SMOKE BOOT FAILED` 면 출력된 `_workspace/smoke.log` 원인 줄을 인용 — 거의 항상 BLOCKER다.
- `포트 사용 중` 이면 이전 프로세스가 남은 것이다. `netstat -ano | grep 18080` 으로 확인해 종료하고 다시 실행한다.
- 외부 의존(DB 등)이 생겨 띄울 수 없다면 "검증 안 됨"에 적는다. PASS로 쓰지 않는다.

## 5. 리포트

`_workspace/qa-report.md` (재검증이면 상단 마커와 판정을 갱신하고 이력은 아래에 날짜 섹션으로 덧붙인다):

```markdown
<!-- qa-report head=<전체 SHA> verdict=PASS|FAIL -->
# QA 리포트 — <작업명>
- 브랜치: <branch> · HEAD: <short sha> · 일시: <YYYY-MM-DD HH:MM>
- 판정: **PASS | FAIL** (BLOCKER n / MAJOR n / MINOR n)

## 검증함
| # | 검사 | 결과 | 증거 |
|---|------|------|------|
| B | ./gradlew check | PASS | 테스트 n개 통과 |
| S | POST /acorns (정상) | PASS | 201, {"id":1,...} |
| 3 | 예외 ↔ 상태 코드 | FAIL | AcornService.kt:31 → 핸들러 없음 |

## 지적
### [BLOCKER] <한 줄 요약>
- **위치:** `파일:줄` ↔ `파일:줄`
- **실패 시나리오:** <구체 입력> → <잘못된 결과>
- **수정안:** <구체적 변경>

## 검증 안 됨
- <항목> — <이유>
```

### 판정 기준
- **BLOCKER** — 런타임에 틀린 동작: 잘못된 상태 코드, 500, 부팅 실패, 계획한 엔드포인트 누락, 테스트 실패, 미커밋 변경, 비밀값 커밋.
- **MAJOR** — 지금은 동작하지만 경계 조건/부하에서 틀림: 누락된 실패 케이스 테스트, null 숨김, N+1, 정렬 없는 목록.
- **MINOR** — 이름, 죽은 코드, 컨벤션 차이.

BLOCKER가 0이면 PASS. MAJOR는 PR 본문에 남겨 리뷰어가 보게 한다.

### 규칙
- "검증 안 됨" 섹션은 생략하지 않는다. 비어 있다면 "없음"이라고 쓴다.
- 실행하지 않은 검사를 PASS로 쓰지 않는다.
- 마커의 head는 `git rev-parse HEAD` 의 실제 값이다. 작업 트리가 더럽다면 판정은 FAIL이다.
- 지적이 10개를 넘으면 BLOCKER는 전부, 나머지는 유형별로 묶는다.
