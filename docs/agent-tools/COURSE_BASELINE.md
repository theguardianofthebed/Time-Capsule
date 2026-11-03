# Course baseline for code explanations

Use this guide to decide which code needs more explanation. The supplied lectures show what was demonstrated in class; they do not establish that every student understands every example. Follow [CODING_AND_COMMENTS.md](CODING_AND_COMMENTS.md) for the team's English comment rules and [SOURCE_REGISTER.md](SOURCE_REGISTER.md) for the approved project design.

## Topics covered

Page numbers refer to the supplied PDFs and match the printed slide numbers. The source filenames identify course materials; the PDFs do not need to be committed to the repository.

| Source | Demonstrated concepts | Pages |
|---|---|---|
| `5_Android Intents (2).pdf` | Activities, view binding, button listeners, and starting another activity | 2–8 |
| Same source | Explicit/implicit intents; component, action, URI, MIME type, extras, and `Bundle` | 9–19 |
| Same source | Choosers, missing handlers, `ActivityNotFoundException`, intent filters, `CATEGORY_DEFAULT`, and an intentionally exported sharing receiver | 20–24 |
| Same source | Activity results, `setResult`, `finish`, and the historical request flow with deprecated caller methods identified | 25–31 |
| Same source | Modern Activity Result API: contracts, launchers, registration, callbacks, `StartActivityForResult`, and `GetContent` | 32–37 |
| `9_Android Permission.pdf` | Permission purpose, delegation through intents, install-time/runtime permissions, and manifest declarations | 2–9 |
| Same source | Permission checks, rationale, and a location-permission example | 10–13 |
| Same source | `RequestPermission`, `RequestMultiplePermissions`, launcher callbacks, permission branching, denial, and graceful degradation | 14–18 |
| Same source | Minimum permissions, requests tied to user actions, dependency permissions, transparency, and sensitive-access indicators | 19–20 |
| `12_Android Sensors.pdf` | Sensor categories, hardware/software sensors, framework objects, and events | 2–9 |
| Same source | Availability, sensor lists, nullable default sensors, event values/accuracy, and requested event rate | 10–13 |
| Same source | Registering in `onResume`, unregistering in `onPause`, battery use, and a light-sensor example | 14–15 |
| Team-reported coursework | UI Widgets; the Intents examples also show TextView, Button, view binding, and click listeners. No separate Widgets deck was supplied. | Intents pp4–5 for those examples |

Modern activity-result and permission launchers are part of this baseline. They still need normal class/function comments. Add finer comments for complicated combinations, lifecycle edge cases, and unusual failure handling.

## Topics that need more explanation

Say **not demonstrated in the supplied lectures**, rather than assuming a student has never learned a topic. The supplied materials do not establish coverage of Compose, coroutines, ViewModels, repositories, Firebase, DataStore, cache authorization, retry reconciliation, or background scheduling.

The Sensors lecture covers Android's sensor framework. It does not teach TimeCapsule's geographic unlock rule, fresh-location checks, distance calculation, or combined time/location condition. A taught example also does not add a sensor feature to the project.

Explain approved mechanisms where they are used:

| Area | What the nearby comments should explain |
|---|---|
| UI state and asynchronous work | Who owns the state/work, when results arrive, and what happens when the screen disappears |
| Authentication/session changes | Which UID owns a result, why old-session callbacks are rejected, and what logout clears |
| Repositories and remote calls | Each layer's job, what the server confirms, and how errors reach the caller |
| Memory-only cache and retained content | How the stores differ, what permits a read, and how ownership/cleanup work |
| DataStore markers and recovery journal | What each record means, when it is written/removed, and why it is separate from protected content |
| Sending, retries, and reconciliation | How the same operation is recognized, what an unknown outcome means, and how duplicates are prevented |
| Photos and content URIs | What the URI refers to, how access is obtained, and how needed bytes remain available |
| Time/location conditions | Units, time source, boundaries, location freshness/accuracy, and why both enabled conditions must pass |
| App-open, resume, detail, and retry checks | What starts the check and what happens when evidence is missing |

Use the exact approved thresholds and permissions. Commenting work does not authorize a framework, storage, or background-trigger change. Read [AGENTS.md](../../AGENTS.md), the [repository guide](REPOSITORY_STATUS.md), and the relevant contract before implementation.

## Read lecture examples carefully

**Multiple-permission results.** Permission p14 calls the result a Boolean array. AndroidX actually returns `Map<String, Boolean>`, with a separate grant result for each permission name. Use the correct type and handle partial grants. [1]

**Cached location.** Permission pp13 and 18 use `LocationManager.getLastKnownLocation`. It may return null or an old cached location; it does not request a fresh fix. A granted permission alone cannot establish the approved unlock conditions. Apply the project's freshness, accuracy, and failure rules. [2]

**Permission denial.** Permission p18 has a short log saying the app cannot be used. Pages 15–16 show explanations, cancellation, and graceful degradation. Follow the approved feature-level denial behavior instead of copying that informal log into the UI.

**Activity results.** Intents pp26–31 show the historical approach, and p27 marks the caller methods deprecated. Pages 32–37 teach the modern API. Read the full sequence before adapting an example.

**Exported components.** Intents pp22–24 deliberately expose an image-sharing receiver. This does not make `android:exported="true"` the default for internal screens. Explain why an exported component exists and how it validates input; an intent filter is not an authorization check. [3]

**Sensor lifecycle.** Sensors pp13–15 connect registration with unregistration, nullable availability, and battery use. Preserve the cleanup behavior when adapting relevant code.

## References

Check these APIs against the repository's actual SDK and dependency versions:

1. [ActivityResultContracts.RequestMultiplePermissions](https://developer.android.com/reference/kotlin/androidx/activity/result/contract/ActivityResultContracts.RequestMultiplePermissions).
2. [LocationManager and getLastKnownLocation](https://developer.android.com/reference/android/location/LocationManager).
3. [Intents and intent filters](https://developer.android.com/guide/components/intents-filters).
