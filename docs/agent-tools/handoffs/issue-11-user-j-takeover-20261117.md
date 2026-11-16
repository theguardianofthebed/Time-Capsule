# Issue #11 takeover and correction handoff — 2026-11-17

- **Issue / PR:** https://github.com/theguardianofthebed/Time-Capsule/issues/11 ; https://github.com/theguardianofthebed/Time-Capsule/pull/35
- **Scope:** MVP capsule metadata, protected-content and Storage authorization rules, security-rule emulator fixtures, documentation corrections. No production deployment or Android application feature implementation.
- **Original implementation owner:** User F (PR #35 author, November 12–13 work as reported in PR description). Preserve all original commits and authorship.
- **Correction owner:** User J, explicitly requested by the project member on November 17. The project member reports User F acknowledged the transfer via the team group chat; this chat itself was not inspected. Both are recorded as GitHub Issue #11 assignees at the transition checkpoint.
- **Independent review:** User G or User F, to be confirmed by the team before final review. User J must not approve their own correction.
- **Target period:** November 17–20, 2026 (planning target, not a completed or team-guaranteed deadline).
- **Base:** `main` contained #9/PR #29 (`c60d866cdc9794af97db129c946b66711270f6b9`), #10/PR #33 (`74aa763076801944a33897b2f67c36d3afb27aa9`), and #12/PR #36 (`26d78cc897ec2bd6853288be9c66838dd3bf63d2`) at the inspection.
- **PR head before handoff documentation:** `35f793b9864a45f0174aaa26baea04c32cda9a33`; branch `feature/11-capsule-rules`. The branch was 9 commits ahead and 6 behind current `main`. Refresh exact heads before editing and merge `main` into the feature branch only after inspecting the worktree and conflicts; do not rebase/push synthetic ancestry.
- **References:** Atlas revision 55 read November 17 (ARC-02, ARC-03, ARC-04, ARC-05, AC-12, DD-16, DD-17, DD-23 and related tests); repository `docs/agent-tools/CURRENT_CONTRACT.md`, `docs/agent-tools/READINESS.md`, Issue #11 and its downstream handoffs. The Atlas has not been edited.

## Current verified repository observations

At pre-correction PR head, `firestore.rules` has `recipientDirectory/{uid}` `allow get: if signedIn() && request.auth.uid == uid`. This denies authenticated A's exact get of B, contrary to the merged Issue #10 lookup and ARC-03. `canRecipientUnlock()` permits only TIME and changes only `unlocked` without `unlockedAt`. Current contract requires a one-way server-timestamped transition. `storage.rules` now permits an initial sender upload before Firestore publication, restricts JPEG/PNG to <=5 MiB, denies overwrites and allows protected read after publication. Prepublication sender retry / lost-upload-ACK behavior is not yet covered, and cross-product orphan cleanup remains best-effort. Security test source has 29 `it(...)` cases, but this inspection did not execute them. The PR description still reports its original 13 tests and contains placeholders. No hosted CI runs were observed for this head.

## Required changes (MVP)

1. **Issue #10 regression:** Restore signed-in exact `recipientDirectory/{uid}` document reads for other UIDs; keep `allow list: if false` and owner-only writes. Add sender-A-gets-recipient-B and directory-list-denied regression tests.
2. **Issue #15/17 compatibility:** Align `unlockedAt` with Atlas's `unlocked=false -> true`, server-time equality, recipient-only and allowed-key conditions; deny early/repeated/relock/forged modifications. Handle TIME now and explicitly document LOCATION/BOTH verification behavior and trust boundary without silently altering the approved contract. Reject any plan that allows LOCATION/BOTH to be unlocked without a fresh official-client current-location decision; raise a design conflict if rules cannot securely express this trust boundary. Do not duplicate Issue #15 Android transaction orchestration.
3. **Photo recovery compatibility with #13/#14:** Preserve upload ACK-before-Firestore-batch semantics, same stable object path and no replace/delete after publication. Verify sender ability to reconcile a prepublication object after a lost response without allowing locked self-send protected reads. If this needs a rule-level exception, test only that bounded exception and record orphan/upload limits; leave the actual send journal, reconciliation and retry UI to #14.
4. **Security tests:** Extend Firestore/Storage emulator fixtures for cross-user directory exact get; unauthorized list; signed-out access; TIME before/at deadline; `unlockedAt` timestamp/allowed-key enforcement; unlocked A->A behavior; forged owner; one-sided/mismatched pair and digest; sender/recipient queries; photo path, MIME, >5-MiB and immutable upload/read restrictions. Separate use of rule-disabled seed fixtures from actual authorized creates. Do not claim client E2 acceptance from synthetic fixtures.
5. **PR documentation:** Replace stale 13-test count, fill issue link, real head SHA, readiness result, test command/environment/date, author/reviewer, handoff and agent-log links; list `firebase.json`/`firestore.indexes.json` as reviewed-not-modified if unchanged. Record warnings and downstream work separately.
6. **Integration:** Update this branch against current `main` (including #12). Check overlap in `.gitignore` and preserve #12 work; never overwrite unrelated changes.

## Readiness and execution gate

**Status at handoff: BLOCKED for new application/security implementation until current eight-gate evidence is recorded and validated.** The user-confirmed owner transfer clears the ownership-agreement question, but does not substitute for a fresh `docs/agent-tools/READINESS.md` manifest/validator. Record a factual `docs/agent-tools/readiness-evidence-issue11.json` against selected current base, predecessor SHAs, applicable tests, overlap inspection and reviewer. Run:
```bash
python3 tools/check_readiness.py --validate-manifest
python3 tools/check_readiness.py --issue 11 --evidence docs/agent-tools/readiness-evidence-issue11.json
```
Keep `PR #35` as Draft until updated implementation, evidence and independent review. The repository's `AGENTS.md` requires Android Studio Agent Mode for project coding; ChatGPT/GitHub document preparation is not a substitute for that environment. Any command not executed must be marked NOT RUN.

## Next action

**User J**, during the November 17–20 planning window: open the existing checkout in Android Studio Agent Mode, inspect clean worktree and current `main`, reconcile `main` safely into `feature/11-capsule-rules`, record and run the eight-gate readiness preflight and then implement the listed Issue #11 bounded corrections. Execute Firebase emulator tests, inspect results, and request independent review before human merge. No code, test, or merge success is asserted by this handoff.
