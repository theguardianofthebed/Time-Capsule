# Issue #13 original photo implementation checkpoint

- Owner: User G. Reviewer: unassigned, confirmed by requester.
- Branch: `feature/13-original-photo`.
- Base: `2228a07f65d8918d59ead727e33fa9fe5c56ab97` (main).
- Head: the sixth implementation commit containing this handoff; resolve with `git rev-parse HEAD`.
- Scope: MVP original-photo selection, private retention/recovery, upload/retry and cleanup seams.
- Contract source: requester explicitly authorized the detailed Issue #13 text on 20 November 2026
  instead of unavailable current full Atlas records. DD-04/DD-25, FR-04, NFR-02/NFR-05,
  AC-02/AC-10/AC-14 and bounded T-05/T-09/T-31/T-36 portions apply.
- Prerequisites: #9/PR #29, #11/PR #35 and #12/PR #36 are merged in the selected base.
  Their recorded checks are predecessor evidence, not new independent integration passes.
- State: implementation prepared; device/backend verification and human review remain pending.
- PR: not opened, as requested for this checkpoint. No merge performed.

## Implementation and verification

Six commits define photo contracts, bounded copying, picker/preview, atomic restart journals,
authenticated frozen upload/cleanup and final regression tests. The ongoing record is
`docs/agent-logs/user-g/agent-log.md`, entries 2026-11-20-01 through 2026-11-20-06.

Final local checks on the sixth commit's implementation tree: 103 JVM tests pass; Debug app and
Android test APK build; lint completes with warnings and no errors. Test APK compilation does not
mean device tests ran. Security-rule JavaScript passes syntax checking only.

## Remaining verification and integration boundaries

- Device picker/preview, provider access revocation, actual decoder behavior, process restart and
  Firebase SDK upload/download/cleanup: NOT RUN. New Android decoder tests are compiled only.
- Four new Storage security-rule cases (including orphan cleanup and empty upload rejection):
  NOT RUN against Firebase Emulator Suite. These remain mandatory verification before acceptance.
- #14 must persist the complete frozen operation before `photoUploads.upload`, await its successful
  acknowledgment before publication, and resolve all outstanding writes before definite cleanup.
  `UNCERTAIN` never allows deletion. No current screen claims successful publication.
- Remote deletion is a best-effort foreground action. Backend denial/failure preserves local retry
  data; no background cleanup service or public media URL is introduced.
- Only editable title/text and photo identity restore; recipient confirmation and lock settings
  require re-entry after process restart. Full protected retained viewing remains #20/#21.
- Storage rules now allow the authenticated owner to delete only unpublished media. Published
  overwrite/deletion remains denied. Re-run the full rule suite before human review/merge.
- Reinspect current upstream work before resuming; retain the original checkout's unrelated edits.

Next action: perform outstanding device/backend checks and obtain an independent teammate review.
No personal phone configuration instructions are included in the repository.
