# Agent Log – Week 3 (11/22/2026 – 11/24/2026)

- Name / Student ID: Junwon (User J)
- Model(s) used: Android Studio Agent Mode (Gemini Flash 3.6)
- My changes to the team's Agent Tools this week: Added bounded review corrections, string updates, and deterministic coroutine race test suite for Issue #13 original photo handling.

## Feature or task: Issue #13 Code Review Corrections

- **Task and purpose:** Bounded code review corrections for Issue #13 (Original Photo Support), based on User G's original implementation in PR #39 (`feature/13-original-photo`), with User J as correction owner.
- **Session date(s):** November 22–24, 2026.
- **Issue and PR:** [Issue #13](https://github.com/theguardianofthebed/Time-Capsule/issues/13), [PR #39](https://github.com/theguardianofthebed/Time-Capsule/pull/39) (`feature/13-original-photo`).
- **Requirement/design references:** FR-04, FR-08, FR-12; ARC-02, ARC-06, ARC-07; DD-04, DD-18, DD-25; AC-02, AC-13, AC-14.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.6.

### Goal

Perform final review corrections for Issue #13 to eliminate unnecessary session-generation increments, resolve asynchronous photo selection/cancellation/journal races in `CapsuleDraftViewModel`, separate photo adoption from previous-photo cleanup errors, fail closed on unreadable frozen journals in `OriginalPhotoUploads.freeze()`, preserve retained bytes on failed journal rollbacks, and update outdated photo UI string resources in `strings.xml`.

### Requests and prompts

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Bounded review corrections for Issue #13 | "Perform bounded review corrections for Issue #13, based on User G's existing implementation... Correction A (Session generation), Correction B (Selection/cancellation race), Correction C (Frozen copy cleanup on journal failure)." | Clear task instructions with exact target files, problem statements, PR #40 alignment instructions, and required test cases. |
| 2 | Final review corrections & UI text update | "Perform the final targeted corrections for Issue #13 / PR #39... Problem A1, Problem A2, Correction B, Correction C (Outdated Photo UI Text)." | Specific failure scenarios (e.g. previous photo cleanup throwing IOException) and clear string update instructions. |
| 3 | Final data-integrity corrections | "Review the latest Issue #13 PR #39 code at HEAD c0b0186... 1. OriginalPhotoUploads.freeze() fail closed, 2. CapsuleDraftViewModel.selectPhoto() rollback safety." | Targeted data-integrity requirements with explicit failure-injection test constraints. |

### What the agent changed and verified

- **Correction A (Session Generation Stability & Selection/Persistence Consistency):**
  - Updated `restoreSessionIfAuthenticated()` in `AccountSessionManager.kt` to check `sessionTracker.currentSession().uid != activeUid` before calling `startSession()`, preventing unnecessary generation increments on repeated same-account checks.
  - Refactored `CapsuleDraftViewModel.kt` (`selectPhoto`, `removePhoto`, `discardDraft`, `persistPhotoDraft`, `restorePhotoDraft`) to validate operations using `isCurrentPhotoOp()` across suspension boundaries (including `photoSaveMutex.withLock`), reconcile journals when an operation becomes obsolete after persistence, and prevent stale operations from overwriting newer drafts.
  - Separated new photo adoption from previous-photo cleanup in `selectPhoto()` so that an `IOException` during cleanup of an old photo does not delete the newly adopted photo or report false errors.
  - Ensured `selectPhoto()` never deletes retained bytes when journal reference status is uncertain due to a rollback save failure.
- **Correction B (Frozen Photo Journal Failure Handling):**
  - Updated `PhotoDraftJournal.kt` to enforce thread-safe access with `synchronized(access)` and preserved PR #40's `replaceIfCurrent` and `clearIfCurrent` methods.
  - Updated `freeze()` in `OriginalPhotoUploads.kt` to fail closed when an existing frozen journal is unreadable/malformed, returning a typed failure (`OperationResult.Unavailable`) without overwriting or replacing an uncertain frozen identity.
- **Correction C (Photo UI Text Update):**
  - Updated `s04_subtext_photo` and `s04_photo_notice` in `app/src/main/res/values/strings.xml` to `"Attach one JPEG or PNG photo (up to 5 MiB)."` accurately describing active photo attachment support.
- **Checks and results:**
  - `./gradlew :app:testDebugUnitTest`: PASS (119 unit tests passed, 0 failed).
  - `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors).
  - `npx firebase emulators:exec --project demo-timecapsule "npm test"`: PASS (40 security rules tests passed, 0 failed).
  - `git diff --check`: PASS (clean).

### Errors and manual fixes

No manual fixes were needed for the implementation. All changes were applied through Android Studio Agent Mode and verified with Gradle build, unit tests, lint, and Firebase Emulator security rules.

### My review and understanding

- **What I reviewed:** Inspected the diffs in `AccountSessionManager.kt`, `OriginalPhotoUploads.kt`, `PhotoDraftJournal.kt`, `CapsuleDraftViewModel.kt`, `strings.xml`, `SessionRestorationTest.kt`, `OriginalPhotoUploadsTest.kt`, and `CapsuleDraftViewModelTest.kt`.
- **What I can explain:** How `isCurrentPhotoOp()` and token checks across suspension boundaries (`photoSaveMutex.withLock`) prevent race conditions between asynchronous provider reads, journal writes, account switches, and user UI actions (discard/removal/edits).
- **Feedback and next step:** Teammate code review and manual testing by a teammate before merging PR #39 into `main`.

## Feature or task: Issue #14 Integration and Code Review Corrections

- **Task and purpose:** Integration of latest `main` (PR #39 merged, `bb5fcf2`) into Issue #14 branch (`feature/14-capsule-publication`), code review, and replacement-draft safety corrections, with User G as original implementation owner and User J as correction owner.
- **Session date(s):** November 22–24, 2026.
- **Issue and PR:** [Issue #14](https://github.com/theguardianofthebed/Time-Capsule/issues/14), [PR #40](https://github.com/theguardianofthebed/Time-Capsule/pull/40) (`feature/14-capsule-publication`).
- **Requirement/design references:** FR-02, FR-03, FR-08, FR-09, FR-11, FR-12; ARC-04, ARC-06; DD-14, DD-15, DD-16, DD-21; AC-02, AC-03, AC-14.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.6.

### Goal

Safely integrate latest `main` into PR #40 branch `feature/14-capsule-publication`, resolve merge conflicts in `AccountSessionManager.kt`, `PhotoDraftJournal.kt`, and `CapsuleDraftViewModel.kt` without losing Issue #13's session/photo safety fixes or Issue #14's publication/recovery contracts, review and enforce replacement-draft photo safety in `PrivatePublicationDrafts.kt`, verify all publication and recovery contracts, add deterministic failure-injection tests, and run automated verification suite.

### Requests and prompts

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Integrate main, verify replacement draft safety, and run tests | "Prepare Issue #14 / PR #40 for manual testing and eventual merge into main... Step 1-6 instructions." | Clear structured prompt specifying exact steps, merge boundaries, safety checks, failure-injection tests, verification commands, and logging constraints. |

### What the agent changed and verified

- **Main Branch Integration & Conflict Resolution:**
  - Safely merged `main` (merge commit `bb5fcf2`) into `feature/14-capsule-publication` via non-destructive merge commit `0f8a055`.
  - Resolved conflicts in `AccountSessionManager.kt`, `PhotoDraftJournal.kt`, and `CapsuleDraftViewModel.kt`, preserving Issue #13's session-generation stability, photo persistence, and journal rollback safety alongside Issue #14's frozen publication tokens, recovery, and replacement functionality.
- **Ambiguous Photo Save & Cancellation Safety Corrections:**
  - Refactored `selectPhoto()` in `CapsuleDraftViewModel.kt` so every uncertain save outcome checks `input.restore(uid)` before releasing bytes, preventing candidate photo deletion if the journal on disk references the photo.
  - Replaced the cancellation test in `CapsuleDraftViewModelTest.kt` with `commitToJournalFollowedByCancellation_entersCancellationHandlerAndPreservesPhoto`, injecting `CancellationException` inside the persistence coroutine and asserting `restoreCount > 0` to verify that `selectPhoto()`'s cancellation handler was genuinely entered and executed.
  - Added deterministic unit tests in `CapsuleDraftViewModelTest.kt`: `commitToJournalFollowedByIOException_preservesJournalRecordAndRetainedPhoto`, `failedJournalReadDuringCleanupSafetyCheck_failsClosedAndPreservesPhoto`, and updated `obsoleteOperationDuringSave_doesNotOverwriteNewerDraft` to use a single shared storage instance to prove competing operations over the same logical journal do not overwrite newer drafts.
- **Replacement Cleanup Directory Assertion Fix:**
  - Updated `replacementFailsSafelyOnJournalWriteFailure()` in `PublicationDraftRecoveryTest.kt` to calculate and inspect the real replacement directory path (`digest("sender")` / `digest("new")`), verifying candidate copy creation prior to failure, candidate deletion after failure, original photo preservation, and journal integrity.
- **Contract Verification:**
  - Verified all 13 required publication/recovery contract properties across `CapsulePublicationCoordinator`, `FrozenOperationJournal`, `FirestoreCapsulePublication`, `CapsulePublicationPayload`, `FrozenCapsuleOperation`, and `CapsuleDraftViewModel`.
- **Checks and results:**
  - `./gradlew :app:testDebugUnitTest`: PASS (201 JVM unit tests passed, 0 failed).
  - `./gradlew :app:assembleDebug`: PASS (`BUILD SUCCESSFUL`).
  - `./gradlew :app:assembleDebugAndroidTest`: PASS (`BUILD SUCCESSFUL` — compiled test APK).
  - `./gradlew :app:connectedDebugAndroidTest`: NOT RUN (0 devices connected; `No connected devices!`).
  - `./gradlew :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors).
  - `npx firebase emulators:exec --project demo-timecapsule "npm test"`: PASS (42 security rules tests passed, 0 failed).
  - `git diff --check`: PASS (clean).

### Errors and manual fixes

No manual code fixes were needed. Merge conflicts and safety enhancements were handled through Android Studio Agent Mode and verified with full test passes.

### My review and understanding

- **What I reviewed:** Inspected integrated diffs across `AccountSessionManager.kt`, `PhotoDraftJournal.kt`, `CapsuleDraftViewModel.kt`, `PrivatePublicationDrafts.kt`, `PublicationDraftRecoveryTest.kt`, `CapsuleDraftViewModelTest.kt`, `CapsulePublicationCoordinator.kt`, `FirestoreCapsulePublication.kt`, and `FrozenCapsuleOperation.kt`.
- **What I can explain:** How `selectPhoto()` and `PrivatePublicationDrafts.replacement()` ensure a retained or copied photo is preserved if the durable journal references it, how `CapsulePublicationCoordinator` enforces one stable capsule ID per publication attempt, and how `FirestoreCapsulePublication` commits metadata and protected content atomically while rejecting pending/cached writes.
- **Feedback and next step:** Code review and manual testing by team members before human merge of PR #40 into `main`.

## Feature or task: Issue #15 UTC Schedules & Server-Authorized One-Way Unlock

- **Task and purpose:** Implementation of Issue #15 (Store UTC schedules and enforce server-authorized one-way unlock), with User J as acting implementation owner and User G as reviewer.
- **Session date(s):** November 22, 2026.
- **Issue and PR:** [Issue #15](https://github.com/theguardianofthebed/Time-Capsule/issues/15), task branch `feature/15-utc-schedule-unlock`.
- **Requirement/design references:** FR-05, NFR-04; ARC-02, ARC-03, ARC-05; DD-13, DD-17, DD-23; AC-04, AC-06, AC-12; T-13, T-14, T-15, T-23, T-41.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.6.

### Goal

Convert selected local date/time using an IANA `ZoneId` to an absolute UTC `Instant`, reject nonexistent local times during DST gaps, handle DST overlaps with explicit offset selection, display creation zone and offset coherently during review, preserve submitted schedules as immutable, enforce online recipient unlock transactions with server timestamp authority in `firestore.rules` and `DefaultCapsuleRepository`, treat already-unlocked capsules as idempotent success, and keep content locked on offline or denied outcomes.

### Requests and prompts

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Complete readiness preflight & implementation plan | "Start Issue #15 — Store UTC schedules and enforce server-authorized one-way unlock... Complete eight-gate preflight, report readiness, and generate plan." | Thorough preflight gates and explicit plan requirement enabled smooth transition to execution. |
| 2 | Execute Issue #15 implementation | "Proceed with the approved implementation plan." | Clear approval allowed immediate branch creation (`feature/15-utc-schedule-unlock`), domain logic creation, repository wiring, UI updates, and automated test execution. |

### What the agent changed and verified

- **Time Conversion Engine (`TimeConversionHelper.kt`):**
  - Added pure domain utility `TimeConversionHelper.kt` converting local date + local time + `ZoneId` into an absolute UTC `Instant`.
  - Rejects DST spring-forward gaps with `OperationResult.Invalid`.
  - Handles DST fall-back overlaps with valid offsets list and optional preferred offset selection.
  - Formats date, time, creation zone, and UTC offset coherently (`"2026-11-25 09:00 (Asia/Seoul, UTC+09:00)"`).
- **Server-Authorized Recipient Unlock Transaction (`DefaultCapsuleRepository.kt` & `DefaultFirestoreAdapter.kt`):**
  - Updated `FirestoreAdapter` interface and implemented `verifyRecipientUnlock()` in `DefaultFirestoreAdapter.kt` using `Source.SERVER`.
  - Enforces recipient authentication and authorization check (`docRecipientId == recipientUid`).
  - Sends `FieldValue.serverTimestamp()` in update payload, matching `canRecipientUnlock()` in `firestore.rules` (`request.time >= resource.data.unlockAt`).
  - Implemented idempotent success for already-unlocked capsules.
  - Returns `OperationResult.Unavailable` on offline/network errors and `OperationResult.Denied` on early unlock attempts.
- **UI Integration (`S05RecipientLockFragment.kt` & `S06ConfirmSendFragment.kt`):**
  - Updated `CapsuleDraftViewModel.kt` to preserve `creationZone` in `DraftUiState` and `updateUnlockAt(instant, zoneId)`.
  - Updated S-05 date & time pickers to validate DST gaps before setting draft state.
  - Updated S-05 and S-06 to format unlock dates using creation zone and UTC offset.
- **Automated Verification Suite:**
  - Written `TimeConversionHelperTest.kt` (5 unit tests).
  - Written `DefaultCapsuleRepositoryUnlockTest.kt` (8 unit tests).
  - Updated `CapsuleDraftViewModelTest.kt` (53 unit tests).
  - Executed Firebase Emulator security rules test suite `tests/capsule.rules.test.js` (42 tests passed).

### Checks and results

- `./gradlew :app:testDebugUnitTest`: PASS (214 JVM unit tests passed, 0 failed).
- `npx firebase emulators:exec --project demo-timecapsule "npm test"`: PASS (42 security rules tests passed, 0 failed).
- `./gradlew :app:assembleDebug :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors).
- `git diff --check`: PASS (clean).

### Errors and manual fixes

No manual code fixes were needed. All changes were applied through Android Studio Agent Mode and verified with Gradle unit tests, lint, assemble, and Firebase Emulator security rules.

### My review and understanding

- **What I reviewed:** Inspected diffs across `TimeConversionHelper.kt`, `DefaultCapsuleRepository.kt`, `FirestoreAdapter.kt`, `DefaultFirestoreAdapter.kt`, `CapsuleDraftViewModel.kt`, `S05RecipientLockFragment.kt`, `S06ConfirmSendFragment.kt`, `TimeConversionHelperTest.kt`, `DefaultCapsuleRepositoryUnlockTest.kt`, and `CapsuleDraftViewModelTest.kt`.
- **What I can explain:** How `TimeConversionHelper` detects DST gaps via `ZoneId.rules.getValidOffsets(localDateTime)` and requires explicit offset choices for overlaps, how `DefaultFirestoreAdapter.verifyRecipientUnlock` uses `runTransaction` with `FieldValue.serverTimestamp()` for atomic server-authorized unlocking, and how `canRecipientUnlock()` in `firestore.rules` enforces one-way recipient unlock transitions.
- **Feedback and next step:** Teammate code re-review by User G on PR #41 before manual testing.

### PR #41 Focused Review Corrections & Integration Tests (November 23, 2026)

- **Correction 1 (Session Generation Validation & Same-UID Relogin Race):** Updated `DefaultCapsuleRepository.verifyUnlock` to compare full `SessionSnapshot` (`postSession != initialSession`), validating `sessionGeneration` in addition to `uid`. Added regression unit test `verifyUnlock rejects result if same UID is relogged in mid flight`.
- **Correction 2 (CancellationException Propagation):** Updated `DefaultFirestoreAdapter` (`mapFirestoreUnlockException`, `mapFirestoreException`, and nested fallback check) to rethrow `kotlinx.coroutines.CancellationException` (`if (e is CancellationException) throw e`), preserving coroutine cancellation semantics.
- **Correction 3 (Firebase Emulator Android Transaction Integration Tests):** Added `FirebaseEmulatorUnlockIntegrationTest.kt` executing against live Firebase Auth & Firestore Emulators. Covered server-authorized TIME unlock, early unlock denial, idempotent repeat/concurrent unlock, unauthorized caller denial, protected content access rules, and metadata immutability during unlock.
- **Correction 4 (Creation Zone Survival & 2027 DST Boundary Fixtures):** Added `creationZoneSurvivesPublicationAndRecovery` test in `CapsulePublicationCoordinatorTest.kt`. Added approved 2027 DST boundary fixtures (March 14, 2027 spring-forward gap and November 7, 2027 fall-back overlap) to `TimeConversionHelperTest.kt`.
- **Correction 5 (KDoc & Cleanup):** Corrected DST overlap KDoc in `TimeConversionHelper.kt`, removed unused imports, added ambiguous lost-response test in `DefaultCapsuleRepositoryUnlockTest.kt`, and updated PR #41 documentation.
- **Checks & Results:** `./gradlew :app:testDebugUnitTest`: PASS (222 JVM unit tests passed, 0 failed). `npm test`: PASS (42 security rules tests passed, 0 failed). `./gradlew :app:connectedDebugAndroidTest`: PASS (25 Android instrumentation tests passed on live emulator-5554 with Firebase Emulators, 0 failed, including 7 in `FirebaseEmulatorUnlockIntegrationTest`). `./gradlew :app:assembleDebug :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors).

## Feature or task: Issue #16 S-05 Map Target Selection & Foreground Location Adapter

- **Task and purpose:** Implementation of S-05 Google Maps target selection UI, pure deterministic location qualifier engine, fused location provider adapter, and draft location persistence for Issue #16, with User J as acting implementation owner and User G as reviewer.
- **Session date(s):** November 23, 2026.
- **Issue and PR:** [Issue #16](https://github.com/theguardianofthebed/Time-Capsule/issues/16), task branch `feature/16-map-foreground-location`.
- **Requirement/design references:** FR-06, NFR-02; ARC-07; DD-12, DD-22; AC-05; T-16, T-17, T-18, T-42 (partial).
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini 3.8 Flash.

### Goal

Deliver MVP S-05 Google Maps target selection and fresh foreground location adapter with permission guidance. The adapter must never authorize capsule unlocks. Implement pin selection UI inside S-05 as a lifecycle-safe `DialogFragment`, allowing pin selection and 150m radius visualization with explicit Confirm action. Implement pure deterministic `LocationQualifier` evaluating distance <= 150m, age in 0..60s, and horizontal accuracy <= 100m. Implement `DefaultLocationAdapter` using `FusedLocationProviderClient.getCurrentLocation` with `PRIORITY_HIGH_ACCURACY`, cancellation handling, and typed failure statuses. Extend `PhotoDraftJournal` to persist confirmed location target across relaunch and activity rotation without erasing target on text/photo edits.

### Requests and prompts

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Execute readiness preflight and implement Issue #16 | "Refer to md and follow instruction, start implementing after reading instruction. @TimeCapsule_Issue16_Gemini_Implementation_Prompt.md" | Validated all 8 readiness gates (`PREFLIGHT_RECORD_VALID`), created `feature/16-map-foreground-location` branch, and implemented domain qualifier, location adapter, map UI, and persistence. |

### What the agent changed and verified

- **Domain Qualifier Engine (`LocationSample.kt`, `LocationQualifier.kt`):**
  - Created `ForegroundLocationSample`, `LocationSampleStatus`, `LocationSampleResult`, `QualificationFailureReason`, and `LocationQualificationResult`.
  - Implemented `LocationQualifier` evaluating `distance <= 150.0m`, `0s <= age <= 60s`, and `0m <= accuracy <= 100m` against `SystemClock.elapsedRealtimeNanos()`.
  - Decoupled WGS84 distance calculation via `DistanceCalculator` seam and `HaversineDistanceCalculator` default implementation for pure JVM unit testing.
- **Android Location Adapter (`LocationAdapter.kt`, `DefaultLocationAdapter.kt`):**
  - Extended `LocationAdapter` with `isFineLocationGranted()` and `getCurrentLocation(): LocationSampleResult`.
  - Implemented `DefaultLocationAdapter` using `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY`, timeout control, coroutine cancellation, and typed error responses (`PERMISSION_DENIED`, `SERVICE_DISABLED`, `TIMEOUT_OR_NULL`, `SECURITY_EXCEPTION`).
  - Updated `MainActivity` container initialization and test double `FakeLocationAdapter`.
- **Map Target Selection UI (`S05MapTargetDialogFragment.kt`, `dialog_s05_map_target.xml`):**
  - Implemented `S05MapTargetDialogFragment` embedding Google Maps `MapView` with pin marker and 150m radius circle.
  - Manual map navigation and pin selection operates without location permissions.
  - "Use current location" button explicitly checks/requests `ACCESS_FINE_LOCATION` and moves camera/pin to current location.
  - Explicit "Confirm Location" button commits selected target to draft; closing/canceling dialog retains prior selection.
- **Draft Target Persistence (`PhotoDraftJournal.kt`, `CapsuleDraftViewModel.kt`):**
  - Extended `PhotoDraftRecord` and `PhotoDraftJournal` to persist `locationTarget` and `locationName` in UID-scoped properties.
  - Maintained 100% backward compatibility for pre-existing properties files.
  - Verified that text/photo edits preserve confirmed location target.
- **Manifest & Account Settings (`AndroidManifest.xml`, `S08AccountSettingsFragment.kt`):**
  - Declared `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`.
  - Configured Google Maps API key meta-data with `${MAPS_API_KEY}` manifest placeholder.
  - Updated S-08 location status badge to require `ACCESS_FINE_LOCATION` for precise qualification.
- **Automated Verification Suite:**
  - Written 15 new JVM unit tests (`LocationQualifierTest`, `DraftLocationPersistenceTest`, `DefaultLocationAdapterTest`). Total 233 JVM unit tests passed (`:app:testDebugUnitTest`).
  - Clean debug build and lint (`:app:assembleDebug`, `:app:lintDebug`).

### Checks and results

- `./gradlew :app:testDebugUnitTest`: PASS (233 JVM unit tests passed across project, 0 failed).
- `./gradlew :app:assembleDebug :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors).
- `python tools/check_readiness.py --issue 16 --evidence docs/agent-tools/readiness-evidence-issue16.json`: PASS (`PREFLIGHT_RECORD_VALID`).
- `git status`: PASS (clean on `feature/16-map-foreground-location`).

### Errors and manual fixes

No manual code fix was required. The agent corrected a missing permission linter annotation by adding `@Suppress("MissingPermission")` to `DefaultLocationAdapter.getCurrentLocation()`, added missing string resources for map UI strings, and fixed an unnecessary non-null assertion warning in unit tests.

### PR #42 Review Corrections (November 23, 2026)

- **Correction 1 (Fragment Recreation & FragmentResult Handoff - P0):** Replaced ordinary property callbacks in `S05MapTargetDialogFragment` with lifecycle-safe `setFragmentResult(REQUEST_KEY_MAP_TARGET, bundle)` and registered `setFragmentResultListener` in `S05RecipientLockFragment`. Saved and restored pending pin selection (`KEY_PENDING_LAT`, `KEY_PENDING_LNG`, `KEY_PENDING_NAME`) in `onSaveInstanceState`.
- **Correction 2 (Reject Invalid GPS Timestamps - P0):** Removed `nowNanos` substitution in `DefaultLocationAdapter` when `androidLocation.elapsedRealtimeNanos <= 0L`, failing closed with `LocationSampleStatus.TIMEOUT_OR_NULL` ("Location fix is missing a valid monotonic timestamp."). Added `sample.elapsedRealtimeNanos <= 0L` validation check in `LocationQualifier`.
- **Correction 3 (Require Actual Pin Selection - P1):** When opening map without an existing target (`initialTarget == null`), map camera opens at default Seoul view without placing a pin or circle, and `btnConfirmLocation` starts disabled until user explicitly taps map or fetches a valid current-location fix. Cancel preserves host selection.
- **Correction 4 (Qualify Current-Location Selection - P1):** Added `LocationQualifier.validateSampleQuality(sample, nowElapsedRealtimeNanos)`. Tapping "Use current location" validates coordinates, positive monotonic timestamp, freshness ($\le 60\text{ s}$), and reported accuracy ($\le 100\text{ m}$) before adopting pin, displaying specific error guidance on poor accuracy ($> 100\text{ m}$) or stale fixes.
- **Correction 5 (Enforce Fixed 150m Radius - P1):** Enforced fixed $150.0\text{ m}$ radius (`LocationTarget.FIXED_RADIUS_METERS = 150.0`). Normalized non-150m or malformed radius values in `PhotoDraftJournal.load()` to $150.0\text{ m}$, and safely invalidated corrupt coordinates (`NaN`, out-of-bounds) to `null` without corrupting title, text, photo, or recipient fields.
- **Correction 6 (Expanded Test Suite):**
  - Updated `LocationQualifierTest` with `validateSampleQuality` tests and non-positive timestamp rejection tests.
  - Updated `DefaultLocationAdapterTest` with `invalidNonPositiveTimestampResultFailsClosed` test.
  - Updated `DraftLocationPersistenceTest` with `malformedOrOutOfBoundsCoordinatesSafelyInvalidatesLocationTarget` test.
  - Added Android instrumentation tests in `S05MapTargetDialogFragmentTest` (5 tests covering `FragmentResult` emission, recreation state preservation, preselection, and cancel behavior).
- **Checks & Results:** `./gradlew :app:testDebugUnitTest`: PASS (236 JVM unit tests passed, 0 failed). `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors). `py tools/check_readiness.py --issue 16 --evidence docs/agent-tools/readiness-evidence-issue16.json`: PASS (`PREFLIGHT_RECORD_VALID`).

### My review and understanding

- **What I reviewed:** Inspected the generated `LocationQualifier`, `DefaultLocationAdapter`, `S05MapTargetDialogFragment`, and `PhotoDraftJournal` diffs.
- **What I can explain:**
  - How `LocationQualifier` checks monotonic sample age (`SystemClock.elapsedRealtimeNanos()`), reported horizontal accuracy, and WGS84 Haversine distance without caching or latching past visits.
  - Why manual map selection requires zero permissions, while "Use current location" explicitly requests `ACCESS_FINE_LOCATION`.
  - Why qualifying a location sample in Issue #16 does NOT grant or authorize a capsule unlock (unlock authorization belongs downstream to Issue #17).
- **Feedback and next step:** Open Draft PR on GitHub, obtain teammate review from User G, and perform human merge into `main`.








