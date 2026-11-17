# Issue #11 takeover and correction handoff — 2026-11-17

- **Issue / PR:** https://github.com/theguardianofthebed/Time-Capsule/issues/11 ; https://github.com/theguardianofthebed/Time-Capsule/pull/35
- **Scope:** MVP capsule metadata, protected-content and Storage authorization rules, security-rule emulator fixtures, documentation corrections. No production deployment or Android application feature implementation.
- **Original implementation owner:** User F (PR #35 author, November 12–13 work as reported in PR description). Preserved all original commits and authorship.
- **Correction owner:** User J, explicitly requested by the project member on November 17. Both are recorded as GitHub Issue #11 assignees.
- **Independent reviewer:** User G or User F (pending independent teammate review before merge). User J will not approve their own PR.
- **Target period:** November 17–20, 2026.
- **Selected Base:** `main` at `2524ae5070477a320e30257a94f23d100e332e98` (containing merged #9/PR #29, #10/PR #33, #12/PR #36, and #37/PR #38).
- **Branch:** `feature/11-capsule-rules`.
- **References:** Atlas revision 55 read November 17 (ARC-02, ARC-03, ARC-04, ARC-05, AC-12, DD-16, DD-17, DD-23); repository `docs/agent-tools/CURRENT_CONTRACT.md`, `docs/agent-tools/READINESS.md`, `docs/agent-tools/readiness-evidence-issue11.json`.

## Implementation and Verification Summary

1. **Reconciliation:** Reconciled current `main` (`2524ae5070477a320e30257a94f23d100e332e98`) into `feature/11-capsule-rules` without losing existing work or User F's original commits.
2. **Readiness Preflight:** Generated `docs/agent-tools/readiness-evidence-issue11.json` and ran `py tools/check_readiness.py --issue 11 --evidence docs/agent-tools/readiness-evidence-issue11.json`, returning `PREFLIGHT_RECORD_VALID` with 0 blockers.
3. **Recipient Directory Access:** Restored `allow get: if signedIn();` on `recipientDirectory/{uid}` to permit exact-UID recipient lookup by authenticated senders while maintaining `allow list: if false;` to block directory listing/enumeration and owner-only profile creation/updates.
4. **One-Way Server-Timestamp Unlock Contract:** Updated `canRecipientUnlock()` in `firestore.rules` to enforce `request.resource.data.unlockedAt == request.time` alongside `request.resource.data.unlocked == true`, restricting affected fields during unlock strictly to `['unlocked', 'unlockedAt']`. Denies client-supplied timestamps, missing timestamps, early unlocks, sender unlocks, relocking, re-unlocking, or changing metadata during unlock.
5. **Photo Prepublication Reconciliation & Immutability:** Updated `storage.rules` to allow `create, update` and `read` for prepublication photo uploads/retries by the authenticated sender (`!capsuleExists(capsuleId)`). Once published (`capsuleExists(capsuleId)` is true), photos become strictly immutable (updates/deletes denied; reads governed strictly by participant authorization and unlock state).
6. **Firebase Emulator Security Tests:** Extended `tests/capsule.rules.test.js` from 29 to 36 test cases covering recipient directory exact get/list denial, unlock `unlockedAt` server-timestamp enforcement, early unlock denial, relock denial, prepublication photo upload/retry/read, photo immutability after publication, and signed-out/unauthorized access.
   - Command: `npx firebase emulators:exec --project demo-timecapsule "npm test"`
   - Result: 36 passed / 36 total (100% pass rate).
   - Android JVM tests command: `./gradlew app:testDebugUnitTest` -> Passed.

## Readiness Gate Result

- **Manifest:** `py tools/check_readiness.py --validate-manifest` -> `MANIFEST_VALID`
- **Preflight:** `py tools/check_readiness.py --issue 11 --evidence docs/agent-tools/readiness-evidence-issue11.json` -> `PREFLIGHT_RECORD_VALID`

## Next Actions

1. Keep PR #35 in **Draft** state until independent review.
2. Request independent review from User G or User F.
3. Teammate review and human merge into `main`.
