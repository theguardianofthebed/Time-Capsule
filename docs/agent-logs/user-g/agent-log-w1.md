# User G agent log week 1

The planned submission date is **13 November 2026**; follow any later professor announcement. Add one feature/task section for each piece of work. Describe your own contribution and use real prompts, results, and examples. If you did not use an agent this week, say so honestly.

Follow [the shared workflow](../../agent-tools/WORKFLOW.md) and [AGENTS.md](../../../AGENTS.md). Remove credentials and private data from any quoted material.

## Adopt and verify Views/XML + ViewBinding skeleton for Issue #25

- **Task and purpose:** Replace the provisional Compose scaffold with native Android Views, XML layouts (S-01 to S-08 screen placeholders), and ViewBinding for Issue #25.
- **Session date(s):** 7 November 2026.
- **Issue and PR:** [Issue #25](https://github.com/theguardianofthebed/Time-Capsule/issues/25); PR pending (Branch feature/25-views-skeleton).
- **Requirement/design references:** `AGENTS.md`; `CURRENT_CONTRACT.md`; `WORKFLOW.md`; `CODING_AND_COMMENTS.md`; `UI_VIEWS_UPDATE_2026-11-07.md`; `IMPLEMENTATION_ORDER.md`.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini 3.6 Flash.

### Purpose of agent use

I used the agent to remove obsolete Compose dependencies and files, configure AGP 9.3.0 compatibility, enable ViewBinding, generate XML layout placeholders for S-01 through S-08 screens, verify Gradle Sync and Debug APK builds, execute unit tests, and test deployment on an emulator.

### Key prompts

- Fetch remote branches and create `feature/25-views-skeleton` based on `origin/docs/25-views-screen-contract`.
- Remove Compose dependencies (`kotlin-compose` plugin, Compose BOM, UI/Material3/Navigation) and enable `buildFeatures.viewBinding`.
- Delete obsolete Compose screen/theme files in `app/src/main/java/com/example/timecapsule/ui`.
- Create `activity_main.xml` and S-01 to S-08 XML placeholder layouts in `res/layout/`.
- Update `MainActivity.kt` to use `AppCompatActivity` and ViewBinding (`ActivityMainBinding`).
- Fix XML inspection errors/warnings and extract hardcoded strings into `strings.xml`.
- Perform Gradle Sync, Debug APK assemble (`:app:assembleDebug`), unit tests (`:app:testDebugUnitTest`), deploy to emulator, and clean-rebuild after a build artifact error.
- Pause before Agent Log updates and Git operations so the user could review the changes and add the onCreate KDoc manually.

### What the agent changed and verified

- **Changes:** Updated `libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `strings.xml`, `MainActivity.kt`. Deleted 9 Compose source files. Added `activity_main.xml` and 8 screen layout placeholders (`fragment_s01_sign_in.xml` through `fragment_s08_account_settings.xml`).
- **Checks and results:**
  - Gradle Sync: PASS (`Sync finished successfully`)
  - ViewBinding generation (`ActivityMainBinding` etc.): PASS
  - Debug APK assemble (`:app:assembleDebug`): PASS (`Build finished successfully`)
  - Unit tests (`:app:testDebugUnitTest`): PASS (4 passed, 0 skipped, 0 failed)
  - Emulator app deployment and launch (`emulator-5554`): PASS
  - XML Inspection (errors/warnings): 0 errors, 0 warnings
- **Remaining checks:** PR publication and review by a teammate. Backend features (Firebase, Maps, Auth) and Fragment/Navigation wiring remain deferred to downstream feature issues as planned.

### Errors and manual fixes

- **Errors:**
  1. Initial build failed due to missing `appcompat` and `material` dependencies when converting `MainActivity` to `AppCompatActivity`. Resolved by adding `androidx.appcompat:appcompat` and `com.google.android.material:material` to `libs.versions.toml` and `app/build.gradle.kts`.
  2. An intermediate build failed because a duplicate resource copy (`ic_launcher_background 2.xml`) was generated inside `app/build/`. Confirmed that original source files were untouched, performed `:app:clean` and re-ran `:app:assembleDebug` successfully.
- **Manual fixes:** User manually added a descriptive English KDoc comment to `MainActivity#onCreate`.

### My review and understanding

- **What I reviewed:** I reviewed the removal of Compose configurations and dependencies, the setup of Views/XML with ViewBinding, the adoption of `ActivityMainBinding` in `MainActivity.kt`, the S-01 to S-08 XML placeholders, and the final build results.
- **What I changed manually:** I manually added a plain English KDoc comment explaining the purpose of `onCreate` in `MainActivity.kt`.
- **What I can explain:** I can explain the motivation for switching from Compose to Views/XML + ViewBinding, how binding classes are generated automatically from XML layouts, how `MainActivity` uses `binding` to inflate and access views, and that the 8 XML files are baseline placeholders rather than completed feature screens.
- **What remains unclear:** The concrete implementation details for wiring Fragments and Navigation in downstream feature issues have not been reviewed yet.
