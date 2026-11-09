# User J agent log week 1

The planned submission date is **13 November 2026**; follow any later professor announcement. Add one feature/task section for each piece of work. Describe your own contribution and use real prompts, results, and examples. If you did not use an agent this week, say so honestly.

Follow [the shared workflow](../../agent-tools/WORKFLOW.md) and [AGENTS.md](../../../AGENTS.md). Remove credentials and private data from any quoted material.

## Task: PR #27 AppCompat theme compatibility correction

- **Task and purpose:** Fixed Theme.TimeCapsule parent compatibility bug where MainActivity inherits from AppCompatActivity but the theme inherited from android:Theme.Material.Light.NoActionBar.
- **Session date(s):** 8 November 2026.
- **Issue and PR:** Issue #25, PR #27 (https://github.com/theguardianofthebed/Time-Capsule/pull/27).
- **Requirement/design references:** DD-01, DD-07, DD-11, instructions v3.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.6 (Android Studio Agent Mode).

### Purpose of agent use

Needed assistance in identifying the AppCompat theme incompatibility, applying the Material 3 theme fix, adding an instrumented regression test, running Gradle/Android test suites, and performing live emulator runtime verification.

### Key prompts

- "TimeCapsule — Fix PR #27 AppCompat Theme Compatibility ... Change Theme.TimeCapsule parent to Theme.Material3.DayNight.NoActionBar ..."

### What the agent changed and verified

- **Changes:**
  - `app/src/main/res/values/themes.xml`: Updated parent of `Theme.TimeCapsule` to `Theme.Material3.DayNight.NoActionBar` with explanatory comment.
  - `app/src/androidTest/java/com/example/timecapsule/MainActivityTest.kt`: Created instrumented regression test checking `MainActivity` launch and `tvMainTitle` visibility.
- **Checks and results:**
  - Gradle Sync: PASS
  - `:app:assembleDebug`: PASS
  - `:app:testDebugUnitTest`: PASS (4 passed, 0 failed)
  - `:app:lintDebug`: PASS
  - Emulator launch (emulator-5554): PASS. `MainActivity` launched successfully, title "TimeCapsule" and subtitle "S-01 to S-08 Screen Placeholders" displayed without crash. Home screen return and app reopen verified. Logcat clean (no `AndroidRuntime` fatal exception).
  - `:app:connectedDebugAndroidTest`: PASS (2 passed on emulator-5554, including `MainActivityTest`).
  - Light/Dark appearance check: PASS. Title and subtitle readable and visible in both light and dark modes.
- **Remaining checks:**
  - Full feature/navigation/backend integration verification (deferred to future issues beyond #25 Views skeleton).

### Errors and manual fixes

- The agent resolved an initial KDoc symbol resolution warning in `MainActivityTest.kt` by updating `[MainActivity.binding.tvMainTitle]` to `[R.id.tvMainTitle]`. No manual fix was needed for this work.

### My review and understanding

- **What I reviewed:**  I reviewed the AppCompat theme compatibility fix and manually tested the application in the Android emulator. I confirmed that the app launches successfully, displays the expected TimeCapsule placeholder screen, and reopens without crashing. I also checked Logcat for runtime errors and verified that the screen remains readable in both light and dark modes.
- **What I can explain:** I understand that AppCompatActivity requires an AppCompat-compatible theme. The previous Android platform theme could cause a runtime crash even when the project compiled successfully. Changing the theme parent to Theme.Material3.DayNight.NoActionBar resolves this compatibility issue without modifying the application's main logic or ViewBinding implementation. I can also explain why a successful Gradle build alone does not guarantee that an Android application will run correctly.
- **Feedback and next step:** The manual emulator tests passed, and I did not observe any remaining problems related to the theme fix. The next step is to update PR #27 with the verified changes, request a peer review, and complete the remaining PR dependency checks before merging. Further feature implementation is outside the scope of this fix.

---

## Task: Issue #9 Shared Android/Firebase Foundation

- **Task and purpose:** Established shared Android/Firebase foundation architecture, typed OperationResult hierarchy, session generation protection against stale account callbacks, replaceable adapter seams, memory-only Firestore cache configuration, and safe local Firebase emulator setup.
- **Session date(s):** 8 & 9 November 2026.
- **Issue and PR:** Issue #9 (https://github.com/theguardianofthebed/Time-Capsule/issues/9); implementation Draft PR #29 (https://github.com/theguardianofthebed/Time-Capsule/pull/29) from `feature/9-reviewable`. PR #28 was the separate rollback of the accidental direct-to-main commit.
- **Requirement/design references:** ARC-01, DD-10, DD-11, DD-20, NFR-01, NFR-06, FR-01, FR-02, FR-03, FR-09, AC-01, AC-03, AC-07, AC-09, AC-11, AC-12, AC-13, AC-14, T-02, T-10, T-32, T-33, T-40, T-43, T-44, T-45.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.6 (Android Studio Agent Mode).

### Purpose of agent use

Needed assistance in pinning Firebase/Maps/DataStore dependencies, defining typed result boundaries (`OperationResult`), implementing session generation tracking (`DefaultSessionTracker`), creating adapter interfaces, configuring memory-only Firestore cache, setting up local Firebase emulator scaffolding with deny-all security baselines, and writing unit tests for repository seams, delayed operation stale callback rejection, memory cache settings, release build safety, and security rules baseline.

### Key prompts

- "TimeCapsule — Issue #9: Shared Android/Firebase Foundation ... Complete the bounded foundation, verify it, present the changes to me..."
- "TimeCapsule Issue #9 — Safely Resume Interrupted Work ... Continue from the verified recovery checkpoint. Preserve all existing work, complete only the remaining Issue #9 tests and minimal corrections..."

### What the agent changed and verified

- **Changes:**
  - `gradle/libs.versions.toml`: Pinned Firebase BoM 34.4.0, Auth, Firestore, Storage, Google Services 4.4.4, Maps 19.2.0, Location 21.3.0, DataStore 1.1.7, ViewModel 2.9.4, Desugaring 2.1.5.
  - `build.gradle.kts` & `app/build.gradle.kts`: Applied plugin, desugaring, and dependencies with conditional google-services application for credential-free local builds and explicit Release build missing-configuration failure check.
  - `firebase.json`, `firestore.rules`, `firestore.indexes.json`, `storage.rules`: Initialized versioned emulator & deny-all security rules.
  - `app/src/main/java/com/example/timecapsule/`: Created `OperationResult` sealed result hierarchy, `SessionSnapshot`, `SessionProvider`, `RecipientProfile`, `CapsuleListsSnapshot`, updated `CapsuleRepository`, implemented `DefaultSessionTracker`, `FirestoreCachePolicy` (memory-only cache), `AuthAdapter`, `FirestoreAdapter`, `StorageAdapter`, `LocationAdapter`, `LocalRetentionAdapter`, `AppDependencyContainer`, and `FirebaseConfig`.
  - `app/src/test/java/com/example/timecapsule/`: Created unit tests `OperationResultTest`, `SessionProtectionTest` (with delayed operation boundary tests), `MemoryCacheConfigTest` (verifying `MemoryCacheSettings`), `RepositorySeamsTest`, and `FirebaseConfigTest` (verifying emulator ports/host, `isEmulatorMode`, and initial deny-all rules).
- **Checks and results:**
  - `gradle_sync`: PASS
  - `:app:assembleDebug`: PASS
  - `:app:testDebugUnitTest`: PASS (25 passed unit tests)
  - `:app:lintDebug`: PASS
  - `:app:assembleRelease`: PASS (failed intentionally as expected with `GradleException` due to missing `google-services.json`)
  - `git diff --check`: PASS (clean)
- **Remaining checks:**
  - Cloud Firebase deployment & live authentication (deferred to downstream feature issues #10-#23).

### Errors and manual fixes

- Resolved compiler warnings for missing trailing commas, parentheses, and catch parameter signatures.
- Fixed `FirebaseConfigTest` rules file location check when running under module root (`app/`). All unit tests passed cleanly.

### My review and understanding

- **What I reviewed:** 
  -  I reviewed Gemini's implementation of the shared Android/Firebase foundation for Issue #9, focusing on the repository interfaces, and Firebase emulator setup.
  -  I also examined the automated test coverage and identified which tests verify actual behavior versus only checking that components exist.
- **What I can explain:**
  -  I can explain why the application uses a Repository layer to separate UI logic from Firebase operations, and how typed results distinguish successful operations from failures or uncertain outcomes. I understand how session generation helps prevent delayed responses from a previous account session from being accepted after logout or account switching. 
  -  I can also explain why Firestore's memory-only cache must be configured before first use, and why Firebase emulator testing should remain isolated from production services.
- **Feedback and next step:**
  - The initial foundation provides reusable interfaces and testable components for subsequent features. I requested additional automated verification for asynchronous session isolation, Firestore initialization, and Firebase configuration safety before performing manual testing. 
  - The next plan is to review the final test results, reproduce the relevant checks in Android Studio, and address any remaining issues in PR #29 before teammate review and merging.

---

## Task: Issue #10 Android 17 Firebase Emulator Test Connectivity Fix

- **Task and purpose:** Fixed Android 17 (API 37) local network permission enforcement (`android.permission.ACCESS_LOCAL_NETWORK`) for Firebase Auth & Firestore Emulator test connectivity, ensuring instrumentation tests execute and pass cleanly on API 37 AVDs without TCP timeout errors.
- **Session date(s):** 10 November 2026.
- **Issue and PR:** Issue #10 (https://github.com/theguardianofthebed/Time-Capsule/issues/10), Draft PR #33 (https://github.com/theguardianofthebed/Time-Capsule/pull/33) on `feature/10-accounts-recipient`.
- **Requirement/design references:** DD-03, DD-11, DD-19, DD-20, AC-01, AC-12.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.6 (Android Studio Agent Mode).

### Purpose of agent use

Needed assistance in diagnosing the Android 17 (API 37) `ACCESS_LOCAL_NETWORK` local network permission requirement, declaring it in the Debug-only manifest, adding `GrantPermissionRule` and `UiAutomation` permission grants before test execution, improving negative Firestore rule assertions to verify exact `PERMISSION_DENIED` exceptions, and verifying instrumented test suite execution.

### Key prompts

- "Issue #10 — Fix Android 17 Firebase Emulator Test Connectivity ... On Android 17 (API 37), the application cannot connect to the Firebase Authentication Emulator at 10.0.2.2:9099 ..."

### What the agent changed and verified

- **Changes:**
  - `app/src/main/AndroidManifest.xml`: Added `<uses-permission android:name="android.permission.INTERNET" />`.
  - `app/src/debug/AndroidManifest.xml`: Added Debug-only `<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />` while preserving `usesCleartextTraffic="true"`.
  - `gradle/libs.versions.toml` & `app/build.gradle.kts`: Added `androidx.test:rules:1.6.1` (`androidTestImplementation(libs.androidx.test.rules)`).
  - `app/src/androidTest/java/com/example/timecapsule/data/adapter/FirebaseEmulatorIntegrationTest.kt`: Added `@get:Rule GrantPermissionRule` and `uiAutomation.grantRuntimePermission` in `@BeforeClass` for `ACCESS_LOCAL_NETWORK`. Improved negative security rule assertions to check for `FirebaseFirestoreException.Code.PERMISSION_DENIED`.
- **Checks and results:**
  - `gradle_sync`: PASS
  - `:app:assembleDebug`: PASS
  - `:app:testDebugUnitTest`: PASS (33 passed unit tests)
  - `:app:lintDebug`: PASS
  - `:app:connectedDebugAndroidTest`: PASS (9 passed instrumentation tests on `emulator-5554` API 37 with 0 failures, including all `FirebaseEmulatorIntegrationTest` cases).
- **Remaining checks:**
  - Live production Firebase authentication (deferred to production deployment after PR review).

### Errors and manual fixes

- Resolved an unused import warning in `FirebaseEmulatorIntegrationTest.kt`. All build, unit, lint, and instrumentation tests passed without manual code edits.

### My review and understanding

- **What I reviewed:** 
  - I investigated the Firebase Emulator connectivity failure on Android 17 (API 37) by checking the emulator connection, installed application permissions, and ADB network logs.
  - I confirmed that Firebase Emulator services were accessible, but the application did not initially request ACCESS_LOCAL_NETWORK.
  - After the Debug-only permission fix, I reran the previously failing test and the complete instrumentation test suite. All 9 instrumentation tests passed.
- **What I can explain:** 
  - I can explain the difference between the Android Emulator and Firebase Emulators, and why Android uses 10.0.2.2 to access services running on the development computer.
  - I understand why Android 17 requires local-network permission, how a missing permission can cause network timeouts, and why the emulator-specific permission should remain Debug-only.
  - I understand that automated permission setup helps other team members reproduce tests without manually granting permissions every time.
- **Feedback and next step:** 
  - The debugging process showed that successful builds do not guarantee working emulator connectivity. Checking the actual installed permissions and network logs was useful for identifying the cause.
  - The corrected test environment passed all 9 instrumentation tests. However, passing tests does not mean every Issue #10 requirement is complete.
  - Next, I will review the remaining account/session and profile-repair issues with the teammate, verify any corrections, and complete the PR review before merging. Once Issue #10 is merged and verified, the team can proceed with Issues #11 and #12 concurrently.

---

## Task: Issue #10 Final Critical Fixes Before Merge

- **Task and purpose:** Completed final critical Issue #10 defects: restored authenticated session state on every Activity/container recreation, integrated `SessionGuard` into real async AccountSessionManager operations to reject stale callbacks across account changes, removed unsafe Firestore fallback in `FirebaseConfig.kt`, completed recoverable profile creation and Profile Repair without duplicate Auth account creation, established exact recipient lookup with explicit confirmation boundary, fail-closed initialization for Debug/Release environments, disabled duplicate UI submissions during in-flight operations, and added unit test coverage.
- **Session date(s):** 10 November 2026.
- **Issue and PR:** Issue #10 (https://github.com/theguardianofthebed/Time-Capsule/issues/10), Draft PR #33 (https://github.com/theguardianofthebed/Time-Capsule/pull/33) on `feature/10-accounts-recipient`.
- **Requirement/design references:** DD-03, DD-11, DD-19, DD-20, AC-01, AC-12, FR-01, FR-03.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.6 (Android Studio Agent Mode).

### Purpose of agent use

Needed assistance in implementing session restoration on Activity recreation in `MainActivity`, integrating `SessionGuard` into `AccountSessionManager`, removing unsafe Firestore fallback in `FirebaseConfig`, enforcing non-blank profile name validation, establishing recipient lookup/confirmation state for Issue #12, and writing comprehensive unit tests (`SessionRestorationTest`, `ProfileRepairTest`, `RecipientConfirmationTest`, `StaleSessionCallbackTest`).

### Key prompts

- "Review the latest feature/10-accounts-recipient HEAD (9e10d06) in PR #33 ... Please perform one final minimal merge-blocker correction ..."

### What the agent changed and verified

- **Changes:**
  - `app/src/main/java/com/example/timecapsule/MainActivity.kt`: Called `restoreSessionIfAuthenticated()` unconditionally on every Activity/container recreation.
  - `app/src/main/java/com/example/timecapsule/config/FirebaseConfig.kt`: Removed unsafe `?: FirebaseFirestore.getInstance()` fallback in `setupDebugEmulatorConnection` to fail closed if memory-only initialization fails.
  - `app/src/main/java/com/example/timecapsule/data/AccountSessionManager.kt`: Integrated `SessionGuard` across async operations, added `restoreSessionIfAuthenticated()`, `lookupRecipient()`, `confirmRecipient()`, blank name validation, password length validation, and recoverable profile repair.
  - `app/src/main/java/com/example/timecapsule/data/DefaultCapsuleRepository.kt`: Updated `lists(uid)` to return `OperationResult.Unavailable` instead of fake empty list success.
  - `app/src/main/java/com/example/timecapsule/domain/model/RecipientProfile.kt`: Added `RecipientConfirmation` data class.
  - `app/src/main/java/com/example/timecapsule/ui/s01/S01SignInFragment.kt` & `ui/s02/S02RegisterFragment.kt`: Disabled submit buttons while in-flight to prevent duplicate requests.
  - `firestore.rules`: Updated `create, update` rules to enforce `displayName.size() > 0`.
  - `app/src/test/java/com/example/timecapsule/`: Added unit tests `SessionRestorationTest`, `ProfileRepairTest`, `RecipientConfirmationTest`, `StaleSessionCallbackTest`.
- **Checks and results:**
  - `gradle_sync`: PASS
  - `:app:assembleDebug`: PASS
  - `:app:testDebugUnitTest`: PASS (41 unit tests passed)
  - `:app:lintDebug`: PASS
  - `:app:connectedDebugAndroidTest`: PASS (9 instrumentation tests passed on `emulator-5554` API 37)
  - `git diff --check`: PASS (clean formatting)
- **Remaining checks:**
  - Full capsule publication, unlocking, and media storage (deferred to Issues #11, #12, #14).

### Errors and manual fixes

- Resolved minor compiler warnings for unused functions and lambda parameter positioning. All tests passed without manual code fixes.

### My review and understanding

- **What I reviewed:** [Awaiting student review]
- **What I can explain:** [Awaiting student response]
- **Feedback and next step:** [Awaiting student confirmation]
