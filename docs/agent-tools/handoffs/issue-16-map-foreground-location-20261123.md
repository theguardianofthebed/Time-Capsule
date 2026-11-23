# Task handoff: Issue #16 S-05 Google Maps target selection & fresh foreground location adapter (PR #42 Follow-up Review Corrections)

- **Confirmed owner:** User J (explicitly confirmed by requester)
- **Scope / period / different human reviewer:** MVP / 23 November 2026 / User G (reviewer)
- **Issue / PR:** Issue #16 / PR #42 (https://github.com/theguardianofthebed/Time-Capsule/pull/42)
- **Branch / base / head:** `feature/16-map-foreground-location` / Base `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff` / Head `feature/16-map-foreground-location`
- **State:** IN_PROGRESS (PR #42 follow-up review corrections applied and verified)
- **Boundary:** S-05 Google Maps target selection UI, pure domain location qualifier engine, location adapter extension, draft target persistence, and manifest permission declarations.
- **Predecessors:** Issue 9 (PR #29, merge `c60d866cdc9794af97db129c946b66711270f6b9`) and Issue 12 (PR #36, merge `26d78cc897ec2bd6853288be9c66838dd3bf63d2`). Ancestry verified on base commit `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff`.
- **Contract:** FR-06, NFR-02, ARC-07, DD-12, DD-22, AC-05, T-16, T-17, T-18, T-42 (partial).
- **Overlap inspection:** Inspected active branches/PRs; no competing claims or overlapping file modifications for location adapter and map target selection.
- **Readiness:** All eight readiness gates PASSED (`PREFLIGHT_RECORD_VALID` in `docs/agent-tools/readiness-evidence-issue16.json`).

## PR #42 Follow-up Review Corrections Completed

1. **Executable Instrumentation Tests & Safe Adapter Injection (Fix A - P1):**
   - Safely fallback to container location adapter or mock in `S05MapTargetDialogFragment` without casting host activity unconditionally to `MainActivity`.
   - Added `debugImplementation("androidx.fragment:fragment-testing-manifest:1.6.2")` in `app/build.gradle.kts`.
   - Placed `S05MapTargetDialogFragmentTest.kt` in `androidTest` (5 tests covering `FragmentResult` emission, recreation state preservation, preselection, and cancel behavior).
2. **Fixed 150m Domain Invariant Enforcement (Fix B - P1):**
   - Enforced `target.radiusMeters == LocationTarget.FIXED_RADIUS_METERS` in `LocationQualifier.qualify()`. Targets with non-150m radii (e.g. 500m) are rejected as `INVALID_TARGET`.
   - Added deterministic unit test `non150mTargetRadiusIsRejectedByQualifier`.
3. **Location Preservation Across Failed-Publication Replacement (Fix C - P1):**
   - Updated `PrivatePublicationDrafts.replacement()` to extract and persist `operation.lock.location` in the replacement `PhotoDraftRecord` and `.properties` file.
   - Added regression unit test `replacementDraftPreservesLocationTargetAcrossProcessRestart` verifying location target survival across process death / storage & ViewModel recreation.
4. **Obsolete Result Validation in Host Fragment (Fix D - P1):**
   - Included `RESULT_KEY_CAPSULE_ID` and `RESULT_KEY_SESSION_UID` in `S05MapTargetDialogFragment` result bundle.
   - Added validation in `S05RecipientLockFragment` checking `currentUiState.editable`, matching capsule ID, matching session UID, and WGS84 coordinate bounds before calling `viewModel.updateLocation()`.
5. **Synchronous Initial Confirm Button State (Fix E - P2):**
   - Updated `S05MapTargetDialogFragment.onViewCreated` to set `btnConfirmLocation.isEnabled = selectedLatLng != null` synchronously prior to asynchronous `onMapReady()`.
6. **Automated Verification Suite:**
   - 238 JVM unit tests passed (`:app:testDebugUnitTest`).
   - Clean debug build, test APK compile, and lint (`:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`).

## Remaining work

- Commit and push review corrections to `feature/16-map-foreground-location`.
- Perform physical device or Google Play emulator walkthrough.
- Teammate PR re-review by User G and human merge into `main`.

## Workspace and recovery

- Branch: `feature/16-map-foreground-location`
- Clean working tree with untracked evidence and handoff files in `docs/agent-tools/`.

## Next action

Commit and push `feature/16-map-foreground-location` branch updates to PR #42. Link User J's agent log.
