package com.example.timecapsule.data.adapter

import android.content.Context
import com.example.timecapsule.config.FirebaseConfig
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
 * Replaceable adapter boundary for Firestore remote capsule metadata operations.
 */
interface FirestoreAdapter {
    /** True if Firestore adapter is configured and ready. */
    fun isAvailable(): Boolean

    /** Returns the configured memory-only [FirebaseFirestore] instance or null if unavailable. */
    fun firestoreInstance(): FirebaseFirestore?
}

/**
 * Production implementation of [FirestoreAdapter] bound to [FirebaseConfig.getMemoryOnlyFirestore].
 * Enforces memory-only cache configuration before returning the Firestore SDK instance.
 */
class DefaultFirestoreAdapter(
    private val contextSupplier: () -> Context? = { null }
) : FirestoreAdapter {

    override fun isAvailable(): Boolean {
        val ctx = contextSupplier()
        return ctx != null && FirebaseConfig.isFirebaseAvailable(ctx)
    }

    override fun firestoreInstance(): FirebaseFirestore? {
        return FirebaseConfig.getMemoryOnlyFirestore(contextSupplier())
    }
}
