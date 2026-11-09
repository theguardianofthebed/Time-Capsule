---
name: timecapsule-implement
description: Implement a small approved TimeCapsule feature or bug fix in Android Studio Agent Mode with English teaching comments, current contract traceability, targeted verification, an individual log entry, and a GitHub PR. Use for a scoped implementation Issue or behavior fix.
---

# Implement a TimeCapsule change

Use this procedure inside Android Studio Agent Mode. Read [AGENTS.md](../../../AGENTS.md), [WORKFLOW.md](../../../docs/agent-tools/WORKFLOW.md), and [CODING_AND_COMMENTS.md](../../../docs/agent-tools/CODING_AND_COMMENTS.md). The rules apply even if this skill is invoked independently.

## Establish the task

1. Identify the acting student, actual Issue, intended behavior, owner, period, dependencies, and MVP/stretch classification. Reuse the existing Issue; create one for an authorized change when needed. Documentation tasks can cite a course/team rule instead of inventing application ACs.
2. Read [CURRENT_CONTRACT.md](../../../docs/agent-tools/CURRENT_CONTRACT.md) and the relevant current detailed records. Distinguish confirmed contracts from design proposals and older examples.
3. Inspect repository identity, branch, working tree, applicable instructions, `CONTRIBUTING.md`, current implementation, wrapper, modules, and dependency pins. Preserve others' work. Use a task branch, never direct default-branch edits for publication.
4. State a short plan and targeted verification. Resolve an actual missing decision before the affected implementation; continue independent work. Do not ask repeatedly for routine work already authorized.

## Make the smallest complete change

5. Search for existing implementations of the same rule or responsibility. Reuse or extend them; avoid a parallel validator, repository, state machine, or permission helper with overlapping behavior.
6. Implement a small end-to-end slice following the approved account/data contracts. For UI work, follow the approved Kotlin + Views/XML + ViewBinding rule in `CURRENT_CONTRACT.md`. Do not silently choose or migrate frameworks, add a duplicate database, or revive old background mechanisms.
7. Add plain English KDoc for each new or materially changed handwritten class/function, including private helpers and overrides. Add step comments around unfamiliar asynchronous, session, authorization, storage, and recovery mechanisms. Keep comments faithful to the code.
8. Present each diff for the student's review before acceptance. Explain what changed, why it is needed, and how a failure is handled. If restructuring existing code, apply the local explanation policy from the refactor skill.

## Verify and publish

9. Run the actual relevant build, tests, lint, and manual journey checks that the environment supports. Record exact results and limits. Do not manufacture a pass or silently weaken an acceptance criterion to get green output.
10. Inspect the accepted staged diff for scope, comments, duplication, formatting, and secrets. Prepare the PR using the repository template, commit the intended files, push the task branch, and open/update an ordinary GitHub PR through the available authorized integration.
11. Return the actual PR URL. If publication is blocked, preserve the work and PR body, state `PR not opened`, and give the precise required access/setup action. Do not substitute another coding agent. Do not self-approve or merge.
12. Automatically prepare the acting student's current weekly log update with the actual model, key prompts, agent changes/checks, errors, and agent corrections. Ask before recording manual fixes, review, understanding, or learning, then show the completed log diff for confirmation.

## Handoff

Report the resulting behavior, relevant trace IDs/files, actual verification and gaps, one unfamiliar mechanism to explain, log location, PR URL or blocker, and next human review action. Distinguish PR opened, merged, and accepted behavior.

## Ownership, dependencies, and recovery

Read [READINESS.md](../../../docs/agent-tools/READINESS.md). Use the explicitly confirmed User F/G/J owner; ask if absent and continue only read-only preparation until identified. Before implementation or refactoring, inspect current Issues, branches, PRs, and handoffs, complete all eight readiness gates, and run the validator with fresh evidence. A closed Issue, unmerged PR, or isolated spike cannot satisfy a predecessor. If any gate is missing or fails, report BLOCKED and limit work to planning/review. Do not bypass a dependency by narrowing a slice without an approved dependency change.

Keep the existing owner and boundaries on resume. Re-read current discussions and compare actual branch/base/head/diff to the handoff, then rerun readiness. Record approved sharing/transfers before overlapping work. Update the owner's ongoing [agent log](../../../docs/agent-logs/README.md) and [handoff](../../../docs/agent-tools/handoffs/TEMPLATE.md) before interruption or completion, including delegated contributions, unfinished implementation, test gaps, and the first next action. Weekly coursework and personal learning remain separate; never invent human confirmation. An agent review is not a human PR approval.
