# Agent work logs

Each contributor has one ongoing record of what agents did for them. An agent updates this record as part of its task so teammates can understand the change, the reason for it, and what still needs checking.

| Confirmed task owner | Ongoing log |
|---|---|
| User G | [user-g/agent-log.md](user-g/agent-log.md) |
| User F | [user-f/agent-log.md](user-f/agent-log.md) |
| User J | [user-j/agent-log.md](user-j/agent-log.md) |

## Before starting

Use the owner explicitly supplied for the current task. If the requester has not identified the owner, ask **“Who owns this agent task: User G, User F, or User J?”** Wait for the answer before making changes or publishing work. Read-only preparation may continue. Do not infer ownership from a name, account, role, branch, or another person's earlier entry.

Keep that owner for follow-ups and delegated agents within the same task unless the human changes it. Read the owner's existing log before appending. Never move a task into another contributor's log merely because their account published the commit.

## What the agent records

Start one entry per coherent task, using a unique heading such as `2026-11-03-01 — Explain capsule retry behavior`. The date uses Asia/Seoul time; choose the next unused suffix for that date. If parallel branches choose the same ID, give one entry a new unused ID when combining them and update its references; preserve both records. Start with the goal and `In progress`, then update the entry before each handoff. Record implementation, investigation, review, documentation, configuration, issue work, and failed or blocked attempts.

Use short, clear sentences. Explain what changed or was learned and why a teammate should care before listing technical details. For example: “A retry now keeps the original capsule ID, which prevents a second capsule when the first reply is lost.” Do not claim that example happened unless it actually did.

- Record actual files, Atlas element IDs, issue numbers, or other affected records. Use plain IDs for Atlas references unless a linking action was explicitly requested.
- Name the actual environment and displayed model when available. Use `not exposed` if a model name is unavailable; do not invent it.
- State the check, observed result, and relevant evidence. Distinguish `PASS`, `FAIL`, `NOT RUN`, and `BLOCKED`.
- Separate agent work from human decisions, manual fixes, review, and personal learning. Leave unknown human activity unclaimed.
- For delegated work, name each contribution in the same task entry and identify which agent/environment performed it. Keep unrelated concurrent tasks in separate entries and preserve both when resolving conflicts.
- Keep credentials, private data, raw tool dumps, and hidden reasoning out of the log. A brief prompt summary is enough.

## Entry template

Append this template to the confirmed owner's `agent-log.md` and replace the placeholders with facts. Do not create sample completed entries.

```markdown
## YYYY-MM-DD-NN — Short task title

- **Owner:** User G / User F / User J (choose the confirmed owner)
- **Agent/environment/model:** Actual values, or not exposed
- **Status:** In progress / Completed / Blocked / Partial
- **Requested outcome:** What the contributor asked for and why
- **References:** Real Issue/PR links and relevant files or Atlas IDs

### Work performed

- Explain the actual change or finding and its purpose in plain language.
- Identify the important files or records. Include delegated contributions.

### Checks and results

- State the check, actual result, and evidence.
- State anything not run, failed, or blocked and why.

### Human input and remaining work

- Record only confirmed human decisions or manual fixes; otherwise say not recorded.
- State the next action, any remaining risk or question, and the responsible person if known.
```

## Keep the record trustworthy

Complete the factual entry before a handoff or completion report and link it in the task PR. Add the actual PR URL after publication; never predict an issue or PR number. Keep logs in the same task branch and PR as the related change. A log-only change still follows the normal PR process.

Preserve earlier entries. Add a dated correction when an earlier fact needs fixing, and explain what was corrected. Do not overwrite another task's entry or another contributor's record.

The `agent-log-w1.md`–`agent-log-w4.md` files are individual weekly coursework forms. Students use the ongoing work record as factual input and write their own review, understanding, and reflections. An agent must not fill those with fictional manual work or learning claims. See [AGENTS.md](../../AGENTS.md), [WORKFLOW.md](../agent-tools/WORKFLOW.md), and [course requirements](../agent-tools/COURSE_COMPLIANCE.md).
