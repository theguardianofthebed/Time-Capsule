# Task handoff: Issue #16 S-05 Google Maps target selection & fresh foreground location adapter

- **Confirmed owner:** User J (explicitly confirmed by requester)
- **Scope / period / different human reviewer:** MVP / 23 November 2026 / User G (reviewer)
- **Issue / PR:** Issue #16 (https://github.com/theguardianofthebed/Time-Capsule/issues/16) / Branch `feature/16-map-foreground-location` ready for PR
- **Branch / base / head:** `feature/16-map-foreground-location` / Base `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff` / Head `feature/16-map-foreground-location`
- **State:** IN_PROGRESS (Ready for student review, log update, and PR opening)
- **Boundary:** S-05 Google Maps target selection UI, pure domain location qualifier engine, location adapter extension, draft target persistence, and manifest permission declarations.
- **Predecessors:** Issue 9 (PR #29, merge `c60d866cdc9794af97db129c946b66711270f6b9`) and Issue 12 (PR #36, merge `26d78cc897ec2bd6853288be9c66838dd3bf63d2`). Ancestry verified on base commit `6ccd2ffb02dde02278f8c9b30a4ae1355ea180ff`.
- **Contract:** FR-06, NFR-02, ARC-07, DD-12, DD-22, AC-05, T-16, T-17, T-18, T-42 (partial).
- **Overlap inspection:** Inspected active branches/PRs; no competing claims or overlapping file modifications for location adapter and map target selection.
- **Readiness:** All eight readiness gates PASSED (`PREFLIGHT_RECORD_VALID` in `docs/agent-tools/readiness-evidence-issue16.json`).

## Completed work

1. **Domain Qualifier Engine (`LocationSample.kt`, `LocationQualifier.kt`):**
   - Created `ForegroundLocationSample`, `LocationSampleStatus`, `LocationSampleResult`, `QualificationFailureReason`, and `LocationQualificationResult`.
   - Created `LocationQualifier` evaluating target coordinates and sample freshness: distance <= 150m, sample age in 0..60s, and accuracy <= 100m.
   - Decoupled distance calculation using `DistanceCalculator` seam with `HaversineDistanceCalculator` default for pure JVM unit testing.
2. **Android Location Adapter (`LocationAdapter.kt`, `DefaultLocationAdapter.kt`):**
   - Extended `LocationAdapter` with `isFineLocationGranted()` and `getCurrentLocation(): LocationSampleResult`.
   - Implemented `DefaultLocationAdapter` using Play Services `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY`, cancellation handling, and typed failure statuses.
   - Updated `MainActivity` container initialization and `FakeLocationAdapter` in test doubles.
3. **Map Target Selection UI (`S05MapTargetDialogFragment.kt`, `dialog_s05_map_target.xml`):**
   - Implemented lifecycle-safe `S05MapTargetDialogFragment` embedding Google Maps `MapView` with pin and 150m circle overlay.
   - Manual map selection operates without location permissions.
   - "Use current location" button explicitly checks/requests `ACCESS_FINE_LOCATION` and moves camera/pin to current location.
   - Explicit "Confirm Location" button commits selected target to draft; closing/canceling dialog retains prior selection.
4. **Draft Target Persistence (`PhotoDraftJournal.kt`, `CapsuleDraftViewModel.kt`):**
   - Extended `PhotoDraftRecord` and `PhotoDraftJournal` to persist `locationTarget` and `locationName` in UID-scoped properties.
   - Maintained 100% backward compatibility for pre-existing properties files.
   - Verified that text/photo edits preserve confirmed location target.
5. **Manifest & Account Settings (`AndroidManifest.xml`, `S08AccountSettingsFragment.kt`):**
   - Declared `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`.
   - Configured Google Maps API key meta-data with `${MAPS_API_KEY}` manifest placeholder.
   - Updated S-08 location status badge to require `ACCESS_FINE_LOCATION` for precise qualification.
6. **Automated Verification:**
   - Written 15 new JVM unit tests (`LocationQualifierTest`, `DraftLocationPersistenceTest`, `DefaultLocationAdapterTest`). Total 233 JVM unit tests passed (`:app:testDebugUnitTest`).
   - Clean debug build and lint (`:app:assembleDebug`, `:app:lintDebug`).

## Remaining work

- Push feature branch `feature/16-map-foreground-location` and open Draft PR.
- Perform physical device or Google Play emulator walkthrough for location permission dialogs and map pin selection.
- Teammate PR review by User G and human merge into `main`.

## Workspace and recovery

- Branch: `feature/16-map-foreground-location`
- Clean working tree with untracked evidence and handoff files in `docs/agent-tools/`.

## Next action

Push `feature/16-map-foreground-location` and create Draft PR against `main`. Link User J's agent log.
