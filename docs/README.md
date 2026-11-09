# TimeCapsule documentation

## Four core agent documents

| Guide | Use it to… |
|---|---|
| [AGENTS.md](../AGENTS.md) | Follow the mandatory agent and repository rules |
| [Project contract](agent-tools/CURRENT_CONTRACT.md) | Read current scope, behavior, architecture, dependencies, and trace IDs |
| [Workflow](agent-tools/WORKFLOW.md) | Complete one task from Issue to reviewed PR |
| [Coding and comments](agent-tools/CODING_AND_COMMENTS.md) | Write clear comments and explain refactoring |

Start with these four. The remaining files are supporting references, templates, configuration, or individual records rather than additional mandatory entry documents.

## Setup and supporting references

| Guide | Use it to… |
|---|---|
| [Android setup](ANDROID_SKELETON_SETUP.md) | Open the project and run local checks |
| [Agent setup](agent-tools/SETUP.md) | Confirm the four core documents and choose a skill |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | Check repository-specific branch and commit names |
| [Course requirements](agent-tools/COURSE_COMPLIANCE.md) | Review the original deliverables and submission dates |

## Design and technical references

| Document | Purpose |
|---|---|
| [Repository guide](agent-tools/REPOSITORY_STATUS.md) | Code locations and implementation boundaries |
| [DESIGN.md](../DESIGN.md) | S-01–S-08 screen contract and visual design |
| [Views decision and starter baseline](agent-tools/UI_VIEWS_UPDATE_2026-11-07.md) | Confirmed 7 November UI update and AGP 9.3.0 exception |
| [Implementation order](agent-tools/IMPLEMENTATION_ORDER.md) | Issue prerequisites and readiness gates |
| [Visual Issue roadmap](agent-tools/ISSUE_ROADMAP.md) | Full dependency graph, enabled capabilities and concrete demo milestones |
| [Readiness report](agent-tools/READINESS_REPORT.md) | Current evidence, blockers and prioritized next actions |
| [Readiness preflight](agent-tools/READINESS.md) | Check prerequisites, ownership and verification before coding |
| [Dependency manifest](agent-tools/dependencies.json) | Machine-readable direct Issue dependencies and dated statuses |
| [Task handoff](agent-tools/handoffs/TEMPLATE.md) | Preserve active ownership, exact progress and unfinished checks |
| [Source register](agent-tools/SOURCE_REGISTER.md) | Source authority and references |
| [Historical full contract](agent-tools/reference/timecapsule-contract-r38.json) | Public r38 records, preserved as historical provenance |
| [Current revision verification](agent-tools/reference/timecapsule-contract-r55-verification.json) | r55 counts/hash/comparison only; current exact records require authorized MCP or a privately supplied snapshot |
| [Journeys](agent-tools/JOURNEYS.md) | Procedures for checking app behavior |

Agents and reviewers should read the exact contract records relevant to a task, including the linked requirements and tests. The JSON is a reference file, not an Atlas import file.

## Skills and templates

Choose the shared skill for the task: [implement](../.agents/skills/timecapsule-implement/SKILL.md), [refactor](../.agents/skills/timecapsule-refactor/SKILL.md), [review](../.agents/skills/timecapsule-review/SKILL.md), or [record learning](../.agents/skills/timecapsule-record-learning/SKILL.md).

Use the [Issue template](../.github/ISSUE_TEMPLATE/implementation-task.md) to define a task and the [PR template](../.github/pull_request_template.md) to explain the result.

The [ongoing work-log guide](agent-logs/README.md) separates factual task handoffs from student-authored coursework.

## Individual coursework

| Student | Weekly logs | Reflection |
|---|---|---|
| User F | [Week 1](agent-logs/user-f/agent-log-w1.md), [Week 2](agent-logs/user-f/agent-log-w2.md), [Week 3](agent-logs/user-f/agent-log-w3.md), [Week 4](agent-logs/user-f/agent-log-w4.md) | [Retrospective](retrospectives/user-f/retrospective.md) |
| User G | [Week 1](agent-logs/user-g/agent-log-w1.md), [Week 2](agent-logs/user-g/agent-log-w2.md), [Week 3](agent-logs/user-g/agent-log-w3.md), [Week 4](agent-logs/user-g/agent-log-w4.md) | [Retrospective](retrospectives/user-g/retrospective.md) |
| User J | [Week 1](agent-logs/user-j/agent-log-w1.md), [Week 2](agent-logs/user-j/agent-log-w2.md), [Week 3](agent-logs/user-j/agent-log-w3.md), [Week 4](agent-logs/user-j/agent-log-w4.md) | [Retrospective](retrospectives/user-j/retrospective.md) |

Record your own model, prompts, contribution, checks, errors, manual fixes, and learning. Use the shared student labels and keep private details out of these files.
