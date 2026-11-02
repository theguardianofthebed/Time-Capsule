# Source register and authority

**Prepared:** 3 November 2026, Asia/Seoul  
**Maintainer:** User G leads source/trace review; User J checks implementation implications; User F checks product wording. All three review meaningful changes.  
**Use:** Read this register before using project context. It prevents an agent from reinstating obsolete scope or treating a prototype/test plan as a completed implementation.

## 1 Current authoritative references

| Source | Locator read for this package | Authority and use |
|---|---|---|
| Professor's agent supplement | `Term_Project_Agent_Usage_Supplement.docx`; Agent environment, Agent deliverables, Agent usage rules, submission summary | Official requirements for agent environment, team/individual documents, review and secret handling. It supplements the main course guide. |
| Current TimeCapsule Atlas instructions | Atlas ID `timecapsule`; content revision **38**, instructions version **3**; retrieved 3 November 2026 KST | Current authorized contract interpretation and canonical IDs. Explicitly replaces earlier alternatives described in historical documents. |
| Current numbered Atlas nodes | FR-01–FR-12, NFR-01–NFR-06; ARC-01–ARC-07; DD-01–DD-28; AC-01–AC-14; T-01–T-45 at revision 38 | Operative feature/design/acceptance/test statements. Read full current overview/constraints for the affected work. |
| Explicit Atlas relationships | 378 relationships read at revision 38 | Actual requirement/design/AC/test links; do not infer new edges merely because items sound related. |
| [CURRENT_CONTRACT.md](CURRENT_CONTRACT.md) | Compact local snapshot of the preceding current sources | Portable reading aid. Does not replace omitted detailed node clauses or a later authorized source revision. |
| [timecapsule-contract-r38.json](reference/timecapsule-contract-r38.json) | Exact 112 node records and 378 relationships retrieved at revision 38; nine original source records from revision-history after-values | Complete local reference for every member without live MCP. Preserves original bilingual contents, IDs, statuses, source references and relationships. It is not an Atlas import/replacement payload. |
| Current user instructions for this package | 3 November 2026 conversation, including the repository-integration follow-up | Easy English class/function comments; finer comments for unfamiliar code; consistent style; no duplication/spaghetti; explain before/after refactors; each logical change through a GitHub PR; anonymous User F/G/J labels and `user-f`/`user-g`/`user-j` paths. |
| Current Android repository | [Time-Capsule at inspected commit](https://github.com/theguardianofthebed/Time-Capsule/tree/c7bb071cdda302f7562a549d5029e026b4c92a10); main inspected 3 November 2026 KST | Existing structure, instructions, build configuration, and provisional Compose scaffold. These facts establish what is checked in, not what is approved or successfully tested. See [REPOSITORY_STATUS.md](REPOSITORY_STATUS.md). |
| [DESIGN.md](../../DESIGN.md) | Team screen/navigation and design-system document | Current product constraints are source-backed; newly specified Material 3 implementation mappings are explicitly labeled proposals. It does not claim the code exists. |
| [JOURNEYS.md](JOURNEYS.md) | Planned verification journeys using existing source IDs | Execution aid. All initial results are NOT RUN; completing the written plan is not executing a test. |

**Order when project information conflicts:** official course requirements → latest explicit authorized team/user decisions → current approved requirements → current workflow/milestones → original proposal → older notes and AI suggestions. Feasibility evidence informs design; it does not approve scope, a vendor or implementation by itself.

The main course project guide was not attached to this request. The supplement says other features, schedule, code, slides and video follow that guide. Existing project MDs report milestones, but this package does not invent additional official submission dates or claim independent re-verification of the main guide.

## 2 What the professor actually requires

The supplement identifies **Android Studio Agent Mode** as the only permitted agent IDE for project code. Models selectable inside Android Studio may be used; record the chosen model. This package is documentation for that workflow. A document created here is not permission to use a prohibited coding agent later.

| Deliverable/rule | Required placement or ownership from the supplement |
|---|---|
| DESIGN.md | One team design document: screen flow/navigation and Material 3 theme, color, type and components. |
| AGENTS.md | Team file at repository root, committed. |
| Rules | Shared/committed; ensure not excluded by .gitignore, or include the rules in AGENTS.md. |
| Team skills | Root `.agents/skills`, committed. |
| MCP settings | Each member's actual settings belong in their Android Studio settings folder. Shared repository copy: `docs/agent-tools/mcp.redacted.json`, all tokens removed. |
| Secrets | Never commit API keys, access tokens or keystore passwords; list secret files such as local.properties in .aiexclude. |
| Human review | Review every change before acceptance; students remain responsible and must explain any submitted code in Q&A. |
| Agent logs | Individual, one file per week, with separate feature sections for purpose, key prompts, agent changes/verification, errors and manual fixes. |
| Retrospective | Individual `retrospective.md`: what worked, where the agent fell short, what the student can now do independently. |

Planned individual log submissions stated in the supplement: `agent-log-w1.md` by **13 November 2026**, `agent-log-w2.md` by **20 November**, `agent-log-w3.md` by **27 November**, and `agent-log-w4.md` by **4 December**. These are the professor's stated planned dates; follow later instructor announcements if they change.

## 3 Historical project sources that were read

All 17 Markdown files inside `TimeCapsule_Project_Context_2026-10-07_Feasibility_Updated.zip` and both loose October 14 Markdown files were read. They remain useful provenance for roles, scope history and E1 feasibility. They are not instructions to reverse the current Atlas contract.

| Group | Files |
|---|---|
| Context and scope | 00_START_HERE.md; 01_PROJECT_CONTEXT.md; 02_SCOPE_AND_REQUIREMENTS.md |
| Roles/workflow | 03_TEAM_ROLES.md; 04_WORKFLOW_AND_SCHEDULE.md; 05_TODO_AND_DEPENDENCIES.md |
| Agent/project rules | 06_CHATGPT_WORKING_RULES.md; 07_DECISIONS_AND_OPEN_QUESTIONS.md; COPY_PASTE_INSTRUCTION.md |
| Requirements | 08_REQUIREMENTS_BASELINE_V0_1.md |
| Feasibility | 09_FEASIBILITY_OVERVIEW.md; 10_FEASIBILITY_ANDROID_RUNTIME.md; 11_FEASIBILITY_ACCOUNT_AND_DELIVERY.md; 12_FEASIBILITY_MEDIA_AND_PERSISTENCE.md; 13_FEASIBILITY_SPIKE_PLAN.md; 14_FEASIBILITY_RESEARCH_SOURCES.md |
| History | 15_CONTEXT_CHANGELOG.md |
| Later uploaded supplements | 16_TECHNICAL_BASELINE_AND_FEASIBILITY_2026-10-14.md; 17_DESIGN_DECISIONS_AND_ARCHITECTURE_DRAFT_2026-10-14.md |

Lecture references supplied: `5_Android Intents (2).pdf`, `9_Android Permission.pdf`, and `12_Android Sensors.pdf`. The user also reports learning UI Widgets, but no separate UI Widgets slide deck was attached. Lecture examples describe taught mechanisms; they do not add unrelated features or replace the current application contract.

Do not automatically commit copyrighted lecture PDFs or the professor's DOCX to a public repository. The package's own summaries and source names are sufficient for the shared workflow; keep original course files in the team's permitted course location.

## 4 Atlas provenance records

These are **source IDs retained by current nodes**, with their recorded document names. Source metadata was read from the relevant revision history; current-node instructions remain authoritative over historical source text. The exact source record objects are preserved in the frozen JSON's `sourceRecords`, with the retrieval revision in `sourceRecordHistory`: revision 13 after-values for eight records, and revision 11 for SRC-PROTOTYPE-20261031. These were not independently re-fetched as full source bytes at revision 38, and that provenance limit is explicit in the reference.

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

The recorded prototype URL is [TimeCapsule Mobile App Prototype](https://www.figma.com/make/TXMuk2CBolcTz4DAAfJPmT/TimeCapsule-Mobile-App-Prototype). This package used the current Atlas nodes and their recorded provenance; it does not claim to have independently inspected that ZIP or the live Figma project's current internals during this preparation.

Atlas revision 13 applied the normalized contract and revision 14 cleaned superseded IDs. Instructions version 3 explicitly explains that source/constraint text saying an export was “not applied” describes its earlier preparation. Revision 38 was a later snapshot; it does not imply every design was changed in that revision.

## 5 Conflicts already resolved by the current sources

| Older wording | Current source to follow |
|---|---|
| Cross-user delivery is stretch. | FR-01/FR-03 and instructions v3: accounts and self/other delivery are MVP. Replace the stale ChatGPT project instruction. |
| Firebase is only a candidate; cache decisions still open. | DD-11/DD-20 select the Firebase service family; ARC-06/DD-18 choose memory-only Firestore plus explicit UID-private retention. Provisioning/testing remain separate. |
| Use alarm/geofence/boot recovery or background location. | ARC-07/DD-08/DD-24: foreground-only supported triggers; no production background registrations. |
| Use separate D/DD/Q/FLOW identifier families. | Instructions v3: ARC-01–ARC-07 and DD-01–DD-28; preserve current links. |
| Prototype supports multiple photos, name search or simulated unlock. | FR-03/FR-04/DD-07: exact UID confirmation, one original photo, no simulation shortcuts in production. |
| E1 spike passed, so the feature is finished. | Evidence statuses are independent: E1 is isolated feasibility, not E2 application integration or an AC pass. |
| Native wireframe build/model tests establish Firebase/security/device success. | Instructions v3 and T-45 require separate evidence; no inferred production passes. |
| Legacy Korean summary differs from current English detailed contract. | Use current explicitly selected English overview plus instructions v3; record the discrepancy when relevant and do not revive the legacy alternative. |

The current repository introduces one additional discrepancy that is **not resolved by this document update**: a provisional Compose scaffold exists, while the revision-38 contract selects native Views. Do not describe the checkout as already implementing the Views target. A separate reviewed application task must reconcile it. Keep the original scaffold, build files, and directory locations intact until that task. The older open [documentation PR #5](https://github.com/theguardianofthebed/Time-Capsule/pull/5) also needs comparison with the current source register before any later merge.

## 6 Refresh and change discipline

Before a feature starts, the student/agent records the supplied authorized source revision, affected FR/NFR → ARC/DD → AC/T references, Issue, branch and expected behavior. For this package, read `nodeRecords` and follow `relationshipRecords` in the [frozen JSON](reference/timecapsule-contract-r38.json); live MCP is not required to obtain the complete revision-38 statements. If an authorized newer revision is supplied or accessible, check it and update the affected reference/documentation in the same reviewed PR. Do not use a cached count or this date as proof of freshness.

The JSON has a documentation wrapper rather than the Atlas import schema. It contains original records and a contract-guidance excerpt, excluding actor/account membership information, glossary data, tool permissions and unrelated settings. Do not import it into Atlas or use it to replace live data. Reading it is enough to use the documented contract locally; any later Atlas mutation requires its separate authorized workflow.

If two genuinely current sources disagree on a material behavior, state the exact conflicting clauses and continue independent work. Obtain the accountable team's decision before implementing that disputed behavior. Routine coding choices that preserve the contract do not require reopening settled product decisions.

The source register describes decisions and provenance. Real test evidence belongs with the actual build/rules revision, environment, tester/date, expected/observed result and log/artifact link. No approval, test run, production provisioning or PR publication is implied by writing these documents.

## 7 Technical references used for the design-system mapping

These references support platform terminology, not product scope or a Compose migration. Consult the installed dependency's documentation before implementation.

- [Material Components for Android getting started](https://github.com/material-components/material-components-android/blob/master/docs/getting-started.md) — Material 3 themes for native Views.
- [Material typography for Android Views](https://github.com/material-components/material-components-android/blob/master/docs/theming/Typography.md) — reusable typography attributes.
- [Android accessibility for Views](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views) — touch targets and descriptions.

Accessed 3 November 2026 KST. Detailed sizes, role mapping and component choices proposed in DESIGN.md are documentation proposals unless already identified as confirmed by DD-01.

