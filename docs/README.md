# TimeCapsule documentation

Use this index to find the shared instructions and individual coursework records. The repository currently contains a provisional Android scaffold; [REPOSITORY_STATUS.md](agent-tools/REPOSITORY_STATUS.md) separates observed code from approved contracts and unverified work.

## Set up and work in the repository

| Document | Purpose |
|---|---|
| [Android checkout and skeleton setup](ANDROID_SKELETON_SETUP.md) | Open the existing Gradle project and run local checks |
| [Agent setup](agent-tools/SETUP.md) | Configure shared instructions, skills, and each student's local environment |
| [AGENTS.md](../AGENTS.md) | Root agent rules, scope protections, and required behavior |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | Branch, commit, Issue, PR, review, and merge conventions |
| [Workflow](agent-tools/WORKFLOW.md) | Step-by-step agent task and human review process |
| [Coding and comments](agent-tools/CODING_AND_COMMENTS.md) | English declaration comments, finer explanations, style, and refactor examples |
| [Course baseline](agent-tools/COURSE_BASELINE.md) | Topics covered in the supplied lectures and areas needing more explanation |
| [MCP setup](agent-tools/MCP_SETUP.md) | Local tool configuration and safe use of the shared redacted example |
| [Course compliance](agent-tools/COURSE_COMPLIANCE.md) | Professor requirements, team additions, files, and planned submission dates |

## Understand the approved project

| Document | Purpose |
|---|---|
| [Source register](agent-tools/SOURCE_REGISTER.md) | Source authority, revisions, and historical conflicts |
| [Current contract](agent-tools/CURRENT_CONTRACT.md) | Reading aid for the captured approved behavior and architecture |
| [Full revision 38 reference](agent-tools/reference/timecapsule-contract-r38.json) | Exact retrieved node/relationship records; a read-only reference, not an import payload |
| [DESIGN.md](../DESIGN.md) | Screen flow, navigation, Material 3 design system, and clearly labeled UI mapping proposals |
| [Journeys](agent-tools/JOURNEYS.md) | Planned verification paths; not claims that tests passed |
| [Repository status](agent-tools/REPOSITORY_STATUS.md) | Inspected paths, scaffold/contract discrepancies, and documentation integration rationale |

## Use shared task skills and templates

The four shared skills live under root `.agents/skills/`: [implement](../.agents/skills/timecapsule-implement/SKILL.md), [refactor](../.agents/skills/timecapsule-refactor/SKILL.md), [review](../.agents/skills/timecapsule-review/SKILL.md), and [record learning](../.agents/skills/timecapsule-record-learning/SKILL.md).

GitHub templates live at the Git root: [task Issue](../.github/ISSUE_TEMPLATE/implementation-task.md) and [pull request](../.github/pull_request_template.md). They support the workflow after they reach the default branch; they do not install branch protection or replace human review.

## Keep individual coursework records

| Student | Weekly logs | Whole-project reflection |
|---|---|---|
| User F | [Week 1](agent-logs/user-f/agent-log-w1.md), [Week 2](agent-logs/user-f/agent-log-w2.md), [Week 3](agent-logs/user-f/agent-log-w3.md), [Week 4](agent-logs/user-f/agent-log-w4.md) | [Retrospective](retrospectives/user-f/retrospective.md) |
| User G | [Week 1](agent-logs/user-g/agent-log-w1.md), [Week 2](agent-logs/user-g/agent-log-w2.md), [Week 3](agent-logs/user-g/agent-log-w3.md), [Week 4](agent-logs/user-g/agent-log-w4.md) | [Retrospective](retrospectives/user-g/retrospective.md) |
| User J | [Week 1](agent-logs/user-j/agent-log-w1.md), [Week 2](agent-logs/user-j/agent-log-w2.md), [Week 3](agent-logs/user-j/agent-log-w3.md), [Week 4](agent-logs/user-j/agent-log-w4.md) | [Retrospective](retrospectives/user-j/retrospective.md) |

Each student records their own actual model, prompts, changes, checks, errors, manual fixes, and learning. Blank templates are not completed submissions. Keep the shared identity labels consistent and do not add a real-name mapping table.
