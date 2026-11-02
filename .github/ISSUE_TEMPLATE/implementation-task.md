---
name: Implementation or project change
about: Define one reviewable feature, fix, refactor, test, documentation, or configuration task.
title: ""
labels: ""
assignees: ""
---

## Goal and scope

[Describe the problem and observable result. Keep this task small enough for one coherent PR.]

- **Type:** [Feature / fix / refactor / test / documentation / configuration]
- **Scope:** [MVP / approved stretch / team process]
- **Owner:** [User F, User G, or User J; collaboration is welcome.]
- **Reviewer:** [A different teammate, using User F, User G, or User J]
- **Deadline or work period:** [Actual agreed date or period]
- **Dependencies:** [Issues, decisions, interfaces, access, or none with reason]

## Requirement and design references

- **Requirement:** [Existing ID and source, or reason not applicable.]
- **Acceptance criteria:** [Existing IDs and sources, if applicable.]
- **Design:** [Existing design IDs, sections, or paths.]
- **Decisions needed before implementation:** [Concrete unresolved dependency, or none.]

Do not invent requirement IDs or silently change the product scope.

## Acceptance criteria

- [ ] [An observable outcome.]
- [ ] [Relevant failure, denial, boundary, or recovery behavior.]
- [ ] [Required documentation or verification evidence.]

Adapt these criteria to the actual change. A documentation task does not need artificial application behavior.

## Implementation boundaries

- **Expected files or components:** [Fill in after inspecting the repository.]
- **Existing logic to reuse:** [Actual functions or components.]
- **Unfamiliar mechanisms to explain:** [Concepts needing finer English comments.]
- **Refactoring needed:** [Before/after intent, or none.]
- **Out of scope:** [Only meaningful limits for this task.]

## Verification plan

[Name the checks that can show the criteria are met, the required environment, and who will perform any manual checks. Record results later in the PR.]

## Agent and PR workflow

Use Android Studio Agent Mode for agent-generated project code. Read the root `AGENTS.md` and `docs/agent-tools/WORKFLOW.md` before editing.

Create a task branch and PR; include code, tests, documentation, and configuration changes in the same coherent review. Continue related fixes in that PR. Do not push directly to `main`, self-approve, or let an agent merge. Update each participating student's own weekly log with actual prompts, changes, verification, and manual fixes.

**PR link:** [Add the real link after creation.]

