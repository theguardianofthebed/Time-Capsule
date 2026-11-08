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

The code contains a UI/navigation preview, capsule models, and a repository interface. The preview does not authenticate accounts, send capsules, or verify unlock conditions. Firebase Authentication, Firestore, and Storage are selected for the application, but their integration remains to be implemented and tested.

The preview uses Compose. The confirmed UI is **native Android Views, XML layouts, and ViewBinding**, with S-01–S-08 defined in [DESIGN.md](../../DESIGN.md). Adopt and verify the separate starter through #25 before extending feature UI. This documentation change leaves application/Gradle files unchanged.

The starter keeps `com.example.timecapsule` and the checked-in toolchain/library versions described in [the baseline update](UI_VIEWS_UPDATE_2026-11-07.md). Its `MainActivity` is a Views host; `ui/navigation`, `ui/screens`, and eight `res/layout/screen_*.xml` files establish screen placeholders. Planned `ui/viewmodel`, `domain/usecase`, `data/remote`, `data/local`, and `platform/location`, `platform/media`, `platform/notification` directories separate future responsibilities. These placeholders do not implement services or acceptance criteria. Existing domain models, repository interfaces, useful assets, and tests are retained unless the reviewed migration manifest explicitly replaces them.

Follow [IMPLEMENTATION_ORDER.md](IMPLEMENTATION_ORDER.md): #25 precedes #9, and each feature must verify its own merged prerequisites. Keep the full app boundary UI/ViewModel → repository → adapters.

Implement features against [CURRENT_CONTRACT.md](CURRENT_CONTRACT.md) and its full reference. Use [DESIGN.md](../../DESIGN.md) for screen behavior and appearance. Unlock checks run when the app opens, resumes in the foreground, opens a detail screen, or the user retries; the design does not use alarms, geofences, boot receivers, background location, polling, or FCM.

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
