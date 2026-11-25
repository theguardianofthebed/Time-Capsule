package com.example.timecapsule.data

import android.os.SystemClock
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.adapter.LocationAdapter
import com.example.timecapsule.data.publication.CapsulePublicationCoordinator
import com.example.timecapsule.data.session.SessionTracker
import com.example.timecapsule.domain.condition.ConditionEvaluator
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.LocationSampleStatus
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.QualificationFailureReason
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecoveredPublication
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.model.UnlockConfirmationKind
import com.example.timecapsule.domain.model.UnlockVerificationOutcome
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import java.time.Instant

/**
 * Default implementation of [CapsuleRepository] interacting with platform adapters and session tracker.
 * Enforces session generation validation across suspension points and orchestrates live condition unlocking.
 */
class DefaultCapsuleRepository(
    private val sessionTracker: SessionTracker,
    private val authAdapter: AuthAdapter,
    private val firestoreAdapter: FirestoreAdapter,
    private val publicationCoordinator: CapsulePublicationCoordinator? = null,
    private val locationAdapter: LocationAdapter? = null,
    private val retentionAdapter: LocalRetentionAdapter? = null,
    private val clockNanosSupplier: () -> Long = { SystemClock.elapsedRealtimeNanos() }
) : CapsuleRepository {

    override fun session(): SessionSnapshot = sessionTracker.currentSession()

    override suspend fun getRecipient(uid: String): OperationResult<RecipientProfile> {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) {
            return OperationResult.Invalid("Recipient UID must not be blank.")
        }
        return firestoreAdapter.getRecipientProfile(cleanUid)
    }

    override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> {
        return publish(frozenOperation, sessionTracker.currentSession())
    }

    override suspend fun publish(
        frozenOperation: CapsuleDraft,
        targetSession: SessionSnapshot
    ): OperationResult<String> =
        if (!sessionTracker.isSessionValid(targetSession)) OperationResult.Denied("Account session changed.")
        else publicationCoordinator?.publish(frozenOperation, targetSession)
            ?: OperationResult.Unavailable("Capsule publication service is unavailable.")

    override suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> {
        return reconcile(capsuleId, sessionTracker.currentSession())
    }

    override suspend fun reconcile(
        capsuleId: String,
        targetSession: SessionSnapshot
    ): OperationResult<SendStatus> =
        if (!sessionTracker.isSessionValid(targetSession)) OperationResult.Denied("Account session changed.")
        else publicationCoordinator?.reconcile(capsuleId, targetSession)
            ?: OperationResult.Unavailable("Publication reconciliation is unavailable.")

    override suspend fun retryPublication(capsuleId: String): OperationResult<String> {
        return retryPublication(capsuleId, sessionTracker.currentSession())
    }

    override suspend fun retryPublication(
        capsuleId: String,
        targetSession: SessionSnapshot
    ): OperationResult<String> =
        if (!sessionTracker.isSessionValid(targetSession)) OperationResult.Denied("Account session changed.")
        else publicationCoordinator?.retry(capsuleId, targetSession)
            ?: OperationResult.Unavailable("Publication retry is unavailable.")

    override suspend fun recoverPublications(): OperationResult<List<RecoveredPublication>> {
        return recoverPublications(sessionTracker.currentSession())
    }

    override suspend fun recoverPublications(
        targetSession: SessionSnapshot
    ): OperationResult<List<RecoveredPublication>> =
        if (!sessionTracker.isSessionValid(targetSession)) OperationResult.Denied("Account session changed.")
        else publicationCoordinator?.recoverPublications(targetSession)
            ?: OperationResult.Unavailable("Publication recovery is unavailable.")

    override suspend fun abandonPublication(capsuleId: String): OperationResult<CapsuleDraft> {
        return abandonPublication(capsuleId, sessionTracker.currentSession())
    }

    override suspend fun abandonPublication(
        capsuleId: String,
        targetSession: SessionSnapshot
    ): OperationResult<CapsuleDraft> =
        if (!sessionTracker.isSessionValid(targetSession)) OperationResult.Denied("Account session changed.")
        else publicationCoordinator?.abandonForNewDraft(capsuleId, targetSession)
            ?: OperationResult.Unavailable("Publication replacement is unavailable.")

    override suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot> {
        return OperationResult.Unavailable("Capsule lists query requires Issue #21 implementation.")
    }

    override suspend fun checkUnlockStatus(
        capsuleId: String,
        targetSession: SessionSnapshot
    ): OperationResult<UnlockVerificationOutcome> {
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            return OperationResult.Invalid("Capsule ID must not be blank for unlock check.")
        }
        if (!sessionTracker.isSessionValid(targetSession) || !targetSession.isAuthenticated || targetSession.uid.isNullOrBlank()) {
            return OperationResult.Denied("Authentication required to check capsule unlock status.")
        }
        val activeUid = targetSession.uid

        val reqResult = firestoreAdapter.readRecipientUnlockRequirements(cleanId, activeUid)
        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed during unlock check.")
        }

        if (reqResult !is OperationResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return reqResult as OperationResult<UnlockVerificationOutcome>
        }

        val requirements = reqResult.data
        return if (requirements.unlocked) {
            OperationResult.Success(
                UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.ALREADY_UNLOCKED)
            )
        } else {
            OperationResult.Denied("Capsule remains locked.")
        }
    }

    override suspend fun verifyUnlockOutcome(capsuleId: String): OperationResult<UnlockVerificationOutcome> {
        return verifyUnlockOutcome(capsuleId, sessionTracker.currentSession())
    }

    /**
     * Orchestrates recipient unlock verification for TIME, LOCATION, and TIME_AND_LOCATION (BOTH) capsules bound to [targetSession].
     * Returns strongly typed [UnlockVerificationOutcome] distinguishing newly confirmed first unlocks from already unlocked capsules.
     */
    override suspend fun verifyUnlockOutcome(
        capsuleId: String,
        targetSession: SessionSnapshot
    ): OperationResult<UnlockVerificationOutcome> {
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            return OperationResult.Invalid("Capsule ID must not be blank for unlock verification.")
        }
        if (!sessionTracker.isSessionValid(targetSession) || !targetSession.isAuthenticated || targetSession.uid.isNullOrBlank()) {
            return OperationResult.Denied("Authentication required to verify capsule unlock.")
        }
        val activeUid = targetSession.uid

        // 1. Authoritative server read for lock mode, unlock target, and existing unlocked status
        val reqResult = firestoreAdapter.readRecipientUnlockRequirements(cleanId, activeUid)
        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed during unlock verification.")
        }

        if (reqResult !is OperationResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return reqResult as OperationResult<UnlockVerificationOutcome>
        }

        val requirements = reqResult.data
        if (requirements.unlocked) {
            return OperationResult.Success(
                UnlockVerificationOutcome(Availability.AVAILABLE, UnlockConfirmationKind.ALREADY_UNLOCKED)
            )
        }

        val mode = requirements.lockMode
        val sample: ForegroundLocationSample? = if (mode == LockMode.LOCATION || mode == LockMode.TIME_AND_LOCATION) {
            val adapter = locationAdapter
                ?: return OperationResult.Unavailable("Location adapter is unavailable for location unlock.")

            if (!adapter.isFineLocationGranted()) {
                return OperationResult.Denied("Precise location permission is required for location unlock.")
            }
            if (!adapter.isLocationAvailable()) {
                return OperationResult.Unavailable("Location services are disabled or unavailable.")
            }

            val locResult = adapter.getCurrentLocation()
            if (!sessionTracker.isSessionValid(targetSession)) {
                return OperationResult.Denied("Account session changed during location acquisition.")
            }

            when (locResult.status) {
                LocationSampleStatus.SUCCESS -> {
                    locResult.sample ?: return OperationResult.Unavailable("Location sample was null.")
                }
                LocationSampleStatus.PERMISSION_DENIED -> {
                    return OperationResult.Denied(locResult.errorMessage ?: "Location permission denied.")
                }
                LocationSampleStatus.SERVICE_DISABLED -> {
                    return OperationResult.Unavailable(locResult.errorMessage ?: "Location service is disabled.")
                }
                else -> {
                    return OperationResult.Unavailable(locResult.errorMessage ?: "Failed to acquire location sample.")
                }
            }
        } else {
            null
        }

        // Recheck location availability and permission prior to remote transaction submission
        if (mode == LockMode.LOCATION || mode == LockMode.TIME_AND_LOCATION) {
            val adapter = locationAdapter
                ?: return OperationResult.Unavailable("Location adapter is unavailable for location unlock.")
            if (!adapter.isFineLocationGranted()) {
                return OperationResult.Denied("Precise location permission was revoked prior to unlock submission.")
            }
            if (!adapter.isLocationAvailable()) {
                return OperationResult.Unavailable("Location services were disabled prior to unlock submission.")
            }

            val nowNanos = clockNanosSupplier()
            val eval = ConditionEvaluator.evaluate(mode, requirements.locationTarget, sample, nowNanos)
            if (!eval.isEligible) {
                return when (eval.failureReason) {
                    QualificationFailureReason.OUTSIDE_RADIUS -> OperationResult.Denied("Current location is outside target radius.")
                    QualificationFailureReason.STALE_SAMPLE -> OperationResult.Unavailable("Location sample is stale. Please acquire a fresh location.")
                    QualificationFailureReason.POOR_ACCURACY -> OperationResult.Unavailable("Location sample accuracy is too poor (must be <= 100m).")
                    QualificationFailureReason.FUTURE_TIMESTAMP -> OperationResult.Unavailable("Location sample timestamp is invalid.")
                    QualificationFailureReason.INVALID_COORDINATES -> OperationResult.Invalid("Location sample contains invalid coordinates.")
                    QualificationFailureReason.INVALID_TARGET -> OperationResult.Invalid("Target location is invalid.")
                    QualificationFailureReason.MISSING_SAMPLE -> OperationResult.Denied("Location sample is missing.")
                    QualificationFailureReason.NONE -> OperationResult.Denied("Location condition not met.")
                }
            }
        }

        // 2. Submit transaction to Firestore with location sample and monotonic timestamp supplier
        val result = firestoreAdapter.verifyRecipientUnlockDetailed(cleanId, activeUid, sample, clockNanosSupplier)
        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed during unlock verification.")
        }
        return result
    }

    override suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> {
        return verifyUnlock(capsuleId, sessionTracker.currentSession())
    }

    override suspend fun verifyUnlock(
        capsuleId: String,
        targetSession: SessionSnapshot
    ): OperationResult<Availability> {
        return when (val res = verifyUnlockOutcome(capsuleId, targetSession)) {
            is OperationResult.Success -> OperationResult.Success(res.data.availability)
            is OperationResult.Invalid -> OperationResult.Invalid(res.message)
            is OperationResult.Denied -> OperationResult.Denied(res.message)
            is OperationResult.Unavailable -> OperationResult.Unavailable(res.message)
            is OperationResult.OutcomeUnknown -> OperationResult.OutcomeUnknown(res.capsuleId, res.message)
        }
    }

    override suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent> {
        return readContent(capsuleId, sessionTracker.currentSession())
    }

    override suspend fun readContent(
        capsuleId: String,
        targetSession: SessionSnapshot
    ): OperationResult<ProtectedCapsuleContent> {
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            return OperationResult.Invalid("Capsule ID must not be blank for content read.")
        }
        if (!sessionTracker.isSessionValid(targetSession) || !targetSession.isAuthenticated || targetSession.uid.isNullOrBlank()) {
            return OperationResult.Denied("Authentication required to read protected content.")
        }
        val activeUid = targetSession.uid!!

        // 1. Check local retention first if adapter is present
        val retention = retentionAdapter
        if (retention != null) {
            val offlineRes = retention.readRetainedContent(activeUid, cleanId)
            if (offlineRes is OperationResult.Success) {
                val retained = offlineRes.data
                val photoPath = if (retained.photoBytes != null) "retainedMedia/${retained.uid}/$cleanId" else null
                return OperationResult.Success(
                    ProtectedCapsuleContent(
                        capsuleId = cleanId,
                        text = retained.text,
                        photoObjectPath = photoPath,
                        photoBytes = retained.photoBytes,
                        photoFormat = retained.photoFormat
                    )
                )
            }
        }

        // 2. Authoritative server check for unlock requirements & unlock timestamp
        val reqResult = firestoreAdapter.readRecipientUnlockRequirements(cleanId, activeUid)
        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed during content read.")
        }
        if (reqResult !is OperationResult.Success) {
            return OperationResult.Denied("Unlock requirements unreadable.")
        }

        val requirements = reqResult.data
        var serverUnlockedAt = requirements.unlockedAt

        if (!requirements.unlocked) {
            val unlockRes = verifyUnlockOutcome(cleanId, targetSession)
            if (!sessionTracker.isSessionValid(targetSession)) {
                return OperationResult.Denied("Account session changed during content read.")
            }
            if (unlockRes !is OperationResult.Success || unlockRes.data.availability != Availability.AVAILABLE) {
                return OperationResult.Denied("Unlock authorization required before reading content.")
            }
            val rechecked = firestoreAdapter.readRecipientUnlockRequirements(cleanId, activeUid)
            if (rechecked is OperationResult.Success) {
                serverUnlockedAt = rechecked.data.unlockedAt
            }
        }

        val verifiedTime = serverUnlockedAt
            ?: return OperationResult.Denied("Server unlock timestamp is missing.")

        val contentRes = firestoreAdapter.readProtectedContent(cleanId, activeUid)
        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed during content read.")
        }

        if (contentRes is OperationResult.Success) {
            val content = contentRes.data
            retentionAdapter?.saveRetainedContent(
                uid = activeUid,
                capsuleId = cleanId,
                verifiedUnlockedAt = verifiedTime,
                text = content.text,
                photoBytes = content.photoBytes,
                photoFormat = content.photoFormat
            )
            return OperationResult.Success(content)
        }

        return OperationResult.Denied("Unlock authorization required before reading content.")
    }
}
