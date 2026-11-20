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

## 2026-11-20-03 — Issue #13: picker and private preview (step 3 of 6)

- **Owner:** User G
- **Agent environment:**
- **Model used:**
- **Status:** Partial.
- **Requested outcome:** Continue through steps 3–6 with a separate commit per step.
- **Work performed:** Connected API 33+ Photo Picker and API 26–32 OpenDocument to bounded private
  retention. Added sampled preview, progress gating and stale-selection rejection in S-04.
- **Checks:** Source review and whitespace check; device picker/decoder checks NOT RUN until phone
  connection. Build validation is recorded in the final checkpoint.
- **Remaining:** Restart recovery, authenticated upload, failure tests and device verification.
- **Human review/understanding:** Pending; reviewer not assigned.

## 2026-11-20-04 — Issue #13: private restart recovery (step 4 of 6)

- **Owner:** User G
- **Agent environment:**
- **Model used:**
- **Status:** Partial.
- **Requested outcome:** Continue the previously authorized six-step implementation.
- **Work performed:** Added atomic UID-scoped draft journals outside Android backup. The same
  capsule ID, title, text and original-photo metadata restore without relying on the original URI.
  Recovery checks retained size/hash; recipient/lock choices must be re-established after restart.
  Serialized saves and account/request tokens prevent obsolete results from replacing active state.
- **Checks:** Debug build and existing JVM tests PASS before final serialization review; final
  checkpoint reruns relevant checks. Device/process-death behavior NOT RUN pending phone access.
- **Remaining:** Upload ACK/retry boundary, cleanup lifecycle and regression coverage.
- **Human review/understanding:** Pending.
