package com.example.timecapsule.data.adapter

import com.example.timecapsule.config.FirebaseConfig
import com.google.firebase.firestore.MemoryCacheSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [FirestoreCachePolicy] configuration contracts and memory-only cache policy (NFR-01, ARC-06).
 */
class MemoryCacheConfigTest {

    @Test
    fun createMemoryOnlySettingsConfiguresMemoryCacheInstance() {
        val settings = FirestoreCachePolicy.createMemoryOnlySettings()

        assertNotNull("Firestore settings must not be null", settings)
        assertNotNull("Local cache settings must not be null", settings.cacheSettings)
        assertTrue(
            "Firestore local cache settings must use MemoryCacheSettings to prevent disk caching (NFR-01)",
            settings.cacheSettings is MemoryCacheSettings
        )
        assertFalse(
            "Persistent disk cache must be disabled in memory-only settings (NFR-01)",
            settings.isPersistenceEnabled
        )
    }

    @Test
    fun firestoreCachePolicyObjectIsAccessible() {
        assertNotNull(FirestoreCachePolicy)
    }

    @Test
    fun defaultFirestoreAdapterSafelyHandlesUninitializedFirebase() {
        val adapter = DefaultFirestoreAdapter(contextSupplier = { null })

        assertFalse("Adapter must report unavailable when Context/Firebase is absent", adapter.isAvailable())
        assertNull("Adapter must return null instance when Context/Firebase is absent", adapter.firestoreInstance())
    }

    @Test
    fun getMemoryOnlyFirestoreFailsSafelyWhenUninitialized() {
        FirebaseConfig.resetEmulatorModeForTesting()
        val instance = FirebaseConfig.getMemoryOnlyFirestore(context = null)

        // Without initialized FirebaseApp in pure unit test, instance creation fails safely or returns null.
        if (instance != null) {
            assertTrue(
                "Memory-only cache settings must be applied before first use",
                instance.firestoreSettings.cacheSettings is MemoryCacheSettings
            )
        }
    }
}
