# TimeCapsule

TimeCapsule is the Mobile Programming team project: an Android app for sending a text/photo capsule to yourself or another registered user, with time, location, or combined unlock conditions.

## Current project status

The repository contains an Android project and a provisional Compose UI scaffold. The preview does not authenticate users, send capsules, or verify unlock conditions. The approved project contract specifies native Android Views and the Firebase service family; those decisions are not proof of a completed integration. The Views/Compose discrepancy needs a separate reviewed reconciliation task before further UI implementation.

See [Repository status](docs/agent-tools/REPOSITORY_STATUS.md) for the inspected commit, current paths, known discrepancies, and verification limits. This documentation change keeps the application directories and code in place.

## Start here

| Task | Read |
|---|---|
| Open the existing Android project | [Android checkout and skeleton setup](docs/ANDROID_SKELETON_SETUP.md) |
| Configure shared agent instructions and personal tools | [Agent setup](docs/agent-tools/SETUP.md) |
| Plan, edit, or review a change | [AGENTS.md](AGENTS.md) and [CONTRIBUTING.md](CONTRIBUTING.md) |
| Understand the approved behavior | [Source register](docs/agent-tools/SOURCE_REGISTER.md) and [Current contract](docs/agent-tools/CURRENT_CONTRACT.md) |
| Work on screens and visual design | [DESIGN.md](DESIGN.md) |
| Find logs, rules, templates, and course requirements | [Documentation index](docs/README.md) |

## Team workflow

Use Android Studio Agent Mode for project code. Add clear English comments for each new or meaningfully changed handwritten class/function, explain unfamiliar steps, follow the shared style, and document refactoring with a short Before / After / Why note. Every coherent code, test, configuration, or documentation change goes through a task branch and GitHub PR. Another teammate reviews it before a human merges.

Shared documents identify the students as **User F**, **User G**, and **User J**. Individual logs and reflections use the matching `user-f`, `user-g`, and `user-j` directories.

## Repository layout

| Path | Purpose |
|---|---|
| `app/` | Existing Android application module |
| `gradle/`, `gradlew`, `gradlew.bat` | Checked-in Gradle configuration and wrapper |
| `docs/` | Project setup, approved-source references, agent workflow, and individual coursework |
| `.agents/skills/` | Shared task instructions for Android Studio agents |
| `.github/` | Issue and PR templates |
| `prototype/`, `spikes/` | Existing supporting-work directories; their presence does not establish application integration |

The Git repository root is also the Gradle project root. Open this root in Android Studio; do not create a second Android project inside `app/` or copy a new template over the checkout.

### Detailed layout
```text
Time-Capsule/
├── .agents/                                      [NEW]
│   └── skills/                                   [NEW]
│       ├── timecapsule-implement/                 [NEW]
│       │   └── SKILL.md                          [NEW]
│       ├── timecapsule-record-learning/           [NEW]
│       │   └── SKILL.md                          [NEW]
│       ├── timecapsule-refactor/                  [NEW]
│       │   └── SKILL.md                          [NEW]
│       └── timecapsule-review/                    [NEW]
│           └── SKILL.md                          [NEW]
│
├── .aiexclude                                    [NEW]
├── .editorconfig                                 [NEW]
│
├── .github/                                      [NEW]
│   ├── ISSUE_TEMPLATE/                           [NEW]
│   │   └── implementation-task.md                [NEW]
│   └── pull_request_template.md                  [NEW]
│
├── .gitignore                                    [UPDATED]
├── AGENTS.md                                     [UPDATED]
├── CONTRIBUTING.md                               [UPDATED]
├── DESIGN.md                                     [NEW]
├── README.md                                     [UPDATED]
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
│   ├── README.md                                 [NEW]
│   ├── ANDROID_SKELETON_SETUP.md                  [UPDATED]
│   │
│   ├── agent-tools/                              [NEW]
│   │   ├── SETUP.md                              [NEW]
│   │   ├── REPOSITORY_STATUS.md                  [NEW]
│   │   ├── WORKFLOW.md                           [NEW]
│   │   ├── CODING_AND_COMMENTS.md                [NEW]
│   │   ├── COURSE_BASELINE.md                    [NEW]
│   │   ├── COURSE_COMPLIANCE.md                  [NEW]
│   │   ├── CURRENT_CONTRACT.md                   [NEW]
│   │   ├── SOURCE_REGISTER.md                    [NEW]
│   │   ├── JOURNEYS.md                           [NEW]
│   │   ├── MCP_SETUP.md                          [NEW]
│   │   ├── mcp.redacted.json                     [NEW]
│   │   ├── GITIGNORE_ADDITIONS.txt                [NEW]
│   │   └── reference/                            [NEW]
│   │       └── timecapsule-contract-r38.json      [NEW]
│   │
│   ├── agent-logs/                               [NEW]
│   │   ├── user-f/                               [NEW]
│   │   │   ├── agent-log-w1.md                    [NEW]
│   │   │   ├── agent-log-w2.md                    [NEW]
│   │   │   ├── agent-log-w3.md                    [NEW]
│   │   │   └── agent-log-w4.md                    [NEW]
│   │   ├── user-g/                               [NEW]
│   │   │   ├── agent-log-w1.md                    [NEW]
│   │   │   ├── agent-log-w2.md                    [NEW]
│   │   │   ├── agent-log-w3.md                    [NEW]
│   │   │   └── agent-log-w4.md                    [NEW]
│   │   └── user-j/                               [NEW]
│   │       ├── agent-log-w1.md                    [NEW]
│   │       ├── agent-log-w2.md                    [NEW]
│   │       ├── agent-log-w3.md                    [NEW]
│   │       └── agent-log-w4.md                    [NEW]
│   │
│   └── retrospectives/                           [NEW]
│       ├── user-f/                               [NEW]
│       │   └── retrospective.md                  [NEW]
│       ├── user-g/                               [NEW]
│       │   └── retrospective.md                  [NEW]
│       └── user-j/                               [NEW]
│           └── retrospective.md                  [NEW]
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
