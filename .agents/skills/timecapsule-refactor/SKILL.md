---
name: timecapsule-refactor
description: Refactor duplicated or tangled TimeCapsule code in Android Studio Agent Mode while preserving approved behavior, adding local Before After Why comments, checking regression risks, and delivering a reviewable PR. Use for reuse, extraction, responsibility cleanup, or reducing complex control flow.
---

# Refactor a TimeCapsule responsibility

Read [AGENTS.md](../../../AGENTS.md), [CODING_AND_COMMENTS.md](../../../docs/agent-tools/CODING_AND_COMMENTS.md), [WORKFLOW.md](../../../docs/agent-tools/WORKFLOW.md), and the current source register before editing. Keep project coding inside Android Studio Agent Mode.

## Establish the behavior that must remain true

1. Identify the actual Issue, acting student, task branch, current contract references, and affected call sites. Inspect existing changes before touching files.
2. Describe the concrete duplication or control-flow problem using observed file/function names. Explain which shared responsibility should own it after the change. Do not label code “spaghetti” without showing the problem.
3. State whether the task preserves behavior or includes an approved behavior fix. List relevant invariants, including error messages, null handling, side effects, account identity, callback lifetime, send identity, and locked-content boundaries when affected.
4. Reuse meaningful tests or record a small before-change reproduction for the specific risk. If execution is unavailable or already fails, record that truthfully. Do not claim regression safety from unexecuted tests.

## Refactor with an explanation at the changed area

5. Extract or simplify one responsibility at a time. Update every affected caller. Avoid new generic frameworks or service locators when a small function or existing component suffices. Do not merge operations whose ownership or authorization differs merely because their code looks similar.
6. Leave a concise plain English local note stating **Before**, **After**, and **Why**, plus whether behavior is preserved. Put it near the new shared responsibility or materially reworked block. Remove dead implementation copies; the complete old code remains in Git history.
7. Add or update the required English KDoc on every implemented or materially changed declaration. Explain complicated steps locally. Remove misleading comments from old call sites when the responsibility moves.
8. Let the student inspect each proposed diff before accepting it. Explain the new data flow and any behavior change explicitly.

## Demonstrate the result and publish

9. Re-run the meaningful before/after checks and any additional checks needed for changed interfaces or lifecycle behavior. Check that duplicate implementations and obsolete paths are actually gone, while unrelated functionality remains untouched.
10. In the PR, give a readable before/after comparison, the reason for the extraction, affected callers, behavior-preserved or behavior-changed statement, actual verification, and limits. Include the required weekly log update.
11. Publish the coherent change on its task branch and open/update its ordinary GitHub PR. Return the real URL or a precise `PR not opened` blocker with the prepared body. Do not self-approve, merge, or invoke another coding agent.

## Handoff question

Ask the student to explain: “Where did this responsibility live before, where does it live now, and what observation supports the claim that the intended behavior is unchanged?” Record their own answer or learning gap, not an invented achievement.
