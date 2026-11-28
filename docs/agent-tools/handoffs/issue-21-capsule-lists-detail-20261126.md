# Handoff: Issue #21 — Add account-scoped lists, locked details and permanent re-view

- **Date:** 26 November 2026
- **Issue:** [Issue #21](https://github.com/theguardianofthebed/Time-Capsule/issues/21)
- **Task Branch:** `feature/21-capsule-lists-detail`
- **Base Commit:** `b157f447f4340b6dba99edee2bef797956b0554b` (Merge PR #46, Issue #20)
- **Owner:** User J
- **Reviewer:** User G
- **Scope:** MVP

---

## 1. What changed and why

1. **Domain & Data Contract Extensions (`Capsule.kt`, `CapsuleListsSnapshot.kt`):**
   - Extended `CapsuleSummary` with safe metadata fields (`title`, `createdAt`, `creationZone`, `hasPhoto`, `unlockedAt`, `senderDisplayName`, `recipientDisplayName`) while preserving default constructor compatibility. Protected message text and photo bytes are explicitly excluded from summary models.
   - Preserved `CapsuleListsSnapshot` holding sent and received summary lists.

2. **Participant Query & Firestore Adapter Layer (`FirestoreAdapter.kt`, `DefaultFirestoreAdapter.kt`):**
   - Implemented `getSentCapsuleSummaries(senderUid)` and `getReceivedCapsuleSummaries(recipientUid)` querying Firestore `capsules` with `whereEqualTo("senderId", senderUid)` / `whereEqualTo("recipientId", recipientUid)` using `Source.SERVER`.
   - Implemented `getCapsuleSummary(capsuleId, uid)` performing exact-ID metadata reads with participant verification (`uid == senderId || uid == recipientId`).
   - All query implementations enforce `!doc.metadata.hasPendingWrites()` and `!doc.metadata.isFromCache`, discarding unconfirmed local overlays and cached documents.

3. **Offline Retention Listing & Relaunch Re-view (`LocalRetentionAdapter.kt`, `DefaultLocalRetentionAdapter.kt`):**
   - Extended `LocalRetentionAdapter` with `listVerifiedRecipientEntries(targetSession)` to enumerate verified account-scoped retained content directories (`noBackupFilesDir/retained-content/<hashed-uid>/<capsule-id>/`).
   - Validates `verified-marker.properties` (v1/v2), matching UID, capsule ID, valid timestamp, and required text/photo file completeness before returning retained entries.
   - Supports offline re-view after process relaunch while signed in (T-29, T-30) without requiring remote network connection or creating a secondary database.

4. **Repository Role & Authorization Gate (`CapsuleRepository.kt`, `DefaultCapsuleRepository.kt`):**
   - Implemented `lists(uid, targetSession)` fetching participant lists from server and falling back to verified local retention when offline.
   - Implemented `getSummary(capsuleId, targetSession)` with participant check and offline fallback.
   - Refactored `readContent(capsuleId, targetSession)` to evaluate participant roles:
     - **Author Copy (`senderUid == activeUid && senderUid != recipientUid`):** Directly reads protected content via `firestoreAdapter.readProtectedContent`. Never executes an unlock transaction, never writes recipient retention markers, and never mutates recipient unlock state.
     - **Recipient (`recipientUid == activeUid`):** Checks existing server/local unlock state. If locked, evaluates unlock conditions or triggers `ForegroundRecoveryCoordinator.verifyUnlock`. Saves verified content to local retention upon successful read.
     - **Unrelated User / Unauthenticated:** Rejects access with `OperationResult.Denied`.

5. **ViewModels & Lifecycle State (`S03CapsuleListViewModel.kt`, `S07CapsuleDetailViewModel.kt`, `ViewModelFactory.kt`):**
   - Implemented `S03CapsuleListViewModel` managing tab selection (Received/Sent), session-scoped list loading, publication recovery, and foreground unlock checks via `ForegroundRecoveryCoordinator`.
   - Implemented `S07CapsuleDetailViewModel` managing role-aware detail fetching, unlock condition evaluation, and protected content display state.
   - Configured tokenized request tracking (`requestToken`) and session validation across coroutine suspension points to prevent stale callbacks or cross-account state leakage.
   - Extended `ViewModelFactory` to instantiate both ViewModels cleanly with required dependencies.

6. **Native Views & Navigation (`S03CapsuleListFragment.kt`, `S07CapsuleDetailFragment.kt`, `MainActivity.kt`):**
   - Created native XML layouts (`fragment_s03_capsule_list.xml`, `item_capsule_summary.xml`, `fragment_s07_capsule_detail.xml`) adhering strictly to Stitch UI wireframes and design tokens.
   - Created `S03CapsuleListFragment` and `S07CapsuleDetailFragment` using ViewBinding and lifecycle-aware state collection (`repeatOnLifecycle(STARTED)`).
   - Updated `MainActivity` navigation callbacks, centralizing fragment attachment, bottom navigation ("Moments" -> `navigateToCapsuleList()`), and notification tap intent routing (`NotificationEntryResult.Valid` -> `navigateToCapsuleDetail(capsuleId)`).

## 2. First Review Functional Corrections (28 November 2026)

1. **Separated Verified Unlock Evidence from Retained Asset Availability (AC-09, FR-12, DD-06, T-30):**
   - Distinct concepts maintained for account-scoped unlock evidence (`verifiedUnlockedAt`), safe summary metadata (`RetainedSummary`), and actual protected file integrity (`readRetainedContent`).
   - Missing or corrupted retained photo/text files no longer hide an unlocked capsule from list enumeration or relock the capsule; returns actionable `OperationResult.Unavailable("Capsule unlocked — content unavailable. Connect and retry.")` allowing online asset re-download.

2. **Eliminated Fabricated Offline Metadata:**
   - Preserved `title`, `lockMode`, `senderDisplayName`, `recipientDisplayName`, and `createdAt` in `verified-marker.properties`.
   - `listVerifiedRecipientEntries()` uses stored properties from `verified-marker.properties` rather than manufacturing generic titles or defaulting all lock modes to `TIME`. Full backward compatibility with legacy v1/v2 markers preserved.

3. **Corrected Fallback Eligibility in Repository:**
   - `DefaultCapsuleRepository` list and summary queries check response types strictly: `OperationResult.Denied` and `OperationResult.Invalid` return directly without attempting offline retention fallback.
   - Offline fallback is attempted ONLY when remote queries return `OperationResult.Unavailable` (network/service down).

4. **Lightweight List Enumeration Without Bulk Loading Protected Bytes:**
   - `listVerifiedRecipientEntries` returns `List<RetainedSummary>` by inspecting `verified-marker.properties` and checking file existence on disk (`isContentAvailable`).
   - Zero text or photo bytes are loaded or decoded during list enumeration, preventing memory pressure on S-03.

5. **Deduplicated Retention Validation:**
   - Extracted shared internal helper methods (`readRetainedContentInternal`, `readRetainedSummaryInternal`) in `DefaultLocalRetentionAdapter`, eliminating duplicate marker parsing while preserving atomic locks, session checks, and SHA-256 integrity checks.

6. **Mandatory Regression Test Suite:**
   - Added `Issue21FunctionalCorrectionsTest.kt` (7 Robolectric unit tests covering TIME, LOCATION, BOTH offline re-view, missing photo retry, corrupted photo integrity check, network failure vs access denial, cross-account isolation, and lightweight summary enumeration).
   - Total **372 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).

## 3. Second Review Functional & Integration Corrections (28 November 2026)

1. **Immediate Session-Bound UI Invalidation:**
   - Bound ViewModels (`S03CapsuleListViewModel`, `S07CapsuleDetailViewModel`) directly to `SessionSnapshot` emission.
   - On account transition or logout, immediately increments `requestToken`, cancels active coroutines, and resets UI state to empty, clearing previous account data, summaries, text, and bitmap references.

2. **Explicit List Freshness Contract (`ListFreshness`):**
   - Removed title-based string heuristic (`"Previously unlocked"`) completely.
   - Extended `CapsuleListsSnapshot` with explicit `ListFreshness` (`SERVER_CONFIRMED`, `PARTIAL_OFFLINE`, `STALE_OFFLINE`).
   - Preserves partial remote list results (e.g. Received succeeded while Sent failed) without discarding data or displaying false empty lists.

3. **Independent Verified Unlock Evidence Persistence (T-30 Initial Download Failure):**
   - Implemented `saveVerifiedUnlockMarker` in `LocalRetentionAdapter` and `DefaultLocalRetentionAdapter`.
   - Persists account-scoped, server-confirmed unlock evidence and safe metadata immediately upon online unlock confirmation BEFORE protected content download.
   - Supports T-30 initial-download-failure and process recreation while maintaining complete content file integrity gates.

4. **Detailed S07 Locked/Unavailable Presentation:**
   - Introduced `DetailStatus` enum covering time/location/both lock states, location permission/service requirements, asset unavailability, and access denial.
   - Actionable retry button provided for recoverable states.

5. **S03 Pending & Unresolved Publication Section:**
   - Integrated `unresolvedOperations` rendering in `S03CapsuleListFragment` under the Sent tab.
   - Displays pending/failed send states with navigation routes to recovery flow (S-06) without exposing raw drafts or mixing into Received list.

6. **Safe Bounded Photo Rendering:**
   - Decodes photo bytes off the UI thread (`Dispatchers.IO`) with bounded sample sizing (max 1024px).
   - Recycles/clears bitmap drawables on view destruction or session generation changes.

## 4. Third Review Functional & Integration Corrections (28 November 2026)

1. **Restored Atomic Retained-Content Protection:**
   - In `DefaultLocalRetentionAdapter.kt`, `saveRetainedContent` invalidates complete-content authorization (`hasText = false, hasPhoto = false`) BEFORE writing new content files to disk.
   - Complete-content authorization is published only AFTER all required text and photo bytes are successfully written and validated.
   - Interrupted replacements leave verified unlock evidence intact without authorizing incomplete or unvalidated bytes.

2. **Authoritative Server Unlock Timestamps & Persistence Order:**
   - In `DefaultCapsuleRepository.kt`, removed device clock time (`Instant.now()`) for `verifiedUnlockedAt`.
   - Obtains and persists `serverUnlockedAt` from authoritative `RecipientUnlockRequirements.unlockedAt`.
   - Persists `saveVerifiedUnlockMarker` BEFORE starting protected content download when recipient unlocks an online capsule.

3. **Cancellable Session-Bound Image Decoding:**
   - In `S07CapsuleDetailFragment.kt`, maintained cancellable `imageRenderJob`.
   - Revalidates session snapshot, capsule ID, and fragment binding state BEFORE applying decoded bitmap to `ivS07Photo`.
   - Immediately clears image drawables and cancels active decoding on view destruction or session generation reset.

4. **Complete Metadata Preservation & Offline Read Contracts:**
   - Preserved `unlockAt` and `creationZone` in `verified-marker.properties` and `RetainedSummary`.
   - Authoritative server access denials (`OperationResult.Denied`) block local retention overrides when online, while offline re-view remains supported when network is unavailable.

5. **Foreground Recovery & Notification Fixes:**
   - In `S03CapsuleListViewModel`, foreground unlock updates both `receivedList` and `sentList` item availability.
   - In `MainActivity.kt`, notification intent deduplication key is recorded ONLY after `validateEntryIntent` succeeds.
   - In `S03CapsuleListFragment`, unresolved publication cards route to `navigateToConfirmSend(pub.capsuleId)` with specific capsule ID.

6. **Production-Path Regression Test Suite:**
   - Expanded `Issue21FunctionalCorrectionsTest.kt` with 10 production-path tests (covering full unlock -> initial download failure -> process recreation -> offline summary -> online retry, ViewModel session state clearing, and interrupted content replacement).

---

## 5. Fourth Review Functional Corrections (28 November 2026)

1. **Retention Commitment Invalidation Gate:**
   - In `DefaultLocalRetentionAdapter.kt`, `saveRetainedContent` writes `hasText = "false"` and `hasPhoto = "false"` BEFORE modifying content files on disk.
   - `saveVerifiedUnlockMarker` writes unlock evidence without inheriting or copying stale `hasText`/`hasPhoto` flags from previous runs unless explicitly provided as true.
   - For lightweight enumeration, a verification-only marker evaluates `isContentAvailable = (hasText || hasPhoto) && textOk && photoOk`, returning `false` for uncommitted or incomplete content files.

2. **Asynchronous Session Validation Gaps Closed:**
   - In `DefaultCapsuleRepository.kt`, added `sessionTracker.isSessionValid(targetSession)` checks before and after every suspension point (after unlock marker save, after protected content reads, and between remote queries).
   - Authoritative `OperationResult.Denied` and `OperationResult.Invalid` return immediately without local retention overrides.

3. **Complete Metadata Preservation & Offline Re-view:**
   - Restored and serialized `unlockAt` and `creationZone` into `verified-marker.properties` and `RetainedSummary`.
   - Reconstructed `CapsuleSummary` from offline entries with authoritative `unlockAt` and `creationZone`.

4. **S03 Sent & Received Foreground Synchronization:**
   - In `S03CapsuleListViewModel.kt`, foreground recovery updates matching availability in both `receivedList` AND `sentList`.

5. **Fault-Injection Regression Test Suite:**
   - Updated `Issue21FunctionalCorrectionsTest.kt` with a deterministic fault-injection test (`interruptedRetainedContentReplacementPreservesUnlockEvidenceWithoutAuthorizingIncompleteBytes`) exercising initial complete save -> pre-marker invalidation -> forced I/O fault -> process recreation -> verified unlock evidence survival without incomplete byte authorization -> unlock marker refresh -> successful clean replacement.

## 6. Fifth Review Functional Corrections (28 November 2026)

1. **Independent Content Commitment Gate (`contentCommitted`):**
   - Implemented explicit `contentCommitted` property in `DefaultLocalRetentionAdapter.kt`.
   - `saveVerifiedUnlockMarker` writes unlock evidence without inheriting or creating `contentCommitted = true`.
   - `saveRetainedContent` invalidates `contentCommitted = false` BEFORE modifying files and sets `contentCommitted = true` only AFTER all files are written and validated.
   - `readRetainedContentInternal` and `readRetainedSummaryInternal` enforce `contentCommitted == true`.

2. **Session Validation Boundaries:**
   - Added `sessionTracker.isSessionValid(targetSession)` checks across all asynchronous boundaries in `DefaultCapsuleRepository.kt` (between sent/received queries, after post-unlock metadata reads, after unlock marker save, and before returning protected content).

3. **Complete Image Render Identity Protection:**
   - In `S07CapsuleDetailFragment.kt`, maintained an explicit `imageRenderToken` and captured session/capsule identity.
   - Revalidates live session, token, capsule ID, and fragment binding before rendering bitmap on main thread.

4. **Production-Path Fault Injection Test:**
   - Implemented testable `faultInjector` seam on `DefaultLocalRetentionAdapter`.
   - `interruptedRetainedContentReplacementPreservesUnlockEvidenceWithoutAuthorizingIncompleteBytes` in `Issue21FunctionalCorrectionsTest.kt` exercises initial complete save -> pre-marker invalidation -> forced I/O fault -> process recreation -> verified unlock evidence survival without incomplete byte authorization -> unlock marker refresh -> clean replacement.

## 7. Sixth Review Functional Corrections (28 November 2026)

1. **Preserved Committed Asset Metadata:**
   - In `DefaultLocalRetentionAdapter.kt`, `saveVerifiedUnlockMarker` checks `existingCommitted = existingProperties.getProperty("contentCommitted") == "true"`.
   - If an existing valid complete commitment exists, `saveVerifiedUnlockMarker` preserves `contentCommitted = "true"`, `hasText`, and `hasPhoto` from `existingProperties`, preventing unlock marker refreshes from clearing or corrupting committed offline assets.

2. **Session Validation Boundaries:**
   - Added explicit `sessionTracker.isSessionValid(targetSession)` checks at all 4 specified boundaries in `DefaultCapsuleRepository.kt` (after `readRecipientUnlockRequirements` in `verifyUnlockOutcome`, after `saveVerifiedUnlockMarker` in `verifyUnlockOutcome`, after fallback `readRetainedContent` in `readContent`, and after `readRecipientUnlockRequirements` in `readContent`).

3. **Complete S03 -> S06 Targeted Recovery Route:**
   - In `CapsuleDraftViewModel.kt`, `recoverPublicationState(targetCapsuleId: String?)` targets the specified capsule ID explicitly (`result.data.firstOrNull { it.capsuleId == targetCapsuleId }`).
   - `S06ConfirmSendFragment.newInstance(capsuleId)` passes `targetCapsuleId` to `viewModel.recoverPublicationState(targetCapsuleId)`.

4. **Preserved Committed Assets Test:**
   - Added `refreshingUnlockEvidenceForCommittedCapsulePreservesBothAssets` to `Issue21FunctionalCorrectionsTest.kt`, verifying that saving a complete text + photo capsule, refreshing unlock evidence, and switching to offline mode preserves full offline readability for both assets.

## 8. Seventh Review Functional Corrections (28 November 2026)

1. **Package A — Finish Publication Recovery:**
   - In `S03CapsuleListFragment.kt`, unresolved publication card button passes `pub.capsuleId` to `navigateToConfirmSend(pub.capsuleId)`.
   - In `CapsuleDraftViewModel.kt`, strict targeted recovery (`recoverPublicationState`) requires target ID match when supplied without falling back to unrelated operations.
   - In `S06ConfirmSendFragment.kt`, stores `activeTargetCapsuleId` and enforces targeted recovery throughout `onViewCreated`, `onResume`, and `btnCheckPublication`.

2. **Package B — Complete S03/S07 User-Facing Behavior:**
   - In `S03CapsuleListFragment.kt`, distinguishes unavailable network states (`isTabUnavailable`) from confirmed empty lists.
   - In `S07CapsuleDetailFragment.kt` & `ViewModel`, renders detailed persistent lock/error states and actionable retry buttons.
   - In `MainActivity.kt`, `onResume()` triggers foreground recovery and calls `refreshScreen()` on active S03 list or S07 detail fragment.

3. **Package C — Version 3 Retention Marker & Metadata:**
   - Upgraded `MARKER_VERSION` to `"3"` in `DefaultLocalRetentionAdapter.kt`.
   - Version 3 requires `contentCommitted == "true"`. Legacy v1/v2 records with valid files remain readable.
   - Preserves `contentCommitted = "true"`, `hasText`, and `hasPhoto` when refreshing unlock evidence for an already committed capsule.

## 9. Eighth Review Functional Corrections (28 November 2026)

1. **Task 1 — S03 Unavailable-State & Session Handling:**
   - In `CapsuleListUiState.kt`, represented tab availability separately (`isReceivedUnavailable`, `isSentUnavailable`).
   - Introduced `UnresolvedPublicationSummary` (status-only presentation summary without raw draft payloads).
   - In `S03CapsuleListFragment.kt`, renders distinct network unavailable text per tab when offline/unavailable instead of confirmed empty lists.

2. **Task 2 — Targeted S06 Recovery Safety:**
   - In `CapsuleDraftViewModel.kt`, strict targeted recovery syncs `activeSessionUid` and blocks publication actions (`recoveryBlocked = true`) if requested target is missing, preventing operation X from ever operating on Y.
   - In `S06ConfirmSendFragment.kt`, enforces `activeTargetCapsuleId` recovery across `onViewCreated`, `onResume`, and `btnCheckPublication`.

3. **Task 3 — Actual S07 Failure Presentation:**
   - In `S07CapsuleDetailViewModel.kt`, maps unlock verification failures to `mapUnlockFailureStatus` (`LOCKED_PERMISSION_REQUIRED`, `LOCKED_LOCATION_DISABLED`, `LOCKED_WAITING_LOCATION`, etc.).
   - In `S07CapsuleDetailFragment.kt`, renders persistent failure cards and provides actionable retry button; image decode failures show persistent error card.

4. **Task 4 — Retention Compatibility & Metadata:**
   - In `DefaultLocalRetentionAdapter.kt`, recognizes legacy v1/v2 complete markers while enforcing `contentCommitted == "true"` for version 3.
   - Preserves `contentCommitted = "true"`, `hasText`, and `hasPhoto` when refreshing unlock evidence for an already committed capsule.

## 10. Tenth Review Bounded Functional Corrections (28 November 2026)

1. **Recoverable Time-Lock Verification:**
   - In `S07CapsuleDetailViewModel.mapUnlockFailureStatus()`, distinguished time-lock pending status ("unlock time not reached") from non-participant access denial.
   - Maps pending time locks to `DetailStatus.LOCKED_WAITING_TIME` (or `LOCKED_WAITING_BOTH`), preserving locked status badge and enabling verification retry action.

2. **Display Known Location-Lock Conditions in S07:**
   - In `S07CapsuleDetailFragment.renderUiState()`, formatted and rendered location target coordinates and radius (e.g., `Target location: (37.5665, 126.9780) · 150 m radius`) for `LOCATION` and `TIME_AND_LOCATION` lock modes.

3. **Offline Self-Send List Consistency:**
   - In `DefaultCapsuleRepository.lists()`, when using offline retained entries, populated matching self-send entries (`senderUid == cleanUid` && `recipientUid == cleanUid`) in both `received` AND `sent` tabs (deduplicated by `capsuleId`).

## 11. Eleventh Review Final Narrow Corrections (28 November 2026)

1. **Strict Offline Self-Send Filtering:**
   - In `DefaultCapsuleRepository.lists()`, updated offline self-send filtering to require strict equality (`it.senderUid == cleanUid && it.recipientUid == cleanUid`), ensuring unknown or blank participant identities are never treated as self-sends.

2. **Targeted Regression Test Enhancements:**
   - In `Issue21FunctionalCorrectionsTest.kt`, updated `offlineSelfSendAppearsInBothTabs` to test A→A self-send in both tabs, B→A in Received only, legacy unknown sender excluded from Sent, and online server-confirmed results un-overwritten.
   - In `Issue21FunctionalCorrectionsTest.kt`, updated `timeLockVerificationReturnsLockedWaitingTime` to inject exact production response `OperationResult.Denied("Unlock authorization denied or unlock time not reached.")` and verify locked retry status, subsequent successful verification, and genuine non-participant denial (`DetailStatus.ACCESS_DENIED`).
   - In `Issue21FunctionalCorrectionsTest.kt`, added `locationLockConditionDisplaysCoordinatesAndRadius` test verifying location target coordinates and radius rendering in S07.

---

## 12. Verification and Checks Summary

- `./gradlew :app:testDebugUnitTest`: **PASS (387 passed, 0 failed)**
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`: **PASS (`BUILD SUCCESSFUL`)**
- `./gradlew :app:clean :app:lintDebug`: **PASS (`BUILD SUCCESSFUL`, 0 lint errors)**
- `py tools/check_readiness.py --issue 21 --evidence docs/agent-tools/readiness-evidence-issue21.json`: **PASS (`PREFLIGHT_RECORD_VALID`)**
- `git diff --check`: **PASS (0 whitespace errors)**

---

## 13. Next Human Action

1. User G code review of branch `feature/21-capsule-lists-detail`.
2. Manual end-to-end verification on Android device/emulator (S-03 list tabs, S-07 detail unlock, Author copy view, notification tap, and offline process relaunch re-view).
3. Merge PR #47 after teammate approval.



## Ninth review · 28 November 2026

User J continued PR #47 from `dc77f31825fd3ed816ef7a310e6f786198bf974d`. User G owns human review. Scope remains FR-08/11/12, NFR-05, DD-06/28, AC-03/09/11 and T-07/08/27–30/35. Each regression below failed on the production behavior before its correction and passed when rerun afterward.

### Targeted publication recovery

`CapsuleDraftViewModelTest` exercises the activity-scoped production ViewModel. The repository fixture records actual retry, replacement and publish calls.

| Regression | Observed before correction | Passing assertion after correction |
|---|---|---|
| `targetedRecoveryClearsPreviousAccountBeforeQuery` | Account B's initial state still contained A's “Frozen message”. | The state is cleared and blocked before B's query. A missing target stays blocked. |
| `targetedRetryRejectsChangedGeneration` | Retry reached the repository after same-UID reauthentication. | The repository receives zero retries. |
| `targetedFailureCannotRetryPreviousOperation` | After Y was denied, Retry called the repository for X. | No retry or publish occurs. Y remains selected and blocked. |
| `targetedFailureCannotReplacePreviousOperation` | After Y was denied, Replace called the repository for X. | No abandonment or publish occurs. Y remains selected and blocked. |
| `targetedSelectionSupersedesPendingRecovery` | X's delayed response replaced the selection of Y. | Y remains selected. Retry sends exactly Y's ID. |
| `targetedRecheckKeepsOriginalGeneration` | A repeated check could silently validate the same selection in a newer generation. | Recheck remains blocked until a new selection originates from the new session. |

Production changes cover `CapsuleDraftViewModel` initialization, `configurePhotos`, `resetSession`, `syncSessionState`, `recoverPublicationState`, `matchesSelection`, `sendCapsule`, `retryPublication`, `startPublicationAction`, `replaceFailedPublication` and `startNewAfterSent`. S03 passes the originating snapshot through `renderUiState`, `renderUnresolvedOperations` and `MainActivity.navigateToConfirmSend`. S06 carries it across recreation in `newInstance`, `onViewCreated`, `onResume`, `checkPublication`, `setupClickListeners` and `observeViewModelState`. `ViewModelFactory.create` configures photo storage without starting generic draft recovery when entering through a targeted selection.

### Sent and Received query evidence

| Regression in `Issue21FunctionalCorrectionsTest` | Observed before correction | Passing assertion after correction |
|---|---|---|
| `tabsEmptySentAndUnavailableReceived` | A successful empty Sent query was labeled unavailable. | Sent is confirmed empty and Received is unavailable. |
| `tabsEmptyReceivedAndUnavailableSent` | A successful empty Received query was labeled unavailable. | Received is confirmed empty and Sent is unavailable. |
| `tabsImmediateRefreshClearsOldGeneration` | Refresh exposed old rows immediately after a same-UID generation change. | Both lists, pending operations and errors are clear before the new query completes. |
| `tabsPendingSendDoesNotHideReceivedEmptyView` | The actual Received empty label was detached when Sent had a pending operation. | The Received label is attached, visible and displays the confirmed-empty text. |

`DefaultCapsuleRepository.lists` records each query's provenance in `CapsuleListsSnapshot` and uses `RetainedSummary.toSummary` for local rows. `S03CapsuleListViewModel.loadLists` compares complete sessions before exposing loading state. `CapsuleListUiState` derives the selected tab's freshness from its provenance. `S03CapsuleListFragment.renderUiState` places the empty or unavailable message in the selected tab independently of the other tab's content.

### S07 presentation and restoration

| Regression in `Issue21FunctionalCorrectionsTest` | Observed before correction | Passing assertion after correction |
|---|---|---|
| `detailScreenPermissionFailurePersists` | The verification explanation was missing from the visible banner. | The real layout displays the precise-location message and permission action. |
| `detailScreenOpensLocationSettings` | Tapping Retry after a disabled-provider result launched no settings intent. | The action launches Android location-source settings. |
| `detailScreenDecodeFailureKeepsUnlockAndRetry` | Image decoding failed without a persistent photo-retry state. | Caption and opened badge stay visible with a photo error and Retry. |
| `detailWaitForRestorationRetainsIdentity` | The unauthenticated state lost the requested capsule ID. | The ID and waiting snapshot survive, then the restored session loads the correct detail. |
| `detailMissingDocumentIsAnAssetFailure` | A successful Firestore read reporting an absent document was converted to an access denial. | The adapter returns Invalid for a confirmed missing asset. The repository preserves it and S07 offers retry. |
| `detailDeniedContentDiffersFromMissingAsset` | Revoked content access appeared as an unavailable unlocked asset. | Access denial clears protected content and the asset-unavailable flag. |

`S07CapsuleDetailViewModel.loadDetail`, `mapUnlockFailureStatus`, `formatLockReason` and `photoDecodeFailed` distinguish these states. `S07CapsuleDetailFragment.onViewCreated`, `onSaveInstanceState`, `needsAppSettings` and `renderUiState` use the persistent XML banner, Android runtime permission request, app permission settings and location-source settings. Robolectric exercised the production activity, lifecycle, Fragment and XML at API 35.

### Retention and download failures

| Regression in `Issue21FunctionalCorrectionsTest` | Observed before correction | Passing assertion after correction |
|---|---|---|
| `retentionLegacyMigrationValidatesOriginalRecord` | Refresh promoted a legacy marker belonging to another UID. | For v1 and v2, a complete record survives. Wrong UID, capsule ID, timestamp, flags or missing required bytes prevent commitment. |
| `retentionMalformedCommittedFlagCannotAdoptRemoteDefault` | A malformed v3 text flag acquired the remote default and remained committed. | Refresh leaves the record uncommitted and unreadable. |
| `retentionOfflineDetailPreservesParticipantsZoneAndLock` | Successful persistence lost the sender UID. | Offline list and detail preserve both participants, creation zone, lock mode, time and location. |
| `retentionUnknownLegacyLockStaysUnknown` | A record without lock metadata acquired a time lock. | Its summary exposes an unknown lock. |
| `retentionUnavailableDownloadUsesCommittedBytes` | Interrupted download made a previously retained capsule unreadable. | The authorized recipient receives the session-checked committed content. |
| `retentionAuthoritativeContentErrorsRemainAuthoritative` | The repository replaced the remote error with a generic denial. | Denied and Invalid are returned intact, even with a cached copy. |
| `retentionStoragePermissionFailureRemainsDenied` | The production Storage adapter translated the SDK's HTTP 403 into Unavailable. | The same SDK error returns Denied and cannot authorize offline fallback. |

`DefaultLocalRetentionAdapter.saveVerifiedUnlockMarker`, `previousMarker`, `saveRetainedContent`, `readLocation` and `readRetainedSummaryInternal` preserve the v3 manifest and atomic replacement protocol. Legacy promotion reuses `readRetainedContentInternal` to validate the original assets. `LocalRetentionAdapter.saveVerifiedUnlockMarker` and `RetainedSummary` carry known coordinates. `DefaultCapsuleRepository.toSummary`, `getSummary`, `readContent` and `readRetained` share the offline mapping and session checks. `CapsuleSummary.lock` permits unknown legacy metadata. S03 `bindCapsuleItem` and S07's lock presentation handle this case. `DefaultStorageAdapter.downloadPhotoBytes` and `DefaultFirestoreAdapter.readProtectedContent` propagate authorization failures through the download path.

### Final gate

Linux, Temurin JDK 25, Gradle 9.6.0, Android SDK 37.0. Debug builds used the repository's credential-free emulator configuration.

| Check | Observed result |
|---|---|
| `:app:testDebugUnitTest` | 399 passed across 43 suites, 0 failed, 0 errors, 0 skipped. Includes 23 new red-to-green regressions. |
| `:app:assembleDebug :app:assembleDebugAndroidTest` | Both tasks passed. Verified both APKs were produced. |
| `:app:lintDebug` | Passed with 0 errors and 166 warnings. |
| Firebase Auth, Firestore and Storage emulators, demo-timecapsule, `npm test` | 42 rules tests passed at their default timeout. |
| `python tools/check_readiness.py --issue 21 --evidence docs/agent-tools/readiness-evidence-issue21.json` | PREFLIGHT_RECORD_VALID, 0 blockers. |
| `git diff --check HEAD` | Passed. |

The first combined Android run lost its Gradle daemon. The final JVM, APK and lint runs used one worker and ran sequentially. Proxying emulator traffic first caused 16 timeouts and then three failures with extended timeouts. With loopback traffic direct, the unchanged rules suite passed twice at the default timeout. The S07 UI tests used Robolectric. Physical-device permission dialogs, GPS and on-device instrumentation remain for User G's review and device testing.
