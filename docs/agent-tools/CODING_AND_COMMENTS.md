# Coding and comments

Write code that all three students can read, review, test, and explain. Follow [AGENTS.md](../../AGENTS.md), the [workflow](WORKFLOW.md), and the approved [project sources](SOURCE_REGISTER.md). These detailed comment/style rules are team conventions that support the professor's explainability requirement.

## Consistent style

Read nearby code and the repository's formatter settings first. Keep established conventions; use these defaults where no rule exists. Resolve an actual conflict in the task or PR rather than mixing styles.

| Area | Convention |
|---|---|
| Language and UI | Use Kotlin and the approved UI architecture. Read the [repository guide](REPOSITORY_STATUS.md) for the Views/Compose discrepancy before UI work. |
| Indentation | Four spaces for Kotlin/XML and ordinary source; two for JSON/YAML, as set in `.editorconfig`. No tabs. |
| Line length | Prefer 100 columns. Wrap for readability without breaking URLs or strings. |
| Classes and interfaces | Descriptive `UpperCamelCase` names. |
| Functions and variables | Descriptive `lowerCamelCase` names; avoid unexplained abbreviations. |
| Constants | `UPPER_SNAKE_CASE` for genuine constants. |
| Resources and packages | Follow the existing Android naming conventions. |
| Imports | Use explicit imports and remove unused ones. |
| Mutability and visibility | Prefer `val`; keep mutable state ownership clear and visibility as narrow as useful. |
| Null handling | Handle expected absence explicitly. Do not hide uncertainty with `!!`; explain any justified exceptional use. |
| Control flow | Prefer small functions and clear branches over dense chains or deeply nested callbacks. |
| Strings and comments | Follow the project's string-resource/localization pattern. Write developer comments in English. |
| Tools and dependencies | Use configured tools and approved versions. Include any necessary change explicitly in the task. |

Keep formatting changes focused on the task. Do not reformat unrelated files or add abstractions only to reduce line counts or anticipate unapproved features.

## Comment every class and function

Add clear English KDoc to every new or meaningfully changed handwritten class, interface, object, and function, including private helpers, meaningful constructors, lifecycle overrides, and test functions. Explain the concept represented by each data class/enum and any unusual values. Do not edit generated or vendor files solely to add comments.

A simple declaration needs a short comment; a complex one needs enough detail to explain its contract. Cover the relevant points:

- Its job and the meaning of significant inputs/output, including units.
- Any change to state, UI, files, or remote data.
- Failure, missing data, denial, or cancellation behavior.
- Assumptions and boundaries that callers must respect.

Use `@param`, `@return`, `@property`, or `@throws` when useful. Avoid tags that repeat names or describe exceptions the code does not throw. Update comments in the same PR as behavior changes.

For a custom getter/setter, explain its calculation or side effect in the property's KDoc. Explain nontrivial lambdas/callbacks beside the block or its meaningful steps. A simple click listener needs no essay, but its enclosing function still needs KDoc. Add nearby XML/configuration comments for an otherwise unclear permission, lifecycle effect, or unusual setting.

### Declaration example

This display-only example shows comment depth; it is not a component to add to TimeCapsule.

```kotlin
/** Prepares display titles while keeping the original stored text unchanged. */
class ScreenTitleFormatter {
    /**
     * Returns a display copy of [rawTitle] with outer whitespace removed.
     * The caller keeps the original value; this method does not validate a capsule.
     */
    fun forPreview(rawTitle: String): String = rawTitle.trim()
}
```

The useful information is the boundary between display formatting and stored data or validation. A comment that only repeats the function name would miss that boundary.

## Explain unfamiliar or complicated steps

Use [COURSE_BASELINE.md](COURSE_BASELINE.md) to judge explanation depth. Modern activity-result and permission launchers were taught, but complicated interactions still need finer comments.

Explain each meaningful step or decision: **why it is needed here and what failure it prevents**. Do not narrate obvious assignments or braces.

| Mechanism | Questions the comments should answer |
|---|---|
| Asynchronous work/coroutines | What starts now and finishes later? Who owns its lifetime? What does cancellation do? Which thread/dispatcher matters? |
| Lifecycle/UI updates | Can the screen disappear before a result arrives? Who stops the work or ignores an obsolete result? |
| Session identity | Which UID/session owns the operation? Why must an old session's result be rejected? |
| Repository calls | What belongs in this layer? What reaches the caller on success or failure? |
| Firebase | What is authenticated/authorized? What has the server confirmed? What remains uncertain offline? |
| Cache, DataStore, retained files | What is stored, why, and for which UID? What permits a read? When is it cleared? What survives for recovery? |
| Retry/reconciliation | How is the same operation recognized? Why is retry safe? How is an unknown result resolved? |
| URI/media handling | Is this a reference or retained content? How long does access last? Who closes/deletes the resource? |
| Time/location | What units and source are used? Which approved boundary, freshness, or accuracy check applies? What if evidence is missing? |

Comments must describe implemented behavior. Do not promise authorization, atomicity, persistence, or timing that the code does not provide. Use real contract IDs and values; never invent thresholds, Issue numbers, or test results.

This fragment explains a multiple-permission callback's `Map<String, Boolean>`. It is not a complete permission request or unlock rule.

```kotlin
// Each permission has its own result because the user may grant only some of them.
// Treat a missing entry as not granted instead of assuming permission is available.
val fineLocationGranted =
    grantResults[Manifest.permission.ACCESS_FINE_LOCATION] == true
```

If a block needs a long explanation because it mixes unrelated jobs, simplify the structure as well as the comments.

## Reuse and structure

Search for existing logic and read its callers before adding another implementation. Reuse it only when its meaning and failure behavior match the new use.

Give each function one clear responsibility. Keep UI events, condition evaluation, remote access, and persistence in their approved layers. Avoid duplicated business rules, unclear shared mutable state, nested callback chains, and broad catch blocks that silently report success.

When extracting shared logic, use specific names and parameters. Remove code made obsolete by the approved change, while preserving unrelated work. A small task does not need a general framework.

## Explain refactoring beside the code

Leave a short English note near the affected class/function or shared helper. Explain the original duplication, nesting, or mixed responsibility; how the revised structure improves it; and what behavior is preserved or intentionally changed.

Put the fuller explanation, affected callers, and verification in the PR. Small changes may need only two or three comment lines. Larger changes may need a KDoc paragraph and a link to the real Issue/design record.

For example, if two screens really repeat display formatting, an extracted helper could carry this note:

```kotlin
// Refactor note: this shared formatter replaces duplicate formatting in two screens.
// Keeping the rule here prevents the screens from applying different formatting.
// Display behavior is unchanged: only outer whitespace is removed from the copy.
```

Keep the old implementation in Git history, not in a large commented-out block. Keep the note accurate as the code changes; source files should not become a chronological diary. If observable behavior changes, describe and verify that change explicitly.

## PR delivery and review

Submit every focused change through a GitHub PR, including code, tests, configuration, and documentation. Keep related commits and review fixes in that PR; use a separate branch/PR for unrelated work. Follow [WORKFLOW.md](WORKFLOW.md) and leave review and merge to humans.

If access blocks publication, preserve the branch/commits and complete PR description. Report **PR not opened**, the exact blocker, and the action needed. A prepared description is not a published PR.

Review the actual diff and callers:

- Does each new or meaningfully changed class/function have useful English KDoc?
- Are unfamiliar steps explained, including ownership, lifecycle, and failure behavior?
- Do comments match the implementation and approved contract?
- Are style and responsibilities consistent, with suitable logic reused?
- Does each refactored area explain its improvement and any behavior change?
- Are checks reported honestly, including failures and work not verified?
- Can a teammate explain the data flow and a relevant failure case?
- Is the change linked to its real Issue and submitted as a PR?

## Reference

Android Developers documents the multiple-permission result type in [ActivityResultContracts.RequestMultiplePermissions](https://developer.android.com/reference/kotlin/androidx/activity/result/contract/ActivityResultContracts.RequestMultiplePermissions). Use [SOURCE_REGISTER.md](SOURCE_REGISTER.md) for the project-specific contracts.
