# Task handoff: Issue #20 Account-Safe Retention and Cleanup

- **Confirmed owner:** User J (Feasibility & Implementation)
- **Scope / period / different human reviewer:** MVP; 26 November 2026; User G (Requirements & Design)
- **Issue / PR:** Issue #20 (https://github.com/theguardianofthebed/Time-Capsule/issues/20); PR pending on branch `feature/20-account-retention-cleanup`
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

## Completed work

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
6. **Sixth Review Corrections:**
   - **Task 1**: Updated `AccountSessionManager.kt` to persist pending cleanup BEFORE Auth sign-out. Added null-UID sign-out retries and fail-closed session activation.
   - **Task 2**: Connected `session: () -> SessionSnapshot` across all 7 stores and enforced `activeSession.sessionGeneration == targetSession.sessionGeneration` inside storage locks without generation-zero bypass. Verified `!file.exists()` on file deletion.
   - **Task 3**: Implemented PNG (`0x89 0x50 0x4E 0x47`) vs JPEG (`0xFF 0xD8 0xFF`) header byte probe and image byte validation in `DefaultFirestoreAdapter.kt`.
7. **Automated Verification:**
   - 341 JVM unit tests passing (`./gradlew :app:testDebugUnitTest`).
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
  .\gradlew.bat :app:assembleDebug
  py tools/check_readiness.py --issue 20 --evidence docs/agent-tools/readiness-evidence-issue20.json
  ```

## Next action

User G performs peer review on `feature/20-account-retention-cleanup` branch.
