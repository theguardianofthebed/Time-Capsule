# Contributing

## 1. General Workflow

Meaningful development work should normally follow this workflow:

Requirement
→ Acceptance Criteria
→ Design
→ GitHub Issue
→ Implementation
→ Pull Request
→ Review
→ Test
→ Merge

Do not push normal development work directly to `main`.

## 2. Branch Rules

Create a separate branch for each task.

Recommended branch name formats:

```text
feature/#issue-task-name
fix/#issue-task-name
refactor/#issue-task-name
docs/#issue-task-name
test/#issue-task-name
chore/#issue-task-name
```

Replace `#issue` with the issue number. If no GitHub Issue number exists yet, a descriptive task name may be used temporarily.

Examples:

```text
feature/12-time-lock
fix/18-location-permission
docs/update-requirements
```

## 3. GitHub Issues

Create a GitHub Issue before meaningful feature implementation.

Use an appropriate category in the issue title: Feature, Fix, Refactor, Docs, Test, or Chore.

Where applicable, include:

- Purpose
- Tasks
- Assignee
- Related requirement
- Acceptance criteria

## 4. Commit Convention

Use this commit message format:

```text
type: short description
```

Supported types:

- `feat`
- `fix`
- `refactor`
- `docs`
- `test`
- `chore`

Examples:

```text
feat: implement capsule creation
fix: handle denied location permission
docs: update contribution workflow
```

Whenever possible, keep each commit focused on one clear purpose.

## 5. Pull Requests

Merge normal work into `main` through a Pull Request.

Each PR should include:

- Summary of changes
- Related issue
- Testing performed
- Screenshots or a recording if the UI changes

Whenever possible, keep each PR focused on one major feature or purpose.

## 6. Review Rules

At least one other team member must review the PR before merge.

Reviewers should check:

- Whether the implementation matches the requirements
- Whether the expected behavior works
- Whether crash and error cases are handled
- Whether permission handling is correct
- Whether the code is easy to read and understand
- Whether existing features have regressions

This course includes a final team interview. All team members must be able to understand and explain the code that is merged.

## 7. Before Merge

Before merging, confirm:

- Merge conflicts are resolved
- The project builds successfully
- The implemented functionality has been tested
- Required review is complete
- No unrelated files have been changed

After merging, delete the task branch if appropriate.
