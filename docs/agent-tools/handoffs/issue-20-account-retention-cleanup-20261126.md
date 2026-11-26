# Task handoff: Issue #20 Account-Safe Retention and Cleanup

- **Confirmed owner:** User J (Feasibility & Implementation)
- **Scope / period / different human reviewer:** MVP; 26 November 2026; User G (Requirements & Design)
- **Issue / PR:** Issue #20 (https://github.com/theguardianofthebed/Time-Capsule/issues/20); [PR #46](https://github.com/theguardianofthebed/Time-Capsule/pull/46) on `feature/20-account-retention-cleanup`
- **Branch / base / head:** `feature/20-account-retention-cleanup` / base `ff24346b8a83f802a4e6ef7eaa41a774057d8aee`
- **State:** AWAITING_REVIEW
- **Boundary:** Retained content storage, account cleanup orchestrator, transition ordering, opaque recovery records, backup exclusions
- **Predecessors:**
  - Issue #10 → PR #33 → `74aa763076801944a33897b2f67c36d3afb27aa9`
  - Issue #11 → PR #35 → `2228a07f65d8918d59ead727e33fa9fe5c56ab97`
  - Issue #14 → PR #40 → `15cf5bfdc39e6827c1a5407198cd65f6e6a7c232`
  - Issue #18 → PR #44 → `1d3bf6090f566e4ab5eec990a4bc082f0dbf6fc3`
  - Issue #19 → PR #45 → `ff24346b8a83f802a4e6ef7eaa41a774057d8aee`
- **Contract:** Atlas revision 55; `FR-01`, `FR-12`, `NFR-01`, `ARC-06`, `DD-18`, `AC-01`, `AC-13`, `T-02`, `T-29`, `T-44`
- **Overlap inspection:** 26 November 2026, no overlapping active claims on account retention or cleanup components
- **Readiness:** Eight gates passed, `docs/agent-tools/readiness-evidence-issue20.json`, `PREFLIGHT_RECORD_VALID`

## Eleventh review · 26 November 2026

User J continued from `83cbe1f`. [Implementation and regression tests](https://github.com/theguardianofthebed/Time-Capsule/commit/0c18e07adc0786cdd1055a9f2985f870414fc230)

| Correction | Behavior to review |
|---|---|
| Offline content | Empty or whitespace-only text counts as absent. Nonblank text keeps its original spacing. Replacing a cache entry removes its old marker first. A complete entry gets a new marker after the byte writes succeed. |
| Image validation | Selected, downloaded and retained photos use the same bounded Android decoder. Offline access requires a matching format and SHA-256 digest. An older photo marker without a digest requires one authorized online read to refresh it. Legacy text-only entries remain readable. |
| Online viewing | `readContent` reports `availableOffline` after the retention result and generation check. A disk failure can leave the authorized online content viewable. A changed session denies the result. |
| Uncertain self-send | The ViewModel clears the review title, message and photo references before the send suspends. S-06 hides the summary. Failed, thrown, timed-out and empty recovery results preserve the same ID for retry. A preflight rejection can restore the unsubmitted draft after recovery finds no record for its ID. Replacement still requires authoritative abandonment. |
| Opaque journal | Version 2 stores the sender and capsule IDs, digest, phase, photo-presence and acknowledgment flags, and unresolved-write and terminal-rejection evidence. Authorized recovery removes legacy lock mode, zone and replacement fields. Legacy records missing the photo-presence flag require photo proof. Opaque records support reconciliation and cannot produce a payload or editable copy. |

On `0c18e07adc0786cdd1055a9f2985f870414fc230`, Gradle 9.6.0, AGP 9.3.0, JDK 25 and SDK 37 passed all four pinned checks. The JVM suite ran 355 tests across 39 suites with zero failures, errors or skips. Both debug APKs built. Lint reported 0 errors and 145 warnings, including one version-catalog style warning for the test-only Robolectric dependency. Robolectric 4.18.1 ran the production Android image decoder in native graphics mode on API 35. Issue #20 readiness returned `PREFLIGHT_RECORD_VALID`.

The Firestore and Storage emulator suite passed all 42 tests on `540a1586de7cae8e45d6d71bad871b62c12a1b66` with local emulator traffic excluded from the proxy. That checkout differed only in the Gradle declaration of the same test dependency. The Android instrumentation APK was built, but no device was attached and no device test result is claimed. User G's review and the previously scheduled device scenarios T-02, T-29 and T-44 remain pending.

## Tenth review · 26 November 2026

User J continued from `7cc60c3`. User G's human review is pending. The implementation and regression tests are in [7eab1b9](https://github.com/theguardianofthebed/Time-Capsule/commit/7eab1b9dd245cbc2ce03fa899fabde4895e36ecb).

| Boundary | What the committed implementation does | Automated evidence |
|---|---|---|
| Publication and draft writes | The coordinator carries the originating session through submission, acknowledgements, recovery and replacement. Journal writes compare the UID and generation while holding the same lock that changes the tracker generation. Cleanup retains the private opaque-compaction path. | A paused initial submission cannot create a journal after same-UID login. A delayed acknowledgement leaves the compacted operation opaque and unresolved. A stale replacement cannot recreate the draft. |
| Photos and retention | The compose UI captures its session before queueing work. The original-photo store buffers at most the 5 MiB input limit, then checks the session under the lock before creating a file. Frozen uploads pass the original session through the copy and draft record. Retained content requires an exact live snapshot on reads and writes. | A paused provider read resumes after logout and same-UID login. Protected files stay absent. A queued freeze and a generation-zero retention request are rejected. |
| Account transitions | Sign-in and sign-up record former accounts before changing credentials and keep their obligations during the SDK request. Logout verifies SDK sign-out before purging. Restoration drains the on-disk queue, and a stale callback retires displaced accounts. A marker-write failure returns a retryable result. Finishing B removes only B from the queue. | Tests inspect the marker before invalidation, interrupt logout before SDK sign-out, recreate the manager and stores, then sign in again. Other cases cover an unreadable journal, a failed marker write, a failed SDK sign-out, stale authentication, and recovery of A while restoring B. |
| Production wiring | The application and integrated tests both create the account manager through `AccountCleanupOrchestrator.sessionManager`. It connects the marker writer and pending-UID reader to the same store that performs the purge. Application startup uses suspending foreground restoration. | The integrated logout test seeds an actual retained file, original photo, compose draft and unresolved operation. It checks on-disk removal and opaque recovery before signing in through the real manager. Firebase services are represented by test adapters. |

The pinned Gradle 9.6.0 wrapper, AGP 9.3.0, JDK 25 and Android SDK 37 produced the following results for the implementation commit.

| Gradle task | Result |
|---|---|
| `:app:testDebugUnitTest` | 353 passed, 0 failed, 0 skipped |
| `:app:assembleDebug` | Debug APK built |
| `:app:assembleDebugAndroidTest` | Instrumentation APK built |
| `:app:lintDebug` | 0 errors, 144 warnings on the fresh checkout |

The Issue #20 readiness check returned `PREFLIGHT_RECORD_VALID`. User G's review and User J's device scenarios T-02, T-29 and T-44 remain open. The automated results cover the storage and transition behavior exercised by this revision.

## Earlier work through the ninth review

1. **UID-Scoped Retained Content Adapter (`DefaultLocalRetentionAdapter.kt`):**
   - Manages UID-scoped directory structure under `noBackupFilesDir/retained-content/<hashed-uid>/<capsule-id>/`.
   - Stores `verified-marker.properties` containing `uid`, `capsuleId`, and `verifiedUnlockedAt` (derived ONLY from authoritative server-confirmed unlock evidence).
   - Enforces strict read contract: matching active UID + valid marker + complete readable text/photo files.
2. **Centralized Account Cleanup & Transition Protection (`AccountCleanupOrchestrator.kt`):**
   - Coordinates multi-store purging on account switch/logout: `retentionAdapter`, `journal`, `originalPhotoStore`, `photoDraftJournal`, `unlockNotificationCoordinator`.
   - Updated `AccountSessionManager.kt` (`signIn`, `signUpAndCreateProfile`, `restoreSessionIfAuthenticated`, `signOut`) to invalidate former session generation and execute cleanup BEFORE activating new session generation in `SessionTracker`.
3. **Opaque Recovery Records & Journal Compaction (`FrozenOperationJournal.kt`, `CapsulePublicationCoordinator.kt`):**
   - Added `compactAccountDataToOpaque()` scrubbing text, title, and photo payload fields on logout while retaining `capsuleId`, `snapshotDigest`, `phase`, `photoAcknowledged`, and `lockMode`.
   - Adapted `CapsulePublicationCoordinator.kt` and `FrozenCapsuleOperation.kt` (`isOpaque = true`) to support server reconciliation for opaque records without re-exposing raw content.
4. **Locked Self-Send Buffer Removal:**
   - Purges compose buffers and local photo files upon confirmed publication.
5. **Backup Exclusions (`backup_rules.xml`, `data_extraction_rules.xml`):**
   - Configured XML rules to exclude all app-private protected directories (`retained-content`, `capsule-operations`, `original-photos`, `frozen-photos`, `photo-drafts`, `notification-attempts.json`, `no_backup`).
6. **Ninth Review Account-Safety Corrections:**
   - **Task 1**: Persisted cleanup obligation via `markCleanupPending()` BEFORE session invalidation in `AccountSessionManager.signOut()`.
   - **Task 2**: Restricted opaque compaction to internal `saveOpaqueCompaction()` method in `FrozenOperationJournal.kt`. Public `save()` strictly requires active authenticated session matching `senderUid`.
   - **Task 3**: Re-validated session in `DefaultCapsuleRepository.readContent()` after `saveRetainedContent()` suspends before returning `OperationResult.Success(content)`.
   - **Task 4**: Updated `testRealProductionSignOutWithUnresolvedPublicationSucceedsAndPermitsSubsequentSignIn()` in `AccountSessionManagerTest.kt` to call `manager.signIn()` for Account B.
7. **Automated Verification:**
   - 342 JVM unit tests passing (`./gradlew :app:testDebugUnitTest`).
   - Clean debug APK build (`./gradlew :app:assembleDebug`).
   - Clean AndroidTest APK build (`./gradlew :app:assembleDebugAndroidTest`).
   - Clean Android lint (`./gradlew :app:lintDebug`).

## Remaining work

- Teammate code review by User G.
- Manual verification scenarios (T-02, T-29, T-44) on emulator/device.

## Workspace and recovery

- Branch: `feature/20-account-retention-cleanup`.
- To verify build and tests:
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest
  .\gradlew.bat :app:lintDebug
  .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
  py tools/check_readiness.py --issue 20 --evidence docs/agent-tools/readiness-evidence-issue20.json
  ```

## Next action

User G performs peer review on `feature/20-account-retention-cleanup` branch.
