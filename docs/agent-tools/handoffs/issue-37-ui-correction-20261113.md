# Task handoff: Issue #37 S-01, S-02, and S-08 UI Design Correction

- **Confirmed owner:** User J, explicitly confirmed as implementation owner for Issue #37.
- **Scope / period / different human reviewer:** MVP; 13 November 2026; User G (designated teammate reviewer).
- **Issue / PR:** Issue #37 (https://github.com/theguardianofthebed/Time-Capsule/issues/37); PR #38 (https://github.com/theguardianofthebed/Time-Capsule/pull/38).
- **Branch / base / head:** `feature/37-ui-design-correction` on base `main` (commit `26d78cc`). Head commit will be updated upon final commit.
- **State:** AWAITING_REVIEW (implemented, verified with unit tests, lint, and emulator walkthrough, ready for teammate review).
- **Boundary:** S-01 (Sign In), S-02 (Register & Profile Repair), and S-08 (Account & Settings) native XML layouts, resource drawables, strings, colors, and Fragment bindings.
- **Predecessors:** PR #33 (`74aa763` for Issue #10 foundation) and PR #36 (`26d78cc` for Issue #12 S-04..S-06 flow) merged into `main`.
- **Contract:** FR-01, FR-10; ARC-01, ARC-06; DD-01, DD-03, DD-07, DD-19, DD-24, DD-26; AC-01, AC-10.
- **Overlap inspection:** Inspected `main` and active PRs (#36 merged). No active competing claims on S-01, S-02, or S-08.
- **Readiness Limitation:** `tools/check_readiness.py` does not contain Issue #37 in `dependencies.json` (as #37 is a UI correction task for #10 and #12). Extending the manifest requires a separate maintainer decision. All 8 underlying gates (approved contract, predecessors merged, base ancestry, verification, no competing work, owner/boundary) were checked manually against repository state.

## Completed work

- Redesigned S-01 Sign In, S-02 Register, and S-08 Account Settings native XML layouts to match Stitch wireframes (`TimeCapsule Android UI Design` project `15276828764661611137`).
- Defined design tokens in `colors.xml` (`#865B4B` terracotta, `#FAF9F5` warm cream, `#FFFDF9` card surface, `#E8E3DA` border) and added shape/vector drawables.
- Corrected input validation messages to be field-specific (`err_email_password_required`, `err_required_field`, `err_name_required`) rather than generic session errors.
- Removed inaccurate prototype/feature text (S-02 footer notice updated; S-08 location description updated to reflect lock verification).
- Corrected S-08 permission status handling using `NotificationManagerCompat.areNotificationsEnabled()` for notifications and showing neutral `Not configured` badge for un-declared location permissions.
- Resolved S-08 profile-editing race conditions:
  - Guarded `checkProfile()` callback so late responses do not overwrite active text edits when `etDisplayName` has focus or user edits.
  - Tracked `lastSubmittedName` in `S08AccountSettingsViewModel` and updated UI header with confirmed submitted name on update success.
  - Disabled `btnUpdateProfile` while update operation is in flight to prevent duplicate concurrent submissions.
- Added unit tests in `S08AccountSettingsViewModelTest.kt` (74 total unit tests across project, all passing).
- Corrected accessibility touch targets (min 48 × 48 dp for buttons and text links) and added dynamic password show/hide content descriptions (`cd_show_password` / `cd_hide_password`).
- Added KDoc documentation across modified ViewModel and Fragment files.
- Ran `:app:assembleDebug`, `:app:testDebugUnitTest` (74/74 PASS), and `:app:lintDebug` (0 errors).

## Remaining work

- Teammate PR review by User G on PR #38.
- Final human merge into `main`.

## Workspace and recovery

- All changes committed and pushed to `origin/feature/37-ui-design-correction`.
- Working tree clean.

## Next action

1. User G reviews PR #38 code and verification diff.
2. User J confirms manual testing and learning reflections in `agent-log-w2.md`.
3. Human maintainer merges PR #38 into `main`.
