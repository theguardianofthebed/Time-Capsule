package com.example.timecapsule.domain.model

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleDraftTest {
    private fun draft(text: String, photoUri: String?): CapsuleDraft = CapsuleDraft(
        capsuleId = "test-1",
        recipientUid = "recipient-uid",
        text = text,
        localPhotoUri = photoUri,
        lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.parse("2026-10-30T09:00:00Z")),
    )

    @Test fun bothEmptyIsInvalid() = assertFalse(draft("  ", null).hasAnyContent())
    @Test fun textOnlyIsAccepted() = assertTrue(draft("Hello", null).hasAnyContent())
    @Test fun photoOnlyIsAccepted() = assertTrue(draft("", "content://test/photo").hasAnyContent())
}
