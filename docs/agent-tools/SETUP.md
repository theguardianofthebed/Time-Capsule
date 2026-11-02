# Set up the shared TimeCapsule agent workflow

**Prepared:** 3 November 2026, Asia/Seoul. **Team labels:** User F, User G, User J.  
**Repository:** [theguardianofthebed/Time-Capsule](https://github.com/theguardianofthebed/Time-Capsule).  
**Scope:** shared instructions, documentation, templates, and local agent setup. The professor permits Android Studio Agent Mode for project code.

This guide is fitted to the existing repository. Its Git root is also the Android/Gradle project root: `settings.gradle.kts`, `gradlew`, and the `app/` directory are already there. The documents are proposed through a documentation PR; a human reviews and merges it. After merge, each member pulls the shared files and performs the local setup below. Existing application directories keep their locations.

## 1. Review and adopt the documentation PR

Review the complete diff with another teammate. Start with [AGENTS.md](../../AGENTS.md), [CONTRIBUTING.md](../../CONTRIBUTING.md), [DESIGN.md](../../DESIGN.md), and [REPOSITORY_STATUS.md](REPOSITORY_STATUS.md). The repository-status document explains the existing structure, each document refactor, and the difference between the provisional Compose scaffold and the approved Views target.

The main application source, Gradle files, `prototype/`, and `spikes/` are outside this documentation change. The root `.gitignore` retains the existing Android/build exclusions and adds the shared-file and sensitive-file rules. `GITIGNORE_ADDITIONS.txt` remains a reference for future merges; do not paste it into `.gitignore` a second time if the PR already includes it.

Use [SOURCE_REGISTER.md](SOURCE_REGISTER.md) and the full [revision-38 reference](reference/timecapsule-contract-r38.json) for contract review. Read exact affected node clauses and their actual relationships; the summary alone can omit details. A newer approved contract must be reconciled through the normal reviewed process. Do not treat an older unmerged handoff as a newer decision merely because it is in another branch.

After peer review and applicable checks, a human merges the documentation PR into `main`. Each member then pulls the reviewed version. Do not overwrite teammates' working trees with the ZIP. If applying the ZIP manually instead, copy the **contents** of its `repo/` folder to a task branch at the Git root, compare all existing files, and publish a PR; do not add a nested `repo/` directory to the project.

## 2. Where the documents live

| Location from repository root | Purpose |
|---|---|
| `AGENTS.md` | Shared mandatory Rules, including the four requested comment/style/refactor/PR requirements and member aliases |
| `CONTRIBUTING.md` | Existing branch/commit conventions, review, and human merge process |
| `DESIGN.md` | Screen flow, navigation, confirmed visual identity, and labeled Material 3 implementation proposals |
| `.editorconfig` | Consistent editor formatting; no formatter or CI installation is implied |
| `.aiexclude` | Sensitive local files excluded from Android Studio agent context |
| `.agents/skills/` | Four shared procedures: implement, refactor, review, and record learning |
| `.github/` | PR template and scoped Issue template, kept at Git root for GitHub discovery |
| `docs/README.md` | Navigation index for the shared documents |
| `docs/ANDROID_SKELETON_SETUP.md` | How to open and verify the Android project that already exists |
| `docs/agent-tools/` | Workflow, comments, lecture baseline, course compliance, source/reference, repository status, journeys, MCP setup, and this guide |
| `docs/agent-logs/user-f/`, `user-g/`, `user-j/` | Four individual weekly templates per student |
| `docs/retrospectives/user-f/`, `user-g/`, `user-j/` | One individual `retrospective.md` per student |

Use **User F**, **User G**, and **User J** in shared text, comments, and PR descriptions. Use `user-f`, `user-g`, and `user-j` in directory names. Do not add a table mapping these labels to real names. Each student still submits their own coursework using the professor's required filenames.

No existing application directory needs to move: the root already supports Android Studio, GitHub, and the professor's required file paths. New subdirectories organize agent tools and individual coursework within the existing `docs/` folder.

## 3. Verify instruction and skill discovery in Android Studio

Open the **existing repository root** in Android Studio and select Agent Mode. Record the actual selectable model used for each session. Do not use another coding-agent IDE or delegate project code to an external coding agent.

The shared Rules are inside root `AGENTS.md`, which the professor explicitly allows. Keep any extra IDE Rules or same-directory `GEMINI.md` consistent with it. Check the agent's context panel rather than assuming a file was loaded. If the IDE does not discover a shared instruction or skill automatically, attach that exact file for the session and record the local discovery issue.

The four procedures are:

| Task | Shared skill |
|---|---|
| Approved feature or fix | [timecapsule-implement](../../.agents/skills/timecapsule-implement/SKILL.md) |
| Remove duplication or simplify code | [timecapsule-refactor](../../.agents/skills/timecapsule-refactor/SKILL.md) |
| Assist contract and readability review | [timecapsule-review](../../.agents/skills/timecapsule-review/SKILL.md) |
| Organize a student's actual session record | [timecapsule-record-learning](../../.agents/skills/timecapsule-record-learning/SKILL.md) |

Select the appropriate skill with the IDE's `@` skill/context picker when supported. These files guide the agent; they do not grant repository access or replace a student's review.

Start with this read-only prompt:

```text
Read root AGENTS.md, CONTRIBUTING.md, docs/agent-tools/REPOSITORY_STATUS.md,
docs/agent-tools/SOURCE_REGISTER.md, docs/agent-tools/CURRENT_CONTRACT.md,
its linked full contract reference, and the shared workflow/comment guides.
Do not edit application code yet.
Confirm the instructions and team skills available to this session.
Summarize the English class/function comment rule, step comments for unfamiliar
code, consistent style, Before/After/Why refactor notes, and PR requirement.
Identify the actual Git remote, branch, Gradle module, and the Views/Compose
discrepancy. Use User F, User G, and User J in shared materials.
Report verified facts and remaining local setup without printing secrets.
```

Every student reviews every generated change before accepting it. Ask for an explanation of anything you cannot explain yourself; a build pass does not establish understanding.

## 4. Configure each member's local GitHub connection

Follow [MCP_SETUP.md](MCP_SETUP.md). The shared [mcp.redacted.json](mcp.redacted.json) is a token-free example. Actual credentials belong in the member's Android Studio settings directory and must never be written into the repository, shared log, or prompt. Copying the example does not create a working connection.

Use ordinary repository, Issue, branch, and PR tools. Do not invoke an operation that delegates implementation to Copilot or another prohibited coding agent. The GitHub MCP example also does not grant access to the separate Project Atlas service; the full frozen contract is available locally for all teammates.

Verify that the member's authorized connection can read this repository and publish a task branch/PR. If publication fails during a real task, preserve the work and complete PR text, report the exact blocker, and label the result `PR not opened`. Do not claim delivery until a real PR URL exists.

The repository owner can separately configure branch rules requiring a PR and one other teammate's approval if supported by the account's plan. Only require status checks that actually exist. This documentation change does not change repository settings or purchase a plan. Until effective rules are verified, the team must follow the review process manually.

## 5. Follow the small task workflow

1. Choose a real Issue and read the relevant approved requirement, design, AC, and test records. Record owner, target period, dependencies, and scope.
2. Create a branch using [CONTRIBUTING.md](../../CONTRIBUTING.md), such as `docs/12-agent-setup`; replace `12` with the actual Issue number.
3. Use the matching skill inside Android Studio Agent Mode and record the actual model.
4. Review each diff. Require clear English KDoc for every new or materially changed handwritten class/function and finer comments at meaningful unfamiliar steps.
5. For a refactor, require a local Before/After/Why explanation and honest behavior/verification comparison in the PR. Keep old implementation copies in Git history.
6. Run checks relevant to the actual change. The existing wrapper/module instructions are in [ANDROID_SKELETON_SETUP.md](../ANDROID_SKELETON_SETUP.md); do not invent build results or upgrade dependencies to avoid a failure.
7. Publish the task branch and open/update its PR. Related review fixes remain in the same PR. Another teammate reviews, and a human merges after applicable verification.
8. Update the acting student's own weekly log from the actual session. Read-only discussion needs no empty PR.

Example task prompt:

```text
Use the timecapsule-implement skill for Issue [actual number].
Acting student: [User F, User G, or User J]. Target period: [actual period].
Read the current contract and repository status before editing.
Follow AGENTS.md for comments, style, reuse, refactor explanations, and PRs.
Show each diff for my review before acceptance. Explain unfamiliar mechanisms.
Run relevant checks, record actual results and limits, help update my own log,
and open or update the ordinary GitHub PR. Return its real URL.
Leave approval and merge to a human.
```

Before any further UI implementation, resolve the existing Compose scaffold versus approved Views target through a separate reviewed application task. The documentation PR itself does not perform that migration or change the approved target.

## 6. Keep individual records and submit them separately

All 12 weekly logs and 3 retrospectives are blank templates. Each student writes their own actual contribution, prompts, model, agent changes/checks, errors, and manual fixes. Shared work can appear in multiple logs only with each person's distinct, truthful contribution. The agent may organize student notes but must not invent personal learning or competence.

| Required individual filename | Planned due date in the professor's supplement |
|---|---|
| `agent-log-w1.md` | 13 November 2026 |
| `agent-log-w2.md` | 20 November 2026 |
| `agent-log-w3.md` | 27 November 2026 |
| `agent-log-w4.md` | 4 December 2026 |
| `retrospective.md` | Final individual deliverable; no date stated in the supplement |

No due time or submission platform is specified there. Check the course's actual submission instructions; committing a log alone does not prove an individual submission. See [COURSE_COMPLIANCE.md](COURSE_COMPLIANCE.md) for the professor-versus-team rule mapping.

## 7. Ownership and maintenance

User J coordinates repository integration and local setup; User G coordinates traceability and review; User F coordinates product/design readability. All three still implement, review, test, and understand the complete application. Each person owns their own coursework records and machine settings.

Update source snapshots, design decisions, instructions, and templates through normal PRs. Preserve later completed logs and stronger existing exclusions. Review overlapping older branches before merging so they do not reintroduce obsolete guidance or real names.

## References

- [Course requirements and source register](SOURCE_REGISTER.md), including the supplied professor supplement and lecture filenames.
- [Android Studio agent files](https://developer.android.com/studio/gemini/agent-files), [skills](https://developer.android.com/studio/gemini/skills), [MCP configuration](https://developer.android.com/studio/gemini/add-mcp-server), and [context exclusions](https://developer.android.com/studio/gemini/aiexclude).
- [GitHub PR templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/creating-a-pull-request-template-for-your-repository) and [protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).
