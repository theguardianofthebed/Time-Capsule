package com.example.timecapsule.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying [FirebaseConfig] constants, emulator mode tracking, and security rules baseline.
 */
class FirebaseConfigTest {

    @Test
    fun emulatorPortConstantsMatchContractDefaults() {
        assertEquals("10.0.2.2", FirebaseConfig.EMULATOR_HOST)
        assertEquals(9099, FirebaseConfig.AUTH_PORT)
        assertEquals(8080, FirebaseConfig.FIRESTORE_PORT)
        assertEquals(9199, FirebaseConfig.STORAGE_PORT)
    }

    @Test
    fun emulatorModeDefaultsToFalse() {
        FirebaseConfig.resetEmulatorModeForTesting()
        assertFalse(FirebaseConfig.isEmulatorMode)
    }

    @Test
    fun firestoreRulesEnforceInitialDenyAllBaseline() {
        val rulesFile = File("firestore.rules").takeIf { it.exists() } ?: File("../firestore.rules")
        assertTrue("firestore.rules file must exist at project root", rulesFile.exists())

        val content = rulesFile.readText()
        assertTrue("firestore.rules must contain deny-all baseline", content.contains("allow read, write: if false;"))
    }

    @Test
    fun storageRulesEnforceInitialDenyAllBaseline() {
        val rulesFile = File("storage.rules").takeIf { it.exists() } ?: File("../storage.rules")
        assertTrue("storage.rules file must exist at project root", rulesFile.exists())

        val content = rulesFile.readText()
        assertTrue("storage.rules must contain deny-all baseline", content.contains("allow read, write: if false;"))
    }
}
