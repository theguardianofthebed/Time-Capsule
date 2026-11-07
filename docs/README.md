# TimeCapsule documentation

## Set up and work

| Guide | Use it to… |
|---|---|
| [Android setup](ANDROID_SKELETON_SETUP.md) | Open the project and run local checks |
| [Agent setup](agent-tools/SETUP.md) | Load shared instructions, choose a skill, and connect tools |
| [AGENTS.md](../AGENTS.md) | Follow the mandatory agent and repository rules |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | Name branches and commits, open PRs, and review changes |
| [Workflow](agent-tools/WORKFLOW.md) | Complete one task from Issue to reviewed PR |
| [Implementation order](agent-tools/IMPLEMENTATION_ORDER.md) | Choose a ready issue and verify every prerequisite |
| [Coding and comments](agent-tools/CODING_AND_COMMENTS.md) | Write clear comments and explain refactoring |
| [Course baseline](agent-tools/COURSE_BASELINE.md) | Check what the lectures cover and what needs extra explanation |
| [MCP setup](agent-tools/MCP_SETUP.md) | Configure a personal tool connection |
| [Course requirements](agent-tools/COURSE_COMPLIANCE.md) | Check deliverables, filenames, and submission dates |

## Design and technical references

| Document | Purpose |
|---|---|
| [Repository guide](agent-tools/REPOSITORY_STATUS.md) | Code locations and implementation boundaries |
| [DESIGN.md](../DESIGN.md) | S-01–S-08 screen contract and visual design |
| [Views decision and starter baseline](agent-tools/UI_VIEWS_UPDATE_2026-11-07.md) | Confirmed UI update, matching repository versions, and pending Atlas reconciliation |
| [Project contract](agent-tools/CURRENT_CONTRACT.md) | Detailed behavior and architecture rules |
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
