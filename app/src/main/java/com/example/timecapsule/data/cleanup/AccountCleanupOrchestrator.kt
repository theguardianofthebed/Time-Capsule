package com.example.timecapsule.data.cleanup

import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.notification.UnlockNotificationCoordinator
import com.example.timecapsule.data.publication.FrozenOperationJournal
import com.example.timecapsule.data.session.SessionTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Orchestrator coordinating process-wide account cleanup and transition protection across all local stores.
 *
 * Cleanup Boundaries & Requirements (DD-18, AC-13):
 * 1. Sequential Execution: Runs BEFORE any new account session becomes active in [SessionTracker].
 * 2. Multi-Store Coverage:
 *    - Retained Content Store: Purges app-private verified content and markers for former UID.
 *    - Frozen Operation Journal: Compacts unresolved operations to opaque recovery records (scrubbing text/photo payload)
 *      and deletes resolved/abandoned records.
 *    - Private Media Stores: Purges private original photo store and photo draft journals for former UID.
 *    - Local Notifications: Cancels active notifications and clears attempt markers for former UID.
 * 3. Thread Safety & Mutex Protection: Serializes cleanup calls to prevent concurrent account transitions from racing.
 *
 * @param retentionAdapter Adapter managing UID-scoped retained content.
 * @param journal Operation journal for submission records and opaque recovery compaction.
 * @param originalPhotoStore Store holding private original photo files.
 * @param photoDraftJournal Store holding editable compose photo drafts.
 * @param unlockNotificationCoordinator Coordinator managing account notifications.
 * @param sessionTracker Process-wide session tracker for active session validation.
 */
class AccountCleanupOrchestrator(
    private val retentionAdapter: LocalRetentionAdapter,
    private val journal: FrozenOperationJournal? = null,
    private val originalPhotoStore: OriginalPhotoStore? = null,
    private val photoDraftJournal: PhotoDraftJournal? = null,
    private val unlockNotificationCoordinator: UnlockNotificationCoordinator? = null,
    private val sessionTracker: SessionTracker? = null
) {

    private val cleanupMutex = Mutex()

    /**
     * Executes complete protected local data cleanup for [uid] during logout or account switch.
     * Must be invoked BEFORE a new account session is activated in [SessionTracker].
     *
     * @param uid The former account UID being cleaned up.
     */
    suspend fun cleanupAccount(uid: String): Unit = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return@withContext

        cleanupMutex.withLock {
            // 1. Purge retained content and verified markers
            try {
                retentionAdapter.clearAccountData(cleanUid)
            } catch (_: Exception) { }

            // 2. Compact publication journal to opaque recovery records for unresolved sends
            try {
                journal?.compactAccountDataToOpaque(cleanUid)
            } catch (_: Exception) { }

            // 3. Purge private photo files
            try {
                originalPhotoStore?.clearAccountData(cleanUid)
            } catch (_: Exception) { }

            // 4. Purge photo compose draft journal
            try {
                photoDraftJournal?.clearAccountData(cleanUid)
            } catch (_: Exception) { }

            // 5. Invalidate notifications and cancel alerts
            try {
                unlockNotificationCoordinator?.invalidateAccount(cleanUid)
            } catch (_: Exception) { }
        }
    }

    /**
     * Checks for interrupted cleanup sessions on application startup and ensures former account state is clean.
     *
     * @param activeUid The currently authenticated UID restored from Auth, or null if unauthenticated.
     */
    suspend fun cleanupInterruptedSessions(activeUid: String?): Unit = withContext(Dispatchers.IO) {
        cleanupMutex.withLock {
            try {
                val currentSessionUid = sessionTracker?.currentSession()?.uid
                if (!activeUid.isNullOrBlank() && currentSessionUid != null && currentSessionUid != activeUid) {
                    cleanupAccount(currentSessionUid)
                }
            } catch (_: Exception) {
                // Isolates startup cleanup exceptions
            }
        }
    }
}
