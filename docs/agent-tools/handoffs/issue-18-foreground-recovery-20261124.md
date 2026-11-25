# Task handoff: Issue #18 Recheck unlock through the supported foreground recovery path (Complete Session-Binding Fix)

- **Confirmed owner:** User J (explicitly confirmed by user prompt)
- **Scope / period / different human reviewer:** MVP / 24–25 November 2026 / User G (reviewer)
- **Issue / PR:** Issue #18 / Task branch `feature/18-foreground-recovery`
- **Branch / base / head:** `feature/18-foreground-recovery` / Base `ed1460ad6435969e43f4faef7e85327342536710`
- **State:** IMPLEMENTED_AND_VERIFIED (Complete session-binding fix applied across coordinator, repository, and publication layers; 284 JVM unit tests + 42 rules tests + assembleDebug + assembleDebugAndroidTest + lint passed)
- **Boundary:** `ForegroundRecoveryCoordinator`, `CapsulePublicationCoordinator`, `CapsuleRepository`, `DefaultCapsuleRepository`, `TimeCapsuleApplication`, `AppDependencyContainer`, `MainActivity`, `ViewModelFactory`, `CapsuleDraftViewModel`, `ForegroundRecoveryCoordinatorTest`, and `ForegroundRecoveryLifecycleTest`.
- **Predecessors:**
  - Issue 14 (PR #40, merge `15cf5bfdc39e6827c1a5407198cd65f6e6a7c232`, 176 JVM unit tests reported in PR description, 201 with review corrections)
  - Issue 15 (PR #41, merge `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff`, 214 JVM unit tests reported in PR description, 222 with review corrections)
  - Issue 17 (PR #43, merge `ed1460ad6435969e43f4faef7e85327342536710`, 260 JVM unit tests reported in PR description)
  Ancestry verified on base commit `ed1460ad6435969e43f4faef7e85327342536710`.
- **Contract:** FR-09, NFR-05; AC-07; DD-08; T-24, T-25, T-32; ARC-05, ARC-07 as architectural context.
- **Overlap inspection:** Inspected active branches/PRs; no competing claims or overlapping file modifications for foreground recovery coordinator.
- **Readiness:** All eight readiness gates PASSED (`PREFLIGHT_RECORD_VALID` in `docs/agent-tools/readiness-evidence-issue18.json`).

## Session-Binding & Firebase Initialization Review Corrections

1. **P1-05 — Firebase Initialization Order & Application Container Startup:**
   - Updated `TimeCapsuleApplication.initializeFirebaseAndContainer()` to initialize Firebase configuration (`FirebaseConfig.setupDebugEmulatorConnection` in DEBUG) **BEFORE** creating operational adapters or accessing `FirebaseStorage`, `FirebaseFirestore`, or `FirebaseAuth`.
   - In credential-free DEBUG environments, demo options are initialized and local emulator endpoints are bound first.
   - `getMemoryOnlyFirestore` applies memory-only cache settings (`FirestoreCachePolicy.applyMemoryOnlyCache`) before any Firestore instance operations occur.
   - If Firebase initialization fails, `container` remains `null` (fails closed) without throwing startup crashes. `MainActivity.onCreate()` detects `app.container == null` and displays fail-closed error UI (`tvInitError`).
   - Added unit test `TimeCapsuleApplicationTest.kt` asserting that uninitialized Firebase fails closed without crashing and leaves `container == null`.

2. **P1-02 — Complete Session Binding Across Callers & Publication Layer:**
   - Updated `ForegroundRecoveryCoordinator` to pass `targetSession` explicitly to `repository.recoverPublications(targetSession)`, `repository.verifyUnlock(cleanId, targetSession)`, and `repository.reconcile(cleanId, targetSession)`.
   - Added `targetSession: SessionSnapshot` parameter to `checkForeground(knownCapsuleIds, targetSession)` and updated `triggerForegroundRecovery()` to pass its originally captured session snapshot.
   - Updated `CapsuleRepository` and `DefaultCapsuleRepository` to require and enforce `targetSession` across all state-mutating and recovery operations.
   - Updated `CapsulePublicationCoordinator` (`recoverPublications`, `reconcile`, `publish`, `retry`, `abandonForNewDraft`) to accept `targetSession` and validate `session() == targetSession` across dispatcher transitions, journal reads, and network calls.

3. **P1-01 — Single-Flight Registration Safety:**
   - Implemented `startSingleFlight` using a short registration `Mutex`, lazy `Deferred` creation, and identity-safe map removal (`map.remove(key, started)`).
   - Guarantees exactly one underlying operation for overlapping identical keys, immediate completion safety, map eviction upon completion/failure, fresh subsequent retries, and isolation between waiters.

4. **P1-04 — Bounded Shared Flight Lifetime & Retry Recovery:**
   - Wrapped underlying repository calls with `withTimeoutOrNull(responseTimeoutMillis)` (15,000 ms).
   - Stalled calls complete cleanly with `OperationResult.Unavailable` and are evicted from the single-flight map, enabling immediate fresh Retries.

## Automated Verification Suite

- **284 JVM unit tests PASSED** (`:app:testDebugUnitTest`).
- **42 Firebase security rules tests PASSED** (`npm test` via emulator).
- **Clean debug APK build PASSED** (`:app:assembleDebug`).
- **Clean AndroidTest test APK build PASSED** (`:app:assembleDebugAndroidTest`).
- **Clean Android lint PASSED** (`:app:lintDebug`).

## Workspace and Recovery

- Branch: `feature/18-foreground-recovery`
- Base commit: `ed1460ad6435969e43f4faef7e85327342536710`
