---
name: timecapsule-record-learning
description: Help one TimeCapsule student record an actual Android Studio agent session in their weekly log or organize their own final retrospective. Use after implementation or review and before individual submission; preserve evidence and never invent prompts, manual fixes, results, or learning achievements.
---

# Record one student's actual work

Read [AGENTS.md](../../../AGENTS.md) and [WORKFLOW.md](../../../docs/agent-tools/WORKFLOW.md).

1. At the end of every implementation, review, refactoring, documentation, or configuration task, identify the acting student and current week and prepare an update to that student's weekly log automatically. Do not wait for a separate request. If the student or week is unknown, ask before choosing a file. Use only that student's existing file, preserve previous entries, and do not copy another student's contribution as if it were theirs.
2. Collect objective facts directly from the actual session: date, model selected inside Android Studio, feature/Issue/PR, purpose of agent use, key prompts and corrections, agent changes, verification, errors, and agent corrections. Mark a missing fact as not recorded rather than guessing.
3. Keep the professor's per-feature sections separate. Record tests as performed, failed, blocked, or not run. Distinguish agent-proposed code, student corrections, and teammate review. Use a true “no manual fix was needed” only when the student confirms it.
4. Before writing personal claims, ask the student what they reviewed, what they changed manually, what they now understand, and what still needs study. You may clarify their wording without turning tentative understanding into independent mastery.
5. For a retrospective, organize the student's whole-project examples under what worked, where the agent fell short, and what they can now do themselves. Link genuine weekly entries/PRs. Leave unanswered personal reflections visibly unfilled; do not produce a fictional first-person account.
6. Remove credentials and private capsule content from quotations/screenshots. Preserve concise, meaningful prompts; redact sensitive values and label any paraphrase.
7. Present the completed log diff to the student for accuracy review. Do not treat the log as final until the student confirms it. Log/document changes follow the same PR rule; include the update in the related open task PR or a coherent documentation PR. Return the actual PR URL or publication blocker without manufacturing completion.

The required individual weekly filenames are `agent-log-w1.md`, `agent-log-w2.md`, `agent-log-w3.md`, and `agent-log-w4.md`; planned deadlines are 13, 20, 27 November and 4 December 2026 respectively. The professor's supplement gives no due time or final retrospective date. Follow the actual course submission channel. A filled file must be personally reviewed by its student before submission.

## Ownership, dependencies, and recovery

Read [READINESS.md](../../../docs/agent-tools/READINESS.md). Use the explicitly confirmed User F/G/J owner; ask if absent and continue only read-only preparation until identified. Before implementation or refactoring, inspect current Issues, branches, PRs, and handoffs, complete all eight readiness gates, and run the validator with fresh evidence. A closed Issue, unmerged PR, or isolated spike cannot satisfy a predecessor. If any gate is missing or fails, report BLOCKED and limit work to planning/review. Do not bypass a dependency by narrowing a slice without an approved dependency change.

Keep the existing owner and boundaries on resume. Re-read current discussions and compare actual branch/base/head/diff to the handoff, then rerun readiness. Record approved sharing/transfers before overlapping work. Update the owner's ongoing [agent log](../../../docs/agent-logs/README.md) and [handoff](../../../docs/agent-tools/handoffs/TEMPLATE.md) before interruption or completion, including delegated contributions, unfinished implementation, test gaps, and the first next action. Weekly coursework and personal learning remain separate; never invent human confirmation. An agent review is not a human PR approval.
