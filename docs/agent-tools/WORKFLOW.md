# Agent development workflow

Use this procedure for one focused task. [AGENTS.md](../../AGENTS.md) defines the rules; [CONTRIBUTING.md](../../CONTRIBUTING.md) defines branch and commit names. Each member follows [SETUP.md](SETUP.md) to configure their own Android Studio environment.

## 1. Define the task

Explicitly identify the acting owner as User F/G/J. If not supplied, ask before changes or publication and continue read-only preparation. Never infer this from the publishing account. Start a task entry in the owner's ongoing [agent log](../agent-logs/README.md). Identify the Issue, intended result, scope, work period, dependencies, and verification plan. Link the relevant requirement, design, acceptance criteria, and test records from [CURRENT_CONTRACT.md](CURRENT_CONTRACT.md). Use the [Issue template](../../.github/ISSUE_TEMPLATE/implementation-task.md); documentation tasks may cite a course or team rule where application IDs do not apply.

Read [IMPLEMENTATION_ORDER.md](IMPLEMENTATION_ORDER.md) and the Issue's `Blocked by` checklist. Verify each prerequisite's implementation PR is merged into the selected base with the relevant recorded checks, and record its PR/commit/evidence. A closed issue or unmerged PR does not establish readiness. Confirm the approved Views contract is present in the base, self-assign a ready issue, and choose a different human reviewer. If blocked, identify the exact gate; reading/review/planning may continue, but implementation waits for that gate or an explicitly recorded maintainer scope change.

Complete all eight gates in [READINESS.md](READINESS.md) and retain the evidence plus validator output in the task handoff. Inspect current Issues, branches, open PRs, and handoffs before claiming work. Record owner, branch, PR, period, MVP/stretch scope, affected files/components, deliverable, dependencies, status, and remaining work in the Issue and handoff. Publish the claim before implementation, then re-read it to detect concurrent claims. This is cooperative coordination, not an atomic lock. If another task overlaps, pause that boundary until the owners explicitly agree to shared work or a transfer; independent tasks may continue.

Check that the repository is `theguardianofthebed/Time-Capsule`. Read existing files and applicable instructions, then create a task branch using the real Issue number. Preserve unrelated local changes. Resolve a missing decision before editing the part that depends on it; continue independent work.

## 2. Implement and explain

Use **Android Studio Agent Mode** and record the actual selected model. Keep the change small and reuse existing components. Follow [CODING_AND_COMMENTS.md](CODING_AND_COMMENTS.md):

- Write easy English comments for each new or meaningfully changed class/function.
- Explain unfamiliar mechanisms at their meaningful steps.
- Follow the shared style and avoid duplicate or tangled logic.
- At a refactored area, explain the problem being removed, the improvement, and whether behavior changes.

The student reviews each proposed diff before accepting it. Ask for an explanation of anything you cannot yet explain. Check that comments match the code. Do not enable automatic acceptance of every generated change.

Ordinary work within the assigned task can continue without repeated permission requests. Report an actual missing decision, destructive action, new cost, deployment, or unavailable permission.

## 3. Check the result

Use the checked-in Gradle wrapper, SDK/toolchain settings, and dependency versions. Confirm the available task and variant in your checkout. Run Windows commands with `.\gradlew.bat`; on macOS/Linux, use `bash ./gradlew` while the wrapper lacks executable permission.

| Purpose | Wrapper task |
|---|---|
| List available tasks | `tasks --all` |
| Build a debug APK | `:app:assembleDebug` |
| Run local JVM tests | `:app:testDebugUnitTest` |
| Run Android lint | `:app:lintDebug` |
| Run tests on a device or emulator | `:app:connectedDebugAndroidTest` |

Each feature must verify its own affected behavior before dependent implementation starts; #23 collects full integrated acceptance later. #9 owns the initial test seams. Do not postpone feature verification until #23.

Choose the checks relevant to the change. For account access, locks, retries, media, or lifecycle behavior, include an appropriate failure case from [JOURNEYS.md](JOURNEYS.md) and the detailed test contract. Use authorized test accounts and data.

For a refactor, verify the behavior that should remain unchanged and record any failure already present in the original code. For a documentation-only change, check wording, links, IDs, formatting, and configuration rather than reporting an Android test you did not run.

Record the command or manual procedure, commit, date, environment, expected/actual result, and evidence. Use `PASS`, `FAIL`, `NOT RUN`, or `BLOCKED` accurately. A test plan, mock, or isolated feasibility spike does not prove application integration or acceptance.

## 4. Publish the PR

After the student accepts the diff, inspect the intended files for secrets, unrelated edits, misleading comments, and duplicated logic. Commit, push the task branch, and open or update its PR using the [PR template](../../.github/pull_request_template.md).

Use ordinary GitHub tools through the member's authorized integration. Do not call another coding agent to create the implementation. The PR should explain the outcome, decisions, trace references, unfamiliar code, any refactor, actual checks, and remaining work. Link the confirmed owner's ongoing entry and the weekly coursework entry if prepared. After opening the PR, add its actual URL to the ongoing log.

Use `Closes #<issue-number>` for a completed Issue and `Refs #<issue-number>` for partial work. Keep review corrections in the same PR. An unrelated task gets a separate branch/PR; read-only work needs no empty PR.

Return the actual PR URL. If publication fails, preserve the branch/commits and complete title/body, report **PR not opened**, and state the exact access problem. Keep incomplete verification or unresolved decisions visible, using draft status when appropriate.

## 5. Review and merge

A teammate other than the author reviews the latest diff against the contract and [comment/readability checklist](CODING_AND_COMMENTS.md). The reviewer asks the author to explain the data flow and a relevant failure path.

Apply fixes on the same branch, update comments and logs, rerun affected checks, and obtain review of the changed area. A human merges after the required review and verification. Agents must not self-approve, enable automatic merging, bypass review, or push directly to `main`.

Review can rotate: User F focuses on product meaning and readability, User G on requirements/design links, and User J on runtime and integration risks. All three still implement, review, test, and understand the whole app.

## 6. Checkpoint and resume

Update the [handoff template](handoffs/TEMPLATE.md) before a session ends, before a context limit, and after an important verified milestone. Store it as `docs/agent-tools/handoffs/issue-N-owner-branch.md` in the task branch. Record the exact branch/base/head, changed and uncommitted files, completed work, remaining implementation, test gaps, failed commands, blockers, decisions, active ownership, PR, and the first next action. Never store credentials or private capsule data. A draft PR may preserve incomplete work, but must clearly identify what remains.

On resume, read the handoff, current Issue/PR discussions, owner log, repository diff, and fresh contract guidance. Compare the recorded head to the actual branch. Re-run the readiness preflight against the new base and current active work; do not trust an old READY label. Preserve uncommitted work, reconcile conflicts without discarding either contributor's changes, and continue only within the recorded boundary. Transfer ownership explicitly, preserving both contributors' factual records.

## 7. Record the session automatically

Before every handoff, update the owner's ongoing `docs/agent-logs/<student>/agent-log.md` using the [log guide](../agent-logs/README.md). Record observed changes/findings, their purpose, checks and limits, errors, remaining work, real Issue/PR references, and each delegated contribution. Do not wait for a separate request or claim unfinished work complete.

Prepare factual input for the student's current weekly log separately. The individual filenames remain `agent-log-w1.md` through `agent-log-w4.md`, with planned deadlines 13, 20, and 27 November and 4 December 2026. Ask for the week if unknown. Human review, manual fixes, understanding, and reflection remain pending until the student supplies them. Show the log diff for accuracy review before submission. A commit does not submit coursework.

End with what changed, evidence, remaining work, handoff/log links, real PR URL or blocker, and next human action. Keep implementation, merge, and acceptance statuses distinct. Atlas updates require separate authorization and evidence; this governance task leaves the live Atlas unchanged.

## References

- Professor supplement: Agent environment, Agent Usage Rules 1, 2, 7, and Agent Deliverables Submission Summary; project sources in [SOURCE_REGISTER.md](SOURCE_REGISTER.md).
- [GitHub PR templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/creating-a-pull-request-template-for-your-repository) and [protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).
