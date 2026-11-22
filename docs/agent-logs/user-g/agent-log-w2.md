# Agent Log – Week 2 (11/14 – 11/20)

- Name / Student ID:김건우/22101988
- Model(s) used:gemini 3.5 flash
- My changes to the team's Agent Tools this week: No shared policy or Skill files were changed for Issue #13.

## Original Photo Selection, Retention and Upload (Issue #13)

- **Task and purpose:** Implement original-photo selection, validation, retention, recovery and authenticated upload for Issue #13.
- **Session date(s):** 17, 18 and 20 November 2026.
- **Issue and PR:** https://github.com/theguardianofthebed/Time-Capsule/issues/13; PR opened: https://github.com/theguardianofthebed/Time-Capsule/pull/39. Branch: `feature/13-original-photo`.
- **Requirement/design references:** DD-04, DD-25, FR-04, NFR-02, NFR-05; bounded AC-02, AC-10, AC-14 portions.
- **Agent environment:** android studio agent
- **Model used:** gemini 3.5 flash

**Goal**

Enable a capsule to contain one valid original JPEG or PNG photo of up to 5 MiB, with private retention, restart recovery and authenticated upload. Preserve the exact original bytes for retries, confirm upload before publication, and retain recovery data while the outcome is uncertain. Actual capsule publication belongs to Issue #14.

**Requests and prompts**

| # | Request summary | Prompt summary (paraphrased) | Why it worked (or didn't) |
|---|-----------------|--------------------------|---------------------------|
| 1 | Define photo contracts | Review Issue #13 and the existing code. Define supported formats, byte limits and the data needed for retention/upload. | Clarified JPEG/PNG, the inclusive 5 MiB limit and the boundary with Issue #14. Commit: `cf3deaa`. |
| 2 | Validate and retain originals | Copy the original into private storage while checking actual size/format, calculating SHA-256 and cleaning failed copies. | Avoided trusting provider metadata. Eight focused unit tests passed; Android decoder execution remained pending. Commit: `3d2d9b0`. |
| 3 | Connect selection and preview | Connect S-04 photo selection, removal and preview to the validated private-copy path. Handle stale asynchronous results. | Added picker/preview wiring and stale-result protection. Real picker behavior needs device testing. Commit: `118aedd`. |
| 4 | Restore drafts after restart | Persist original-photo and editable draft information so recovery does not depend on the original provider URI. | Added UID-scoped atomic journals and size/hash verification. Recipient/lock choices require re-entry after restart. Commit: `36a018b`. |
| 5 | Implement upload and retry | Upload the frozen original with authenticated Storage access, reuse the same bytes for retry and preserve files during uncertain outcomes. | Added stable upload handoff and outcome-dependent cleanup without claiming SENT. Actual Firebase integration remained unverified. Commit: `0e8eb02`. |
| 6 | Verify the implementation | Run unit tests, Debug builds and lint, and identify any remaining integration checks. | All 103 JVM tests passed; app/test APKs compiled; lint had no errors. Real Android-device and Firebase integration verification was subsequently performed; detailed scenario results remain to be recorded. Commit: `1121e81`. |

### What the agent changed and verified

- **Changes:** Photo contracts; bounded copying/decoding; private restart journals; S-04 picker/preview; frozen upload/retry/cleanup service; empty-upload and prepublication-owner-cleanup rules; focused tests.
- **Checks and results:** `:app:testDebugUnitTest` — 103 passed, zero failures/errors/skips; `:app:assembleDebug` — PASS; `:app:assembleDebugAndroidTest` — PASS (compilation only); `:app:lintDebug` — warnings but no errors; `git diff --check` — PASS; `node --check tests/capsule.rules.test.js` — PASS (syntax only).
- **Device and integration checks:** Real Android-device and Firebase integration verification was performed after implementation.
- **Remaining checks and records:** Record the specific device/Firebase scenarios and outcomes. Three Android decoder tests and four additional Storage security-rule cases are prepared; their execution results have not yet been recorded. Confirm coverage of URI revocation, process restart, upload/download, security rules and the full media flow, and run any uncovered scenarios.

### Errors and manual fixes

The initial Gradle invocation could not access its cache lock in the restricted execution environment. Retrying with authorized cache access succeeded.

I reviewed the photo constraints, original retention and retry behavior, and the implementation and verification results.

### My review and understanding

- **What I reviewed:** I reviewed the Issue #13 scope and six implementation stages, including photo format/size limits, original retention and retry behavior, and the reported unit-test/build results. I also personally performed real Android-device and Firebase integration verification after implementation.
- **What I can explain:** One JPEG or PNG of up to 5 MiB is supported. A private copy allows retry without continued access to the original provider URI. SHA-256 checks whether the photo bytes have changed. Photo upload is separate from capsule publication, which belongs to Issue #14.
- **Feedback and next step:** Opened PR #39 and requested teammate code review and independent verification of the remaining device/Firebase scenarios in its description. Independent reviewer assignment and detailed verification records remain pending. Actual capsule publication belongs to Issue #14.
