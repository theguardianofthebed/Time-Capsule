package com.example.timecapsule.data.publication

import com.example.timecapsule.data.media.OriginalPhotoUploads
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.FrozenCapsuleOperation
import com.example.timecapsule.domain.model.FrozenOperationRecord
import com.example.timecapsule.domain.model.FrozenPhotoUpload
import com.example.timecapsule.domain.model.PhotoUploadAcknowledgment
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RetainedOriginalPhoto
import com.example.timecapsule.domain.model.RecoveredPublication
import com.example.timecapsule.domain.model.SendStatus
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
 * Saves before network work, waits for photo ACK and publishes one atomic batch.
 * Timeouts do not cancel writes. Retry first checks server evidence and preserves the frozen ID.
 * An absent read never proves that an older delayed write cannot still arrive.
 */
class CapsulePublicationCoordinator(
    private val journal: FrozenOperationJournal,
    private val backend: CapsulePublicationBackend,
    private val media: PublicationMedia?,
    private val session: () -> SessionSnapshot,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> String = { ZoneId.systemDefault().id },
    private val responseTimeoutMillis: Long = 15_000L,
    private val drafts: PublicationDraftStore? = null,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    /** Identifies one shared attempt across recreated coordinators using the same private store. */
    private data class AttemptKey(val store: String, val uid: String, val capsuleId: String)

    companion object {
        private val registration = Mutex()
        private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val active = ConcurrentHashMap<
            AttemptKey, Deferred<OperationResult<String>>>()

        fun clearActiveAttemptsForTesting() {
            active.clear()
        }
    }

    init {
        require(responseTimeoutMillis > 0)
    }

    /** Prepares a new submission or joins/checks the same already-frozen operation. */
    suspend fun publish(
        draft: CapsuleDraft,
        targetSession: SessionSnapshot = session()
    ): OperationResult<String> = withContext(Dispatchers.IO) {
        val uid = targetSession.uid
        if (uid.isNullOrBlank() || !targetSession.isAuthenticated || session() != targetSession) {
            return@withContext denied()
        }
        try {
            val attempt = registration.withLock {
                if (session() != targetSession || !targetSession.isAuthenticated) {
                    return@withLock null
                }
                val previous = journal.load(uid, draft.capsuleId)
                require(previous?.phase != PublicationPhase.ABANDONED) {
                    "An abandoned operation cannot be submitted again."
                }
                require(previous?.operation?.isOpaque != true) {
                    "An opaque recovery record cannot be resubmitted."
                }
                val candidate = if (previous == null) {
                    FrozenCapsuleOperation.freeze(draft, uid, draft.creationZone.ifBlank { zone() }, now())
                } else {
                    FrozenCapsuleOperation.fromConfirmedDraft(draft, uid, previous.operation.creationZone)
                }
                require(previous == null ||
                    previous.operation.canonicalPayload() == candidate.canonicalPayload()) {
                    "Submitted capsule cannot be edited. Resolve its outcome first."
                }
                val key = key(candidate)
                val existing = active[key]
                if (existing != null && existing.isActive) {
                    existing
                } else {
                    if (previous == null) {
                        journal.save(FrozenOperationRecord(candidate))
                        start(key) { execute(FrozenOperationRecord(candidate), targetSession) }
                    } else {
                        start(key) {
                            if (previous.phase == PublicationPhase.SENT && hasPhotoProof(previous)) {
                                OperationResult.Success(previous.operation.capsuleId)
                            } else recover(previous, targetSession, replay = false)
                        }
                    }
                }
            } ?: return@withContext denied()
            awaitResult(draft.capsuleId, targetSession, attempt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: IllegalArgumentException) {
            OperationResult.Invalid(invalid.message ?: "Invalid capsule.")
        } catch (error: Exception) {
            OperationResult.Unavailable("Cannot prepare saved capsule. Check storage and retry.")
        }
    }

    /** Reconciles first, then replays only the saved ID/payload after authoritative absence. */
    suspend fun retry(
        capsuleId: String,
        targetSession: SessionSnapshot = session()
    ): OperationResult<String> = withContext(Dispatchers.IO) {
        val uid = targetSession.uid
        if (uid.isNullOrBlank() || !targetSession.isAuthenticated || session() != targetSession) {
            return@withContext denied()
        }
        try {
            val attempt = registration.withLock {
                val saved = journal.load(uid, capsuleId)
                    ?: return@withLock null
                require(saved.phase != PublicationPhase.ABANDONED) {
                    "An abandoned operation cannot be retried."
                }
                val key = key(saved.operation)
                val existing = active[key]
                if (existing != null && existing.isActive) existing
                else start(key) { recover(saved, targetSession, replay = true) }
            } ?: return@withContext OperationResult.Invalid("No saved send exists for this account.")
            awaitResult(capsuleId, targetSession, attempt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            OperationResult.Unavailable("Cannot recover the saved send. Keep it and retry.")
        }
    }

    /** Reads server evidence without starting a new write or treating pending/local data as SENT. */
    suspend fun reconcile(
        capsuleId: String,
        targetSession: SessionSnapshot = session()
    ): OperationResult<SendStatus> = withContext(Dispatchers.IO) {
        val uid = targetSession.uid
        if (uid.isNullOrBlank() || !targetSession.isAuthenticated || session() != targetSession) {
            return@withContext denied()
        }
        try {
            val saved = journal.load(uid, capsuleId)
                ?: return@withContext OperationResult.Invalid("No saved send exists for this account.")
            if (saved.phase == PublicationPhase.ABANDONED) {
                return@withContext OperationResult.Invalid("This operation was abandoned.")
            }
            val evidence = inspect(saved.operation)
            if (session() != targetSession) return@withContext denied()
            when (evidence) {
                PublicationInspection.MATCHING -> when (val result = resolveMatching(saved.operation)) {
                    is OperationResult.Success -> OperationResult.Success(SendStatus.SENT)
                    else -> unresolvedResult(capsuleId, result)
                }
                PublicationInspection.CONFLICT -> conflict(saved.operation)
                PublicationInspection.ABSENT -> {
                    registration.withLock {
                        val latest = journal.load(uid, capsuleId) ?: saved
                        if (active[key(saved.operation)]?.isActive != true &&
                            !mayHaveEarlierWrite(latest) &&
                            (latest.phase == PublicationPhase.FAILED || latest.terminalRejectionConfirmed)) {
                            val resolved = progress(latest.operation, PublicationPhase.FAILED,
                                unresolved = false)
                            OperationResult.Success(if (resolved.phase == PublicationPhase.SENT)
                                SendStatus.SENT else SendStatus.FAILED)
                        } else {
                            markUnknown(saved.operation)
                            unknown(capsuleId)
                        }
                    }
                }
                else -> {
                    markUnknown(saved.operation)
                    unknown(capsuleId)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            unknown(capsuleId)
        }
    }

    /** Foreground-only recovery checks saved sends and never starts a new publication implicitly. */
    suspend fun recoverPublications(
        targetSession: SessionSnapshot = session()
    ): OperationResult<List<RecoveredPublication>> = withContext(Dispatchers.IO) {
        val uid = targetSession.uid
        if (uid.isNullOrBlank() || !targetSession.isAuthenticated || session() != targetSession) {
            return@withContext denied()
        }
        try {
            val recovered = mutableListOf<RecoveredPublication>()
            for (record in journal.list(uid)) {
                if (session() != targetSession) return@withContext denied()
                if (record.phase == PublicationPhase.ABANDONED) {
                    if (!record.replacementPrepared && record.replacementDraftId != null) {
                        when (val result = prepareReplacement(record, targetSession)) {
                            is OperationResult.Success -> Unit
                            else -> return@withContext OperationResult.Unavailable(
                                "Finish recovering the replacement draft before starting another send.")
                        }
                    }
                    continue
                }
                if (record.phase != PublicationPhase.SENT) reconcile(record.operation.capsuleId, targetSession)
                if (session() != targetSession) return@withContext denied()
                val latest = journal.load(uid, record.operation.capsuleId) ?: record
                if (latest.phase == PublicationPhase.ABANDONED) continue
                val cleared = if (latest.phase == PublicationPhase.SENT && hasPhotoProof(latest)) {
                    drafts?.clearConfirmed(latest.operation) ?: false
                } else false
                if (session() != targetSession) return@withContext denied()
                val isSelfSend = latest.operation.senderUid == latest.operation.recipientUid
                val isUncertainSelfSend = isSelfSend && latest.phase != PublicationPhase.SENT
                val snapshotToExpose = if (latest.phase == PublicationPhase.SENT || isUncertainSelfSend || latest.operation.isOpaque) null else latest.operation
                recovered += RecoveredPublication(latest.operation.capsuleId, latest.phase,
                    snapshotToExpose,
                    latest.phase == PublicationPhase.FAILED && !mayHaveEarlierWrite(latest) &&
                        active[key(latest.operation)]?.isActive != true, cleared)
            }
            OperationResult.Success(recovered)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            OperationResult.Unavailable("Cannot restore saved sends. Keep them and retry recovery.")
        }
    }

    /**
     * Abandons only a definitively failed unpublished operation with no active attempt.
     * The new ID is journaled first so an interrupted replacement cannot generate several drafts.
     */
    suspend fun abandonForNewDraft(
        capsuleId: String,
        targetSession: SessionSnapshot = session()
    ): OperationResult<CapsuleDraft> = withContext(Dispatchers.IO) {
        val uid = targetSession.uid
        if (uid.isNullOrBlank() || !targetSession.isAuthenticated || session() != targetSession) {
            return@withContext denied()
        }
        if (drafts == null) return@withContext OperationResult.Unavailable("Draft recovery is unavailable.")
        try {
            val saved = journal.load(uid, capsuleId)
                ?: return@withContext OperationResult.Invalid("No saved send exists for this account.")
            if (saved.operation.isOpaque) {
                return@withContext OperationResult.Denied("An opaque recovery record cannot be abandoned for a new draft.")
            }
            if (saved.phase == PublicationPhase.ABANDONED) {
                return@withContext prepareReplacement(saved, targetSession)
            }
            val evidence = inspect(saved.operation)
            if (session() != targetSession) return@withContext denied()
            if (evidence == PublicationInspection.MATCHING) {
                resolveMatching(saved.operation)
                return@withContext OperationResult.Denied("This capsule is published; it cannot be abandoned.")
            }
            if (evidence != PublicationInspection.ABSENT) {
                return@withContext unknown(capsuleId)
            }
            val abandoned = registration.withLock {
                val latest = journal.load(uid, capsuleId) ?: saved
                if (latest.phase == PublicationPhase.ABANDONED) return@withLock latest
                if (active[key(latest.operation)]?.isActive == true ||
                    latest.phase != PublicationPhase.FAILED || mayHaveEarlierWrite(latest)) {
                    return@withLock null
                }
                val replacementId = newId()
                require(replacementId != capsuleId &&
                    replacementId.matches(Regex("[A-Za-z0-9_-]+")) &&
                    journal.load(uid, replacementId) == null) { "Replacement ID is already used or invalid." }
                latest.copy(phase = PublicationPhase.ABANDONED,
                    replacementDraftId = replacementId).also { journal.save(it) }
            } ?: return@withContext unknown(capsuleId)
            prepareReplacement(abandoned, targetSession)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: IllegalArgumentException) {
            OperationResult.Invalid(invalid.message ?: "Invalid replacement draft.")
        } catch (error: Exception) {
            OperationResult.Unavailable("Cannot prepare the replacement draft. Original content is retained.")
        }
    }

    /** Completes an idempotent local replacement without reusing the old publication identity. */
    private suspend fun prepareReplacement(
        record: FrozenOperationRecord,
        captured: SessionSnapshot
    ): OperationResult<CapsuleDraft> {
        val handler = drafts ?: return OperationResult.Unavailable("Draft recovery is unavailable.")
        val id = record.replacementDraftId
            ?: return OperationResult.Invalid("Replacement draft identity is missing.")
        if (session() != captured) return denied()
        val replacement = handler.replacement(record.operation, id)
        if (session() != captured) return denied()
        require(replacement.capsuleId == id && id != record.operation.capsuleId)
        journal.update(record.operation.senderUid, record.operation.capsuleId) {
            require(it.phase == PublicationPhase.ABANDONED)
            it.copy(replacementPrepared = true)
        }
        return OperationResult.Success(replacement)
    }

    /** Registers work before starting it; caller holds the short process-wide registration mutex. */
    private fun start(
        key: AttemptKey,
        action: suspend () -> OperationResult<String>
    ): Deferred<OperationResult<String>> {
        val started = work.async(start = CoroutineStart.LAZY) { action() }
        active[key] = started
        started.invokeOnCompletion { active.remove(key, started) }
        started.start()
        return started
    }

    /** Bounds UI waiting without canceling the process-owned attempt or inventing a failure. */
    private suspend fun awaitResult(
        capsuleId: String,
        captured: SessionSnapshot,
        attempt: Deferred<OperationResult<String>>
    ): OperationResult<String> {
        val result = withTimeoutOrNull(responseTimeoutMillis) { attempt.await() }
        if (session() != captured) return denied()
        if (result != null) return result
        val saved = journal.load(captured.uid ?: return denied(), capsuleId)
            ?: return unknown(capsuleId)
        if (saved.phase == PublicationPhase.SENT && hasPhotoProof(saved)) {
            return OperationResult.Success(capsuleId)
        }
        markUnknown(saved.operation)
        return unknown(capsuleId)
    }

    /** Resolves matching content or replays an absent operation without editing its original snapshot. */
    private suspend fun recover(
        saved: FrozenOperationRecord,
        captured: SessionSnapshot,
        replay: Boolean
    ): OperationResult<String> {
        val operation = saved.operation
        if (session() != captured) return denied()
        val evidence = inspect(operation)
        if (session() != captured) return denied()
        return when (evidence) {
            PublicationInspection.MATCHING -> resolveMatching(operation)
            PublicationInspection.CONFLICT -> conflict(operation)
            PublicationInspection.ABSENT -> {
                if (replay && !operation.isOpaque && saved.phase != PublicationPhase.SENT &&
                    saved.phase != PublicationPhase.ABANDONED) {
                    // An absent record permits only unchanged replay, never edited/new-ID restart.
                    execute(journal.load(operation.senderUid, operation.capsuleId) ?: saved, captured)
                } else {
                    markUnknown(operation)
                    unknown(operation.capsuleId)
                }
            }
            else -> {
                markUnknown(operation)
                unknown(operation.capsuleId)
            }
        }
    }

    /** Publishes unchanged content and tracks whether an older attempt could still arrive. */
    private suspend fun execute(
        saved: FrozenOperationRecord,
        captured: SessionSnapshot
    ): OperationResult<String> {
        var operation = saved.operation
        val priorUncertain = mayHaveEarlierWrite(saved)
        var submitted = false
        try {
            if (session() != captured) return failBeforePublication(operation, denied(), priorUncertain)
            operation.photo?.let { original ->
                val handler = media ?: return failBeforePublication(operation,
                    OperationResult.Unavailable("Photo service is unavailable."), priorUncertain)
                val frozen = when (val result = handler.freeze(original)) {
                    is OperationResult.Success -> result.data
                    else -> return failBeforePublication(operation, photoFailure(result), priorUncertain)
                }
                require(frozen.objectPath == operation.photoObjectPath)
                require(frozen.original.sha256 == original.sha256 &&
                    frozen.original.sizeBytes == original.sizeBytes &&
                    frozen.original.format == original.format)
                operation = operation.copy(photo = frozen.original)
                progress(operation, PublicationPhase.UPLOADING)
                if (session() != captured) return failBeforePublication(operation, denied(), priorUncertain)
                val ack = when (val result = handler.upload(frozen)) {
                    is OperationResult.Success -> result.data
                    else -> return failBeforePublication(operation, photoFailure(result), priorUncertain)
                }
                require(ack.objectPath == operation.photoObjectPath &&
                    ack.sizeBytes == original.sizeBytes && ack.sha256 == original.sha256) {
                    "Photo acknowledgment does not match the submitted original."
                }
                progress(operation, PublicationPhase.UPLOADING, photoAcknowledged = true)
            }
            if (session() != captured) return failBeforePublication(operation, denied(), priorUncertain)
            val publishing = progress(operation, PublicationPhase.PUBLISHING,
                unresolved = true, rejected = false)
            if (publishing.phase == PublicationPhase.SENT) {
                return OperationResult.Success(operation.capsuleId)
            }
            if (session() != captured) return failBeforePublication(operation, denied(), priorUncertain)
            submitted = true
            backend.publish(operation)
            progress(operation, PublicationPhase.SENT, unresolved = false, rejected = false)
            return if (session() == captured) OperationResult.Success(operation.capsuleId) else denied()
        } catch (cancelled: CancellationException) {
            markUnknown(operation, unresolved = submitted || priorUncertain)
            throw cancelled
        } catch (error: Exception) {
            if (!submitted) {
                return failBeforePublication(operation,
                    if (error is IllegalArgumentException) OperationResult.Invalid(
                        error.message ?: "Invalid photo.") else
                        OperationResult.Unavailable("Cannot prepare photo or save operation."),
                    priorUncertain)
            }
            val rejected = error is PublicationRejectedException
            markUnknown(operation, unresolved = priorUncertain || !rejected, rejected = rejected)
            if (session() != captured) return denied()
            // A rejected replay cannot settle an older delayed write, even after an absent read.
            return when (inspect(operation)) {
                PublicationInspection.MATCHING -> resolveMatching(operation)
                PublicationInspection.CONFLICT -> conflict(operation)
                PublicationInspection.ABSENT -> if (rejected && !priorUncertain) {
                    val resolved = progress(operation, PublicationPhase.FAILED,
                        unresolved = false, rejected = true)
                    if (resolved.phase == PublicationPhase.SENT) OperationResult.Success(operation.capsuleId)
                    else OperationResult.Invalid("Publication was rejected. Retry unchanged or review the failure.")
                } else unknown(operation.capsuleId)
                else -> unknown(operation.capsuleId)
            }
        }
    }

    /** A new media failure is definite only when no earlier publication can still arrive. */
    private fun failBeforePublication(
        operation: FrozenCapsuleOperation,
        result: OperationResult<String>,
        priorUncertain: Boolean
    ): OperationResult<String> {
        if (priorUncertain) {
            markUnknown(operation, unresolved = true)
            return unknown(operation.capsuleId)
        }
        progress(operation, PublicationPhase.FAILED, unresolved = false)
        return result
    }

    /** Updates progress atomically while preserving a matching server success from another check. */
    private fun progress(
        operation: FrozenCapsuleOperation,
        phase: PublicationPhase,
        unresolved: Boolean? = null,
        photoAcknowledged: Boolean? = null,
        rejected: Boolean? = null
    ): FrozenOperationRecord = journal.update(operation.senderUid, operation.capsuleId) { current ->
        if (current.phase == PublicationPhase.ABANDONED ||
            (current.phase == PublicationPhase.SENT && phase != PublicationPhase.SENT)) current
        else current.copy(operation = operation, phase = phase,
            hasUnresolvedWrite = unresolved ?: current.hasUnresolvedWrite,
            photoAcknowledged = photoAcknowledged ?: current.photoAcknowledged,
            terminalRejectionConfirmed = rejected ?: current.terminalRejectionConfirmed)
    }

    /** Preserves earlier uncertainty and never overwrites a concurrent confirmed SENT record. */
    private fun markUnknown(
        operation: FrozenCapsuleOperation,
        unresolved: Boolean? = null,
        rejected: Boolean? = null
    ) {
        try {
            progress(operation, PublicationPhase.OUTCOME_UNKNOWN,
                unresolved = unresolved, rejected = rejected)
        } catch (_: Exception) {
            // Keep the previous durable record. A failed status write never proves remote failure.
        }
    }

    /** Matches the saved photo proof as well as server content before resolving successful delivery. */
    private fun resolveMatching(operation: FrozenCapsuleOperation): OperationResult<String> {
        val saved = journal.load(operation.senderUid, operation.capsuleId)
            ?: return unknown(operation.capsuleId)
        if (saved.phase == PublicationPhase.ABANDONED) {
            return OperationResult.Invalid("This operation was abandoned.")
        }
        if (!hasPhotoProof(saved)) {
            return OperationResult.OutcomeUnknown(operation.capsuleId,
                "Publication matches, but the required saved photo acknowledgment is missing.")
        }
        return try {
            progress(saved.operation, PublicationPhase.SENT, unresolved = false, rejected = false)
            OperationResult.Success(operation.capsuleId)
        } catch (_: Exception) {
            unknown(operation.capsuleId)
        }
    }

    /** A conflicting existing ID is never overwritten or silently accepted as the same operation. */
    private fun conflict(operation: FrozenCapsuleOperation): OperationResult.Invalid {
        markUnknown(operation)
        return OperationResult.Invalid("Server capsule content conflicts with the saved operation.")
    }

    /** Reads only backend evidence; timeouts/cache/unavailable results cannot become absence. */
    private suspend fun inspect(operation: FrozenCapsuleOperation): PublicationInspection = try {
        withTimeoutOrNull(responseTimeoutMillis) { backend.inspect(operation) }
            ?: PublicationInspection.UNAVAILABLE
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        PublicationInspection.UNAVAILABLE
    }

    /** Old PUBLISHING/UNKNOWN records are conservative until terminal rejection rules out old writes. */
    private fun mayHaveEarlierWrite(record: FrozenOperationRecord): Boolean =
        record.hasUnresolvedWrite || record.phase == PublicationPhase.PUBLISHING ||
            (record.phase == PublicationPhase.OUTCOME_UNKNOWN && !record.terminalRejectionConfirmed)

    /** Text-only operations do not need a photo ACK; photo operations require its durable evidence. */
    private fun hasPhotoProof(record: FrozenOperationRecord): Boolean =
        record.operation.photo == null || record.photoAcknowledged

    /** Builds the process-wide operation key without changing its frozen ID. */
    private fun key(operation: FrozenCapsuleOperation): AttemptKey =
        AttemptKey(journal.storageIdentity, operation.senderUid, operation.capsuleId)

    /** Converts unresolved reconciliation responses without converting them into a status success. */
    private fun unresolvedResult(
        capsuleId: String,
        result: OperationResult<*>
    ): OperationResult<SendStatus> = when (result) {
        is OperationResult.Invalid -> result
        is OperationResult.Denied -> result
        else -> unknown(capsuleId)
    }

    /** Keeps denied/invalid media failures distinct before publication is attempted. */
    private fun photoFailure(result: OperationResult<*>): OperationResult<String> = when (result) {
        is OperationResult.Denied -> result
        is OperationResult.Invalid -> result
        is OperationResult.Unavailable -> result
        else -> OperationResult.Unavailable("Photo upload is not confirmed. Retry the same original.")
    }

    /** Leaves the original operation intact for same-ID inspection or retry. */
    private fun unknown(capsuleId: String): OperationResult.OutcomeUnknown =
        OperationResult.OutcomeUnknown(capsuleId, "Send result is unknown. Check or retry this same capsule.")

    /** Rejects stale/signed-out callbacks without exposing another account's capsule. */
    private fun denied(): OperationResult.Denied =
        OperationResult.Denied("Account session changed. Sign in to check the saved send.")
}
