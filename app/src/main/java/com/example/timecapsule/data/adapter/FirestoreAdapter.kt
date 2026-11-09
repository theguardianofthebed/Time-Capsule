package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/**
 * Configures [FirebaseFirestore] memory-only cache settings as required by NFR-01 and ARC-06.
 * Disables persistent disk cache to prevent sensitive capsule data surviving session cleanup.
 */
object FirestoreCachePolicy {

    /** Creates memory-only cache settings instance without persistent disk caching. */
    fun createMemoryOnlySettings(): FirebaseFirestoreSettings {
        return FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
    }

    /** Applies memory-only cache settings to [firestore] instance before first use. */
    fun applyMemoryOnlyCache(firestore: FirebaseFirestore): FirebaseFirestoreSettings {
        val settings = createMemoryOnlySettings()
        firestore.firestoreSettings = settings
        return settings
    }
}

/**
 * Replaceable adapter boundary for Firestore remote capsule and recipient metadata operations.
 */
interface FirestoreAdapter {
    /** True if Firestore adapter is configured and ready. */
    fun isAvailable(): Boolean

    /** Returns the configured memory-only [FirebaseFirestore] instance or null if unavailable. */
    fun firestoreInstance(): FirebaseFirestore?

    /** Performs exact recipient profile lookup in `recipientDirectory/{uid}`. */
    suspend fun getRecipientProfile(uid: String): OperationResult<RecipientProfile>

    /** Creates or updates public recipient profile in `recipientDirectory/{uid}`. */
    suspend fun saveRecipientProfile(profile: RecipientProfile): OperationResult<Unit>
}
