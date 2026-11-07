# Source register

Use this register to choose the right source for a task. Course requirements and explicit team decisions take precedence over drafts, prototypes, and feasibility notes. User G coordinates source review, User J checks implementation implications, and User F checks product wording; all three review meaningful changes.

## 1 Authoritative references

| Source | Reference | Use |
|---|---|---|
| Professor's agent supplement | `Term_Project_Agent_Usage_Supplement.docx`: Agent environment, deliverables, usage rules, and submission summary | Required agent environment, shared and individual files, review, and secret handling. |
| [7 November Views/screen decision](UI_VIEWS_UPDATE_2026-11-07.md) | User-supplied Kakao discussion, explicit eight-screen table, reference images, and current repository baseline | Latest narrow update for Views/XML + ViewBinding, S-01–S-08 grouping, starter compatibility, and implementation-order gates. Live Atlas reconciliation remains pending. |
| Atlas instructions | Retained Atlas `timecapsule`, content revision 38, instructions version 3 | Contract interpretation, explicit design selections, and canonical IDs, with the narrow later decision above. |
| Atlas nodes | FR-01–FR-12, NFR-01–NFR-06; ARC-01–ARC-07; DD-01–DD-28; AC-01–AC-14; T-01–T-45 | Read the complete overview and constraints of every affected requirement, design, acceptance criterion, and test. |
| Atlas relationships | 378 explicit relationships in revision 38 | Use the recorded links; do not invent an edge because two items sound related. |
| [Full contract reference](reference/timecapsule-contract-r38.json) | 112 node records, 378 relationships, and nine source records with revision-history provenance | Exact bilingual records available locally without MCP. Read-only reference; not an Atlas import or replacement payload. |
| [Implementation contract](CURRENT_CONTRACT.md) | Summary and indices of the full reference | Fast navigation and implementation boundaries; exact node clauses remain authoritative. |
| [Agent instructions](../../AGENTS.md) | The team's shared rules | English comments, consistent style, reuse, explanations of code refactors, PR delivery, and anonymous student labels. |
| [Android repository](https://github.com/theguardianofthebed/Time-Capsule) | Checked-out source and build configuration | Establishes actual implementation and dependencies. The [repository guide](REPOSITORY_STATUS.md) explains the project layout and UI framework issue. |
| [Design document](../../DESIGN.md) | Screen flow and design-system guidance | Confirmed product decisions and explicitly labeled implementation proposals. |
| [Verification journeys](JOURNEYS.md) | Procedures using existing T and AC IDs | Plan and record actual checks; a procedure alone does not establish a pass. |

When sources conflict, use this order: official course requirements → latest explicit authorized team/user decisions → approved requirements and design → workflow and milestones → proposals and historical notes. Feasibility results inform a design but do not approve scope or establish completed integration.

Use the main course guide for general project features, milestones, slides, video, and final submission. The agent supplement covers agent usage and its deliverables; it does not replace the main guide.

## 2 Course requirements

Use Android Studio Agent Mode for project code and record the actual model selected there. The detailed submission guide is [COURSE_COMPLIANCE.md](COURSE_COMPLIANCE.md).

| Requirement | Required location or practice |
|---|---|
| Team design | Root `DESIGN.md`: screen flow/navigation and Material 3 theme, color, type, and components. |
| Shared agent instructions | Root `AGENTS.md`, committed. Shared Rules may be included in this file. |
| Shared skills | Root `.agents/skills/`, committed and available to the team. |
| MCP configuration | Actual settings in each member's Android Studio settings directory; token-free copy at `docs/agent-tools/mcp.redacted.json`. |
| Secret protection | No committed API keys, tokens, or keystore passwords; sensitive files such as `local.properties` listed in `.aiexclude`. |
| Human review | Review every generated change before accepting it. Every student must explain the submitted code in Q&A. |
| Weekly logs | Individual files, with per-feature purpose, key prompts, agent changes/checks, errors, manual fixes, and the model used. |
| Retrospective | Individual account of what worked, agent limitations, and what the student can do independently. |

Planned weekly submissions are `agent-log-w1.md` on 13 November 2026, `agent-log-w2.md` on 20 November, `agent-log-w3.md` on 27 November, and `agent-log-w4.md` on 4 December. Follow later professor announcements. The supplement gives no submission time, platform, or final retrospective date.

## 3 Supporting project and lecture sources

The project context contains 17 Markdown files in `TimeCapsule_Project_Context_2026-10-07_Feasibility_Updated.zip` and two October 14 supplements. Use them for roles, scope history, and feasibility evidence; approved Atlas decisions govern implementation.

| Group | Files |
|---|---|
| Context and scope | 00_START_HERE.md; 01_PROJECT_CONTEXT.md; 02_SCOPE_AND_REQUIREMENTS.md |
| Roles/workflow | 03_TEAM_ROLES.md; 04_WORKFLOW_AND_SCHEDULE.md; 05_TODO_AND_DEPENDENCIES.md |
| Agent/project rules | 06_CHATGPT_WORKING_RULES.md; 07_DECISIONS_AND_OPEN_QUESTIONS.md; COPY_PASTE_INSTRUCTION.md |
| Requirements | 08_REQUIREMENTS_BASELINE_V0_1.md |
| Feasibility | 09_FEASIBILITY_OVERVIEW.md; 10_FEASIBILITY_ANDROID_RUNTIME.md; 11_FEASIBILITY_ACCOUNT_AND_DELIVERY.md; 12_FEASIBILITY_MEDIA_AND_PERSISTENCE.md; 13_FEASIBILITY_SPIKE_PLAN.md; 14_FEASIBILITY_RESEARCH_SOURCES.md |
| History | 15_CONTEXT_CHANGELOG.md |
| Later uploaded supplements | 16_TECHNICAL_BASELINE_AND_FEASIBILITY_2026-10-14.md; 17_DESIGN_DECISIONS_AND_ARCHITECTURE_DRAFT_2026-10-14.md |

Lecture sources are `5_Android Intents (2).pdf`, `9_Android Permission.pdf`, and `12_Android Sensors.pdf`. UI Widgets is also a reported class topic, but no separate deck is supplied. [COURSE_BASELINE.md](COURSE_BASELINE.md) identifies taught mechanisms and where agents should add explanation. A lecture example does not add an unrelated project feature.

Keep original course files in the team's permitted course location. Shared summaries and source names are sufficient for the repository; do not automatically publish the lecture PDFs or professor's DOCX.

## 4 Atlas source records

The full reference preserves the source objects in `sourceRecords`. `sourceRecordHistory` identifies revision 13 after-values for eight records and revision 11 for `SRC-PROTOTYPE-20261031`. These are original provenance records, not full source files retrieved anew at revision 38.

| Source ID | Recorded document/provenance | Interpretation |
|---|---|---|
| source-1 | TimeCapsule_Design_Review_v0.3_2026-10-25.md | Historical design input; use current ARC/DD statements. |
| source-2 | TimeCapsule_Astra_Work_Build_Prompt.md | Historical Atlas construction prompt, not authorization for prohibited mobile coding agents. |
| source-3 | TimeCapsule_Requirements_Review_v0.3_2026-10-25.md | Historical requirements input; current FR/NFR wording governs. |
| source-4 | TimeCapsule_Astra_Handoff_README.md | Historical handoff. |
| source-5 | TimeCapsule_Traceability_and_Gaps_v0.3_2026-10-25.md | Historical trace input; current explicit edges govern. |
| source-6 | TimeCapsule_Test_Plan_v0.3_2026-10-25.md | Historical tests; current T-01–T-45 procedures/statuses govern. |
| SRC-CONTRACT-20261031 | TimeCapsule_Contract.md | Normalized current contract provenance and unified ARC/DD identifiers. |
| SRC-DECISIONS-20261031 | TimeCapsule_Contract.md | Final design selections, including foreground-only triggers, vendor/dependency selections and durable original-photo copies. |
| SRC-PROTOTYPE-20261031 | TimeCapsule Mobile App Prototype.zip | Historical inspected Figma Make source: App.tsx, theme.css, fonts.css and related entry/package files. Source for DD-01 visual identity and flows, not production behavior proof. |

The recorded visual prototype is [TimeCapsule Mobile App Prototype](https://www.figma.com/make/TXMuk2CBolcTz4DAAfJPmT/TimeCapsule-Mobile-App-Prototype). The repository reference relies on the Atlas nodes and recorded source provenance; it does not establish the live Figma project's contents or runtime behavior.

Instructions version 3 treats source text saying an export was “not applied” as preparation history. The normalized contract is applied; revision 38 identifies the frozen reference, not the revision where every individual decision was made.

## 5 Contract interpretation

| Area | Rule to apply |
|---|---|
| Delivery | Accounts and self/other-user delivery are MVP: FR-01/FR-03 and instructions v3. |
| Backend and retention | Firebase is selected by DD-11/DD-20. ARC-06/DD-18 select memory-only Firestore and explicit UID-private retention. Provisioning and verification require separate work. |
| Verification triggers | ARC-07/DD-08/DD-24 use app-open, foreground resume, detail-open, and explicit Retry, without production background registrations. |
| Design IDs | Use ARC-01–ARC-07 and DD-01–DD-28, with their recorded relationships. |
| Recipient and content | FR-03/FR-04/DD-07 require exact UID confirmation, one supported original photo, and actual authorization instead of simulated unlock. |
| Evidence | E1 is isolated feasibility; E2 is app integration; E3 is repeated verification. A wireframe/model/build result does not establish Firebase, security, device, or full acceptance success. |
| Bilingual summaries | Follow the explicit English contract selection and instructions v3 when a historical summary differs. Report a material unresolved conflict. |
| UI framework | Views/XML + ViewBinding and S-01–S-08 are confirmed. #25 adopts the separately verified starter; application files in the documentation PR remain unchanged. |

The frozen records contain `not_started` requirement/design statuses, `not_verified` ACs, and `not_run` tests. Those values describe the reference's recorded progress. Use actual implementation and test evidence to assess later progress, and update each record only through its authorized workflow.

## 6 Keeping references aligned

Existing issue bodies retain an English contract capture at revision 46; the local JSON remains the unchanged revision 38 reference. The 7 November supplement is an explicit targeted decision, not an invented live revision. The legacy Sites MCP returned `410 ATLAS_MOVED`. A subsequently authorized new Project Atlas MCP connection did not list the Time Capsule target, so its live update is pending until the correct atlas is accessible. Preserve unrelated later issue refinements and reconcile the targeted Atlas proposal against a freshly read authorized revision before applying it.

For each substantive task, record the authorized source revision, affected FR/NFR → ARC/DD → AC/T references, Issue, branch, and expected behavior. Read `nodeRecords` and their actual `relationshipRecords` locally; live MCP is optional for this reference.

When an approved newer revision is available, review its changes and update the reference and related guidance through the same PR. Do not infer freshness from a cached count or mix conflicting revisions. State exact conflicting clauses when a material decision is missing, continue independent work, and obtain the responsible team's decision before implementing the disputed behavior.

The JSON uses a documentation wrapper, not the Atlas import schema. It omits account membership, glossary data, tool permissions, and unrelated settings. Do not import it or use it to overwrite live Atlas data. Atlas changes follow their own authorized process.

Record test evidence with the actual build/rules revision, environment, tester, expected and observed result, and evidence link. Documentation, PR publication, human approval, and acceptance are separate facts.

## 7 Technical references

Use the installed dependency's documentation when implementing the proposed design-system mappings in `DESIGN.md`.

- [Material Components for Android](https://github.com/material-components/material-components-android/blob/master/docs/getting-started.md): Material 3 themes for native Views.
- [Material typography for Android Views](https://github.com/material-components/material-components-android/blob/master/docs/theming/Typography.md): reusable typography attributes.
- [Android accessibility for Views](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views): touch targets and descriptions.
