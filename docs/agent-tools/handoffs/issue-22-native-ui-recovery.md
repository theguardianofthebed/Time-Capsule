# Issue #22 · Native navigation and recovery

User J owns the MVP correction for S-01–S-08. User G is the designated independent reviewer. Work took place on 28 November 2026 KST on `feature/22-native-ui-recovery`. [Issue #22](https://github.com/theguardianofthebed/Time-Capsule/issues/22)

## Starting point

The selected main commit was `a344980a56eae0078567cbbf421188c88ec49a5a`. The reviewed first implementation was `bb6bb0936ede7996700dca7fd6f21834182d10c3`. This session verified the prerequisites before the correction. No preflight is claimed for the earlier implementation. The [readiness record](../readiness-evidence-issue22.json) identifies the inspected commits, substantive predecessor paths, source PRs, reported verification and open-work boundaries. The manifest and Issue #22 validator both returned no blockers.

| Prerequisite | Merged PR | Evidence reported in that PR |
|---|---|---|
| #10 | #33 · `74aa763` | 47 JVM and 9 instrumentation tests |
| #12 | #36 · `26d78cc` | 69 JVM tests, debug assembly and lint |
| #16 | #42 · `93b7409` | 245 JVM tests, assembly and lint |
| #19 | #45 · `ff24346` | 313 JVM tests and both assemblies. Notification settings recovery belongs here. |
| #21 | #47 · `a344980` | 403 JVM tests, both assemblies and lint |

All five merge commits are ancestors of the selected base. Open PR #50 concerns the separate push reminder foundation. PRs #5 and #24 concern documentation. No other open Issue #22 implementation PR was found at inspection.

## Changes and AC-10 evidence

| Screen or failure | Actual change | Production-path checks |
|---|---|---|
| S-01 / S-02 | A ViewModel claims a submission before launching a coroutine. Registration and repair share that guard. Password fields opt out of saved view state. The host rechecks the live account, reads the directory from the server, and distinguishes missing, unavailable and denied. A missing profile is repaired on the same UID. Unavailable verification allows account-scoped re-view and blocks composition. | `duplicateAuthSubmission`, `duplicateRegisterOrRepairDoesNotWriteProfileTwice`, `registrationFailureRepairsSameAccount`, `signInRotationKeepsOperation`, `missingProfileOpensRepairOnStartup`, `unavailableProfileOffersReadRetry`, `offlineProfileCanOpenRetainedListWithoutCreating`, `unknownAuthErrorHasActionableCopy` |
| Navigation / insets | Newly attached and restored fragments share callback wiring. Protected destinations carry their originating UID and generation. Explicit Back routes preserve the draft, return from detail to the selected tab, and keep frozen operations out of the editor. One root applies system bar, display cutout and IME insets. Notification entry still validates through the existing validator. | `systemBackReturnsFromLockToDraft`, `detailBackRestoresSelectedSentTab`, `rootAppliesInsetsOnce`, existing notification-entry and frozen-operation tests |
| S-03 / S-05 | Unknown recipient availability shows “Verification required.” Sender-copy access is distinct from recipient unlock. The list and lock controls expose selection. Existing code retains Received/Sent provenance, handles partial query failure, and invalidates recipient confirmation on input changes. S-05 still supports manual pin selection. | `unknownReceivedAvailabilityRequiresVerification`, `tabsEmptySentAndUnavailableReceived`, `tabsEmptyReceivedAndUnavailableSent`, `tabsPendingSendDoesNotHideReceivedEmptyView`, draft and location suites |
| S-04 / S-06 | S-04 retains its existing selection/read validation. S-06 renders the original zone, rejects invented target/time defaults and identifies an unconfirmed recipient. A recovered record carries its unchanged-retry capability independently of whether private content is exposed. Definite photo and backend failures retain safe recovery guidance. Confirmed success offers Sent and a new capsule. | `publicationControlsFollowEvidence`, `photoFailurePreventsPublication`, `firstTerminalRejectionCanResolveFailure`, `replayRejectionDoesNotEraseOlderUncertainty`, `committedSelfSendWithLostAckStaysStatusOnlyAcrossFailedRecovery`, `testOpaqueRetryWithAbsentServerStateDoesNotRepublish`, `testAbsentServerReadPreservesUncertaintyForInFlightWrite` |
| S-07 | The renderer checks the live generation and access role before drawing text and again before asynchronous photo display. Old bitmaps are removed before decoding replacements. Offline first verification keeps content hidden. A failed decode keeps the verified badge, readable caption and Retry. | `staleDetailRenderClearsBody`, `delayedPhotoCannotRepaintAfterRevocation`, `offlineFirstVerificationKeepsContentHidden`, `detailScreenDecodeFailureKeepsUnlockAndRetry`, `detailDeniedContentDiffersFromMissingAsset` |
| S-05 / S-07 / S-08 permissions | Android permission-request history survives a new fragment. A suppressed dialog routes to settings. Location accuracy and service availability are shown separately. Notification permission, app notifications and the reminder channel have distinct status and settings paths. No background feature was added. | `notificationDenialHasSettingsRoute`, `detailScreenOpensLocationSettings`, `detailScreenPermissionFailurePersists`, existing location and notification suites |
| Native controls | Header buttons use Back, Account and Refresh artwork and labels. Persistent field labels identify the corresponding editable field. Form controls grow with text, light-surface text uses existing darker palette values and bottom actions have minimum heights. The native screens use real adapters. The historical simulation assets are outside the production flow. | Seven resource regressions in `tests/test_issue22_layout.py`; device walkthrough remains pending |

The previous BOTH-lock fixture never reached its configured backend time denial because it omitted a location adapter. It now supplies a qualifying current fix and exercises the denial and successful retry through the repository. The pre-existing frozen-ID journal, server reconciliation, self-send clearing, independent query provenance, retention, and notification validator remain the sources of authority.

## Regression evidence

Against the reviewed implementation, two sign-in submissions reached the adapter, a missing profile entered S-03, Back lost the draft screen, an unknown list row read “Sealed,” the repeat notification action requested permission again, and a stale render retained the old body. The updated tests call production code. Resource tests failed against the undersized/mislabeled controls, password state declaration, missing labels/minimum heights, fixed-size fields and low-contrast hints.

`S06ConfirmSendLockDisplayTest` passes the final string from the function called by the real review label into each assertion. The six cases are `timeOnlyUsesCreationZone`, `locationOnlyUsesValidTarget`, `bothExplicitlyRequiresTimeAndPlace`, `missingConditions`, `invalidCoordinates` and `unknownZoneUsesExplicitUtc`. A separate checkout restored the exact old formatting behavior at `bb6bb09`, keeping the production extraction and final assertions. The last three failed. The control cases still passed.

Two additional failures were reproduced after the first pass. At `2b02398c9c9ccedbbfacf51441259d77e5d61bc4`, `staleRepairScreenCannotWriteProfile` observed a directory write from an old form following a new session of the same UID. At `c1ff40e802ad26cefdda1f8a632293e24e28561a`, `interruptedRepairCannotStartDirectoryWrite` observed a write after a generation change during the Auth display-name update. The form now passes its original session to the account manager, and the manager rechecks ownership before the next directory write. The sign-out path checks the supplied generation inside the existing account mutex.

At `8ac19eb9a90cde0dfe2f268019cca0dc5100c8bf`, `test_detail_photo_has_a_spoken_label` failed on the visible detail photo. The label is now attached to the authorized photo view. The other resource regression cases are `test_header_actions_have_48dp_targets_and_action_labels`, `test_password_fields_opt_out_of_saved_view_state`, `test_edit_fields_have_persistent_labels`, `test_navigation_items_have_minimum_height`, `test_form_fields_and_submit_buttons_grow_with_text` and `test_input_hints_are_legible_on_form_surface`.

## Verification and remaining work

The final local code commit is `05b9bfa10a055110f038d16f749e59d94a65e5f1`, with tree `954ff443424db68ddb2fd51cfb161e42766cfc03`. The build used Linux x86_64, Temurin 25.0.3, Gradle 9.6.0, Android Gradle Plugin 9.3.0 and Android SDK 37.0.

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 428 passed across 44 suites, zero failed, skipped or errored. Both same-UID repair regressions passed. |
| `:app:assembleDebug` and `:app:assembleDebugAndroidTest` | Both APKs assembled successfully. |
| `:app:lintDebug` | Passed with 0 errors and 171 warnings. |
| `python -m unittest discover -s tests -p 'test_*.py'` | 20 passed, including the 7 resource regressions |
| Firebase Auth/Firestore/Storage emulators with `npm test` | 42 passed. The first attempt timed out through the environment proxy. The successful run connected directly to the local emulators. Rules were unchanged. |
| `python3 tools/check_readiness.py --validate-manifest` and `--issue 22 --evidence docs/agent-tools/readiness-evidence-issue22.json` | Valid manifest and evidence, zero blockers |
| `git diff --check` against the selected base | Passed on the accepted candidate. The published layout of the log supplement preserves the original ongoing log byte-for-byte. Its trailing blank line at line 1000 was already present at `bb6bb09`. Checking the changes since that reviewed branch head passes. |
| Connected instrumentation and manual device walkthrough | NOT RUN. No connected device was present. |

User G's independent review is pending. The device walkthrough still needs to check TalkBack announcements and focus, 48 dp hit areas in use, large text, keyboard reachability, and system location and notification dialogs on supported Android versions. Actual GPS and notification delivery also need a device. Issue #23 remains a separate test and evidence activity. Personal review and learning statements are for User J to supply.

No remaining functional blocker was identified by the source review and automated checks. Full AC-10 device acceptance and independent review remain open.

[User J’s dated correction to the ongoing log](../../agent-logs/user-j/issue-22-correction.md)

[Draft PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51) contains the published implementation at `3bfa2b888f365b77990cd5a2293546f48b75227e`. The published app tree `c51d3715d087dce1ebb5c69d29621dce92139c2c` and test tree `18099503d04c55fcc58e1bb09d9873294b637f6e` match the verified local candidate. The publication follow-up links this PR from the coursework records.
