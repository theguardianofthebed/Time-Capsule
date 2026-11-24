# Task handoff: Issue #17 Evaluate current time-and-location conditions without a past-visit latch (Post-Review Corrections)

- **Confirmed owner:** User J (explicitly confirmed by user prompt)
- **Scope / period / different human reviewer:** MVP / 24 November 2026 / User G (reviewer)
- **Issue / PR:** Issue #17 / Branch pushed (`origin/feature/17-current-conditions`)
- **Branch / base / head:** `feature/17-current-conditions` / Base `93b7409e871f215ea2cfc08642c1c934ddf5ec27` / Head `feature/17-current-conditions`
- **State:** READY_FOR_REVIEW (Post-review corrections P0/P1/P2 applied, branch pushed, verification passed)
- **Boundary:** Pure `ConditionEvaluator`, live recipient unlock requirements read, pending-write rejection, location-qualified unlock transaction, `DefaultCapsuleRepository` location orchestration, and automated unit/rules/emulator test suite.
- **Predecessors:** Issue 15 (PR #41, merge `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff`) and Issue 16 (PR #42, merge `93b7409e871f215ea2cfc08642c1c934ddf5ec27`). Ancestry verified on base commit `93b7409e871f215ea2cfc08642c1c934ddf5ec27`.
- **Contract:** FR-06, FR-07, NFR-04, ARC-05, DD-05, AC-06, T-19, T-20, T-42.
- **Overlap inspection:** Inspected active branches/PRs; no competing claims or overlapping file modifications for condition evaluation and location unlock verification.
- **Readiness:** All eight readiness gates PASSED (`PREFLIGHT_RECORD_VALID` in `docs/agent-tools/readiness-evidence-issue17.json`).

## Post-Review Corrections Applied

1. **P0 — Prevent False Server-Confirmed Unlock:**
   - Updated `readRecipientUnlockRequirements` and transaction failure reconciliation path in `DefaultFirestoreAdapter.kt` to check `!doc.metadata.hasPendingWrites() && !doc.metadata.isFromCache`.
   - Rejects unconfirmed local mutations and cached state so `unlocked == true` is accepted strictly when server confirmation is established.
   - Added regression test `verifyUnlock rejects unacknowledged local update with pending writes` in `DefaultCapsuleRepositoryUnlockTest.kt`.
2. **P1 — Validate Location Availability Throughout the Attempt:**
   - Updated `DefaultCapsuleRepository.verifyUnlock` to recheck `isFineLocationGranted()` AND `isLocationAvailable()` both before and after sample acquisition, prior to transaction submission.
   - Re-evaluates sample freshness with fresh `clockNanosSupplier()` on every transaction retry callback without performing GPS inside the callback.
   - Added unit tests for permission revocation mid-flight and location service disablement mid-flight.
3. **P1 — Remove Wall-Clock Fallback:**
   - Removed `System.currentTimeMillis() * 1_000_000L` wall-clock fallback from `DefaultCapsuleRepository.kt` and `FirestoreAdapter.kt`.
   - Uses `android.os.SystemClock.elapsedRealtimeNanos()` in production and deterministic injected clock suppliers in tests.
4. **P1 — Add Missing Automated Verification:**
   - Extended `DefaultCapsuleRepositoryUnlockTest.kt` (260 unit tests passed) to cover pending-write rejection, permission/service revocation mid-flight, sample expiration during retry, malformed target rejection, lost response reconciliation, and session races.
   - Extended `FirebaseEmulatorUnlockIntegrationTest.kt` (42 security rules tests passed) to cover real LOCATION-only unlock, real BOTH mode truth table combinations (`F/F`, `F/T`, `T/F`, `T/T`), prior visit departure before deadline, and staying inside across deadline without re-entry (T-20).
5. **P2 — Focused Cleanup:**
   - Separated location eligibility check in `ConditionEvaluator.kt` and removed `STALE_SAMPLE` as a time failure representation.
   - Rejects malformed backend location records missing `radiusM` or with `radiusM != 150.0`.

## Automated Verification Suite

- 260 JVM unit tests passed (`:app:testDebugUnitTest`).
- 42 Firebase Firestore & Storage security rules tests passed (`cmd /c npx firebase emulators:exec --project demo-timecapsule "npm test"`).
- Clean debug build, AndroidTest APK compile, and lint (`:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`).
- Android instrumentation test execution: **NOT RUN** (0 connected ADB devices; test code compiles cleanly).

## Remaining work

- Teammate PR review by User G and human merge into `main`.

## Workspace and recovery

- Branch: `feature/17-current-conditions` (pushed to `origin/feature/17-current-conditions`).
- Clean working tree.
