# Task handoff: Issue #16 S-05 Google Maps target selection & fresh foreground location adapter (PR #42 Test-Coverage Cleanup)

- **Confirmed owner:** User J (explicitly confirmed by requester)
- **Scope / period / different human reviewer:** MVP / 23 November 2026 / User G (reviewer)
- **Issue / PR:** Issue #16 / PR #42 (https://github.com/theguardianofthebed/Time-Capsule/pull/42)
- **Branch / base / head:** `feature/16-map-foreground-location` / Base `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff` / Head `feature/16-map-foreground-location`
- **State:** IN_PROGRESS (PR #42 test-coverage cleanup applied and verified)
- **Boundary:** S-05 Google Maps target selection UI, pure domain location qualifier engine, location adapter extension, draft target persistence, and manifest permission declarations.
- **Predecessors:** Issue 9 (PR #29, merge `c60d866cdc9794af97db129c946b66711270f6b9`) and Issue 12 (PR #36, merge `26d78cc897ec2bd6853288be9c66838dd3bf63d2`). Ancestry verified on base commit `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff`.
- **Contract:** FR-06, NFR-02, ARC-07, DD-12, DD-22, AC-05, T-16, T-17, T-18, T-42 (partial).
- **Overlap inspection:** Inspected active branches/PRs; no competing claims or overlapping file modifications for location adapter and map target selection.
- **Readiness:** All eight readiness gates PASSED (`PREFLIGHT_RECORD_VALID` in `docs/agent-tools/readiness-evidence-issue16.json`).

## PR #42 Test-Coverage Cleanup Completed

1. **Production Guard Logic Testing (Correction 1):**
   - Extracted pure `S05MapTargetResultValidator` object and `ResultPayload` container used directly by `S05RecipientLockFragment`.
   - Updated `S05RecipientLockResultGuardsTest` so all 7 JVM tests call `S05MapTargetResultValidator.validateResult(...)` directly.
   - Enforced one-time request ID consumption (`activeMapRequestId = null` on successful update) to prevent result replay while preserving `onSaveInstanceState` configuration-change recovery.
2. **Instrumentation Test Coverage Correction (Correction 2):**
   - Clarified scope and assertions in `S05MapTargetDialogFragmentTest.kt` in `androidTest`.
   - Verified synchronous creation button state, scoped result emission, preselected target recreation, and cancel behavior.
3. **Automated Verification Suite:**
   - 245 JVM unit tests passed (`:app:testDebugUnitTest`).
   - Clean debug build, test APK compile, and lint (`:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`).
   - Android instrumentation test execution status: **NOT RUN** (0 adb devices connected).

## Remaining work

- Commit and push test-coverage cleanup to `feature/16-map-foreground-location`.
- Perform physical device or Google Play emulator walkthrough.
- Teammate PR re-review by User G and human merge into `main`.

## Workspace and recovery

- Branch: `feature/16-map-foreground-location`
- Clean working tree with untracked evidence and handoff files in `docs/agent-tools/`.

## Next action

Commit and push `feature/16-map-foreground-location` branch updates to PR #42. Link User J's agent log.
