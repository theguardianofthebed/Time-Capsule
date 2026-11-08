# TimeCapsule

TimeCapsule is an Android app for sending a text/photo capsule to yourself or another registered user. A capsule opens when its time, location, or combined condition is met.

## Start here

| Task | Guide |
|---|---|
| Open and run the Android project | [Android setup](docs/ANDROID_SKELETON_SETUP.md) |
| Set up the coding agent and tools | [Agent setup](docs/agent-tools/SETUP.md) |
| Make or review a change | [AGENTS.md](AGENTS.md) and [CONTRIBUTING.md](CONTRIBUTING.md) |
| Understand the app's rules | [Project contract](docs/agent-tools/CURRENT_CONTRACT.md) |
| Choose an implementation issue | [Required order and readiness](docs/agent-tools/IMPLEMENTATION_ORDER.md) |
| Work on screens and visual design | [DESIGN.md](DESIGN.md) |
| Find documents and individual logs | [Documentation index](docs/README.md) |
| Understand the code structure and remaining work | [Repository guide](docs/agent-tools/REPOSITORY_STATUS.md) |

The UI contract is **Android Views/XML + ViewBinding** with **eight main screens, S-01–S-08**. Read [the confirmed update](docs/agent-tools/UI_VIEWS_UPDATE_2026-11-07.md) and [DESIGN.md](DESIGN.md). The application still has its earlier Compose scaffold until the separately tested starter is adopted through #25; this documentation change does not migrate application code.

## Working together

Use Android Studio Agent Mode for project code. Write easy English comments for each new or meaningfully changed class/function, explain unfamiliar steps, and follow the shared style. When refactoring, explain the problem being removed and how the revised code improves it.

Choose an issue whose [required prerequisites](docs/agent-tools/IMPLEMENTATION_ORDER.md) are merged and verified in your base, then self-assign it. Each task uses a branch and GitHub PR. Review generated changes before accepting them; another teammate reviews the PR before a human merges it.

Use **User F**, **User G**, and **User J** in shared documents, comments, and PR descriptions. Each student keeps their own records under `user-f`, `user-g`, or `user-j`.

## Repository layout

Open the repository root in Android Studio. It contains both the Git repository and the Gradle project.

| Path | Purpose |
|---|---|
| `app/` | Android application and tests |
| `gradle/`, `gradlew`, `gradlew.bat` | Gradle configuration and wrapper |
| `docs/` | Setup guides, project references, and individual coursework |
| `.agents/skills/` | Shared instructions for agent tasks |
| `.github/` | Issue and PR templates |
| `prototype/`, `spikes/` | Supporting prototypes and feasibility work |

### Detailed layout

This is the checked-in application layout; the separate Views package has its own migration manifest. Keep this distinction until #25 is merged.

```text
Time-Capsule/
├── .agents/
│   └── skills/
│       ├── timecapsule-implement/
│       │   └── SKILL.md
│       ├── timecapsule-record-learning/
│       │   └── SKILL.md
│       ├── timecapsule-refactor/
│       │   └── SKILL.md
│       └── timecapsule-review/
│           └── SKILL.md
│
├── .aiexclude
├── .editorconfig
│
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   └── implementation-task.md
│   └── pull_request_template.md
│
├── .gitignore
├── AGENTS.md
├── CONTRIBUTING.md
├── DESIGN.md
├── README.md
│
├── app/
│   ├── .gitignore
│   ├── build.gradle.kts
│   └── src/
│       ├── androidTest/
│       │   └── java/
│       │       └── com/
│       │           └── example/
│       │               └── timecapsule/
│       │                   └── ExampleInstrumentedTest.kt
│       │
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/
│       │   │   └── com/
│       │   │       └── example/
│       │   │           └── timecapsule/
│       │   │               ├── MainActivity.kt
│       │   │               │
│       │   │               ├── data/
│       │   │               │   └── CapsuleRepository.kt
│       │   │               │
│       │   │               ├── domain/
│       │   │               │   └── model/
│       │   │               │       └── Capsule.kt
│       │   │               │
│       │   │               └── ui/
│       │   │                   ├── TimeCapsuleApp.kt
│       │   │                   ├── screens/
│       │   │                   │   ├── CapsuleDetailScreen.kt
│       │   │                   │   ├── CreateCapsuleScreen.kt
│       │   │                   │   ├── HomeScreen.kt
│       │   │                   │   ├── ProfileScreen.kt
│       │   │                   │   └── WelcomeScreen.kt
│       │   │                   └── theme/
│       │   │                       ├── Color.kt
│       │   │                       ├── Theme.kt
│       │   │                       └── Type.kt
│       │   │
│       │   ├── keepRules/
│       │   │   └── rules.keep
│       │   │
│       │   └── res/
│       │       ├── drawable/
│       │       │   ├── ic_launcher_background.xml
│       │       │   └── ic_launcher_foreground.xml
│       │       ├── mipmap-anydpi/
│       │       │   ├── ic_launcher.xml
│       │       │   └── ic_launcher_round.xml
│       │       ├── mipmap-hdpi/
│       │       │   ├── ic_launcher.webp
│       │       │   └── ic_launcher_round.webp
│       │       ├── mipmap-mdpi/
│       │       │   ├── ic_launcher.webp
│       │       │   └── ic_launcher_round.webp
│       │       ├── mipmap-xhdpi/
│       │       │   ├── ic_launcher.webp
│       │       │   └── ic_launcher_round.webp
│       │       ├── mipmap-xxhdpi/
│       │       │   ├── ic_launcher.webp
│       │       │   └── ic_launcher_round.webp
│       │       ├── mipmap-xxxhdpi/
│       │       │   ├── ic_launcher.webp
│       │       │   └── ic_launcher_round.webp
│       │       ├── values/
│       │       │   ├── colors.xml
│       │       │   ├── strings.xml
│       │       │   └── themes.xml
│       │       └── xml/
│       │           ├── backup_rules.xml
│       │           └── data_extraction_rules.xml
│       │
│       └── test/
│           └── java/
│               └── com/
│                   └── example/
│                       └── timecapsule/
│                           ├── ExampleUnitTest.kt
│                           └── domain/
│                               └── model/
│                                   └── CapsuleDraftTest.kt
│
├── docs/
│   ├── .gitkeep
│   ├── README.md
│   ├── ANDROID_SKELETON_SETUP.md
│   │
│   ├── agent-tools/
│   │   ├── SETUP.md
│   │   ├── REPOSITORY_STATUS.md
│   │   ├── WORKFLOW.md
│   │   ├── IMPLEMENTATION_ORDER.md
│   │   ├── UI_VIEWS_UPDATE_2026-11-07.md
│   │   ├── CODING_AND_COMMENTS.md
│   │   ├── COURSE_BASELINE.md
│   │   ├── COURSE_COMPLIANCE.md
│   │   ├── CURRENT_CONTRACT.md
│   │   ├── SOURCE_REGISTER.md
│   │   ├── JOURNEYS.md
│   │   ├── MCP_SETUP.md
│   │   ├── mcp.redacted.json
│   │   ├── GITIGNORE_ADDITIONS.txt
│   │   └── reference/
│   │       └── timecapsule-contract-r38.json
│   │
│   ├── agent-logs/
│   │   ├── user-f/
│   │   │   ├── agent-log-w1.md
│   │   │   ├── agent-log-w2.md
│   │   │   ├── agent-log-w3.md
│   │   │   └── agent-log-w4.md
│   │   ├── user-g/
│   │   │   ├── agent-log-w1.md
│   │   │   ├── agent-log-w2.md
│   │   │   ├── agent-log-w3.md
│   │   │   └── agent-log-w4.md
│   │   └── user-j/
│   │       ├── agent-log-w1.md
│   │       ├── agent-log-w2.md
│   │       ├── agent-log-w3.md
│   │       └── agent-log-w4.md
│   │
│   └── retrospectives/
│       ├── user-f/
│       │   └── retrospective.md
│       ├── user-g/
│       │   └── retrospective.md
│       └── user-j/
│           └── retrospective.md
│
├── gradle/
│   ├── gradle-daemon-jvm.properties
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
│
├── prototype/
│   └── .gitkeep
│
└── spikes/
    └── .gitkeep
```
