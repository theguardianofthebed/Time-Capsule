package com.example.timecapsule.data.media

import com.example.timecapsule.domain.model.LocationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM unit tests for [PhotoDraftJournal] location target persistence & backward compatibility (T-17).
 * Verifies that confirmed map target coordinates survive relaunch, older records without location restore safely,
 * and account UID isolation prevents cross-account draft leakage.
 */
class DraftLocationPersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun locationTargetSavesAndRestoresCorrectly() {
        val root = tempFolder.newFolder("photo-drafts")
        val journal = PhotoDraftJournal(root)

        val target = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        val record = PhotoDraftRecord(
            capsuleId = "capsule-101",
            title = "Seoul Capsule",
            text = "Hello Seoul",
            photo = null,
            locationTarget = target,
            locationName = "37.5665, 126.9780"
        )

        journal.save("user-a", record)

        val restored = journal.load("user-a")
        assertNotNull(restored)
        assertEquals("capsule-101", restored!!.capsuleId)
        assertEquals("Seoul Capsule", restored.title)
        assertEquals("Hello Seoul", restored.text)
        val loc = restored.locationTarget!!
        assertEquals(37.5665, loc.latitude, 0.0001)
        assertEquals(126.9780, loc.longitude, 0.0001)
        assertEquals(150.0, loc.radiusMeters, 0.01)
        assertEquals("37.5665, 126.9780", restored.locationName)
    }

    @Test
    fun legacyRecordWithoutLocationRestoresSafelyAsNull() {
        val root = tempFolder.newFolder("photo-drafts")
        val journal = PhotoDraftJournal(root)

        // Legacy record without location properties
        val legacyRecord = PhotoDraftRecord(
            capsuleId = "capsule-legacy",
            title = "Legacy Title",
            text = "Legacy Message",
            photo = null
        )

        journal.save("user-legacy", legacyRecord)

        val restored = journal.load("user-legacy")
        assertNotNull(restored)
        assertEquals("capsule-legacy", restored!!.capsuleId)
        assertNull(restored.locationTarget)
        assertNull(restored.locationName)
    }

    @Test
    fun uidIsolationPreventsCrossAccountDraftRestoration() {
        val root = tempFolder.newFolder("photo-drafts")
        val journal = PhotoDraftJournal(root)

        val target = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        val record = PhotoDraftRecord(
            capsuleId = "capsule-user-a",
            title = "Private A",
            text = "Message A",
            photo = null,
            locationTarget = target,
            locationName = "Seoul"
        )

        journal.save("user-a", record)

        // User B attempts to restore User A's draft -> returns null
        val restoredB = journal.load("user-b")
        assertNull(restoredB)
    }

    @Test
    fun updatingTextPreservesLocationTarget() {
        val root = tempFolder.newFolder("photo-drafts")
        val journal = PhotoDraftJournal(root)

        val target = LocationTarget(latitude = 37.5665, longitude = 126.9780, radiusMeters = 150.0)
        val initialRecord = PhotoDraftRecord(
            capsuleId = "capsule-edit",
            title = "Draft",
            text = "Original Text",
            photo = null,
            locationTarget = target,
            locationName = "Seoul"
        )

        journal.save("user-a", initialRecord)

        val updatedRecord = initialRecord.copy(text = "Updated Text")
        journal.save("user-a", updatedRecord)

        val restored = journal.load("user-a")
        assertNotNull(restored)
        assertEquals("Updated Text", restored!!.text)
        assertNotNull(restored.locationTarget)
        assertEquals(37.5665, restored.locationTarget!!.latitude, 0.0001)
    }

    @Test
    fun malformedOrOutOfBoundsCoordinatesSafelyInvalidatesLocationTarget() {
        val root = tempFolder.newFolder("photo-drafts")
        val journal = PhotoDraftJournal(root)

        val corruptRecord = PhotoDraftRecord(
            capsuleId = "capsule-corrupt",
            title = "Corrupt Title",
            text = "Keep this text intact",
            photo = null,
            locationTarget = LocationTarget(latitude = 999.0, longitude = 126.9780, radiusMeters = 500.0),
            locationName = "Invalid Location"
        )

        journal.save("user-corrupt", corruptRecord)

        val restored = journal.load("user-corrupt")
        assertNotNull(restored)
        assertEquals("Corrupt Title", restored!!.title)
        assertEquals("Keep this text intact", restored.text)
        assertNull(restored.locationTarget)
        assertNull(restored.locationName)
    }
}
