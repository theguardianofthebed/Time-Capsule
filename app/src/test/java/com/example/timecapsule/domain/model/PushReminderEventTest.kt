package com.example.timecapsule.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Strict parsing fixtures contain only synthetic routing information, never actual user content. */
class PushReminderEventTest {
    /** Produces one supported wire payload whose timestamps have an explicit UTC offset. */
    private fun payload() = mapOf("schemaVersion" to "1", "eventType" to "TIME_REMINDER",
        "eventId" to "event-1", "capsuleId" to "capsule-1", "accountUid" to "account-1",
        "bindingId" to "login-1", "notBefore" to "2026-11-28T00:00:00Z",
        "expiresAt" to "2026-11-29T00:00:00Z")

    /** A supported reminder parses without turning it into an unlock confirmation. */
    @Test fun acceptsSupportedReminder() {
        assertEquals("capsule-1", PushReminderEvent.parse(payload())?.capsuleId)
    }

    /** Every declared field is mandatory, including account and binding evidence. */
    @Test fun rejectsEachMissingField() {
        payload().keys.forEach { assertNull(PushReminderEvent.parse(payload() - it)) }
    }

    /** Protected previews and all undeclared payload extensions are rejected. */
    @Test fun rejectsContentAndUnknownFields() {
        listOf("text", "title", "photo", "url", "latitude", "unknown").forEach {
            assertNull(PushReminderEvent.parse(payload() + (it to "fixture")))
        }
    }

    /** A new protocol version requires deliberate implementation instead of permissive parsing. */
    @Test fun rejectsUnknownVersion() {
        assertNull(PushReminderEvent.parse(payload() + ("schemaVersion" to "2")))
    }

    /** Other event types cannot accidentally borrow confirmed-unlock semantics. */
    @Test fun rejectsOtherEventTypes() {
        assertNull(PushReminderEvent.parse(payload() + ("eventType" to "UNLOCKED")))
    }

    /** Whitespace, path separators, controls and empty identifiers cannot select storage paths. */
    @Test fun rejectsUnsafeIdentifiers() {
        for (field in listOf("eventId", "capsuleId", "accountUid", "bindingId")) {
            for (value in listOf("", "a/b", "../x", " x", "x\n", "a".repeat(257))) {
                assertNull(PushReminderEvent.parse(payload() + (field to value)))
            }
        }
    }

    /** The documented inclusive identifier bound still accepts the largest supported value. */
    @Test fun acceptsIdentifierAtLocalBound() {
        assertNotNull(PushReminderEvent.parse(payload() + ("eventId" to "a".repeat(256))))
    }

    /** Invalid or offset-free date strings do not inherit a device time zone. */
    @Test fun rejectsMalformedTimes() {
        for (value in listOf("", "tomorrow", "2026-11-28T00:00:00", "x".repeat(41))) {
            assertNull(PushReminderEvent.parse(payload() + ("notBefore" to value)))
        }
    }

    /** An empty validity window cannot be displayed at its end boundary. */
    @Test fun rejectsEqualTimes() {
        assertNull(PushReminderEvent.parse(payload() + ("expiresAt" to payload().getValue("notBefore"))))
    }

    /** Reversed times cannot create an indefinitely usable event. */
    @Test fun rejectsReversedTimes() {
        assertNull(PushReminderEvent.parse(payload() + ("expiresAt" to "2026-11-27T00:00:00Z")))
    }

    /** Map iteration order does not change an accepted event's fingerprint. */
    @Test fun fingerprintIgnoresMapOrder() {
        assertEquals(PushReminderEvent.parse(payload())!!.fingerprint(),
            PushReminderEvent.parse(payload().toList().reversed().toMap())!!.fingerprint())
    }

    /** A changed capsule under an existing event ID produces a conflicting fingerprint. */
    @Test fun fingerprintIncludesCapsule() {
        assertNotEquals(PushReminderEvent.parse(payload())!!.fingerprint(),
            PushReminderEvent.parse(payload() + ("capsuleId" to "other"))!!.fingerprint())
    }

    /** Account and login binding are part of the fingerprint, not just the receipt key. */
    @Test fun fingerprintIncludesAccountAndBinding() {
        for (key in listOf("accountUid", "bindingId")) {
            assertNotEquals(PushReminderEvent.parse(payload())!!.fingerprint(),
                PushReminderEvent.parse(payload() + (key to "other"))!!.fingerprint())
        }
    }
}
