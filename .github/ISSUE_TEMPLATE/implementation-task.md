---
name: Implementation or project change
about: Define one reviewable feature, fix, refactor, test, documentation, or configuration task.
title: ""
labels: ""
assignees: ""
---

## Goal

[Describe the problem and the result this task should produce.]

- **Type and scope:** [Feature / fix / refactor / test / documentation / configuration; MVP / approved stretch / team process]
- **Owner and reviewer:** [User F, User G, or User J; choose a different teammate to review.]
- **Work period or deadline:** [Actual agreed date or period]
- **Blocked by (hard implementation prerequisites):** [Exact Issue numbers, or explicitly none; follow docs/agent-tools/IMPLEMENTATION_ORDER.md.]
- **Readiness:** [READY / BLOCKED after checking the current base and every prerequisite.]

## Prerequisite evidence

- [ ] The approved contract is present in the selected base.
- [ ] Each prerequisite implementation is merged into that base and has relevant verification evidence; record Issue → PR/merge commit → check evidence. A closed Issue alone is insufficient.
- [ ] The owner self-assigned using their own account and identified User F/G/J; a different human reviewer and actual work period are recorded.

[If blocked, name the missing gate. Reading/review/planning may continue; implementation waits unless a maintainer explicitly records a scope/dependency change.]

## References

[Link the actual requirement, design, acceptance criteria, and test records. For documentation work, cite the relevant course/team rule when application IDs do not apply. Do not invent IDs.]

## Acceptance criteria

- [ ] [Observable result.]
- [ ] [Relevant failure, denial, or recovery behavior, if applicable.]
- [ ] [Required documentation or verification evidence.]

## Plan and boundaries

- **Files or components:** [Inspect the repository first.]
- **Existing logic to reuse:** [Actual functions/components.]
- **Code needing extra explanation:** [Unfamiliar mechanisms or complicated steps.]
- **Refactoring, if needed:** [The problem to simplify and the intended improvement.]
- **Outside this task:** [Relevant limits.]

## Verification

[Name the checks, required environment, and any manual verification owner. Record actual results in the PR.]

## Delivery

Follow root `AGENTS.md` and `docs/agent-tools/WORKFLOW.md`. Use Android Studio Agent Mode for project code, review each generated change before accepting it, and publish a task branch and PR. Keep related corrections in that PR; leave approval and merge to humans. Each participating student records their actual work in their own weekly log.

**PR:** [Add the real URL when opened.]
