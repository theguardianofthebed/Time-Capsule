# Contributing

Use a task branch and pull request for every feature, fix, refactor, test, documentation, or configuration change. Read [AGENTS.md](AGENTS.md) for the shared rules and [WORKFLOW.md](docs/agent-tools/WORKFLOW.md) for the full task procedure.

## Plan the task

Follow this sequence:

**Requirement → Acceptance Criteria → Design → GitHub Issue → Implementation → PR → Review → Test → Merge.**

Create or identify the Issue before implementation. Use the [task template](.github/ISSUE_TEMPLATE/implementation-task.md) to record the goal, scope, owner, work period, dependencies, relevant requirement/design/test references, and verification plan. Documentation tasks can cite a course or team rule instead of an application requirement.

Read the relevant [project sources](docs/agent-tools/SOURCE_REGISTER.md) and existing files before editing. Preserve teammates' work. Agree on any missing product or architecture decision before implementing the affected part. The [repository guide](docs/agent-tools/REPOSITORY_STATUS.md) explains the application structure and the Views/Compose discrepancy.

## Branches and commits

Use one branch per task:

```text
feature/<issue-number>-<short-description>
fix/<issue-number>-<short-description>
refactor/<issue-number>-<short-description>
docs/<issue-number>-<short-description>
test/<issue-number>-<short-description>
chore/<issue-number>-<short-description>
```

Examples: `feature/12-time-lock`, `fix/18-location-permission`. Do not include a literal `#` in the branch name. A descriptive temporary name such as `docs/update-requirements` is acceptable until an Issue exists.

Use `type: short description` for commits. Supported types are `feat`, `fix`, `refactor`, `docs`, `test`, and `chore`:

```text
feat: implement capsule creation
fix: handle denied location permission
docs: update contribution workflow
```

Keep each commit focused. Check the staged diff for unrelated changes, generated files, secrets, and real student names. Use **User F**, **User G**, and **User J** in shared documents and PR descriptions.

Do not push normal work directly to `main`, force-push shared history, or overwrite teammates' changes.

## Code and comments

Use **Android Studio Agent Mode** for project code and record the selected model. A student must review every generated change before accepting it. Do not delegate coding to another coding agent through an IDE or MCP tool.

Follow [.editorconfig](.editorconfig), [CODING_AND_COMMENTS.md](docs/agent-tools/CODING_AND_COMMENTS.md), and the [lecture baseline](docs/agent-tools/COURSE_BASELINE.md):

- Add easy English KDoc to every new or meaningfully changed handwritten class/function, including private helpers and overrides.
- Explain unfamiliar or complicated steps beside the code.
- Reuse suitable existing logic and keep each function focused.
- At a refactored area, explain the original problem, how the code is simpler, and whether behavior changed. Keep old implementation copies in Git history.
- Update comments whenever behavior changes.

## Publish and review

Once the student accepts the local diff, commit the intended files, publish the task branch, and open or update its PR into `main`. Use the [PR template](.github/pull_request_template.md). Explain the problem, resulting behavior, important decisions, checks performed, and remaining work. Include UI evidence when useful and link the student's actual weekly entry.

Use `Closes #<issue-number>` only when the PR fully resolves that Issue; otherwise use `Refs #<issue-number>`. Related review fixes stay in the same PR. Read-only investigation needs no empty PR.

At least one teammate other than the author must review the latest diff. Check the requirements, behavior, error/denial paths, comments, style, duplication, and possible regressions. The author should be able to explain the data flow and a failure case. Agent review does not count as human approval.

If publication is blocked, keep the branch/commits and complete PR description. Report **PR not opened** and the exact access problem. Return the real PR URL once publication succeeds.

## Verify and merge

Run checks that can reveal problems in the changed behavior. Use the checked-in wrapper and dependency versions; see [Android setup](docs/ANDROID_SKELETON_SETUP.md). For document changes, check wording, links, paths, sources, and structured configuration. Report only checks actually run and identify any outstanding checks.

Apply fixes on the same branch, rerun affected checks, and obtain review of the updated diff. Keep the PR in draft when a required decision or check is incomplete. A human merges after review, verification, and conflict resolution. Agents must not self-approve, merge, or enable automatic merging.

Each student records their own work in the weekly log and writes their own retrospective. See [course requirements](docs/agent-tools/COURSE_COMPLIANCE.md) for filenames and deadlines.
