# Android Studio and GitHub tool setup

**Owner:** each student for personal authentication; User J coordinates reproducibility. **When:** before the student's first agent-created PR. **Dependency:** access to the TimeCapsule Android repository. **Scope:** shared development tooling supporting the MVP.

The professor requires actual MCP settings to stay in each member's Android Studio settings folder and one token-free shared copy at `docs/agent-tools/mcp.redacted.json`. The supplied JSON is a **proposed configuration template**, not evidence that a connection or credential is already installed. MCP is an optional route for repository operations; a usable authenticated GitHub workflow is needed to satisfy the team's PR rule.

## 1. Keep the coding agent inside Android Studio

Open the Android application project in Android Studio and use Agent Mode. Record the actual Android Studio version and selected model in the student's log. Do not install another coding-agent IDE to follow this guide.

Keep root `AGENTS.md` and the team skills committed. Verify that the agent's context includes the root instructions. If Android is a subdirectory of the Git repository, keep the shared package and `.github` at the Git root, record the Android build working directory, and explicitly attach the root instructions/chosen skill when automatic discovery is unavailable. Check existing `GEMINI.md`, nested `AGENTS.md`, and IDE Rules for contradictions: Android Studio documents that a same-directory `GEMINI.md` takes precedence and that Rules are combined with agent-file instructions. Merge stale rules into the shared policy during the setup PR rather than maintaining conflicting copies.

The professor specifies `.agents/skills/`. Current Android Studio documentation supports that path; older releases used different locations. Use a course-compatible version that recognizes the required files. If discovery fails, attach the same shared skill/instructions explicitly for the session and record the limitation while fixing version/discovery settings; do not silently rename the required committed deliverables.

## 2. Configure the optional GitHub MCP connection locally

Use **Settings → Tools → AI → MCP Servers** in Android Studio (the macOS application menu provides Settings). The IDE stores actual settings in its version-specific configuration directory. Use the settings UI instead of guessing a Windows or macOS path.

1. Enable MCP servers if using this route.
2. Copy the structure of `mcp.redacted.json` into the local settings field.
3. Replace the obvious placeholder with the member's own authorized credential **in that local settings field only**. Never edit the tracked redacted copy to hold a token, even briefly.
4. Restrict the credential to the intended repository and operations. Repository read/push, Issue operations used by the workflow, and PR creation require corresponding access; do not add administration, workflow-management, organization-wide, or unrelated repository access merely to resolve a vague error.
5. Save the local configuration and inspect the available tools. Start with a read-only check of the intended repository, default branch, and a known Issue. Do not print credential headers.

The example uses Android Studio's documented `httpUrl` configuration field. Its GitHub toolset header selects ordinary repository, Issue, and PR tooling. Check the actual tools exposed by your installed versions before use; toolset selection does not replace credential permissions or human review.

**Why the URL contains “githubcopilot”:** that is GitHub's official hosted MCP endpoint. Calling ordinary GitHub repository tools from Android Studio is a tool integration. The professor's ban on other coding agents still applies: never call `create_pull_request_with_copilot`, assign coding work to Copilot, or use an equivalent agent-delegation tool. Use the ordinary PR creation operation for code produced and reviewed in Android Studio.

The repository identifier recorded in prior team context is `theguardianofthebed/Time-Capsule`. Verify it against this checkout's actual remote before any write. The inspected default branch is `main`; verify the current remote and target branch before writes. This shared example does not prove that each member's personal connection has write access or is configured.

## 3. Verify PR publication without confusing it with implementation

Use the document setup PR as the first practical check. The member reviews and accepts the document changes, pushes the setup branch, then has the Android Studio agent open an ordinary PR. Verify the returned URL, base branch, head branch, changed files, and body in GitHub. Another teammate reviews it before a human merges.

If MCP cannot publish, the agent should give a concrete diagnostic and preserve the prepared branch and PR body. Use an existing authenticated GitHub integration or GitHub CLI from the Android Studio workflow if available; do not ask another coding agent to recreate the code. If the member must open the PR manually, record that manual step and keep publication blocked until the actual PR URL exists.

No token should be pasted into a chat prompt, weekly log, PR, screenshot, or repository file. If a token was committed, merely adding an ignore rule does not remove it from history: the credential owner must revoke/rotate the exposed token and handle repository cleanup through the team's review process.

## 4. Record the real setup state

Each student records the following in their own weekly log after performing setup:

| Field | Record after verification |
|---|---|
| Android Studio and model | Actual version and selected model name |
| Root instructions | How inclusion was checked |
| Skill discovery | Which team skill was successfully invoked |
| GitHub route | MCP, existing IDE integration, or authenticated CLI |
| Repository and base branch | Actual verified values |
| Read check | What was read and result, without secrets |
| Publication check | Actual setup PR URL, or exact blocker |

Do not check off another member's setup or claim all three environments are identical without verifying them. When adding or changing a server later, update the shared redacted example in a PR; keep credentials and member-specific settings local.

## References checked 3 November 2026

- Professor supplement, Agent Usage Rules 1–6.
- [Android Studio agent files](https://developer.android.com/studio/gemini/agent-files).
- [Android Studio skills](https://developer.android.com/studio/gemini/skills).
- [Android Studio MCP configuration](https://developer.android.com/studio/gemini/add-mcp-server).
- [GitHub remote MCP server and toolsets](https://github.com/github/github-mcp-server/blob/main/docs/remote-server.md).
- [Android Studio context exclusions](https://developer.android.com/studio/gemini/aiexclude).
