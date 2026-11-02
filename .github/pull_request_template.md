## Purpose and scope

[Explain the problem, why this change is needed, and the resulting behavior.]

- **Issue:** [Link; use a closing keyword only when this PR actually completes it.]
- **Change type:** [Feature / fix / refactor / test / documentation / configuration]
- **Scope:** [MVP / approved stretch / team process; link the relevant decision.]
- **Requirement, acceptance criteria, and design:** [Actual IDs or links; explain any not-applicable item.]
- **Owner and requested human reviewer:** [User F, User G, or User J]

One PR covers one coherent task. Related review fixes stay here. Every change type, including documentation and configuration, follows this process.

## What changed

[Name the important files or components and explain how they now work. Identify any contract or dependency change.]

## Comments and unfamiliar code

[Point to the English class/function comments and finer comments for unfamiliar mechanisms. Explain any relevant concept beyond the lecture baseline. For a change without code, write “Not applicable” and why.]

## Refactoring

- **Before:** [Duplication, mixed responsibilities, or other concrete problem.]
- **After and reason:** [What improved and why behavior is preserved or intentionally changed.]
- **Local comment:** [Location of the concise before/after explanation.]
- **Verification:** [Evidence covering behavior that could regress.]

[If no refactoring occurred, replace this section's fields with that statement.]

## Verification

| Check actually performed | Environment or procedure | Observed result and evidence |
| --- | --- | --- |
| [Fill in.] | [Fill in.] | [Fill in.] |

**Not run or unresolved:** [Checks not performed, reasons, failures, and remaining risks. Do not report a test as passed without running it.]

**UI evidence, when relevant:** [Sanitized screenshots or recording and the behavior shown.]

## Agent use and student understanding

- **Agent use:** [Actual environment and model; project code must use Android Studio Agent Mode. Identify documentation-only assistance honestly.]
- **Individual log:** [Links to the involved students' relevant weekly entries.]
- **Agent contribution:** [Actual work.]
- **Human decisions and manual changes:** [Actual work.]
- **Explanation:** [Purpose, data flow, Android mechanism, failure behavior, and verification, as relevant.]

## Author checks

- [ ] I inspected the complete diff, including agent changes.
- [ ] English comments and style follow the shared rules for the affected code.
- [ ] I checked for duplicated logic and unnecessary complexity.
- [ ] Verification results are accurate; incomplete work is clearly marked.
- [ ] No secrets or private user data were added.
- [ ] Relevant design, journeys, and individual logs are updated.

## Human review and merge

**Reviewer:** [A teammate other than the PR author]  
**Review result and evidence:** [Pending until the human reviewer completes it.]

The agent must not claim human approval, approve its own work, or merge this PR. Keep incomplete verification or unresolved review clearly visible and use draft status when appropriate.

Repository guidance: `AGENTS.md`, `docs/agent-tools/WORKFLOW.md`, and `docs/agent-tools/CODING_AND_COMMENTS.md`. Use actual repository permalinks when adding clickable links to this PR body.

