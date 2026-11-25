# Task handoff: Issue #18 Recheck unlock through the supported foreground recovery path

- **Confirmed owner:** User J (explicitly confirmed by user prompt)
- **Scope / period / different human reviewer:** MVP / 24 November 2026 / User G (reviewer)
- **Issue / PR:** Issue #18 / Task branch `feature/18-foreground-recovery`
- **Branch / base / head:** `feature/18-foreground-recovery` / Base `ed1460ad6435969e43f4faef7e85327342536710` / Head `feature/18-foreground-recovery`
- **State:** IMPLEMENTED_AND_VERIFIED (Shared foreground recovery path implemented, 273 JVM unit tests + lint + assembleDebug passed)
- **Boundary:** `ForegroundRecoveryCoordinator`, `AppDependencyContainer`, `MainActivity`, `ViewModelFactory`, `CapsuleDraftViewModel`, S-04/S-05/S-06 fragments, and `ForegroundRecoveryCoordinatorTest`.
- **Predecessors:**
  - Issue 14 (PR #40, merge `15cf5bfdc39e6827c1a5407198cd65f6e6a7c232`)
  - Issue 15 (PR #41, merge `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff`)
  - Issue 17 (PR #43, merge `ed1460ad6435969e43f4faef7e85327342536710`)
  Ancestry verified on base commit `ed1460ad6435969e43f4faef7e85327342536710`.
- **Contract:** FR-09, NFR-05; AC-07; DD-08; T-24, T-25, T-32; ARC-05, ARC-07 as architectural context.
- **Overlap inspection:** Inspected active branches/PRs; no competing claims or overlapping file modifications for foreground recovery coordinator.
- **Readiness:** All eight readiness gates PASSED (`PREFLIGHT_RECORD_VALID` in `docs/agent-tools/readiness-evidence-issue18.json`).

## Implemented Deliverables

1. **Shared `ForegroundRecoveryCoordinator`:**
   - Created `com.example.timecapsule.data.recovery.ForegroundRecoveryCoordinator` to coordinate and coalesce foreground recovery across app-open, return to foreground (`onResume`), and explicit UI Retry actions.
   - Coalesces overlapping publication recovery for the same `(uid, sessionGeneration)`.
   - Coalesces overlapping recipient unlock checks for the same `(uid, sessionGeneration, capsuleId)`.
   - Coalesces overlapping send outcome reconciliation for the same `(uid, sessionGeneration, capsuleId)`.
   - Removes completed flight entries upon completion (including failures) so subsequent retries acquire fresh server and location evidence.
   - Revalidates session tracker before returning results to callers and discards stale callbacks on account switch or session generation changes.
   - Does not hold strong references to Activity or Fragment instances.
2. **Repository & Session Delegate Usage:**
   - Calls ONLY existing authoritative repository methods: `recoverPublications()`, `reconcile(capsuleId)`, and `verifyUnlock(capsuleId)`.
   - Does NOT duplicate server time, location qualification, Firestore transaction, or publication reconciliation.
   - Exposes `checkForeground(knownCapsuleIds)` as a clean entry point for S-03 refresh and S-07 detail open to be wired in Issue #21 when those screens exist.
3. **Android Lifecycle & Container Integration:**
   - Wired `ForegroundRecoveryCoordinator` into `AppDependencyContainer` and `MainActivity`.
   - `MainActivity.onResume()` triggers `triggerForegroundRecovery()` when user is authenticated.
   - Post-login checks in `MainActivity` (S-01 sign-in success and S-02 register success) trigger `triggerForegroundRecovery()`.
   - `CapsuleDraftViewModel.syncSessionState()` receives latest container repository and recovery coordinator on Fragment `onResume()` / Activity recreation, preventing stale container or session generation references on device rotation.
4. **Deterministic Unit Test Suite:**
   - Added `ForegroundRecoveryCoordinatorTest` covering minimum requirements (a) through (k) with 13 deterministic unit tests.

## Automated Verification Suite

- **273 JVM unit tests PASSED** (`:app:testDebugUnitTest`).
- **Clean debug APK build PASSED** (`:app:assembleDebug`).
- **Clean Android lint PASSED** (`:app:lintDebug`).

## Live UI vs Deferred #21 Actions

- **Live UI actions now:**
  1. App-open / Activity creation in `MainActivity` (session restoration and post-login check).
  2. Return to foreground in `MainActivity.onResume()` and Fragment `onResume()`.
  3. UI Retry / Check publication buttons in `S06ConfirmSendFragment` / `S04ContentDraftFragment` / `CapsuleDraftViewModel`.
  4. Recipient unlock check seam via `ForegroundRecoveryCoordinator.verifyUnlock(capsuleId)`.
- **Pending Issue #21 hookups:**
  1. S-03 Capsule lists refresh on pull-to-refresh or list load (will pass list of received `capsuleId`s into `checkForeground(knownCapsuleIds)`).
  2. S-07 Capsule detail open (will invoke `verifyUnlock(capsuleId)` when user opens a locked capsule detail screen).

## Workspace and Recovery

- Branch: `feature/18-foreground-recovery`
- Base commit: `ed1460ad6435969e43f4faef7e85327342536710`
