# Course requirements and submissions

The professor's `Term_Project_Agent_Usage_Supplement.docx` defines the agent rules and deliverables below. Follow the main course guide for the remaining project requirements and submission procedure.

## Shared team requirements

| Professor requirement | Where to use it |
|---|---|
| Use Android Studio Agent Mode for project code. Other coding agents, including Cursor, Claude Code, Copilot, and Antigravity, are not allowed. | [AGENTS.md](../../AGENTS.md) and [WORKFLOW.md](WORKFLOW.md) |
| Use any model selectable inside Android Studio and record the model actually used. | Model field in each weekly log's feature section |
| Commit `AGENTS.md` at the project root. Keep shared Rules committed or include them in that file. | Root [AGENTS.md](../../AGENTS.md) contains the shared Rules |
| Commit shared skills at root `.agents/skills/`. | The four team skill files |
| Keep actual MCP settings in each member's Android Studio settings folder. Commit a shared copy with tokens removed at `docs/agent-tools/mcp.redacted.json`. | [MCP_SETUP.md](MCP_SETUP.md) and [mcp.redacted.json](mcp.redacted.json) |
| Never commit secrets; list sensitive files such as `local.properties` in `.aiexclude`. | Root `.aiexclude` and `.gitignore`; check the actual diff as well |
| Review every generated change before accepting it. Each student remains responsible for the submitted code and must explain it in Q&A. | Student review during implementation and teammate review of PRs |
| Provide a team design document covering screen flow/navigation and Material 3 theme, color, typography, and components. | Root [DESIGN.md](../../DESIGN.md) |

The supplement's final summary includes `DESIGN.md`. Journeys and other tools are examples, not requirements to activate every available integration. [SETUP.md](SETUP.md) explains how each member uses the shared files.

## Individual weekly logs

Each student keeps one log per week and adds a section for each feature or task. Record:

- Why you used the agent and the model selected.
- Key prompts, including corrections.
- What the agent changed and verified.
- Errors and your own manual fixes.

Describe your own participation in shared work. Record a week with no agent use honestly. An agent may organize your notes but must not invent your work, review, or learning.

| Required filename | Planned submission date |
|---|---|
| `agent-log-w1.md` | 13 November 2026 |
| `agent-log-w2.md` | 20 November 2026 |
| `agent-log-w3.md` | 27 November 2026 |
| `agent-log-w4.md` | 4 December 2026 |

| Student | Weekly folder | Final reflection |
|---|---|---|
| User F | `docs/agent-logs/user-f/` | `docs/retrospectives/user-f/retrospective.md` |
| User G | `docs/agent-logs/user-g/` | `docs/retrospectives/user-g/retrospective.md` |
| User J | `docs/agent-logs/user-j/` | `docs/retrospectives/user-j/retrospective.md` |

The separate folders keep each person's files distinct while preserving the professor's required filenames. Use the anonymous labels in shared materials.

The supplement does not specify a submission time or platform. Check the course instructions and submit your own file when individual upload is required; committing it to GitHub alone does not complete that submission. Follow later instructor announcements if dates change.

## Individual retrospective

Each student completes `retrospective.md` for the whole project:

- What worked well when using the agent?
- Where did the agent fall short?
- What can you now do independently?

Use your own experience and weekly logs. The supplement does not give a separate retrospective deadline; check the final submission instructions. Blank templates and unchecked review fields are for the responsible student to complete.

## Additional team rules

These rules come from the team's request and support the professor's explainability requirement:

| Team rule | Guide |
|---|---|
| Easy English comments for every new or meaningfully changed handwritten class/function; finer comments for unfamiliar or complicated steps. | [Coding and comments](CODING_AND_COMMENTS.md), using the [lecture baseline](COURSE_BASELINE.md) |
| Consistent code style. | Root `.editorconfig` and the coding guide |
| Reuse suitable logic and avoid tangled code. At a refactored area, explain the original problem, how the structure improves it, and whether behavior changes. | Coding guide and PR explanation |
| Submit every focused change through a GitHub PR, including documents and configuration. Keep related fixes in the same PR. | [Workflow](WORKFLOW.md), [Issue template](../../.github/ISSUE_TEMPLATE/implementation-task.md), and [PR template](../../.github/pull_request_template.md) |

A different teammate reviews each PR, and a human merges it. These written rules do not configure GitHub branch protection automatically.

## Sources

Professor supplement: Agent environment; Agent Usage Rules 1–7; Development record; Reflection; Agent Deliverables Submission Summary. See [SOURCE_REGISTER.md](SOURCE_REGISTER.md) for the course and project sources.
