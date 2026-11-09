# Repository guide

## Code and configuration

The Git repository root is also the Android/Gradle project root. Open this root in Android Studio.

| Path | Purpose |
|---|---|
| `app/src/main/java/com/example/timecapsule/MainActivity.kt` | Application entry point |
| `app/src/main/java/com/example/timecapsule/ui/` | Screen navigation, preview screens, and theme |
| `app/src/main/java/com/example/timecapsule/domain/model/` | Capsule model definitions |
| `app/src/main/java/com/example/timecapsule/data/CapsuleRepository.kt` | Interface for capsule data operations |
| `app/src/main/res/` | Android resources |
| `app/src/main/AndroidManifest.xml` | Android application declarations |
| `app/src/test/` | Local unit tests |
| `app/src/androidTest/` | Tests that run on Android |
| `app/build.gradle.kts` | App build settings and dependencies |
| `gradle/`, `gradlew`, `gradlew.bat` | Gradle versions, toolchain, and wrapper |
| `prototype/`, `spikes/` | Supporting prototypes and feasibility work |

The full directory tree is in [README.md](../../README.md). Use [Android setup](../ANDROID_SKELETON_SETUP.md) to open the project and run checks.

## Application boundaries

The main branch contains a Views starter, screen/navigation placeholders, capsule models, typed repository interfaces, session/adapters and Firebase configuration/test scaffolding. The preview does not authenticate accounts, send capsules, or verify unlock conditions. Firebase Authentication, Firestore, and Storage are selected for the application, but their integration remains to be implemented and tested.

At final inspected `main` commit `c60d866cdc9794af97db129c946b66711270f6b9`, the app uses **native Android Views, XML layouts, and ViewBinding**, with S-01–S-08 defined in [DESIGN.md](../../DESIGN.md). #25 is merged through PR #27 (`767da710`) and PR #26 (`6e2e286b`). The checked-in activity launches a placeholder title screen; the eight XML screen files do not establish completed navigation or feature flows. User J's Week 1 log records build/lint, four unit tests, two instrumented tests and launch/reopen/light-dark verification for the starter. Those historical runs were not rerun during this documentation task.

The merged starter keeps `com.example.timecapsule`, AGP 9.3.0 and the checked-in toolchain/library versions described in [the baseline update](UI_VIEWS_UPDATE_2026-11-07.md). Its `MainActivity` is a Views host; `ui/navigation`, `ui/screens`, and eight `res/layout/screen_*.xml` files establish screen placeholders. Planned `ui/viewmodel`, `domain/usecase`, `data/remote`, `data/local`, and `platform/location`, `platform/media`, `platform/notification` directories separate future responsibilities. These placeholders do not implement services or acceptance criteria. Existing domain models, repository interfaces, useful assets, and tests are retained unless the reviewed migration manifest explicitly replaces them.

Foundation PR #29 **merged externally at 00:56:26 KST on 10 November 2026**, producing `c60d866cdc9794af97db129c946b66711270f6b9` from head `544f5a673037059f3fb9fc5656e1616caa27748c`. This supersedes the audit's initial draft observation at `59f3a0b` and restores the foundation after historical rollback PR #28. Status: **MERGED, VERIFICATION PENDING**. Issue #9 remains open. #10 remains **BLOCKED** until required verification evidence and its owner/reviewer preflight are established, not because PR #29 is still unmerged.

The latest PR #29 source includes typed repository results, adapters/session generation, memory-only Firestore initialization and a release Firebase-configuration guard. Its latest User J log reports 25 unit tests and relevant build/lint/configuration checks. Its PR description still reports an older 17-test state. Source/test definitions, reported test runs, and independent verification are separate: actual Firebase emulator service/rule execution and integrated account flows are not established by those unit tests. The latest release guard uses a dedicated validation task. Preserve this merged work; inspect exact evidence and approved verification boundaries before declaring the successor READY.

Follow [IMPLEMENTATION_ORDER.md](IMPLEMENTATION_ORDER.md): #25 precedes #9, and each feature must verify its own merged prerequisites. Keep the full app boundary UI/ViewModel → repository → adapters.

Implement features against [CURRENT_CONTRACT.md](CURRENT_CONTRACT.md) and the exact current affected records obtained through authorized MCP or a privately supplied current snapshot. Use [DESIGN.md](../../DESIGN.md) for screen behavior and appearance. Unlock checks run when the app opens, resumes in the foreground, opens a detail screen, or the user retries; the design does not use alarms, geofences, boot receivers, background location, polling, or FCM.

Record actual build and test results in the relevant PR and student log. The presence of a preview or test file does not show that an application requirement has passed.

## Shared project files

| Path | Purpose |
|---|---|
| `AGENTS.md` | Mandatory agent rules and repository protections |
| `CONTRIBUTING.md` | Branch, commit, PR, and review process |
| `DESIGN.md` | Screen flow and design system |
| `.agents/skills/` | Reusable procedures for agent tasks |
| `.github/` | Issue and PR templates |
| `.editorconfig` | Shared formatting |
| `.aiexclude` | Sensitive files excluded from agent context |
| `.gitignore` | Local, generated, and sensitive files excluded from Git |
| `docs/agent-tools/` | Setup guides, coding guidance, and project references |
| `docs/agent-logs/` | Individual weekly work records |
| `docs/retrospectives/` | Individual final reflections |

Actual MCP credentials belong in each member's Android Studio settings directory. Commit only the redacted example. Keep the shared rules and skill files tracked, and use the anonymous student labels in shared material.

Use [SETUP.md](SETUP.md) for member setup and [docs/README.md](../README.md) to find a document.

## Contract access

The TimeCapsule MCP at `atlastree.app` was successfully read at revision 55 on 10 November 2026. The full 112-record/378-relationship capture is retained privately. The public [r55 verification record](reference/timecapsule-contract-r55-verification.json) contains metadata only; the public full r38 snapshot remains historical. Obtain authorized current-record access before implementation. Historical connection failures are not a current outage. The live Atlas remains unchanged; its initialized progress labels do not override later GitHub evidence for the starter.
