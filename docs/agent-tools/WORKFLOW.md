# Agent development workflow

Use this procedure for one focused task. [AGENTS.md](../../AGENTS.md) defines the rules; [CONTRIBUTING.md](../../CONTRIBUTING.md) defines branch and commit names. Each member follows [SETUP.md](SETUP.md) to configure their own Android Studio environment.

## 1. Define the task

Identify the acting student as **User G**, **User F**, or **User J**, then identify the Issue, intended result, scope, work period, dependencies, and verification plan. If the owner was not explicitly supplied for this task, ask **“Who owns this agent task: User G, User F, or User J?”** before making changes or publishing work. Do not guess from an account name or role. Read-only preparation may continue while waiting.

Read the owner's ongoing `docs/agent-logs/<student>/agent-log.md` and start a factual task entry using the [log guide](../agent-logs/README.md). Delegated agents and follow-ups keep the same owner. Link the relevant requirement, design, acceptance criteria, and test records from the [source register](SOURCE_REGISTER.md). Use the [Issue template](../../.github/ISSUE_TEMPLATE/implementation-task.md); documentation tasks may cite a course or team rule where application IDs do not apply.

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

Choose the checks relevant to the change. For account access, locks, retries, media, or lifecycle behavior, include an appropriate failure case from [JOURNEYS.md](JOURNEYS.md) and the detailed test contract. Use authorized test accounts and data.

For a refactor, verify the behavior that should remain unchanged and record any failure already present in the original code. For a documentation-only change, check wording, links, IDs, formatting, and configuration rather than reporting an Android test you did not run.

Record the command or manual procedure, commit, date, environment, expected/actual result, and evidence. Use `PASS`, `FAIL`, `NOT RUN`, or `BLOCKED` accurately. A test plan, mock, or isolated feasibility spike does not prove application integration or acceptance.

## 4. Publish the PR

After the student accepts the diff, inspect the intended files for secrets, unrelated edits, misleading comments, and duplicated logic. Commit, push the task branch, and open or update its PR using the [PR template](../../.github/pull_request_template.md).

Use ordinary GitHub tools through the member's authorized integration. Do not call another coding agent to create the implementation. The PR should explain the outcome, decisions, trace references, unfamiliar code, any refactor, actual checks, and remaining work. Complete and link the confirmed owner's ongoing agent-log entry before publication, then add the actual PR URL to the entry after opening the PR. Link a weekly coursework entry separately when the student has written it.

Use `Closes #<issue-number>` for a completed Issue and `Refs #<issue-number>` for partial work. Keep review corrections in the same PR. An unrelated task gets a separate branch/PR; read-only work needs no empty PR.

Return the actual PR URL. If publication fails, preserve the branch/commits and complete title/body, report **PR not opened**, and state the exact access problem. Keep incomplete verification or unresolved decisions visible, using draft status when appropriate.

## 5. Review and merge

A teammate other than the author reviews the latest diff against the contract and [comment/readability checklist](CODING_AND_COMMENTS.md). The reviewer asks the author to explain the data flow and a relevant failure path.

Apply fixes on the same branch, update comments and logs, rerun affected checks, and obtain review of the changed area. A human merges after the required review and verification. Agents must not self-approve, enable automatic merging, bypass review, or push directly to `main`.

Review can rotate: User F focuses on product meaning and readability, User G on requirements/design links, and User J on runtime and integration risks. All three still implement, review, test, and understand the whole app.

## 6. Record the session

Before every handoff or completion report, the agent updates the confirmed owner's ongoing `agent-log.md`. Record actual changes or findings, their purpose, affected files/records, checks and results, errors, remaining work, and real Issue/PR references. Include each delegated agent's contribution in the same task entry. Record review-only, documentation, failed, and blocked work too; never mark unfinished work complete or invent a successful check.

Keep these entries understandable to teammates: explain the outcome first, use short sentences, and include technical details only when they explain a decision or failure. Preserve earlier entries and use a dated correction if an earlier fact needs fixing. Commit log changes through the task branch and PR.

Each student separately updates their own weekly coursework log with the actual model, purpose, key prompts, agent changes/checks, errors, and manual fixes. The ongoing work log supplies facts but does not replace the weekly submission. Personal reflections, human review, and claims of understanding must come from the student.

[COURSE_COMPLIANCE.md](COURSE_COMPLIANCE.md) lists the individual files and planned deadlines. A committed log is not automatically an individual course submission.

Keep progress claims precise: a published PR can still await review; a merged PR can still lack acceptance evidence. Update Atlas implementation, acceptance, and test statuses separately, only when the relevant evidence supports them. End the session with the result, checks, remaining work, log location, real PR URL or blocker, and next human action.

## References

- Professor supplement: Agent environment, Agent Usage Rules 1, 2, 7, and Agent Deliverables Submission Summary; project sources in [SOURCE_REGISTER.md](SOURCE_REGISTER.md).
- [GitHub PR templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/creating-a-pull-request-template-for-your-repository) and [protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).
