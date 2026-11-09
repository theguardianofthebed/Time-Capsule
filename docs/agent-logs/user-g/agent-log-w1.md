# Agent Log – Week 1 (MM/DD – MM/DD)

- Name / Student ID:
- Model(s) used:
- My changes to the team's Agent Tools this week:

The planned submission date is **13 November 2026**; follow any later professor announcement. Add one feature/task section for each piece of work. Describe your own contribution and use real prompts, results, and examples. If you did not use an agent this week, say so honestly.

Follow [the shared workflow](../../agent-tools/WORKFLOW.md) and [AGENTS.md](../../../AGENTS.md). Remove credentials and private data from any quoted material.

## Agent workflow documentation review and restructuring

- **Task and purpose:** Review the professor's agent-usage requirements and simplify the repository's shared agent workflow without changing application code.
- **Session date(s):** 7 November 2026.
- **Issue and PR:** Issue #7; https://github.com/theguardianofthebed/Time-Capsule/pull/8
- **Requirement/design references:** Professor's `Term_Project_Agent_Usage_Supplement.docx`; `AGENTS.md`; `CURRENT_CONTRACT.md`; `WORKFLOW.md`; `CODING_AND_COMMENTS.md`.
- **Agent environment:** Codex desktop app, used for repository documentation only. No Android application code was changed in this task.
- **Model used:** Exact displayed model name was not recorded. Gemini 3.6 Flash was discussed but was not used for this documentation session.

**Goal**

I used the agent to inspect the existing Rules, Skills, individual weekly logs, retrospectives, ignore settings, and MCP example, then compare their structure with the professor's supplement. I decided that the weekly log should be prepared automatically after each task, that the mandatory workflow should have four core documents, and that the existing KDoc rule should remain.

**Requests and prompts**

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Check whether `AGENTS.md`, Rules, Skills, three students' weekly logs, and retrospectives are already structured. |  | |
| 2 | Explain the existing `AGENTS.md` and identify strengths, gaps, and differences from the professor's requirements. |  | |
| 3 | Make the agent prepare the weekly log automatically, while asking before recording personal review, manual fixes, understanding, or learning. |  | |
| 4 | Reduce the mandatory document structure to `AGENTS.md`, `CURRENT_CONTRACT.md`, `WORKFLOW.md`, and `CODING_AND_COMMENTS.md`. |  | |
| 5 | Keep the KDoc requirement. |  | |
| 6 | Keep real MCP settings private and retain only the professor-required token-free example in the repository. |  | |

### What the agent changed and verified

- **Changes:** Simplified `AGENTS.md`; assigned product and technical decisions to `CURRENT_CONTRACT.md`; assigned Issue, PR, verification, automatic logging, and retrospective procedures to `WORKFLOW.md`; retained KDoc and code-explanation rules in `CODING_AND_COMMENTS.md`; updated the four shared Skills and documentation index to use the four core documents.
- **Checks and results:** `git diff --check` passed; all checked local Markdown links resolved; `mcp.redacted.json` parsed as valid JSON; sensitive-file patterns were confirmed in `.aiexclude` and `.gitignore`.
- **Remaining checks:** A teammate still needs to review the published PR. This documentation task did not run an Android build or application test because it did not change application code.

### Errors and manual fixes

The first attempted whole-file replacement of `AGENTS.md` was rejected because it would temporarily remove the shared safety instructions. The agent changed to an atomic in-place update, so the repository was never left without `AGENTS.md`. No student-authored manual file edit was recorded during this session.

### My review and understanding

- **What I reviewed:** I reviewed the professor's required deliverables and approved three documentation decisions: automatic weekly log preparation, four core workflow documents, and retention of the KDoc rule. I also checked why the token-free MCP example is required while real MCP credentials remain private.
- **What I can explain:** I can distinguish the four core documents by purpose and distinguish a private MCP configuration from the committed token-free example.
- **Feedback and next step:** Publish this documentation branch as a PR, request review from Junwon and Flora, then configure Android Studio Agent Mode and verify that it reads the four core documents and Skills.

## Adopt and verify Views/XML + ViewBinding skeleton for Issue #25

- **Task and purpose:** Replace the provisional Compose scaffold with native Android Views, XML layouts (S-01 to S-08 screen placeholders), and ViewBinding for Issue #25.
- **Session date(s):** 7 November 2026.
- **Issue and PR:** [Issue #25](https://github.com/theguardianofthebed/Time-Capsule/issues/25); [PR #27](https://github.com/theguardianofthebed/Time-Capsule/pull/27) (Branch `feature/25-views-skeleton`).
- **Requirement/design references:** `AGENTS.md`; `CURRENT_CONTRACT.md`; `WORKFLOW.md`; `CODING_AND_COMMENTS.md`; `UI_VIEWS_UPDATE_2026-11-07.md`; `IMPLEMENTATION_ORDER.md`.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini 3.6 Flash.

**Goal**

I used the agent to remove obsolete Compose dependencies and files, configure AGP 9.3.0 compatibility, enable ViewBinding, generate XML layout placeholders for S-01 through S-08 screens, verify Gradle Sync and Debug APK builds, execute unit tests, and test deployment on an emulator.

**Requests and prompts**

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Fetch remote branches and create `feature/25-views-skeleton` based on `origin/docs/25-views-screen-contract`. |  | |
| 2 | Remove Compose dependencies (`kotlin-compose` plugin, Compose BOM, UI/Material3/Navigation) and enable `buildFeatures.viewBinding`. |  | |
| 3 | Delete obsolete Compose screen/theme files in `app/src/main/java/com/example/timecapsule/ui`. |  | |
| 4 | Create `activity_main.xml` and S-01 to S-08 XML placeholder layouts in `res/layout/`. |  | |
| 5 | Update `MainActivity.kt` to use `AppCompatActivity` and ViewBinding (`ActivityMainBinding`). |  | |
| 6 | Fix XML inspection errors/warnings and extract hardcoded strings into `strings.xml`. |  | |
| 7 | Perform Gradle Sync, Debug APK assemble (`:app:assembleDebug`), unit tests (`:app:testDebugUnitTest`), deploy to emulator, and clean-rebuild after a build artifact error. |  | |
| 8 | Pause before Agent Log updates and Git operations so the user could review the changes and add the onCreate KDoc manually. |  | |

### What the agent changed and verified

- **Changes:** Updated `libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `strings.xml`, `MainActivity.kt`. Deleted 9 Compose source files. Added `activity_main.xml` and 8 screen layout placeholders (`fragment_s01_sign_in.xml` through `fragment_s08_account_settings.xml`).
- **Checks and results:**
  - Gradle Sync: PASS (`Sync finished successfully`)
  - ViewBinding generation (`ActivityMainBinding` etc.): PASS
  - Debug APK assemble (`:app:assembleDebug`): PASS (`Build finished successfully`)
  - Unit tests (`:app:testDebugUnitTest`): PASS (4 passed, 0 skipped, 0 failed)
  - Emulator app deployment and launch (`emulator-5554`): PASS
  - XML Inspection (errors/warnings): 0 errors, 0 warnings
- **Remaining checks:** A different teammate's PR review and a post-theme-fix emulator recheck remain pending. Backend features (Firebase, Maps, Auth) and Fragment/Navigation wiring remain deferred to downstream feature issues as planned.

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
