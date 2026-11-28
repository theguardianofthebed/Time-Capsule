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

---

## 9. Verification and Checks Summary

- `./gradlew :app:testDebugUnitTest`: **PASS (376 passed, 0 failed)**
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`: **PASS (`BUILD SUCCESSFUL`)**
- `./gradlew :app:clean :app:lintDebug`: **PASS (`BUILD SUCCESSFUL`, 0 lint errors)**
- `py tools/check_readiness.py --issue 21 --evidence docs/agent-tools/readiness-evidence-issue21.json`: **PASS (`PREFLIGHT_RECORD_VALID`)**
- `git diff --check`: **PASS (0 whitespace errors)**

---

## 10. Next Human Action

1. User G code review of branch `feature/21-capsule-lists-detail`.
2. Manual end-to-end verification on Android device/emulator (S-03 list tabs, S-07 detail unlock, Author copy view, notification tap, and offline process relaunch re-view).
3. Merge PR #47 after teammate approval.







