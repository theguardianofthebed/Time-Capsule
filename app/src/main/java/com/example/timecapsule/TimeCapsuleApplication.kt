package com.example.timecapsule

import android.app.Application
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.DefaultCapsuleRepository
import com.example.timecapsule.data.adapter.DefaultAuthAdapter
import com.example.timecapsule.data.adapter.DefaultFirestoreAdapter
import com.example.timecapsule.data.adapter.DefaultLocationAdapter
import com.example.timecapsule.data.adapter.DefaultStorageAdapter
import com.example.timecapsule.data.adapter.FirestoreCapsulePublication
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.di.AppDependencyContainer
import com.example.timecapsule.data.media.AndroidOriginalImageProbe
import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.OriginalPhotoUploads
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.publication.CapsulePublicationCoordinator
import com.example.timecapsule.data.publication.FrozenOperationJournal
import com.example.timecapsule.data.publication.OriginalPublicationMedia
import com.example.timecapsule.data.publication.PrivatePublicationDrafts
import com.example.timecapsule.data.recovery.ForegroundRecoveryCoordinator
import com.example.timecapsule.data.session.DefaultSessionTracker
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

    lateinit var container: AppDependencyContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    /** Creates the process-wide [AppDependencyContainer] with application context dependencies. */
    private fun createContainer(): AppDependencyContainer {
        val sessionTracker = DefaultSessionTracker()
        val authAdapter = DefaultAuthAdapter()
        val firestoreAdapter = DefaultFirestoreAdapter { applicationContext }
        val accountSessionManager = AccountSessionManager(authAdapter, firestoreAdapter, sessionTracker)

        val storageAdapter = DefaultStorageAdapter(
            FirebaseStorage.getInstance(),
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance()
        )
        val photoUploads = OriginalPhotoUploads(
            OriginalPhotoStore(File(noBackupFilesDir, "original-photos"), AndroidOriginalImageProbe()),
            File(noBackupFilesDir, "frozen-photos"),
            storageAdapter,
            sessionTracker::currentSession
        )
        val locationAdapter = DefaultLocationAdapter(applicationContext)
        val retentionAdapter = object : LocalRetentionAdapter {
            override suspend fun clearAccountData(uid: String) {}
        }

        val publication = firestoreAdapter.firestoreInstance()?.let { firestore ->
            CapsulePublicationCoordinator(
                journal = FrozenOperationJournal(File(noBackupFilesDir, "capsule-operations")),
                backend = FirestoreCapsulePublication(firestore, FirebaseAuth.getInstance()),
                media = OriginalPublicationMedia(photoUploads),
                session = sessionTracker::currentSession,
                drafts = PrivatePublicationDrafts(
                    OriginalPhotoStore(File(noBackupFilesDir, "original-photos"), AndroidOriginalImageProbe()),
                    PhotoDraftJournal(File(noBackupFilesDir, "photo-drafts"))
                )
            )
        }
        val repository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = authAdapter,
            firestoreAdapter = firestoreAdapter,
            publicationCoordinator = publication,
            locationAdapter = locationAdapter
        )

        val recoveryCoordinator = ForegroundRecoveryCoordinator(
            repository = repository,
            sessionTracker = sessionTracker
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
            recoveryCoordinator = recoveryCoordinator
        )
    }

    /** Allows injecting a custom test container for deterministic instrumentation tests. */
    fun setContainerForTesting(testContainer: AppDependencyContainer) {
        container = testContainer
    }
}
