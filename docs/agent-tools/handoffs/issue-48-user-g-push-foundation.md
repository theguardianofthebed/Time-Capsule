# Issue #48 - push reminder foundation

- Owner: User G.
- Scope: explicitly requested stretch foundation; no real FCM delivery in this change.
- Work date: 27 November 2026.
- Branch: `feature/48-push-reminder-foundation`.
- Base: `b157f447f4340b6dba99edee2bef797956b0554b` (current inspected main).
- Predecessors: #19 / PR #45 at `ff24346`; #20 / PR #46 at `b157f44`.
- Different teammate review: pending.
- Follow-up: [Issue #49](https://github.com/theguardianofthebed/Time-Capsule/issues/49).

## Implemented boundary

`PushReminderEvent.parse` accepts exactly the version-1 TIME_REMINDER fields.
Unknown fields, protected previews, unsafe/oversized identifiers and invalid validity
windows are rejected without logging payload values. A canonical fingerprint includes
every accepted field, so an event ID reused with different content is a conflict.

`PushReminderCoordinator.handle` checks the active session generation, registration
binding, account, display window and permission. It claims the receipt before calling
the generic reminder renderer, then rechecks session/binding/time. Claims survive
uncertain posting failures. Only a renderer-confirmed unattempted post releases its
matching claim. The existing shared session lock serializes this short decision/side-effect
boundary with #20 generation changes.

This code is not installed in the dependency container, manifest, settings or account cleanup.
There is no FCM dependency, receiver, device registration upload, cloud sender or scheduler.
The memory receipt store and renderer in tests are fixtures, not production persistence.

## Proposed wire payload

All fields are strings. This is a new stretch protocol, not an existing Atlas record.

```json
{
  "schemaVersion": "1",
  "eventType": "TIME_REMINDER",
  "eventId": "example-event",
  "capsuleId": "example-capsule",
  "accountUid": "example-account",
  "bindingId": "example-login-binding",
  "notBefore": "2026-11-28T00:00:00Z",
  "expiresAt": "2026-11-29T00:00:00Z"
}
```

The 256-character identifier limit is a local input bound. Device time filters display;
it never grants server unlock authorization. No title, body, image, URL or coordinates
are carried in this payload.

## Next implementation in #49

1. Recheck latest main and coordinate shared S-08/MainActivity entry work with #21.
2. Confirm project access, SDK/message compatibility and permitted server resources before
   provisioning. Prove actual foreground/background FCM transport on a phone.
3. Implement a durable atomic `PushReceiptStore` and persistent login binding. Binding
   mutations must use the shared session lock. Scope receipt storage/cancellation to the
   account and invalidate the binding before logout; connect #20 cleanup.
4. Implement the short generic-reminder renderer. Do not call #19's
   `postUnlockNotification` or `handleUnlockOutcome` for an unverified time reminder:
   those APIs mean confirmed unlock, whereas this event only requests a check.
5. Reuse permission/channel/cancellation principles. Keep reminder event deduplication
   distinct from the confirmed-first-unlock attempt marker. Route a tap through current
   authentication and server checks; the existing AVAILABLE-only read validator is not
   permission to auto-unlock a still-locked capsule.
6. Add authenticated registration and a trusted server TIME due-event sender. Keep
   registration credentials private and published capsule data immutable. No background GPS,
   alarms, periodic device polling or automatic unlock.

## Verification and acceptance

The parser and coordinator have JVM regression tests including concurrent duplicate delivery,
same-UID/new-generation invalidation, permission refusal, stale binding, validity boundaries,
conflicting IDs, claim-before-post, adapter exceptions and conservative receipt retention.
The PR records final command results. No real FCM or device-delivery pass is claimed here.
Acceptance requires teammate review of this bounded foundation; #49 owns runtime integration.
