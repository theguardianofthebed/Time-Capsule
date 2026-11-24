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







