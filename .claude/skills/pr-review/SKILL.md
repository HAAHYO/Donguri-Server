---
name: pr-review
description: "SafeFlow PR을 '팀원 리뷰어'처럼 리뷰하고 GitHub에 한국어 라인 코멘트와 요약 리뷰를 남기는 방법 — 무엇이 코멘트할 가치가 있는지, 이 코드베이스에서 볼 것, gh 로 게시하는 법, _workspace/review-report.md 형식. pr-reviewer 에이전트의 주 스킬이며 'PR 리뷰해줘', '셀프 리뷰', '코멘트 남겨줘' 요청에 사용한다."
---

# PR 셀프 리뷰

테스트와 CI가 "동작하는가"를 본다면, 리뷰어는 **"6개월 뒤의 내가 이 코드를 고칠 때 괜찮은가"** 를 본다.

## 코멘트를 남길 기준

질문은 "이게 사실인가?"가 아니라 **"작성자가 이걸 보고 코드를 바꿀까?"** 다.

| 남긴다 | 남기지 않는다 |
|--------|---------------|
| 구체적 입력에서 틀린 동작 | 내가 썼으면 다르게 썼을 것 |
| `kotlin-spring-conventions` 위반 | 이 코드베이스가 어디서도 따르지 않는 일반론 |
| 처리되지 않은 실패 경로, 잘못된 상태 코드 | ktlint가 잡는 포맷 (CI가 이미 막는다) |
| 레이어 경계 불일치 | 다른 코멘트에서 이미 지적한 것 |
| 테스트가 주장하는 것을 실제로 검증하지 않음 | diff가 하는 일을 다시 설명하는 것 |
| 이름/구조가 실제로 오해를 부름 | 취향 차이의 이름 |
| 정말로 의도를 모르겠는 부분 | CI 가 이미 실패로 잡은 것 |

사소하지만 실제인 것 여러 개는 라인 코멘트로 흩뿌리지 말고 요약 리뷰에 묶는다. 칭찬은 구체적일 때만 ("LGTM" ✗).

## 볼 것 — 우선순위 순

1. 계획(`_workspace/01_plan.md`)에 없던 동작 변경, 또는 계획했는데 빠진 것
2. 예외 → 상태 코드 매핑 누락 (500 유출), 내부 정보가 담긴 에러 본문
3. `@Valid` 누락, 검증 없이 신뢰하는 입력
4. 테스트의 공백 — 실패 케이스 표의 행 중 테스트가 없는 것
5. 컨트롤러의 비즈니스 로직, 엔티티를 그대로 응답
6. 바뀐 public 계약을 쓰는 다른 호출부
7. 하드코딩된 설정값/비밀값, 기본값 없는 새 필수 설정
8. 커밋 분리 — 리뷰할 수 없을 만큼 섞인 커밋 (이건 요약 리뷰에)

## 코멘트 작성

구조: 무엇이 문제 → 왜 문제 → 무엇을 할지. 해요체, 사람이 아니라 코드에 대해.

```
(필수) `createAcorn` 의 요청 파라미터에 `@Valid` 가 없어서 `CreateAcornRequest.name` 의 `@NotBlank` 가 동작하지 않아요.
빈 문자열로 생성 요청을 보내면 400 대신 그대로 저장됩니다. 파라미터에 `@Valid` 를 붙이고 실패 케이스 테스트를 추가해주세요.
```

```
(제안) `AcornResponse.from` 이 `count` 가 null 일 때 0을 넣고 있어요. "아직 집계 안 됨"과 "0개"가 구분되지 않으니 `Int?` 로 두는 게 어떨까요?
```

나쁜 예: `이거 좀 이상해요` (무엇이, 왜?), `예외 처리 필요` (어디에, 어떤 입력에서?)

## 게시

```bash
gh pr view <번호> --json number,title,body,headRefOid,files
gh pr diff <번호>
```

diff를 읽은 뒤 반드시 주변 파일을 연다. 그 다음:

**라인 코멘트** (한 번에 묶어서 리뷰로 게시 — 코멘트마다 알림이 가지 않도록). payload는 **Write 도구로** `_workspace/review-payload.json` 에 쓴다(셸 heredoc 대신 — 한글/따옴표 이스케이프 문제와 권한 프롬프트를 피한다):

```json
{
  "commit_id": "<headRefOid>",
  "event": "COMMENT",
  "body": "<요약 리뷰 마크다운>",
  "comments": [
    {"path": "src/main/kotlin/com/safeflow/...", "line": 42, "side": "RIGHT", "body": "(필수) ..."}
  ]
}
```

```bash
gh api repos/HAAHYO/SafeFlow-Server/pulls/<번호>/reviews --input _workspace/review-payload.json
```

`line` 은 diff에 포함된 줄이어야 한다(아니면 422). diff 밖의 문제는 요약 본문에 `파일:줄` 로 적는다.

**재리뷰 시 스레드 답글/해결:**

```bash
# 스레드 목록 (isResolved 포함)
gh api graphql -f query='query($pr:Int!){repository(owner:"HAAHYO",name:"SafeFlow-Server"){pullRequest(number:$pr){reviewThreads(first:100){nodes{id isResolved path line comments(first:20){nodes{databaseId author{login} body}}}}}}}' -F pr=<번호>
# 답글
gh api repos/HAAHYO/SafeFlow-Server/pulls/<번호>/comments/<comment databaseId>/replies -f body='확인했습니다. <해시>에서 해결됐어요.'
# 해결
gh api graphql -f query='mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}' -f id=<thread id>
```

## 요약 리뷰 본문

```markdown
## 셀프 리뷰 요약
<전반 평가 1~2줄>

### 필수 (n건)
- `파일:줄` — <요약>
### 제안 (n건)
- `파일:줄` — <요약>
### 질문 (n건)
### 확인하지 못한 부분
- <읽지 못한/판단 못한 부분과 이유>
```

## 리포트 — `_workspace/review-report.md`

```markdown
<!-- self-review pr=<번호> head=<headRefOid 전체 SHA> required_open=<열린 필수 수> -->
# 셀프 리뷰 — PR #<번호> (라운드 n)
| # | 심각도 | 위치 | 요약 | thread id | comment id | 상태 |
|---|--------|------|------|-----------|------------|------|
| 1 | 필수 | AcornController.kt:18 | @Valid 누락 | PRRT_xxx | 123456 | OPEN / RESOLVED(<해시>) / DECLINED-수긍 / 사용자 결정 필요 / DECIDED(사용자) |
| 2 | 필수 | (diff 밖) Foo.kt:40 | ... | — (요약 본문) | — | OPEN |
## 사용자 결정 필요
- #<n> <쟁점> — 리뷰어 입장 / 작성자 입장
```

- **thread id / comment id 를 반드시 적는다.** 작성자와 리뷰어가 같은 GitHub 계정이라 `author{login}` 으로 누가 쓴 코멘트인지 구분할 수 없다. 재리뷰는 이 id로 스레드를 찾는다.
- diff 밖의 지적은 스레드가 없다. 작성자는 PR 일반 코멘트(`gh pr comment`)에 `#<n>` 을 붙여 답한다.
- `required_open` 은 상태가 OPEN 또는 "사용자 결정 필요"인 필수 코멘트 수다. `DECIDED(사용자)` 는 세지 않는다.
- **사용자 결정을 닫는 법:** 작성자(메인 에이전트)는 사용자에게 물어 받은 답을 `_workspace/decisions.md` 에 `#<n> <날짜> 사용자 결정: "<사용자 원문>"` 형식으로 기록한다. 재리뷰 때 리뷰어가 그 기록을 확인하고 상태를 `DECIDED(사용자)` 로 바꾼다. 기록 없이 닫지 않는다.
- 재리뷰 때는 표를 갱신하고 라운드를 올린다.

## 규칙
- `--approve` / `--request-changes` 쓰지 않는다 (event는 항상 `COMMENT`).
- 같은 지적을 두 번 남기지 않는다.
- 근거(구체적 실패 또는 컨벤션)를 댈 수 없는 코멘트는 남기지 않는다.
- PR이 크면 중요한 것부터 보고, 나머지는 얕게 봤다고 요약에 쓴다.
