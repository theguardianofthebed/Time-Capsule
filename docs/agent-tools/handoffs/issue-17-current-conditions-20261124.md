# Task handoff: Issue #17 Evaluate current time-and-location conditions without a past-visit latch (Final Review Corrections)

- **Confirmed owner:** User J (explicitly confirmed by user prompt)
- **Scope / period / different human reviewer:** MVP / 24 November 2026 / User G (reviewer)
- **Issue / PR:** Issue #17 / Branch pushed (`origin/feature/17-current-conditions`)
- **Branch / base / head:** `feature/17-current-conditions` / Base `93b7409e871f215ea2cfc08642c1c934ddf5ec27` / Head `feature/17-current-conditions`
- **State:** READY_FOR_REVIEW (Final review corrections applied, branch pushed, 260 JVM unit tests + 42 rules tests + 35 Android emulator instrumentation tests passed)
- **Boundary:** Pure `ConditionEvaluator`, live recipient unlock requirements read, pending-write rejection, location-qualified unlock transaction, `DefaultCapsuleRepository` location orchestration, and automated unit/rules/emulator test suite.
- **Predecessors:** Issue 15 (PR #41, merge `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff`) and Issue 16 (PR #42, merge `93b7409e871f215ea2cfc08642c1c934ddf5ec27`). Ancestry verified on base commit `93b7409e871f215ea2cfc08642c1c934ddf5ec27`.
- **Contract:** FR-06, FR-07, NFR-04; ARC-05; DD-05; AC-06; T-19, T-20, T-42.
- **Overlap inspection:** Inspected active branches/PRs; no competing claims or overlapping file modifications for condition evaluation and location unlock verification.
- **Readiness:** All eight readiness gates PASSED (`PREFLIGHT_RECORD_VALID` in `docs/agent-tools/readiness-evidence-issue17.json`).

## Final Review Corrections Applied

1. **Deterministic Android Firebase Integration Tests:**
   - Updated `FirebaseEmulatorUnlockIntegrationTest.kt` so early-time assertions execute with 3600s or 5s future deadlines verified with `assertTrue(System.currentTimeMillis() / 1000 < unlockAt.seconds)` before calling unlock.
   - After-time assertions use 5s bounded deadlines and `waitUntilServerTimeReached(unlockAt.seconds, timeoutMillis = 15000L)` which loops until `System.currentTimeMillis() / 1000 > unlockAt.seconds` with a 15-second timeout.
2. **Clarified Fake Transaction Retry Test Scope:**
   - Refactored `DefaultCapsuleRepositoryUnlockTest.kt` KDoc to clarify that time-advancement during callback execution on a test double verifies callback clock rechecking logic, distinguishing it from SDK-level contention retries.
3. **P0 Snapshot Rejection in `readRecipientUnlockRequirements`:**
   - Updated `DefaultFirestoreAdapter.kt` so `readRecipientUnlockRequirements` rejects the entire unconfirmed or cached snapshot (`hasPendingWrites || isFromCache`), returning `OperationResult.Unavailable("Capsule metadata is unconfirmed or cached.")`.
   - Maintained confirmed-unlock reconciliation in `verifyRecipientUnlock` catch path.
4. **Corrected test12 Assertion:**
   - Renamed `test12_readRecipientUnlockRequirementsSucceedsForConfirmedServerMetadata` in `FirebaseEmulatorUnlockIntegrationTest.kt` to accurately describe its assertion of confirmed server metadata reads.
5. **Live Android Emulator Instrumentation Test Execution:**
   - Executed `gradle_build("app:connectedDebugAndroidTest")` on live `emulator-5554` connected to Firebase Emulators: **35 PASSED, 0 FAILED** (including all 12 tests in `FirebaseEmulatorUnlockIntegrationTest`).
6. **Accurate User J Logs & Pending Understanding:**
   - Updated `agent-log-w3.md` and `agent-log.md` with factual execution details, keeping personal student reflections pending confirmation by User J.

## Automated Verification Suite

- 260 JVM unit tests passed (`:app:testDebugUnitTest`).
- 42 Firebase Firestore & Storage security rules tests passed (`cmd /c npx firebase emulators:exec --project demo-timecapsule "npm test"`).
- 35 Android instrumentation tests passed on live Android emulator `emulator-5554` with Firebase Emulators (`:app:connectedDebugAndroidTest`).
- Clean debug build, AndroidTest APK compile, and lint (`:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`).

## Remaining Work (Separate Manual Walkthrough)

- Perform human manual UI walkthrough on device.
- Teammate PR review by User G and human merge into `main`.

## Workspace and Recovery

- Branch: `feature/17-current-conditions` (pushed to `origin/feature/17-current-conditions`).
- Clean working tree.
