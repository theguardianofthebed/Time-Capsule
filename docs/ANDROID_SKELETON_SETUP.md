# TimeCapsule — open and verify the existing Android project

**Updated:** 3 November 2026, Asia/Seoul (KST)  
**Inspected source:** `main` commit `c7bb071cdda302f7562a549d5029e026b4c92a10`  
**Verification status:** Source and configuration inspected. Gradle Sync, Android build, unit tests, lint, emulator, and device checks were **NOT RUN** for this documentation update.

This repository already contains the Android project. Open the existing checkout rather than generating another Empty Activity project or copying an overlay over it. The original October bootstrap instructions are superseded by this guide; their implementation history remains in Git.

## 1. Understand the current scaffold

The checked-in entry activity and screens use Compose with Navigation Compose. They provide a provisional UI/navigation preview, with authentication and sending explicitly unavailable. The approved Atlas revision 38 contract specifies native Android Views and the Firebase service family.

These are separate facts: the scaffold describes what is currently checked in, while the contract describes the approved target. Before further UI implementation, complete a separate reviewed reconciliation task. This documentation change does not migrate screens, add dependencies, or approve Compose as the target. Read [REPOSITORY_STATUS.md](agent-tools/REPOSITORY_STATUS.md), [CURRENT_CONTRACT.md](agent-tools/CURRENT_CONTRACT.md), and [DESIGN.md](../DESIGN.md) together.

## 2. Open the checkout

1. Clone or update `theguardianofthebed/Time-Capsule` using your authorized GitHub account. Preserve unrelated local work. For a change, create a task branch from the current `main` following [CONTRIBUTING.md](../CONTRIBUTING.md).
2. In Android Studio, choose **Open** and select the repository root containing `settings.gradle.kts`, `gradlew`, `gradlew.bat`, and `app/`. The Git root and Gradle root are the same directory.
3. Use the checked-in wrapper, version catalog, and Gradle scripts. Let Android Studio configure the local SDK path in `local.properties`; keep that file untracked and outside agent context.
4. Allow Gradle Sync to run on your own machine. Record the actual outcome. If the required SDK or toolchain is missing, resolve that local setup issue or report the exact error; do not silently change dependency versions or the UI framework.
5. Follow [Agent setup](agent-tools/SETUP.md) to confirm that Android Studio Agent Mode reads the root `AGENTS.md` and relevant shared skill. Configure actual MCP credentials only in your own Android Studio settings directory.

Do not overwrite the root `AGENTS.md`, `CONTRIBUTING.md`, `.gitignore`, Gradle files, or teammates' changes with files from a newly generated project.

## 3. Configuration observed in the inspected commit

These are recorded repository values, not a new compatibility recommendation or proof that a build passed. Re-read the files if the checkout has changed.

| Item | Observed value | Source |
|---|---|---|
| Application module | `:app` | `settings.gradle.kts` |
| Namespace and application ID | `com.example.timecapsule` | `app/build.gradle.kts` |
| Minimum SDK | API 26 | `app/build.gradle.kts` |
| Compile / target SDK | API 37 / API 37 | `app/build.gradle.kts` |
| Current scaffold UI | Compose enabled; Navigation Compose dependency present | `app/build.gradle.kts` and `ui/TimeCapsuleApp.kt` |
| Gradle distribution | 9.6.0, with a checked-in checksum | `gradle/wrapper/gradle-wrapper.properties` |
| Gradle daemon toolchain | Version 25 | `gradle/gradle-daemon-jvm.properties` |
| Java source / target compatibility | Version 11 | `app/build.gradle.kts` |

The Gradle daemon version and Java source/target compatibility describe different settings. Preserve both unless a separate reviewed change intentionally updates them.

## 4. Run relevant local checks

From the repository root on Windows PowerShell:

```powershell
.\gradlew.bat tasks --all
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

On macOS/Linux, the inspected `gradlew` is tracked without an executable bit, so use:

```bash
bash ./gradlew tasks --all
bash ./gradlew :app:assembleDebug
bash ./gradlew :app:testDebugUnitTest
```

Use `:app:lintDebug` or device tests when relevant to the task and available in the actual checkout. The commands above are instructions for future verification, not recorded successes. Record the commit, machine/toolchain, command, result, and relevant error output. Do not change wrapper permissions or versions as an incidental part of a documentation task.

For a local UI check, launch the app on a configured emulator or device. The following are **source-derived expectations to verify**, not test results:

| Preview path | Expected scaffold boundary |
|---|---|
| Welcome → Explore UI preview | Enters a navigation preview; does not authenticate an account |
| Home / Received / Sent | No live backend integration is established by the preview |
| Home → Create → Back | Shows draft controls; Send remains disabled |
| Home → Account → Back | Displays a preview account screen rather than a completed account service |

A preview screen, source test file, or successful compile cannot establish server authorization, send acknowledgment, unlock correctness, or application acceptance.

## 5. Continue with approved feature work

The current contract selects Firebase Authentication, Firestore, and Storage; the inspected scaffold has not integrated them. Provider selection and implementation completion must not be confused. Follow the exact current contract for server-confirmed send/unlock, current-condition checks, account-scoped retained content, and recovery.

Supported checks are app-open, foreground resume, detail opening, and explicit Retry. Alarms, registered geofences, boot receivers, periodic polling, background location, and FCM are outside the current production design. Do not restore an old spike or bootstrap suggestion as an approved feature.

Use the shared requirement → Issue → branch → PR workflow for each coherent task. Add the required easy English declaration comments and finer comments for unfamiliar code as it is implemented. Record actual verification and each student's own learning activity. Leave incomplete functionality and unrun checks labeled honestly.

## 6. Why this guide changed

**Before:** the guide instructed teammates to create a new Compose project and copy an overlay, described the repository as having no app, and treated several now-confirmed decisions as pending.  
**After:** it opens the existing project, records the current scaffold separately from the approved design, and provides honest local verification instructions.  
**Why:** teammates need instructions that match the checkout without overwriting existing work or mistaking a preview for a completed feature. No application directory was moved. See the full integration rationale in [REPOSITORY_STATUS.md](agent-tools/REPOSITORY_STATUS.md).
