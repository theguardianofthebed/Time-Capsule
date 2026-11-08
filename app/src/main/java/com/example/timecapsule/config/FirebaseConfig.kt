package com.example.timecapsule.config

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * Utility for verifying Firebase configuration availability and setting up local emulator connections.
 */
object FirebaseConfig {

    const val EMULATOR_HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
    const val STORAGE_PORT = 9199

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
     * Connects Auth, Firestore, and Storage instances to local emulator endpoints for testing.
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
    }
}
