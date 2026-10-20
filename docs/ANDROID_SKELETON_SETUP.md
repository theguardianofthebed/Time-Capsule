# TimeCapsule — Android Studio skeleton setup

**Status**: scaffold proposal, Oct 21, 2026. Kotlin + Compose + Navigation Compose are recommendations, **not yet confirmed team decisions**.

## 1. Existing repository

GitHub: `theguardianofthebed/Time-Capsule`. Existing `main` includes `.gitignore`, `AGENTS.md`, `CONTRIBUTING.md`, README and support folders but no Android app. Do not replace those root files. Do not commit to `main` directly.

**Conflict to fix in a reviewed change**: existing repository `AGENTS.md` inaccurately calls direct A→B account delivery a stretch; the Oct 14 confirmed project baseline includes it in MVP. This overlay does not modify that document.

## 2. Create the Android Studio template (on your Windows PC)

1. Clone the **Time-Capsule** repository locally and create a task branch from `main`, following `CONTRIBUTING.md`. Use the real issue number when Geonwoo creates the issue.
2. Android Studio → New Project → **Phone and Tablet → Empty Activity** (Jetpack Compose).
3. Recommended temporary settings: **Name** `TimeCapsule`, **Package name** `com.example.timecapsule`, **Language** `Kotlin`, **Minimum SDK** API 26. Treat the SDK choice as a proposal pending team review. Let Android Studio generate the Gradle wrapper/dependencies instead of copying an old Gradle configuration from SpikeLab.
4. Create the project in a **temporary sibling folder**, NOT on top of the nonempty repository; e.g. `C:\dev\TimeCapsuleAndroidBootstrap`.
5. Close Android Studio while copying files. Copy the Android Studio-generated project files/folders into the **existing cloned repo root**, excluding `.git`, `.idea`, `.gradle`, `build`, `app/build`, `local.properties`, any existing README/.gitignore, and any generated machine-specific secrets. Retain `gradlew`, `gradlew.bat`, `gradle/wrapper`, `gradle/libs.versions.toml`, `settings.gradle.kts`, root/app Gradle scripts and the generated Android manifest/resources.
6. Copy the **contents** of this overlay's `app/` and `docs/` folders into the matching folders in the cloned repository. Replacing only the generated `MainActivity.kt` is expected; inspect differences before replacing anything else.
7. Check that `app/src/main/java/com/example/timecapsule/MainActivity.kt` matches the package selected in step 3. If you chose a different package, refactor Kotlin packages and directories consistently.
8. Add `implementation("androidx.navigation:navigation-compose:2.10.0")` inside `dependencies {}` of `app/build.gradle.kts` (no duplicate entries). This uses the 2026 official Navigation Compose release documented on Android Developers; review version compatibility with the Android Studio-generated AGP/Kotlin configuration.
9. Open the cloned repository root with Android Studio → **Open**. Let Gradle Sync finish and run on an emulator. All screens are nonfunctional UI previews; they do not authenticate or send capsules.

## 3. Verify locally

- In project-root terminal on Windows:
  ```powershell
  .\gradlew.bat :app:assembleDebug
  .\gradlew.bat :app:testDebugUnitTest
  ```
- Run from Android Studio on a configured emulator, preferably API 33+ for the team's later permissions testing.
- You should see Welcome → **Explore UI preview** → Received/Sent tabs → Create screen → Back; Account screen → Back.
- The Send button is disabled. No remote data or protected content can be fetched. Real account login, server time checks, remote ACK, notification/geofence and photo handling are pending feature issues.
- Share the build instructions with teammates. Review `git status` before committing; `local.properties`, `.idea/`, `build/`, and secrets must never be committed.

## 4. Boundaries / next work

- No Firebase, Room, FCM or Google Maps dependency is included. Provider selection remains a reviewed team decision.
- Use separate metadata and protected-content concepts so locked text/photo are not accidentally serialized into a publicly readable metadata document.
- Android app will evaluate **current** location when required. Server-authoritative time (not device clock) controls initial time eligibility. Once unlocked, do not re-lock.
- Stable `capsuleId`, backend ACK before SENT, and app-open recipient sync are requirements to implement later, not working code in this scaffold.
- For the Oct 21–27 milestone: finalize backend and schema approvals, create issue-linked core time-lock slice, then add A→B integrated slice promptly. All 3 members review and test.

## 5. Suggested issue acceptance criteria

- [ ] Android Studio project committed on a feature/init branch, not directly to main.
- [ ] `gradlew.bat :app:assembleDebug` works for at least two members locally.
- [ ] Entry → Home → Create/Profile and back works without crashing.
- [ ] Received/Sent are explicitly empty (no fake account data).
- [ ] No fake send success, premature content access, or Firebase/Room dependency.
- [ ] Unit test for empty/text-only/photo-only draft rule passes.
- [ ] Existing repository rules/docs are retained and scope conflict is flagged.

**Evidence**: This overlay was generated separately from Android Studio and has not been assembled or run on Android. It must pass on-machine Gradle verification before PR approval.
