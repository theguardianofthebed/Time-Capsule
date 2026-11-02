# Repository status and documentation integration

**Inspected:** 3 November 2026, Asia/Seoul (KST)  
**Repository:** `theguardianofthebed/Time-Capsule`  
**Inspected branch and commit:** `main` at `c7bb071cdda302f7562a549d5029e026b4c92a10`  
**Approved contract reference:** TimeCapsule Atlas revision 38, instructions version 3  
**Runtime verification:** **NOT RUN** during this documentation update

This document records the checkout used to fit the shared agent package to the existing project. It does not certify application behavior or change Atlas statuses. Re-read the current branch and source register before later implementation because the repository and approved contracts may advance independently.

## 1. What is already in the repository

The Git root and Android/Gradle project root coincide. The following paths existed in the inspected commit:

| Existing path | Observed purpose |
|---|---|
| `AGENTS.md` | Baseline repository-safety and project-scope rules |
| `CONTRIBUTING.md` | Task branches, commit conventions, PRs, peer review, and merge expectations |
| `README.md` | Short repository-initialization notice |
| `.gitignore` | Android/build, IDE, local-environment, and some secret exclusions |
| `settings.gradle.kts` | Declares `:app` and names the project TimeCapsule |
| `app/build.gradle.kts` | Current Compose-enabled application configuration |
| `app/src/main/java/com/example/timecapsule/MainActivity.kt` | Compose `setContent` entry activity |
| `app/src/main/java/com/example/timecapsule/ui/TimeCapsuleApp.kt` | Navigation Compose preview routes |
| `app/src/main/java/com/example/timecapsule/ui/screens/` | Welcome, Home, Create, Account, and detail scaffold screens |
| `app/src/main/java/com/example/timecapsule/ui/theme/` | Existing generated-style Compose theme files |
| `app/src/main/java/com/example/timecapsule/domain/model/` | Prototype model contracts |
| `app/src/main/java/com/example/timecapsule/data/CapsuleRepository.kt` | Repository interface; no completed backend implementation |
| `app/src/main/res/` and `app/src/main/AndroidManifest.xml` | Existing Android resources and manifest |
| `app/src/test/` and `app/src/androidTest/` | Existing test sources; presence does not prove execution |
| `gradle/`, `gradlew`, `gradlew.bat` | Existing wrapper, version catalog, and toolchain configuration |
| `docs/ANDROID_SKELETON_SETUP.md` | Historical instructions for creating/copying the initial scaffold |
| `prototype/` and `spikes/` | Existing supporting-work directories |

The relevant source is available at the [inspected commit](https://github.com/theguardianofthebed/Time-Capsule/tree/c7bb071cdda302f7562a549d5029e026b4c92a10). The application structure is retained by this documentation change.

## 2. Separate the scaffold from the approved contract

| Topic | Observed checkout | Approved contract or required interpretation | Next action |
|---|---|---|---|
| UI framework | Compose is enabled; the entry activity and navigation use Compose. | The captured approved design specifies native Android Views. | Complete a separate reviewed reconciliation task before further affected UI implementation. Do not silently expand Compose or migrate the app as documentation cleanup. |
| Authentication and sending | Welcome enters a UI preview; Send is disabled; repository APIs are interfaces. | Accounts, direct self/cross-user delivery, server acknowledgment, and access controls are MVP requirements. | Implement and verify approved slices through feature Issues/PRs. Do not describe the scaffold as a working account or send system. |
| Backend selection | The inspected Gradle dependencies do not integrate Firebase. Older setup text calls provider selection pending. | The current contract selects Firebase Authentication, Firestore, and Storage. | Treat selection as confirmed design and integration as unfinished work. Do not reopen the provider decision from old setup text. |
| Trigger design | No completed unlock/notification integration was established by this source inspection. Older setup text mentions geofences as pending. | Checks occur on app-open, foreground resume, detail-open, and explicit Retry. No production alarms, geofences, boot receivers, polling, background location, or FCM. | Follow the exact current contract when implementing; remove obsolete future-work guidance from current setup documents. |
| Theme | Existing Compose theme resources do not establish that the approved visual design is implemented. | DD-01 defines the visual identity; `DESIGN.md` separates confirmed values from proposed implementation mappings. | Review and implement the approved design in the UI reconciliation/feature work, with actual screenshots and verification. |
| Implementation status | Scaffold source and test files exist. | Captured Atlas requirements/design records are `not_started`, ACs `not_verified`, and tests `not_run`. Those are recorded evidence statuses, not a claim that the repository is empty. | Update each status only from the appropriate verified evidence and authorized workflow. |

This task edits documentation and shared agent configuration. It does not migrate UI code, add runtime dependencies, initialize Firebase, provision cloud resources, modify application directories, or mark acceptance criteria passed.

## 3. Why the documentation is integrated this way

| Area | Before | After | Why |
|---|---|---|---|
| Root `AGENTS.md` | Repository-safety rules with older project-selection language. | Retains repository protections and adds current contract boundaries, course requirements, comments/style/refactor rules, identity labels, and PR delivery obligations. | Agents need one consistent entry point without losing existing safety constraints. |
| Root `CONTRIBUTING.md` | Existing branch/commit conventions and general PR review checks. | Keeps those conventions and links the detailed agent workflow, comments, learning records, and honest verification requirements. | Teammates keep familiar repository habits while gaining the required checks. |
| Root `README.md` | Describes an initialized repository. | Describes the actual scaffold, approved target discrepancy, and links to setup and policy documents. | New contributors need an accurate starting point. |
| `docs/ANDROID_SKELETON_SETUP.md` | Says no Android app exists and asks teammates to create/copy a Compose overlay. | Opens and verifies the existing checkout, identifies the provisional scaffold, and records current configuration without claiming a build pass. | Following the old instructions could overwrite current work or repeat initialization. |
| Root `DESIGN.md` | No root design document at the inspected commit. | Adds the required screen flow, navigation, and Material 3 design documentation with source/status labels. | Provides the professor's shared design deliverable and a consistent implementation reference. |
| `docs/README.md` and `docs/agent-tools/SETUP.md` | No current shared document index or installed-package setup guide. | Adds an index and the project-specific agent setup guide. | Teammates can find the right instructions without separate personal copies. |
| Student documentation | No packaged individual log/reflection directories in the inspected checkout. | Adds blank templates under `user-f`, `user-g`, and `user-j`. | Preserves required filenames without collisions and uses the requested shared identity labels. |

These are documentation replacements and additions. Old versions remain in Git history; retaining duplicate obsolete policy files would make current instructions harder to identify.

## 4. Directory additions and justification

| Added path | Why it belongs here |
|---|---|
| `.agents/skills/` | The professor requires committed shared skills at the project root. |
| `.github/ISSUE_TEMPLATE/` and `.github/pull_request_template.md` | GitHub discovers Issue and PR templates from the repository's standard root location. |
| `docs/agent-tools/` | Extends the existing `docs/` area with shared rules, setup, source references, and a token-free MCP example. |
| `docs/agent-tools/reference/` | Keeps the full frozen contract next to its source register and summary. |
| `docs/agent-logs/user-f/`, `user-g/`, `user-j/` | Separates each student's four weekly files while preserving the required filenames. |
| `docs/retrospectives/user-f/`, `user-g/`, `user-j/` | Keeps each student's final reflection separate and uses the same identity convention. |

**No existing application directory is moved or renamed.** The root already serves both Git and Gradle, so another wrapper directory would add confusion. `app/`, `gradle/`, `prototype/`, and `spikes/` retain their locations. Existing ignore rules are preserved while the shared-file and secret exclusions are merged.

## 5. Overlapping draft PR

At integration time, [draft PR #5](https://github.com/theguardianofthebed/Time-Capsule/pull/5), on `docs/4-notion-handoff`, contains an older October 13 handoff from another author. It overlaps root instructions, contribution guidance, the README, templates, and the documentation index, and includes older undecided contract choices.

This task leaves that draft PR unchanged and uses a separate Issue and PR based on the inspected current `main`. Before merging PR #5 later, its author and reviewer must reconcile the overlapping files against the then-current approved contract and merged documentation. Keep still-useful work, but do not reintroduce obsolete decisions or inconsistent student labels. An open draft is not automatically the current source of truth.

## 6. Verification boundaries

Source inspection established the paths and configuration described here. The documentation PR records the actual checks run for text, paths, links, identity labels, JSON/YAML, ignore behavior, and the changed-file scope. A checklist in a template is not evidence that a student completed it.

For this documentation update, Gradle Sync, Android compilation, unit tests, lint, emulator/device execution, Firebase behavior, and end-to-end acceptance checks are **NOT RUN**. They must remain separate from document validation. See [ANDROID_SKELETON_SETUP.md](../ANDROID_SKELETON_SETUP.md) for future local commands and [WORKFLOW.md](WORKFLOW.md) for recording real results.

Human review and merge remain pending until the actual PR receives them. This document does not assert that branch protection or any local Android Studio/MCP connection has been configured.
