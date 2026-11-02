# Contributing

Read [AGENTS.md](AGENTS.md) before working in this repository. It holds the shared agent rules and repository protections. [WORKFLOW.md](docs/agent-tools/WORKFLOW.md) explains the detailed process; this document keeps the repository's branch, commit, PR, and review conventions in one place.

## 1. General workflow

**Requirement → Acceptance Criteria → Design → GitHub Issue → Implementation → Pull Request → Review → Test → Merge.**

Every coherent feature, fix, refactor, documentation, configuration, or test change belongs on a task branch and in a PR into `main`. Read-only investigation does not need an empty PR. Related review fixes stay in the same PR. Do not push normal work directly to `main`, approve your own PR, force-push shared history, or enable automatic merging.

Inspect the current files and preserve unrelated work before editing. Use the [source register](docs/agent-tools/SOURCE_REGISTER.md) and relevant exact contract records. Do not silently introduce a dependency, cloud service, scope expansion, or architecture change. The current Compose scaffold and approved Views target are explicitly recorded in [REPOSITORY_STATUS.md](docs/agent-tools/REPOSITORY_STATUS.md); resolve that discrepancy through a separate reviewed task before affected implementation.

## 2. Branch rules

Create a separate branch for each task. Keep the existing category-and-issue convention:

```text
feature/<issue-number>-<short-description>
fix/<issue-number>-<short-description>
refactor/<issue-number>-<short-description>
docs/<issue-number>-<short-description>
test/<issue-number>-<short-description>
chore/<issue-number>-<short-description>
```

Replace the placeholders with the actual Issue number and a short task name. The number in the branch name does not include a `#` character. Examples are `feature/12-time-lock` and `fix/18-location-permission`. A descriptive temporary branch such as `docs/update-requirements` is acceptable before an Issue number is available; link the real Issue when publishing the PR.

Preserve teammates' branches and any unrelated local changes. Do not rewrite history merely to make a diff easier to review.

## 3. GitHub Issues

Create or identify the Issue for the coherent task before implementation. Use the [task template](.github/ISSUE_TEMPLATE/implementation-task.md) and an appropriate title category: Feature, Fix, Refactor, Docs, Test, or Chore.

Record the purpose, scope, owner, target period, dependencies, tasks, relevant requirement/design/AC/test IDs, and verification plan. For governance or documentation work, cite the applicable course or team rule and mark application trace IDs not applicable when appropriate. Do not invent an ID or acceptance criterion just to fill a field.

Use **User F**, **User G**, and **User J** in shared document content and PR descriptions. Keep the identity labels consistent with each student's `user-f`, `user-g`, or `user-j` log directory.

## 4. Implementation and comments

Use **Android Studio Agent Mode** for project code and record the actual model selected there. The student reviews every generated change before accepting it; agent self-review does not replace human review. Do not delegate coding to a prohibited coding agent through an IDE or MCP tool.

Follow [CODING_AND_COMMENTS.md](docs/agent-tools/CODING_AND_COMMENTS.md), [COURSE_BASELINE.md](docs/agent-tools/COURSE_BASELINE.md), and the root `.editorconfig`:

- Add easy English documentation to every new or meaningfully changed handwritten class and function, including private helpers and overrides.
- Explain complicated or unfamiliar mechanisms in short comments beside their meaningful steps.
- Reuse shared rules and keep responsibilities focused; do not duplicate validation or create tangled control flow.
- When refactoring, leave a local Before / After / Why explanation and state whether behavior is preserved or changed. Keep the full old implementation in Git history, with the fuller comparison in the PR.
- Update comments when behavior changes. Do not claim a safeguard that the implementation does not provide.

## 5. Commit convention

Use the existing format:

```text
type: short description
```

Supported types are `feat`, `fix`, `refactor`, `docs`, `test`, and `chore`.

```text
feat: implement capsule creation
fix: handle denied location permission
docs: update contribution workflow
```

Keep each commit focused on one clear purpose. Before committing, inspect the staged diff for secrets, real student names, unrelated changes, and generated-file churn. Keep local credentials and actual MCP settings outside committed files.

## 6. Pull requests

After the student accepts the local diff, publish the task branch and open or update its PR against `main`. Use the [PR template](.github/pull_request_template.md). Each PR must explain the concrete problem, resulting behavior, related Issue, trace references where applicable, verification actually performed, and remaining limits. Include screenshots or a recording when UI changes need visual review.

For refactoring, explain Before / After / Why and the behavior checked. For unfamiliar code, identify what teammates should learn to explain. Link the acting student's actual weekly entry; do not fabricate experience or fill another student's personal reflection.

Use `Closes #<issue-number>` only when merging the PR fully resolves that Issue. Otherwise use `Refs #<issue-number>`. Opening or merging a PR does not automatically establish that an Atlas acceptance criterion passed.

Return the actual PR URL. If authentication or permission blocks publication, preserve the branch/commits and complete PR text, report the failing operation, and state **PR not opened**. Do not call unpublished work delivered. Keep a PR in draft when required verification or unresolved decisions prevent review as a completed change.

## 7. Review rules

At least one other team member must review the latest PR diff before merge. Reviewers check the relevant requirements, actual behavior, error and denial paths, account/lifecycle boundaries, clear comments, consistent style, and possible regressions. They also check that no duplicate implementation or misleading status was introduced.

All three students must understand and explain the submitted code during the course interview. Ask the author to explain the data flow and a relevant failure case; a green build alone is not evidence of understanding. Agent review remains advisory and must not be represented as a teammate's human approval.

Apply review fixes on the same branch. Rerun affected checks and re-review the changed area. A human decides when to merge.

## 8. Verification and merge

For application behavior changes, confirm that the project builds in the recorded environment and that relevant functionality and failure paths were tested. Use the checked-in wrapper and existing dependency pins. The actual `:app` module and setup instructions are documented in [ANDROID_SKELETON_SETUP.md](docs/ANDROID_SKELETON_SETUP.md).

For documentation-only changes, verify wording, links, paths, source consistency, identity labels, and any structured configuration. An Android build or runtime result must be recorded as **NOT RUN** if it was not executed; do not manufacture an application test result for a documentation update.

Before a human merges, confirm that conflicts are resolved, applicable checks are complete, another teammate reviewed the latest diff, and no unrelated files or secrets are included. Resolve any live decision or verification gap that the change depends on. After merging, a human may delete the task branch when appropriate.

Each student updates their own weekly log from actual activity and completes their own retrospective from experience. See [COURSE_COMPLIANCE.md](docs/agent-tools/COURSE_COMPLIANCE.md) for the required filenames and planned submission dates.
