# Task handoff: Issue #17 Evaluate current time-and-location conditions without a past-visit latch

- **Confirmed owner:** User J (explicitly confirmed by user prompt)
- **Scope / period / different human reviewer:** MVP / 24 November 2026 / User G (reviewer)
- **Issue / PR:** Issue #17 / PR pending
- **Branch / base / head:** `feature/17-current-conditions` / Base `93b7409e871f215ea2cfc08642c1c934ddf5ec27` / Head `feature/17-current-conditions`
- **State:** IN_PROGRESS (Implementation complete, verification passed, pending draft PR and teammate review)
- **Boundary:** Pure `ConditionEvaluator`, live recipient unlock requirements read, current-location verification in `DefaultFirestoreAdapter` transaction, `DefaultCapsuleRepository` location orchestration, and automated unit/rules test suite.
- **Predecessors:** Issue 15 (PR #41, merge `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff`) and Issue 16 (PR #42, merge `93b7409e871f215ea2cfc08642c1c934ddf5ec27`). Ancestry verified on base commit `93b7409e871f215ea2cfc08642c1c934ddf5ec27`.
- **Contract:** FR-06, FR-07, NFR-04, ARC-05, DD-05, AC-06, T-19, T-20, T-42.
- **Overlap inspection:** Inspected active branches/PRs; no competing claims or overlapping file modifications for condition evaluation and location unlock verification.
- **Readiness:** All eight readiness gates PASSED (`PREFLIGHT_RECORD_VALID` in `docs/agent-tools/readiness-evidence-issue17.json`).

## Implementation Summary

1. **Pure Domain Condition Evaluator (`ConditionEvaluator.kt`):**
   - Created pure JVM `ConditionEvaluator` evaluating client-side conditions for `TIME`, `LOCATION`, and `TIME_AND_LOCATION` (BOTH) modes.
   - Reuses `LocationQualifier.qualify` to evaluate sample age (0..60s), accuracy (<=100m), and target distance (<=150m).
   - Never remembers previous truth or persists `locationOK` / `timeOK` flags as future authorization.
2. **Server-Authoritative Recipient Unlock Requirements (`RecipientUnlockRequirements.kt`, `FirestoreAdapter.kt`, `DefaultFirestoreAdapter.kt`):**
   - Added `readRecipientUnlockRequirements(capsuleId, recipientUid)` doing an authoritative `Source.SERVER` read of metadata.
   - Returns lock mode, unlock target, and existing unlocked status. If already unlocked (`unlocked == true`), skips GPS acquisition completely.
3. **Atomic Location & Time Transaction (`DefaultFirestoreAdapter.verifyRecipientUnlock`):**
   - Extended `verifyRecipientUnlock` transaction to handle `LOCATION`, `BOTH`, and `TIME_AND_LOCATION` lock modes inside `runTransaction`.
   - Evaluates `LocationQualifier.qualify` using fresh `clockNanosSupplier()` on EVERY transaction retry execution.
   - Rejects stale samples (>60s), poor accuracy (>100m), or distance outside 150m before any update.
   - Updates `unlocked = true` and `unlockedAt = FieldValue.serverTimestamp()` inside the transaction. Server rules enforce `request.time >= unlockAt` for TIME and BOTH modes.
4. **Repository Location Orchestration (`DefaultCapsuleRepository.verifyUnlock`):**
   - Injected optional `locationAdapter` and `clockNanosSupplier` into `DefaultCapsuleRepository`.
   - Evaluates session validity across suspension points, requests ephemeral foreground location fix for LOCATION/BOTH modes, checks permissions/services, and submits to Firestore adapter.
5. **Automated Verification:**
   - 254 JVM unit tests passed (`:app:testDebugUnitTest`).
   - 42 Firebase Firestore & Storage security rules tests passed (`npx firebase emulators:exec --project demo-timecapsule "npm test"`).
   - Clean debug build, test APK compile, and lint (`:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`).

## Remaining work

- Commit and push `feature/17-current-conditions` branch.
- Open Draft PR on GitHub (`Refs #17`).
- Perform physical device or Google Play emulator walkthrough.
- Teammate PR review by User G and human merge into `main`.

## Workspace and recovery

- Branch: `feature/17-current-conditions`
- Clean working tree with untracked evidence and handoff files in `docs/agent-tools/`.

## Next action

Update User J's agent logs (`agent-log-w3.md` and `agent-log.md`), commit implementation files, and open Draft PR.
