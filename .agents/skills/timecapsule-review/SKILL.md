---
name: timecapsule-review
description: Review a TimeCapsule diff or PR for contract correctness, readable English comments, coding consistency, duplication, refactor explanations, verification evidence, and student explainability. Use for pre-acceptance review, peer review, or Q and A preparation; keep review read only unless fixes are requested.
---

# Review a TimeCapsule change

Read [AGENTS.md](../../../AGENTS.md), [WORKFLOW.md](../../../docs/agent-tools/WORKFLOW.md), [CODING_AND_COMMENTS.md](../../../docs/agent-tools/CODING_AND_COMMENTS.md), and the relevant approved contract. Use [COURSE_BASELINE.md](../../../docs/agent-tools/COURSE_BASELINE.md) to judge explanation depth.

1. Resolve the actual branch/PR, base and head revision, scope, Issue, source revision, and intended behavior. Read the diff and the surrounding code needed to understand each change. Do not claim to have reviewed an unseen file or inaccessible PR.
2. Check requirements, ownership, time/location rules, session changes, uncertain outcomes, permissions, and lifecycle behavior where affected. Distinguish a confirmed defect from a question or untested risk. Do not treat source finalization as implementation evidence.
3. Check every new/materially changed handwritten class/function for useful English documentation. Check complicated or unfamiliar steps for local explanations. Flag comments that merely repeat names or promise safeguards the implementation lacks.
4. Check style consistency and actual reuse. Look for duplicated business rules, overly broad classes, hidden side effects, and difficult control flow. For refactors, check that the local note explains the previous problem and the improvement, then inspect all affected callers and the PR's behavior comparison.
5. Compare claimed tests against actual commands, reports, environment, and manual observations. State what was not run. Confirm that applicable failure and denial cases were considered, and that no fake pass or unrelated spike is being used as acceptance evidence.
6. Check for accidental secrets, generated/private files, scope expansion, unapproved dependency changes, and an honest individual log entry. Verify that a real PR exists for a changed task; read-only review itself needs no new PR.
7. Report findings in severity order with a concrete file/function or diff location, observed problem, effect, and suggested correction. If no actionable defect is found, say that and name the review limits. Do not invent findings to fill a checklist.
8. Ask a few focused explanation questions: purpose and data flow; one unfamiliar mechanism; one failure or account-switch path; how verification supports the result. The human supplies answers. Do not mark understanding as passed without their participation.

Keep this review read-only unless fixes are requested. If authorized to fix, use the same task branch/PR when appropriate and repeat affected verification. The agent's assessment is advisory: a teammate performs the actual human review, approval, and acceptance. Never post a human approval, self-approve the author's work, or merge on the student's behalf.

Output: reviewed revisions/scope, findings, evidence and limitations, explanation questions, and the next human action. Do not equate “no issue found” with a guaranteed correct program.
