---
name: review-response
description: "PR 리뷰 코멘트(셀프 리뷰어 pr-reviewer 또는 사용자 본인이 남긴 것)를 하나씩 판단해 반영하거나 거절하고, 커밋 해시 또는 거절 사유로 답글을 다는 절차. '리뷰 반영해줘', '코멘트 처리해줘', 셀프 리뷰 직후에 사용한다."
---

# 리뷰 반영

목표: 모든 스레드가 **해시와 함께 반영** 또는 **사유와 함께 거절** 둘 중 하나로 끝난다. 답 없는 스레드는 다음 재리뷰에서 다시 지적된다.

## 1. 가져오기

```bash
gh pr view --json number,url,headRefOid
gh api graphql -f query='query($pr:Int!){repository(owner:"HAAHYO",name:"Donguri-Server"){pullRequest(number:$pr){reviewThreads(first:100){nodes{id isResolved isOutdated path line comments(first:20){nodes{databaseId author{login} body}}}}}}}' -F pr=<번호>
gh pr view <번호> --json reviews,comments   # 요약 리뷰/일반 코멘트
```

`_workspace/review-report.md` 의 표와 대조한다.

## 2. 판단 — 리뷰어가 AI라고 무조건 따르지 않는다

각 코멘트에 대해 인용된 코드를 직접 열어 확인한다.

| 판단 | 조건 | 행동 |
|------|------|------|
| 반영 | 지적이 사실이고 수정이 범위 안 | 수정 → 테스트 → 커밋(코멘트 하나당 하나) → 해시로 답글 |
| 거절 | 사실이 아님 / 계획상 의도된 동작 / 범위 밖 | 근거(파일:줄, 계획 문서)와 함께 답글. 범위 밖이면 후속 이슈로 남길지 적는다 |
| 질문 | 리뷰어의 질문 | 답글로 답한다. 코드가 설명을 필요로 했다면 그 자체가 개선 신호 |
| 사용자에게 | 공개 API 형식 변경, 새 의존성, 제품 결정 | 답글에 "사용자 결정 필요"라고 쓰고 사용자에게 묻는다. 답을 받으면 `_workspace/decisions.md` 에 `#<n> <날짜> 사용자 결정: "<사용자 원문>"` 으로 기록한다 — 재리뷰에서 리뷰어가 이 기록을 보고 닫는다 |

코멘트는 `_workspace/review-report.md` 표의 thread id / comment id 로 찾는다(작성자와 리뷰어가 같은 계정이라 작성자 이름으로 구분되지 않는다). diff 밖 지적(스레드 없음)은 `gh pr comment <번호> --body '#<n> ...'` 로 답한다.

(제안)도 비용이 작으면 반영한다. 혼자 하는 프로젝트에서 미뤄둔 제안은 다시 오지 않는다.

## 3. 답글

```bash
gh api repos/HAAHYO/Donguri-Server/pulls/<번호>/comments/<databaseId>/replies -f body='<답글>'
```

```
반영했어요 — a1b2c3d
`@Valid` 를 추가하고 빈 이름 요청이 400을 반환하는 테스트를 추가했습니다.
```

```
반영하지 않을게요.
`count` 는 생성 시점에 항상 0으로 초기화되어(Acorn.kt:22) null 상태가 존재하지 않습니다.
```

스레드 resolve는 **리뷰어(재리뷰)가** 한다. 작성자가 자기 답에 스스로 resolve하지 않는다 — 그래야 재리뷰가 의미를 가진다.

## 4. 다음 단계

반영 커밋이 하나라도 있으면: push → qa-inspector 재검증 → pr-reviewer 재리뷰 (`donguri-workflow` Phase 7). 코드가 바뀐 뒤의 QA/리뷰 리포트는 이전 HEAD 기준이라 hook이 머지를 막는다.
