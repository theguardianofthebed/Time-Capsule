# TimeCapsule agent development workflow

**Policy owner:** User G for traceability/review, User J for integration. **Applies:** every coherent project change. **Status:** workflow prepared and fitted to the inspected repository on 3 November 2026. Each member must still verify their own Android Studio, GitHub authentication, and instruction discovery. See [SETUP.md](SETUP.md) and [REPOSITORY_STATUS.md](REPOSITORY_STATUS.md).

Use this alongside [AGENTS.md](../../AGENTS.md), [CODING_AND_COMMENTS.md](CODING_AND_COMMENTS.md), and [CONTRIBUTING.md](../../CONTRIBUTING.md). These files are integrated by the documentation PR; preserve their agreement when changing the process. The professor requires Android Studio Agent Mode, review before acceptance, and individual learning records. PR delivery, one teammate's review, and the comment/style policy are additional team rules.

## 1. Start a small traceable task

The human names the feature or Issue and the acting student. The agent reads the current source register, relevant contract records, applicable instructions, and existing implementation. It identifies the real repository and default branch; never confuse TimeCapsule with the Project Atlas website or gateway.

Before editing, record the intended outcome, scope, linked requirement/AC/design IDs, owner, target period, dependencies, and a small verification plan. Use the Issue template. A governance or documentation task may say that application requirement IDs are not applicable and cite the course or team rule instead. Do not invent an AC just to fill a template.

Create a task branch using the repository's existing convention. Use `feature/<issue-number>-<short-name>`, `fix/<issue-number>-<short-name>`, `refactor/<issue-number>-<short-name>`, or `docs/<issue-number>-<short-name>`, following the existing concrete examples in `CONTRIBUTING.md`. The issue marker in older placeholder notation was not a required literal `#` in the branch name. Use the actual Issue number. Keep unrelated work on separate branches, and preserve any pre-existing uncommitted changes.

## 2. Implement and explain in Android Studio

Use a model selectable inside Android Studio Agent Mode and record its exact name. Reuse existing components and keep the change focused. Add the required English declaration comments and step comments as the implementation is written. Leave the local Before / After / Why refactor note when restructuring code.

The student reviews each proposed diff before accepting it in Android Studio. Ask the agent to explain anything the student cannot yet explain. Correct wrong assumptions and check that comments describe actual behavior. Do not enable a workflow that silently accepts all generated changes for this assignment.

The agent may continue ordinary implementation and verification within the assigned scope. Escalate an actual missing decision, destructive action, new cost, production deployment, or unavailable permission; do not repeatedly ask whether to perform already authorized routine work.

## 3. Verify the change with appropriate evidence

Read the actual Gradle settings and task list. Preserve the checked-in wrapper, Java/toolchain settings, SDK versions, and dependency pins. Do not upgrade a dependency or switch a UI framework to make an unrelated task easier.

The inspected checkout contains the `:app` module. Confirm the task/variant in your own checkout before use. Windows PowerShell uses `.\gradlew.bat`. The inspected Unix wrapper is stored with mode `100644`, so use `bash ./gradlew` unless a separately reviewed change has made it executable. Keep the wrapper version and recorded Gradle toolchain settings; Java source compatibility is not the same as the JDK used to run Gradle.

| Purpose | Example wrapper task | What it establishes |
|---|---|---|
| Discover available tasks | `tasks --all` | Which commands this checkout actually exposes |
| Compile a debug APK | `:app:assembleDebug` | Build succeeds for that variant |
| Run local JVM tests | `:app:testDebugUnitTest` | Only the tests actually executed |
| Run Android lint | `:app:lintDebug` | Static checks for that variant |
| Run device tests when needed and available | `:app:connectedDebugAndroidTest` | Instrumented tests on the recorded device/emulator |

Run the relevant commands, not every command indiscriminately. For an application behavior change, choose tests that could reveal its specific failure. For account access, locks, retries, media, or lifecycle handling, include the relevant adverse case from [JOURNEYS.md](JOURNEYS.md) and the current test contract. Use authorized test accounts/data and record the environment; do not probe production accounts as a substitute.

For a behavior-preserving refactor, compare the same meaningful behavior before and after. If the pre-change test already fails, record that separately. For a comments-only or documentation-only change, inspect wording, links, identifiers, and formatting; do not claim an Android build or runtime test was run when it was not needed.

Each result should identify the command or manual procedure, date, commit, environment, expected behavior, actual result, and evidence location. Use `PASS`, `FAIL`, `NOT RUN`, or `BLOCKED` accurately. A test source file or test plan is not a test execution. A local mock or E1 spike does not prove production Firebase integration.

## 4. Open or update the PR

After the student accepts the local diff, stage only the intended files. Inspect the staged diff for accidental secrets, unrelated edits, obsolete comments, and duplicated logic. Commit the coherent change, push the task branch, and open a PR against the verified default branch. Use [.github/pull_request_template.md](../../.github/pull_request_template.md).

The normal agent assignment includes publishing its change through a PR when the member's authorized integration permits it. Use GitHub MCP or an authenticated GitHub workflow from Android Studio. Do not call another coding agent to create the implementation.

Fill in the concrete problem, resulting behavior, trace links, main design decisions, unfamiliar mechanisms, refactor comparison, actual verification, and remaining limits. Link the acting student's weekly entry. Request review from another team member. If checks or required verification remain incomplete, keep the PR in draft and describe the gap.

Use `Closes #<actual-issue-number>` only if merging the PR fully resolves that Issue. Use `Refs #<actual-issue-number>` for partial work. A closing keyword links GitHub work; it is not evidence that an Atlas requirement or acceptance criterion passed.

**One PR per coherent change** does not mean one PR per line, function, or commit. Review fixes and additional verification update the same PR. If an unrelated task appears, record it separately. A read-only review needs no empty PR.

If remote access fails, preserve the local branch/commits and a complete title/body. Report the failing operation and the action needed from the member. State `PR not opened` and mark delivery blocked. Do not invent a URL or mark the change delivered. After access is restored, publish the prepared work and return the actual URL.

## 5. Review, test, and merge

At least one teammate other than the author reviews the actual latest diff. Review against the source contract and the practical checklist in CODING_AND_COMMENTS, not just the fact that the build is green. The reviewer asks the author to explain the data flow and one relevant failure path.

Apply review fixes on the same branch, update comments/logs, and rerun checks affected by those fixes. Re-review the changed area. A human merges only when the PR's review and applicable verification are complete. Agents do not self-approve, enable automatic merging, bypass required review, or push directly to the default branch.

For this three-person project, review can rotate. User F checks product meaning and readability, User G checks contracts and traceability, and User J checks runtime/integration risks; these are review strengths, not exclusive permissions or exemptions from implementation.

## 6. Keep progress and learning records truthful

Update the acting student's own weekly log after the session while details are fresh. Record the model, purpose, key prompts, actual agent changes/checks, errors, and human fixes. Another student records their own review or testing participation in their own log. Do not duplicate a team log and imply that all three students did the same work.

Changes to implementation, requirements, design, acceptance, and tests have distinct statuses. A merged PR is not automatically a passed acceptance criterion. Update Atlas progress only with the appropriate evidence and normal authorized workflow; do not cascade completion to neighboring records.

The weekly filenames and planned submission dates come from the professor's supplement:

| Week | Individual filename | Planned due date |
|---|---|---|
| 1 | `agent-log-w1.md` | 13 November 2026 |
| 2 | `agent-log-w2.md` | 20 November 2026 |
| 3 | `agent-log-w3.md` | 27 November 2026 |
| 4 | `agent-log-w4.md` | 4 December 2026 |

No due time or submission channel is stated in that supplement. Follow the course's actual submission instructions. The whole-project `retrospective.md` is separate; each student supplies their own reflection. Templates are not completed submissions.

## 7. Completion vocabulary

| Status | Meaning |
|---|---|
| Prepared locally | Files exist; remote PR may still be missing |
| PR opened | A real URL exists; review or verification may remain |
| Ready for human merge | Required review and applicable checks are complete |
| Merged | The actual PR was merged into the intended branch |
| Requirement accepted | The relevant acceptance evidence supports that requirement |

End the agent session with what changed, why, tests actually run, limits, what the student should understand, log update, PR URL or blocker, and next human action. Never collapse these statuses into an unsupported “all done.”

## References

- Professor supplement: Agent environment; Agent Usage Rules 1, 2, 7; Agent Deliverables Submission Summary.
- Uploaded project context: `03_TEAM_ROLES.md` and `06_CHATGPT_WORKING_RULES.md`; current sources are registered in [SOURCE_REGISTER.md](SOURCE_REGISTER.md).
- [GitHub PR template placement](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/creating-a-pull-request-template-for-your-repository).
- [GitHub protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).
