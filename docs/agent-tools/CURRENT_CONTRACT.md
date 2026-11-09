# TimeCapsule implementation contract

This is the core guide for project scope, behavior, UI decisions, architecture, dependency choices, and trace IDs. Start here rather than opening every supporting document. Before implementation, read the exact current affected FR/NFR, ARC/DD, AC, and T records through authorized TimeCapsule MCP access or an explicitly supplied private current snapshot. The [public r38 reference](reference/timecapsule-contract-r38.json) is historical and cannot establish later wording. Supporting source, design, and journey files provide deeper evidence when a task needs it; they are not additional mandatory entry documents for every task.

The repository retains the full historical revision 38 reference and a [revision 55 verification record](reference/timecapsule-contract-r55-verification.json). The latter contains counts, comparison results and a private-capture hash, **not the current element text**. The complete r55 capture is retained privately pending explicit public-disclosure approval. In a supplied full snapshot, find IDs in `nodeRecords`, read their `sections`, and follow explicit `source`/`target` links in `relationshipRecords`; `sourceRecords`, `sourceRecordHistory` and `contractGuidance` retain provenance and guidance. Documentation snapshots are not Atlas import, sync or replacement payloads.

The TimeCapsule MCP at `atlastree.app` was read successfully on **10 November 2026 (Asia/Seoul)**: revision **55**, instructions version **3**, **112 elements and 378 relationships**. The private capture preserves the exact contract and its recorded statuses; the live Atlas was not edited. Public repository readers must obtain authorized current-record access before relying on later wording. The [revision 38 snapshot](reference/timecapsule-contract-r38.json) remains historical provenance.

Apply the confirmed [7 November Views/screen update](UI_VIEWS_UPDATE_2026-11-07.md) alongside the capture: **Views/XML + ViewBinding**, exactly **S-01–S-08**, recipient and lock choices together in S-05, and **AGP 9.3.0**. The later approved starter toolchain and application identity take precedence over the older DD-11/DD-20/T-45 values retained in the Atlas. #25 and PR #29's bounded #9 foundation are merged on `main`; #9 verification readiness remains pending. Follow [the implementation order](IMPLEMENTATION_ORDER.md) before feature work.

**Visual proposal pending review:** [DESIGN.md §3](../../DESIGN.md) compares the October 14 prototype screenshots with the still-current DD-01 palette, typography and navigation wording. Screenshot-derived swatches and the proposed "Moments" tab label are **not yet an approved Atlas contract revision**; the actual TypeScript/CSS tokens are unavailable in the repository. Preserve the r55 DD-01/DD-07 text and the confirmed S-01–S-08, S-04 → S-05 → S-06 flow until the team reviews and authorizes any visual contract changes. No implementation or acceptance status changes follow from this design-document PR.

### Implementation evidence, separate from specification

Final inspected baseline: `main` at `c60d866cdc9794af97db129c946b66711270f6b9`. PR #29 merged externally at **00:56:26 KST on 10 November 2026**, during this audit. The earlier observation at `59f3a0b` preceded that merge. This is a dated observation, not a permanent readiness certificate.

| Work | Observed implementation and verification | What remains |
|---|---|---|
| [#25](https://github.com/theguardianofthebed/Time-Capsule/issues/25) Views starter | PR #27 merge `767da710` is in `main` through PR #26 merge `6e2e286b`. Source uses AppCompatActivity, XML and ViewBinding. User J's Week 1 log records build, lint, four unit tests, two instrumented tests and emulator launch/reopen/light-dark checks. | Eight layout placeholders are not eight completed feature screens. Authentication, delivery, unlock, full navigation and T-45 acceptance are not established. |
| [#9 / PR #29](https://github.com/theguardianofthebed/Time-Capsule/pull/29) foundation | Merged at `c60d866` from head `544f5a673037059f3fb9fc5656e1616caa27748c`. Contains interfaces, session/adapters, memory-only cache initialization and release configuration guard. Its latest log records 25 unit tests, debug build/lint and expected missing-config release rejection. These reported runs were not rerun here. | MERGED, VERIFICATION PENDING. Issue #9 remains open. The later PR #29 merge restores the foundation after historical rollback PR #28. Latest source/log are ahead of its description. Actual emulator service/rule enforcement and applicable current-head review/verification evidence still require a readiness check; downstream feature acceptance is not established. |
| [#10–#23](IMPLEMENTATION_ORDER.md) feature work | PR #29 now satisfies #9's merge-presence condition. #10 remains BLOCKED until missing prerequisite verification evidence and its owner/reviewer preflight are recorded; later issues retain their own direct gates. | A plan, interface, fake success or closed prerequisite issue does not establish a working account/capsule flow. |
| Atlas acceptance | Captured requirements/designs remain `not_started`, ACs `not_verified`, and tests `not_run`. | Reconcile progress through a separately authorized Atlas task. Do not infer all contract statuses from #25 completion. |

A local feasibility check is E1 evidence. A complete app path using the integrated backend is E2 evidence. Neither a build nor one component test establishes the entire linked acceptance criterion.

The Git root is the Gradle root, `app/` is the application module, and `prototype/` and `spikes/` hold non-production prototype and feasibility work.

## 1 Scope and required behavior

| Area | Current confirmed contract | Source IDs |
|---|---|---|
| Accounts | Firebase email/password registration, sign-in and sign-out. Use Auth UID as the public recipient code; exact profile lookup plus explicit recipient confirmation. Display/copy own UID; maintain own display name. | FR-01, FR-03; DD-03, DD-19, DD-26 |
| Delivery | Self-send and account-to-account delivery are MVP. Sender success does not wait for the recipient to open the app. New receipts are discovered on app-open/refresh. | FR-03, FR-11; ARC-04; DD-21 |
| Content | Nonblank text and/or one original JPEG/PNG of 1–5,242,880 bytes. Optional title is safe metadata and is explicitly disclosed as visible while locked; title alone does not satisfy the content rule. | FR-02, FR-04, FR-08; DD-04, DD-25, DD-27 |
| Editing | Only DRAFT is editable. Send freezes one snapshot, ID, media path and digest. SENT is immutable. OUTCOME_UNKNOWN remains frozen; edited restart needs definite unpublished failure and a new ID. | FR-02, FR-03; DD-14, DD-15, DD-21 |
| Time | TIME/BOTH publish only when unlockAt is strictly later than backend request time. Store a UTC instant and creation IANA zone. First unlock requires online server authorization. Device clock changes do not authorize it. | FR-05; DD-13, DD-23 |
| Location | Use Google Maps confirmed pin or current location. Check finite coordinates, distance ≤150 m, sample age ≤60 s and reported accuracy ≤100 m in the current attempt. Permission/service failures, negative/missing/invalid values and poor readings retain the lock. | FR-06; DD-05, DD-12, DD-22 |
| Combined lock | Time and fresh current location must qualify in the same online attempt. An earlier visit does not latch. A user already inside may retry without exiting/re-entering. | FR-07; DD-05, DD-17, DD-23 |
| Protected access | A→B sender can read its own sent content; locked B cannot. A→A remains locked in Sent and Received. Unrelated C and signed-out users cannot read private metadata/content. | FR-08; ARC-03; DD-18, DD-28 |
| Recovery and notifications | The complete trigger set is app-open, foreground resume, detail opening and explicit Retry. After a supported check confirms first unlock, attempt one generic local notification when permission permits. A missed notification never changes access. | FR-09, FR-10; DD-08, DD-09, DD-24 |
| Local retention | Firestore memory-only cache; small DataStore markers/journal and UID-scoped app-private retained files. Offline recipient re-view needs the active UID, server-confirmed marker and actual retained bytes. First unlock is online. | FR-12, NFR-01; ARC-06; DD-06, DD-18 |
| Logout/switch | Invalidate session generation before clearing old state; cancel listeners/work/account notifications; clear views, verified markers and protected files. Discard late old-session callbacks. Only opaque UID/ID/digest/status recovery information may survive logout for uncertain sends. | FR-01, NFR-01; ARC-06; DD-18 |
| Product boundary | Confirmed UI: native Android Views, XML layouts and ViewBinding; eight main screens S-01–S-08. The former Compose scaffold has been replaced through merged #25 work. No incidental framework migration, Room capsule database, background location, alarms, geofence registrations, boot receivers, periodic polling, FCM, always-on services, external links/QR, social graph, audio or one-view destruction. Direct camera input and mandatory compression are outside the required path. | DD-07, DD-11, DD-24, DD-25; instructions v3 |

Instructions v3, ARC-07 and DD-08/DD-24 select foreground-only operation. ARC-06 and instructions v3 select memory-only Firestore. Apply those explicit decisions when reading historical hints or bilingual summaries in the source records.

## 2 Architecture in one page

| ID | Responsibility and boundary |
|---|---|
| ARC-01 | UI/ViewModel → CapsuleRepository → Auth, Firebase, location, media and local adapters. Repository operations cover session, getRecipient(uid), publish(frozenOperation), reconcile(id), lists, verifyUnlock(id), readContent(id). Return typed results; scope asynchronous results to a session generation. ConditionEvaluator stays pure. |
| ARC-02 | Separate readable capsule metadata, protected text, Storage photo and local verification state. Metadata/content share a stable ID and immutable digest. Lists constrain senderId or recipientId to the active UID. |
| ARC-03 | Backend account/time rules and matching text/photo access rules. Exact directory get is permitted for authenticated users; listing the directory is denied. Prevent forged ownership, invalid paired creation and mutable published content. |
| ARC-04 | Persist the frozen operation before upload. Required Storage upload ACK precedes one atomic Firestore metadata/content publication. Serialize attempts per ID. Matching server-confirmed publication resolves SENT. Absence alone does not cancel an outstanding write. |
| ARC-05 | Online recipient transaction makes false→true unlock monotonic. Backend enforces time and changed-key rules; current location remains the official client's explicit trust boundary. Do not queue a location unlock for a later stale retry. |
| ARC-06 | Memory-only Firestore plus explicit UID-scoped retained content. Server-confirmed verification marker gates offline reads; logout/session cleanup blocks old callbacks and excludes protected state from Android backup. |
| ARC-07 | Native foreground location, map, media and notification adapters. API 33+ Photo Picker; API 26–32 ACTION_OPEN_DOCUMENT; bounded original private copy. No production background trigger adapters. |

Data paths from ARC-02:

| Path | Data and visibility |
|---|---|
| `recipientDirectory/{uid}` | UID/displayName only; exact lookup and owner profile maintenance. |
| `capsules/{capsuleId}` | Participants, optional disclosed title, lock metadata, photo reference, creation/unlock state. |
| `protectedContent/{capsuleId}` | Message text behind protected-read authorization. |
| `capsuleMedia/{senderUid}/{capsuleId}/original` | Original photo, read with authenticated Storage access; no public download-token URL as the access boundary. |
| App-private UID-scoped files and DataStore | Retained bytes, verified markers, in-flight snapshot and limited recovery journal, according to their separate lifecycle rules. |

**Two limits that code and explanations must retain:** Firestore publication cannot atomically prove Cloud Storage object existence; the official sender enforces upload acknowledgment and interruption tests cover failure. The backend does not attest real physical location or guarantee resistance to GPS spoofing. These limits are documented MVP boundaries, not missing promises.

## 3 Detailed design index

Use the exact DD identifier for a task. These are current canonical IDs; do not introduce D/Q/FLOW families.

- **DD-01** — Native Android visual and interaction specification
- **DD-02** — Error results and retry presentation
- **DD-03** — Account and recipient identity
- **DD-04** — Content and photo validation
- **DD-05** — Pure current-condition evaluation
- **DD-06** — Permanent unlocked access
- **DD-07** — MVP navigation boundary
- **DD-08** — Foreground event-independent verification
- **DD-09** — Best-effort foreground notification delivery
- **DD-10** — Verification seams and evidence sequence
- **DD-11** — Pinned Firebase and Android adapter dependencies
- **DD-12** — Map target and foreground location UX
- **DD-13** — UTC schedule and immutable locks
- **DD-14** — Stable operation identity and reconciliation
- **DD-15** — Frozen submission and uncertain-send lifecycle
- **DD-16** — Atomic publication and media boundary
- **DD-17** — One-way unlock transaction
- **DD-18** — Verified retention and account cleanup
- **DD-19** — Email/password accounts and exact recipient profiles creation/modification
- **DD-20** — Firebase deployment and configuration contract
- **DD-21** — Definite and uncertain delivery reconciliation
- **DD-22** — Map target and current-location implementation
- **DD-23** — UTC publication and one-way unlock rules
- **DD-24** — Foreground verification and notification triggers
- **DD-25** — Original-photo acquisition, retry and cleanup
- **DD-26** — Account entry and exact recipient flow
- **DD-27** — Compose, review and send flow
- **DD-28** — Receive, verify and re-view flow

For detailed code work, read the full current overview and constraints of each relevant node plus its explicitly linked requirement, AC and test through authorized MCP access or a privately supplied current snapshot. The public r38 file is historical; the r55 verification metadata is not a full contract substitute. If current affected records cannot be read, report that exact evidence gap and continue independent work rather than guessing. Public publication of a full private snapshot requires explicit disclosure approval as well as a reviewed PR.

## 4 Selected dependency and deployment contract

This table combines the narrow 7 November starter decision with the retained service selections in DD-11/DD-20. Read [the exact starter baseline](UI_VIEWS_UPDATE_2026-11-07.md). Preserve the adopted starter versions. PR #29 has merged the #9 service dependencies and interfaces; verify their bounded readiness before downstream integration. No build pass, newest-release claim, cloud provisioning, or dependency compatibility pass is implied.

| Item | Selected value |
|---|---|
| UI | Kotlin native Android Views + XML + ViewBinding; constructor-injected repository adapters |
| Starter AGP / Gradle | 9.3.0 / 9.6.0; AGP-only correction for the reported Android Studio support ceiling |
| Starter Gradle daemon / Java source target | JDK 25 / Java 11; these are separate settings |
| Starter Kotlin | AGP built-in mechanism; the removed Compose-plugin catalog version is not an embedded-compiler pin |
| Starter Android SDK | minSdk 26; compile/target 37 |
| Core library desugaring | desugar_jdk_libs 2.1.5 |
| Firebase | Android BoM 34.4.0; main auth/firestore/storage modules; no separate Firebase KTX artifacts |
| Google services Gradle plugin | 4.4.4 |
| Maps / Location | Maps SDK 19.2.0; play-services-location 21.3.0 |
| DataStore Preferences / Lifecycle ViewModel | 1.1.7 / 2.9.4 |
| Project/backend | Dedicated team Firebase project on Blaze; Firestore Standard/Native default database and default Storage bucket in asia-northeast3 |
| Starter application ID / namespace | com.example.timecapsule; matching future Firebase/Maps configuration must be reviewed in #9 |
| Rules/configuration | Version firebase.json, firestore.rules, storage.rules and firestore.indexes.json together; production configuration and deployment are separate from this document |
| Queries | Participant equality queries; default single-field indexes; stable client-side sort by createdAt then capsule ID |

The corrected starter uses **AGP 9.3.0** after Android Studio rejected the initially preserved 9.4.1. This is an explicit compatibility exception to the earlier repository baseline; PR #27 supplied the application-side change now merged on `main`. Gradle 9.6.0, daemon JDK 25, Java target 11, SDK 37/37/26 and the remaining library pins are retained. [AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes) requires Gradle 9.5.0 or later and supports API 37; the starter's recorded Sync/build/device evidence is summarized above. Changes to that build require fresh relevant checks.

The merged starter includes AppCompat 1.7.0 and Material Components 1.12.0 for its Views/theme. PR #29 now adds Firebase, Maps and DataStore dependencies and foundation interfaces on main; actual service feature integration belongs to later issues. Preserve Core KTX 1.19.1, Lifecycle runtime KTX 2.6.1, Activity KTX 1.13.0 and the existing test pins. #9 verifies the selected service pins against that adopted baseline before integration. Do not silently downgrade the toolchain to historical Atlas values or rename an existing cloud app. T-45 must record the actual current toolchain and distinguish build checks, device walkthrough, and production acceptance.

## 5 Acceptance and test index

Use the full obligations of each AC when assessing results. The table identifies planned coverage; record actual executions and acceptance evidence separately.

| AC | Acceptance area | Explicit planned-test links in revision 55 |
|---|---|---|
| AC-01 | Account, public code and session isolation | T-01, T-02, T-21, T-38, T-39 |
| AC-02 | Creation, content and immutable submission | T-04, T-05, T-06, T-31, T-39, T-40, T-45 |
| AC-03 | Exact lookup and acknowledged delivery | T-03, T-07, T-08, T-09, T-10, T-11, T-12, T-35, T-38, T-39 |
| AC-04 | Authoritative time and timezone handling | T-13, T-14, T-15, T-25, T-41 |
| AC-05 | Current location and map target | T-16, T-17, T-18, T-32, T-42 |
| AC-06 | Current AND and monotonic state | T-19, T-20, T-37, T-42 |
| AC-07 | Recovery without background delivery | T-24, T-25, T-32, T-42 |
| AC-08 | Foreground notification acceptance | T-26, T-32 |
| AC-09 | Permanent access and conditional offline re-view | T-28, T-29, T-30, T-44 |
| AC-10 | Actionable failures and screen recovery | T-05, T-09, T-18, T-25, T-30, T-31, T-36, T-38, T-45 |
| AC-11 | Lists, safe titles and app-open discovery | T-07, T-08, T-27, T-33, T-35, T-39, T-45 |
| AC-12 | Backend ACL, paired creation and immutability | T-07, T-14, T-21, T-22, T-23, T-33, T-43 |
| AC-13 | Retained-content and session cleanup | T-02, T-29, T-44 |
| AC-14 | Ambiguous outcomes and complete publication | T-06, T-09, T-10, T-11, T-22, T-31, T-34, T-36, T-40, T-43, T-44 |

An AC is not passed because one related test passes. Assess its complete obligations. Requirement/design/AC/test progress remains separate; do not cascade completion automatically.

## 6 Updating the contract

Record the authorized contract revision in the Issue/PR. When a newer approved Atlas revision or export is available, review the affected changes and update this summary and revision-verification metadata. Update a public full reference only when public disclosure is explicitly authorized. If live MCP is unavailable, obtain an authorized current private snapshot; do not substitute historical r38 for unread later wording. Use [SOURCE_REGISTER.md](SOURCE_REGISTER.md) for provenance and source precedence: official course requirements, latest explicit approved team decisions, current approved requirements/design, then historical proposals and feasibility notes.

## 7 Contract reconciliation and unresolved evidence

| Topic | Current interpretation | Follow-up owner and gate |
|---|---|---|
| Historical scope instruction calls cross-account delivery stretch | The October 7 confirmed baseline, Atlas v3/r55 and this task explicitly retain accounts and self/other-account delivery in MVP. No scope change is proposed. | User G reviews trace consistency in this governance PR; all implementers preserve FR-01/FR-03. |
| Historical DD-11 toolchain and DD-20 identity | Preserve the raw snapshot. Use the later approved starter: AGP 9.3.0, Gradle 9.6.0, daemon JDK 25, Java target 11, SDK 37/37/26, `com.example.timecapsule`. Future Firebase/Maps configuration must match the adopted app identity; do not silently provision `com.timecapsule.app`. | User J closes #9 verification gaps before successor readiness; User G reviews any later identity change. |
| Four creation steps versus eight main screens | Content, recipient, lock and review are logical checks. S-04/S-05/S-06 implement them with recipient and lock on S-05. | #12 owns the creation flow, #22 final consistency. |
| Early text-flow guidance versus actual dependencies | NFR-06/DD-10 describe verification progression. The current dependency contract still requires #13 before #14 and the dependencies of #21 before full recipient browsing. | Follow the actual graph. A narrower vertical slice requires an explicitly reviewed dependency/scope change first. |
| Backend and tests | Firebase family/region/plan are design selections. No cloud deployment, billing setup, actual emulator rule execution or integrated MVP acceptance is established by this documentation task. | #9 proves bounded foundation readiness; #11 and later issues supply scoped implementation/test evidence; #23 records integrated acceptance. |

Read exact trace edges from `relationshipRecords`. Issue-to-record coverage is an implementation planning mapping, not a new Atlas relationship. Do not add implicit edges or propagate passes across the graph.
