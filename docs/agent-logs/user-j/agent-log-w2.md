# Agent Log – Week 2 (MM/DD – MM/DD)

- Name / Student ID:
- Model(s) used:
- My changes to the team's Agent Tools this week:

The planned submission date is **20 November 2026**; follow any later professor announcement. Add one feature/task section for each piece of work. Describe your own contribution and use real prompts, results, and examples. If you did not use an agent this week, say so honestly.

Follow [the shared workflow](../../agent-tools/WORKFLOW.md) and [AGENTS.md](../../../AGENTS.md). Remove credentials and private data from any quoted material.

## Task: Issue #12 S-04, S-05, and S-06 Drafting & Review Flow (Stitch Design Integration)

- **Task and purpose:** Implemented GitHub Issue #12 delivering the S-04 → S-05 → S-06 content creation, recipient confirmation, lock selection, and review flow based on the pre-made designs in the Stitch project `TimeCapsule Android UI Design`.
- **Session date(s):** 11 November 2026.
- **Reporting placement:** Moved into Week 2 at User J's request; the original 11 November 2026 session dates and recorded evidence are unchanged.
- **Issue and PR:** Issue #12 (https://github.com/theguardianofthebed/Time-Capsule/issues/12); implementation branch `feature/12-draft-review-flow`.
- **Requirement/design references:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07; ARC-01; DD-01, DD-03, DD-07, DD-12, DD-13, DD-19, DD-26, DD-27; AC-01, AC-02, AC-03, AC-05, AC-06, AC-11.
- **Agent environment:** Android Studio Agent Mode.
- **Model used:** Gemini Flash 3.8 (Android Studio Agent Mode).

**Goal**

Needed assistance in translating the pre-made Stitch UI designs (`TimeCapsule Android UI Design` project `15276828764661611137`) for S-04, S-05, and S-06 into native Android Views XML + ViewBinding, managing Activity-scoped draft state via `CapsuleDraftViewModel`, removing obsolete global skeleton headers from `MainActivity`, enforcing exact UID recipient directory lookup with explicit user confirmation, invalidating stale lookup callbacks on UID edits or session switches, providing truthful user-facing subtext for downstream boundaries (Issues #13, #14, #16, #21), and writing unit tests for draft validation and lookup token invalidation.

**Requests and prompts**

| # | Request summary | Actual prompt (key part) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Stitch Design Retrieval & Implementation | "TimeCapsule — Issue #12 Implementation (Stitch Design Integration) ... Deliverable: S-04 → S-05 → S-06 content creation, recipient confirmation, lock configuration and review flow ..." | Successfully retrieved Stitch HTML/screens, created `CapsuleDraftViewModel`, S-04/S-05/S-06 Fragments/XML, and passed 67 unit tests. |
| 2 | Review Findings Fixes & UI Cleanup | "TimeCapsule — Issue #12 Final UI Cleanup, Verification & Agent Log Update ... Remove obsolete global placeholder header ... Improve user-facing unavailable-function messages ..." | Removed global skeleton header from `activity_main.xml`, updated user-facing subtext messages, updated `MainActivityTest.kt`, and passed 69 unit tests and lint. |

### What the agent changed and verified

- **Changes:**
  - `app/src/main/res/layout/activity_main.xml` & `MainActivity.kt`: Removed obsolete top global skeleton header (`tvMainTitle` / `tvMainSubtitle`). Fragment container now occupies full application content area. Preserved fail-closed Firebase initialization error view (`tvInitError`). Set authenticated launch default to S-04 (`navigateToContentDraft()`) and added `btnCreateCapsule` on S-08 Account Settings.
  - `app/src/main/java/com/example/timecapsule/domain/model/Capsule.kt`: Extended `CapsuleDraft` with optional `title`, `recipientDisplayName`, `isRecipientConfirmed`, and `locationName`.
  - `app/src/main/java/com/example/timecapsule/ui/draft/CapsuleDraftViewModel.kt`: Implemented Activity-scoped ViewModel holding shared draft state across S-04, S-05, S-06. Increments `activeLookupToken++` on UID text edit, recipient mode change, or session switch to reject stale async lookup responses. Blocks progression if content is empty, recipient is unconfirmed, or Location lock is selected without Issue #16 integration.
  - `app/src/main/java/com/example/timecapsule/ui/s04/S04ContentDraftFragment.kt` & `fragment_s04_content_draft.xml`: Implemented S-04 Content Draft screen (optional title, pre-unlock disclosure note, multiline body, 5 MiB photo boundary validation, truthful photo notice).
  - `app/src/main/java/com/example/timecapsule/ui/s05/S05RecipientLockFragment.kt` & `fragment_s05_recipient_lock.xml`: Implemented S-05 Recipient & Lock Selection screen (Future Me vs Someone Else exact UID lookup, explicit recipient confirmation, confirmation reset on UID text edit, LockMode TIME/LOCATION/BOTH, future date/time picker, disabled CTA until recipient confirmed).
  - `app/src/main/java/com/example/timecapsule/ui/s06/S06ConfirmSendFragment.kt` & `fragment_s06_confirm_send.xml`: Implemented S-06 Review screen (displays actual message text, title, recipient name & UID, unlock time, pre-unlock title disclosure note, back navigation to edit draft, and user-facing notice that sending is unavailable).
  - `app/src/test/java/com/example/timecapsule/ui/draft/CapsuleDraftViewModelTest.kt`: Added 17 unit tests covering text/photo validation, title-only rejection, recipient lookup & explicit confirmation, stale lookup callback rejection on UID change, session switch state reset, and lock mode validation.
  - `app/src/androidTest/java/com/example/timecapsule/MainActivityTest.kt`: Updated instrumented test to verify `fragmentContainer` hosting without obsolete `tvMainTitle`.
- **Checks and results:**
  - `py tools/check_readiness.py --issue 12 --evidence docs/agent-tools/readiness-evidence-issue12.json`: PASS (`PREFLIGHT_RECORD_VALID`)
  - `:app:assembleDebug`: PASS (`BUILD SUCCESSFUL`)
  - `:app:testDebugUnitTest`: PASS (69 unit tests passed across project, 0 failed)
  - `:app:lintDebug`: PASS (0 errors)
  - Emulator runtime walkthrough: S-06 review screen launched successfully on emulator; identified and corrected top global header and developer-oriented text.

### Errors and manual fixes

- The agent updated `CapsuleDraftViewModelTest.kt` when user-facing send notice text was changed from `"Issue #14"` to `"Sending isn't available yet"`. All 69 unit tests passed cleanly.

### My review and understanding

- **What I reviewed:**
  - I reviewed the S-04 → S-05 → S-06 drafting and review flow on the Android emulator and in the codebase.
  - I verified that authenticated users land directly on S-04, can enter a message and optional title, look up a recipient UID, explicitly confirm recipient identity, select a future unlock date/time, and review the actual message text on S-06.
  - I confirmed that the obsolete top global header was removed and that S-06 uses product-friendly language rather than internal GitHub issue numbers.
- **What I can explain:**
  - I understand that an Activity-scoped ViewModel (`CapsuleDraftViewModel` using `requireActivity()`) allows multiple Fragments to share a single draft state without data loss when moving Back/Next.
  - I can explain why exact recipient directory lookup requires an explicit user confirmation step, and how lookup token generation (`activeLookupToken++`) prevents stale asynchronous responses from accidentally confirming an altered UID input.
  - I understand why changing `activity_main.xml` required updating `MainActivityTest.kt` to check `R.id.fragmentContainer` instead of the removed `R.id.tvMainTitle`.
  - I can explain why unavailable downstream operations (photo upload #13, real publication #14, map geofencing #16, lists #21) must display truthful user-facing notices rather than simulating false success.
- **Feedback and next step:**
  - The implementation passes all 69 unit tests, debug build, and lint checks.
  - I will perform the complete manual testing walkthrough using the step-by-step checklist before requesting teammate PR review and human merge.

