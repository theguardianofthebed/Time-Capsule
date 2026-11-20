# User G agent work log

Agents append factual task entries here only after the requester explicitly identifies **User G** as the owner. Follow the [entry guide and template](../README.md). Preserve earlier entries, record actual checks and remaining work, and keep human review or learning claims separate.

Weekly coursework remains in `agent-log-w1.md`–`agent-log-w4.md`.

## Entries

## 2026-11-20-01 — Issue #13: original photo contracts (step 1 of 6)

- **Owner:** User G
- **Agent environment:**
- **Model used:**
- **Status:** Partial; first implementation checkpoint.
- **Requested outcome:** Implement Issue #13 in approximately six commits and stop after each
  commit to report. Device testing waits until the user connects an Android phone.
- **References:** Issue #13; DD-04, DD-25, FR-04; AC-02, AC-10, AC-14.

### Work performed

- Started `feature/13-original-photo` from main commit
  `2228a07f65d8918d59ead727e33fa9fe5c56ab97` in a separate checkout to preserve existing edits.
- The requester authorized using the detailed Issue #13 text as the implementation source
  because current full Atlas records are unavailable through this session's tools.
- Defined JPEG/PNG formats, the inclusive 1–5,242,880-byte limit, retained original metadata,
  frozen upload input and upload acknowledgment. These types do not claim publication.
- Reviewed the existing S-04 picker placeholder, draft state and Storage interface/rules.
  Prerequisite PRs #35 and #36 are merged. A different teammate reviewer is still unassigned.

### Checks and results

- Source/diff inspection: completed for this domain-only checkpoint.
- Build and unit tests: NOT RUN in this checkpoint; executable media behavior is not yet added.
- Android device tests: NOT RUN; awaiting the user's phone connection.

### Human input and remaining work

- The requester approved six real implementation checkpoints and per-commit reports.
- Personal review/understanding is pending. No manual source edits by the user are claimed.
- Next: bounded copying, hash calculation and actual-format validation, followed by picker,
  recovery, upload and focused tests. Remote orphan deletion needs reconciliation with current
  Storage rules, which deny client deletion. No deletion success is claimed.

## 2026-11-20-02 — Issue #13: bounded original retention (step 2 of 6)

- **Owner:** User G
- **Agent environment:**
- **Model used:**
- **Status:** Partial; second implementation checkpoint.
- **Requested outcome:** Continue to the next step and stop after its commit.
- **References:** Issue #13; DD-04, DD-25; T-05, T-31 (bounded local portions).

### Work performed

- Added `OriginalPhotoStore` to stream one original into identity-scoped directories, count actual
  bytes, enforce 1–5,242,880 bytes and compute SHA-256 without transforming the original.
- Added `AndroidOriginalImageProbe` to check decoded MIME/dimensions and decode a sampled preview.
- Failed replacements preserve the previous file; read failures, revoked access and cancellation
  propagate to the caller and remove this attempt's incomplete candidate.
- Added eight focused JVM tests for exact-boundary preservation/hash, oversized/empty input,
  MIME mismatch, unreadable input, revoked access, failed replacement and cancellation.

### Checks and results

- `:app:testDebugUnitTest --tests '*OriginalPhotoStoreTest'`: PASS, eight tests, zero failures.
- `:app:assembleDebug`: PASS. Existing deprecation/native-symbol warnings did not block the build.
- `git diff --check`: PASS.
- Initial sandboxed Gradle attempt could not open its cache lock; retry with authorized cache
  access succeeded.
- JVM tests inject an image probe: they verify copying and failure behavior, not Android decoding.
  Real JPEG/PNG decoding, picker and device lifecycle checks remain NOT RUN pending phone access.

### Human input and remaining work

- Reviewer remains unassigned as confirmed by the user. Human review/understanding is pending.
- These components are not yet wired to the screen or upload path. Next: picker and preview wiring
  with the store rooted under Android's non-backed-up private directory, then persistent recovery.
