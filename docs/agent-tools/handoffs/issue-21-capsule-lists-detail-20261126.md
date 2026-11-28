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

---

## 3. Verification and Checks Summary

- `./gradlew :app:testDebugUnitTest`: **PASS (372 passed, 0 failed)**
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`: **PASS (`BUILD SUCCESSFUL`)**
- `./gradlew :app:clean :app:lintDebug`: **PASS (`BUILD SUCCESSFUL`, 0 lint errors)**
- `py tools/check_readiness.py --issue 21 --evidence docs/agent-tools/readiness-evidence-issue21.json`: **PASS (`PREFLIGHT_RECORD_VALID`)**

---

## 4. Key Files Created/Modified

- `app/src/main/java/com/example/timecapsule/domain/model/Capsule.kt`
- `app/src/main/java/com/example/timecapsule/data/adapter/FirestoreAdapter.kt`
- `app/src/main/java/com/example/timecapsule/data/adapter/DefaultFirestoreAdapter.kt`
- `app/src/main/java/com/example/timecapsule/data/adapter/LocalRetentionAdapter.kt`
- `app/src/main/java/com/example/timecapsule/data/adapter/DefaultLocalRetentionAdapter.kt`
- `app/src/main/java/com/example/timecapsule/data/CapsuleRepository.kt`
- `app/src/main/java/com/example/timecapsule/data/DefaultCapsuleRepository.kt`
- `app/src/main/java/com/example/timecapsule/data/AccountSessionManager.kt`
- `app/src/main/java/com/example/timecapsule/ui/ViewModelFactory.kt`
- `app/src/main/java/com/example/timecapsule/ui/s03/CapsuleListUiState.kt`
- `app/src/main/java/com/example/timecapsule/ui/s03/S03CapsuleListViewModel.kt`
- `app/src/main/java/com/example/timecapsule/ui/s03/S03CapsuleListFragment.kt`
- `app/src/main/java/com/example/timecapsule/ui/s07/CapsuleDetailUiState.kt`
- `app/src/main/java/com/example/timecapsule/ui/s07/S07CapsuleDetailViewModel.kt`
- `app/src/main/java/com/example/timecapsule/ui/s07/S07CapsuleDetailFragment.kt`
- `app/src/main/java/com/example/timecapsule/MainActivity.kt`
- `app/src/main/res/layout/fragment_s03_capsule_list.xml`
- `app/src/main/res/layout/item_capsule_summary.xml`
- `app/src/main/res/layout/fragment_s07_capsule_detail.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/timecapsule/data/CapsuleListsAndDetailRepositoryTest.kt`
- `app/src/test/java/com/example/timecapsule/data/Issue21FunctionalCorrectionsTest.kt`
- `app/src/test/java/com/example/timecapsule/ui/s03/S03CapsuleListViewModelTest.kt`
- `app/src/test/java/com/example/timecapsule/ui/s07/S07CapsuleDetailViewModelTest.kt`

---

## 5. Next Human Action

1. User G code review of branch `feature/21-capsule-lists-detail`.
2. Manual end-to-end verification on Android device/emulator (S-03 list tabs, S-07 detail unlock, Author copy view, notification tap, and offline process relaunch re-view).
3. Merge PR #47 after teammate approval.

