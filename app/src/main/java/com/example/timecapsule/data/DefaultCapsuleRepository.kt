package com.example.timecapsule.data

import android.os.SystemClock
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
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
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot

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

    /** Persists and submits through the shared publication coordinator; success requires server ACK. */
    override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> =
        publicationCoordinator?.publish(frozenOperation)
            ?: OperationResult.Unavailable("Capsule publication service is unavailable.")

    /** Resolves server-only matching evidence without treating a missing record as a failed write. */
    override suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> =
        publicationCoordinator?.reconcile(capsuleId)
            ?: OperationResult.Unavailable("Publication reconciliation is unavailable.")

    /** Replays the original saved snapshot only after the coordinator's server check. */
    override suspend fun retryPublication(capsuleId: String): OperationResult<String> =
        publicationCoordinator?.retry(capsuleId)
            ?: OperationResult.Unavailable("Publication retry is unavailable.")

    /** Restores private operations through server reconciliation without implicit replay. */
    override suspend fun recoverPublications() =
        publicationCoordinator?.recoverPublications()
            ?: OperationResult.Unavailable("Publication recovery is unavailable.")

    /** Retains failed content under a new identity only when safe abandonment is established. */
    override suspend fun abandonPublication(capsuleId: String) =
        publicationCoordinator?.abandonForNewDraft(capsuleId)
            ?: OperationResult.Unavailable("Publication replacement is unavailable.")

    override suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot> {
        return OperationResult.Unavailable("Capsule lists query requires Issue #21 implementation.")
    }

    /**
     * Orchestrates recipient unlock verification for TIME, LOCATION, and TIME_AND_LOCATION (BOTH) capsules (Issue #17).
     *
     * Execution Flow & Security Rules:
     * 1. Validates authenticated session snapshot (`initialSession`).
     * 2. Reads live server unlock requirements (`firestoreAdapter.readRecipientUnlockRequirements`).
     * 3. Idempotency: If server record is already `unlocked == true`, returns [Availability.AVAILABLE] without requesting location.
     * 4. For LOCATION / BOTH modes: Validates permission and location services before AND after location acquisition.
     * 5. Requests fresh ephemeral foreground location fix from [locationAdapter].
     * 6. Revalidates session tracker across suspension boundaries (`sessionTracker.currentSession() == initialSession`).
     * 7. Evaluates client-side sample quality, age (0..60s), accuracy (<=100m), and distance (<=150m) via [ConditionEvaluator].
     * 8. Executes atomic Firestore transaction ([firestoreAdapter.verifyRecipientUnlock]), where server rules enforce
     *    `request.time >= resource.data.unlockAt` for TIME and BOTH modes.
     * 9. Never persists `locationOK` or past geofence ENTER as future authorization.
     */
    override suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> {
        val cleanId = capsuleId.trim()
        if (cleanId.isBlank()) {
            return OperationResult.Invalid("Capsule ID must not be blank for unlock verification.")
        }
        val initialSession = sessionTracker.currentSession()
        if (!initialSession.isAuthenticated || initialSession.uid.isNullOrBlank()) {
            return OperationResult.Denied("Authentication required to verify capsule unlock.")
        }
        val activeUid = initialSession.uid

        // 1. Authoritative server read for lock mode, unlock target, and existing unlocked status
        val reqResult = firestoreAdapter.readRecipientUnlockRequirements(cleanId, activeUid)
        val postReadSession = sessionTracker.currentSession()
        if (!postReadSession.isAuthenticated || postReadSession != initialSession) {
            return OperationResult.Denied("Account session changed during unlock verification.")
        }

        if (reqResult !is OperationResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return reqResult as OperationResult<Availability>
        }

        val requirements = reqResult.data
        if (requirements.unlocked) {
            return OperationResult.Success(Availability.AVAILABLE)
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
            val postLocSession = sessionTracker.currentSession()
            if (!postLocSession.isAuthenticated || postLocSession != initialSession) {
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
        val result = firestoreAdapter.verifyRecipientUnlock(cleanId, activeUid, sample, clockNanosSupplier)
        val postSession = sessionTracker.currentSession()
        if (!postSession.isAuthenticated || postSession != initialSession) {
            return OperationResult.Denied("Account session changed during unlock verification.")
        }
        return result
    }

    override suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent> {
        return OperationResult.Invalid("Protected content read requires Issue #21 implementation.")
    }
}
