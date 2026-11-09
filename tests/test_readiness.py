"""Exercise readiness failures with a disposable Git repository."""
from datetime import datetime, timezone, timedelta
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("readiness", Path(__file__).parents[1] / "tools/check_readiness.py")
readiness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(readiness)


class ReadinessTest(unittest.TestCase):
    """Verify fail-closed behavior without network or production mutations."""

    def setUp(self):
        """Create a local merged-baseline fixture and complete evidence."""
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        def git(*args):
            """Run Git only in this disposable fixture."""
            return subprocess.check_output(["git", "-C", str(self.root), *args], stderr=subprocess.DEVNULL, text=True).strip()
        self.git = git
        git("init", "-b", "feature/10-accounts")
        git("config", "user.name", "Test Contributor")
        git("config", "user.email", "test@example.invalid")
        (self.root / "foundation.txt").write_text("integrated foundation")
        git("add", ".")
        git("commit", "-m", "fixture baseline")
        self.sha = git("rev-parse", "HEAD")
        self.now = datetime(2026, 11, 10, tzinfo=timezone.utc)
        self.manifest = {"schema_version": 1, "issues": [{"number": 9, "depends_on": []}, {"number": 10, "depends_on": [9]}]}
        self.evidence = {
            "issue": 10, "checked_at": self.now.isoformat(), "selected_base": self.sha,
            "owner": "User J", "owner_confirmation": "requester selected User J",
            "reviewer": "User G", "branch": "feature/10-accounts", "period": "10 November",
            "scope": "MVP", "components": ["accounts"], "deliverable": "account flow",
            "contract_approval": "approved current account record", "blocking_failures": [],
            "inventory": {"issues": "inspected #10", "branches": "inspected all", "prs": "inspected all open",
                "handoffs": "none observed", "compatible": True, "compatibility_evidence": "no shared files/interfaces",
                "active_claims": []},
            "dependencies": [{"issue": 9, "pr": "fixture merged PR", "merged": True,
                "merge_commit": self.sha, "implementation_inspection": "inspected current foundation behavior and later reverts", "paths": ["foundation.txt"], "blocking_failures": [],
                "checks": [{"result": "PASS", "procedure": "foundation fixture check", "expected_actual": "expected present; observed present",
                    "commit": self.sha, "environment": "local fixture", "evidence": "fixture report"}]}]
        }

    def tearDown(self):
        """Remove only the temporary test repository."""
        self.temp.cleanup()

    def findings(self):
        """Evaluate the fixture at a fixed instant."""
        return readiness.evaluate(self.manifest, 10, self.evidence, self.root, self.now)

    def test_complete_record_passes(self):
        """A complete evidenced local record passes structural checks."""
        self.assertEqual([], self.findings())

    def test_draft_pr_cannot_satisfy_dependency(self):
        """Unmerged work remains blocking even if its Issue is closed."""
        self.evidence["dependencies"][0].update(merged=False, issue_closed=True)
        self.assertTrue(any("merged implementation" in x for x in self.findings()))

    def test_unmerged_commit_outside_base_blocks(self):
        """A locally existing commit outside the selected base is insufficient."""
        (self.root / "future.txt").write_text("future")
        self.git("add", ".")
        self.git("commit", "-m", "future fixture")
        self.evidence["dependencies"][0]["merge_commit"] = self.git("rev-parse", "HEAD")
        self.assertTrue(any("not proven present" in x for x in self.findings()))

    def test_unmerged_verification_commit_blocks(self):
        """A passing report from a commit outside the selected base is insufficient."""
        (self.root / "future.txt").write_text("future")
        self.git("add", ".")
        self.git("commit", "-m", "future check fixture")
        self.evidence["dependencies"][0]["checks"][0]["commit"] = self.git("rev-parse", "HEAD")
        self.assertTrue(any("verification commit" in x for x in self.findings()))

    def test_equivalent_squash_test_commit_passes(self):
        """A tested commit with exactly the merge tree can carry evidence forward."""
        tree = self.git("rev-parse", self.sha + "^{tree}")
        tested = self.git("commit-tree", tree, "-m", "tested squash source")
        self.evidence["dependencies"][0]["checks"][0]["commit"] = tested
        self.assertFalse(readiness.git_ok(self.root, "merge-base", "--is-ancestor", tested, self.sha))
        self.assertEqual([], self.findings())

    def test_different_squash_tree_blocks(self):
        """An unrelated tested tree cannot carry evidence to the merge."""
        (self.root / "foundation.txt").write_text("different behavior")
        self.git("add", ".")
        tree = self.git("write-tree")
        tested = self.git("commit-tree", tree, "-m", "different source")
        self.evidence["dependencies"][0]["checks"][0]["commit"] = tested
        self.assertTrue(any("verification commit" in x for x in self.findings()))

    def test_missing_current_behavior_inspection_blocks(self):
        """An ancestor and file path alone cannot establish behavior after a revert."""
        del self.evidence["dependencies"][0]["implementation_inspection"]
        self.assertTrue(any("implementation inspection" in x for x in self.findings()))

    def test_stale_inspection_blocks(self):
        """Prior READY evidence expires rather than silently carrying forward."""
        self.evidence["checked_at"] = (self.now - timedelta(hours=25)).isoformat()
        self.assertTrue(any("older than 24" in x for x in self.findings()))

    def test_missing_file_or_failed_check_blocks(self):
        """A merge without relevant implementation/evidence cannot unlock work."""
        self.evidence["dependencies"][0]["paths"] = ["missing.txt"]
        self.evidence["dependencies"][0]["checks"][0]["result"] = "FAIL"
        findings = self.findings()
        self.assertTrue(any("path missing" in x for x in findings))
        self.assertTrue(any("not passed" in x for x in findings))

    def test_competing_claim_blocks(self):
        """A second owner/branch needs an explicit sharing or transfer agreement."""
        self.evidence["inventory"]["active_claims"] = [{"issue": 10, "owner": "User F", "branch": "feature/other", "overlaps": True}]
        self.assertTrue(any("competing/overlapping" in x for x in self.findings()))

    def test_wrong_branch_and_unknown_owner_block(self):
        """Do not approve evidence belonging to another checkout or guessed owner."""
        self.evidence["branch"] = "main"
        self.evidence["owner"] = "repository-account"
        self.assertGreaterEqual(len(self.findings()), 2)

    def test_cycle_and_malformed_manifest_block(self):
        """Invalid dependency data is never accepted as an empty graph."""
        self.manifest["issues"][0]["depends_on"] = [10]
        self.assertTrue(any("cycle" in x for x in readiness.graph_errors(self.manifest)))
        self.assertTrue(readiness.graph_errors([]))

    def test_missing_dependency_and_inventory_block(self):
        """Empty records cannot grant readiness by omission."""
        self.evidence["dependencies"] = []
        self.evidence["inventory"] = {}
        self.assertTrue(any("prerequisite evidence missing" in x for x in self.findings()))
        self.assertTrue(any("inventory" in x for x in self.findings()))


if __name__ == "__main__":
    unittest.main()
