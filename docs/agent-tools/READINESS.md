# Implementation readiness

A task is READY only when all eight gates below have current evidence. Any missing, contradictory, failed, or stale evidence leaves it BLOCKED. Reading, design preparation, and test planning may continue while blocked. Do not change a dependency merely to make a task appear ready.

| Gate | Evidence to record before coding |
|---|---|
| Approved contract | Exact FR/NFR, ARC/DD, AC/T records, source revision, and approval/confirmed-decision reference. |
| Predecessors merged | Each direct prerequisite from [dependencies.json](dependencies.json), merged PR URL, and merge commit. Closed Issues and unmerged PRs do not qualify. |
| Present in selected base | Full base commit, ancestor check for each prerequisite merge, and concrete implementation paths in that base. Inspect behavior and later reverts as well as file presence; record `implementation_inspection` for every predecessor. A reverted implementation can still have an ancestor merge and surviving file paths. |
| Relevant verification | Check/procedure, expected and actual result, tested commit/build, environment/date, evidence location, and remaining limits. An isolated spike is not integrated acceptance. |
| No blocking failure | Current failures/review findings checked; no unresolved blocker within the prerequisite or current task boundary. |
| No competing implementation | Fresh inspection of Issues, branches, open PRs, and handoffs. Record existing claims and any explicit sharing/transfer agreement. |
| Owner and boundary | Explicit User F/G/J, active branch, period, MVP/stretch classification, components/files, deliverable, and a different human reviewer. |
| Compatible active work | Compare current PR boundaries, shared interfaces/files, and handoffs. Resolve overlapping changes with owners before implementing that boundary. |

## Repeatable preflight

1. Fetch/read current repository state through the authorized integration. Read the selected base, relevant Issue/PR discussion, contract, [implementation order](IMPLEMENTATION_ORDER.md), and active handoffs. Repeat after a base, contract, dependency, ownership, or conflicting-PR change.
2. Record the eight gates in the Issue and [task handoff](handoffs/TEMPLATE.md). Use `readiness-evidence.example.json` as a structural starting point, replacing every placeholder with observed evidence. Do not commit private data or credentials.
3. Run from the Git root:

```bash
python3 tools/check_readiness.py --validate-manifest
python3 tools/check_readiness.py --issue 10 --evidence /path/to/current-evidence.json
```

The command returns nonzero and names blockers when input is absent, malformed, stale, or contradictory, when a required merge is outside the selected base, or when prerequisite paths are missing. Evidence inspection must be within 24 hours of running this preflight. This is a team freshness rule, not a course requirement. Recheck immediately after any material change even within that window.

For a squash/rebase merge, the original tested PR commit may not be an ancestor of the base. The validator also accepts an existing tested commit whose complete Git tree exactly equals the prerequisite merge tree, provided that merge is present in the selected base. Record the actual tested commit and check that the test environment, configuration, and later changes still make its evidence applicable. Tree equality does not validate external services or prevent a later revert. If trees differ, or the evidence no longer applies, run the relevant checks on the merged base; do not substitute an invented commit.

4. Record the output and timestamp. Publish the bounded claim, then re-read current claims before coding. An existing claim by another owner or branch needs an explicit shared-boundary/transfer agreement. Do not infer that silence releases a task. Keep an interrupted task claimed until the owner records a transfer, completion, or release.
5. Continue implementation only after both the machine checks and the agent's semantic review of all evidence pass. Keep the course restriction: Android Studio Agent Mode for application coding, student review of generated changes, different-teammate PR review, and human merge.

## What the automation does and does not enforce

[The validator](../../tools/check_readiness.py) checks the dependency graph, evidence structure and recency, local Git ancestry, prerequisite paths, and declared ownership conflicts. It reads local files and Git only; it performs no write, network request, claim, merge, deployment, or approval. It validates declared records, not the truth of a test report or GitHub approval. The agent must independently read those sources. The snapshot's status fields are descriptive and cannot substitute for current task evidence.

This is **agent-side preflight**, not CI enforcement or an atomic work lock. No CI workflow or branch-protection rule is installed by this change. Documentation and a local script cannot physically prevent someone coding or bypassing the process. Current branch-protection configuration has not been verified; limited API access must not be interpreted as proof that protection is absent. A future required CI gate needs explicit repository configuration and testing before anyone calls it enforced.

## Known example

PR #29 merged at 00:56 KST on 10 November 2026 into main commit `c60d866cdc9794af97db129c946b66711270f6b9`. Issue #9 remains open. PR #29 records build checks and 25 passing unit tests. Issue #10 remains BLOCKED pending review of whether that evidence covers #9’s bounded foundation responsibilities, classification of the announced but unspecified follow-up problem, and confirmation of #10’s acting owner/different reviewer and current preflight. Require emulator integration evidence only where needed to verify #9’s bounded behavior; full downstream backend feature acceptance is not a blanket prerequisite. The merge satisfies the merge gate only. Re-read #9, #29, their latest checks, and the selected base before changing readiness.
