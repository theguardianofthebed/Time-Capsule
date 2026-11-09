---
name: timecapsule-refactor
description: Refactor duplicated or tangled TimeCapsule code in Android Studio Agent Mode while preserving approved behavior, adding local comments that explain the problem and improvement, checking regression risks, and delivering a reviewable PR. Use for reuse, extraction, responsibility cleanup, or reducing complex control flow.
---

# Refactor a TimeCapsule responsibility

Read [AGENTS.md](../../../AGENTS.md), [CURRENT_CONTRACT.md](../../../docs/agent-tools/CURRENT_CONTRACT.md), [CODING_AND_COMMENTS.md](../../../docs/agent-tools/CODING_AND_COMMENTS.md), and [WORKFLOW.md](../../../docs/agent-tools/WORKFLOW.md) before editing. Keep project coding inside Android Studio Agent Mode.

## Establish the behavior that must remain true

1. Identify the actual Issue, acting student, task branch, current contract references, and affected call sites. Inspect existing changes before touching files.
2. Describe the concrete duplication or control-flow problem using observed file/function names. Explain which shared responsibility should own it after the change. Do not label code “spaghetti” without showing the problem.
3. State whether the task preserves behavior or includes an approved behavior fix. List relevant invariants, including error messages, null handling, side effects, account identity, callback lifetime, send identity, and locked-content boundaries when affected.
4. Reuse meaningful tests or record a small before-change reproduction for the specific risk. If execution is unavailable or already fails, record that truthfully. Do not claim regression safety from unexecuted tests.

## Refactor with an explanation at the changed area

5. Extract or simplify one responsibility at a time. Update every affected caller. Avoid new generic frameworks or service locators when a small function or existing component suffices. Do not merge operations whose ownership or authorization differs merely because their code looks similar.
6. Leave a concise English note near the shared responsibility or reworked block. Explain the previous duplication or complexity, how the revised code improves it, and whether behavior is preserved. Remove dead implementation copies; the complete old code remains in Git history.
7. Add or update the required English KDoc on every implemented or materially changed declaration. Explain complicated steps locally. Remove misleading comments from old call sites when the responsibility moves.
8. Let the student inspect each proposed diff before accepting it. Explain the new data flow and any behavior change explicitly.

## Demonstrate the result and publish

9. Re-run the baseline checks and any additional checks needed for changed interfaces or lifecycle behavior. Check that duplicate implementations and obsolete paths are actually gone, while unrelated functionality remains untouched.
10. In the PR, explain the problem removed, the reason for the extraction, affected callers, whether behavior changed, actual verification, and limits. Include the required weekly log update.
11. Publish the coherent change on its task branch and open/update its ordinary GitHub PR. Return the real URL or a precise `PR not opened` blocker with the prepared body. Do not self-approve, merge, or invoke another coding agent.

## Handoff question

Ask the student to explain: “Where did this responsibility live before, where does it live now, and what observation supports the claim that the intended behavior is unchanged?” Record their own answer or learning gap, not an invented achievement.

## Ownership, dependencies, and recovery

Read [READINESS.md](../../../docs/agent-tools/READINESS.md). Use the explicitly confirmed User F/G/J owner; ask if absent and continue only read-only preparation until identified. Before implementation or refactoring, inspect current Issues, branches, PRs, and handoffs, complete all eight readiness gates, and run the validator with fresh evidence. A closed Issue, unmerged PR, or isolated spike cannot satisfy a predecessor. If any gate is missing or fails, report BLOCKED and limit work to planning/review. Do not bypass a dependency by narrowing a slice without an approved dependency change.

Keep the existing owner and boundaries on resume. Re-read current discussions and compare actual branch/base/head/diff to the handoff, then rerun readiness. Record approved sharing/transfers before overlapping work. Update the owner's ongoing [agent log](../../../docs/agent-logs/README.md) and [handoff](../../../docs/agent-tools/handoffs/TEMPLATE.md) before interruption or completion, including delegated contributions, unfinished implementation, test gaps, and the first next action. Weekly coursework and personal learning remain separate; never invent human confirmation. An agent review is not a human PR approval.
