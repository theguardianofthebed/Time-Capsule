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
