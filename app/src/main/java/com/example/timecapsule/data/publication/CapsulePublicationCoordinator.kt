package com.example.timecapsule.data.publication

import com.example.timecapsule.data.media.OriginalPhotoUploads
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.FrozenOperationRecord
import com.example.timecapsule.domain.model.FrozenPhotoUpload
import com.example.timecapsule.domain.model.PhotoUploadAcknowledgment
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Replaceable #13 handoff, keeping deterministic publication tests independent of Firebase. */
interface PublicationMedia {
    /** Makes the stable private upload copy without starting network publication. */
    suspend fun freeze(photo: RetainedOriginalPhoto): OperationResult<FrozenPhotoUpload>
    /** Returns only a matching confirmed upload, not a capsule publication result. */
    suspend fun upload(photo: FrozenPhotoUpload): OperationResult<PhotoUploadAcknowledgment>
}

/** Delegates photo handling to the existing #13 implementation instead of duplicating it. */
class OriginalPublicationMedia(private val uploads: OriginalPhotoUploads) : PublicationMedia {
    /** Reuses #13's persisted frozen image identity. */
    override suspend fun freeze(photo: RetainedOriginalPhoto) = uploads.freeze(photo)
    /** Reuses #13's authenticated byte/acknowledgment checks. */
    override suspend fun upload(photo: FrozenPhotoUpload) = uploads.upload(photo)
}

/**
 * Saves before network work, waits for photo acknowledgment and then publishes one atomic batch.
 * Repeated local taps join the same attempt. An interrupted caller does not cancel an SDK write.
 * Reconciliation/replay of restored uncertain records is deliberately handled by the recovery step.
 */
class CapsulePublicationCoordinator(
    private val journal: FrozenOperationJournal,
    private val backend: CapsulePublicationBackend,
    private val media: PublicationMedia?,
    private val session: () -> SessionSnapshot,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> String = { ZoneId.systemDefault().id }
) {
    /** Identifies one process-wide attempt without sharing records between different private stores. */
    private data class AttemptKey(val store: String, val uid: String, val capsuleId: String)

    companion object {
        private val registration = Mutex()
        private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val active = java.util.concurrent.ConcurrentHashMap<
            AttemptKey, Deferred<OperationResult<String>>>()
    }

    /** Validates a submission, persists it and joins/starts its one active operation. */
    suspend fun publish(draft: CapsuleDraft): OperationResult<String> = withContext(Dispatchers.IO) {
        val captured = session()
        val uid = captured.uid
        if (uid.isNullOrBlank()) return@withContext denied()
        try {
            val candidate = FrozenCapsuleOperation.freeze(draft, uid, zone(), now())
            val key = AttemptKey(journal.storageIdentity, uid, candidate.capsuleId)
            var result: OperationResult<String>? = null
            val attempt = registration.withLock {
                val previous = journal.load(uid, candidate.capsuleId)
                if (previous != null &&
                    previous.operation.canonicalPayload() != candidate.canonicalPayload()) {
                    result = OperationResult.Invalid("Submitted capsule cannot be edited. Resolve its outcome first.")
                    return@withLock null
                }
                active[key]?.let { return@withLock it }
                if (previous != null) {
                    result = if (previous.phase == PublicationPhase.SENT) {
                        OperationResult.Success(candidate.capsuleId)
                    } else {
                        OperationResult.OutcomeUnknown(candidate.capsuleId,
                            "A saved send requires server reconciliation before another attempt.")
                    }
                    return@withLock null
                }
                // If this durable write fails, neither photo upload nor Firestore is contacted.
                journal.save(FrozenOperationRecord(candidate))
                val started = work.async(start = CoroutineStart.LAZY) { execute(candidate, captured) }
                active[key] = started
                started.invokeOnCompletion { active.remove(key, started) }
                started.start()
                started
            }
            val outcome = attempt?.await() ?: result ?: error("Missing publication result.")
            if (session() != captured) denied() else outcome
        } catch (cancelled: CancellationException) {
            // The separate attempt keeps running; no definite failure or deletion is inferred.
            throw cancelled
        } catch (invalid: IllegalArgumentException) {
            OperationResult.Invalid(invalid.message ?: "Invalid capsule.")
        } catch (error: Exception) {
            OperationResult.Unavailable("Cannot prepare saved capsule. Check storage and retry.")
        }
    }

    /** Runs in process-owned scope so screen cancellation cannot turn a pending write into failure. */
    private suspend fun execute(
        initial: FrozenCapsuleOperation,
        captured: SessionSnapshot
    ): OperationResult<String> {
        var operation = initial
        var submitted = false
        try {
            if (session() != captured) return failBeforePublication(operation, denied())
            initial.photo?.let { original ->
                val handler = media ?: return failBeforePublication(operation,
                    OperationResult.Unavailable("Photo service is unavailable."))
                val frozen = when (val result = handler.freeze(original)) {
                    is OperationResult.Success -> result.data
                    else -> return failBeforePublication(operation, photoFailure(result))
                }
                require(frozen.objectPath == initial.photoObjectPath)
                require(frozen.original.sha256 == original.sha256 &&
                    frozen.original.sizeBytes == original.sizeBytes &&
                    frozen.original.format == original.format)
                operation = initial.copy(photo = frozen.original)
                // The separate retained filename changes, but immutable server content/digest does not.
                journal.save(FrozenOperationRecord(operation, PublicationPhase.UPLOADING))
                if (session() != captured) return failBeforePublication(operation, denied())
                val ack = when (val result = handler.upload(frozen)) {
                    is OperationResult.Success -> result.data
                    else -> return failBeforePublication(operation, photoFailure(result))
                }
                require(ack.objectPath == operation.photoObjectPath &&
                    ack.sizeBytes == original.sizeBytes && ack.sha256 == original.sha256) {
                    "Photo acknowledgment does not match the submitted original."
                }
            }
            if (session() != captured) return failBeforePublication(operation, denied())
            journal.save(FrozenOperationRecord(operation, PublicationPhase.PUBLISHING))
            if (session() != captured) return failBeforePublication(operation, denied())
            // From here onward an exception is uncertain until server reconciliation settles it.
            submitted = true
            backend.publish(operation)
            journal.save(FrozenOperationRecord(operation, PublicationPhase.SENT))
            return if (session() == captured) OperationResult.Success(operation.capsuleId) else denied()
        } catch (cancelled: CancellationException) {
            recordFailure(operation, submitted)
            throw cancelled
        } catch (error: Exception) {
            recordFailure(operation, submitted)
            return if (submitted) OperationResult.OutcomeUnknown(operation.capsuleId,
                "Publication acknowledgment is uncertain. Check the same capsule before retrying.")
            else if (error is IllegalArgumentException) OperationResult.Invalid(
                error.message ?: "Photo does not match the submitted capsule.")
            else OperationResult.Unavailable("Cannot prepare photo or save operation. Retry this capsule.")
        }
    }

    /** Records that no publication call was started; remote photo bytes may still require recovery. */
    private fun failBeforePublication(
        operation: FrozenCapsuleOperation,
        result: OperationResult<String>
    ): OperationResult<String> {
        journal.save(FrozenOperationRecord(operation, PublicationPhase.FAILED))
        return result
    }

    /** Best-effort progress update; an older PUBLISHING record remains uncertain if storage fails. */
    private fun recordFailure(operation: FrozenCapsuleOperation, submitted: Boolean) {
        try {
            journal.save(FrozenOperationRecord(operation,
                if (submitted) PublicationPhase.OUTCOME_UNKNOWN else PublicationPhase.FAILED))
        } catch (_: Exception) {
            // Preserve the existing record. Never delete it or claim definite remote absence.
        }
    }

    /** Keeps denied/invalid media errors distinct without ever returning a publication success. */
    private fun photoFailure(result: OperationResult<*>): OperationResult<String> = when (result) {
        is OperationResult.Denied -> result
        is OperationResult.Invalid -> result
        is OperationResult.Unavailable -> result
        else -> OperationResult.Unavailable("Photo upload is not confirmed. Retry the same original.")
    }

    /** Rejects stale/signed-out callbacks without exposing another account's capsule. */
    private fun denied(): OperationResult.Denied =
        OperationResult.Denied("Account session changed. Sign in to check the saved send.")
}
