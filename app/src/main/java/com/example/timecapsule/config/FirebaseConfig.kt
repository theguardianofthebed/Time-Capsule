package com.example.timecapsule.config

import android.content.Context
import com.example.timecapsule.data.adapter.FirestoreCachePolicy
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * Utility for verifying Firebase configuration availability and setting up local emulator connections.
 * Enforces local emulator isolation and prevents silent fallback to cloud production services (DD-20, NFR-06).
 */
object FirebaseConfig {

    const val EMULATOR_HOST = "10.0.2.2"
    const val LOCALHOST = "127.0.0.1"
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
     * Safely connects Auth, Firestore, and Storage to local emulators ONLY in Debug builds.
     * Initializes a demo [FirebaseApp] if google-services.json is missing in debug/test environments.
     * Returns true only if initialization and emulator binding both succeed successfully.
     * Fails closed if memory-only Firestore initialization fails.
     */
    fun setupDebugEmulatorConnection(context: Context, isDebug: Boolean): Boolean {
        if (!isDebug) {
            // Release builds MUST NOT connect to local emulators
            return false
        }

        if (isEmulatorMode) {
            return true
        }

        return try {
            val appContext = context.applicationContext ?: context
            if (!isFirebaseAvailable(appContext)) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("demo-timecapsule")
                    .setApplicationId("1:1234567890:android:abcdef123456")
                    .setApiKey("AIzaSyDemoKeyForTestingOnly00000000000")
                    .build()
                FirebaseApp.initializeApp(appContext, options)
            }

            val auth = FirebaseAuth.getInstance()
            val firestore = getMemoryOnlyFirestore(appContext) ?: return false
            val storage = FirebaseStorage.getInstance()

            useEmulators(auth, firestore, storage, EMULATOR_HOST)
            isEmulatorMode = true
            true
        } catch (_: Exception) {
            isEmulatorMode = false
            false
        }
    }

    /**
     * Explicitly connects Auth, Firestore, and Storage instances to local emulator endpoints.
     * Enforces local emulator mode and prevents silent fallback to production services.
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

    /**
     * Connects Auth and Firestore instances to local emulator for testing or debug builds.
     */
    fun useEmulatorsForAuthAndFirestore(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        host: String = EMULATOR_HOST,
    ) {
        auth.useEmulator(host, AUTH_PORT)
        firestore.useEmulator(host, FIRESTORE_PORT)
        isEmulatorMode = true
    }

    /**
     * Verifies that emulator mode is active; throws [IllegalStateException] if disabled or disconnected
     * to prevent silent fallback to production backend services.
     */
    fun verifyEmulatorOrFail(isEmulatorConnected: Boolean) {
        if (!isEmulatorMode || !isEmulatorConnected) {
            throw IllegalStateException(
                "Emulator safety failure: emulator mode is inactive or disconnected. Silent fallback to production is prohibited."
            )
        }
    }

    /** Resets emulator mode flag for testing isolated helper states. */
    internal fun resetEmulatorModeForTesting() {
        isEmulatorMode = false
        configuredFirestore = null
    }
}
