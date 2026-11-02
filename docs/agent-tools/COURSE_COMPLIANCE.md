# Course requirements and document placement

This package prepares the team's agent rules and submission templates. It contains no completed development history or personal reflection. The source for official agent requirements is the supplied **2026 Mobile Programming Term Project Supplement Agent Usage Requirements**, in `Term_Project_Agent_Usage_Supplement.docx`.

The supplement covers agent usage only. The main term project guide still governs other features, scheduling, code, slides, video, and final submission details. This document distinguishes the professor's requirements from the user's additional team rules and the package's organizational choices.

## Official professor requirements

| Requirement and source location | Where this package addresses it |
| --- | --- |
| Android Studio Agent Mode is the only allowed coding-agent environment. Other coding agents such as Cursor, Claude Code, Copilot, and Antigravity are not allowed for project code. Agent environment and rule 1. | [AGENTS.md](../../AGENTS.md) and [WORKFLOW.md](WORKFLOW.md). |
| Any model selectable inside Android Studio may be used. Record the model actually used. Agent environment. | A model field in every feature section of each individual weekly log. |
| The shared team `AGENTS.md` must be at the project root and committed. Rule 2. | [AGENTS.md](../../AGENTS.md). |
| Shared rules must be committed and not excluded by `.gitignore`, or included in `AGENTS.md`. Rule 3. | The root agent instructions are authoritative; supporting policy documents remain committed. A duplicate Rules file is unnecessary. |
| Team skills belong in the root `.agents/skills` directory and must be committed. Rule 4. | The packaged team skills under `.agents/skills/`. They contain reusable instructions, not private installed settings. |
| Actual MCP settings stay in each member's Android Studio settings folder. Commit one shared copy with all tokens removed at `docs/agent-tools/mcp.redacted.json`. Rule 5. | [MCP_SETUP.md](MCP_SETUP.md) and [mcp.redacted.json](mcp.redacted.json). No connection is claimed by the template. |
| Never commit secrets. List secret files, such as `local.properties`, in `.aiexclude`. Rule 6. | Root `.aiexclude`, the setup guidance, and the review checks. Git exclusion must also be handled separately. |
| Review every change before accepting it; students are responsible for all submitted code and must explain any part in Q&A. Rule 7. | Human review and explanation checks in the workflow, PR template, and weekly logs. |
| Team design document covering screen flow, navigation, and the design system, including Material 3 theme, color, typography, and components. Deliverables table and final summary. | [DESIGN.md](../../DESIGN.md). Verify its stated decisions against the actual approved design before implementation. |
| Every student keeps an individual agent log, with separate per-feature sections for purpose, key prompts, changes and verification, and errors and manual fixes. Development record and weekly summary. | Twelve individual weekly log templates under `docs/agent-logs/`. |
| Every student writes an individual whole-project retrospective covering what worked, agent limitations, and what they can now do independently. Reflection and final summary. | Three individual templates under `docs/retrospectives/`. |

The design row says “if applicable,” while the final submission summary lists `DESIGN.md` as a team deliverable. This package includes it. The summary also mentions journeys and other tools as examples; it does not specify an additional mandatory journey filename or require the team to activate every available integration.

## Individual weekly submissions

The professor calls the dates below planned submissions. No time of day or submission platform is specified in the supplement.

| Week | Required filename | Planned due date |
| --- | --- | --- |
| 1 | `agent-log-w1.md` | 13 November 2026 |
| 2 | `agent-log-w2.md` | 20 November 2026 |
| 3 | `agent-log-w3.md` | 27 November 2026 |
| 4 | `agent-log-w4.md` | 4 December 2026 |

The per-person directories prevent filename collisions. They are a team organization choice; the weekly filenames and individual submissions come from the professor.

| Student | Weekly files | Final reflection |
| --- | --- | --- |
| User F | `docs/agent-logs/user-f/agent-log-w1.md` through `agent-log-w4.md` | `docs/retrospectives/user-f/retrospective.md` |
| User G | `docs/agent-logs/user-g/agent-log-w1.md` through `agent-log-w4.md` | `docs/retrospectives/user-g/retrospective.md` |
| User J | `docs/agent-logs/user-j/agent-log-w1.md` through `agent-log-w4.md` | `docs/retrospectives/user-j/retrospective.md` |

Use one file for the whole week and add sections as work occurs. Each student records their own participation. For a shared feature, describe each person's actual contribution rather than duplicating identical personal statements. If there was no agent use, record that honestly. Do not manufacture activity to fill a template.

Before submission, confirm the actual submission method in the main course guide. Submit your own file when individual upload is required; a team repository alone does not prove that an individual submission was made.

## Additional rules requested by the team

The following are the user's requested working rules. They support the course obligations but are not quoted professor requirements.

| Team rule | How to apply it |
| --- | --- |
| Easy English comments for each implemented class and function. | Follow [CODING_AND_COMMENTS.md](CODING_AND_COMMENTS.md), including private helpers and meaningful changes to existing code. |
| Finer comments for unfamiliar or complicated code. | Use [COURSE_BASELINE.md](COURSE_BASELINE.md) to identify the lecture baseline. Explain unfamiliar mechanisms in small logical steps near the code. |
| A consistent coding style. | Follow the shared coding rules and the repository's agreed formatting configuration. |
| Avoid duplicate and tangled code. Explain refactoring before and after. | Put a concise explanation near the refactored code and the fuller account in the PR. Preserve old code in Git history rather than leaving large blocks of commented-out code. |
| Every coherent change goes through a GitHub PR. | Include application code, tests, documentation, and configuration. Continued fixes to the same task stay in the same PR. No direct push to `main`, self-approval, or agent merge. |

The [PR template](../../.github/pull_request_template.md) and [task template](../../.github/ISSUE_TEMPLATE/implementation-task.md) make these checks repeatable. Templates do not enforce GitHub branch protection by themselves.

## Repository integration

The shared files have been fitted to the existing root of `theguardianofthebed/Time-Capsule`; its Git root and Gradle root coincide. [SETUP.md](SETUP.md) is the repository-specific adoption guide. [REPOSITORY_STATUS.md](REPOSITORY_STATUS.md) explains which existing documents were updated, why new folders were added, and the unresolved Views/Compose discrepancy. The application directories keep their existing paths.

## How to place and maintain these files

1. Copy the package's `repo/` contents into the project repository root, normally also the Gradle root. Keep `.github` at the Git root. For a nested Android project, retain the shared package at the Git root, record the Android subdirectory for builds, and verify/attach the shared root instructions and skills in Android Studio. Preserve the leading dots in `.github`, `.agents`, and `.aiexclude`.
2. Review existing files before merging the supplied text. Keep valid project-specific rules and do not replace the application's README, ignore patterns, or build files blindly.
3. Read [AGENTS.md](../../AGENTS.md), [WORKFLOW.md](WORKFLOW.md), [SOURCE_REGISTER.md](SOURCE_REGISTER.md), and the setup instructions together. Resolve a relevant source conflict before asking an agent to implement the affected behavior.
4. Commit the shared documents and templates through a documentation PR. A different team member reviews it. A student performs the final merge under the team's repository rules.
5. Each member opens the same repository in Android Studio, confirms the shared instructions are available to Agent Mode, and follows [MCP_SETUP.md](MCP_SETUP.md) for any actual integration.
6. Update the appropriate individual log after a work session. Complete personal reflections from real experience near final submission. Keep [JOURNEYS.md](JOURNEYS.md) aligned with the accepted design and checks actually performed.

A checked box is an assertion, not decoration. Agents must leave human review and personal understanding fields for the responsible student to complete.

## Suggested ownership and timing

These assignments organize the existing team roles; they do not transfer responsibility for understanding the project.

| Work | Coordinator or owner | Period or deadline | Dependency |
| --- | --- | --- | --- |
| Review shared rules and source references | User G, with all three members | Before the first implementation task using this package | Current approved requirements and design |
| Confirm design presentation and screen flow | User F, with all three members | Before implementing the affected screen or journey | Approved feature scope and UI decisions |
| Verify local setup and repository workflow | User J; each member verifies their own machine | Before that member's first agent coding session | Android Studio and repository access |
| Write individual logs | Each student | After sessions; submit by the listed weekly dates | Actual prompts, changes, and verification evidence |
| Review a PR | A teammate other than its author | Before acceptance and merge | Reviewable diff and honest verification record |
| Write individual retrospective | Each student | By the final deadline in the main course guide | Their own logs and demonstrated understanding |

These are team process tasks. They introduce no MVP or stretch application feature and do not authorize a product-scope change.

