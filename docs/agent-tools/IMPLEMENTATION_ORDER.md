# Implementation order and issue readiness

Choose work by its **prerequisites**, then self-assign it. All implementation issues in this guide are MVP work. User F, User G, and User J may choose any ready issue; their usual review strengths do not create exclusive coding areas.

The [Views and eight-screen decision](UI_VIEWS_UPDATE_2026-11-07.md) and [DESIGN.md](../../DESIGN.md) govern the UI. [#25](https://github.com/theguardianofthebed/Time-Capsule/issues/25) adopts the tested starter. Its bounded starter implementation is now merged through PR #27/#26 with recorded build/binding/launch evidence. This does not complete downstream screen behavior or backend acceptance.

## Current readiness snapshot

Checked after the external merge at **00:56 KST on 10 November 2026**: #25 is **DONE** for the starter boundary; #9 is **MERGED / VERIFICATION PENDING**. PR #29 merged as `c60d866cdc9794af97db129c946b66711270f6b9` and is present in main. Its newer log records 25 unit tests and cache/configuration corrections, while its description remains stale. Actual emulator connectivity/rules execution is unrecorded; review the applicability of the recorded bounded unit/configuration checks and classify the unspecified follow-up. Full account/capsule rules acceptance remains downstream, not a new #9 gate. #10 stays **BLOCKED** pending a fresh bounded-evidence and owner/reviewer preflight, rather than on an unmerged PR. #11–#23 additionally require unfinished predecessor features.

A post-review comment reports an additional problem to be tracked in a new issue, but does not specify the defect, severity or a verification exception ([comment](https://github.com/theguardianofthebed/Time-Capsule/pull/29#issuecomment-6744585480)). Confirm that follow-up scope before relying on the foundation; no specific runtime failure is asserted here.

Use the [three visual roadmap views](ISSUE_ROADMAP.md) to see what each issue enables. [dependencies.json](dependencies.json) preserves the 52 direct prerequisite edges in the current issue bodies. It is a dated status snapshot, not authorization to code. Follow the [repeatable readiness preflight](WORKFLOW.md) before claiming or resuming work.

## Before implementing an issue

1. Read the issue's `Blocked by` list and the table below. Inspect the **current** prerequisite issues, their implementation PRs, and the branch you will use.
2. For every prerequisite, verify that its required implementation is merged into your selected base, its relevant checks have recorded results, and that implementation has not been reverted or left with an unresolved failure that blocks your task. Record the PR/merge commit and evidence in the issue or task handoff. A closed issue, draft PR, local ZIP, checkbox, or planned test alone is insufficient.
3. Confirm that the current Views/screen contract and this order guide are approved and present in the selected base. The documentation PR may be reviewed while stacked on the existing documentation work; application work must use the base selected by the team after that review.
4. Self-assign the issue using your own GitHub account and identify your existing contributor label, **User F / User G / User J**. Choose a different teammate to review. Do not guess another member's handle or transfer their task.
5. Inspect open/merged PRs, branches and handoffs for an existing claim or overlapping components. Record the active branch, related PR, affected files/components, bounded deliverable, current status and remaining work. Resume or explicitly transfer existing work rather than starting a competing implementation.
6. Record an actual work period and verification plan. The periods below are dependency-based planning windows, not invented calendar promises. Keep the current course milestone in view.

**If a gate fails:** report `BLOCKED`, name the missing prerequisite/evidence, and choose another ready issue if one exists. Reading, reviewing, investigating, and drafting a plan can happen before the gate passes. Do not implement a blocked issue by silently substituting mocks, weakening the dependency, or completing its prerequisite inside an unrelated PR. A maintainer may explicitly split or change a dependency; record the changed scope and contract first.

Use the repository's existing branch, agent-environment, review, and logging rules. This guide adds implementation-order checks; it does not replace those rules.

## Required order

The table lists direct prerequisites. Shared upstream dependencies also apply. Issues on different rows may run in parallel once their own listed gates pass; the window is an earliest planning position, not an additional hidden dependency.

| Window | Issue and deliverable | Blocked by — required before implementation | Owner / period |
|---|---|---|---|
| 0 | [#25](https://github.com/theguardianofthebed/Time-Capsule/issues/25) — adopt and verify Views/XML + ViewBinding starter | Approved Views documentation in the selected base; clean-file comparison and local package verification | User G; starter work recorded 7–8 November. User J contributed the recorded theme correction. DONE for the starter only. |
| 1 | [#9](https://github.com/theguardianofthebed/Time-Capsule/issues/9) — shared Android/Firebase configuration, interfaces, and test seams | #25 | User J; #29 merged 10 November, 8–9 November implementation recorded. Verification follow-up period must be recorded. |
| 2 | [#10](https://github.com/theguardianofthebed/Time-Capsule/issues/10) — accounts, profile repair, and exact recipient confirmation | #9 | Unassigned; first feature session after shared interfaces/configuration. |
| 3 | [#11](https://github.com/theguardianofthebed/Time-Capsule/issues/11) — capsule schema and matching access rules | #9, #10 | Unassigned; may run alongside #12 after the same gates. |
| 3 | [#12](https://github.com/theguardianofthebed/Time-Capsule/issues/12) — S-04 → S-05 → S-06 draft and review flow | #9, #10 | Unassigned; may run alongside #11 after the same gates. |
| 4 | [#13](https://github.com/theguardianofthebed/Time-Capsule/issues/13) — one original photo, validation, and retry copy | #9, #11, #12 | Unassigned; after schema and composition, before publication. |
| 4 | [#16](https://github.com/theguardianofthebed/Time-Capsule/issues/16) — map target and fresh foreground location adapter | #9, #12 | Unassigned; may run alongside the photo/publication path. |
| 5 | [#14](https://github.com/theguardianofthebed/Time-Capsule/issues/14) — immutable publication and uncertain-result recovery | #10, #11, #12, #13 | Unassigned; after recipient/schema/draft/media integration. |
| 6 | [#15](https://github.com/theguardianofthebed/Time-Capsule/issues/15) — UTC schedules and online one-way unlock | #11, #14 | Unassigned; complete and verify the focused time-only path next. |
| 7 | [#17](https://github.com/theguardianofthebed/Time-Capsule/issues/17) — current time/location AND evaluation | #15, #16 | Unassigned; after both time and fresh-location inputs exist. |
| 8 | [#18](https://github.com/theguardianofthebed/Time-Capsule/issues/18) — shared foreground recovery path | #14, #15, #17 | Unassigned; after send recovery and supported unlock modes. |
| 9 | [#19](https://github.com/theguardianofthebed/Time-Capsule/issues/19) — account-safe local notifications | #18 | Unassigned; after confirmed first-unlock results are available. |
| 10 | [#20](https://github.com/theguardianofthebed/Time-Capsule/issues/20) — retained-content isolation and account cleanup | #10, #11, #14, #18, #19 | Unassigned; after the affected account/send/verification/notification adapters exist. |
| 11 | [#21](https://github.com/theguardianofthebed/Time-Capsule/issues/21) — S-03/S-07 lists, protected detail, and permanent re-view | #10, #11, #14, #15, #20 | Unassigned; after authorization and retained-content gates. |
| 12 | [#22](https://github.com/theguardianofthebed/Time-Capsule/issues/22) — eight-screen visual/accessibility and recovery consistency | #10, #12, #16, #19, #21 | Unassigned; after the affected screen behaviors are integrated. |
| 13 | [#23](https://github.com/theguardianofthebed/Time-Capsule/issues/23) — integrated acceptance evidence | Every issue #9–#22; #25 is required transitively | Unassigned; complete before claiming integrated MVP acceptance. |

Issue #16 no longer waits for #15: selecting a target and obtaining a fresh sample do not depend on the time transaction. Its adapter still cannot grant unlock. Issue #17 joins the verified time and location paths. No other feature dependency is removed by that scheduling choice.

## Screen ownership by issue

This table defines implementation boundaries, not permanent student assignments. Every screen uses XML layouts and ViewBinding.

| Screen | Primary implementation issue | Shared work |
|---|---|---|
| S-01 Sign in | #10 | #20 session cleanup; #22 consistency |
| S-02 Register / profile-repair variant | #10 | #11 preserves the directory rules; #22 consistency |
| S-03 Capsule lists | #21 | #18 refresh/recovery; #22 consistency |
| S-04 Content | #12 | #13 photo validation/retention; #22 consistency |
| S-05 Recipient and locks | #12 | #10 identity confirmation; #15 time; #16 map/location; #17 current AND |
| S-06 Confirm send and outcome variants | #12 | #13 media; #14 frozen publication/reconciliation; #22 consistency |
| S-07 Capsule detail and access variants | #21 | #15/#17 unlock; #18 recovery; #20 authorized retention; #19 notification entry |
| S-08 Account and permissions | #10 | #16 location guidance; #19 notification permission; #20 cleanup; #22 consistency |

#25 supplies layout/binding placeholders. #22 does not postpone choosing the UI framework; it checks the final shared visual and accessibility behavior after the feature issues own their working screens.

## Bounded handoffs keep the order executable

A prerequisite closes only when its **own bounded deliverable** is implemented and verified. Some existing AC/test records cover several features; their full integrated obligations remain outstanding until the required downstream features are tested. Record the completed and deferred portions separately instead of creating a circular dependency or declaring a full AC passed early.

| Issue | Deliverable that can unblock its successors | Later integration still required |
|---|---|---|
| #10 | Auth, profile repair, exact lookup/confirmation, and the minimal `recipientDirectory` rules/emulator checks these functions need. | #11 extends the same rules for capsules/content/Storage and preserves directory tests; #20/#23 check full protected account cleanup. |
| #11 | Capsule/content/Storage rules verified with synthetic emulator fixtures. | #14/#15/#17 supply the real publication/unlock clients. Rules fixtures do not claim those clients exist. |
| #12 | XML/ViewBinding draft forms, three-screen navigation, validation and typed handoffs. | #13 photo, #14 publication, #15 time, #16 map/location. Normal app actions stay unavailable until their real adapter exists; tests may inject controlled outcomes without claiming live success. |
| #13 | Actual photo acquisition, original private copy and authenticated upload adapter verified against the agreed frozen-operation interface. | #14/#23 exercise complete ambiguous publication and operation-dependent cleanup. |
| #14 | One-ID atomic publication/reconciliation verified using backend-valid frozen fixtures and failure injection. | #15/#16 supply the complete date/map input behavior; #21 finishes list/detail navigation. |
| #15 | UTC/time-only transaction and denial/monotonic behavior verified against the merged rules/publication. | LOCATION/BOTH remain fail-closed until #17 integrates qualified location input. |
| #16 | Map target and fresh sample adapter verified independently; it cannot authorize unlock. | #17 integrates the current sample with time/AND authorization. |
| #18/#19/#20 | Foreground hooks, notification requests and account cleanup verified against the shared interfaces and existing adapters. | #21/#23 repeat the behavior through completed S-03/S-07 views and notification entry. |

Tests for a shared AC are not automatically all required to close every issue that mentions it. Each issue's handoff states its bounded evidence and the named downstream coverage. Required safety behavior for that deliverable must still pass; unavailable dependencies must not be replaced by successful-looking production mocks.

## Verification is part of every feature

#9 creates replaceable adapter boundaries and the basic verification setup. Each feature PR adds/runs the checks for its own behavior before downstream work relies on it. In particular, #15 verifies the time-only publication/unlock path without waiting for the location implementation, and #17 verifies the combined condition after both inputs exist.

#23 collects and runs the complete integrated suite after its prerequisites. Its existence does not postpone feature testing. A wireframe/placeholder walk-through, successful APK build, or one test passing cannot close an entire application AC. Use [JOURNEYS.md](JOURNEYS.md), retain the exact test IDs, and record any `NOT RUN`, `BLOCKED`, or failed checks.

## Readiness record

Copy this compact record into a task handoff or issue update when claiming work:

```text
Issue:
Owner (User F/G/J) and self-assigned GitHub account:
Different human reviewer:
Active branch and related PR:
Components/files and bounded deliverable:
Current status and remaining work:
Overlap check (issues, branches, PRs, handoffs):
Scope: MVP
Work period:
Selected base branch and commit:
Contract documentation PR/commit present in base:
Blocked by:
For each prerequisite: issue -> merged PR/commit -> verification evidence
Readiness: READY or BLOCKED, with the reason
Planned checks:
```

Recheck readiness immediately before implementation and before the PR is considered mergeable. A branch changing or a prerequisite being reverted can invalidate an earlier check. Update the table and issue bodies together when an authorized dependency changes.
