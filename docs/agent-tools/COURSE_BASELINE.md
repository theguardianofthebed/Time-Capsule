# Course baseline for agent explanations

This document helps an agent judge how much explanation our team needs in code comments and reviews. The supplied lectures establish what was demonstrated in class. They do not add product features, replace the approved TimeCapsule design, or prove that every team member already understands every example.

The requirement to leave easy English comments comes from the team's instructions. The specific comment levels in [CODING_AND_COMMENTS.md](CODING_AND_COMMENTS.md) are team conventions, not additional requirements attributed to the professor.

Read [AGENTS.md](../../AGENTS.md), [SOURCE_REGISTER.md](SOURCE_REGISTER.md), and [WORKFLOW.md](WORKFLOW.md) before implementation. Source names below identify the supplied attachments; they do not mean that the lecture PDFs must be committed to GitHub.

## What the supplied lectures demonstrate

Page numbers are PDF page numbers and match the printed slide numbers.

| Source | Demonstrated concepts | Pages |
| --- | --- | --- |
| `5_Android Intents (2).pdf` | Activities, view binding, button listeners, and starting another activity | 2–8 |
| Same source | Explicit and implicit intents; component, action, URI, MIME type, extras, and `Bundle` | 9–19 |
| Same source | Choosers, missing handlers, `ActivityNotFoundException`, intent filters, `CATEGORY_DEFAULT`, and an intentionally exported sharing receiver | 20–24 |
| Same source | Results returned by an activity; `setResult`, `finish`, and the historical result-request flow, with deprecated caller methods identified | 25–31 |
| Same source | Modern Activity Result API: contracts, launchers, registration, callbacks, `StartActivityForResult`, and `GetContent` | 32–37 |
| `9_Android Permission.pdf` | Permission purpose, delegation through intents, install-time versus runtime permissions, and manifest declarations | 2–9 |
| Same source | Checking permission, explaining a rationale, and a location-permission example | 10–13 |
| Same source | `RequestPermission`, `RequestMultiplePermissions`, launcher callbacks, permission branching, denial, and graceful degradation | 14–18 |
| Same source | Minimum permissions, requests connected to user actions, dependency permissions, transparency, and sensitive-access indicators | 19–20 |
| `12_Android Sensors.pdf` | Sensor categories, hardware and software sensors, framework objects, and events | 2–9 |
| Same source | Availability checks, sensor lists, nullable default sensors, event values and accuracy, and requested event rate | 10–13 |
| Same source | Registering in `onResume`, unregistering in `onPause`, battery use, and a light-sensor example | 14–15 |
| Team's statement in this request | UI Widgets were learned. The Intents examples also show TextView, Button, view binding, and click listeners. A separate UI Widgets lecture was not supplied. | User-reported; examples on Intents pp4–5 |

Modern permission launchers and activity-result callbacks are therefore part of the demonstrated baseline. They still require the normal class/function comments. Complex combinations, lifecycle edge cases, and unusual failure handling require finer comments even when their building blocks appeared in class.

## Limits of this baseline

Use the wording **not demonstrated in the supplied lecture set** when discussing an unfamiliar mechanism. Do not claim that the team has never learned it.

The Sensors lecture concerns the Android sensor framework. It does not demonstrate TimeCapsule's geographic unlock rule, location freshness checks, distance calculation, or the combination of time and location conditions. It also does not instruct the team to add a light sensor or motion-based feature.

The materials do not establish that Compose, coroutines, ViewModels, repository design, Firebase, DataStore, cache authorization, retry reconciliation, or background scheduling were taught. This is a reason to explain approved mechanisms carefully; it is not permission to introduce new architecture.

## Current project mechanisms that need finer comments

The live design evidence recorded in [SOURCE_REGISTER.md](SOURCE_REGISTER.md) is separate from the lecture evidence. It records native Android Views; Firebase Auth, Firestore, and Storage; memory-only Firestore cache; DataStore markers and recovery journal; and UID-scoped app-private retained content. Explain these mechanisms where implemented, using the exact approved contracts.

| Area being changed | Explain near the relevant code |
| --- | --- |
| Android Views and asynchronous state updates | Which object owns visible state, when a callback can arrive, and how lifecycle changes affect it |
| Authentication and session changes | Which account owns a result, why a previous session's callback must be rejected, and what logout invalidates |
| Repository and remote operations | What each layer is responsible for, what the server confirms, and how errors reach the caller |
| Memory-only cache and retained content | Why these are different stores, which verified condition permits a read, and how UID ownership and cleanup are enforced |
| DataStore markers and recovery journal | What a record means, when it is written or removed, and how it differs from the protected content itself |
| Sending, retry, and reconciliation | How the same operation is recognized, what an uncertain outcome means, and how duplicate delivery is prevented |
| Photos and content URIs | What the URI refers to, how read access is obtained, and how required bytes remain available |
| Time and geographic conditions | Units, clock/source, boundary comparison, freshness and accuracy handling, and why both enabled conditions must pass |
| App-open, resume, detail, and retry checks | What initiates a check, what can be reused, and what happens if evidence is unavailable |

Use the current design's actual thresholds and permissions. Do not introduce Room, switch to Compose, or add alarms, geofences, boot handling, background location, or FCM through a commenting or cleanup task. A source conflict must be resolved through the workflow; this document does not settle a new product decision.

## Teaching examples that require care

**Permission result shape.** Permission p14 describes the multiple-permission result as an array of Boolean values. The AndroidX contract returns `Map<String, Boolean>`: each permission name has its own grant result. Use the correct type for the repository's dependency and explain partial grants. [1]

**Cached location.** Permission pp13 and 18 demonstrate `LocationManager.getLastKnownLocation`. That method can return null or an old cached location and does not request a fresh fix. A permission example alone is insufficient evidence for an unlock decision. Apply the project's approved freshness, accuracy, and failure rules. [2]

**Denial handling.** Permission p18 contains a short log message about being unable to use the app. The same deck's pp15–16 shows an educational explanation, cancellation, and graceful degradation. Implement the approved feature-level denial behavior; do not copy an informal teaching log as product UI.

**Historical activity-result code.** Intents pp26–31 explains the old approach, and p27 marks the caller methods deprecated. The deck then teaches the modern approach on pp32–37. Read the whole sequence before choosing an example to adapt.

**Exported components.** Intents pp22–24 deliberately exposes an image-sharing receiver. That example does not make `android:exported="true"` a default for internal screens. Explain the purpose and validation of every intentionally exported component; an intent filter is not an authorization check. [3]

**Sensor lifecycle.** Sensors pp13–15 connects listener registration with unregistration, nullable availability, and battery use. Preserve that reasoning when relevant; do not copy an isolated registration line without its cleanup behavior.

## Sources

The three lecture PDFs and the team's UI Widgets statement are supplied course context. Their page references appear above. Official documentation below was checked while preparing this package on 2026-11-03; verify compatibility with the repository's actual SDK and dependency versions when implementing.

1. Android Developers, [ActivityResultContracts.RequestMultiplePermissions](https://developer.android.com/reference/kotlin/androidx/activity/result/contract/ActivityResultContracts.RequestMultiplePermissions).
2. Android Developers, [LocationManager and getLastKnownLocation](https://developer.android.com/reference/android/location/LocationManager).
3. Android Developers, [Intents and intent filters](https://developer.android.com/guide/components/intents-filters).
