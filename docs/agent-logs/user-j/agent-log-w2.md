# Agent Log – Week 2 (MM/DD – MM/DD)

- Name / Student ID:
- Model(s) used:
- My changes to the team's Agent Tools this week:

The planned submission date is **20 November 2026**; follow any later professor announcement. Add one feature/task section for each piece of work. Describe your own contribution and use real prompts, results, and examples. If you did not use an agent this week, say so honestly.

Follow [the shared workflow](../../agent-tools/WORKFLOW.md) and [AGENTS.md](../../../AGENTS.md). Remove credentials and private data from any quoted material.

## Task: Issue #12 S-04, S-05, and S-06 Drafting & Review Flow (Stitch Design Integration)

- **Task and purpose:** Implemented GitHub Issue #12 delivering the S-04 → S-05 → S-06 content creation, recipient confirmation, lock selection, and review flow based on the pre-made designs in the Stitch project `TimeCapsule Android UI Design`.
- **Session date(s):** 11 November 2026.
- **Reporting placement:** Moved into Week 2 at User J's request; the original 11 November 2026 session dates and recorded evidence are unchanged.
- **Issue and PR:** Issue #12 (https://github.com/theguardianofthebed/Time-Capsule/issues/12); implementation branch `feature/12-draft-review-flow`.
- **Requirement/design references:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07; ARC-01; DD-01, DD-03, DD-07, DD-12, DD-13, DD-19, DD-26, DD-27; AC-01, AC-02, AC-03, AC-05, AC-06, AC-11.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.8 (Android Studio Agent Mode).

**Goal**

Needed assistance in translating the pre-made Stitch UI designs (`TimeCapsule Android UI Design` project `15276828764661611137`) for S-04, S-05, and S-06 into native Android Views XML + ViewBinding, managing Activity-scoped draft state via `CapsuleDraftViewModel`, removing obsolete global skeleton headers from `MainActivity`, enforcing exact UID recipient directory lookup with explicit user confirmation, invalidating stale lookup callbacks on UID edits or session switches, providing truthful user-facing subtext for downstream boundaries (Issues #13, #14, #16, #21), and writing unit tests for draft validation and lookup token invalidation.

**Requests and prompts**

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Stitch Design Retrieval & Implementation | "TimeCapsule — Issue #12 Implementation (Stitch Design Integration) ... Deliverable: S-04 → S-05 → S-06 content creation, recipient confirmation, lock configuration and review flow ..." | Successfully retrieved Stitch HTML/screens, created `CapsuleDraftViewModel`, S-04/S-05/S-06 Fragments/XML, and passed 67 unit tests. |
| 2 | Review Findings Fixes & UI Cleanup | "TimeCapsule — Issue #12 Final UI Cleanup, Verification & Agent Log Update ... Remove obsolete global placeholder header ... Improve user-facing unavailable-function messages ..." | Removed global skeleton header from `activity_main.xml`, updated user-facing subtext messages, updated `MainActivityTest.kt`, and passed 69 unit tests and lint. |

### What the agent changed and verified

- **Changes:**
  - `app/src/main/res/layout/activity_main.xml` & `MainActivity.kt`: Removed obsolete top global skeleton header (`tvMainTitle` / `tvMainSubtitle`). Fragment container now occupies full application content area. Preserved fail-closed Firebase initialization error view (`tvInitError`). Set authenticated launch default to S-04 (`navigateToContentDraft()`) and added `btnCreateCapsule` on S-08 Account Settings.
  - `app/src/main/java/com/example/timecapsule/domain/model/Capsule.kt`: Extended `CapsuleDraft` with optional `title`, `recipientDisplayName`, `isRecipientConfirmed`, and `locationName`.
  - `app/src/main/java/com/example/timecapsule/ui/draft/CapsuleDraftViewModel.kt`: Implemented Activity-scoped ViewModel holding shared draft state across S-04, S-05, S-06. Increments `activeLookupToken++` on UID text edit, recipient mode change, or session switch to reject stale async lookup responses. Blocks progression if content is empty, recipient is unconfirmed, or Location lock is selected without Issue #16 integration.
  - `app/src/main/java/com/example/timecapsule/ui/s04/S04ContentDraftFragment.kt` & `fragment_s04_content_draft.xml`: Implemented S-04 Content Draft screen (optional title, pre-unlock disclosure note, multiline body, 5 MiB photo boundary validation, truthful photo notice).
  - `app/src/main/java/com/example/timecapsule/ui/s05/S05RecipientLockFragment.kt` & `fragment_s05_recipient_lock.xml`: Implemented S-05 Recipient & Lock Selection screen (Future Me vs Someone Else exact UID lookup, explicit recipient confirmation, confirmation reset on UID text edit, LockMode TIME/LOCATION/BOTH, future date/time picker, disabled CTA until recipient confirmed).
  - `app/src/main/java/com/example/timecapsule/ui/s06/S06ConfirmSendFragment.kt` & `fragment_s06_confirm_send.xml`: Implemented S-06 Review screen (displays actual message text, title, recipient name & UID, unlock time, pre-unlock title disclosure note, back navigation to edit draft, and user-facing notice that sending is unavailable).
  - `app/src/test/java/com/example/timecapsule/ui/draft/CapsuleDraftViewModelTest.kt`: Added 17 unit tests covering text/photo validation, title-only rejection, recipient lookup & explicit confirmation, stale lookup callback rejection on UID change, session switch state reset, and lock mode validation.
  - `app/src/androidTest/java/com/example/timecapsule/MainActivityTest.kt`: Updated instrumented test to verify `fragmentContainer` hosting without obsolete `tvMainTitle`.
- **Checks and results:**
  - `py tools/check_readiness.py --issue 12 --evidence docs/agent-tools/readiness-evidence-issue12.json`: PASS (`PREFLIGHT_RECORD_VALID`)
  - `:app:assembleDebug`: PASS (`BUILD SUCCESSFUL`)
  - `:app:testDebugUnitTest`: PASS (69 unit tests passed across project, 0 failed)
  - `:app:lintDebug`: PASS (0 errors)
  - Emulator runtime walkthrough: S-06 review screen launched successfully on emulator; identified and corrected top global header and developer-oriented text.

### Errors and manual fixes

- The agent updated `CapsuleDraftViewModelTest.kt` when user-facing send notice text was changed from `"Issue #14"` to `"Sending isn't available yet"`. All 69 unit tests passed cleanly.

### My review and understanding

- **What I reviewed:**
  - I reviewed the S-04 → S-05 → S-06 drafting and review flow on the Android emulator and in the codebase.
  - I verified that authenticated users land directly on S-04, can enter a message and optional title, look up a recipient UID, explicitly confirm recipient identity, select a future unlock date/time, and review the actual message text on S-06.
  - I confirmed that the obsolete top global header was removed and that S-06 uses product-friendly language rather than internal GitHub issue numbers.
- **What I can explain:**
  - I understand that an Activity-scoped ViewModel (`CapsuleDraftViewModel` using `requireActivity()`) allows multiple Fragments to share a single draft state without data loss when moving Back/Next.
  - I can explain why exact recipient directory lookup requires an explicit user confirmation step, and how lookup token generation (`activeLookupToken++`) prevents stale asynchronous responses from accidentally confirming an altered UID input.
  - I understand why changing `activity_main.xml` required updating `MainActivityTest.kt` to check `R.id.fragmentContainer` instead of the removed `R.id.tvMainTitle`.
  - I can explain why unavailable downstream operations (photo upload #13, real publication #14, map geofencing #16, lists #21) must display truthful user-facing notices rather than simulating false success.
- **Feedback and next step:**
  - The implementation passes all 69 unit tests, debug build, and lint checks.
  - I will perform the complete manual testing walkthrough using the step-by-step checklist before requesting teammate PR review and human merge.

## Task: Issue #37 S-01, S-02, and S-08 UI Design Correction (Stitch Integration)

- **Task and purpose:** Implemented GitHub Issue #37 to correct the UI of S-01 (Sign In), S-02 (Register & Profile Repair), and S-08 (Account & Settings) to match the connected Stitch wireframes (`TimeCapsule Android UI Design`, project ID `15276828764661611137`) using Kotlin + native Android Views/XML + ViewBinding while preserving account authentication, profile creation/repair, UID display/copy, logout, and session state.
- **Session date(s):** 13 November 2026.
- **Issue and PR:** Issue #37 (https://github.com/theguardianofthebed/Time-Capsule/issues/37); implementation branch `feature/37-ui-design-correction`.
- **Requirement/design references:** FR-01, FR-10; ARC-01, ARC-06; DD-01, DD-03, DD-07, DD-19, DD-24, DD-26; AC-01, AC-10.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.6 (Android Studio Agent Mode).

**Goal**

Translate the pre-made Stitch UI designs for S-01, S-02, and S-08 into native Android Views XML + ViewBinding with high visual fidelity, matching the color palette (`#865B4B` terracotta, `#FAF9F5` warm cream, `#FFFDF9` card surface, `#E8E3DA` border), typography, vector iconography, rounded input fields, hero illustrations, permission status badges, and buttons, while preserving all existing account authentication, registration, missing-profile repair variant, UID copy, profile updates, and sign-out capabilities.

**Requests and prompts**

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Stitch Wireframe Download & Comparison | Download S-01, S-02, and S-08 HTML files from Stitch project `TimeCapsule Android UI Design` (`15276828764661611137`) and compare against existing layouts. | Successfully downloaded Stitch HTML specifications, extracted colors, typography, shapes, and iconography. |
| 2 | UI Implementation & Fragment Wiring | Implement S-01, S-02, and S-08 XML layouts, drawables, strings, colors, and Fragment bindings with password visibility toggle, UID copy, system permission checks, and navigation listeners. | Successfully created all XML layouts, drawables, strings, colors, updated Fragments and `MainActivity.kt`, and passed 69 unit tests and lint. |
| 3 | Emulator Deployment & Verification | Deploy debug build to emulator, verify rendering of all 3 screens, test password visibility toggle, error handling, clipboard copy, and navigation. | Successfully deployed to emulator, inspected `ui_state`, captured screenshots, verified S-01, S-02, and S-08 visual alignment with Stitch. |

### What the agent changed and verified

- **Changes:**
  - `app/src/main/res/values/colors.xml`: Added Stitch design system color tokens (`capsule_bg`, `capsule_card_surface`, `capsule_border`, `capsule_primary` `#865B4B`, `capsule_badge_bg`, `capsule_banner_bg`, `capsule_avatar_bg`, etc.).
  - `app/src/main/res/values/strings.xml`: Added string resources for S-01, S-02, and S-08 headers, titles, hints, subtexts, badges, toasts, and greetings.
  - `app/src/main/res/drawable/`: Created shape drawables (`bg_card_surface.xml`, `bg_button_primary.xml`, `bg_button_outlined.xml`, `bg_badge_success.xml`, `bg_badge_neutral.xml`, `bg_banner_info.xml`, `bg_circle_badge.xml`, `bg_circle_avatar.xml`) and vector icons (`ic_capsule_logo`, `ic_hero_envelope`, `ic_mail`, `ic_lock`, `ic_visibility`, `ic_arrow_right`, `ic_leaf`, `ic_copy`, `ic_location`, `ic_bell`, `ic_logout`, `ic_edit`, `ic_info`).
  - `app/src/main/res/layout/fragment_s01_sign_in.xml`: Redesigned S-01 Sign in layout with brand header, handcrafted envelope illustration, "Hello, again." headline, outlined mail/lock inputs, password visibility toggle button, terracotta CTA button ("Come on in"), and "Create an account" link.
  - `app/src/main/res/layout/fragment_s02_register.xml`: Redesigned S-02 Register layout with circular leaf badge, "YOUR NEXT CHAPTER" eyebrow, "Make room for memories." headline, name/email/password inputs, terracotta CTA button ("Create your account"), "Sign in" link, and profile repair variant mode support.
  - `app/src/main/res/layout/fragment_s08_account_settings.xml`: Redesigned S-08 Account settings layout with "Your account" header, initial avatar + greeting, monospace UID card with copy button, display name update card, Location & Notifications permission status card with badges and system settings link, info banner, "Seal a new moment (S-04)" button, and "Sign out of this session" button.
  - `app/src/main/java/com/example/timecapsule/ui/s01/S01SignInFragment.kt`: Added password visibility toggle and wired email/password authentication observers.
  - `app/src/main/java/com/example/timecapsule/ui/s01/S01SignInFragment.kt`: Added password visibility toggle with dynamic accessibility content descriptions (`cd_show_password` / `cd_hide_password`), field-specific validation error message (`err_email_password_required`), and wired email/password authentication observers.
  - `app/src/main/java/com/example/timecapsule/ui/s02/S02RegisterFragment.kt`: Added `onGoToSignInListener`, password visibility toggle with dynamic accessibility descriptions, field-specific validation error messages (`err_required_field`, `err_name_required`), adapted profile repair layout visibility, and updated footer text.
  - `app/src/main/java/com/example/timecapsule/ui/s08/S08AccountSettingsViewModel.kt` & `S08AccountSettingsFragment.kt`: Resolved profile-editing race conditions by guarding `checkProfile()` callback when `etDisplayName` has focus or active user edits, invalidating delayed profile lookups via `activeProfileCheckToken` when a newer update starts, tracking `lastSubmittedName` to update header display with confirmed submitted text on update success, and disabling `btnUpdateProfile` while update operations are in flight.
  - `app/src/test/java/com/example/timecapsule/ui/s08/S08AccountSettingsViewModelTest.kt`: Added 7 unit tests covering profile check, profile repair, duplicate submission prevention, delayed lookup invalidation, submitted name retention, state clearing, and sign-out.
  - `app/src/main/java/com/example/timecapsule/MainActivity.kt`: Wired `onGoToSignInListener` for `S02RegisterFragment`.
- **Checks and results:**
  - `:app:assembleDebug`: PASS (`BUILD SUCCESSFUL`)
  - `:app:testDebugUnitTest`: PASS (76 unit tests passed across project, 0 failed)
  - `:app:lintDebug`: PASS (0 errors)
  - Emulator runtime walkthrough: Deployed app to `emulator-5554`, verified S-01, S-02, and S-08 visual alignment with Stitch wireframes, tested field-specific validation errors, password show/hide content descriptions, clipboard copy, navigation flow, and profile update race prevention.

### Errors and manual fixes

- Adjusted button background tinting (`app:backgroundTint="@null"`) so Material 3 theme does not override custom terracotta background drawables.
- Replaced generic `err_session_unavailable` on blank field inputs with field-specific validation messages (`err_email_password_required`, `err_required_field`, `err_name_required`).
- Fixed Android notification permission check on API <33 by using `NotificationManagerCompat.from(context).areNotificationsEnabled()`.
- Updated touch targets to 48×48dp minimum for password show/hide buttons, UID copy button, and navigation text links.
- Fixed S-08 profile-editing race condition by tracking submitted name, invalidating delayed in-flight profile checks (`activeProfileCheckToken`), and preventing late profile checks from overwriting in-progress user input.

### My review and understanding

- **What I reviewed:**
  - I reviewed the implemented S-01, S-02, and S-08 screens on the Android emulator and compared them with the Stitch wireframes in `TimeCapsule Android UI Design`.
  - I verified that sign-in, registration, profile repair, UID display/copy, profile display name editing, sign-out, and navigation between screens work properly with field-specific validation errors and 48dp accessibility touch targets.
- **What I can explain:**
  - I understand how native Views XML layouts with custom shape drawables and `app:backgroundTint="@null"` achieve Material 3 design system alignment without introducing Compose or third-party UI libraries.
  - I can explain how `S08AccountSettingsFragment` inspects system permissions using `NotificationManagerCompat.areNotificationsEnabled()` across Android versions and opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
  - I understand how `S02RegisterFragment` adapts its layout for missing-profile repair mode by hiding credentials fields while reusing the same `AccountSessionManager` workflow.
- **Feedback and next step:**
  - The changes pass build, 76 unit tests, lint, and emulator walkthrough.
  - I will submit the PR for teammate review by User G.

## Task: Issue #11 Capsule Records and Security Rules (Takeover Completion)

- **Task and purpose:** Completed GitHub Issue #11 security rules implementation takeover and verification. Restored authenticated exact-UID recipient lookup while denying directory listing, aligned Firestore unlock rules with the one-way `unlockedAt` server-timestamp contract, supported prepublication photo upload/retry/read while preserving post-publication photo immutability, and extended Firebase emulator security tests to 36 passing cases.
- **Session date(s):** 17 November 2026.
- **Issue and PR:** Issue #11 (https://github.com/theguardianofthebed/Time-Capsule/issues/11); PR #35 (https://github.com/theguardianofthebed/Time-Capsule/pull/35); branch `feature/11-capsule-rules`.
- **Requirement/design references:** FR-08, NFR-04; ARC-02, ARC-03, ARC-04, ARC-05; DD-16, DD-17, DD-23; AC-12; T-07, T-21, T-22, T-23, T-33, T-43.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.8 (Android Studio Agent Mode).

**Goal**

Complete Issue #11 security rules implementation takeover on `feature/11-capsule-rules` and PR #35 without rebuilding existing work or losing User F's original authored commits. Reconcile `main` (`2524ae5070477a320e30257a94f23d100e332e98`), run eight-gate readiness preflight, restore signed-in exact UID recipient lookup in `firestore.rules`, align unlock rules with `request.resource.data.unlockedAt == request.time`, support prepublication photo reconciliation in `storage.rules`, and extend Firebase emulator security tests in `tests/capsule.rules.test.js`.

**Requests and prompts**

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Reconcile main & readiness preflight | "Continue TimeCapsule Issue #11 using existing feature/11-capsule-rules branch and PR #35 ... Reconcile main ... run eight-gate readiness preflight ..." | Successfully merged `main` into `feature/11-capsule-rules` and validated readiness with `PREFLIGHT_RECORD_VALID`. |
| 2 | Firestore & Storage security rules alignment | "Restore authenticated exact-UID recipient lookup ... align Firestore unlock rules with agreed one-way unlockedAt server-timestamp contract ... review prepublication photo rules ..." | Updated `firestore.rules` and `storage.rules` to enforce exact lookup, server-timestamp unlock, and photo prepublication retry / post-publication immutability. |
| 3 | Security rules emulator verification | "Extend and run the relevant Firebase emulator security tests, including regression cases ..." | Extended `tests/capsule.rules.test.js` to 36 test cases; all 36 passed under `firebase emulators:exec`. |

### What the agent changed and verified

- **Changes:**
  - `firestore.rules`: Updated `match /recipientDirectory/{uid}` to `allow get: if signedIn();` while retaining `allow list: if false;` and owner-only `create, update`. Updated `canRecipientUnlock()` to require `request.resource.data.unlockedAt == request.time` and restricted affected fields during unlock strictly to `['unlocked', 'unlockedAt']`. Updated `validMetadata()` to allow `unlockedAt` field (must be null/absent at creation).
  - `storage.rules`: Updated `match /capsuleMedia/{senderUid}/{capsuleId}/original` to `allow create, update: if signedIn() && request.auth.uid == senderUid && !capsuleExists(capsuleId) && validImageUpload();` to allow prepublication upload and retry. Updated `allow read` to permit prepublication sender access or published authorized reading (`canReadCapsule`). Retained `allow delete: if false;` and denied post-publication overwrites.
  - `tests/capsule.rules.test.js`: Extended security test suite from 29 to 36 tests. Added tests for exact-UID directory get, directory list denial, signed-out directory access denial, foreign profile write denial, unlock with `unlockedAt` server timestamp, missing/invalid timestamp unlock denial, relock denial, re-unlock denial, prepublication photo upload/retry/read, and post-publication photo immutability.
  - `docs/agent-tools/readiness-evidence-issue11.json`: Recorded eight-gate preflight evidence against selected base `2524ae5070477a320e30257a94f23d100e332e98` and merged dependencies #9 and #10.
  - `docs/agent-tools/handoffs/issue-11-user-j-takeover-20261117.md`: Updated handoff document with actual execution results and test evidence.
- **Checks and results:**
  - `py tools/check_readiness.py --validate-manifest`: PASS (`MANIFEST_VALID`)
  - `py tools/check_readiness.py --issue 11 --evidence docs/agent-tools/readiness-evidence-issue11.json`: PASS (`PREFLIGHT_RECORD_VALID`)
  - `npx firebase emulators:exec --project demo-timecapsule "npm test"`: PASS (36 tests passed, 0 failed, 100% pass rate)
  - `./gradlew app:testDebugUnitTest`: PASS (76 Android JVM unit tests passed across project)

### Errors and manual fixes

- Adjusted `tests/capsule.rules.test.js` photo storage test fixtures so that prepublication uploads run before `seedPhotoCapsule` creates the Firestore document, matching actual production upload-ACK-before-Firestore-batch semantics (ARC-04, DD-16, DD-25).
- Handled Windows PowerShell script execution policy for `npm` by invoking via `cmd /c npx firebase emulators:exec --project demo-timecapsule "npm test"`.

### My review and understanding

- **What I reviewed:**
  - I reviewed the updated `firestore.rules`, `storage.rules`, and `tests/capsule.rules.test.js`.
  - I verified that authenticated users can look up recipient profiles by exact UID without listing the directory.
  - I verified that unlocking a capsule requires setting `unlockedAt: serverTimestamp()` and `unlocked: true`, and that relocking or altering other metadata is strictly denied.
  - I verified that photo uploads can be retried before Firestore publication, but become 100% immutable once published.
- **What I can explain:**
  - I understand why `recipientDirectory` rules must allow `get: if signedIn()` so senders can verify a recipient's public UID before creating a capsule, while denying `list` to prevent directory harvesting.
  - I can explain how `request.resource.data.unlockedAt == request.time` in Firestore rules guarantees that unlock timestamps are set atomically by the server clock, preventing client clock manipulation.
  - I understand how checking `!capsuleExists(capsuleId)` in Storage rules enables prepublication upload retries while enforcing post-publication immutability.
- **Feedback and next step:**
  - The implementation passes all 36 security rules emulator tests, readiness preflight, and Android unit tests.
  - I will update the PR #35 description, keep PR #35 in Draft state, and request independent teammate review from User G or User F before merge.


