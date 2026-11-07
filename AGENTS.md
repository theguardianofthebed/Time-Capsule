# TimeCapsule agent instructions

These are the shared rules for the TimeCapsule Android application. They do not govern the Project Atlas website or its MCP gateway. Read this file before planning, editing, reviewing, or publishing a project change.

## 1. Repository scope and safety

- Work only in `theguardianofthebed/Time-Capsule` unless the human explicitly authorizes another repository.
- Read existing files before editing. Preserve unrelated work and do not take over another member's unfinished task unless the team reassigns or shares it.
- Do not bypass permissions, branch rules, tests, security checks, or validation. Do not perform destructive or irreversible work without clear human authorization.
- Never expose, log, commit, or transmit secrets, credentials, private keys, private capsule content, or sensitive personal data.
- Do not silently add requirements, architecture, dependencies, paid resources, accounts, or stretch features. Report a real unresolved decision and continue independent work.

The Git root is also the Gradle root, with the `:app` module under `app/`. Keep `app/`, `gradle/`, `prototype/`, and `spikes/` in place. Project layout and implementation boundaries belong in [CURRENT_CONTRACT.md](docs/agent-tools/CURRENT_CONTRACT.md).

## 2. Permitted agent environment and human responsibility

- Use **Android Studio Agent Mode** for project code. Any model selectable there may be used; record its exact displayed name in the acting student's log.
- Do not delegate project coding to Antigravity, Cursor, Claude Code, Copilot, or another coding agent.
- The acting student reviews every generated change before accepting it. A successful build or agent self-review does not replace human review.
- Every student remains responsible for submitted code and must be able to explain it during Q&A.
- This file is the team's shared Rules source. Additional IDE Rules must agree with it.
- Use **User F**, **User G**, and **User J** in shared repository materials until the team approves a different naming convention. Use `user-f`, `user-g`, and `user-j` for student directories.

## 3. Read the right guide for the task

Keep this root file short. Detailed instructions live in the linked guides below.

| Task | Required core guide |
|---|---|
| Find current scope, architecture, dependencies, UI decisions, and trace IDs | [CURRENT_CONTRACT.md](docs/agent-tools/CURRENT_CONTRACT.md) |
| Write, review, or refactor code | [CODING_AND_COMMENTS.md](docs/agent-tools/CODING_AND_COMMENTS.md) |
| Create an Issue, branch, PR, test handoff, weekly log, or retrospective | [WORKFLOW.md](docs/agent-tools/WORKFLOW.md) |

Before implementation, read the exact affected `FR`/`NFR`, `ARC`/`DD`, `AC`, and `T` records. Official course requirements and the latest explicit team decisions take priority over historical proposals. Do not invent an identifier, approval, threshold, API, or test result.

## 4. Code students can explain

- Keep the existing team KDoc rule. Add plain English KDoc to every new or meaningfully changed handwritten class and function, including private helpers and overrides, as defined in [CODING_AND_COMMENTS.md](docs/agent-tools/CODING_AND_COMMENTS.md).
- Add short English comments at meaningful steps in complicated or unfamiliar code. Explain why the step exists, not merely what an obvious line says.
- Follow `.editorconfig`, reuse suitable existing logic, keep responsibilities focused, and do not introduce duplicate business rules.
- For a refactor, explain the previous problem, the improvement, and whether behavior is preserved or intentionally changed.
- Keep comments accurate when behavior changes.

## 5. Workflow, verification, and publication

Follow [WORKFLOW.md](docs/agent-tools/WORKFLOW.md):

**Requirement → Acceptance Criteria → Design → GitHub Issue → Implementation → PR → Review → Test → Merge.**

- Use a task branch and preserve teammates' work. Do not push directly to `main`, force-push shared history, self-approve, or merge automatically.
- Run checks relevant to the actual change and record their commands, environment, results, and limits honestly. Use `NOT RUN`, `BLOCKED`, or `FAILED` when applicable.
- A teammate reviews the change, and a human performs the merge. Opening a PR does not prove implementation, review, testing, or acceptance.
- Return the actual PR URL. If publication is blocked, preserve the work and report `PR not opened` with the exact blocker.

## 6. Automatic individual log update

After every implementation, review, refactoring, documentation, or configuration task, automatically prepare an update to the acting student's current weekly log at `docs/agent-logs/<student>/agent-log-wN.md`.

- Record objective facts observed in the session without waiting for a separate request: date, model, task and purpose, key prompts and corrections, changed files or behavior, checks and results, errors, agent corrections, Issue, and PR.
- Before recording personal claims, ask the student what they reviewed, what they changed manually, what they can explain, and what remains unclear. Never invent manual fixes, participation, understanding, or learning.
- If the current student or week is unknown, ask before choosing a file. Do not write into another student's record.
- Show the completed log diff to the student for review. The student must confirm that it accurately describes the session before submission.
- A committed log is not automatically an individual course submission. Follow the professor's actual submission channel and deadline.

Final retrospectives follow the same evidence rule but are not generated after every task. Each student completes their own `retrospective.md` from real weekly entries and project experience.

## 7. Credentials and final handoff

- Each member configures MCP privately in Android Studio. Do not copy a real MCP configuration, endpoint credential, or token into project Markdown. Commit only the professor-required token-free example at `docs/agent-tools/mcp.redacted.json`.
- Respect `.aiexclude` and `.gitignore`, and inspect the intended diff for secrets before publication.
- End with: what changed and why, affected files and trace IDs, verification and limits, unfamiliar mechanisms to explain, the log update, PR URL or blocker, and the next human action.

User F leads product and presentation, User G leads requirements, design, and review, and User J leads feasibility, implementation, and integration. All three still implement, review, test, and learn the whole application.
