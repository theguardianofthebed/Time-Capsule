# Agent setup

Each member uses Android Studio Agent Mode for project code, with the same repository instructions and their own local tool settings.

## 1. Open the shared project

Open the repository root in Android Studio using [Android setup](../ANDROID_SKELETON_SETUP.md). Read [AGENTS.md](../../AGENTS.md) first, then the core guide that matches the task.

The agent workflow has four core documents:

| Path | Purpose |
|---|---|
| `AGENTS.md` | Team rules for every agent task |
| `docs/agent-tools/CURRENT_CONTRACT.md` | Current scope, behavior, architecture, dependencies, and trace IDs |
| `docs/agent-tools/WORKFLOW.md` | Issue, branch, verification, PR, automatic weekly log, and retrospective process |
| `docs/agent-tools/CODING_AND_COMMENTS.md` | KDoc, explanatory comments, code style, reuse, and refactoring rules |

Skills, templates, the full Atlas reference, and course/source notes support these four documents. They are not extra mandatory entry documents for every task. `.aiexclude`, `.gitignore`, and `.editorconfig` remain active project configuration.

The [documentation index](../README.md) links all guides and student records.

## 2. Check that the agent can read the instructions

Select Agent Mode and record the actual model used. Check its context panel for `AGENTS.md`. Keep any additional IDE Rules or `GEMINI.md` consistent with the shared rules.

Choose the relevant skill with the IDE's `@` picker when available. If a file is not discovered, attach it explicitly for that session.

| Task | Skill |
|---|---|
| Implement a feature or fix | [timecapsule-implement](../../.agents/skills/timecapsule-implement/SKILL.md) |
| Simplify code or remove duplication | [timecapsule-refactor](../../.agents/skills/timecapsule-refactor/SKILL.md) |
| Help review a change | [timecapsule-review](../../.agents/skills/timecapsule-review/SKILL.md) |
| Organize your own work record | [timecapsule-record-learning](../../.agents/skills/timecapsule-record-learning/SKILL.md) |

Use this first prompt to check the setup:

```text
Read AGENTS.md and the relevant core guide: CURRENT_CONTRACT.md,
WORKFLOW.md, or CODING_AND_COMMENTS.md.
Read only the relevant full contract records for this task.
Do not edit code yet.
Tell me which shared instructions and skills are available.
Summarize the rules for English comments, unfamiliar code, consistent style,
refactoring explanations, and PRs.
Identify the repository, branch, app module, and any unresolved design conflict.
Use User F, User G, and User J in shared material. Do not print secrets.
```

Review every generated change before accepting it. Ask the agent to explain anything you cannot explain yourself.

## 3. Set up PR access

Give the agent an authorized GitHub connection so it can publish each task branch and PR. [MCP_SETUP.md](MCP_SETUP.md) describes the MCP option. Keep actual MCP credentials in your Android Studio settings directory. The committed [mcp.redacted.json](mcp.redacted.json) contains placeholders only.

Use ordinary GitHub tools for repository, Issue, branch, and PR work. Do not use a tool that delegates project coding to another agent. GitHub access also does not grant access to Project Atlas; the full contract reference is available in the repository.

Verify your own connection before relying on it. If PR publication fails, preserve the branch and prepared PR text, record the blocker, and finish publication when access is restored. A real PR URL confirms that the PR was opened.

## 4. Work on one task at a time

1. Choose the Issue and read its relevant requirement, design, acceptance, and test records.
2. Create a task branch using [CONTRIBUTING.md](../../CONTRIBUTING.md).
3. Use the matching skill and review the proposed changes. Require clear class/function comments, extra explanation for unfamiliar steps, consistent style, and reusable code.
4. When refactoring, explain the original problem and how the revised code improves it. Keep that note near the code and include the verification in the PR.
5. Run checks that match the change, then publish or update its PR. Related fixes stay in that PR.
6. The agent automatically prepares the current weekly log update. Answer its questions about your own review, manual fixes, understanding, and remaining gaps, then check the completed log diff. Another teammate reviews the work before a human merges it.

Read [WORKFLOW.md](WORKFLOW.md) for the full process. For a coding task, this prompt is a useful starting point:

```text
Use the timecapsule-implement skill for Issue [actual number].
Acting student: [User F, User G, or User J].
Follow AGENTS.md and the relevant contract records.
Show each diff for my review and explain unfamiliar code.
Run the relevant checks and record actual results and limits.
Help update my own weekly log and open or update the GitHub PR.
Return its URL. Leave approval and merge to a human.
```

## 5. Keep your own coursework records

Use your `user-f`, `user-g`, or `user-j` folder under `docs/agent-logs/` and `docs/retrospectives/`. Record your actual prompts, model, contribution, checks, errors, manual fixes, and learning. Each student writes their own experience, including when several people worked on the same feature.

Use **User F**, **User G**, and **User J** in shared documents, comments, and PR descriptions. Do not include a real-name mapping. See [COURSE_COMPLIANCE.md](COURSE_COMPLIANCE.md) for required filenames, planned deadlines, and submission guidance.
