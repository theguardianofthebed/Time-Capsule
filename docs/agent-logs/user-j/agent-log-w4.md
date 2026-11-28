# Week 4 · User J

## Issue #22 · Native navigation and recovery

- Project development environment specification: Android Studio Agent Mode.
- Work period: 28 November 2026 (KST). Scope: MVP. Human reviewer: User G.
- Contract: AC-10, DD-01, DD-02, NFR-02, NFR-03, NFR-05, T-38 and T-45.
- Branch: `feature/22-native-ui-recovery`. [Issue #22](https://github.com/theguardianofthebed/Time-Capsule/issues/22).

The first implementation at `bb6bb09` added authentication progress views, live regions, lock wording, and clearing on capsule ID changes. Its log reported 406 passing JVM tests. Its three new tests repeated the formatter and did not verify the screen text. The initial report also attributed existing list and recipient-confirmation behavior to that commit.

The correction binds authentication progress to each form’s ViewModel, checks the current account and profile before entering protected screens, and offers repair, verification retry, or offline re-view according to the result. System Back and visible Back use the same route and preserve the editable draft or original frozen publication. The review screen presents the actual lock evidence, sends confirmed publications to the Sent tab, and offers only the recovery actions supported by the journal.

Several failures were reproduced through the Android activity. A duplicate sign-in reached the adapter twice. A missing profile opened the list. A stale emission for the same capsule retained the old session’s caption. Reinstalling the previous production lock formatter made three assertions fail. The final test names, environment, results, and outstanding device checks are recorded in the [technical handoff](../../agent-tools/handoffs/issue-22-native-ui-recovery.md).

User J’s personal review, explanations, manual fixes, and course submission remain pending student input. User G’s independent review is pending.

See the [dated correction to the ongoing log](issue-22-correction.md).

Published for independent review in [Draft PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51).

## Issue #22 · Third Implementation: Targeted Functional Corrections (28 November 2026)

- Work period: 28 November 2026 (KST). Scope: MVP. Human reviewer: User G.
- Target PR: [PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51). Branch: `feature/22-native-ui-recovery`.
- Corrected registration failure boundary in `DefaultAuthAdapter.signUp()` when Auth creation succeeds but profile update fails, keeping session active for same-account profile repair.
- Applied originating session protection (`SessionSnapshot`) across `S08AccountSettingsFragment.kt` and `S08AccountSettingsViewModel.kt` (`repairProfile`, `signOut`, `checkProfile`, `btnCopyUid`).
- Enforced authoritative server profile reads in `DefaultFirestoreAdapter.getRecipientProfile()` (`doc.metadata.hasPendingWrites() || doc.metadata.isFromCache` rejection, required UID and nonblank display name validation).
- Updated `MainActivity.resolveAccountScreen()` with a 5s profile-check timeout and preserved offline viewing of previously authorized retained content.
- Cleared stale location labels on recovered operations in `CapsuleDraftViewModel.applyRecovered()` and represented missing recovery records truthfully without false `PublicationPhase.FAILED` states.
- Refined `S07CapsuleDetailViewModel.mapUnlockFailureStatus()` to ensure TIME-only verification failures never generate location-settings actions.
- Re-evaluated session validity in `MainActivity.replaceFragment()`'s `runOnCommit` callback to prevent asynchronous session transition leakage.
- Automated verification: 436 JVM unit tests passed (`./gradlew :app:testDebugUnitTest`), debug/test APKs assembled, lint clean (0 errors), readiness valid (`py tools/check_readiness.py --validate-manifest` -> `MANIFEST_VALID`).

## Issue #22 · Fourth Review: Remaining Functional Corrections (28 November 2026)

- Work period: 28 November 2026 (KST). Scope: MVP. Human reviewer: User G.
- Target PR: [PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51). Branch: `feature/22-native-ui-recovery`.
- Replaced/supplemented regression tests in `AccountSessionManagerTest`, `CapsuleDraftViewModelTest`, and `S07CapsuleDetailViewModelTest` to test actual production adapter/ViewModel boundaries with Red->Green verification.
- Enforced complete profile verification in `DefaultFirestoreAdapter.getRecipientProfile()` (checks `hasPendingWrites()`/`isFromCache` before document existence, requires matching UID and nonblank display name).
- Added in-flight submission guard `isSignOutInFlight` in `S08AccountSettingsViewModel` and session validation in S-08 LiveData observers.
- Automated verification: 442 JVM unit tests passed (`./gradlew :app:testDebugUnitTest`), debug/test APKs assembled, lint clean (0 errors), readiness preflight valid (`py tools/check_readiness.py --issue 22 --evidence docs/agent-tools/readiness-evidence-issue22.json` -> `PREFLIGHT_RECORD_VALID`).

## Issue #22 · Fifth Review: Outstanding Corrections (29 November 2026)

- Work period: 29 November 2026 (KST). Scope: MVP. Human reviewer: User G.
- Target PR: [PR #51](https://github.com/theguardianofthebed/Time-Capsule/pull/51). Branch: `feature/22-native-ui-recovery`.
- Committed previously pending changes to `MainActivity.kt` and `S07CapsuleDetailViewModel.kt`.
- Strengthened `MainActivity.replaceFragment().runOnCommit` with `belongsToCurrentSession(fragment)` check and `accountCheck?.isActive` guard.
- Refined `S07CapsuleDetailViewModel.mapUnlockFailureStatus()` so specific location failures (`"location service"`, `"permission"`, `"null fix"`) produce correct location guidance while account/denial errors map to `ACCESS_DENIED`.
- Strengthened `CapsuleDraftViewModel.applyRecovered()` location-label logic to require matching coordinates before preserving a custom location label.
- Added tests exercising real `DefaultAuthAdapter` and `DefaultFirestoreAdapter` production adapter validation in `AuthAndRecipientTest.kt`.
- Automated verification: 448 JVM unit tests passed (`./gradlew :app:testDebugUnitTest`), debug/test APKs assembled, lint clean (0 errors), readiness preflight valid (`py tools/check_readiness.py --issue 22 --evidence docs/agent-tools/readiness-evidence-issue22.json` -> `PREFLIGHT_RECORD_VALID`).
