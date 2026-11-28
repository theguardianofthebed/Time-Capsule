# User J agent work log

Agents append factual task entries here only after the requester explicitly identifies **User J** as the owner. Follow the [entry guide and template](../README.md). Preserve earlier entries, record actual checks and remaining work, and keep human review or learning claims separate.

Weekly coursework remains in `agent-log-w1.md`–`agent-log-w4.md`.

## Entries

## 2026-11-23-issue16-map-foreground-location — S-05 Google Maps target selection & fresh foreground location adapter

- **Owner:** User J, explicitly confirmed as implementation owner for Issue #16. Reviewer: User G.
- **Environment/model:** Android Studio Agent Mode (Gemini 3.8 Flash).
- **Status:** Implemented, verified with 233 passing JVM unit tests, clean debug build and lint, task branch `feature/16-map-foreground-location` prepared for PR.
- **Requested outcome:** Deliver MVP S-05 Google Maps target selection and fresh foreground location adapter with permission guidance. The adapter must never authorize capsule unlocks. Implement pin selection UI inside S-05 as a lifecycle-safe `DialogFragment`, allowing pin selection and 150m radius visualization with explicit Confirm action. Implement pure deterministic `LocationQualifier` evaluating distance <= 150m, age in 0..60s, and horizontal accuracy <= 100m. Implement `DefaultLocationAdapter` using `FusedLocationProviderClient.getCurrentLocation` with `PRIORITY_HIGH_ACCURACY`, cancellation handling, and typed failure statuses. Extend `PhotoDraftJournal` to persist confirmed location target across relaunch and activity rotation without erasing target on text/photo edits.
- **References:** [Issue #16](https://github.com/theguardianofthebed/Time-Capsule/issues/16); FR-06, NFR-02; ARC-07; DD-12, DD-22; AC-05; T-16, T-17, T-18, T-42.

### Work performed

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

### PR #42 Follow-up Review Corrections (November 23, 2026)

- **Fix A (P1 - Make Android Instrumentation Tests Executable):** Safely fallback to container location adapter or mock in `S05MapTargetDialogFragment` without casting host activity unconditionally to `MainActivity`. Added `debugImplementation("androidx.fragment:fragment-testing-manifest:1.6.2")` in `app/build.gradle.kts`.
- **Fix B (P1 - Enforce Fixed 150m Domain Invariant):** Enforced `target.radiusMeters == LocationTarget.FIXED_RADIUS_METERS` in `LocationQualifier.qualify()`. Non-150m target radii (e.g. 500m) are rejected as `INVALID_TARGET`. Added regression unit test `non150mTargetRadiusIsRejectedByQualifier`.
- **Fix C (P1 - Preserve Location Across Failed-Publication Replacement):** Updated `PrivatePublicationDrafts.replacement()` to extract and persist `operation.lock.location` in the replacement `PhotoDraftRecord` and `.properties` file. Added regression unit test `replacementDraftPreservesLocationTargetAcrossProcessRestart` verifying location target survival across process death / storage & ViewModel recreation.
- **Fix D (P1 - Reject Obsolete Map Results):** Included `RESULT_KEY_CAPSULE_ID` and `RESULT_KEY_SESSION_UID` in `S05MapTargetDialogFragment` result bundle. Added validation in `S05RecipientLockFragment` checking `currentUiState.editable`, matching capsule ID, matching session UID, and WGS84 coordinate bounds before calling `viewModel.updateLocation()`.
- **Fix E (P2 - Correct Initial Confirm State Synchronously):** Updated `S05MapTargetDialogFragment.onViewCreated` to set `btnConfirmLocation.isEnabled = selectedLatLng != null` synchronously prior to asynchronous `onMapReady()`.

### PR #42 Final Review Corrections (November 23, 2026)

- **Correction A (Complete Stale FragmentResult Protection - P1):** Emitted full session identity (`RESULT_KEY_SESSION_GENERATION`) and one-time request identifier (`RESULT_KEY_REQUEST_ID`) in `S05MapTargetDialogFragment`. Updated `S05RecipientLockFragment` to capture `activeMapRequestId`, persist it across rotation in `onSaveInstanceState`, and validate `editable`, non-null matching capsule ID, matching session UID, matching `sessionGeneration` (rejects same-UID reauthentication), and matching `requestId` before calling `viewModel.updateLocation()`. Written 7 pure JVM unit tests in `S05RecipientLockResultGuardsTest` verifying guard checks against stale, obsolete, or reauthenticated results.
- **Correction B (Validate Android Test Suite - P1):** Verified runtime execution and dependencies of `S05MapTargetDialogFragmentTest.kt` in `androidTest`. Updated assertions to synchronously check view creation states, verify scoped `FragmentResult` emission, test pending selection state preservation across recreation, and confirm cancel leaves host state untouched.
- **Correction C (Update Existing PR Records - P2):** Updated PR #42 description, handoff file `issue-16-map-foreground-location-20261123.md`, and User J's logs with latest HEAD SHA (`d4ddacd`), 245 passing JVM unit tests, and `NOT RUN` instrumentation status (0 adb devices connected).

### PR #42 Final Test-Coverage Cleanup (November 23, 2026)

- **Correction 1 (Test Actual Production Guard Logic):** Extracted `S05MapTargetResultValidator` object and `ResultPayload` container used directly by `S05RecipientLockFragment`. Updated `S05RecipientLockResultGuardsTest` so all 7 JVM tests call `S05MapTargetResultValidator.validateResult(...)` directly. Enforced one-time request ID consumption (`activeMapRequestId = null` on successful update) to prevent result replay while preserving `onSaveInstanceState` configuration-change recovery.
- **Correction 2 (Correct Instrumentation Test Coverage):** Renamed preselected target recreation test in `S05MapTargetDialogFragmentTest.kt` to `preselectedInitialTargetPreservedAcrossRecreation` accurately reflecting its scope.
- **Correction 3 & 4 (Final Verification & PR Records):** Updated PR #42 description, handoff file `issue-16-map-foreground-location-20261123.md`, and User J's logs with latest HEAD SHA (`6df925c`), 245 passing JVM unit tests, and `NOT RUN` instrumentation status (0 adb devices connected).

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (245 JVM unit tests passed across project, 0 failed).
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors).
- `python tools/check_readiness.py --issue 16 --evidence docs/agent-tools/readiness-evidence-issue16.json`: PASS (`PREFLIGHT_RECORD_VALID`).
- `git status`: PASS (clean on `feature/16-map-foreground-location`).

### Human input and remaining work

User J confirmed task ownership and implementation review. Personal reflections, teammate PR review by User G, manual testing on device/emulator, and human merge into `main` remain for the students to complete.

## 2026-11-22-issue15-utc-schedules-server-unlock — UTC schedules & server-authorized one-way unlock

- **Owner:** User J, explicitly confirmed as implementation owner for Issue #15. Reviewer: User G.
- **Environment/model:** Android Studio Agent Mode (Gemini Flash 3.6).
- **Status:** Implemented, verified with unit tests and Firebase Emulator security rules tests, clean assemble & lint, task branch `feature/15-utc-schedule-unlock` prepared for PR.
- **Requested outcome:** Store UTC schedules using an IANA ZoneId, convert local date/time to absolute UTC Instant, reject Daylight Saving Time (DST) spring-forward gaps, handle DST fall-back overlaps with explicit offset selection, display creation zone and offset coherently during review, keep submitted schedule immutable, and enforce online recipient unlock transaction with server-authorized `FieldValue.serverTimestamp()` timestamp in `firestore.rules` and `DefaultCapsuleRepository`. Device clock changes cannot unlock capsules; already-unlocked capsules yield idempotent success; offline or denied requests remain locked.
- **References:** [Issue #15](https://github.com/theguardianofthebed/Time-Capsule/issues/15); FR-05, NFR-04; ARC-02, ARC-03, ARC-05; DD-13, DD-17, DD-23; AC-04, AC-06, AC-12; T-13, T-14, T-15, T-23, T-41.

### Work performed

- **Time Conversion Engine & DST Validation (`TimeConversionHelper.kt`):**
  - Created domain utility `TimeConversionHelper.kt` converting local date (`LocalDate`) + local time (`LocalTime`) + IANA `ZoneId` into an absolute UTC `Instant`.
  - Implemented DST gap detection (`rules.getValidOffsets(localDateTime).isEmpty()`), rejecting nonexistent times with `OperationResult.Invalid("Selected local time does not exist due to daylight saving transition: ...")`.
  - Implemented DST overlap handling (`validOffsets.size > 1`), returning `isAmbiguousOverlap = true` with available offsets and applying preferred or default offset.
  - Implemented coherent timezone string formatting (`formatZonedDateTime(instant, zoneId)` -> `"2026-11-25 09:00 (Asia/Seoul, UTC+09:00)"`).
- **Server-Authorized One-Way Unlock Transaction (`DefaultCapsuleRepository.kt` & `DefaultFirestoreAdapter.kt`):**
  - Updated `FirestoreAdapter` interface and implemented `verifyRecipientUnlock(capsuleId, recipientUid)` in `DefaultFirestoreAdapter.kt` using `Source.SERVER`.
  - Enforced recipient check (`docRecipientId == recipientUid`), returning `OperationResult.Denied` for non-recipients.
  - Enforced server authority by sending `mapOf("unlocked" to true, "unlockedAt" to FieldValue.serverTimestamp())`, evaluated against `request.time >= resource.data.unlockAt` in `firestore.rules`.
  - Implemented idempotent success: already-unlocked capsules return `OperationResult.Success(Availability.AVAILABLE)` without redundant network updates.
  - Implemented fail-closed behavior on offline, network error, or permission denied (`OperationResult.Unavailable` / `OperationResult.Denied`).
  - Deferred `LOCATION` and `TIME_AND_LOCATION` unlock conditions to Issue #17 with explicit fail-closed notices.
- **UI Integration & Timezone Formatting (`S05RecipientLockFragment.kt` & `S06ConfirmSendFragment.kt`):**
  - Updated `CapsuleDraftViewModel.kt` to preserve `creationZone` in `DraftUiState` and `updateUnlockAt(instant, zoneId)`.
  - Integrated `TimeConversionHelper` into `S05RecipientLockFragment.kt`'s date & time pickers to validate DST gaps before setting state, and format UI buttons with date, time, zone, and offset.
  - Updated `S06ConfirmSendFragment.kt` to display formatted target UTC date/time, creation zone, and offset in the review card.
- **Automated Verification Suite:**
  - Written `TimeConversionHelperTest.kt` (5 unit tests covering UTC conversion, DST spring-forward gap rejection, DST fall-back overlap resolution, timezone formatting, and offset formatting).
  - Written `DefaultCapsuleRepositoryUnlockTest.kt` (8 unit tests covering unauthenticated denial, blank ID validation, non-recipient denial, early unlock denial, server deadline unlock success, idempotent unlock, device clock manipulation safety, and offline network failure handling).
  - Updated `CapsuleDraftViewModelTest.kt` with timezone preservation tests (53 unit tests passing in suite).

- **PR #41 Code Review Corrections & Integration Tests (2026-11-23):**
  - **Correction A (Real Firestore Transaction & Session Validation):** Refactored `DefaultFirestoreAdapter.verifyRecipientUnlock` to use genuine atomic `runTransaction<TransactionUnlockResult> { transaction -> transaction.get(docRef) ... transaction.update(...) }`. Updated `DefaultCapsuleRepository.verifyUnlock` to compare full `SessionSnapshot` (`postSession != initialSession`), validating `sessionGeneration` across async boundaries and adding same-UID relogin race regression test.
  - **Correction B (Preserve Creation Zone End-to-End & 2027 Fixtures):** Added `creationZone` to `CapsuleDraft`, updated `CapsulePublicationCoordinator` to pass `draft.creationZone` when freezing new operations, and preserved `creationZone` through `PrivatePublicationDrafts.asDraft()` and draft restoration/replacement. Added 2027 DST boundary fixtures (March 14, 2027 gap and November 7, 2027 overlap) and `creationZoneSurvivesPublicationAndRecovery` test.
  - **Correction C (Explicit DST Overlap Selection):** Updated `TimeConversionHelper.toUtcInstant` to reject ambiguous DST overlap times with `OperationResult.Invalid` unless an explicit `preferredOffset` in `validOffsets` is supplied. Updated `S05RecipientLockFragment` to display an offset selection dialog when a DST overlap is selected.
  - **Correction D (Lock Mode Consistency & Cancellation Exception):** Aligned lock mode string mappings (`"TIME"`, `"LOCATION"`, `"BOTH"`, `"TIME_AND_LOCATION"`). Re-threw `kotlinx.coroutines.CancellationException` in `DefaultFirestoreAdapter` exception mappers. Added `FirebaseEmulatorUnlockIntegrationTest.kt` executing against live Firebase Auth & Firestore Emulators.

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (222 JVM unit tests passed across project, 0 failed).
- `npm test`: PASS (42 security rules tests passed, 0 failed).
- `./gradlew :app:connectedDebugAndroidTest`: PASS (25 Android instrumentation tests passed on live emulator-5554 with Firebase Emulators, 0 failed, including 7 in `FirebaseEmulatorUnlockIntegrationTest`).
- `./gradlew :app:assembleDebug :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors).
- `git diff --check`: PASS (clean).

### Human input and remaining work

User J confirmed task ownership and implementation review. Personal reflections, teammate PR review by User G, manual testing, and human merge into `main` remain for the students to complete.


## 2026-11-13-issue37-ui-design-correction — S-01, S-02, S-08 Stitch UI design correction & review fixes

- **Owner:** User J, explicitly confirmed as implementation owner for Issue #37. Reviewer: User G.
- **Environment/model:** Android Studio Agent Mode (Gemini Flash 3.8).
- **Status:** Implemented, verified with unit tests and Android Lint, verified on emulator, branch pushed, PR #38 updated.
- **Requested outcome:** Correct S-01 (Sign In), S-02 (Register), and S-08 (Account/Profile) screen layouts and styling to match connected Stitch wireframes ("TimeCapsule Android UI Design", project ID `15276828764661611137`) using Kotlin + native Android Views/XML + ViewBinding, while preserving working account authentication/profile creation/repair, UID display/copy, logout, and session functionality. Apply code review corrections for input validation messages, accessibility touch targets, Show/Hide content descriptions, and truthful OS permissions status.
- **References:** [Issue #37](https://github.com/theguardianofthebed/Time-Capsule/issues/37); [PR #38](https://github.com/theguardianofthebed/Time-Capsule/pull/38); FR-01, FR-10; ARC-01, ARC-06; DD-01, DD-03, DD-07, DD-19, DD-24, DD-26; AC-01, AC-10.

### Work performed

- **Stitch Wireframe Inspection:** Retrieved and analyzed authoritative HTML/CSS wireframes for S-01, S-02, and S-08 from connected Stitch project `TimeCapsule Android UI Design`.
- **UI & Layout Corrections:**
  - Defined Stitch design tokens in `res/values/colors.xml` (`capsule_bg`, `capsule_card_surface`, `capsule_border`, `capsule_text_dark`, `capsule_primary` `#865B4B`, `capsule_badge_bg`, etc.) and string resources in `res/values/strings.xml`.
  - Created vector assets (`ic_capsule_logo`, `ic_hero_envelope`, `ic_mail`, `ic_lock`, `ic_visibility`, `ic_arrow_right`, `ic_leaf`, `ic_copy`, `ic_location`, `ic_bell`, `ic_logout`, `ic_edit`, `ic_info`) and shape drawables (`bg_card_surface`, `bg_button_primary`, `bg_button_outlined`, `bg_badge_success`, `bg_badge_neutral`, `bg_banner_info`, `bg_circle_badge`, `bg_circle_avatar`).
  - Redesigned `fragment_s01_sign_in.xml` with top brand header (`timecapsule` + `A MOMENT, KEPT.`), handcrafted envelope illustration, "Hello, again." headline, outlined inputs with mail/lock icons, 48×48dp password visibility toggle button, terracotta CTA button ("Come on in"), and "New around here? Create an account" link.
  - Redesigned `fragment_s02_register.xml` with circular leaf badge, "YOUR NEXT CHAPTER" eyebrow, "Make room for memories." headline, name/email/password/confirm password inputs, terracotta CTA button ("Create your account"), "Already have an account? Sign in" link, and profile repair mode handling (hides email/passwords and adapts titles when repairing an incomplete profile). Updated footer to "A time capsule for your meaningful moments."
  - Redesigned `fragment_s08_account_settings.xml` with "Your account" header, initial avatar + greeting ("Hello, [DisplayName]."), UID card with monospace UID and 48×48dp one-tap clipboard copy button, display name update card, "A little permission" section with Location & Notifications system permission status badges + "Open system settings →" link, info banner, "Seal a new moment (S-04)" navigation button, and "Sign out of this session" button. Updated location description to "For verifying location lock conditions when unlocking".
- **Fragment Wiring & Pre-Merge Fixes:**
  - `S01SignInFragment.kt`: Added password visibility toggle with dynamic accessibility content descriptions (`cd_show_password` / `cd_hide_password`), field-specific validation error message (`err_email_password_required`), and wired `btnGoToRegister` / `btnSignIn`.
  - `S02RegisterFragment.kt`: Added `onGoToSignInListener`, password visibility toggle with dynamic accessibility descriptions, field-specific validation error messages (`err_required_field`, `err_name_required`), and adapted profile repair layout visibility.
  - `S08AccountSettingsViewModel.kt` & `S08AccountSettingsFragment.kt`: Resolved profile-editing race conditions by guarding `checkProfile()` callback when `etDisplayName` has focus or active user edits, tracking `lastSubmittedName` to update header display with confirmed submitted text on update success, and disabling `btnUpdateProfile` while update operations are in flight.
  - `S08AccountSettingsViewModel.kt` & `S08AccountSettingsFragment.kt`: Resolved profile-editing race conditions by guarding `checkProfile()` callback when `etDisplayName` has focus or active user edits, invalidating delayed profile lookups via `activeProfileCheckToken` when a newer update starts, tracking `lastSubmittedName` to update header display with confirmed submitted text on update success, and disabling `btnUpdateProfile` while update operations are in flight.
  - `S08AccountSettingsViewModelTest.kt`: Added 7 unit tests covering profile check, profile repair, duplicate submission prevention, delayed profile lookup invalidation, submitted name retention, state clearing, and sign-out.
  - `MainActivity.kt`: Wired `onGoToSignInListener` on `S02RegisterFragment`.

### Checks and verification

- `:app:assembleDebug`: PASS (`BUILD SUCCESSFUL`)
- `:app:testDebugUnitTest`: PASS (76 unit tests passed across project, 0 failed)
- `:app:lintDebug`: PASS (0 errors)
- Emulator runtime verification: Deployed app to `emulator-5554`, captured screenshots, verified S-01, S-02, and S-08 rendering against Stitch wireframes, verified navigation flow (S-01 ↔ S-02, S-08 ↔ S-04/S-01), verified Auth error messages, field-specific validation, password show/hide content descriptions, UID copy to clipboard, system settings intent launch, and profile update race prevention.

### Human input and remaining work

User J confirmed task ownership and manual testing. Personal reflections, teammate PR review by User G, and human merge into `main` remain for the students to complete.

## 2026-11-10-governance — Readiness, ownership, and resumable work

- **Owner:** User J, explicitly selected by the requester for this coordination task.
- **Environment/model:** Documentation coordination outside Android Studio; exact displayed model name unavailable in session metadata. No Android application feature coding was performed.
- **Status:** Prepared for parent integration and human PR review; not merged.
- **Requested outcome:** Prevent premature or overlapping implementation and make interrupted sessions resumable.
- **Prompt summary:** Synchronize contracts, enforce critical dependencies, strengthen Issue outcomes, preserve coursework rules, and open governance work for manual merge. Concurrent agent work was explicitly permitted.
- **References:** [Issue #7](https://github.com/theguardianofthebed/Time-Capsule/issues/7) (historical governance), [draft PR #24](https://github.com/theguardianofthebed/Time-Capsule/pull/24) (owner-log proposal), [PR #29](https://github.com/theguardianofthebed/Time-Capsule/pull/29) (foundation dependency). New coordination PR URL will be recorded after publication.

### Work performed

The governance agent updated root instructions, contribution/workflow guidance, Issue/PR templates, and four repository workflow skills. It ported the compatible owner-specific log structure from PR #24 onto the current task branch without editing that PR or inventing historical entries. Weekly coursework files remain separate.

Added the eight-gate readiness guide, evidence example, a read-only validator, failure-case tests, and a resumable handoff template. The validator consumes the dependency manifest prepared by the roadmap agent. It checks declared records, freshness, branch/base identity, merge and test-commit ancestry, implementation paths, and recorded ownership conflicts. It does not authenticate remote evidence, install CI, configure branch protection, or prevent arbitrary local coding. The coordinating agent owns contract/Issue/presentation integration and final publication records.

### Checks and corrections

- `python3 -m unittest discover -s tests -p test_readiness.py -v`: PASS, 13 tests in disposable local Git repositories. Cases include unmerged prerequisites, unrelated test commits, stale evidence, missing implementation inspection, missing paths, failing checks, conflicting claims, wrong branch/owner, cycles, and malformed manifests.
- `python3 tools/check_readiness.py --validate-manifest`: PASS against the prepared dependency manifest.
- Example #10 evidence: BLOCKED with nonzero exit, as intended; placeholders, missing approval evidence, and a declared unmerged prerequisite cannot grant readiness. This is a negative test template, not a claim about the current state of PR #29.
- `git diff --check`: PASS at the governance checkpoint.
- Review corrections strengthened malformed-manifest handling, verification-commit ancestry, and current behavior inspection after possible reverts. Exact full-tree equivalence permits applicable tested evidence to carry forward across squash/rebase merges; a different tree still blocks.
- Android build/device/backend checks: NOT RUN for this governance change. Existing application evidence was not represented as newly executed.

### Human input and remaining work

User J confirmed task ownership. Human diff review, a different teammate's PR review, personal understanding, manual fixes, and course submission are not claimed. The parent integration must record the actual PR and final validation, reconcile overlapping policy proposals with PR #24 through human review, and leave merge to a human. No live Atlas changes, deployments, or paid resources were made by this subtask.

### 10 November 2026 freshness correction

The coordinating agent observed PR #29 merged at 00:56 KST into main commit `c60d866cdc9794af97db129c946b66711270f6b9`; the governance task did not perform that merge. Issue #9 remains open. PR #29 records build checks and 25 passing unit tests. #10 still needs applicability review of that evidence against #9’s bounded responsibilities, classification of the announced unspecified follow-up problem, and explicit acting owner/different reviewer/current-preflight evidence. Emulator integration is required only where necessary for bounded foundation behavior; downstream backend acceptance is not silently made a #9 requirement. The readiness example and handoff distinguish the satisfied merge gate from those remaining gates. Earlier draft-state observations described the initial inspection, not the current PR state.


## 2026-11-10-coordination — Contract, Issues and roadmap

- **Owner:** User J, explicitly confirmed for this task. Period: 10 November 2026, Asia/Seoul. Scope: MVP coordination and governance.
- **Requested work:** Read the supplied mission, synchronize the repository contract with the TimeCapsule Atlas, strengthen Issues, expose dependencies/capabilities and prepare an academic presentation. Concurrent agent work was authorized.
- **Sources:** All 19 supplied Markdown files, original professor supplement and main course guide, live Atlas r55, repository source/PRs/logs and all 16 implementation Issues. Atlas access succeeded, with 112 elements and 378 relationships.
- **Changes:** Preserved the public r38 reference, captured a full private r55 reference, and added a public r55 verification record; synchronized current scope/toolchain/implementation guidance; prepared the 16 Issue refinements and three visual roadmap views preserving all 52 direct dependencies. Added the readiness report, updated the documentation index and corrected stale starter/MCP descriptions.
- **Delegated contributions:** Source/course audit; contract snapshot and synchronization; Issue/dependency/diagram work; governance/preflight/logging; academic presentation; independent review. All inherited this task's User J owner and separate file boundaries.
- **Freshness correction:** Initial inspection saw PR #29 as draft on main `59f3a0b`. Before publication, the repository changed externally: PR #29 merged at 00:56:26 KST into `c60d866`. The task branch incorporated that main update without altering the application changes. Reports, Issue drafts and diagrams were revised to MERGED, VERIFICATION PENDING. The announced unspecified follow-up and applicability of bounded evidence still need classification; #10 requires fresh owner/reviewer/preflight evidence. No runtime failure or missing full-feature acceptance requirement is invented.
- **Checks:** 13 readiness-validator tests passed; the dependency manifest parsed and passed graph validation; all 112 snapshot IDs and 378 edges/source provenance were checked; 52 Issue dependencies matched the prior contract; every referenced trace ID exists; changed Markdown links and diff formatting passed; three diagram views and 14 presentation slides were rendered and visually inspected. No application/Gradle file was changed by this task, and no Android/backend/device tests were rerun.
- **Limitations:** Local preflight validates evidence records and Git content, not remote truth or human understanding. Branch-protection read was unavailable through the integration; no CI/protection configuration changed. The supplied mission ends mid-M2, so M3 is marked a recommended demonstration grouping using existing MVP scope.
- **Publication:** The coordinating agent records the real PR URL and Issue readback results below after publication. The PR remains for different-teammate review and manual merge. PR #24's branch/history was preserved; this task does not attribute its earlier work to User J.
- **Human record:** Task ownership is confirmed. Personal review, manual fixes, understanding, teammate approval and course submission remain for the students to report. This factual coordination log is not a completed weekly coursework submission.


### Public snapshot publication limit

Automatic approval review rejected uploading the full private Atlas r55 snapshot to the public repository without explicit public-disclosure consent. The full snapshot was retained privately. The public PR contains revision/count/hash verification metadata, existing public contract summaries and governance changes; it does not contain the full private payload. Publishing that full snapshot remains pending the requester’s explicit approval.


### Publication and final verification

- Opened [PR #30](https://github.com/theguardianofthebed/Time-Capsule/pull/30) against main for different-teammate review and manual merge. Initial published commit: `8ebbac2ec0ca5fab0b4a5fe7d0d783aa7f1a0c92`.
- Updated Issues #9–#23 and #25 (16 total), preserving existing discussion, assignees, titles and state. Each body was checked against its prior content before editing, and the saved response matched the intended body. All include the exact completion-outcome heading and links to the visual roadmap/proposal.
- Verified all 35 published file blob hashes against the local intended files. Final local checks resolved 197 Markdown links, parsed JSON, preserved all 52 dependency edges and found no application/Gradle edits relative to merged main `c60d866`.
- Published a separate 14-slide academic presentation. Its current status reflects merged #29 and pending bounded verification, not the earlier draft observation.
- Full private r55 snapshot saved separately; public upload remains awaiting explicit disclosure approval. No live Atlas write, deployment, paid resource, PR merge or human approval was performed by this task.
- Next human action: review PR #30 and the owner log, designate a different teammate reviewer, and decide manual merge. For application work, classify #9's announced follow-up and perform #10's fresh bounded-evidence/ownership preflight.


## 2026-11-10-pr30-visuals — Presentation and diagrams from the merged contract

- **Owner/period/scope:** User J; 10 November 2026; MVP documentation and presentation.
- **Request:** Prepare the presentation and diagrams using PR #30 as the final/recent contract, after the requester confirmed its merge.
- **Source:** PR #30 merged externally at 01:14:47 KST, main `6ec6215786447882d9aca2e9e9c18d5d0fd8eda0`. This task performed no merge. Its earlier publication/log follow-up remains preserved in branch history.
- **Work:** Refreshed the 14-slide academic presentation, exported all three diagrams as PNG and editable SVG, and updated their repository SVG sources/provenance. All 16 Issues and 52 direct dependencies were read from PR #30's committed manifest. Governance is adopted; feature verification remains separate. M3 stays a recommended demonstration grouping as the source states.
- **Checks:** Diagram renders inspected, PNG decode/SVG XML checked, source node/edge totals confirmed, and modified slide renders inspected. No application or dependency change, extra acceptance pass, deployment or live Atlas edit.
- **Handoff:** A focused documentation follow-up contains the refreshed SVGs and factual delivery record for human review. Personal learning/review and course submission remain unclaimed. Full private r55 public disclosure still awaits explicit approval.


### Parallel layout follow-up

- **Owner/period/scope:** User J; 10 November 2026; MVP documentation, dependent on merged PR #30.
- **Request and change:** Revised only Diagram A to show independent work in parallel columns and shared bottlenecks in one centered lane. #11 and #12 branch after #10; #13 → #14 → #15 runs alongside #16, and both paths join at #17. Existing colors, Issue content, order and PR #30 status snapshot are preserved.
- **Verification:** The renderer checked the exact 16-node/52-edge graph, identified 17 main-path edges, confirmed that parallel Issues have no dependency path between them, and checked all 52 edge IDs in the SVG. PNG/SVG renders were visually inspected; final diff formatting passed. B/C and application files were unchanged. No current-status audit or Android test is claimed.
- **Delivery:** Updated PNG and editable SVG in the diagram bundle; revised repository SVG and this log in [PR #31](https://github.com/theguardianofthebed/Time-Capsule/pull/31). Rows do not impose extra synchronization barriers; readiness evidence still controls implementation starts. Different-teammate review and human merge remain pending.
## 2026-11-10-visual-design-alignment — Screenshot-aligned UI design review proposal

- **Owner / period / scope:** User J; 10 November 2026 (KST); MVP design documentation preparation before Issue #12, not Android implementation.
- **Environment/model:** ChatGPT GPT-6 via authorized GitHub connector and read-only TimeCapsule Atlas access. No Android source was coded using ChatGPT or another coding agent.
- **PR:** [#32](https://github.com/theguardianofthebed/Time-Capsule/pull/32), open for review, not merged. Related Issues #12 and #22; Issue #12 implementation still depends on #10.
- **Request:** Compare the 18 supplied October 14 PNG prototype screenshots to the current DESIGN.md, reconcile visual-document staleness with the smallest necessary edits, preserve section structure, and open a documentation-only PR.
- **Work performed:** Updated only DESIGN.md §3's proposed colors, typography, native component guidance, screenshot coverage and prototype limitations, plus one explanatory note in CURRENT_CONTRACT.md. Checked actual current DD-01/DD-02/DD-07 text in the TimeCapsule Atlas at revision 55; **the live Atlas was not modified**. Original screenshot pixels, rather than unavailable TypeScript/CSS, support the proposed color values.
- **Verification:** Checked main baseline `6ec6215786447882d9aca2e9e9c18d5d0fd8eda0`; inspected the two-file source diff and section headings; confirmed eight-screen/three-screen contracts are preserved and no application or Atlas files changed. Android build, device, backend and formal accessibility checks: NOT RUN (documentation-only). Exact original CSS variables, typography identity and team visual approval: PENDING.
- **Handoff / human inputs:** Review of palette/typography/tab naming by User F and DD-01/DD-07 compatibility by User G is pending. User J's personal review, manual fixes, learning and weekly course submission are not claimed. Do not count this proposed documentation revision as a completed Atlas update.

## 2026-11-10-issue10-recipient-interface-handoff — Issue boundary documentation

- **Owner / period / scope:** User J, 10 November 2026 (KST); MVP documentation/handoff only, on existing Draft PR #33. User G's existing Issue #10 implementation and authorship are retained.
- **Requester-directed implementation allocation:** Issue #10 provides the exact UID lookup and recipient-confirmation interface/state; Issue #12 owns the actual S-05 user-facing recipient selection/confirmation UI. This is an implementation allocation, **not** removal of recipient confirmation from the MVP.

## 2026-11-11-issue12-draft-review-flow — S-04, S-05, and S-06 Stitch Design Integration and Drafting Flow

- **Owner / period / scope:** User J; 11 November 2026; MVP Issue #12 implementation.
- **Environment/model:** Gemini Flash 3.8 / Android Studio Agent Mode.
- **Branch / PR:** Branch `feature/12-draft-review-flow`, PR targeting `main`.
- **Requested work:** Implement GitHub Issue #12 delivering the S-04 → S-05 → S-06 content creation, recipient confirmation, lock selection, and review flow based on the pre-made designs in the Stitch project `TimeCapsule Android UI Design`.
- **Stitch Project & Screen IDs:**
  - Project ID: `projects/15276828764661611137` (`TimeCapsule Android UI Design`)
  - S-04 Content creation: Screen `projects/15276828764661611137/screens/107630f968bd46bc84cdc75a2b2830b9`
  - S-05 Recipient and locks: Screen `projects/15276828764661611137/screens/fe93d8abf5534ff0a443d55ffdfcfaad`
  - S-06 Confirm send: Screen `projects/15276828764661611137/screens/b343b7b4940d4020baa12610db6019bc`
- **Work performed:**
  - Completed eight-gate readiness preflight with `check_readiness.py` (`PREFLIGHT_RECORD_VALID`).
  - Extended `CapsuleDraft` domain model and built `CapsuleDraftViewModel` handling shared draft state across S-04, S-05, and S-06.
  - Implemented `S04ContentDraftFragment` and `fragment_s04_content_draft.xml` (optional title, title pre-unlock disclosure note, multiline body, 5 MiB photo boundary validation, discard draft).
  - Implemented `S05RecipientLockFragment` and `fragment_s05_recipient_lock.xml` (Future Me vs Someone Else exact UID lookup, explicit recipient confirmation, confirmation reset on UID text edit/lookup error/session change, rejection of stale async lookup callbacks, LockMode TIME/LOCATION/TIME_AND_LOCATION, future date/time picker, 150m place selector, disabled review CTA until recipient confirmed).
  - Implemented `S06ConfirmSendFragment` and `fragment_s06_confirm_send.xml` (complete review summary of optional title, recipient name & UID, lock conditions, content summary, downstream Issue #14 send notice, back to edit draft).
  - Wired navigation and listener callbacks in `MainActivity`.
  - Added comprehensive unit tests in `CapsuleDraftViewModelTest`.
- **Checks and results:**
  - `python tools/check_readiness.py --issue 12 --evidence docs/agent-tools/readiness-evidence-issue12.json`: PASS (`PREFLIGHT_RECORD_VALID`)
  - `gradle_build("app:testDebugUnitTest")`: PASS (69 unit tests passed across project, 0 failed)
  - `gradle_build("app:assembleDebug")`: PASS
  - `gradle_build("app:lintDebug")`: PASS (0 errors)

## 2026-11-11-issue12-refinements — Review Findings Corrections & Manual Testing Package

- **Owner / period / scope:** User J; 11 November 2026; MVP Issue #12 refinements and manual test preparation.
- **Environment/model:** Gemini Flash 3.8 / Android Studio Agent Mode.
- **Branch / PR:** Branch `feature/12-draft-review-flow` (pushed to `origin/feature/12-draft-review-flow`). PR merge into `main` deferred until manual testing complete.
- **Fixes applied:**
  1. **S-04 Reachability:** Authenticated app launch defaults directly to S-04 (`navigateToContentDraft()`); added `btnCreateCapsule` ("Create Time Capsule (S-04)") on S-08 Account Settings to guarantee bidirectional navigation without bypassing authentication or adding a 9th screen.
  2. **Removed Fake Locations:** Removed hardcoded `University garden` coordinates (`37.5665, 126.9780`). Selecting Location or Both locks sets `locationTarget = null` and displays truthful subtext: `"Location lock selection is unavailable until Issue #16 (Map and fresh location)."`, blocking progression to S-06 while leaving functional Time-only drafting fully available.
  3. **Actual Content in S-06 Review:** Updated S-06 to display actual draft message text (`tvReviewMessageBody`), optional title, recipient name & UID, unlock date/time, and explicit pre-unlock title disclosure note.
  4. **Stale Recipient Lookup Protection:** Invalidation token (`activeLookupToken++`) is incremented whenever UID input text, recipient mode, or user session changes, instantly rejecting in-flight async lookup responses from stale UID inputs or older queries. Added unit test `staleAsyncLookupCallbackIsRejected_whenUidChangedAndChangedBack`.
  5. **Truthful Photo Handling:** Photo attachment action displays truthful notice: `"Photo attachment is unavailable until Issue #13 (Original photo support)."` while preserving `CapsuleDraft` photo model and unit-test coverage.
  6. **Truthful Bottom Navigation:** "Moments" bottom nav for authenticated users displays a Toast notice (`"Moments list is unavailable until Issue #21"`) instead of redirecting to Sign In.
- **Checks and results:**
  - `py tools/check_readiness.py --issue 12 --evidence docs/agent-tools/readiness-evidence-issue12.json`: PASS (`PREFLIGHT_RECORD_VALID`)
  - `gradle_build("app:testDebugUnitTest")`: PASS (69 unit tests passed, 0 failed)
  - `gradle_build("app:assembleDebug")`: PASS (`BUILD SUCCESSFUL`)
  - `gradle_build("app:lintDebug")`: PASS (0 errors)


- **Documentation changes:** Updated the #10/#12 issue boundaries, PR #33, implementation contract, implementation order, roadmap, design guide, and a dedicated #10→#12 task handoff. Kept the original dependency graph unchanged; no app code or rules were edited.
- **Verification:** Documentation changes and live Issue/PR readback checked; code tests, emulator tests and manual user-flow checks **NOT RUN** for this documentation-only update. Existing PR log reports earlier local test evidence, not a new test execution. Reviewer approval and PR merge remain pending.
- **Next action:** User G and a different teammate review the remaining asynchronous account-switch race in PR #33; assign an Issue #12 implementer/reviewer and begin S-05 UI only after #10 satisfies its merge/preflight gates. No additional coursework learning or human review is claimed by this entry.


## 2026-11-17-issue11-correction-handoff — Ownership and code-review preparation

- **Owner / scope / period:** User J; November 17, 2026; MVP Issue #11 correction handoff and documentation only (no project-code changes). Proposed implementation period November 17–20, after readiness validation.
- **Attribution:** User F created PR #35 and retains the original authored commits. The project member reports that User F accepted a transfer of the remaining correction tasks through the team group chat; the original group-chat message was not directly inspected.
- **GitHub:** [Issue #11](https://github.com/theguardianofthebed/Time-Capsule/issues/11); [PR #35](https://github.com/theguardianofthebed/Time-Capsule/pull/35) remains unmerged and was converted to Draft; GitHub account `theguardianofthebed` was added as a co-assignee while retaining User F's existing assignment.
- **Evidence and review:** Read PR #35 at head `35f793b9864a45f0174aaa26baea04c32cda9a33`, #9/#10/#12 predecessor merges, current `main`, repository AGENTS/workflow/readiness guidance, and the affected TimeCapsule Atlas revision 55 design records. Found exact recipient-directory lookup regression, incomplete `unlockedAt` contract, and prepublication photo-reconciliation gap; tests not rerun.
- **Deliverable:** [Issue #11 correction handoff](../../agent-tools/handoffs/issue-11-user-j-takeover-20261117.md), with bounded fixes, test matrix, known head/base SHAs, and explicit NOT RUN evidence.
- **Verification and blockers:** PR head was 6 commits behind `main` at inspection. The Issue #11 eight-gate readiness manifest/validator has not been executed and is BLOCKED until verified in the approved project coding environment. This task performed no Android Studio coding, Firebase emulator run, build, source edits, merge, deployment or full AC-12 acceptance. No independent human review is claimed.
- **Next action:** User J continues with an existing clean checkout in Android Studio Agent Mode, reconciles main, supplies factual readiness evidence, runs validator and implements bounded Issue #11 corrections. Obtain a different teammate review and human merge only after applicable tests pass.


## 2026-11-17-issue11-capsule-rules-takeover — Capsule Records & Security Rules Completion

- **Owner / scope / period:** User J, explicitly confirmed as takeover implementation owner for Issue #11 per team transfer agreement documented in PR #35 and task prompt. Period: November 17, 2026. MVP Scope.
- **Environment/model:** Android Studio Agent Mode (Gemini Flash 3.8).
- **GitHub:** [Issue #11](https://github.com/theguardianofthebed/Time-Capsule/issues/11); [PR #35](https://github.com/theguardianofthebed/Time-Capsule/pull/35); branch `feature/11-capsule-rules`.
- **References:** Atlas revision 55 read November 17 (ARC-02, ARC-03, ARC-04, ARC-05, AC-12, DD-16, DD-17, DD-23); repository `CURRENT_CONTRACT.md`, `READINESS.md`, `readiness-evidence-issue11.json`, `handoffs/issue-11-user-j-takeover-20261117.md`.

### Work performed

1. **Reconciliation:** Merged current `main` (`2524ae5070477a320e30257a94f23d100e332e98`, including merged Issue #12 and #37) into `feature/11-capsule-rules` without conflicts, preserving User F's original authored commits.
2. **Readiness Preflight:** Executed eight-gate readiness preflight by generating `docs/agent-tools/readiness-evidence-issue11.json` and running `py tools/check_readiness.py --issue 11 --evidence docs/agent-tools/readiness-evidence-issue11.json`, returning `PREFLIGHT_RECORD_VALID` with 0 blockers.
3. **Recipient Directory Access:** Restored `allow get: if signedIn();` on `recipientDirectory/{uid}` in `firestore.rules` to permit authenticated exact-UID recipient lookups by senders while maintaining `allow list: if false;` to block directory listing/enumeration and owner-only profile creation/updates.
4. **One-Way Server-Timestamp Unlock Contract:** Updated `canRecipientUnlock()` in `firestore.rules` to enforce `request.resource.data.unlockedAt == request.time` alongside `request.resource.data.unlocked == true`, restricting affected fields during unlock strictly to `['unlocked', 'unlockedAt']`. Denies client-supplied timestamps, missing timestamps, early unlocks, sender unlocks, relocking, re-unlocking, or changing metadata during unlock.
5. **Photo Prepublication Reconciliation & Immutability:** Updated `storage.rules` to allow `create, update` and `read` for prepublication photo uploads/retries by the authenticated sender (`!capsuleExists(capsuleId)`). Once published (`capsuleExists(capsuleId)` is true), photos become strictly immutable (updates/deletes denied; reads governed strictly by participant authorization and unlock state).
6. **Firebase Emulator Security Tests:** Extended `tests/capsule.rules.test.js` from 29 to 36 test cases covering recipient directory exact get/list denial, unlock `unlockedAt` server-timestamp enforcement, early unlock denial, relock denial, prepublication photo upload/retry/read, photo immutability after publication, and signed-out/unauthorized access.

### Checks and verification

- `py tools/check_readiness.py --validate-manifest`: PASS (`MANIFEST_VALID`)
- `py tools/check_readiness.py --issue 11 --evidence docs/agent-tools/readiness-evidence-issue11.json`: PASS (`PREFLIGHT_RECORD_VALID`)
- `npx firebase emulators:exec --project demo-timecapsule "npm test"`: PASS (36 unit tests passed, 0 failed, 100% pass rate)
- `./gradlew app:testDebugUnitTest`: PASS (76 Android JVM unit tests passed across project, 0 failed)

### Human input and remaining work

User J confirmed task ownership and execution. Independent teammate PR review by User G or User F and human merge into `main` remain for the team to complete. PR #35 remains in Draft state until review.


## 2026-11-22-issue13-code-review-corrections — Original Photo Support Review Corrections

- **Owner / scope / period:** User J (Junwon), explicitly confirmed as correction owner for Issue #13 based on User G's original implementation. Period: November 22–24, 2026. MVP Scope.
- **Environment/model:** Android Studio Agent Mode (Gemini Flash 3.8).
- **GitHub:** [Issue #13](https://github.com/theguardianofthebed/Time-Capsule/issues/13); [PR #39](https://github.com/theguardianofthebed/Time-Capsule/pull/39); branch `feature/13-original-photo`.
- **References:** FR-04, FR-08, FR-12; ARC-02, ARC-06, ARC-07; DD-04, DD-18, DD-25; AC-02, AC-13, AC-14.

### Work performed

1. **Correction A (Session-Generation Stability & Selection/Persistence Consistency):** Fixed `restoreSessionIfAuthenticated()` in `AccountSessionManager.kt` to check `sessionTracker.currentSession().uid != activeUid` before calling `startSession()`. Refactored `selectPhoto()`, `removePhoto()`, `discardDraft()`, `persistPhotoDraft()`, and `restorePhotoDraft()` in `CapsuleDraftViewModel.kt` to revalidate `isCurrentPhotoOp()` across suspension boundaries (including `photoSaveMutex.withLock`), reconcile journals when an operation becomes obsolete after persistence, prevent stale operations from overwriting newer drafts, and preserve retained bytes when journal rollback status is uncertain. Separated new photo adoption from previous-photo cleanup errors.
2. **Correction B (Frozen-Photo Journal Failure Handling):** Added thread safety (`synchronized(access)`) to `PhotoDraftJournal.kt` and preserved PR #40's `replaceIfCurrent` and `clearIfCurrent` methods. Updated `freeze()` in `OriginalPhotoUploads.kt` to fail closed on malformed/unreadable journals, returning typed failure (`OperationResult.Unavailable`) without overwriting or replacing an uncertain frozen identity.
3. **Correction C (Photo UI Text Update):** Updated `s04_subtext_photo` and `s04_photo_notice` in `app/src/main/res/values/strings.xml` to `"Attach one JPEG or PNG photo (up to 5 MiB)."` accurately describing active photo attachment support.
4. **Unit Test Suite:** Added 3 session restoration tests, 1 same-account upload ack test, 2 fail-closed/corrupted journal freeze tests, and 10 deterministic coroutine race tests using `CompletableDeferred` in `CapsuleDraftViewModelTest.kt`.

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (119 JVM unit tests passed, 0 failed)
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `npx firebase emulators:exec --project demo-timecapsule "npm test"`: PASS (40 security rules tests passed, 0 failed)
- `git diff --check`: PASS (clean)

### Human input and remaining work

User J confirmed task ownership and execution. Independent teammate PR review by User G or User F and manual testing follow separately.


## 2026-11-22-issue14-code-review-corrections — Capsule Publication & Recovery Review Corrections

- **Owner / scope / period:** User J (Junwon), explicitly confirmed as correction owner for Issue #14 based on User G's original implementation. Period: November 22–24, 2026. MVP Scope.
- **Environment/model:** Android Studio Agent Mode (Gemini Flash 3.6).
- **GitHub:** [Issue #14](https://github.com/theguardianofthebed/Time-Capsule/issues/14); [PR #40](https://github.com/theguardianofthebed/Time-Capsule/pull/40); branch `feature/14-capsule-publication`.
- **References:** FR-02, FR-03, FR-08, FR-09, FR-11, FR-12; ARC-04, ARC-06; DD-14, DD-15, DD-16, DD-21; AC-02, AC-03, AC-14.

### Work performed

1. **Main Branch Integration & Conflict Resolution:** Safely integrated latest `main` (`bb5fcf2`, including PR #39 merge) into `feature/14-capsule-publication` via merge commit `0f8a055`. Resolved conflicts in `AccountSessionManager.kt`, `PhotoDraftJournal.kt`, and `CapsuleDraftViewModel.kt` without losing Issue #13's session/photo safety fixes or Issue #14's publication/recovery contracts.
2. **Ambiguous Photo Save & Cancellation Safety Corrections:** Refactored `selectPhoto()` in `CapsuleDraftViewModel.kt` so every uncertain save outcome inspects `input.restore(uid)` before releasing bytes, preventing candidate photo deletion if the journal on disk references the photo. Replaced cancellation test in `CapsuleDraftViewModelTest.kt` with `commitToJournalFollowedByCancellation_entersCancellationHandlerAndPreservesPhoto` throwing `CancellationException` inside the persistence coroutine and asserting `restoreCount > 0`. Added `commitToJournalFollowedByIOException_preservesJournalRecordAndRetainedPhoto`, `failedJournalReadDuringCleanupSafetyCheck_failsClosedAndPreservesPhoto`, and updated `obsoleteOperationDuringSave_doesNotOverwriteNewerDraft` to use a single shared storage instance to prove competing operations over the same logical journal do not overwrite newer drafts.
3. **Replacement Cleanup Directory Assertion Fix:** Updated `replacementFailsSafelyOnJournalWriteFailure()` in `PublicationDraftRecoveryTest.kt` to compute and inspect the real replacement directory (`digest("sender")` / `digest("new")`), verifying candidate copy creation prior to failure, candidate deletion after failure, original photo preservation, and journal integrity.
4. **Contract Verification:** Verified all 13 required publication/recovery contract properties across `CapsulePublicationCoordinator`, `FrozenOperationJournal`, `FirestoreCapsulePublication`, `CapsulePublicationPayload`, `FrozenCapsuleOperation`, and `CapsuleDraftViewModel`.

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (201 JVM unit tests passed, 0 failed)
- `./gradlew :app:assembleDebug`: PASS (`BUILD SUCCESSFUL`)
- `./gradlew :app:assembleDebugAndroidTest`: PASS (`BUILD SUCCESSFUL` — compiled test APK)
- `./gradlew :app:connectedDebugAndroidTest`: PASS (35 Android instrumentation tests passed on live emulator-5554 with Firebase Emulators)
- `./gradlew :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `npx firebase emulators:exec --project demo-timecapsule "npm test"`: PASS (42 security rules tests passed, 0 failed)
- `git diff --check`: PASS (clean)

### Human input and remaining work

User J confirmed task ownership and execution. Independent teammate PR review by User G or User F and manual testing follow separately.


## 2026-11-24-issue17-current-conditions — Evaluate current time-and-location conditions without a past-visit latch (Final Review Corrections)

- **Owner / scope / period:** User J (Junwon), explicitly confirmed as implementation owner for Issue #17. Period: November 24, 2026. MVP Scope.
- **Environment/model:** Android Studio Agent Mode (Gemini 3.8 Flash).
- **GitHub:** [Issue #17](https://github.com/theguardianofthebed/Time-Capsule/issues/17); task branch `feature/17-current-conditions` (pushed to `origin/feature/17-current-conditions`).
- **References:** FR-06, FR-07, NFR-04; ARC-05; DD-05; AC-06; T-19, T-20, T-42.

### Work performed

1. **Pure Domain Condition Evaluator (`ConditionEvaluator.kt`):** Implemented `ConditionEvaluator.evaluate(mode, target, sample, nowElapsedRealtimeNanos)` evaluating client-side conditions for `TIME`, `LOCATION`, and `TIME_AND_LOCATION` (BOTH) modes. Reuses `LocationQualifier.qualify` to check distance ($\le 150\text{ m}$), sample age ($0..\!60\text{ s}$), and horizontal accuracy ($\le 100\text{ m}$). Does NOT persist or latch past states (`locationOK`/`timeOK`). Separated pure location eligibility check and removed `STALE_SAMPLE` as a time failure representation.
2. **Server-Authoritative Recipient Unlock Requirements (`RecipientUnlockRequirements.kt`, `FirestoreAdapter.kt`, `DefaultFirestoreAdapter.kt`):** Implemented `readRecipientUnlockRequirements(capsuleId, recipientUid)` returning `Source.SERVER` metadata. **P0 Correction:** Rejects entire unconfirmed local mutation or cached metadata snapshot (`doc.metadata.hasPendingWrites() || doc.metadata.isFromCache`), returning `OperationResult.Unavailable("Capsule metadata is unconfirmed or cached.")`. Skips location acquisition completely for server-confirmed unlocked records.
3. **Atomic Location & Time Transaction (`DefaultFirestoreAdapter.verifyRecipientUnlock`):** Extended `verifyRecipientUnlock` transaction to handle `LOCATION`, `BOTH`, and `TIME_AND_LOCATION` lock modes inside `runTransaction`. Evaluates `LocationQualifier.qualify` using fresh `clockNanosSupplier()` on EVERY transaction retry execution without performing GPS inside the callback. Updates `unlocked = true` and `unlockedAt = FieldValue.serverTimestamp()` inside the transaction. Server rules enforce `request.time >= unlockAt` for TIME and BOTH modes. Rejects malformed location records missing `radiusM` or with `radiusM != 150.0`.
4. **Repository Location Orchestration (`DefaultCapsuleRepository.verifyUnlock`):** Injected optional `locationAdapter` and monotonic `clockNanosSupplier` into `DefaultCapsuleRepository`. **P1 Correction:** Rechecks `isFineLocationGranted()` AND `isLocationAvailable()` both before and after sample acquisition, prior to transaction submission. **P1 Correction:** Removed `System.currentTimeMillis() * 1_000_000L` wall-clock fallback, relying strictly on monotonic `SystemClock.elapsedRealtimeNanos()`.
5. **UI & DI Wiring (`MainActivity.kt`):** Updated `MainActivity` container initialization to pass `locationAdapter` to `DefaultCapsuleRepository`.
6. **Automated Verification Suite:** Written `ConditionEvaluatorTest.kt` (7 JVM unit tests covering truth table combinations for T-19 and boundary limits for T-42). Extended `DefaultCapsuleRepositoryUnlockTest.kt` (260 JVM unit tests passed covering TIME, LOCATION, BOTH modes, pending-write rejection, mid-flight permission/service revocation, sample expiration during retry, malformed target rejection, lost response reconciliation, and session races). Extended `FirebaseEmulatorUnlockIntegrationTest.kt` (42 security rules tests passed covering real LOCATION-only unlock, real BOTH mode truth table combinations `F/F`, `F/T`, `T/F`, `T/T`, prior visit departure before deadline, and staying inside across deadline without re-entry T-20). Executed Android instrumentation integration test suite on live Android emulator `emulator-5554` connected to Firebase Emulators (`:app:connectedDebugAndroidTest`): **35 PASSED, 0 FAILED**.

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (260 JVM unit tests passed across project, 0 failed)
- `cmd /c npx firebase emulators:exec --project demo-timecapsule "npm test"`: PASS (42 security rules tests passed, 0 failed)
- `./gradlew :app:connectedDebugAndroidTest`: PASS (35 Android instrumentation tests passed on live `emulator-5554` with Firebase Emulators, 0 failed)
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `py tools/check_readiness.py --issue 17 --evidence docs/agent-tools/readiness-evidence-issue17.json`: PASS (`PREFLIGHT_RECORD_VALID`)
- `git diff --check`: PASS (clean)

### Human input and remaining work

User J confirmed task ownership and execution. Independent teammate PR review by User G and manual testing follow separately. Personal student review and understanding statements remain pending confirmation by User J.


## 2026-11-24-issue18-foreground-recovery — Recheck unlock through the supported foreground recovery path

- **Owner / scope / period:** User J (Junwon), explicitly confirmed as implementation owner for Issue #18. Period: November 24, 2026. MVP Scope.
- **Environment/model:** Android Studio Agent Mode (Gemini 3.8 Flash).
- **GitHub:** [Issue #18](https://github.com/theguardianofthebed/Time-Capsule/issues/18); task branch `feature/18-foreground-recovery`.
- **References:** FR-09, NFR-05; ARC-05, ARC-07; DD-08; AC-07; T-24, T-25, T-32.

### Goal

Implement one shared foreground recovery path (`ForegroundRecoveryCoordinator`) triggered by app-open, return to foreground (`onResume`), and explicit UI Retry actions. Coalesce overlapping publication recovery for `(uid, sessionGeneration)` and recipient unlock checks for `(uid, sessionGeneration, capsuleId)`. Call only existing authoritative repository methods (`recoverPublications()`, `reconcile()`, `verifyUnlock()`), remove completed flights to ensure retries acquire fresh server and location evidence, validate session tracker to discard stale callbacks on account switch or session generation change, and integrate with `MainActivity`, `AppDependencyContainer`, `ViewModelFactory`, and `CapsuleDraftViewModel`.

### Requests and prompts

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Execute Issue #18 implementation & update logs | "Implement GitHub Issue #18, 'Recheck unlock through the supported foreground recovery path,' on theguardianofthebed/Time-Capsule... Acting student: User J... Also update w3 log of User J as well after implementation." | Complete prompt specification detailing readiness preflight, foreground recovery coordinator rules, session generation coalescing, UI integration, test suite requirements, handoff, and log updates. |

### What the agent changed and verified

- **Foreground Recovery Coordinator (`ForegroundRecoveryCoordinator.kt`):**
  - Created `ForegroundRecoveryCoordinator` to coordinate and coalesce foreground recovery across app-open, return to foreground (`onResume`), and explicit UI Retry actions.
  - Coalesces overlapping publication recovery for `(uid, sessionGeneration)`.
  - Coalesces overlapping recipient unlock checks for `(uid, sessionGeneration, capsuleId)`.
  - Coalesces overlapping send outcome reconciliation for `(uid, sessionGeneration, capsuleId)`.
  - Removes completed flight entries upon completion (including failures) so subsequent retries acquire fresh server and location evidence.
  - Revalidates session tracker before returning results to callers and discards stale callbacks on account switch or session generation changes.
  - Exposes `checkForeground(knownCapsuleIds)` as a clean entry point for S-03 refresh and S-07 detail open to be wired in Issue #21 when those screens exist.
  - Does not hold strong references to Activity or Fragment instances.
- **Android Lifecycle & Container Integration:**
  - Wired `ForegroundRecoveryCoordinator` into `AppDependencyContainer`, `MainActivity`, and `ViewModelFactory`.
  - `MainActivity.onResume()` triggers `triggerForegroundRecovery()` when user is authenticated.
  - Post-login checks in `MainActivity` (S-01 sign-in success and S-02 register success) trigger `triggerForegroundRecovery()`.
  - `CapsuleDraftViewModel.syncSessionState()` receives latest container repository and recovery coordinator on Fragment `onResume()` / Activity recreation, preventing stale container or session generation references on device rotation.
- **Automated Verification Suite:**
  - Written `ForegroundRecoveryCoordinatorTest.kt` (13 JVM unit tests passed covering app-open/resume/detail/Retry same path, two simultaneous same-ID coalescing, different UID or new session generation isolation, waiter cancellation, failed/offline result fresh retry, zero known IDs, time-only before/after deadline, combined location invalid/permission denied/previous visit not latched, confirmed unlock idempotent non-relock, saved uncertain send reconciliation without implicit republication, and activity/fragment recreation state preservation).
  - Total 273 JVM unit tests passed (`:app:testDebugUnitTest`).
  - Clean debug APK build passed (`:app:assembleDebug`).
  - Clean Android lint passed (`:app:lintDebug`).

### Review Corrections Applied (November 25, 2026)

- **P1-01 Single-Flight Registration Safety:** Implemented `startSingleFlight` using a short registration `Mutex`, lazy `Deferred` creation, and identity-safe map removal (`map.remove(key, started)`). Guarantees exactly one underlying operation for overlapping identical keys, immediate completion safety, map eviction upon completion/failure, fresh subsequent retries, and isolation between waiters.
- **P1-02 Session Drift Protection:** Bound every operation and aggregate `checkForeground` request to an immutable `SessionSnapshot`. Updated `ForegroundRecoveryCoordinator`, `CapsuleRepository`, `DefaultCapsuleRepository`, and `CapsulePublicationCoordinator` (`recoverPublications`, `reconcile`, `publish`, `retry`, `abandonForNewDraft`) to accept and validate `targetSession` across dispatcher transitions, journal reads, and network calls.
- **P1-03 Application Dependency Container Lifetime:** Created `TimeCapsuleApplication` holding a process-wide `AppDependencyContainer`. `MainActivity.onCreate()` retrieves `(application as TimeCapsuleApplication).container`, ensuring `SessionTracker`, `AccountSessionManager`, `DefaultCapsuleRepository`, and `ForegroundRecoveryCoordinator` are 100% identical instances across Activity recreation/rotation. Added `ForegroundRecoveryLifecycleTest` in AndroidTest using `ActivityScenario.recreate()`.
- **P1-04 Bounded Shared Flight Lifetime & Retry Recovery:** Wrapped underlying repository calls with `withTimeoutOrNull(responseTimeoutMillis)` (15,000 ms). Stalled calls complete cleanly with `OperationResult.Unavailable` and are evicted from the single-flight map, enabling immediate fresh Retries.
- **P1-05 Firebase Initialization Order:** Updated `TimeCapsuleApplication` to run `FirebaseConfig.setupDebugEmulatorConnection` before creating operational adapters or accessing Firebase SDK singletons. Leaves `container == null` and displays fail-closed error UI if Firebase initialization fails.
- **Automated Verification Suite:** 284 JVM unit tests passed (`:app:testDebugUnitTest`), 42 Firebase security rules tests passed (`npm test`), clean debug APK build passed (`:app:assembleDebug`), clean AndroidTest APK build passed (`:app:assembleDebugAndroidTest`), clean Android lint passed (`:app:lintDebug`).

### Checks and results

- `./gradlew :app:testDebugUnitTest`: PASS (284 JVM unit tests passed across project, 0 failed)
- `npm test`: PASS (42 security rules tests passed, 0 failed)
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `py tools/check_readiness.py --issue 18 --evidence docs/agent-tools/readiness-evidence-issue18.json`: PASS (`PREFLIGHT_RECORD_VALID`)
- `git status`: PASS (clean on `feature/18-foreground-recovery`)

### My review and understanding

- **What I reviewed:** Pending student review and personal confirmation by User J.
- **What I can explain:** Pending student review and personal confirmation by User J.
- **Feedback and next step:** Teammate PR review by User G and manual testing on device/emulator before human merge into `main`.

## 2026-11-25-issue19-local-notifications — Attempt account-safe local notifications after confirmed first unlock

- **Owner / scope / period:** User J (Junwon), explicitly confirmed as implementation owner for Issue #19. Period: November 25, 2026. MVP Scope.
- **Environment/model:** Android Studio Agent Mode (Gemini 3.8 Flash).
- **GitHub:** [Issue #19](https://github.com/theguardianofthebed/Time-Capsule/issues/19); task branch `feature/19-local-notifications`.
- **References:** FR-10, NFR-02; ARC-06, ARC-07; DD-09, DD-24; AC-08; T-26, T-32.

### Goal

Implement account-safe local notifications for confirmed first unlocks without turning `Availability.AVAILABLE` into false alerts. Introduce `UnlockVerificationOutcome` (`FIRST_UNLOCK_CONFIRMED`, `ALREADY_UNLOCKED`, `RECONCILED_AVAILABLE`). Implement `NotificationAttemptStore` persisting markers (`ABSENT`, `PENDING_PERMISSION`, `ATTEMPTED`) in a backup-excluded JSON file. Implement `UnlockNotificationCoordinator` enforcing session validity, decision table logic, and error isolation. Implement `NotificationAdapter` & `DefaultNotificationAdapter` using Android NotificationChannel and immutable PendingIntent with Uri payload. Declare `POST_NOTIFICATIONS` in `AndroidManifest.xml` and integrate runtime permission request launcher in S-08 Account Settings fragment. Implement `NotificationEntryValidator` for untrusted tap intent re-validation.

### Requests and prompts

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Execute Issue #19 implementation & update w3 log | "Issue #19 Implementation and Engineering Handoff... update w3 log of user J as user J is in charge of this session after implementation" | Detailed handoff document specifying architecture, decision table, deduplication, PendingIntent safety, test requirements, and log update. |

### What the agent changed and verified

- **Unlock Outcome Provenance (`UnlockVerificationOutcome.kt`, `FirestoreAdapter.kt`, `CapsuleRepository.kt`):**
  - Introduced `UnlockConfirmationKind` (`FIRST_UNLOCK_CONFIRMED`, `ALREADY_UNLOCKED`, `RECONCILED_AVAILABLE`) and `UnlockVerificationOutcome`.
  - Updated `FirestoreAdapter` and `DefaultFirestoreAdapter` to return `verifyRecipientUnlockDetailed` with exact confirmation provenance.
  - Updated `CapsuleRepository` and `DefaultCapsuleRepository` to expose `verifyUnlockOutcome(capsuleId, targetSession)`.
- **Persistent Notification Attempt Store (`NotificationAttemptStore.kt`):**
  - Implemented `NotificationAttemptStore` storing opaque keys `(accountUid, capsuleId)` with states `ABSENT`, `PENDING_PERMISSION`, `ATTEMPTED`.
  - Implemented thread-safe `atomicallyClaimAttempt` ensuring at most one claim per candidate.
  - Excludes all private message body, photo, email, location, or credential data.
  - Durably persists entries in backup-excluded `notification-attempts.json` file.
- **Session-Safe Notification Coordinator (`UnlockNotificationCoordinator.kt`):**
  - Implemented `UnlockNotificationCoordinator` consuming `verifyUnlockOutcome` results.
  - Enforces decision table:
    - `FIRST_UNLOCK_CONFIRMED` + allowed -> claims attempt and posts notification.
    - `FIRST_UNLOCK_CONFIRMED` + denied -> records `PENDING_PERMISSION` candidate.
    - `ALREADY_UNLOCKED` / `RECONCILED_AVAILABLE` + `PENDING_PERMISSION` candidate + allowed -> posts notification once.
    - `ALREADY_UNLOCKED` + `ABSENT` -> suppresses historical notification.
  - Validates `SessionSnapshot` identity before claiming attempt and posting.
  - Isolates notification/storage exceptions so unlock results are never corrupted or reversed.
- **System Notification Adapter & Channel (`NotificationAdapter.kt`, `DefaultNotificationAdapter.kt`):**
  - Implemented `DefaultNotificationAdapter` creating channel `capsule_unlock_channel`.
  - Posts generic alert ("A capsule is ready.") without disclosing private message or photo data.
  - Sets lockscreen visibility to `NotificationCompat.VISIBILITY_PRIVATE`.
  - Builds explicit `PendingIntent` targeting `MainActivity` with `ACTION_NOTIFICATION_TAP`, `FLAG_IMMUTABLE`, and unique `data` URI (`timecapsule://notification/$accountUid/$capsuleId`) to prevent PendingIntent overwriting across capsules.
- **Untrusted Entry Intent Re-validation (`NotificationEntryValidator.kt` & `MainActivity.kt`):**
  - Implemented `NotificationEntryValidator` revalidating incoming tap intents against active session UID and authoritative repository `verifyUnlockOutcome` check.
  - Handled cold start (`onCreate`) and `onNewIntent` in `MainActivity`.
- **Runtime Permissions & Manifest (`AndroidManifest.xml`, `S08AccountSettingsFragment.kt`):**
  - Declared `POST_NOTIFICATIONS` permission in `AndroidManifest.xml`.
  - Added runtime permission request launcher (`ActivityResultContracts.RequestPermission`) in `S08AccountSettingsFragment` for API 33+ (Android 13 Tiramisu).
- **Automated Verification Suite:**
  - Written `NotificationAttemptStoreTest.kt` (8 unit tests).
  - Written `UnlockNotificationCoordinatorTest.kt` (7 unit tests).
  - Written `NotificationEntryValidatorTest.kt` (5 unit tests).
  - Total 305 JVM unit tests passed (`:app:testDebugUnitTest`).
  - Clean debug APK assemble passed (`:app:assembleDebug`).
  - Clean Android lint passed (`:app:lintDebug`).

### Checks and results

### P1 & P2 Review Corrections Applied (November 25, 2026)

- **P1-01 Durable Notification Attempt Claims:** Updated `NotificationAttemptStore.kt` to write updates via atomic temporary-file replacement and verify `saveToFile()` success before returning `true` on attempt claims. Reverts in-memory state on write failure. Replaced colon key concatenation with structured URL-encoded keys (`uid:<uid>:capsule:<capsuleId>`). Added unwritable storage test in `NotificationAttemptStoreTest.kt`.
- **P1-02 Read-Only Notification Tap Authorization:** Introduced `checkUnlockStatus(capsuleId, targetSession)` in `CapsuleRepository` and `DefaultCapsuleRepository`, which authoritatively checks `readRecipientUnlockRequirements` without executing an unlock transaction, acquiring location samples, or mutating backend state. Updated `NotificationEntryValidator` to call `checkUnlockStatus`. Added regression test verifying locked capsules do not trigger unlock transactions on notification tap.
- **P1-03 Conservative Fallback Provenance:** Updated interface default implementations in `CapsuleRepository.kt` and `FirestoreAdapter.kt` to map legacy `verifyUnlock` / `verifyRecipientUnlock` success to `ALREADY_UNLOCKED` (conservative, non-notifying fallback) instead of `FIRST_UNLOCK_CONFIRMED`.
- **P1-04 Account-Switch Invalidation & Functional Cancellation:** Implemented functional account notification cancellation in `DefaultNotificationAdapter.cancelAccountNotifications(uid)`. Added `UnlockNotificationCoordinator.invalidateAccount(uid)` and wired `AccountSessionManager.onAccountCleared` to cancel posted notifications and clear attempt markers upon logout/switch.
- **P1-05 Complete Permission Evaluation:** Updated `areNotificationsPermitted()` in `DefaultNotificationAdapter` to evaluate app-wide notification settings, API 33+ `POST_NOTIFICATIONS` runtime permission, and API 26+ channel importance (`IMPORTANCE_NONE`). Updated `S08AccountSettingsFragment` to open system app/channel notification settings via `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.
- **P1-06 Distinguish Attempts from Permission-Blocked Non-Attempts:** Introduced `NotificationPostResult` enum (`NOT_ATTEMPTED_PERMISSION_DENIED`, `NOT_ATTEMPTED_CHANNEL_BLOCKED`, `ATTEMPTED_SUCCESS`, `ATTEMPTED_FAILED`). `UnlockNotificationCoordinator` records/preserves `PENDING_PERMISSION` on non-attempts, preserving candidate retryability, and claims `ATTEMPTED` only on actual posting attempts.
- **P2 Correctness & Maintainability:** Built Uri data payloads safely with `Uri.Builder()`, prevented duplicate notification intent processing in `MainActivity`, rechecked active session snapshot before returning valid entry result, moved strings to `res/values/strings.xml`, added KDoc, and re-threw `CancellationException`.

### Second Review Corrections Applied (November 25, 2026)

- **1. Durable Claim-Before-Post Ordering:** Enforced exact claim-before-post ordering in `UnlockNotificationCoordinator.kt`. `attemptStore.atomicallyClaimAttempt(uid, capsuleId)` executes FIRST using atomic journal persistence. Notification posting executes ONLY after successful claim persistence. If posting returns a definite non-attempt (`NOT_ATTEMPTED_PERMISSION_DENIED` or `NOT_ATTEMPTED_CHANNEL_BLOCKED`), `restorePendingPermissionIfClaimed` restores `PENDING_PERMISSION` marker.
- **2. Reused Atomic Journal Persistence:** Adapted `FrozenOperationJournal`'s atomic persistence pattern (`File.createTempFile`, `stream.fd.sync()`, `Files.move(..., StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)`) in `NotificationAttemptStore.kt`. Removed destination-deleting fallback. Failed loads or corrupt records set `persistentLoadFailed` and fail claims closed without clearing memory state.
- **3. Corrected Legacy Fallback Provenance:** Corrected `FirestoreAdapter.kt` default interface fallback `verifyRecipientUnlockDetailed` to map legacy `verifyRecipientUnlock` success to `ALREADY_UNLOCKED` (conservative non-notifying fallback), eliminating false `FIRST_UNLOCK_CONFIRMED` alerts from legacy code.
- **4. Process Recreation Account Cancellation:** Updated `DefaultNotificationAdapter.cancelAccountNotifications` to query OS `NotificationManager.activeNotifications` on API 23+ in addition to memory tracking, cancelling matching tag prefixes (`capsule_unlock:<encodedUid>:`) across process restarts. Connected all session invalidation paths in `AccountSessionManager` (`signOut`, `signIn`, `signUpAndCreateProfile`, `restoreSessionIfAuthenticated`, `checkAndHandleStaleCallback`) to trigger account cleanup.
- **5. Complete UI & Lifecycle Fixes:** Added duplicate intent key tracking in `MainActivity.kt` with `onSaveInstanceState` / `onCreate` state restoration. Updated `S08AccountSettingsFragment` to evaluate channel importance (`IMPORTANCE_NONE`) and route to `Settings.ACTION_APP_NOTIFICATION_SETTINGS`. Moved all user-facing strings into `res/values/strings.xml`.
- **6. Regression Verification:** Added 3 new unit tests in `UnlockNotificationCoordinatorTest.kt` (310 total passed in `:app:testDebugUnitTest`). Clean debug APK, test APK, and Android lint (`:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`).

### Final Focused Review Corrections Applied (November 25, 2026)

- **1. Malformed Journal Recovery:** Updated `loadFromFile()` in `NotificationAttemptStore.kt` to validate structure and enum constants strictly. Malformed lines set `persistentLoadFailed = true`. `clearAccount` is guarded by `if (persistentLoadFailed) return` to prevent overwriting corrupted persistent files with empty/partial in-memory state. Added `corrupted or malformed file fails claims closed and preserves file` unit test.
- **2. Session Invalidation Wiring:** Connected all session invalidation and account replacement paths in `AccountSessionManager.kt` (`signOut`, `signIn`, `signUpAndCreateProfile`, `restoreSessionIfAuthenticated`, `checkAndHandleStaleCallback`) to invoke `onAccountCleared`, cancelling active notifications and clearing attempt store markers. Same-session restoration (`currentUid == activeUid`) preserves in-flight work. Added `testSignOutTriggersOnAccountClearedCallback` unit test.
- **3. UI & Lifecycle Intent Fixes:** Updated intent processing in `MainActivity.kt` so that intent key tracking only retains successful/valid authorizations, ensuring transient entry failures do not permanently block subsequent legitimate notification taps. Verified system notification settings route in `S08AccountSettingsFragment.kt` and moved remaining user-facing strings to `res/values/strings.xml`.

### Minimal Final Correction Applied (November 25, 2026)

- **1. Account Invalidation Ordering:** Corrected invalidation ordering in `AccountSessionManager.kt` (`signIn`, `signUpAndCreateProfile`). `sessionTracker.startSession(newUid)` is executed FIRST, and `onAccountCleared(oldUid)` is invoked immediately after. Ensures the old session snapshot is invalidated in `SessionTracker` before notification cleanup runs, blocking any in-flight old-session callbacks. Corrected `checkAndHandleStaleCallback` to clean up the UID being invalidated. Added `testAccountReplacementInvalidatesOldSessionBeforeOnAccountClearedCallback` unit test in `AccountSessionManagerTest.kt`.
- **2. Notification Permission Settings:** Verified system notification settings routing in `S08AccountSettingsFragment.kt` via `openSystemNotificationSettings()`.
- **3. Verification:** Executed full test suite (313 JVM unit tests passed), clean debug build, lint, and test APK build.

### Checks and results

- `./gradlew :app:testDebugUnitTest`: PASS (313 JVM unit tests passed across project, 0 failed)
- `./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `py tools/check_readiness.py --issue 19 --evidence docs/agent-tools/readiness-evidence-issue19.json`: PASS (`PREFLIGHT_RECORD_VALID`)
- `git status`: PASS (clean on `feature/19-local-notifications`)

### My review and understanding

- **What I reviewed:** Pending student review and personal confirmation by User J.
- **What I can explain:** Pending student review and personal confirmation by User J.
- **Feedback and next step:** Teammate PR review by User G and manual testing on device/emulator before human merge into `main`.










## 26 November 2026 · Issue #20 tenth review

User J continued the MVP account-storage work on PR #46 from `7cc60c3`. User G is the designated reviewer. [PR #46](https://github.com/theguardianofthebed/Time-Capsule/pull/46)

- Carried the originating session generation through publication, compose drafts, original photos, frozen uploads and retained content. Generation changes and protected file commits use the same lock. Removed the UID-only retention overloads.
- Updated account sign-in, sign-up, logout, restoration and late callbacks to record and drain durable cleanup obligations. The application and integration tests share the account-manager wiring. A pending UID survives cleanup of a different account.
- Verified interrupted logout against real files, including the marker before invalidation, deletion of protected content, opaque compaction and subsequent sign-in. Paused photo and publication operations were released after same-UID reauthentication.
- The first JVM run exposed four UI fixture failures. Connecting the transition dispatcher to the test scheduler made the intended ordering reproducible. Final pinned checks passed with 353 JVM tests, both debug APKs and lint reporting 0 errors and 144 warnings on the fresh checkout. Issue #20 readiness passed.
- User G's review, User J's device tests and personal log confirmation are pending. [Implementation](https://github.com/theguardianofthebed/Time-Capsule/commit/7eab1b9dd245cbc2ce03fa899fabde4895e36ecb) · [Handoff](../../agent-tools/handoffs/issue-20-account-retention-cleanup-20261126.md)

## 26 November 2026 · Issue #20 eleventh review

User J continued PR #46 from `83cbe1f`. User G is the human reviewer. MVP scope covers FR-01, FR-12, NFR-01, ARC-06 and DD-18.

- Exercised text-only, empty-text photo-only and mixed capsules through the repository with the server offline. Missing files, a substituted photo and a disk write failure have distinct tested outcomes. Legacy photo entries need an authorized online refresh.
- Cleared compose and review fields before a self-send could commit. A lost acknowledgment keeps the same ID available for retry after reconciliation fails, throws, times out or returns no record.
- Used one sampled Android JPEG/PNG decoder for selection, download and offline validation. Added a photo digest and an explicit offline-availability result. Verified the post-retention session check in the committed repository code.
- Reduced opaque journal records to identity, digest and recovery evidence. Verified legacy migration and rejection of payload reconstruction.

On `0c18e07adc0786cdd1055a9f2985f870414fc230`, Gradle 9.6.0, AGP 9.3.0, JDK 25 and SDK 37 passed all four pinned checks. The JVM suite ran 355 tests across 39 suites with zero failures, errors or skips. Both debug APKs built. Lint reported 0 errors and 145 warnings, including one version-catalog style warning for the test-only Robolectric dependency. Robolectric 4.18.1 ran the production Android image decoder in native graphics mode on API 35. Issue #20 readiness returned `PREFLIGHT_RECORD_VALID`.

The Firestore and Storage emulator suite passed all 42 tests on `540a1586de7cae8e45d6d71bad871b62c12a1b66` with local emulator traffic excluded from the proxy. That checkout differed only in the Gradle declaration of the same test dependency. The Android instrumentation APK was built, but no device was attached and no device test result is claimed. User G's review and the previously scheduled device scenarios T-02, T-29 and T-44 remain pending.


## Feature or task: Issue #21 Add account-scoped lists, locked details and permanent re-view

- **Task and purpose:** Implementation of Issue #21 (Add account-scoped lists, locked details and permanent re-view), delivering S-03 Capsule Lists, S-07 Capsule Detail, participant-scoped Firestore queries, role-aware protected content reads (Author Copy vs Recipient), offline retention listing and relaunch re-view (T-29, T-30), and MainActivity navigation with notification tap entry, with User J as acting implementation owner and User G as reviewer.
- **Session date(s):** November 26, 2026.
- **Issue and PR:** [Issue #21](https://github.com/theguardianofthebed/Time-Capsule/issues/21), task branch `feature/21-capsule-lists-detail`.
- **Requirement/design references:** DD-06, DD-28; FR-08, FR-11, FR-12; NFR-05; AC-03, AC-09, AC-11; T-07, T-08, T-27, T-28, T-29, T-30, T-35.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini 3.8 Flash.

### Goal

Implement complete bounded scope for Issue #21: S-03 Capsule Lists (Received and Sent tabs, stale/offline indicator, unresolved send recovery routing), S-07 Capsule Detail (locked details, unlocked letter content, photo rendering, Author Copy sender view, retry action), server-authoritative participant metadata queries (`getSentCapsuleSummaries`, `getReceivedCapsuleSummaries`, `getCapsuleSummary`), role-aware protected reads in `DefaultCapsuleRepository`, offline retention entry enumeration (`listVerifiedRecipientEntries`) supporting process relaunch re-view (T-29, T-30), and MainActivity navigation wiring with notification tap intent routing.

### Requests and prompts

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Execute Issue #21 implementation instruction & update w3 log | "Read the attached TimeCapsule_Issue21_Gemini_Implementation_Prompt.md in full and execute it as the implementation instruction for Issue #21... User J, update agent w3 log... check Stitch project TimeCapsule Android UI Design" | Comprehensive prompt with exact preflight, wireframe inspection requirements, repository boundaries, and regression testing constraints. |

### What the agent changed and verified

- **Data Models (`Capsule.kt`, `CapsuleListsSnapshot.kt`):**
  - Extended `CapsuleSummary` with safe metadata fields (`title`, `createdAt`, `creationZone`, `hasPhoto`, `unlockedAt`, `senderDisplayName`, `recipientDisplayName`) while keeping existing positional/named constructors backwards compatible. Protected text and photo bytes remain strictly excluded from summary models.
- **Participant Query & Firestore Adapter Layer (`FirestoreAdapter.kt`, `DefaultFirestoreAdapter.kt`):**
  - Implemented `getSentCapsuleSummaries(senderUid)` and `getReceivedCapsuleSummaries(recipientUid)` executing equality queries on `capsules` via `Source.SERVER`.
  - Implemented `getCapsuleSummary(capsuleId, uid)` performing exact-ID metadata reads with participant verification (`uid == senderId || uid == recipientId`).
  - Enforced `!doc.metadata.hasPendingWrites()` and `!doc.metadata.isFromCache` on all document queries, rejecting unconfirmed local overlays and cached records.
- **Offline Retention Listing & Re-view (`LocalRetentionAdapter.kt`, `DefaultLocalRetentionAdapter.kt`):**
  - Extended `LocalRetentionAdapter` with `listVerifiedRecipientEntries(targetSession)` to enumerate verified account-scoped retained content directories (`noBackupFilesDir/retained-content/<hashed-uid>/<capsule-id>/`).
  - Validates `verified-marker.properties` (v1/v2), matching UID, capsule ID, valid timestamp, and required text/photo file completeness before returning retained entries, enabling offline re-view after process relaunch while signed in (T-29, T-30).
- **Repository Role & Authorization Gate (`CapsuleRepository.kt`, `DefaultCapsuleRepository.kt`):**
  - Implemented `lists(uid, targetSession)` fetching participant lists from server and falling back to verified local retention when offline.
  - Implemented `getSummary(capsuleId, targetSession)` with participant check and offline fallback.
  - Refactored `readContent(capsuleId, targetSession)` to evaluate participant roles:
    - **Author Copy (`senderUid == activeUid && senderUid != recipientUid`):** Directly reads protected content via `firestoreAdapter.readProtectedContent`. Never executes an unlock transaction, never writes recipient retention markers, and never mutates recipient unlock state.
    - **Recipient (`recipientUid == activeUid`):** Checks existing server/local unlock state. If locked, evaluates unlock conditions or triggers `ForegroundRecoveryCoordinator.verifyUnlock`. Saves verified content to local retention upon successful read.
    - **Unrelated User / Unauthenticated:** Rejects access with `OperationResult.Denied`.
- **ViewModels & Presentation State (`S03CapsuleListViewModel.kt`, `S07CapsuleDetailViewModel.kt`, `ViewModelFactory.kt`):**
  - Implemented `S03CapsuleListViewModel` managing tab selection (Received/Sent), session-scoped list loading, publication recovery, and foreground unlock checks via `ForegroundRecoveryCoordinator`.
  - Implemented `S07CapsuleDetailViewModel` managing role-aware detail fetching, unlock condition evaluation, and protected content display state.
  - Extended `ViewModelFactory` to instantiate both ViewModels cleanly with required dependencies.
- **Native Views & Navigation (`S03CapsuleListFragment.kt`, `S07CapsuleDetailFragment.kt`, `MainActivity.kt`):**
  - Created native XML layouts (`fragment_s03_capsule_list.xml`, `item_capsule_summary.xml`, `fragment_s07_capsule_detail.xml`) adhering strictly to Stitch UI wireframes and design tokens.
  - Created `S03CapsuleListFragment` and `S07CapsuleDetailFragment` using ViewBinding and lifecycle-aware state collection (`repeatOnLifecycle(STARTED)`).
  - Updated `MainActivity` navigation callbacks, centralizing fragment attachment, bottom navigation ("Moments" -> `navigateToCapsuleList()`), and notification tap intent routing (`NotificationEntryResult.Valid` -> `navigateToCapsuleDetail(capsuleId)`).
- **Automated Verification Suite:**
  - Written `CapsuleListsAndDetailRepositoryTest.kt` (6 unit tests passed covering sent/received list queries, non-matching UID denial, participant summary lookup, Author Copy read without unlock transaction, recipient lock enforcement, and offline retention fallback).
  - Written `S03CapsuleListViewModelTest.kt` (2 unit tests passed covering list loading and tab selection).
  - Written `S07CapsuleDetailViewModelTest.kt` (2 unit tests passed covering Author Copy direct read and recipient locked reason formatting).
  - Total **365 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Clean debug APK build passed (`./gradlew :app:assembleDebug`).
  - Clean AndroidTest APK build passed (`./gradlew :app:assembleDebugAndroidTest`).
  - Clean Android lint passed (`./gradlew :app:clean :app:lintDebug`).
  - Preflight readiness check passed (`py tools/check_readiness.py --issue 21 --evidence docs/agent-tools/readiness-evidence-issue21.json` -> `PREFLIGHT_RECORD_VALID`).

### Checks and results

- `./gradlew :app:testDebugUnitTest`: PASS (365 JVM unit tests passed across project, 0 failed)
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`: PASS (`BUILD SUCCESSFUL`)
- `./gradlew :app:clean :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `py tools/check_readiness.py --issue 21 --evidence docs/agent-tools/readiness-evidence-issue21.json`: PASS (`PREFLIGHT_RECORD_VALID`)
- `git status`: PASS (clean on `feature/21-capsule-lists-detail`)

### My review and understanding

- **What I reviewed:** Pending student review and personal confirmation by User J.
- **What I can explain:** How `DefaultCapsuleRepository` uses `getCapsuleSummary` to differentiate Author Copy from Recipient roles, how `DefaultLocalRetentionAdapter.listVerifiedRecipientEntries` enables process relaunch re-view when offline (T-29, T-30), and how `S03CapsuleListViewModel` and `S07CapsuleDetailViewModel` isolate session state using `requestToken` checks across coroutine suspension points.
- **Feedback and next step:** Teammate PR review by User G and manual testing on device/emulator before human merge into `main`.

### First Review Functional Corrections Applied (28 November 2026)

- **1. Separated Verified Unlock Evidence from Retained Asset Availability (AC-09, FR-12, DD-06, T-30):**
  - Maintained distinct concepts for account-scoped unlock evidence (`verifiedUnlockedAt`), safe summary metadata (`RetainedSummary`), and actual protected file integrity (`readRetainedContent`).
  - Missing or corrupted retained photo/text files no longer hide an unlocked capsule from list enumeration or relock the capsule; returns actionable `OperationResult.Unavailable("Capsule unlocked — content unavailable. Connect and retry.")` allowing online asset re-download.
- **2. Eliminated Fabricated Offline Metadata:**
  - Preserved `title`, `lockMode`, `senderDisplayName`, `recipientDisplayName`, and `createdAt` in `verified-marker.properties`.
  - `listVerifiedRecipientEntries()` uses stored properties from `verified-marker.properties` rather than manufacturing generic titles or defaulting all lock modes to `TIME`. Full backward compatibility with legacy v1/v2 markers preserved.
- **3. Corrected Fallback Eligibility in Repository:**
  - `DefaultCapsuleRepository` list and summary queries check response types strictly: `OperationResult.Denied` and `OperationResult.Invalid` return directly without attempting offline retention fallback.
  - Offline fallback is attempted ONLY when remote queries return `OperationResult.Unavailable` (network/service down).
- **4. Lightweight List Enumeration Without Bulk Loading Protected Bytes:**
  - `listVerifiedRecipientEntries` returns `List<RetainedSummary>` by inspecting `verified-marker.properties` and checking file existence on disk (`isContentAvailable`).
  - Zero text or photo bytes are loaded or decoded during list enumeration, preventing memory pressure on S-03.
- **5. Deduplicated Retention Validation:**
  - Extracted shared internal helper methods (`readRetainedContentInternal`, `readRetainedSummaryInternal`) in `DefaultLocalRetentionAdapter`, eliminating duplicate marker parsing while preserving atomic locks, session checks, and SHA-256 integrity checks.
- **6. Mandatory Regression Test Suite:**
  - Added `Issue21FunctionalCorrectionsTest.kt` (7 Robolectric unit tests covering TIME, LOCATION, BOTH offline re-view, missing photo retry, corrupted photo integrity check, network failure vs access denial, cross-account isolation, and lightweight summary enumeration).
  - Total **372 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).

### Second Review Functional & Integration Corrections Applied (28 November 2026)

- **1. Immediate Session-Bound UI Invalidation:**
  - Bound `S03CapsuleListViewModel` and `S07CapsuleDetailViewModel` directly to `SessionSnapshot` flow collection.
  - On account transition or logout, immediately increments `requestToken`, cancels active coroutines, and resets UI state to empty, clearing previous account data, summaries, text, and bitmap references.
- **2. Explicit List Freshness Contract (`ListFreshness`):**
  - Removed title-based string heuristic (`"Previously unlocked"`) completely.
  - Extended `CapsuleListsSnapshot` with explicit `ListFreshness` (`SERVER_CONFIRMED`, `PARTIAL_OFFLINE`, `STALE_OFFLINE`).
  - Preserves partial remote list results (e.g. Received succeeded while Sent failed) without discarding data or displaying false empty lists.
- **3. Independent Verified Unlock Evidence Persistence (T-30 Initial Download Failure):**
  - Implemented `saveVerifiedUnlockMarker` in `LocalRetentionAdapter` and `DefaultLocalRetentionAdapter`.
  - Persists account-scoped, server-confirmed unlock evidence and safe metadata immediately upon online unlock confirmation BEFORE protected content download.
  - Supports T-30 initial-download-failure and process recreation while maintaining complete content file integrity gates.
- **4. Detailed S07 Locked/Unavailable Presentation:**
  - Introduced `DetailStatus` enum covering time/location/both lock states, location permission/service requirements, asset unavailability, and access denial.
  - Actionable retry button provided for recoverable states.
- **5. S03 Pending & Unresolved Publication Section:**
  - Integrated `unresolvedOperations` rendering in `S03CapsuleListFragment` under the Sent tab.
  - Displays pending/failed send states with navigation routes to recovery flow (S-06) without exposing raw drafts or mixing into Received list.
- **6. Safe Bounded Photo Rendering:**
  - Decodes photo bytes off the UI thread (`Dispatchers.IO`) with bounded sample sizing (max 1024px).
  - Recycles/clears bitmap drawables on view destruction or session generation changes.
- **7. Automated Verification Suite:**
  - Total **374 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Debug and AndroidTest APKs built cleanly (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`).
  - Lint passed cleanly with 0 errors (`./gradlew :app:clean :app:lintDebug`).
  - Readiness check passed (`PREFLIGHT_RECORD_VALID`).

### Third Review Functional & Integration Corrections Applied (28 November 2026)

- **1. Restored Atomic Retained-Content Protection:**
  - `DefaultLocalRetentionAdapter.kt` invalidates complete-content authorization (`hasText = false, hasPhoto = false`) BEFORE modifying retained content files on disk.
  - Complete-content authorization is published only AFTER all required text and photo bytes are written and validated. Interrupted replacements preserve unlock evidence without authorizing incomplete bytes.
- **2. Authoritative Server Unlock Timestamps & Persistence Order:**
  - Removed device clock time (`Instant.now()`) for `verifiedUnlockedAt` in `DefaultCapsuleRepository.kt`.
  - Obtains and persists `serverUnlockedAt` from authoritative `RecipientUnlockRequirements.unlockedAt`.
  - Persists `saveVerifiedUnlockMarker` BEFORE starting protected content download.
- **3. Cancellable Session-Bound Image Decoding:**
  - In `S07CapsuleDetailFragment.kt`, maintained cancellable `imageRenderJob`.
  - Revalidates session snapshot, capsule ID, and fragment binding state BEFORE applying decoded bitmap to `ivS07Photo`.
  - Immediately clears image drawables and cancels active decoding on view destruction or session generation reset.
- **4. Complete Metadata Preservation & Access Contracts:**
  - Preserved `unlockAt` and `creationZone` in `verified-marker.properties` and `RetainedSummary`.
  - Authoritative server access denials (`OperationResult.Denied`) block local retention overrides when online.
- **5. Foreground Recovery & Notification Fixes:**
  - In `S03CapsuleListViewModel`, foreground unlock updates both `receivedList` and `sentList` item availability.
  - In `MainActivity.kt`, notification intent deduplication key is recorded ONLY after `validateEntryIntent` succeeds.
  - In `S03CapsuleListFragment`, unresolved publication cards route to `navigateToConfirmSend(pub.capsuleId)` with specific capsule ID.
- **6. Automated Verification Suite:**
  - Total **375 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Debug and AndroidTest APKs built cleanly (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`).
  - Lint passed cleanly with 0 errors (`./gradlew :app:clean :app:lintDebug`).
  - Readiness check passed (`PREFLIGHT_RECORD_VALID`).

### Fifth Review Functional Corrections Applied (28 November 2026)

- **1. Independent Content Commitment Gate (`contentCommitted`):**
  - Implemented explicit `contentCommitted` property in `DefaultLocalRetentionAdapter.kt`.
  - `saveVerifiedUnlockMarker` writes unlock evidence without inheriting or creating `contentCommitted = true`.
  - `saveRetainedContent` invalidates `contentCommitted = false` BEFORE modifying files and sets `contentCommitted = true` only AFTER all files are written and validated.
- **2. Session Validation Boundaries:**
  - Added `sessionTracker.isSessionValid(targetSession)` checks across all asynchronous boundaries in `DefaultCapsuleRepository.kt`.
- **3. Complete Image Render Identity Protection:**
  - In `S07CapsuleDetailFragment.kt`, maintained an explicit `imageRenderToken` and captured session/capsule identity. Revalidates live session, token, capsule ID, and fragment binding before rendering bitmap.
- **4. Production-Path Fault Injection Test:**
  - Implemented testable `faultInjector` seam on `DefaultLocalRetentionAdapter`.
  - `interruptedRetainedContentReplacementPreservesUnlockEvidenceWithoutAuthorizingIncompleteBytes` in `Issue21FunctionalCorrectionsTest.kt` exercises initial complete save -> pre-marker invalidation -> forced I/O fault -> process recreation -> verified unlock evidence survival without incomplete byte authorization -> unlock marker refresh -> clean replacement.
- **5. Automated Verification Suite:**
  - Total **375 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Debug and AndroidTest APKs built cleanly (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`).
  - Lint passed cleanly with 0 errors (`./gradlew :app:clean :app:lintDebug`).
  - Readiness check passed (`PREFLIGHT_RECORD_VALID`).

### Sixth Review Functional Corrections Applied (28 November 2026)

- **1. Preserved Committed Asset Metadata:**
  - `saveVerifiedUnlockMarker` in `DefaultLocalRetentionAdapter.kt` checks `existingCommitted = existingProperties.getProperty("contentCommitted") == "true"`.
  - Preserves `contentCommitted = "true"`, `hasText`, and `hasPhoto` from `existingProperties` if an existing valid complete commitment exists, ensuring unlock evidence refreshes do not corrupt or clear committed assets.
- **2. Session Validation Boundaries:**
  - Verified and added explicit `sessionTracker.isSessionValid(targetSession)` checks at all 4 specified boundaries in `DefaultCapsuleRepository.kt`.
- **3. Complete S03 -> S06 Targeted Recovery Route:**
  - In `CapsuleDraftViewModel.kt`, `recoverPublicationState(targetCapsuleId: String?)` targets the specified capsule ID explicitly.
  - `S06ConfirmSendFragment.newInstance(capsuleId)` passes `targetCapsuleId` to `viewModel.recoverPublicationState(targetCapsuleId)`.
- **4. Automated Verification Suite:**
  - Added `refreshingUnlockEvidenceForCommittedCapsulePreservesBothAssets` test to `Issue21FunctionalCorrectionsTest.kt`.
  - Total **376 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Debug and AndroidTest APKs built cleanly (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`).
  - Lint passed cleanly with 0 errors (`./gradlew :app:clean :app:lintDebug`).
  - Readiness check passed (`PREFLIGHT_RECORD_VALID`).

### Eighth Review Functional Corrections Applied (28 November 2026)

- **1. Task 1 — S03 Unavailable-State & Session Handling:**
  - Implemented `isReceivedUnavailable` and `isSentUnavailable` in `CapsuleListUiState.kt`.
  - Introduced status-only `UnresolvedPublicationSummary` in `CapsuleListUiState`.
  - `S03CapsuleListFragment` renders distinct network unavailable text per tab when offline/unavailable instead of confirmed empty lists.
- **2. Task 2 — Targeted S06 Recovery Safety:**
  - `CapsuleDraftViewModel.recoverPublicationState(targetCapsuleId)` syncs `activeSessionUid` and blocks publication actions (`recoveryBlocked = true`) if requested target is missing.
  - `S06ConfirmSendFragment` enforces `activeTargetCapsuleId` recovery across `onViewCreated`, `onResume`, and `btnCheckPublication`.
- **3. Task 3 — Actual S07 Failure Presentation:**
  - `S07CapsuleDetailViewModel` maps unlock verification failures to `mapUnlockFailureStatus` (`LOCKED_PERMISSION_REQUIRED`, `LOCKED_LOCATION_DISABLED`, `LOCKED_WAITING_LOCATION`, etc.).
  - `S07CapsuleDetailFragment` renders persistent failure cards and provides actionable retry button; image decode failures show persistent error card.
- **4. Task 4 — Retention Compatibility & Metadata:**
  - `DefaultLocalRetentionAdapter.kt` recognizes legacy v1/v2 complete markers while enforcing `contentCommitted == "true"` for version 3.
  - Preserves `contentCommitted = "true"`, `hasText`, and `hasPhoto` when refreshing unlock evidence for an already committed capsule.
- **5. Automated Verification Suite:**
  - Total **376 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Debug and AndroidTest APKs built cleanly (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`).
  - Lint passed cleanly with 0 errors (`./gradlew :app:clean :app:lintDebug`).
  - Readiness check passed (`PREFLIGHT_RECORD_VALID`).

### Tenth Review Bounded Functional Corrections Applied (28 November 2026)

- **1. Recoverable Time-Lock Verification:**
  - In `S07CapsuleDetailViewModel.mapUnlockFailureStatus()`, distinguished time-lock pending status ("unlock time not reached") from non-participant access denial.
  - Maps pending time locks to `DetailStatus.LOCKED_WAITING_TIME` (or `LOCKED_WAITING_BOTH`), preserving locked status badge and enabling verification retry action.
- **2. Display Known Location-Lock Conditions in S07:**
  - In `S07CapsuleDetailFragment.renderUiState()`, formatted and rendered location target coordinates and radius (e.g., `Target location: (37.5665, 126.9780) · 150 m radius`) for `LOCATION` and `TIME_AND_LOCATION` lock modes.
- **3. Offline Self-Send List Consistency:**
  - In `DefaultCapsuleRepository.lists()`, when using offline retained entries, populated matching self-send entries (`senderUid == cleanUid` && `recipientUid == cleanUid`) in both `received` AND `sent` tabs (deduplicated by `capsuleId`).
- **4. Automated Verification Suite:**
  - Total **401 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Debug and AndroidTest APKs built cleanly (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`).
  - Lint passed cleanly with 0 errors (`./gradlew :app:clean :app:lintDebug`).
  - Readiness check passed (`PREFLIGHT_RECORD_VALID`).

### Eleventh Review Final Narrow Corrections Applied (28 November 2026)

- **1. Strict Offline Self-Send Filtering:**
  - Enforced strict `it.senderUid == cleanUid && it.recipientUid == cleanUid` filter in `DefaultCapsuleRepository.lists()`.
- **2. Regression Test Suite Corrections:**
  - Enhanced `offlineSelfSendAppearsInBothTabs` to test A→A in both tabs, B→A in Received only, legacy unknown sender excluded from Sent, and online server-confirmed results un-overwritten.
  - Enhanced `timeLockVerificationReturnsLockedWaitingTime` with exact `OperationResult.Denied("Unlock authorization denied or unlock time not reached.")` response.
  - Added `locationLockConditionDisplaysCoordinatesAndRadius` test for location target rendering.
- **3. Automated Verification Suite:**
  - Total **387 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Debug and AndroidTest APKs built cleanly (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`).
  - Lint passed cleanly with 0 errors (`./gradlew :app:clean :app:lintDebug`).
  - Readiness check passed (`PREFLIGHT_RECORD_VALID`).

### Twelfth Review Restored Regression Coverage Applied (28 November 2026)

- **1. Restored 15 Regression Tests:**
  - Restored all 15 previously deleted regression tests in `Issue21FunctionalCorrectionsTest.kt`: `detailMissingDocumentIsAnAssetFailure`, `retentionStoragePermissionFailureRemainsDenied`, `retentionLegacyMigrationValidatesOriginalRecord`, `retentionMalformedCommittedFlagCannotAdoptRemoteDefault`, `retentionOfflineDetailPreservesParticipantsZoneAndLock`, `retentionUnknownLegacyLockStaysUnknown`, `retentionUnavailableDownloadUsesCommittedBytes`, `retentionAuthoritativeContentErrorsRemainAuthoritative`, `detailScreenPermissionFailurePersists`, `detailScreenOpensLocationSettings`, `detailScreenDecodeFailureKeepsUnlockAndRetry`, `detailWaitForRestorationRetainsIdentity`, `detailDeniedContentDiffersFromMissingAsset`, `tabsImmediateRefreshClearsOldGeneration`, `tabsPendingSendDoesNotHideReceivedEmptyView`.
- **2. Added BOTH Lock & Retry Action Coverage:**
  - Added `bothLockVerificationReturnsLockedWaitingBothAndRetryUnlocks` test.
- **3. Extended Location Target Layout Inspection:**
  - Extended `locationLockConditionDisplaysCoordinatesAndRadius` to inspect `tvS07LockCondition` on `MainActivity` via Robolectric.
- **4. Automated Verification Suite:**
  - Total **403 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Debug and AndroidTest APKs built cleanly (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`).
  - Lint passed cleanly with 0 errors (`./gradlew :app:clean :app:lintDebug`).
  - Readiness check passed (`PREFLIGHT_RECORD_VALID`).





### Seventh Review Functional Corrections Applied (28 November 2026)

- **1. Package A — Publication Recovery:**
  - `S03CapsuleListFragment` passes `pub.capsuleId` to `navigateToConfirmSend(pub.capsuleId)`.
  - `CapsuleDraftViewModel.recoverPublicationState(targetCapsuleId)` strictly targets the requested capsule ID without falling back to unrelated operations.
  - `S06ConfirmSendFragment` preserves `activeTargetCapsuleId` across creation, resume, and check actions.
- **2. Package B — S03/S07 User-Facing Behavior:**
  - `S03CapsuleListFragment` distinguishes unavailable network tabs from confirmed empty tabs.
  - `S07CapsuleDetailViewModel` & `Fragment` render persistent, detailed lock/error states and actionable retry buttons.
  - `MainActivity.onResume()` triggers foreground recovery and calls `refreshScreen()` on active S03 list or S07 detail fragment.
- **3. Package C — Version 3 Marker & Metadata:**
  - Upgraded `MARKER_VERSION = "3"` in `DefaultLocalRetentionAdapter.kt`.
  - Version 3 requires `contentCommitted == "true"`. Legacy v1/v2 records with valid files remain readable.
- **4. Automated Verification Suite:**
  - Total **376 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Debug and AndroidTest APKs built cleanly (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`).
  - Lint passed cleanly with 0 errors (`./gradlew :app:clean :app:lintDebug`).
  - Readiness check passed (`PREFLIGHT_RECORD_VALID`).



### Fourth Review Functional Corrections Applied (28 November 2026)

- **1. Retention Commitment Invalidation Gate:**
  - `DefaultLocalRetentionAdapter.kt` invalidates complete-content authorization (`hasText = false, hasPhoto = false`) BEFORE writing new content files on disk.
  - `saveVerifiedUnlockMarker` writes unlock evidence without copying stale `hasText`/`hasPhoto` flags from previous runs unless explicitly provided as true.
  - `isContentAvailable = (hasText || hasPhoto) && textOk && photoOk` evaluates to `false` for uncommitted or incomplete content files.
- **2. Asynchronous Session Validation Gaps Closed:**
  - `DefaultCapsuleRepository.kt` verifies `sessionTracker.isSessionValid(targetSession)` before and after every suspension point.
  - Authoritative `OperationResult.Denied` and `OperationResult.Invalid` return immediately without local retention overrides.
- **3. Complete Metadata Preservation & Offline Re-view:**
  - Serialized `unlockAt` and `creationZone` into `verified-marker.properties` and restored them into `RetainedSummary` and `CapsuleSummary`.
- **4. S03 Sent & Received Foreground Synchronization:**
  - `S03CapsuleListViewModel.kt` updates matching availability in both `receivedList` AND `sentList` upon foreground unlock confirmation.
- **5. Fault-Injection Regression Test Suite:**
  - `Issue21FunctionalCorrectionsTest.kt` exercises initial complete save -> pre-marker invalidation -> forced I/O fault -> process recreation -> verified unlock evidence survival without incomplete byte authorization -> unlock marker refresh -> successful clean replacement.
- **6. Automated Verification Suite:**
  - Total **375 JVM unit tests passed** (`./gradlew :app:testDebugUnitTest`).
  - Debug and AndroidTest APKs built cleanly (`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`).
  - Lint passed cleanly with 0 errors (`./gradlew :app:clean :app:lintDebug`).
  - Readiness check passed (`PREFLIGHT_RECORD_VALID`).






[Implementation](https://github.com/theguardianofthebed/Time-Capsule/commit/0c18e07adc0786cdd1055a9f2985f870414fc230) · [Handoff](../../agent-tools/handoffs/issue-20-account-retention-cleanup-20261126.md)

## 28 November 2026 · Issue #21 ninth review

User J · MVP · PR #47 · human reviewer User G. Continued from `dc77f318`. Reproduced each defect before changing production code. The 23 added regressions cover selected-publication identity across sessions, independent list query results, persistent S07 recovery actions and verified offline content. SDK-level cases distinguish a denied photo download from an authorized read of a missing protected document.

Final verification passed 399 JVM tests (0 failed or skipped), debug APK assembly, AndroidTest APK assembly, lint (0 errors, 166 warnings), 42 Firebase emulator rules tests, Issue #21 readiness and the whitespace check. APKs were built. On-device instrumentation and manual permission/GPS journeys remain pending.

[Test names, observed failures, corrected production functions and passing assertions](../../agent-tools/handoffs/issue-21-capsule-lists-detail-20261126.md#ninth-review--28-november-2026) · [PR #47](https://github.com/theguardianofthebed/Time-Capsule/pull/47)


## 2026-11-28-issue22-native-ui-recovery — Apply native screen specification and truthful recovery messages

- **Owner:** User J, explicitly confirmed as implementation owner for Issue #22. Reviewer: User G.
- **Environment/model:** Android Studio Agent Mode (Gemini 3.8 Flash).
- **Status:** Implemented, verified with 406 passing JVM unit tests, clean debug build, clean AndroidTest APK build, clean lint (0 errors), task branch `feature/22-native-ui-recovery` prepared from ready `main` commit `a344980a56eae0078567cbbf421188c88ec49a5a`.
- **Requested outcome:** Complete shared UI, navigation, accessibility, and error-recovery implementation across all eight TimeCapsule screens (S-01 through S-08). Correct lock mode presentation on S-06 Confirm Send (especially LOCATION-only and BOTH lock modes), ensure self-send content protection after submission, add loading progress indicators and input validation (email format, password length) on S-01/S-02, enforce accessibility live regions for error/status banners (`android:accessibilityLiveRegion="polite"`), ensure 48dp touch targets, clear old presentation state when rendering different capsules on S-07, and add pure JVM unit tests verifying truthful unlock condition formatting.
- **References:** [Issue #22](https://github.com/theguardianofthebed/Time-Capsule/issues/22); FR-01–FR-12; ARC-01, ARC-04, ARC-05, ARC-07; DD-01, DD-02, DD-03, DD-04, DD-05, DD-06, DD-07, DD-08, DD-09, DD-12, DD-13, DD-14, DD-15, DD-18, DD-19, DD-26, DD-27, DD-28; AC-10; T-38, T-45.

### Work performed

- **S-01 Sign in (`S01SignInFragment.kt`, `fragment_s01_sign_in.xml`):**
  - Added `ProgressBar` `pbLoading` and `android:accessibilityLiveRegion="polite"` to `tvStatusMessage`.
  - Added email format validation (`Patterns.EMAIL_ADDRESS`) and TextWatchers clearing error messages on input modification.
  - Managed button state and progress bar visibility during sign-in attempt.
- **S-02 Register & Profile Repair (`S02RegisterFragment.kt`, `fragment_s02_register.xml`):**
  - Added `ProgressBar` `pbLoading` and `android:accessibilityLiveRegion="polite"` to `tvStatusMessage`.
  - Added field validation: display name non-blank, email format check (`Patterns.EMAIL_ADDRESS`), password length minimum (>=6 chars), and password confirmation match.
  - Added TextWatchers clearing status messages on field edits.
- **S-03 Capsule Lists (`S03CapsuleListFragment.kt`, `fragment_s03_capsule_list.xml`, `item_capsule_summary.xml`):**
  - Added `android:accessibilityLiveRegion="polite"` to `tvStaleBanner` and `tvErrorMessage`.
  - Ensured minimum 48dp touch targets for summary items (`itemCapsuleRoot`).
  - Enhanced list rendering so empty lists distinguish network/stale errors from confirmed empty lists.
- **S-04 Content Draft (`S04ContentDraftFragment.kt`, `fragment_s04_content_draft.xml`):**
  - Explicitly disclosed title behavior (`s04_title_disclosure`: "Visible to the recipient while locked — keep sensitive words inside your letter.").
  - Added `android:accessibilityLiveRegion="polite"` to `tvS04Error`.
  - Preserved draft text and title inputs when photo attachments fail or are removed.
- **S-05 Recipient & Locks (`S05RecipientLockFragment.kt`, `fragment_s05_recipient_lock.xml`):**
  - Added `android:accessibilityLiveRegion="polite"` to `tvS05Error`.
  - Enforced recipient confirmation invalidation on UID text modification.
  - Explicitly distinguished absent recipient (NONEXISTENT) from lookup unavailable (ERROR).
- **S-06 Confirm Send (`S06ConfirmSendFragment.kt`, `fragment_s06_confirm_send.xml`):**
  - **Corrected misleading lock mode fallback:** For LOCATION-only capsules, displays `"Meant to open: At target location (<placeName>)"` instead of `"Meant to open: Instant unlock"`. For BOTH lock mode, displays `"Meant to open: <formattedTime> AND at <placeName>"`.
  - Added `android:accessibilityLiveRegion="polite"` to `tvSendNoticeBanner`.
  - Ensured self-send content protection after submission.
- **S-07 Capsule Detail (`S07CapsuleDetailFragment.kt`, `fragment_s07_capsule_detail.xml`):**
  - Immediately cleared old headline, meta, lock condition, body text, signoff, and banner messages when rendering a different capsule ID.
  - Maintained unlocked state on photo decode failure while explaining missing content.
  - Provided App Details Settings route for location permission recovery when permanently denied.
- **S-08 Account & Settings (`S08AccountSettingsFragment.kt`, `fragment_s08_account_settings.xml`):**
  - Added `android:accessibilityLiveRegion="polite"` to `tvStatusMessage`.
  - Supported one-tap Auth UID clipboard copy with Toast confirmation.
  - Verified system permission status badges and settings navigation links.
- **Unit Test Suite (`S06ConfirmSendLockDisplayTest.kt`):**
  - Added 3 new unit tests verifying truthful unlock condition formatting for LOCATION-only, TIME-only, and BOTH lock modes.
- **Automated Verification Results:**
  - `./gradlew :app:testDebugUnitTest`: PASS (406 passed, 0 failed).
  - `./gradlew :app:assembleDebug`: PASS (`BUILD SUCCESSFUL`).
  - `./gradlew :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors).

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (406 JVM unit tests passed across project, 0 failed)
- `./gradlew :app:assembleDebug :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `git status`: PASS (clean on `feature/22-native-ui-recovery`)

### Human input and remaining work

User J confirmed task ownership and implementation review. Personal reflections, teammate PR review by User G, manual device testing, and human merge into `main` remain for the students to complete.


## 2026-11-28-issue22-third-implementation — Issue #22 Targeted Functional Corrections

- **Owner:** User J, explicitly confirmed as implementation owner for Issue #22. Reviewer: User G.
- **Environment/model:** Android Studio Agent Mode (Gemini 3.8 Flash).
- **Status:** Implemented, verified with 436 passing JVM unit tests, clean debug build, clean AndroidTest APK build, clean lint (0 errors), branch `feature/22-native-ui-recovery` updated on HEAD `1b85e54bbef37e7ce008112cbcc03bff7cb18fc9`. PR: [Draft PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51).
- **Requested outcome:** Apply targeted functional corrections for Issue #22 covering registration failure after Auth creation, complete originating-session protection in S-08, authoritative profile verification boundaries, recovered publication presentation, S-07 failure classification, and navigation transaction completion.
- **References:** [Issue #22](https://github.com/theguardianofthebed/Time-Capsule/issues/22); [PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51); FR-01–FR-12; ARC-01, ARC-04, ARC-05, ARC-07; DD-01, DD-02, DD-03, DD-04, DD-05, DD-06, DD-07, DD-08, DD-09, DD-12, DD-13, DD-14, DD-15, DD-18, DD-19, DD-26, DD-27, DD-28; AC-10; T-38, T-45.

### Work performed

- **Registration Failure after Auth Creation (`DefaultAuthAdapter.kt`, `AccountSessionManager.kt`):**
  - Updated `DefaultAuthAdapter.signUp()` to catch exceptions during optional `user.updateProfile()` after successful `createUserWithEmailAndPassword()`, returning `OperationResult.Success(user.uid)` so the created Auth user session is activated and can transition cleanly into Profile Repair mode without duplicate registration errors.
  - Added regression test `testSignUpAuthSuccessWithProfileWriteFailureActivatesSessionAndEnablesProfileRepair` in `AccountSessionManagerTest.kt`.
- **Originating-Session Protection in S-08 (`S08AccountSettingsFragment.kt`, `S08AccountSettingsViewModel.kt`):**
  - Updated `S08AccountSettingsFragment` to extract originating `SessionSnapshot` from screen arguments (`screenUid`, `screenGeneration`) and pass `origin` to `viewModel.repairProfile(newName, origin)`, `viewModel.signOut(origin)`, and `viewModel.checkProfile(activeUid, origin)`.
  - Updated `S08AccountSettingsViewModel` to validate `accountSessionManager.isSessionValid(origin)` before launching or completing coroutines and pass `origin` to `AccountSessionManager`.
  - Updated `btnCopyUid` click handler to validate that the screen's originating `SessionSnapshot` matches the live session.
  - Added 4 regression tests in `S08AccountSettingsViewModelTest.kt` covering obsolete session generation, different UID, session change between click and coroutine execution, and session change during an async profile operation.
- **Authoritative Profile Verification (`DefaultFirestoreAdapter.kt`, `MainActivity.kt`):**
  - Updated `DefaultFirestoreAdapter.getRecipientProfile()` to reject unconfirmed or cached document snapshots (`doc.metadata.hasPendingWrites() || doc.metadata.isFromCache`), validate document UID consistency (`docUid == cleanUid`), and enforce nonblank display name.
  - Updated `MainActivity.resolveAccountScreen()` with a 5000ms timeout (`withTimeoutOrNull`) around `checkProfile(uid)`. If profile check returns `OperationResult.Unavailable` (network offline/timeout) and `verifiedProfile == snapshot`, preserved `verifiedProfile = snapshot` and kept the screen visible without kicking the user to profile repair.
- **Recovered Publication Presentation (`CapsuleDraftViewModel.kt`):**
  - Updated `CapsuleDraftViewModel.applyRecovered()` to restore `locationName = operation.locationName` (or formatted coordinates) on recovered operations, preventing stale location label leakage from previous drafts.
  - Updated `CapsuleDraftViewModel.recoverPublicationState()` so a missing recovery record for a target capsule ID sets `recoveryBlocked = true` and `statusOnly = true` without setting `publicationPhase = PublicationPhase.FAILED` or allowing `canAbandon = true`.
  - Added regression test `recoveringDifferentOperationClearsStaleLocationLabel` in `S06ConfirmSendLockDisplayTest.kt` and `missingRecoveryRecord_doesNotSetPublicationPhaseFailedOrAllowAbandonment` in `CapsuleDraftViewModelTest.kt`.
- **S-07 Failure Classification (`S07CapsuleDetailViewModel.kt`):**
  - Updated `S07CapsuleDetailViewModel.mapUnlockFailureStatus()` to check `lockMode` context first. If `lockMode == LockMode.TIME`, location-specific statuses (`LOCKED_PERMISSION_REQUIRED`, `LOCKED_LOCATION_DISABLED`) are never returned.
  - Mapped `res is OperationResult.Denied` to `DetailStatus.ACCESS_DENIED`.
  - Added regression test `timeOnlyLock_accountDisabledError_mapsToAccessDenied_notLocationDisabled` in `S07CapsuleDetailViewModelTest.kt`.
- **Navigation Transaction Completion (`MainActivity.kt`):**
  - Updated `MainActivity.replaceFragment()`'s `runOnCommit` callback to re-evaluate `currentOwner = container.sessionTracker.currentSession()`. If the session is no longer authenticated or verified for a protected destination, keeps container invisible and calls `resolveAccountScreen()`.

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (436 JVM unit tests passed across project, 0 failed)
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `py tools/check_readiness.py --validate-manifest`: PASS (`MANIFEST_VALID`)
- `git diff --check`: PASS (clean)

### Human input and remaining work

User J confirmed task ownership and implementation review. Personal reflections, teammate PR review by User G, manual device testing, and human merge into `main` remain for the students to complete.


## 2026-11-28-issue22-fourth-review — Issue #22 Fourth Review Remaining Functional Corrections

- **Owner:** User J, explicitly confirmed as implementation owner for Issue #22. Reviewer: User G.
- **Environment/model:** Android Studio Agent Mode (Gemini 3.8 Flash).
- **Status:** Implemented, verified with 442 passing JVM unit tests, clean debug build, clean AndroidTest APK build, clean lint (0 errors), branch `feature/22-native-ui-recovery` updated. Target PR: [Draft PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51).
- **Requested outcome:** Complete fourth review corrections for Issue #22: fix regression tests (Auth boundary test, recovered location label test, unlock failure classification test), complete unlock failure classification, complete authoritative profile verification, finish navigation transaction protection, and complete S-08 async result protection.
- **References:** [Issue #22](https://github.com/theguardianofthebed/Time-Capsule/issues/22); [PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51); FR-01–FR-12; ARC-01, ARC-04, ARC-05, ARC-07; DD-01, DD-02, DD-03, DD-04, DD-05, DD-06, DD-07, DD-08, DD-09, DD-12, DD-13, DD-14, DD-15, DD-18, DD-19, DD-26, DD-27, DD-28; AC-10; T-38, T-45.

### Work performed

- **Section 2 — Auth Adapter & Profile Write Boundaries:**
  - Added tests in `AccountSessionManagerTest.kt` verifying both outcomes when `user.updateProfile()` or `saveRecipientProfile()` fail during registration.
- **Section 3 — S-08 In-Flight & Result Protection:**
  - Added in-flight submission guard `isSignOutInFlight` in `S08AccountSettingsViewModel.signOut()`.
  - Added session validity checks in `S08AccountSettingsFragment.kt` LiveData observers to ignore stale results.
  - Added duplicate sign-out test in `S08AccountSettingsViewModelTest.kt`.
- **Section 4 — Complete Authoritative Profile Verification:**
  - Updated `DefaultFirestoreAdapter.getRecipientProfile()` to check metadata (`hasPendingWrites() || isFromCache`) BEFORE `doc.exists()`, and validate `docUid.isNotBlank() && docUid == cleanUid` and `displayName.isNotBlank()`.
  - Added unit tests in `AuthAndRecipientTest.kt` covering pending writes, cached snapshots, missing UID, mismatched UID, and blank display name.
- **Section 5 — Production-Path Location Recovery Test:**
  - Added `recoveringDifferentOperationClearsStaleLocationLabelThroughViewModelFlow` in `CapsuleDraftViewModelTest.kt` exercising real ViewModel recovery.
- **Section 6 — Complete Unlock Failure Classification:**
  - Refined `S07CapsuleDetailViewModel.mapUnlockFailureStatus()` so `OperationResult.Unavailable` maps to `DetailStatus.LOCKED_VERIFICATION_FAILED` when network is offline, while `isLocationRequired` failures match specific permission/disabled keywords. Added tests in `S07CapsuleDetailViewModelTest.kt`.
- **Section 7 — Navigation Transaction Protection:**
  - Updated `MainActivity.replaceFragment().runOnCommit` to re-check `currentOwner = container.sessionTracker.currentSession()` and `isProtected(fragment)`.

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (442 JVM unit tests passed across project, 0 failed)
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `py tools/check_readiness.py --validate-manifest`: PASS (`MANIFEST_VALID`)
- `py tools/check_readiness.py --issue 22 --evidence docs/agent-tools/readiness-evidence-issue22.json`: PASS (`PREFLIGHT_RECORD_VALID`)
- `git diff --check`: PASS (clean)

### Human input and remaining work

User J confirmed task ownership and implementation review. Personal reflections, teammate PR review by User G, manual device testing, and human merge into `main` remain for the students to complete.


## 2026-11-29-issue22-fifth-review — Issue #22 Fifth Review Outstanding Corrections

- **Owner:** User J, explicitly confirmed as implementation owner for Issue #22. Reviewer: User G.
- **Environment/model:** Android Studio Agent Mode (Gemini 3.8 Flash).
- **Status:** Implemented, verified with 448 passing JVM unit tests, clean debug build, clean AndroidTest APK build, clean lint (0 errors), branch `feature/22-native-ui-recovery` updated. Target PR: [Draft PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51).
- **Requested outcome:** Complete fifth review outstanding items: commit previously pending changes to `MainActivity.kt` and `S07CapsuleDetailViewModel.kt`, complete unlock failure classification, strengthen navigation transaction completion with `belongsToCurrentSession(fragment)` and `accountCheck?.isActive`, strengthen location label restoration in `CapsuleDraftViewModel.kt`, and add production adapter validation tests.
- **References:** [Issue #22](https://github.com/theguardianofthebed/Time-Capsule/issues/22); [PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51); FR-01–FR-12; ARC-01, ARC-04, ARC-05, ARC-07; DD-01, DD-02, DD-03, DD-04, DD-05, DD-06, DD-07, DD-08, DD-09, DD-12, DD-13, DD-14, DD-15, DD-18, DD-19, DD-26, DD-27, DD-28; AC-10; T-38, T-45.

### Work performed

- **Committed Outstanding File Changes (`MainActivity.kt`, `S07CapsuleDetailViewModel.kt`):**
  - Included modified `MainActivity.kt` and `S07CapsuleDetailViewModel.kt` in git staging and commit.
- **Complete Unlock Failure Classification (`S07CapsuleDetailViewModel.kt`):**
  - Refined `mapUnlockFailureStatus()` to distinguish participant denial (`ACCESS_DENIED`), permission denial (`LOCKED_PERMISSION_REQUIRED`), disabled location services (`LOCKED_LOCATION_DISABLED`), missing/null location fix (`LOCKED_WAITING_LOCATION`), and time pending (`LOCKED_WAITING_TIME` / `LOCKED_WAITING_BOTH`).
- **Complete Navigation Transaction Protection (`MainActivity.kt`):**
  - Updated `replaceFragment().runOnCommit` to check `belongsToCurrentSession(fragment)` and `accountCheck?.isActive == true`, preventing obsolete fragments or premature container reveals during active profile checks.
- **Recovered Location Label Restoration (`CapsuleDraftViewModel.kt`):**
  - Updated `applyRecovered()` to require matching capsule ID AND matching location coordinates before preserving a custom location name.
- **Production Adapter Validation Tests (`AuthAndRecipientTest.kt`):**
  - Added unit tests directly exercising `DefaultAuthAdapter` and `DefaultFirestoreAdapter` production validation.

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (448 JVM unit tests passed across project, 0 failed)
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `py tools/check_readiness.py --validate-manifest`: PASS (`MANIFEST_VALID`)
- `py tools/check_readiness.py --issue 22 --evidence docs/agent-tools/readiness-evidence-issue22.json`: PASS (`PREFLIGHT_RECORD_VALID`)
- `git diff --check`: PASS (clean)

### Human input and remaining work

User J confirmed task ownership and implementation review. Personal reflections, teammate PR review by User G, manual device testing, and human merge into `main` remain for the students to complete.


## 2026-11-29-issue22-sixth-review — Issue #22 Sixth Review Final Outstanding Corrections

- **Owner:** User J, explicitly confirmed as implementation owner for Issue #22. Reviewer: User G.
- **Environment/model:** Android Studio Agent Mode (Gemini 3.8 Flash).
- **Status:** Implemented, verified with 455 passing JVM unit tests, clean debug build, clean AndroidTest APK build, clean lint (0 errors), branch `feature/22-native-ui-recovery` updated. Target PR: [Draft PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51).
- **Requested outcome:** Complete sixth review items: refine unlock failure classification for ambiguous denials vs specific location issues, distinguish malformed recipient profiles from absent recipients, add production adapter tests for `DefaultAuthAdapter` and `DefaultFirestoreAdapter`, and add Robolectric navigation transaction protection tests.
- **References:** [Issue #22](https://github.com/theguardianofthebed/Time-Capsule/issues/22); [PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51); FR-01–FR-12; ARC-01, ARC-04, ARC-05, ARC-07; DD-01, DD-02, DD-03, DD-04, DD-05, DD-06, DD-07, DD-08, DD-09, DD-12, DD-13, DD-14, DD-15, DD-18, DD-19, DD-26, DD-27, DD-28; AC-10; T-38, T-45.

### Work performed

- **Truthful Unlock Failure Classification (`S07CapsuleDetailViewModel.kt`):**
  - Refined `mapUnlockFailureStatus()` so ambiguous backend denials (`OperationResult.Denied`) do not claim definitive time-condition waiting, while specific location failures (`permission`, `location services disabled`, `null fix`) map to their corresponding statuses.
- **Distinguished Malformed Profiles (`CapsuleDraftViewModel.kt`):**
  - Updated `CapsuleDraftViewModel` recipient lookup mapping so confirmed missing documents return `RecipientLookupStatus.NONEXISTENT` ("User not found"), while malformed or invalid profile data returns `RecipientLookupStatus.ERROR` ("Recipient profile is invalid or incomplete").
- **Production Adapter Unit Tests (`DefaultAuthAdapterTest.kt`, `DefaultFirestoreAdapterTest.kt`):**
  - Added unit test suites directly exercising production `DefaultAuthAdapter` and `DefaultFirestoreAdapter` methods.
- **Navigation Transaction Protection Test (`Issue21FunctionalCorrectionsTest.kt`):**
  - Added Robolectric regression test `navigationTransactionSessionChangeDuringRunOnCommitKeepsContainerInvisible` verifying that an asynchronous session change during transaction completion keeps the container invisible and rechecks account state.

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (455 JVM unit tests passed across project, 0 failed)
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `py tools/check_readiness.py --validate-manifest`: PASS (`MANIFEST_VALID`)
- `py tools/check_readiness.py --issue 22 --evidence docs/agent-tools/readiness-evidence-issue22.json`: PASS (`PREFLIGHT_RECORD_VALID`)
- `git diff --check`: PASS (clean)

### Human input and remaining work

User J confirmed task ownership and implementation review. Personal reflections, teammate PR review by User G, manual device testing, and human merge into `main` remain for the students to complete.


## 2026-11-29-issue22-seventh-review — Issue #22 Seventh Review Complete Remaining Functional and Verification Gaps

- **Owner:** User J, explicitly confirmed as implementation owner for Issue #22. Reviewer: User G.
- **Environment/model:** Android Studio Agent Mode (Gemini 3.8 Flash).
- **Status:** Implemented, verified with 463 passing JVM unit tests, clean debug build, clean AndroidTest APK build, clean lint (0 errors), branch `feature/22-native-ui-recovery` updated. Target PR: [Draft PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51).
- **Requested outcome:** Complete seventh review items: refine unlock failure classification for exact production ambiguous string `"Unlock authorization denied or unlock time not reached."` to `DetailStatus.ACCESS_DENIED`, complete production adapter tests in `DefaultAuthAdapterTest.kt` and `DefaultFirestoreAdapterTest.kt`, and verify all Robolectric navigation and recovery flows.
- **References:** [Issue #22](https://github.com/theguardianofthebed/Time-Capsule/issues/22); [PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51); FR-01–FR-12; ARC-01, ARC-04, ARC-05, ARC-07; DD-01, DD-02, DD-03, DD-04, DD-05, DD-06, DD-07, DD-08, DD-09, DD-12, DD-13, DD-14, DD-15, DD-18, DD-19, DD-26, DD-27, DD-28; AC-10; T-38, T-45.

### Work performed

- **Exact Ambiguous Unlock-Denial Handling (`S07CapsuleDetailViewModel.kt`):**
  - Refined `mapUnlockFailureStatus()` so `"Unlock authorization denied or unlock time not reached."` maps to `DetailStatus.ACCESS_DENIED` while genuine time-pending messages map to `LOCKED_WAITING_TIME` / `LOCKED_WAITING_BOTH`.
- **Production Adapter Unit Test Suites (`DefaultAuthAdapterTest.kt`, `DefaultFirestoreAdapterTest.kt`):**
  - Added test suites directly exercising production `DefaultAuthAdapter` exception-handling and `DefaultFirestoreAdapter` document verification methods.
- **Navigation & Recovery Flow Verification (`Issue21FunctionalCorrectionsTest.kt`):**
  - Verified `navigationTransactionSessionChangeDuringRunOnCommitKeepsContainerInvisible` and location label restoration.

### Checks and verification

- `./gradlew :app:testDebugUnitTest`: PASS (463 JVM unit tests passed across project, 0 failed)
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: PASS (`BUILD SUCCESSFUL`, 0 lint errors)
- `py tools/check_readiness.py --validate-manifest`: PASS (`MANIFEST_VALID`)
- `py tools/check_readiness.py --issue 22 --evidence docs/agent-tools/readiness-evidence-issue22.json`: PASS (`PREFLIGHT_RECORD_VALID`)
- `git diff --check`: PASS (clean)

### Human input and remaining work

User J confirmed task ownership and implementation review. Personal reflections, teammate PR review by User G, manual device testing, and human merge into `main` remain for the students to complete.






