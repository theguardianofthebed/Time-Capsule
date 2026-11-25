package com.example.timecapsule.data.cleanup

import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.notification.UnlockNotificationCoordinator
import com.example.timecapsule.data.publication.FrozenOperationJournal
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Orchestrator coordinating process-wide account cleanup and transition protection across all local stores.
 *
 * Fail-Closed & Durable Interrupted-Cleanup Architecture (DD-18, AC-13):
 * 1. Fail-Closed Execution: Returns [OperationResult.Unavailable] if any store cleanup fails.
 *    New account sessions MUST NOT be activated when cleanup fails.
 * 2. Durable Pending Cleanup Marker: Writes `pending-cleanup.properties` before starting cleanup.
 *    The marker is deleted ONLY when all store cleanups succeed completely.
 * 3. Process Recreation Recovery: Startup checks for `pending-cleanup.properties` and executes pending
 *    cleanups before allowing protected application operations.
 * 4. Multi-Store Coverage: Retained content, operation journal compaction, original photo store,
 *    photo draft journal, frozen uploads, and notifications.
 *
 * @param retentionAdapter Adapter managing UID-scoped retained content.
 * @param journal Operation journal for submission records and opaque recovery compaction.
 * @param originalPhotoStore Store holding private original photo files.
 * @param photoDraftJournal Store holding editable compose photo drafts.
 * @param unlockNotificationCoordinator Coordinator managing account notifications.
 * @param sessionTracker Process-wide session tracker for active session validation.
 * @param noBackupDir Non-backed-up directory for storing durable pending cleanup markers.
 * @param frozenPhotosDir Base directory holding frozen photo uploads.
 */
class AccountCleanupOrchestrator(
    private val retentionAdapter: LocalRetentionAdapter,
    private val journal: FrozenOperationJournal? = null,
    private val originalPhotoStore: OriginalPhotoStore? = null,
    private val photoDraftJournal: PhotoDraftJournal? = null,
    private val unlockNotificationCoordinator: UnlockNotificationCoordinator? = null,
    private val sessionTracker: SessionTracker? = null,
    private val noBackupDir: File? = null,
    private val frozenPhotosDir: File? = null
) {

    private val cleanupMutex = Mutex()

    companion object {
        private const val PENDING_CLEANUP_FILE = "pending-cleanup.properties"
    }

    /**
     * Executes complete protected local data cleanup for [uid] during logout or account switch.
     * Fails closed: Returns [OperationResult.Unavailable] if any store cleanup fails or is interrupted.
     *
     * @param uid The former account UID being cleaned up.
     * @return [OperationResult.Success] on complete cleanup; [OperationResult.Unavailable] on failure.
     */
    suspend fun cleanupAccount(uid: String): OperationResult<Unit> = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return@withContext OperationResult.Success(Unit)

        cleanupMutex.withLock {
            performCleanupInternal(cleanUid)
        }
    }

    /** Returns true if a pending cleanup marker file exists on disk. */
    fun hasPendingCleanup(): Boolean {
        val markerFile = pendingMarkerFile() ?: return false
        return markerFile.exists()
    }

    /**
     * Checks for interrupted cleanup sessions on application startup and ensures former account state is clean.
     * Fails closed: Returns [OperationResult.Unavailable] if malformed marker or startup cleanup fails.
     *
     * @param activeUid The currently authenticated UID restored from Auth, or null if unauthenticated.
     */
    suspend fun cleanupInterruptedSessions(activeUid: String?): OperationResult<Unit> = withContext(Dispatchers.IO) {
        cleanupMutex.withLock {
            try {
                val markerFile = pendingMarkerFile()
                if (markerFile != null && markerFile.exists()) {
                    val pendingUid = try {
                        val properties = Properties().apply {
                            markerFile.inputStream().use { load(it) }
                        }
                        properties.getProperty("pendingUid") ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    if (pendingUid.isNotBlank()) {
                        val result = performCleanupInternal(pendingUid)
                        if (result !is OperationResult.Success) {
                            return@withLock result
                        }
                    } else {
                        return@withLock OperationResult.Unavailable("Malformed pending cleanup marker found on startup.")
                    }
                }

                val currentSessionUid = sessionTracker?.currentSession()?.uid
                if (!activeUid.isNullOrBlank() && currentSessionUid != null && currentSessionUid != activeUid) {
                    performCleanupInternal(currentSessionUid)
                } else {
                    OperationResult.Success(Unit)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                OperationResult.Unavailable("Startup cleanup recovery failed: ${error.message}")
            }
        }
    }

    /**
     * Internal cleanup implementation executing all store purges while holding [cleanupMutex].
     */
    private suspend fun performCleanupInternal(cleanUid: String): OperationResult<Unit> {
        var hasError = false
        var firstErrorMessage: String? = null

        // 1. Record durable pending cleanup marker before modifying stores
        try {
            writePendingMarker(cleanUid)
        } catch (e: Exception) {
            hasError = true
            firstErrorMessage = "Failed to record pending cleanup marker: ${e.message}"
        }

        // 2. Purge retained content and verified markers
        try {
            retentionAdapter.clearAccountData(cleanUid)
        } catch (e: Exception) {
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Retained content cleanup failed: ${e.message}"
        }

        // 3. Compact publication journal to opaque recovery records for unresolved sends
        try {
            journal?.compactAccountDataToOpaque(cleanUid)
        } catch (e: Exception) {
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Journal compaction failed: ${e.message}"
        }

        // 4. Purge private original photos
        try {
            originalPhotoStore?.clearAccountData(cleanUid)
        } catch (e: Exception) {
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Original photo store cleanup failed: ${e.message}"
        }

        // 5. Purge compose draft journal
        try {
            photoDraftJournal?.clearAccountData(cleanUid)
        } catch (e: Exception) {
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Photo draft cleanup failed: ${e.message}"
        }

        // 6. Purge frozen photo upload journals across all capsule directories
        try {
            frozenPhotosDir?.let { dir ->
                if (dir.exists()) {
                    val capsuleDirs = dir.listFiles()
                    if (capsuleDirs != null) {
                        for (capsuleDir in capsuleDirs) {
                            if (capsuleDir.isDirectory) {
                                val journal = PhotoDraftJournal(capsuleDir)
                                val draft = try { journal.load(cleanUid) } catch (_: Exception) { null }
                                draft?.photo?.let { photo ->
                                    try {
                                        originalPhotoStore?.file(photo)?.delete()
                                    } catch (_: Exception) {}
                                }
                                journal.clearAccountData(cleanUid)
                                val remainingFiles = capsuleDir.listFiles()
                                if (remainingFiles != null && remainingFiles.isEmpty()) {
                                    capsuleDir.delete()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Frozen photo upload cleanup failed: ${e.message}"
        }

        // 7. Invalidate notifications and cancel alerts
        try {
            unlockNotificationCoordinator?.invalidateAccount(cleanUid)
        } catch (e: Exception) {
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Notification invalidation failed: ${e.message}"
        }

        return if (!hasError) {
            removePendingMarker()
            OperationResult.Success(Unit)
        } else {
            OperationResult.Unavailable(firstErrorMessage ?: "Account cleanup failed for $cleanUid")
        }
    }

    private fun pendingMarkerFile(): File? {
        val dir = noBackupDir ?: return null
        return File(dir, PENDING_CLEANUP_FILE)
    }

    private fun writePendingMarker(uid: String) {
        val markerFile = pendingMarkerFile() ?: return
        val parent = markerFile.parentFile ?: return
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Cannot create directory for pending cleanup marker.")
        }
        val properties = Properties().apply {
            setProperty("pendingUid", uid)
            setProperty("timestamp", System.currentTimeMillis().toString())
        }
        val temp = File.createTempFile("pending-", ".partial", parent)
        try {
            temp.outputStream().use { output ->
                properties.store(output, null)
                output.fd.sync()
            }
            Files.move(
                temp.toPath(),
                markerFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temp.delete()
        }
    }

    private fun removePendingMarker() {
        val markerFile = pendingMarkerFile() ?: return
        if (markerFile.exists()) {
            try {
                Files.deleteIfExists(markerFile.toPath())
            } catch (_: Exception) {
                markerFile.delete()
            }
        }
    }
}
