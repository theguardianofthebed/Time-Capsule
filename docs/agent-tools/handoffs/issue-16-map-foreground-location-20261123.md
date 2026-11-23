# Task handoff: Issue #16 S-05 Google Maps target selection & fresh foreground location adapter (PR #42 Review Corrections)

- **Confirmed owner:** User J (explicitly confirmed by requester)
- **Scope / period / different human reviewer:** MVP / 23 November 2026 / User G (reviewer)
- **Issue / PR:** Issue #16 / PR #42 (https://github.com/theguardianofthebed/Time-Capsule/pull/42)
- **Branch / base / head:** `feature/16-map-foreground-location` / Base `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff` / Head `feature/16-map-foreground-location`
- **State:** IN_PROGRESS (PR #42 review corrections applied and verified)
- **Boundary:** S-05 Google Maps target selection UI, pure domain location qualifier engine, location adapter extension, draft target persistence, and manifest permission declarations.
- **Predecessors:** Issue 9 (PR #29, merge `c60d866cdc9794af97db129c946b66711270f6b9`) and Issue 12 (PR #36, merge `26d78cc897ec2bd6853288be9c66838dd3bf63d2`). Ancestry verified on base commit `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff`.
- **Contract:** FR-06, NFR-02, ARC-07, DD-12, DD-22, AC-05, T-16, T-17, T-18, T-42 (partial).
- **Overlap inspection:** Inspected active branches/PRs; no competing claims or overlapping file modifications for location adapter and map target selection.
- **Readiness:** All eight readiness gates PASSED (`PREFLIGHT_RECORD_VALID` in `docs/agent-tools/readiness-evidence-issue16.json`).

## PR #42 Review Corrections Completed

1. **Fragment Recreation & FragmentResult Handoff (P0):**
   - Replaced ordinary Fragment property callbacks with lifecycle-safe `parentFragmentManager.setFragmentResultListener` / `setFragmentResult(REQUEST_KEY_MAP_TARGET, bundle)`.
   - Added `S05MapTargetDialogFragment.newInstance(initialTarget, initialName)` factory passing args via Bundle.
   - Saved pending pin selection (`KEY_PENDING_LAT`, `KEY_PENDING_LNG`, `KEY_PENDING_NAME`) in `onSaveInstanceState` and restored on view creation.
2. **Invalid GPS Timestamps Rejection (P0):**
   - Removed `nowNanos` substitution in `DefaultLocationAdapter` when `androidLocation.elapsedRealtimeNanos <= 0L`.
   - Returns typed status `LocationSampleStatus.TIMEOUT_OR_NULL` ("Location fix is missing a valid monotonic timestamp.") when `elapsedRealtimeNanos <= 0L`.
   - Updated `LocationQualifier` to reject `sample.elapsedRealtimeNanos <= 0L`.
3. **Require Actual Pin Selection (P1):**
   - When `initialTarget == null`, map camera opens at default Seoul position without placing a pin or circle.
   - `btnConfirmLocation` starts disabled until user explicitly taps the map or fetches a valid current-location fix.
   - Pre-existing confirmed target enables `btnConfirmLocation` on open. Cancel preserves host selection.
4. **Current-Location Selection Qualification (P1):**
   - Added `LocationQualifier.validateSampleQuality(sample, nowElapsedRealtimeNanos)`.
   - Tapping "Use current location" validates coordinates, positive monotonic timestamp, freshness ($\le 60\text{ s}$), and reported accuracy ($\le 100\text{ m}$) before adopting pin.
   - Displays specific inline guidance in `tvMapStatus` on poor accuracy ($> 100\text{ m}$) or stale fixes.
5. **Fixed 150m Radius Invariant (P1):**
   - Enforced fixed $150.0\text{ m}$ radius (`LocationTarget.FIXED_RADIUS_METERS = 150.0`).
   - Normalizes non-150m or malformed radius values in `PhotoDraftJournal.load()` to $150.0\text{ m}$.
   - Safely invalidates corrupt coordinates (`NaN`, out-of-bounds) to `null` without corrupting title, text, photo, or recipient fields.
6. **Automated Verification Suite:**
   - 236 JVM unit tests passed (`:app:testDebugUnitTest`).
   - Android instrumentation test suite written for `S05MapTargetDialogFragmentTest` (5 tests covering `FragmentResult` emission, recreation state preservation, preselection, and cancel behavior).
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
