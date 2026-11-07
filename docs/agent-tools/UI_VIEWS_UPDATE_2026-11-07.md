# Views, screens, and starter baseline

The 7 November 2026 team decision confirms **Kotlin, Android Views, XML layouts, and ViewBinding** for the app UI. This matches the course's XML/ViewBinding approach and gives the team one implementation style to explain during the interview. Jetpack Compose is not the selected UI framework. The word “Compose” in S-04 means writing capsule content.

Read [DESIGN.md](../../DESIGN.md) for the complete **S-01–S-08** screen contract and [IMPLEMENTATION_ORDER.md](IMPLEMENTATION_ORDER.md) before choosing an implementation issue.

## What this decision changes

- Make ViewBinding explicit in the existing native Views architecture and screen specification.
- Use eight main screens. Content is S-04; recipient and lock choices share S-05; review and send are S-06. Loading, failure, locked/unlocked, profile repair, and send success are variants of these screens.
- Adopt and verify the separate Views starter through [#25](https://github.com/theguardianofthebed/Time-Capsule/issues/25) before the shared foundation in #9 and dependent feature work.
- Preserve the current repository's build versions and app identity in the starter, with the explicit AGP 9.3.0 compatibility correction below. Adding the planned Firebase, Maps, retention, and other service adapters remains separate issue work.
- Treat each issue's `Blocked by` list as a mandatory implementation gate. Members may self-assign any issue whose prerequisites are merged and verified in their base.

The scope remains accounts, delivery to self or another registered user, text and/or one photo, Time/Location/Both locks, lists, foreground recovery, local notifications, and permanent authorized re-view. This decision does not add a social directory, demo login, password reset, email-verification gate, background monitoring, Room, or FCM.

## Evidence and source precedence

The user supplied a Kakao discussion, an explicit S-01–S-08 table, and screen images. The discussion confirms the Views choice and dependency-respecting self-assignment. The screen table states behavior; the images show visual direction and state variations. Image text such as “simulated,” “mock map,” sample names/codes, a 2,000-character counter, or an eight-character password hint does not create a new production rule. Preserve established requirements unless an explicit decision changes them.

The [retained Atlas reference](reference/timecapsule-contract-r38.json) remains an unchanged historical capture. Existing GitHub issue descriptions retain a later English Atlas capture at revision 46. Apply this narrow, explicitly authorized update to the UI stack, screen grouping, starter baseline, and implementation gates; preserve the other captured clauses. “Compose” as a content-writing action is not a Compose framework requirement.

The existing ChatGPT Sites Atlas returned **410 ATLAS_MOVED**. The user then authorized the new Project Atlas MCP connection; that authenticated connection did not list the Time Capsule atlas among the accessible targets. The live Time Capsule update therefore remains **pending** until the intended atlas is accessible. Do not substitute another atlas or infer whether this is an access-scope or migration issue. This document does not claim an applied Atlas revision or a changed implementation/test status. Reconcile the prepared targeted proposal with a freshly read authorized Atlas revision before applying it. Do not import the historical reference as a replacement atlas.

Relevant existing records are ARC-01; DD-01, DD-07, DD-11, DD-20, DD-26, DD-27, DD-28; and T-45. S-01–S-08 are screen labels within those records, not a new family of Atlas design nodes.

## Starter versions and Android Studio compatibility

The baseline comes from `main` commit `c7bb071cdda302f7562a549d5029e026b4c92a10`. The first package preserved AGP 9.4.1, but the user then reported that Android Studio rejected it and supported at most AGP 9.3.0. The corrected package therefore changes **AGP alone to 9.3.0**. This documented exception takes precedence over earlier blanket instructions to preserve every version. The remote application remains unchanged until the reviewed #25 adoption PR.

[AGP 9.3's official requirements](https://developer.android.com/build/releases/agp-9-3-0-release-notes) specify Gradle 9.5.0 minimum and support API 37. Gradle's [Java 25 support](https://docs.gradle.org/9.1.0/release-notes.html#support-for-java-25) began in 9.1, so the existing Gradle 9.6.0/JDK 25 settings are retained. These source checks establish the documented compatibility basis, not an observed Sync, APK or device pass.

| Setting | Starter value or treatment |
|---|---|
| Application ID and namespace | `com.example.timecapsule` |
| Android Gradle Plugin | `9.3.0`; corrected from the initial package/repository's `9.4.1` |
| Gradle wrapper | `9.6.0`; retain the original wrapper JAR and distribution checksum |
| Gradle daemon JDK | `25`, as recorded in `gradle/gradle-daemon-jvm.properties` |
| Java source / target compatibility | `11` |
| Android SDK | `minSdk 26`, `compileSdk 37`, `targetSdk 37` |
| AndroidX Core KTX | `1.19.1` |
| Lifecycle runtime KTX | `2.6.1` |
| AndroidX Activity | Replace `activity-compose:1.13.0` with `activity-ktx:1.13.0` |
| Existing test libraries | JUnit `4.13.2`, AndroidX JUnit `1.3.0`, Espresso `3.7.0` |
| ViewBinding | Enable `buildFeatures.viewBinding`; use generated layout bindings |
| Compose | Remove rendering, the compiler plugin, Compose BOM/UI/test artifacts, and Navigation Compose from the separate starter |
| Kotlin | Retain AGP's built-in mechanism. The old catalog value `2.2.10` configured the Compose plugin; it does not establish the embedded Kotlin compiler version after that plugin is removed. |

For this starter, the historical DD-11 toolchain target (AGP 9.0.1, Gradle 9.1.0, source target 17, SDK 36) and DD-20 app identity `com.timecapsule.app` do not override the current repository-compatible baseline. No existing Firebase app is renamed or provisioned by this documentation change. Future configuration must match the app identity actually adopted by the reviewed application PR.

The selected future service versions remain recorded in [CURRENT_CONTRACT.md](CURRENT_CONTRACT.md): Firebase BoM 34.4.0, Google services plugin 4.4.4, Maps 19.2.0, Location 21.3.0, DataStore 1.1.7, and Lifecycle ViewModel 2.9.4. They are not silently added to the starter. Issue #9 must verify their compatibility with the adopted toolchain and existing runtime dependencies before integration; a needed version change requires an explicit reviewed contract update.

## Starter and feature completion

The starter establishes an activity, XML layouts, generated binding use, and the planned UI/domain/data/adapter directories. Its eight screen placeholders support structure and launch checks. They do not prove authentication, sending, Maps, unlock, offline access, or final Material 3 screen completion.

The repository's application/Gradle files stay unchanged in the documentation PR. The starter is tested independently and then adopted with a separate application PR for #25. Use its explicit file replacement/removal manifest; preserve unrelated code, relevant documentation, coursework records, assets, and Git history. Retire obsolete guidance only when an identified replacement exists and the removal is visible in that PR.

Keep four checks explicit within the three creation screens: content, confirmed recipient, valid lock, and final review. Preserve server-time first unlock, current location AND semantics, one stable capsule ID for uncertain sends, JPEG/PNG original-photo validation, safe locked metadata, the sender/self-send distinction, and account cleanup.
