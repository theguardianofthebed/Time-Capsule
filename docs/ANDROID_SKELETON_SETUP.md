# Android setup

## Open the project

1. Clone or update `theguardianofthebed/Time-Capsule`. Save any unfinished local work first.
2. In Android Studio, choose **Open** and select the repository root containing `settings.gradle.kts`, `gradlew`, `gradlew.bat`, and `app/`.
3. Let Gradle Sync finish. Use the repository's wrapper, dependency versions, and toolchain settings. If something is missing, record the error and fix the local setup rather than changing project versions without review.
4. Keep `local.properties` local. Android Studio uses it for your SDK path; it must stay out of Git and agent context.
5. Follow [Agent setup](agent-tools/SETUP.md) to load the shared instructions and configure personal tools.

## Project settings

| Setting | Value or file |
|---|---|
| Application module | `:app` |
| Package / application ID | `com.example.timecapsule` |
| Minimum SDK | API 26 |
| Compile / target SDK | API 37 |
| Dependency versions | `gradle/libs.versions.toml` |
| Gradle distribution | `gradle/wrapper/gradle-wrapper.properties` |
| Gradle runtime toolchain | JDK 25; `gradle/gradle-daemon-jvm.properties` |
| Existing repository AGP / Gradle | 9.4.1 / 9.6.0 |
| Corrected Views starter AGP / Gradle | 9.3.0 / 9.6.0; Android Studio compatibility correction |
| Java source / target | 11; independent of the daemon JDK |
| Selected UI | XML Views + ViewBinding; separate starter adoption in #25 |

The JDK that runs Gradle and the Java version accepted by app source code are separate settings. The corrected standalone starter changes AGP alone to **9.3.0**, because the user's Android Studio rejected 9.4.1 and reported 9.3.0 as its supported ceiling. In that starter, `gradle/libs.versions.toml` must contain `agp = "9.3.0"`; then run **Sync Now**. Keep Gradle 9.6.0, daemon JDK 25 and SDK 37. The [AGP 9.3 requirements](https://developer.android.com/build/releases/agp-9-3-0-release-notes) specify Gradle 9.5.0 minimum and support API 37. This targeted correction does not establish a successful Sync or build; record the local result.

## Build and test

Run the commands from the repository root.

Windows PowerShell:

```powershell
.\gradlew.bat tasks --all
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

macOS/Linux:

```bash
bash ./gradlew tasks --all
bash ./gradlew :app:assembleDebug
bash ./gradlew :app:testDebugUnitTest
```

Using `bash` works with the wrapper's checked-in file permissions. Run `:app:lintDebug` and device tests when relevant to your task. Record the command, environment, result, and any error in the PR and your weekly log.

For the unchanged repository scaffold, the existing preview route is Welcome → Explore UI preview → Home → Create or Account → Back; sending is disabled. This documents the existing code, not the selected UI framework.

For the **separate Views starter**, extract it into a new folder and choose **Open** on its existing Gradle root; do not generate another project over it. Use its package guide and manifest. Run Gradle Sync, `:app:assembleDebug`, and the relevant existing tests, then launch and inspect the S-01–S-08 XML/ViewBinding placeholders on a compatible device/emulator. Record actual results; a placeholder walkthrough does not verify real accounts, sending, Maps, or unlock.

The [starter baseline](agent-tools/UI_VIEWS_UPDATE_2026-11-07.md) preserves the current repository versions and `com.example.timecapsule`, except for the documented AGP 9.3.0 compatibility correction. After local verification, adopt only the reviewed replacement/removal manifest in a separate branch and PR for #25. Compare every affected file against the recorded baseline and preserve unrelated work. Do not delete the documentation or coursework folders wholesale.

See the [Repository guide](agent-tools/REPOSITORY_STATUS.md) for code boundaries and [Implementation order](agent-tools/IMPLEMENTATION_ORDER.md) for the next ready issue.

For each change, follow [CONTRIBUTING.md](../CONTRIBUTING.md): work on a task branch, open a PR, and obtain another teammate's review before a human merges.
