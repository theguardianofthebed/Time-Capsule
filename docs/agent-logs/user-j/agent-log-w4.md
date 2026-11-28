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
