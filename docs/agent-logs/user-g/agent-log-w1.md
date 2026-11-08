# User G agent log week 1

The planned submission date is **13 November 2026**; follow any later professor announcement. Add one feature/task section for each piece of work. Describe your own contribution and use real prompts, results, and examples. If you did not use an agent this week, say so honestly.

Follow [the shared workflow](../../agent-tools/WORKFLOW.md) and [AGENTS.md](../../../AGENTS.md). Remove credentials and private data from any quoted material.

## Agent workflow documentation review and restructuring

- **Task and purpose:** Review the professor's agent-usage requirements and simplify the repository's shared agent workflow without changing application code.
- **Session date(s):** 7 November 2026.
- **Issue and PR:** Issue #7; https://github.com/theguardianofthebed/Time-Capsule/pull/8
- **Requirement/design references:** Professor's `Term_Project_Agent_Usage_Supplement.docx`; `AGENTS.md`; `CURRENT_CONTRACT.md`; `WORKFLOW.md`; `CODING_AND_COMMENTS.md`.
- **Agent environment:** Codex desktop app, used for repository documentation only. No Android application code was changed in this task.
- **Model used:** Exact displayed model name was not recorded. Gemini 3.6 Flash was discussed but was not used for this documentation session.

### Purpose of agent use

I used the agent to inspect the existing Rules, Skills, individual weekly logs, retrospectives, ignore settings, and MCP example, then compare their structure with the professor's supplement. I decided that the weekly log should be prepared automatically after each task, that the mandatory workflow should have four core documents, and that the existing KDoc rule should remain.

### Key prompts

- Check whether `AGENTS.md`, Rules, Skills, three students' weekly logs, and retrospectives are already structured.
- Explain the existing `AGENTS.md` and identify strengths, gaps, and differences from the professor's requirements.
- Make the agent prepare the weekly log automatically, while asking before recording personal review, manual fixes, understanding, or learning.
- Reduce the mandatory document structure to `AGENTS.md`, `CURRENT_CONTRACT.md`, `WORKFLOW.md`, and `CODING_AND_COMMENTS.md`.
- Keep the KDoc requirement.
- Keep real MCP settings private and retain only the professor-required token-free example in the repository.

### What the agent changed and verified

- **Changes:** Simplified `AGENTS.md`; assigned product and technical decisions to `CURRENT_CONTRACT.md`; assigned Issue, PR, verification, automatic logging, and retrospective procedures to `WORKFLOW.md`; retained KDoc and code-explanation rules in `CODING_AND_COMMENTS.md`; updated the four shared Skills and documentation index to use the four core documents.
- **Checks and results:** `git diff --check` passed; all checked local Markdown links resolved; `mcp.redacted.json` parsed as valid JSON; sensitive-file patterns were confirmed in `.aiexclude` and `.gitignore`.
- **Remaining checks:** A teammate still needs to review the published PR. This documentation task did not run an Android build or application test because it did not change application code.

### Errors and manual fixes

The first attempted whole-file replacement of `AGENTS.md` was rejected because it would temporarily remove the shared safety instructions. The agent changed to an atomic in-place update, so the repository was never left without `AGENTS.md`. No student-authored manual file edit was recorded during this session.

### My review and understanding

- **What I reviewed:** I reviewed the professor's required deliverables and approved three documentation decisions: automatic weekly log preparation, four core workflow documents, and retention of the KDoc rule. I also checked why the token-free MCP example is required while real MCP credentials remain private.
- **What I can explain:** I can distinguish the four core documents by purpose and distinguish a private MCP configuration from the committed token-free example.
- **Feedback and next step:** Publish this documentation branch as a PR, request review from Junwon and Flora, then configure Android Studio Agent Mode and verify that it reads the four core documents and Skills.
