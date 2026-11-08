# Android Studio and GitHub setup

Use Android Studio Agent Mode for project code. Each member keeps real MCP settings and credentials in their own Android Studio settings directory. The shared, token-free example is [mcp.redacted.json](mcp.redacted.json).

GitHub MCP is one way to publish branches and PRs. An existing authenticated GitHub integration or CLI can also support the workflow; a configuration example alone does not provide access.

## Make the shared instructions available

Open the repository root in Android Studio. Confirm that the agent reads `AGENTS.md` and can use the skills in `.agents/skills/`. Select the required skill with the IDE's context/skill picker, or attach its file explicitly if discovery fails. Record the actual IDE version, selected model, and any discovery problem in your log.

Keep IDE Rules and nested instruction files consistent with root `AGENTS.md`. A same-directory `GEMINI.md` takes precedence in Android Studio, and IDE Rules are combined with agent-file instructions. Resolve conflicts rather than maintaining contradictory copies. Use a course-compatible Android Studio version that supports the required shared files. See [SETUP.md](SETUP.md).

## Configure GitHub MCP locally

1. Open **Settings → Tools → AI → MCP Servers** in Android Studio and enable MCP servers. Use the Settings UI to reach the IDE's configuration directory; do not guess a platform-specific path.
2. Copy the structure from `docs/agent-tools/mcp.redacted.json` into your local settings.
3. Replace the placeholder with your own authorized credential **only in those local settings**. Never put a real token in the tracked example, even temporarily.
4. Limit the credential to the TimeCapsule repository and the read, push, Issue, and PR operations you need. Do not grant administration, workflow-management, organization-wide, or unrelated repository access merely to work around an unclear error.
5. Save and inspect the tools offered by the server. Start with a read-only check of `theguardianofthebed/Time-Capsule`, its target branch (`main`), and a known Issue. Do not print authorization headers.

The example uses Android Studio's `httpUrl` field with GitHub's official endpoint, `https://api.githubcopilot.com/mcp/`. Its `X-MCP-Toolsets` header selects `repos,issues,pull_requests`. These choices limit the tools requested; credential permissions and human review still apply.

The endpoint's name does not permit another coding agent. Use ordinary repository and PR operations. **Do not call `create_pull_request_with_copilot` or any equivalent tool that delegates coding to Copilot.** Project code must be produced in the professor's allowed environment.

## Check a real PR

During an authorized task, review the diff, publish its branch, and open an ordinary PR. Check the returned URL, repository, base/head branches, files, and description. Another teammate reviews the PR before a human merges.

If MCP cannot publish, preserve the branch and complete PR description. Report the specific error and use an available authorized IDE integration or CLI if appropriate. If a member opens the PR manually, record that step. Until the real PR exists, report **PR not opened**.

Keep tokens out of prompts, logs, screenshots, PRs, and repository files. If a token is exposed, its owner must revoke or rotate it; an ignore rule does not remove a committed secret from Git history.

## Record your setup

In your own weekly log, record the IDE version/model, how you checked instruction and skill discovery, the GitHub connection used, the repository/branch checked, and the actual PR URL or access blocker. Do not claim another member's setup is working without checking it.

Update the shared redacted example through a PR when its structure changes. Keep each member's credentials local.

## References

- Professor supplement, Agent Usage Rules 1–6.
- [Android Studio agent files](https://developer.android.com/studio/gemini/agent-files), [skills](https://developer.android.com/studio/gemini/skills), [MCP configuration](https://developer.android.com/studio/gemini/add-mcp-server), and [context exclusions](https://developer.android.com/studio/gemini/aiexclude).
- [GitHub remote MCP server and toolsets](https://github.com/github/github-mcp-server/blob/main/docs/remote-server.md).
