# Verification journeys

Use these procedures to verify an implemented feature and record its actual result. They group the canonical T-01–T-45 tests and AC-01–AC-14 acceptance criteria from the [full contract reference](reference/timecapsule-contract-r38.json); journey numbers are navigation aids. Read the affected T/AC records and [implementation contract](CURRENT_CONTRACT.md) before a run.

## 1 How to run and record a journey

For a feature PR, choose the affected journeys and relevant regression cases. Do not execute every unrelated case for a small comment edit. Before WIP or release, run the implemented integrated flow and the meaningful cross-account/permission/network regressions. A planned case that needs missing code or fixtures remains **BLOCKED** or **NOT RUN**, with the exact reason.

Use:

- A dedicated local Firebase Auth/Firestore/Storage emulator setup for rules and controlled network/publication failures.
- Synthetic accounts **A** (sender), **B** (recipient), **C** (unrelated), and a signed-out session. Keep passwords/tokens out of logs.
- A Google Play-enabled API 36 emulator for target integration and a physical Android phone for location/lifecycle evidence. Use an API 26–32 device/emulator for the document-selector photo fallback.
- Known JPEG/PNG fixtures: small valid file, exactly 5,242,880 bytes, oversized, zero bytes, misleading MIME/format, and unreadable input.
- A controlled test seam for delayed ACK, pending writes, process restart and malformed location. The test seam must not become a production “force unlock” UI.

Before each run, record app commit/build, Android API/device, backend/rules revision, tester/date, test IDs and fixture setup. Inspect results in the actual authorized repository/runtime. If emulator setup or a fault-injection seam does not exist, record that prerequisite instead of claiming a simulated conversation or screenshot proves the case.

The feature author prepares and runs the relevant cases. Another teammate reproduces or reviews the important results before merge. Rotate participation across User F, User G, and User J so everyone understands the full flow.

## 2 Coverage map

A journey groups related tests. It does not automatically pass any AC or every test listed in the group.

| Journey | Existing tests | Explicitly linked acceptance criteria |
|---|---|---|
| 1. Account, profile and exact recipient | T-01, T-02, T-03, T-39 | AC-01, AC-02, AC-03, AC-11, AC-13 |
| 2. Four-step creation and invalid inputs | T-04, T-06, T-12 | AC-02, AC-03, AC-14 |
| 3. A to B delivery and truthful lists | T-08, T-27, T-35 | AC-03, AC-11 |
| 4. Self-send remains locked | T-07, T-44 | AC-03, AC-09, AC-11, AC-12, AC-13, AC-14 |
| 5. Server-time unlock and foreground recovery | T-13, T-14, T-24, T-25 | AC-04, AC-07, AC-10, AC-12 |
| 6. Single-photo validation and original-copy retry | T-05, T-09, T-31, T-36 | AC-02, AC-03, AC-10, AC-14 |
| 7. Location and current AND | T-16, T-17, T-18, T-19, T-20, T-42 | AC-05, AC-06, AC-07, AC-10 |
| 8. Permanent re-view and account cleanup | T-28, T-29, T-30, T-44 | AC-09, AC-10, AC-13, AC-14 |
| 9. Notifications and absent background registrations | T-26, T-32 | AC-05, AC-07, AC-08 |
| 10. Ambiguous publication and delayed writes | T-10, T-11, T-34, T-40 | AC-02, AC-03, AC-14 |
| 11. Backend access and atomic-pair rules | T-21, T-22, T-23, T-33, T-43 | AC-01, AC-11, AC-12, AC-14 |
| 12. Time zones and daylight-saving boundaries | T-15, T-41 | AC-04 |
| 13. Integrated flow, failures and native UI | T-37, T-38, T-45 | AC-01, AC-02, AC-03, AC-06, AC-10, AC-11 |

The map covers all 45 current planned tests. T-44 intentionally appears in both self-send and account cleanup; one well-recorded execution may supply relevant evidence to both reviews, without inventing a second run.

## 3 Journey 1 Account, profile and exact recipient

**Related design:** DD-03, DD-19, DD-26; ARC-03/ARC-06.  
**Prerequisite:** Auth/profile UI and emulator rules; an account whose Auth registration succeeds while initial directory creation is made to fail.

1. Register A with a nonblank display name, sign out, and sign back in. Try an invalid password and a signed-out protected read.
2. Interrupt profile creation after Auth success. Relaunch/sign in and use the profile repair form; confirm that retry repairs the one profile without duplicating registration.
3. Copy A's UID from Account, edit only A's display name, and look up A by exact UID from B.
4. In a draft, confirm B as recipient, change any UID input, and verify confirmation clears until the new exact lookup is confirmed.
5. Try blank/fabricated UID, unavailable network, denied lookup and directory listing. Inspect the actual error category.
6. Sign out A, sign in B and relaunch. Review visible records and delayed old-session results.

**Expected:** only valid authorized sessions access data; own UID/name maintenance works; exact lookup and confirmation are required; absent/unavailable/denied are distinct; sending is blocked during missing-profile repair; no previous-account records or callbacks appear.

**Evidence:** screen states, redacted typed error classes, rules decisions and matching test IDs. Do not capture passwords or protected message content.

## 4 Journey 2 Four-step creation and invalid inputs

**Related design:** DD-04, DD-07, DD-14, DD-15, DD-27.

1. Walk Content → Recipient → Lock → Review with text-only, photo-only and both.
2. Try no body/photo, title-only content, missing recipient, absent lock and invalid photo.
3. Enter an optional title; verify the content/review screen explains that the recipient can see it while locked.
4. Move Back/Next and inspect draft preservation. At Send, observe the assigned operation ID/snapshot.
5. During SENDING and OUTCOME_UNKNOWN, try editing and repeated Send/Retry taps.
6. After definite unpublished failure, test unchanged retry; then separately abandon and make an edited new draft.
7. Deliberately create a second identical-content capsule as a separate new operation.

**Expected:** valid content combinations work; title is optional and not substitute content. Only DRAFT is editable. One in-flight logical send preserves its ID/snapshot. An edited restart requires definite failure and a new ID; an intentionally new identical capsule is allowed.

**Evidence:** UI state transitions and operation IDs/digests without body/photo data. Verify the actual changed behavior, not only a disabled-looking button.

## 5 Journey 3 A to B delivery and truthful lists

**Related design:** ARC-02/ARC-04; DD-16, DD-21, DD-28.

1. Keep B's app closed. A selects B by exact UID and sends a future time capsule.
2. Record required upload completion, if any, and final backend publication acknowledgment.
3. Confirm A can reach SENT while B is still closed.
4. Open/sign in as B and refresh Received. Inspect only safe locked metadata.
5. Compare Sent/Received for A, B and C. Include failed and unknown operations in the scenario.
6. Repeat across two independent Android sessions/devices and inspect stale-list wording while offline.

**Expected:** A's success depends on acknowledged complete publication, not a B handshake. B discovers the record on app-open/refresh. C cannot retrieve private data. Pending/failed/unknown operations are not presented as successful delivery; stale lists are labeled.

**Evidence:** participant-constrained query results, one capsule ID and separately captured sender/recipient states.

## 6 Journey 4 Self-send remains locked

**Related design:** ARC-03/ARC-06; DD-15, DD-18, DD-28.

1. A creates a future self-send containing text and a photo.
2. Open it from Sent, then Received; compare the underlying capsule ID.
3. Before unlock, attempt protected text/photo reads through the app and applicable emulator rules tests.
4. Return to the composer/detail after publication and restart the process. Inspect whether old raw sender preview buffers expose the body/photo.
5. Repeat with in-flight callbacks finishing after logout/account change using the controlled session test seam.

**Expected:** one self-send record appears in both lists, but sender privileges do not bypass recipient locking. Raw composer buffers do not become a post-send read path; late callbacks cannot restore protected data.

**Evidence:** actual denied protected reads and UI/cache inspection. Hiding a card preview alone is insufficient.

## 7 Journey 5 Server-time unlock and foreground recovery

**Related design:** ARC-05; DD-08, DD-17, DD-23, DD-24.

1. Publish a time-only capsule with a known future backend deadline. Before the deadline, open detail and Retry online.
2. Move the device clock forward/back and repeat. Try publishing TIME/BOTH at or before the backend current time.
3. Close/force-stop the app and suppress notification display. After the deadline, reopen/resume or use detail/Retry.
4. Repeat with the network unavailable before first unlock, then restore connectivity.
5. Repeat a combined lock with time reached but no qualifying current location.

**Expected:** no early unlock or invalid past-time publication. A valid online foreground check recovers after the deadline without any background event. Offline first unlock remains locked/verification-required; combined conditions still require current location.

**Evidence:** backend time decision, persisted instant, online confirmation and actual protected-read result. Do not use the device's displayed time as the authority.

## 8 Journey 6 Single-photo validation and original-copy retry

**Related design:** DD-04, DD-16, DD-25.

1. Select one valid JPEG/PNG; try exactly 5,242,880 bytes, oversize, zero-byte, unreadable and mismatched-format fixtures.
2. On API 33+ exercise Photo Picker; on API 26–32 exercise ACTION_OPEN_DOCUMENT. Cancel selection and ensure the draft remains usable.
3. After the original private copy is made, interrupt/restart a definite retry and revoke the original URI's access in the fixture.
4. Fail an upload before ACK and inspect the recipient query. Separately inject wrong path, missing object and denied download.
5. Inspect cleanup only for a definitely failed/abandoned operation with no outstanding publication. Leave an uncertain operation's potential object intact.

**Expected:** only one supported original within the real-byte limit is accepted. Retry uses the bounded private original copy. Failed required upload never yields successful publication. Missing/denied media shows truthful recovery; uncertain outcomes do not trigger unsafe orphan deletion.

**Evidence:** measured file sizes/formats, selected input path by API, upload/publication phases and object access results. Do not include fixture contents containing personal photos.

## 9 Journey 7 Location and current AND

**Related design:** DD-05, DD-12, DD-17, DD-22, DD-23.

1. Choose a map pin and explicitly confirm it; repeat with Use current location. Relaunch and compare the stored coordinates.
2. Deny/revoke foreground permission and turn location services off. Confirm manual pin selection still works and failed checks retain locked content.
3. Use deterministic samples at distance 150 m, age 60 s and accuracy 100 m, then values just outside each limit. Include negative, missing and nonfinite data.
4. Exercise BOTH with time/location F/F, F/T, T/F and T/T in the same online attempt.
5. Visit before the deadline and leave; then retry after time eligibility. Separately remain inside across the deadline and retry without exiting/re-entering.
6. Let a previously valid sample expire before submission or transaction retry. Drop the network and reconnect after departure.
7. Repeat the supported qualifying/nonqualifying foreground flow on a physical phone, recording actual accuracy and environment.

**Expected:** exact valid boundary values qualify; invalid/stale/poor data does not. Only current time AND current location can grant first unlock. No history latch, queued old-location intent or injected ENTER hint authorizes access. A real-phone run is separate from deterministic boundary verification.

**Evidence:** test input distance/age/accuracy, decision and server confirmation. Do not claim meter-perfect real GPS from an emulator fixture or silently change 150 m/60 s/100 m thresholds.

## 10 Journey 8 Permanent re-view and account cleanup

**Related design:** ARC-06; DD-06, DD-18.

1. Unlock online, fetch protected text/photo, and confirm the verified marker and retained bytes belong to the active UID.
2. Navigate away, leave the target location, change zone and relaunch; re-view online and from another authorized session.
3. Go offline and re-view retained same-account content.
4. Repeat offline with a photo never downloaded or removed from the test retention fixture.
5. While offline, sign out and relaunch; verify blank protected state. Attempt unavailable sign-in without treating it as successful authentication.
6. Sign in as B through a valid authenticated path, go offline again and inspect A's former retained content. Use the controlled session seam for the specific offline account-switch/late-callback case.
7. Complete delayed A callbacks after the generation changes; inspect screen writes, files and notifications. Inspect the uncertain-send journal for opaque metadata only.

**Expected:** confirmed remote unlock stays permanent. Offline view needs active UID + marker + bytes. Missing assets give download guidance. Logout/switch removes old protected data and blocks its recreation. Network-required sign-in failure is not itself proof of cross-account isolation.

**Evidence:** lifecycle/session sequence, redacted retained-file ownership checks, actual reads and cleanup. AC-13 remains unverified until its full cleanup obligations pass.

## 11 Journey 9 Notifications and absent background registrations

**Related design:** ARC-07; DD-08, DD-09, DD-24.

1. Deny notification permission on an applicable API and perform a valid foreground first-unlock check.
2. For another fresh capsule, allow permission and perform one supported check; repeat sync/detail/Retry afterward.
3. Delay/drop the alert and confirm in-app access still works.
4. Tap a stale notification after logout/account change; verify normal authentication/state checks.
5. Inspect production manifest and runtime registration paths: no background-location permission, alarm/geofence/boot receiver, periodic polling or FCM.
6. On a physical phone, force-stop/reboot, reopen and verify current online conditions; an injected hint without a valid current sample cannot unlock.

**Expected:** no crash or access change from notification denial/missed delivery; at most the intended attempt marker behavior, generic preview and safe entry. Recovery occurs at the next supported foreground check. Exact-time or closed-app delivery is not a pass criterion.

**Evidence:** notification attempt/permission observation, manifest/runtime inspection and actual reopened state. A notification appearing is not proof that a protected access rule is correct.

## 12 Journey 10 Ambiguous publication and delayed writes

**Related design:** ARC-04; DD-14, DD-15, DD-16, DD-21.  
**Prerequisite:** emulator/test seam that can distinguish delayed commit from lost ACK. Ordinary airplane-mode toggling alone cannot reliably locate every phase.

1. Cut connectivity while a Firestore write is locally pending; inspect UI and task/metadata state, then reconnect.
2. Let the backend commit but withhold/loss the ACK. Retry the same ID and press Retry concurrently.
3. With Storage ACK already complete, interrupt before/after final paired publication. Inspect both metadata/protected documents.
4. Delay the original write while an authoritative read still says absent. Verify the UI stays OUTCOME_UNKNOWN and cannot start an edited new-ID send.
5. Restart the process; replay only the unchanged original ID/path/digest when the contract permits, then allow the delayed original to arrive.
6. Separately cause an explicit terminal rejection with no outstanding write, then exercise unchanged retry and edited-new-draft behavior.

**Expected:** one logical operation resolves to one matching publication or definite recoverable failure. No split Firestore pair, duplicate new-ID send, silent overwrite or false failure from absence. Storage and Firestore are not represented as one cross-product transaction.

**Evidence:** ordered phase/timestamp/ID records, matching digest, final remote pair and truthful UI state. Record a conflict as a conflict; never rewrite the record merely to make the test pass.

## 13 Journey 11 Backend access and atomic-pair rules

**Related design:** ARC-02/ARC-03/ARC-05; DD-16, DD-17, DD-23.  
**Prerequisite:** local rules-test harness with synthetic fixtures and both rulesets. These negative tests must not be run against unrelated or production user data.

1. For A→B, attempt actual text and photo reads as A, locked B, unlocked B, C and signed out.
2. Repeat locked A→A, including Storage access.
3. Attempt forged sender/recipient, modified title/body/lock fields, early unlock, unauthorized unlock and true→false relock.
4. Attempt metadata-only create, protected-content-only create, mismatched owner/digest, malformed field/lock shape and forged initial unlocked state.
5. Verify a valid atomic pair and allowed recipient transition succeed.
6. Compare constrained participant list queries with unrestricted capsule/directory listing; attempt post-publication media replacement/deletion.

**Expected:** access matches the actual participant/self-send/time contract; malformed/one-sided creates and immutable mutations are denied; the valid pair works. Firestore and Storage protected-read decisions agree.

**Evidence:** separate rules-test expected/observed outcomes. Android screen hiding alone cannot pass this journey.

## 14 Journey 12 Time zones and daylight-saving boundaries

**Related design:** DD-13, DD-23.

1. Create in one IANA zone, store the UTC instant/creation zone, then change device zone and compare the instant/display.
2. Use the explicit T-41 fixtures: America/New_York gap **2027-03-14 02:30**, and overlap **2027-11-07 01:30**.
3. Reject the nonexistent gap. For the overlap, choose each valid offset in separate attempts and inspect the resulting review instant.
4. Change device clock and test strict-future publication at the backend boundary.

**Expected:** display is coherent without altering the stored instant. Ambiguity requires an explicit offset; nonexistent local time is rejected. Backend time remains authoritative.

**Evidence:** zone/offset/instant values and server acceptance/rejection. These fixtures are planned inputs, not observations from a live test.

## 15 Journey 13 Integrated flow, failures and native UI

**Related design:** DD-01, DD-02, DD-07, DD-10, DD-26–DD-28.

1. Run the actual A→B sequence: register/sign in → exact recipient confirmation → create → acknowledged send → locked B → online time unlock → photo fetch → permanent re-view.
2. Repeat LOCATION and BOTH, adding unrelated-C denial and correct account state.
3. Interrupt lookup, publication, current-location access and photo loading; verify each actual failure category gives a feasible recovery.
4. Walk every screen with keyboard visible, long titles/UIDs, larger system text and normal Back navigation. Check scrolling, insets, labels, touch targets and step progression.
5. If testing the local native wireframe, label the simulation and record only its appearance/model/build results. Run actual production acceptance separately.

**Expected:** complete implemented flow meets its applicable ACs with safe failure states. No clipped critical actions or unexplained screen trap. A local simulation never substitutes for Firebase rules, real location or cross-device evidence.

**Evidence:** full tested commit/rules configuration, a short reproducible walkthrough and actual result for each selected T item. T-45's historical wireframe build environment is not a completed Android Studio GUI/import/device launch.

## 16 Result record to fill after actual execution

Copy one record per T case or clearly defined run. The student writes what they actually observed; the agent may organize evidence but must not invent it.

```text
Test ID and journey:
Relevant FR/NFR, ARC/DD and AC:
Date/time and timezone:
Tester and reviewing teammate:
App commit / build:
Backend and rules revision:
Android API / emulator or physical device:
Fixture and setup:
Exact actions:
Expected result:
Observed result:
Result: NOT RUN | PASS | FAIL | BLOCKED
Evidence paths or PR links:
Failure/blocker and next action:
Owner / target period / dependency:
```

If a result changes, append the new run and preserve the earlier failure evidence. A passing local unit test, a passing emulator rules test and a physical-device run are separate evidence types. Update progress only for the item actually verified, and keep unresolved obligations visible.

