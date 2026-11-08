package com.example.timecapsule.data.adapter

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/**
 * Configures [FirebaseFirestore] memory-only cache settings as required by NFR-01 and ARC-06.
 * Disables persistent disk cache to prevent sensitive capsule data surviving session cleanup.
 */
object FirestoreCachePolicy {

    /** Applies memory-only cache settings to [firestore] instance before first use. */
    fun applyMemoryOnlyCache(firestore: FirebaseFirestore) {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        firestore.firestoreSettings = settings
    }
}

/**
 * Replaceable adapter boundary for Firestore remote capsule metadata operations.
 */
interface FirestoreAdapter {
    /** True if Firestore adapter is configured and ready. */
    fun isAvailable(): Boolean
}
