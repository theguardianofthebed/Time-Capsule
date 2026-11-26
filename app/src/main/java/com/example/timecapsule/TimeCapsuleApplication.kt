package com.example.timecapsule

import android.app.Application
import android.content.Context
import com.example.timecapsule.config.FirebaseConfig
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.DefaultCapsuleRepository
import com.example.timecapsule.data.adapter.DefaultAuthAdapter
import com.example.timecapsule.data.adapter.DefaultFirestoreAdapter
import com.example.timecapsule.data.adapter.DefaultLocalRetentionAdapter
import com.example.timecapsule.data.adapter.DefaultLocationAdapter
import com.example.timecapsule.data.adapter.DefaultStorageAdapter
import com.example.timecapsule.data.adapter.FirestoreCapsulePublication
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.cleanup.AccountCleanupOrchestrator
import kotlinx.coroutines.runBlocking
import com.example.timecapsule.data.di.AppDependencyContainer
import com.example.timecapsule.data.media.AndroidOriginalImageProbe
import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.OriginalPhotoUploads
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.notification.DefaultNotificationAdapter
import com.example.timecapsule.data.notification.NotificationAttemptStore
import com.example.timecapsule.data.notification.UnlockNotificationCoordinator
import com.example.timecapsule.data.publication.CapsulePublicationCoordinator
import com.example.timecapsule.data.publication.FrozenOperationJournal
import com.example.timecapsule.data.publication.OriginalPublicationMedia
import com.example.timecapsule.data.publication.PrivatePublicationDrafts
import com.example.timecapsule.data.recovery.ForegroundRecoveryCoordinator
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File

/**
 * Application class for TimeCapsule holding process-wide application dependencies.
 * Ensures a single shared [AppDependencyContainer], [DefaultSessionTracker], and [ForegroundRecoveryCoordinator]
 * survive Activity configuration changes (device rotation) without creating duplicate singletons or session drift.
 */
class TimeCapsuleApplication : Application() {

    @Volatile
    var container: AppDependencyContainer? = null
        private set

    override fun onCreate() {
        super.onCreate()
        initializeFirebaseAndContainer()
    }

    /**
     * Initializes Firebase configuration and creates the process-wide [AppDependencyContainer].
     *
     * Ordering & Safety Rules:
     * 1. In DEBUG builds: Connects Auth, Firestore, and Storage to local emulators BEFORE instantiating Firebase SDK singletons.
     * 2. In RELEASE builds: Verifies default FirebaseApp availability before accessing services.
     * 3. Applies [com.example.timecapsule.data.adapter.FirestoreCachePolicy.applyMemoryOnlyCache] via [FirebaseConfig.getMemoryOnlyFirestore].
     * 4. Returns true on success; if Firebase initialization fails, leaves [container] null and fails closed without crashing.
     */
    @Synchronized
    fun initializeFirebaseAndContainer(context: Context? = null): Boolean {
        if (container != null) {
            return true
        }

        val targetContext = context ?: try { applicationContext } catch (_: Exception) { null }
        if (targetContext == null) {
            container = null
            return false
        }

        val isDebug = BuildConfig.DEBUG
        if (isDebug) {
            val emulatorInitialized = FirebaseConfig.setupDebugEmulatorConnection(targetContext, true)
            if (!emulatorInitialized) {
                container = null
                return false
            }
        } else {
            if (!FirebaseConfig.isFirebaseAvailable(targetContext)) {
                container = null
                return false
            }
        }

        return try {
            container = createContainer(targetContext)
            true
        } catch (_: Exception) {
            container = null
            false
        }
    }

    /** Creates the process-wide [AppDependencyContainer] with application context dependencies. */
    private fun createContainer(context: Context): AppDependencyContainer {
        val sessionTracker = DefaultSessionTracker()
        val authAdapter = DefaultAuthAdapter()
        val storageAdapter = DefaultStorageAdapter(
            FirebaseStorage.getInstance(),
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance()
        )
        val firestoreAdapter = DefaultFirestoreAdapter({ context }, storageAdapter)

        val noBackupDir = context.noBackupFilesDir ?: File(context.filesDir, "no_backup")

        val notificationAdapter = DefaultNotificationAdapter(context)
        val notificationAttemptStore = NotificationAttemptStore(File(noBackupDir, "notification-attempts.json"))
        val unlockNotificationCoordinator = UnlockNotificationCoordinator(
            notificationAdapter = notificationAdapter,
            attemptStore = notificationAttemptStore,
            sessionTracker = sessionTracker
        )

        val retentionAdapter = DefaultLocalRetentionAdapter(
            root = File(noBackupDir, "retained-content"),
            session = sessionTracker::currentSession
        )
        val operationJournal = FrozenOperationJournal(
            root = File(noBackupDir, "capsule-operations"),
            session = sessionTracker::currentSession
        )
        val originalPhotoStore = OriginalPhotoStore(
            root = File(noBackupDir, "original-photos"),
            probe = AndroidOriginalImageProbe(),
            session = sessionTracker::currentSession
        )
        val photoDraftJournal = PhotoDraftJournal(
            root = File(noBackupDir, "photo-drafts"),
            session = sessionTracker::currentSession
        )

        val accountCleanupOrchestrator = AccountCleanupOrchestrator(
            retentionAdapter = retentionAdapter,
            journal = operationJournal,
            originalPhotoStore = originalPhotoStore,
            photoDraftJournal = photoDraftJournal,
            unlockNotificationCoordinator = unlockNotificationCoordinator,
            sessionTracker = sessionTracker,
            noBackupDir = noBackupDir,
            frozenPhotosDir = File(noBackupDir, "frozen-photos")
        )

        val accountSessionManager = AccountSessionManager(
            authAdapter = authAdapter,
            firestoreAdapter = firestoreAdapter,
            sessionTracker = sessionTracker,
            onAccountCleared = { uid ->
                accountCleanupOrchestrator.cleanupAccount(uid)
            },
            isCleanupPending = accountCleanupOrchestrator::hasPendingCleanup
        )

        val photoUploads = OriginalPhotoUploads(
            originalPhotoStore,
            File(noBackupDir, "frozen-photos"),
            storageAdapter,
            sessionTracker::currentSession
        )
        val locationAdapter = DefaultLocationAdapter(context)

        val publication = firestoreAdapter.firestoreInstance()?.let { firestore ->
            CapsulePublicationCoordinator(
                journal = operationJournal,
                backend = FirestoreCapsulePublication(firestore, FirebaseAuth.getInstance()),
                media = OriginalPublicationMedia(photoUploads),
                session = sessionTracker::currentSession,
                drafts = PrivatePublicationDrafts(
                    originalPhotoStore,
                    photoDraftJournal,
                    sessionTracker::currentSession
                )
            )
        }

        val startupCleanupResult = runBlocking {
            accountCleanupOrchestrator.cleanupInterruptedSessions(authAdapter.activeUid())
        }
        if (startupCleanupResult !is OperationResult.Success) {
            sessionTracker.clearSession()
        }

        val repository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = authAdapter,
            firestoreAdapter = firestoreAdapter,
            publicationCoordinator = publication,
            locationAdapter = locationAdapter,
            retentionAdapter = retentionAdapter
        )

        val recoveryCoordinator = ForegroundRecoveryCoordinator(
            repository = repository,
            sessionTracker = sessionTracker,
            unlockNotificationCoordinator = unlockNotificationCoordinator
        )

        return AppDependencyContainer(
            sessionTracker = sessionTracker,
            authAdapter = authAdapter,
            firestoreAdapter = firestoreAdapter,
            storageAdapter = storageAdapter,
            locationAdapter = locationAdapter,
            retentionAdapter = retentionAdapter,
            repository = repository,
            accountSessionManager = accountSessionManager,
            photoUploads = photoUploads,
            recoveryCoordinator = recoveryCoordinator,
            notificationAdapter = notificationAdapter,
            notificationAttemptStore = notificationAttemptStore,
            unlockNotificationCoordinator = unlockNotificationCoordinator
        )
    }

    /** Allows injecting a custom test container for deterministic instrumentation tests. */
    fun setContainerForTesting(testContainer: AppDependencyContainer) {
        container = testContainer
    }
}
