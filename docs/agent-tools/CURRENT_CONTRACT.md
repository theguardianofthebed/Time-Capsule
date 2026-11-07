# TimeCapsule implementation contract

Use this guide for the project's behavior, architecture, dependency choices, and acceptance links. Read the exact affected FR/NFR, ARC/DD, AC, and T records in the [full contract reference](reference/timecapsule-contract-r38.json) before implementation. [SOURCE_REGISTER.md](SOURCE_REGISTER.md) identifies the authoritative sources; [DESIGN.md](../../DESIGN.md) covers screens and visual design; [JOURNEYS.md](JOURNEYS.md) gives verification procedures.

The full reference contains Atlas revision 38 and instructions version 3. Find an ID in `nodeRecords`, read its `sections` and current English overview/constraints, and follow the explicit `source`/`target` links in `relationshipRecords`. `sourceRecords` and `sourceRecordHistory` retain provenance. `contractGuidance` contains the contract instructions. This is a read-only documentation reference, not an Atlas import, sync, or replacement payload.

Apply the confirmed [7 November Views/screen update](UI_VIEWS_UPDATE_2026-11-07.md) alongside this retained reference: the UI uses **Views/XML + ViewBinding** and exactly S-01–S-08, with recipient and lock choices sharing S-05. The live legacy Atlas update is pending; no newer live revision is claimed. The repository still has its provisional Compose scaffold until #25 adopts the separate verified starter. Follow [the implementation order](IMPLEMENTATION_ORDER.md) before any feature work. Implementation and test status require their own evidence.

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
| Product boundary | Confirmed UI: native Android Views, XML layouts and ViewBinding; eight main screens S-01–S-08. The provisional Compose scaffold is replaced only through reviewed #25 adoption. No incidental framework migration, Room capsule database, background location, alarms, geofence registrations, boot receivers, periodic polling, FCM, always-on services, external links/QR, social graph, audio or one-view destruction. Direct camera input and mandatory compression are outside the required path. | DD-07, DD-11, DD-24, DD-25; instructions v3 |

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

For detailed code work, read the full overview and constraints of the relevant node plus its explicitly linked requirement, AC and test in the [frozen reference](reference/timecapsule-contract-r38.json). Live MCP is optional for reading this captured contract. When a later authorized revision is supplied or accessible, check the affected changes and refresh the shared reference through a reviewed PR. Do not guess a later decision or silently blend revisions; work determined by the available authorized contract may continue.

## 4 Selected dependency and deployment contract

This table combines the narrow 7 November starter decision with the retained service selections in DD-11/DD-20. Read [the exact starter baseline](UI_VIEWS_UPDATE_2026-11-07.md). Preserve the current repository versions for the starter; add and verify future service dependencies in #9. No build pass, newest-release claim, cloud provisioning, or dependency compatibility pass is implied.

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

The corrected starter uses **AGP 9.3.0** after Android Studio rejected the initially preserved 9.4.1. This is an explicit compatibility exception to the inspected repository baseline; the documentation PR does not change the remote Gradle files. Gradle 9.6.0, daemon JDK 25, Java target 11, SDK 37/37/26 and the remaining library pins are retained. [AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes) requires Gradle 9.5.0 or later and supports API 37; local Sync/build/device evidence is still required by #25.

The starter does not add Firebase/Maps/DataStore/Material libraries merely because the target design names them. Preserve Core KTX 1.19.1, Lifecycle runtime KTX 2.6.1, and the existing test pins; replace Activity Compose with Activity KTX at the same 1.13.0 version. #9 verifies the selected service pins against that adopted baseline before integration. Do not silently downgrade the toolchain to historical r38 values or rename an existing cloud app. T-45 must record the actual current toolchain and distinguish build checks, device walkthrough, and production acceptance.

## 5 Acceptance and test index

Use the full obligations of each AC when assessing results. The table identifies planned coverage; record actual executions and acceptance evidence separately.

| AC | Acceptance area | Explicit planned-test links in revision 38 |
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

Record the authorized contract revision in the Issue/PR. When a newer approved Atlas revision or export is available, review the affected changes and update this summary and the full reference together. Read the complete local reference when live MCP is unavailable. Use [SOURCE_REGISTER.md](SOURCE_REGISTER.md) for provenance and source precedence.
