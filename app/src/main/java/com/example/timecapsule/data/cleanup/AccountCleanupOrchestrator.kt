package com.example.timecapsule.data.cleanup

import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.media.OriginalPhotoStore
import com.example.timecapsule.data.media.PhotoDraftJournal
import com.example.timecapsule.data.notification.UnlockNotificationCoordinator
import com.example.timecapsule.data.publication.FrozenOperationJournal
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
 * @param noBackupDir Non-backed-up directory for storing durable pending cleanup markers.
 * @param frozenPhotosDir Base directory holding frozen photo uploads.
 */
class AccountCleanupOrchestrator(
    private val retentionAdapter: LocalRetentionAdapter,
    private val journal: FrozenOperationJournal? = null,
    private val originalPhotoStore: OriginalPhotoStore? = null,
    private val photoDraftJournal: PhotoDraftJournal? = null,
    private val unlockNotificationCoordinator: UnlockNotificationCoordinator? = null,
    private val noBackupDir: File,
    private val frozenPhotosDir: File? = null
) {

    /** Connects auth transitions to the same durable store for marking, recovery and purging. */
    fun sessionManager(auth: AuthAdapter, firestore: FirestoreAdapter, tracker: SessionTracker) =
        AccountSessionManager(
            auth, firestore, tracker,
            onAccountCleared = ::cleanupAccount,
            isCleanupPending = ::hasPendingCleanup,
            markCleanupPending = ::markCleanupPending,
            getPendingCleanupUid = ::getPendingCleanupUid
        )

    companion object {
        private val cleanupMutex = Mutex()
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
        cleanupMutex.withLock {
            try {
                val obligations = pendingUids()
                val target = uid.ifBlank { obligations.firstOrNull().orEmpty() }
                if (target.isBlank()) return@withLock OperationResult.Success(Unit)
                if (target !in obligations) persistUids(obligations + target)
                val result = performCleanupInternal(target)
                if (result is OperationResult.Success) persistUids(pendingUids() - target)
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                OperationResult.Unavailable("Cannot complete account cleanup: ${error.message}")
            }
        }
    }

    /** Returns true if a pending cleanup marker file exists on disk. */
    fun hasPendingCleanup(): Boolean {
        val markerFile = pendingMarkerFile()
        return markerFile.exists()
    }

    /**
     * Reads the pending cleanup UID from `pending-cleanup.properties` if present.
     */
    fun getPendingCleanupUid(): String {
        return try {
            pendingUids().firstOrNull().orEmpty()
        } catch (_: IOException) {
            ""
        } catch (_: IllegalArgumentException) {
            ""
        }
    }

    /**
     * Durably persists a pending cleanup marker for [uid] BEFORE former session invalidation or store purges.
     */
    suspend fun markCleanupPending(uid: String): Unit = withContext(Dispatchers.IO) {
        val cleanUid = uid.trim()
        if (cleanUid.isNotBlank()) {
            cleanupMutex.withLock {
                val existing = pendingUids()
                if (cleanUid !in existing) persistUids(existing + cleanUid)
            }
        }
    }

    /**
     * Internal cleanup implementation executing all store purges while holding [cleanupMutex].
     */
    private suspend fun performCleanupInternal(cleanUid: String): OperationResult<Unit> {
        var hasError = false
        var firstErrorMessage: String? = null

        // Purge retained content and verified markers
        try {
            retentionAdapter.clearAccountData(cleanUid)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Retained content cleanup failed: ${e.message}"
        }

        // Compact publication journal to opaque recovery records for unresolved sends
        try {
            journal?.compactAccountDataToOpaque(cleanUid)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Journal compaction failed: ${e.message}"
        }

        // Purge private original photos
        try {
            originalPhotoStore?.clearAccountData(cleanUid)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Original photo store cleanup failed: ${e.message}"
        }

        // Purge compose draft journal
        try {
            photoDraftJournal?.clearAccountData(cleanUid)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Photo draft cleanup failed: ${e.message}"
        }

        // Purge frozen photo upload journals across all capsule directories
        try {
            frozenPhotosDir?.let { dir ->
                if (dir.exists()) {
                    val capsuleDirs = dir.listFiles() ?: throw IOException("Cannot enumerate frozen uploads.")
                    for (capsuleDir in capsuleDirs.filter { it.isDirectory }) {
                        PhotoDraftJournal(capsuleDir).clearAccountData(cleanUid)
                        val remaining = capsuleDir.listFiles() ?: throw IOException("Cannot inspect frozen upload.")
                        if (remaining.isEmpty() && !capsuleDir.delete() && capsuleDir.exists()) {
                            throw IOException("Cannot remove empty frozen upload directory.")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Frozen photo upload cleanup failed: ${e.message}"
        }

        // Invalidate notifications and cancel alerts
        try {
            unlockNotificationCoordinator?.invalidateAccount(cleanUid)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            hasError = true
            if (firstErrorMessage == null) firstErrorMessage = "Notification invalidation failed: ${e.message}"
        }

        return if (!hasError) {
            OperationResult.Success(Unit)
        } else {
            OperationResult.Unavailable(firstErrorMessage ?: "Account cleanup failed for $cleanUid")
        }
    }

    private fun pendingMarkerFile(): File = File(noBackupDir, PENDING_CLEANUP_FILE)

    /** Reads the oldest obligation first, including markers written by earlier releases. */
    private fun pendingUids(): List<String> {
        val file = pendingMarkerFile()
        if (!file.exists()) return emptyList()
        val fields = Properties().apply { file.inputStream().use { load(it) } }
        val first = fields.getProperty("pendingUid")?.takeIf { it.isNotBlank() }
            ?: throw IOException("Malformed pending cleanup marker.")
        return listOf(first) + fields.stringPropertyNames().filter { it.startsWith("additional.") }
            .sorted().map { fields.getProperty(it).also { uid -> require(uid.isNotBlank()) } }
    }

    /** Atomically replaces the queue after recording an account or finishing its last purge. */
    private fun persistUids(uids: List<String>) {
        val markerFile = pendingMarkerFile()
        if (uids.isEmpty()) {
            removePendingMarker()
            return
        }
        val parent = markerFile.parentFile ?: throw IOException("Invalid cleanup directory.")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create cleanup directory.")
        val properties = Properties().apply {
            setProperty("pendingUid", uids.first())
            uids.drop(1).forEachIndexed { index, uid -> setProperty("additional.$index", uid) }
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
        if (!markerFile.exists()) {
            throw IOException("Failed to create pending cleanup marker.")
        }
    }

    private fun removePendingMarker() {
        val markerFile = pendingMarkerFile()
        if (markerFile.exists()) {
            try {
                Files.deleteIfExists(markerFile.toPath())
            } catch (_: Exception) {
                markerFile.delete()
            }
            if (markerFile.exists()) {
                throw IOException("Failed to delete pending cleanup marker.")
            }
        }
    }
}
