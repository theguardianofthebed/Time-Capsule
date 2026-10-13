# Contributing / 협업 규칙

This PR proposes the full workflow from Geonwoo's Notion draft to replace bootstrap-only guidance. The commit format below replaces temporary bracket tags after this change is reviewed and merged. / 초기 임시 가이드를 대체할 노션 협업 초안이며 사람의 리뷰·병합 후 저장소 규칙으로 적용합니다.

## Work and branches

Create an issue before meaningful work, link FR/AC and design, and identify MVP/Stretch scope and dependencies. Keep main runnable once the Android app exists. Do not push normal work directly to main; use one focused branch and PR.

Branch names: `feature/<issue-number>-task`, `fix/<issue-number>-task`, `refactor/<issue-number>-task`, `docs/<issue-number>-task`, `test/<issue-number>-task`, `chore/<issue-number>-task`.

작업 전 Issue에 목적·담당자·요구사항/AC·범위·의존성을 작성합니다. main 직접 push 대신 별도 브랜치와 PR을 사용합니다. 예: `docs/4-notion-handoff`.

## Issues

Use the task template. Include purpose, tasks, owner (only when agreed), related requirements/design, testable acceptance criteria, scope and dependencies/open questions. Do not invent assignments or deadlines.

Issue titles: `[Feature]`, `[Fix]`, `[Refactor]`, `[Docs]`, `[Test]`, `[Chore]` followed by the concrete task.

## Commits

Proposed final format: `type: description`; types `feat`, `fix`, `refactor`, `docs`, `test`, `chore`. Example: `docs: align current capsule design`. Keep one meaningful change per commit. The transition from bootstrap `[feat]` tags is explicit in this review.

커밋 형식은 노션 초안의 `type: description`으로 통일하는 제안입니다. bootstrap의 `[feat]`와 달라지는 점을 리뷰에서 확인합니다.

## Pull requests and validation

Target main; explain the problem and resulting behavior, link the issue/FR/AC, list validation actually run and limitations, and provide screenshots for UI changes. Use the PR template. A documentation-only PR needs document/link verification, not a fabricated Android build. Mark unavailable build/integration checks as not run with reasons.

PR은 한 목적에 집중합니다. 구현/테스트/리뷰 완료를 혼동하지 않고 실제 실행한 검증만 기록합니다.

## Review and merge

At least one other teammate must review a PR. Authors must not merge without review; AI agents do not merge their own PR unless explicitly directed by a human. Address feedback and request re-review. Before merge, check conflicts and appropriate build/function tests if applicable; preserve clear limitations. Delete the task branch only after human-approved merge.

Notion document completion is a separate rule: all required team LGTM and substantive questions settled. A PR review or written draft is not proof that every product decision is approved. Preserve joint original contents/comments until all participants respond.

Notion 문서 Complete는 모든 필수 리뷰자의 LGTM 및 질문 처리가 필요합니다. PR 최소 1명 리뷰와 별개이며 공동 댓글을 먼저 지우거나 해결 처리하지 않습니다.

## Scope and evidence

Consult [current documents](docs/README.md). Working MVP includes account delivery; backend/vendor choices and open self-send/editing/retry questions remain pending. Use E0 research, E1 isolated spike, E2 integrated app, E3 repeated verification honestly; never promote evidence without running the corresponding checks.
