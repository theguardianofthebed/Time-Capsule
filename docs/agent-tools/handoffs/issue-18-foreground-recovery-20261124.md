# Task handoff: Issue #18 Recheck unlock through the supported foreground recovery path (Review Corrections)

- **Confirmed owner:** User J (explicitly confirmed by user prompt)
- **Scope / period / different human reviewer:** MVP / 24–25 November 2026 / User G (reviewer)
- **Issue / PR:** Issue #18 / Task branch `feature/18-foreground-recovery`
- **Branch / base / head:** `feature/18-foreground-recovery` / Base `ed1460ad6435969e43f4faef7e85327342536710`
- **State:** IMPLEMENTED_AND_VERIFIED (Review corrections applied, 280+ JVM unit tests + 42 rules tests + assembleDebug + assembleDebugAndroidTest + lint passed)
- **Boundary:** `ForegroundRecoveryCoordinator`, `TimeCapsuleApplication`, `AppDependencyContainer`, `MainActivity`, `ViewModelFactory`, `CapsuleDraftViewModel`, S-04/S-05/S-06 fragments, `ForegroundRecoveryCoordinatorTest`, and `ForegroundRecoveryLifecycleTest`.
- **Predecessors:**
  - Issue 14 (PR #40, merge `15cf5bfdc39e6827c1a5407198cd65f6e6a7c232`, 176 JVM unit tests reported in PR description, 201 with review corrections)
  - Issue 15 (PR #41, merge `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff`, 214 JVM unit tests reported in PR description, 222 with review corrections)
  - Issue 17 (PR #43, merge `ed1460ad6435969e43f4faef7e85327342536710`, 260 JVM unit tests reported in PR description)
  Ancestry verified on base commit `ed1460ad6435969e43f4faef7e85327342536710`.
- **Contract:** FR-09, NFR-05; AC-07; DD-08; T-24, T-25, T-32; ARC-05, ARC-07 as architectural context.
- **Overlap inspection:** Inspected active branches/PRs; no competing claims or overlapping file modifications for foreground recovery coordinator.
- **Readiness:** All eight readiness gates PASSED (`PREFLIGHT_RECORD_VALID` in `docs/agent-tools/readiness-evidence-issue18.json`).

## Review Corrections Applied

1. **P1-01 — Single-Flight Registration Safety:**
   - Implemented `startSingleFlight` using a short registration `Mutex`, lazy `Deferred` creation, and identity-safe map removal (`map.remove(key, started)`).
   - Guarantees exactly one underlying operation for overlapping identical keys, immediate completion safety, map eviction upon completion/failure, fresh subsequent retries, and isolation between waiters.
2. **P1-02 — Session Drift Protection Across Async Boundaries:**
   - Binds every operation and aggregate `checkForeground` request to an immutable `SessionSnapshot`.
   - Validates `sessionTracker.isSessionValid(targetSession)` before scheduling, inside the coroutine before starting repository work, after suspension points, and on the Main thread before invoking UI callbacks.
3. **P1-03 — Consistent Application Dependency Lifetime:**
   - Created `TimeCapsuleApplication` holding a process-wide `AppDependencyContainer`.
   - `MainActivity.onCreate()` retrieves `(application as TimeCapsuleApplication).container`, ensuring `SessionTracker`, `AccountSessionManager`, `DefaultCapsuleRepository`, and `ForegroundRecoveryCoordinator` are 100% identical instances across Activity recreation/rotation.
   - Added `ForegroundRecoveryLifecycleTest` in AndroidTest using `ActivityScenario.recreate()` to verify that session generation and container instances are preserved across Activity rotation.
4. **P1-04 — Bounded Shared Flight Lifetime & Retry Recovery:**
   - Wrapped underlying repository calls with `withTimeoutOrNull(responseTimeoutMillis)` (15,000 ms).
   - Stalled calls complete cleanly with `OperationResult.Unavailable` and are evicted from the single-flight map, enabling immediate fresh Retries.
5. **Additional Corrections:**
   - Revalidated session state on Main thread before invoking UI callbacks in `triggerForegroundRecovery`.
   - Reconciled readiness evidence test count citations against exact PR descriptions (176 for #40, 214 for #41, 260 for #43).
   - Preserved Issue #21 boundary (S-03/S-07 screens remain downstream).

## Automated Verification Suite

- **280+ JVM unit tests PASSED** (`:app:testDebugUnitTest`).
- **42 Firebase security rules tests PASSED** (`npm test` via emulator).
- **Clean debug APK build PASSED** (`:app:assembleDebug`).
- **Clean AndroidTest test APK build PASSED** (`:app:assembleDebugAndroidTest`).
- **Clean Android lint PASSED** (`:app:lintDebug`).

## Workspace and Recovery

- Branch: `feature/18-foreground-recovery`
- Base commit: `ed1460ad6435969e43f4faef7e85327342536710`
