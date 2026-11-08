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
| [DESIGN.md](../DESIGN.md) | Screen flow and visual design |
| [Source register](agent-tools/SOURCE_REGISTER.md) | Source authority and references |
| [Full contract reference](agent-tools/reference/timecapsule-contract-r38.json) | Exact requirement, design, acceptance, and test records |
| [Journeys](agent-tools/JOURNEYS.md) | Procedures for checking app behavior |

Agents and reviewers should read the exact contract records relevant to a task, including the linked requirements and tests. The JSON is a reference file, not an Atlas import file.

## Skills and templates

Choose the shared skill for the task: [implement](../.agents/skills/timecapsule-implement/SKILL.md), [refactor](../.agents/skills/timecapsule-refactor/SKILL.md), [review](../.agents/skills/timecapsule-review/SKILL.md), or [record learning](../.agents/skills/timecapsule-record-learning/SKILL.md).

Use the [Issue template](../.github/ISSUE_TEMPLATE/implementation-task.md) to define a task and the [PR template](../.github/pull_request_template.md) to explain the result.

## Individual coursework

| Student | Weekly logs | Reflection |
|---|---|---|
| User F | [Week 1](agent-logs/user-f/agent-log-w1.md), [Week 2](agent-logs/user-f/agent-log-w2.md), [Week 3](agent-logs/user-f/agent-log-w3.md), [Week 4](agent-logs/user-f/agent-log-w4.md) | [Retrospective](retrospectives/user-f/retrospective.md) |
| User G | [Week 1](agent-logs/user-g/agent-log-w1.md), [Week 2](agent-logs/user-g/agent-log-w2.md), [Week 3](agent-logs/user-g/agent-log-w3.md), [Week 4](agent-logs/user-g/agent-log-w4.md) | [Retrospective](retrospectives/user-g/retrospective.md) |
| User J | [Week 1](agent-logs/user-j/agent-log-w1.md), [Week 2](agent-logs/user-j/agent-log-w2.md), [Week 3](agent-logs/user-j/agent-log-w3.md), [Week 4](agent-logs/user-j/agent-log-w4.md) | [Retrospective](retrospectives/user-j/retrospective.md) |

Record your own model, prompts, contribution, checks, errors, manual fixes, and learning. Use the shared student labels and keep private details out of these files.
