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
| Gradle runtime toolchain | `gradle/gradle-daemon-jvm.properties` |

The JDK that runs Gradle and the Java version accepted by app source code are separate settings. Use the versions specified by the repository.

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

Launch the app on an emulator or device to check the UI preview: Welcome → Explore UI preview → Home → Create or Account → Back. Sending is disabled in this preview. See the [Repository guide](agent-tools/REPOSITORY_STATUS.md) for the remaining application work and the UI framework decision.

For each change, follow [CONTRIBUTING.md](../CONTRIBUTING.md): work on a task branch, open a PR, and obtain another teammate's review before a human merges.
