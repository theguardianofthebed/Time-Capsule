"""Check local dependency evidence without changing Git or contacting services.

This validates declarations and Git ancestry. The acting agent must still inspect
source approvals, actual verification reports, and current remote ownership.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import sys


SHA = re.compile(r"[0-9a-f]{40}")
OWNERS = {"User F", "User G", "User J"}


def meaningful(value):
    """Reject absent fields and unfilled example markers."""
    return isinstance(value, str) and bool(value.strip()) and "REPLACE" not in value


def git_ok(root, *args):
    """Run a read-only Git command using argument boundaries, never a shell."""
    return subprocess.run(["git", "-C", str(root), *args], capture_output=True).returncode == 0


def same_tree(root, first, second):
    """Allow test carry-forward only when both existing commits have equal trees."""
    if not all(isinstance(x, str) and SHA.fullmatch(x) and git_ok(root, "cat-file", "-e", x + "^{commit}") for x in (first, second)):
        return False
    trees = [subprocess.run(["git", "-C", str(root), "rev-parse", x + "^{tree}"], capture_output=True, text=True).stdout.strip() for x in (first, second)]
    return bool(trees[0]) and trees[0] == trees[1]


def graph_errors(manifest):
    """Reject malformed dependencies, duplicate issues, and cycles."""
    errors = []
    if not isinstance(manifest, dict):
        return ["manifest must be an object"]
    if manifest.get("schema_version") != 1 or not isinstance(manifest.get("issues"), list):
        return ["manifest must have schema_version 1 and an issues array"]
    graph = {}
    for item in manifest["issues"]:
        if not isinstance(item, dict):
            errors.append("manifest issue must be an object")
            continue
        number, deps = item.get("number"), item.get("depends_on")
        if type(number) is not int or number <= 0 or number in graph:
            errors.append("invalid or duplicate issue number")
            continue
        if not isinstance(deps, list) or any(type(x) is not int for x in deps) or len(set(deps)) != len(deps):
            errors.append(f"#{number}: invalid prerequisite list")
            continue
        graph[number] = deps
    for number, deps in graph.items():
        for dep in deps:
            if dep not in graph:
                errors.append(f"#{number}: missing prerequisite #{dep}")
    visiting, visited = set(), set()

    def visit(number):
        """Detect a back edge while traversing known graph nodes."""
        if number in visiting:
            errors.append(f"dependency cycle at #{number}")
            return
        if number in visited or number not in graph:
            return
        visiting.add(number)
        for dep in graph[number]:
            visit(dep)
        visiting.remove(number)
        visited.add(number)

    for number in graph:
        visit(number)
    return errors


def evaluate(manifest, issue, evidence, root, now=None):
    """Return all blocking findings for a current implementation claim."""
    errors = graph_errors(manifest)
    if errors:
        return errors
    rows = {x["number"]: x for x in manifest["issues"]}
    if issue not in rows:
        return [f"#{issue}: not in dependency manifest"]
    if evidence.get("issue") != issue:
        errors.append("evidence issue does not match requested issue")
    now = now or datetime.now(timezone.utc)
    try:
        checked = datetime.fromisoformat(evidence["checked_at"].replace("Z", "+00:00"))
        age = (now - checked).total_seconds()
        if not 0 <= age <= 86400:
            errors.append("evidence is future-dated or older than 24 hours")
    except (KeyError, TypeError, ValueError, AttributeError):
        errors.append("checked_at must be an ISO timestamp with timezone")
    base = evidence.get("selected_base", "")
    if not isinstance(base, str) or not SHA.fullmatch(base) or not git_ok(root, "cat-file", "-e", base + "^{commit}"):
        errors.append("selected base must be a full commit available locally")
        base = ""
    if base and not git_ok(root, "merge-base", "--is-ancestor", base, "HEAD"):
        errors.append("selected base is not present in current checkout HEAD")
    branch = evidence.get("branch", "")
    current_branch = subprocess.run(["git", "-C", str(root), "branch", "--show-current"], capture_output=True, text=True).stdout.strip()
    if branch in {"main", "master", ""} or branch != current_branch:
        errors.append("recorded task branch must match current non-default checkout")
    for key in ("owner_confirmation", "branch", "period", "deliverable", "contract_approval"):
        if not meaningful(evidence.get(key)):
            errors.append(f"missing {key}")
    if evidence.get("owner") not in OWNERS:
        errors.append("owner must be explicitly identified as User F/G/J")
    if evidence.get("reviewer") not in OWNERS or evidence.get("reviewer") == evidence.get("owner"):
        errors.append("a different human reviewer must be recorded")
    if evidence.get("scope") not in {"MVP", "approved stretch"}:
        errors.append("scope must be MVP or approved stretch")
    components = evidence.get("components")
    if not isinstance(components, list) or not components or not all(meaningful(x) for x in components):
        errors.append("bounded components/files must be recorded")
    if evidence.get("blocking_failures") != []:
        errors.append("task has blocking failures or missing failure review")
    inventory = evidence.get("inventory", {})
    if not isinstance(inventory, dict):
        inventory = {}
    for key in ("issues", "branches", "prs", "handoffs", "compatibility_evidence"):
        if not meaningful(inventory.get(key)):
            errors.append(f"missing current inventory inspection: {key}")
    if inventory.get("compatible") is not True:
        errors.append("compatibility with active work not established")
    claims = inventory.get("active_claims")
    if not isinstance(claims, list):
        errors.append("active claims inspection missing")
    else:
        for claim in claims:
            if not isinstance(claim, dict) or type(claim.get("issue")) is not int or claim.get("owner") not in OWNERS or not meaningful(claim.get("branch")) or type(claim.get("overlaps")) is not bool:
                errors.append("malformed active claim")
                continue
            same_task = claim["issue"] == issue
            same_owner_branch = claim["owner"] == evidence.get("owner") and claim["branch"] == evidence.get("branch")
            if (same_task and not same_owner_branch) or claim.get("overlaps") is True:
                if not meaningful(claim.get("agreement")):
                    errors.append(f"competing/overlapping claim #{claim['issue']} lacks an explicit agreement")
    records = evidence.get("dependencies")
    if not isinstance(records, list):
        records = []
        errors.append("dependency evidence array missing")
    mapped = {}
    for record in records:
        if not isinstance(record, dict) or type(record.get("issue")) is not int or record["issue"] in mapped:
            errors.append("malformed or duplicate dependency evidence")
        else:
            mapped[record["issue"]] = record
    for dep in rows[issue]["depends_on"]:
        record = mapped.get(dep)
        if not record:
            errors.append(f"#{dep}: prerequisite evidence missing")
            continue
        if record.get("merged") is not True or not meaningful(record.get("pr")):
            errors.append(f"#{dep}: merged implementation PR not established")
        merge = record.get("merge_commit", "")
        if not isinstance(merge, str) or not SHA.fullmatch(merge) or not base or not git_ok(root, "merge-base", "--is-ancestor", merge, base):
            errors.append(f"#{dep}: merge commit is not proven present in selected base")
        if not meaningful(record.get("implementation_inspection")):
            errors.append(f"#{dep}: current implementation inspection evidence missing")
        paths = record.get("paths")
        if not isinstance(paths, list) or not paths:
            errors.append(f"#{dep}: implementation paths missing")
        else:
            for path in paths:
                if not meaningful(path) or path.startswith("/") or ".." in Path(path).parts or not base or not git_ok(root, "cat-file", "-e", f"{base}:{path}"):
                    errors.append(f"#{dep}: implementation path missing from selected base: {path}")
        checks = record.get("checks")
        if not isinstance(checks, list) or not checks:
            errors.append(f"#{dep}: relevant verification evidence missing")
        else:
            for check in checks:
                if not isinstance(check, dict) or check.get("result") != "PASS" or not all(meaningful(check.get(k)) for k in ("procedure", "expected_actual", "environment", "evidence")):
                    errors.append(f"#{dep}: required verification not passed/documented")
                    continue
                commit = check.get("commit", "")
                ancestor = isinstance(commit, str) and bool(SHA.fullmatch(commit)) and bool(base) and git_ok(root, "merge-base", "--is-ancestor", commit, base)
                equivalent = isinstance(merge, str) and bool(SHA.fullmatch(merge)) and bool(base) and git_ok(root, "merge-base", "--is-ancestor", merge, base) and same_tree(root, commit, merge)
                if not (ancestor or equivalent):
                    errors.append(f"#{dep}: verification commit is neither in selected base nor tree-equivalent to its prerequisite merge")
        if record.get("blocking_failures") != []:
            errors.append(f"#{dep}: unresolved blocking failure or missing review")
    return errors


def main():
    """Print a machine-readable outcome and exit nonzero for any blocker."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default="docs/agent-tools/dependencies.json")
    parser.add_argument("--validate-manifest", action="store_true")
    parser.add_argument("--issue", type=int)
    parser.add_argument("--evidence")
    args = parser.parse_args()
    try:
        manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
        if args.validate_manifest:
            errors = graph_errors(manifest)
            status = "MANIFEST_VALID" if not errors else "BLOCKED"
        else:
            if not args.issue or not args.evidence:
                raise ValueError("--issue and --evidence are required")
            evidence = json.loads(Path(args.evidence).read_text(encoding="utf-8"))
            if not isinstance(evidence, dict):
                raise ValueError("evidence must be an object")
            errors = evaluate(manifest, args.issue, evidence, Path.cwd())
            status = "PREFLIGHT_RECORD_VALID" if not errors else "BLOCKED"
    except (OSError, ValueError, TypeError, KeyError) as exc:
        errors, status = [str(exc)], "BLOCKED"
    print(json.dumps({"status": status, "blockers": errors}, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
