package com.example.timecapsule.config

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.example.timecapsule.data.adapter.FirestoreCachePolicy

/**
 * Utility for verifying Firebase configuration availability and setting up local emulator connections.
 * Enforces local emulator isolation and prevents silent fallback to cloud production services (DD-20, NFR-06).
 */
object FirebaseConfig {

    const val EMULATOR_HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
    const val STORAGE_PORT = 9199

    /**
     * Flag indicating whether local emulator mode is active.
     * When true, SDK instances are strictly bound to local emulator endpoints
     * and production service fallbacks are prohibited.
     */
    var isEmulatorMode: Boolean = false
        private set

    @Volatile
    private var configuredFirestore: FirebaseFirestore? = null

    /**
     * Checks whether default [FirebaseApp] is initialized in [context].
     * Returns false when google-services.json or cloud credentials are missing.
     */
    fun isFirebaseAvailable(context: Context): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Safely obtains and configures the singleton [FirebaseFirestore] instance with memory-only cache settings (NFR-01, ARC-06).
     * Returns null if Firebase is not initialized or unavailable in the current context/environment.
     * Applies [FirestoreCachePolicy.applyMemoryOnlyCache] before any Firestore operations occur.
     */
    @Synchronized
    fun getMemoryOnlyFirestore(context: Context? = null): FirebaseFirestore? {
        configuredFirestore?.let { return it }

        if (context != null && !isFirebaseAvailable(context)) {
            return null
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()
            FirestoreCachePolicy.applyMemoryOnlyCache(firestore)
            configuredFirestore = firestore
            firestore
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Connects Auth, Firestore, and Storage instances to local emulator endpoints for testing.
     * Sets [isEmulatorMode] to true to enforce local endpoint isolation.
     */
    fun useEmulators(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        storage: FirebaseStorage,
        host: String = EMULATOR_HOST,
    ) {
        auth.useEmulator(host, AUTH_PORT)
        firestore.useEmulator(host, FIRESTORE_PORT)
        storage.useEmulator(host, STORAGE_PORT)
        isEmulatorMode = true
    }

    /** Resets emulator mode flag for testing isolated helper states. */
    internal fun resetEmulatorModeForTesting() {
        isEmulatorMode = false
        configuredFirestore = null
    }
}
