# Coding and explanation conventions

Our team must be able to read, review, test, and explain every submitted change. Agents must therefore produce consistent code with easy English comments, reuse existing logic where appropriate, explain refactoring, and submit each logical change through a GitHub pull request.

These conventions implement the team's instructions. Their exact formatting rules and comment examples are team conventions, not claims about wording required by the professor. Read [AGENTS.md](../../AGENTS.md), [WORKFLOW.md](WORKFLOW.md), [COURSE_BASELINE.md](COURSE_BASELINE.md), and the current sources identified in [SOURCE_REGISTER.md](SOURCE_REGISTER.md).

## Consistent style

First inspect the repository's approved style, formatter settings, and nearby code. Preserve those conventions. Where the repository has no established rule, use the defaults below. Resolve a genuine conflict in the task or PR instead of mixing styles or reformatting the whole project.

| Area | Default convention |
| --- | --- |
| Language and UI | Follow the current Kotlin and native Android Views design. Do not switch frameworks during a feature or cleanup task. |
| Indentation | Kotlin/XML and ordinary source files use four spaces; JSON/YAML use two, as set in `.editorconfig`. Do not use tabs. |
| Line length | Prefer 100 columns. Wrap naturally; do not damage URLs, strings, or readability to satisfy a strict count. |
| Classes and interfaces | `UpperCamelCase` names that describe a responsibility. |
| Functions and variables | `lowerCamelCase` names that describe the action or value. Avoid unexplained abbreviations. |
| Constants | `UPPER_SNAKE_CASE` for genuine constants. Do not make a mutable value look like a constant. |
| Resources and packages | Follow the existing Android resource and package naming conventions; use descriptive names. |
| Imports | Use explicit imports. Remove unused imports. |
| Mutability | Prefer `val`. Use `var` only when state must change, and keep ownership clear. |
| Visibility | Use the narrowest visibility that supports the actual design. |
| Null handling | Handle expected absence explicitly. Do not use `!!` to hide uncertainty; explain any justified exceptional use. |
| Control flow | Prefer readable branches and small functions over dense chains, nested callbacks, or clever one-liners. |
| Resources and messages | Follow the existing string-resource and localization pattern. Use English for developer comments. |
| Formatting tools | Use the already configured tools. Do not add or upgrade a formatter, plugin, SDK, or dependency without including that change in the approved task. |

Keep the PR focused. Formatting unrelated files makes the actual behavior change harder to review. Do not create an abstraction solely to reduce a line count or to anticipate a feature that has not been approved.

## English comments for every class and function

Add clear English KDoc to every new or materially changed project-authored class, interface, object, and function. This includes agent-authored code, private helpers, constructors with meaningful behavior, lifecycle overrides, and test functions. Data classes and enums need a comment explaining the represented concept and any non-obvious values. Automatically generated framework files and third-party/vendor files are not edited solely to insert comments.

A simple declaration can have a short comment. A complex one needs enough detail to explain its contract. Write for a student who knows the supplied lectures but has not seen this implementation.

The comment should answer the relevant questions:

- What job does this class or function perform?
- What do its significant inputs and output mean? State units when relevant.
- What state, files, UI, or remote data can it change?
- What can fail, be absent, be denied, or be cancelled, and what happens then?
- What important assumption or boundary must remain true?

Use `@param`, `@return`, `@property`, or `@throws` when they add useful information. Do not add tags that merely repeat a name or claim an exception that the function does not throw. Keep comments consistent with the implementation and update them in the same PR when behavior changes.

For a custom property getter or setter, explain the calculation or side effect in the property's KDoc and add a nearby comment for a non-obvious step. For a nontrivial lambda or callback, put a short English explanation immediately before the block or at its meaningful steps. A trivial click listener does not require an essay, but its enclosing function still requires KDoc. XML and configuration changes need nearby comments when their purpose, permission, lifecycle impact, or unusual setting would otherwise be unclear.

### Small teaching example

The following is an illustrative display-only example. It is not a proposed TimeCapsule component, a product validation rule, or a change to the current application.

```kotlin
/**
 * Prepares screen titles for display without changing the stored source text.
 *
 * This example performs no network, file, or Android lifecycle operation.
 */
class ScreenTitleFormatter {
    /**
     * Returns [rawTitle] without outer whitespace for a display preview.
     *
     * The caller keeps the original value. This method does not validate a capsule.
     */
    fun forPreview(rawTitle: String): String = rawTitle.trim()
}
```

`/** Formats the title. */` would say little beyond the function name. The longer comment is useful because it explains the boundary between display formatting and stored data or validation. Do not introduce a new class merely to copy this teaching example; follow the actual design.

## Finer comments for unfamiliar or complicated code

Use [COURSE_BASELINE.md](COURSE_BASELINE.md) to judge explanation depth. Modern Activity Result launchers are taught; advanced interactions and mechanisms not demonstrated in the supplied lecture set need more explanation.

Comment each meaningful step or decision boundary. A useful step comment explains **why this operation is needed here** and **what could go wrong without it**. Do not narrate every obvious assignment or brace.

| Mechanism | Questions the nearby comments should answer |
| --- | --- |
| Asynchronous work or coroutines, when used | What starts now and finishes later? What owns its lifetime? What does cancellation mean? Which thread or dispatcher matters? |
| Lifecycle and UI updates | Can this screen disappear before the result arrives? Who stops the work or ignores an obsolete result? |
| Session identity | Which UID/session owns the operation? Why must a result from an older session be rejected? |
| Repository calls | Which responsibility belongs here? What does the caller receive on success or failure? |
| Firebase operations | What is authenticated or authorized? What has the server confirmed? What remains uncertain offline? |
| Cache, DataStore, and retained files | What is stored and why? What permits a read? When is it cleared? What must survive for recovery? |
| Retry and reconciliation | How is the same operation recognized? Why is a retry safe? How is an uncertain result resolved? |
| URI and media handling | Is this a reference or retained content? How long is access valid? Who closes or deletes the resource? |
| Time or location rules | What are the units and source? Which approved boundary, freshness, or accuracy rule is applied? What happens if evidence is missing? |

Explain only behavior that the code actually implements. Do not write comments promising security, atomicity, persistence, or exact timing without matching implementation and evidence. Do not invent thresholds, requirement IDs, design IDs, issue numbers, or test results to make an explanation look complete.

Here is a teaching fragment for reading a multiple-permission result. `grantResults` represents the callback's `Map<String, Boolean>`; this is not a complete permission request or a proposed unlock rule.

```kotlin
// Each key is a permission name; its value says whether that permission was granted.
// Read the needed permission separately because the user can grant only some permissions.
val fineLocationGranted =
    grantResults[Manifest.permission.ACCESS_FINE_LOCATION] == true
```

Do not use a short comment as a substitute for understandable structure. If a block needs a long explanation because it combines unrelated responsibilities, consider separating those responsibilities within the approved design.

## Reuse and readable structure

Before adding logic, search the repository for an existing implementation and inspect its callers. Reuse a function only when its meaning and failure behavior match the new use. Similar-looking code can represent different rules.

Keep each function focused on one understandable responsibility. Keep UI event handling, condition evaluation, remote access, and persistence in their approved places. Avoid duplicated business rules, shared mutable state with unclear ownership, deeply nested callback chains, and broad catch blocks that silently report success.

When extracting common logic, keep names and parameters specific enough for a student to understand. Do not build a general framework for one small use case. Remove code made obsolete by the approved change, but do not remove unrelated work or rename large parts of the project for consistency alone.

## Explain refactoring at the changed area

A refactor must leave a short English explanation close to the affected class/function or the newly shared logic. State:

1. **Before:** the actual duplication, excessive nesting, mixed responsibility, or other concrete problem.
2. **After:** the new structure and why it is easier to understand or maintain.
3. **Behavior:** what is preserved, and any intentional behavior change that is separately covered by requirements and verification.

Keep the durable explanation in code. Put the detailed before/after comparison, affected callers, and verification evidence in the PR. A small refactor may need only two or three comment lines. A larger one may need a KDoc paragraph plus a link to a real issue or design record.

### Hypothetical refactor comment

This is an example of wording, not a claim that the current repository contains two duplicated preview screens. Use it only after replacing the situation with the facts observed in the actual change.

```kotlin
// Refactor: Two preview screens previously repeated this display-only formatting.
// Both now call this helper so the same formatting rule is maintained in one place.
// Behavior is preserved: only outer whitespace is removed from the displayed copy.
```

Keep the old implementation in Git history, not in a large commented-out block. Do not accumulate a chronological diary in source files. If a later change makes a refactor note inaccurate, revise it while retaining any explanation that remains useful. Use actual verified issue/PR identifiers when linking history; never insert a plausible-looking placeholder number.

If the change alters observable behavior, describe that plainly. Do not call the whole change behavior-preserving merely because it also reduces duplication.

## Pull requests for each logical change

Every logical change must be submitted through a GitHub PR, including code, refactoring, tests, configuration, and documentation. A logical change is one reviewable task, not every individual line or commit. Several commits and review fixes can belong to the same task's PR. An unrelated task gets a separate branch and PR.

Follow the issue, branch, review, and verification steps in [WORKFLOW.md](WORKFLOW.md). Do not push directly to `main`, merge your own work without the required human review, or silently leave a completed change only on a local branch.

If a PR cannot be opened because access is unavailable, preserve the branch or patch and prepare the exact PR description. Report **PR not created**, the specific blocker, and the remaining action. A prepared description is not a posted PR. Do not invent a URL or mark the task complete.

## Reviewer check

Before requesting review, inspect the diff and answer these questions in the PR where applicable:

- Does every new or materially changed class/function have useful English documentation?
- Are unfamiliar steps explained near the code, including lifecycle, ownership, and failure behavior?
- Do comments describe the implementation accurately instead of making unsupported promises?
- Does the change follow the existing style and approved architecture?
- Was existing logic checked before adding another implementation?
- Does every refactored area explain the real before/after improvement and preserved or changed behavior?
- Do the reported checks distinguish actual results, failures, and checks not run?
- Can a teammate explain the changed data flow and one relevant failure case?
- Is the work linked to its actual issue/design context and submitted as a PR?

For the teaching `ScreenTitleFormatter` example, a reviewer can check that the method affects only the returned display string and does not pretend to perform validation. For a real repository change, the reviewer must examine its actual callers and evidence; the example supplies no test result or approval.

## Reference

The multiple-permission result type is documented by Android Developers in [ActivityResultContracts.RequestMultiplePermissions](https://developer.android.com/reference/kotlin/androidx/activity/result/contract/ActivityResultContracts.RequestMultiplePermissions). Other rules in this file are team conventions or project-source constraints, not new platform requirements. Check [SOURCE_REGISTER.md](SOURCE_REGISTER.md) for the current project evidence.
