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
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.ListFreshness
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
        return lists(uid, sessionTracker.currentSession())
    }

    override suspend fun lists(
        uid: String,
        targetSession: SessionSnapshot
    ): OperationResult<CapsuleListsSnapshot> {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) {
            return OperationResult.Invalid("UID must not be blank for capsule lists.")
        }
        if (!sessionTracker.isSessionValid(targetSession) || !targetSession.isAuthenticated || targetSession.uid != cleanUid) {
            return OperationResult.Denied("Account session changed or authentication required.")
        }

        val sentRes = firestoreAdapter.getSentCapsuleSummaries(cleanUid)
        val receivedRes = firestoreAdapter.getReceivedCapsuleSummaries(cleanUid)

        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed during lists query.")
        }

        if (sentRes is OperationResult.Success && receivedRes is OperationResult.Success) {
            return OperationResult.Success(
                CapsuleListsSnapshot(
                    sent = sentRes.data,
                    received = receivedRes.data,
                    freshness = ListFreshness.SERVER_CONFIRMED
                )
            )
        }

        // Authoritative Denied / Invalid -> Return immediately without offline fallback
        @Suppress("UNCHECKED_CAST")
        if (sentRes is OperationResult.Denied) return sentRes as OperationResult<CapsuleListsSnapshot>
        @Suppress("UNCHECKED_CAST")
        if (receivedRes is OperationResult.Denied) return receivedRes as OperationResult<CapsuleListsSnapshot>
        @Suppress("UNCHECKED_CAST")
        if (sentRes is OperationResult.Invalid) return sentRes as OperationResult<CapsuleListsSnapshot>
        @Suppress("UNCHECKED_CAST")
        if (receivedRes is OperationResult.Invalid) return receivedRes as OperationResult<CapsuleListsSnapshot>

        val retention = retentionAdapter

        // Partial remote success: If one query succeeded and the other failed due to Unavailable
        if (sentRes is OperationResult.Success && receivedRes is OperationResult.Unavailable) {
            val offlineEntries = retention?.listVerifiedRecipientEntries(targetSession)
            if (!sessionTracker.isSessionValid(targetSession)) {
                return OperationResult.Denied("Account session changed during offline lists fallback.")
            }
            val offlineReceived = if (offlineEntries is OperationResult.Success) {
                offlineEntries.data.map { retained ->
                    CapsuleSummary(
                        capsuleId = retained.capsuleId,
                        senderUid = retained.senderUid ?: "",
                        recipientUid = retained.recipientUid ?: cleanUid,
                        lock = CapsuleLock(mode = retained.lockMode ?: LockMode.TIME, unlockAt = retained.unlockAt),
                        sendStatus = SendStatus.SENT,
                        availability = Availability.AVAILABLE,
                        title = retained.title,
                        createdAt = retained.createdAt,
                        hasPhoto = retained.hasPhoto,
                        unlockedAt = retained.verifiedUnlockedAt,
                        senderDisplayName = retained.senderDisplayName,
                        recipientDisplayName = retained.recipientDisplayName
                    )
                }
            } else emptyList()
            return OperationResult.Success(
                CapsuleListsSnapshot(
                    sent = sentRes.data,
                    received = offlineReceived,
                    freshness = ListFreshness.PARTIAL_OFFLINE
                )
            )
        }

        if (receivedRes is OperationResult.Success && sentRes is OperationResult.Unavailable) {
            return OperationResult.Success(
                CapsuleListsSnapshot(
                    sent = emptyList(),
                    received = receivedRes.data,
                    freshness = ListFreshness.PARTIAL_OFFLINE
                )
            )
        }

        // Both remote queries failed due to Unavailable -> Stale offline fallback
        if (retention != null) {
            val offlineEntries = retention.listVerifiedRecipientEntries(targetSession)
            if (!sessionTracker.isSessionValid(targetSession)) {
                return OperationResult.Denied("Account session changed during offline lists fallback.")
            }
            if (offlineEntries is OperationResult.Success && offlineEntries.data.isNotEmpty()) {
                val offlineSummaries = offlineEntries.data.map { retained ->
                    CapsuleSummary(
                        capsuleId = retained.capsuleId,
                        senderUid = retained.senderUid ?: "",
                        recipientUid = retained.recipientUid ?: cleanUid,
                        lock = CapsuleLock(mode = retained.lockMode ?: LockMode.TIME, unlockAt = retained.unlockAt),
                        sendStatus = SendStatus.SENT,
                        availability = Availability.AVAILABLE,
                        title = retained.title,
                        createdAt = retained.createdAt,
                        hasPhoto = retained.hasPhoto,
                        unlockedAt = retained.verifiedUnlockedAt,
                        senderDisplayName = retained.senderDisplayName,
                        recipientDisplayName = retained.recipientDisplayName
                    )
                }
                return OperationResult.Success(
                    CapsuleListsSnapshot(
                        sent = emptyList(),
                        received = offlineSummaries,
                        freshness = ListFreshness.STALE_OFFLINE
                    )
                )
            }
        }

        @Suppress("UNCHECKED_CAST")
        return when {
            sentRes !is OperationResult.Success -> sentRes as OperationResult<CapsuleListsSnapshot>
            else -> receivedRes as OperationResult<CapsuleListsSnapshot>
        }
    }

    override suspend fun getSummary(capsuleId: String): OperationResult<CapsuleSummary> {
        return getSummary(capsuleId, sessionTracker.currentSession())
    }

    override suspend fun getSummary(
        capsuleId: String,
        targetSession: SessionSnapshot
    ): OperationResult<CapsuleSummary> {
        val cleanCapsuleId = capsuleId.trim()
        if (cleanCapsuleId.isBlank()) {
            return OperationResult.Invalid("Capsule ID must not be blank.")
        }
        if (!sessionTracker.isSessionValid(targetSession) || !targetSession.isAuthenticated || targetSession.uid.isNullOrBlank()) {
            return OperationResult.Denied("Authentication required to get capsule summary.")
        }
        val activeUid = targetSession.uid!!

        val summaryRes = firestoreAdapter.getCapsuleSummary(cleanCapsuleId, activeUid)
        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed during getSummary.")
        }

        if (summaryRes is OperationResult.Success) {
            return summaryRes
        }

        // Authoritative Denied / Invalid -> Return immediately without offline fallback
        if (summaryRes is OperationResult.Denied || summaryRes is OperationResult.Invalid) {
            return summaryRes
        }

        // Offline fallback for recipient ONLY when network is Unavailable
        val retention = retentionAdapter
        if (retention != null && summaryRes is OperationResult.Unavailable) {
            val offlineEntries = retention.listVerifiedRecipientEntries(targetSession)
            if (!sessionTracker.isSessionValid(targetSession)) {
                return OperationResult.Denied("Account session changed during offline summary read.")
            }
            if (offlineEntries is OperationResult.Success) {
                val matching = offlineEntries.data.firstOrNull { it.capsuleId == cleanCapsuleId }
                if (matching != null) {
                    return OperationResult.Success(
                        CapsuleSummary(
                            capsuleId = cleanCapsuleId,
                            senderUid = matching.senderUid ?: "",
                            recipientUid = matching.recipientUid ?: activeUid,
                            lock = CapsuleLock(mode = matching.lockMode ?: LockMode.TIME, unlockAt = matching.unlockAt),
                            sendStatus = SendStatus.SENT,
                            availability = Availability.AVAILABLE,
                            title = matching.title,
                            createdAt = matching.createdAt,
                            hasPhoto = matching.hasPhoto,
                            unlockedAt = matching.verifiedUnlockedAt,
                            senderDisplayName = matching.senderDisplayName,
                            recipientDisplayName = matching.recipientDisplayName
                        )
                    )
                }
            }
        }

        return summaryRes
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
        if (result is OperationResult.Success && result.data.availability == Availability.AVAILABLE) {
            val rechecked = firestoreAdapter.readRecipientUnlockRequirements(cleanId, activeUid)
            val serverUnlockedAt = if (rechecked is OperationResult.Success) rechecked.data.unlockedAt else null
            if (serverUnlockedAt != null) {
                retentionAdapter?.saveVerifiedUnlockMarker(
                    targetSession = targetSession,
                    capsuleId = cleanId,
                    verifiedUnlockedAt = serverUnlockedAt,
                    lockMode = mode,
                    recipientUid = activeUid
                )
            }
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

        val retention = retentionAdapter

        // 1. If network is offline, attempt local retention read immediately
        if (!firestoreAdapter.isAvailable()) {
            val retentionReadRes = retention?.readRetainedContent(targetSession, cleanId)
            if (!sessionTracker.isSessionValid(targetSession)) {
                return OperationResult.Denied("Account session changed during content read.")
            }
            return when (retentionReadRes) {
                is OperationResult.Success -> {
                    val retained = retentionReadRes.data
                    val photoPath = if (retained.photoBytes != null) "retainedMedia/${retained.uid}/$cleanId" else null
                    OperationResult.Success(
                        ProtectedCapsuleContent(
                            capsuleId = cleanId,
                            text = retained.text,
                            photoObjectPath = photoPath,
                            photoBytes = retained.photoBytes,
                            photoFormat = retained.photoFormat,
                            availableOffline = true
                        )
                    )
                }
                is OperationResult.Unavailable -> {
                    OperationResult.Unavailable("Capsule unlocked — content unavailable. Connect and retry.")
                }
                else -> {
                    OperationResult.Unavailable("Network unavailable and no retained content found.")
                }
            }
        }

        // 2. Network is online: Check remote server for summary & unlock requirements
        val summaryRes = firestoreAdapter.getCapsuleSummary(cleanId, activeUid)
        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed during content read.")
        }

        if (summaryRes is OperationResult.Denied) {
            return summaryRes // Authoritative denial from server blocks local fallback!
        }

        var summaryTitle: String? = null
        var summaryLockMode: LockMode? = null
        var summarySenderName: String? = null
        var summaryRecipientName: String? = null
        var summaryCreatedAt: Instant? = null

        if (summaryRes is OperationResult.Success) {
            val summary = summaryRes.data
            summaryTitle = summary.title
            summaryLockMode = summary.lock.mode
            summarySenderName = summary.senderDisplayName
            summaryRecipientName = summary.recipientDisplayName
            summaryCreatedAt = summary.createdAt

            val isAuthorCopy = (activeUid == summary.senderUid && summary.senderUid != summary.recipientUid)

            if (isAuthorCopy) {
                // Author Copy branch: direct protected read, NO unlock verification, NO recipient marker, NO retention save
                val contentRes = firestoreAdapter.readProtectedContent(cleanId, activeUid)
                if (!sessionTracker.isSessionValid(targetSession)) {
                    return OperationResult.Denied("Account session changed during content read.")
                }
                if (contentRes is OperationResult.Success) {
                    val content = contentRes.data
                    if (!content.photoObjectPath.isNullOrBlank() && content.photoBytes == null) {
                        return OperationResult.Unavailable("Photo download failed for author copy.")
                    }
                    return OperationResult.Success(content.copy(availableOffline = false))
                }
                return if (contentRes is OperationResult.Unavailable) contentRes else OperationResult.Denied("Protected content unreadable for author copy.")
            }
        }

        // Recipient online branch
        val reqResult = firestoreAdapter.readRecipientUnlockRequirements(cleanId, activeUid)
        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed during content read.")
        }

        if (reqResult !is OperationResult.Success) {
            if (reqResult is OperationResult.Unavailable) {
                val retentionReadRes = retention?.readRetainedContent(targetSession, cleanId)
                if (retentionReadRes is OperationResult.Success) {
                    val retained = retentionReadRes.data
                    val photoPath = if (retained.photoBytes != null) "retainedMedia/${retained.uid}/$cleanId" else null
                    return OperationResult.Success(
                        ProtectedCapsuleContent(
                            capsuleId = cleanId,
                            text = retained.text,
                            photoObjectPath = photoPath,
                            photoBytes = retained.photoBytes,
                            photoFormat = retained.photoFormat,
                            availableOffline = true
                        )
                    )
                }
                if (retentionReadRes is OperationResult.Unavailable) {
                    return OperationResult.Unavailable("Capsule unlocked — content unavailable. Connect and retry.")
                }
                return OperationResult.Unavailable("Network unavailable and no retained content found.")
            }
            return if (reqResult is OperationResult.Denied) reqResult else OperationResult.Denied("Unlock requirements unreadable.")
        }

        val requirements = reqResult.data
        var serverUnlockedAt = requirements.unlockedAt
        if (summaryLockMode == null) {
            summaryLockMode = requirements.lockMode
        }

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

        // Persist unlock marker BEFORE downloading protected content!
        retention?.saveVerifiedUnlockMarker(
            targetSession = targetSession,
            capsuleId = cleanId,
            verifiedUnlockedAt = verifiedTime,
            title = summaryTitle,
            lockMode = summaryLockMode ?: requirements.lockMode,
            senderUid = (summaryRes as? OperationResult.Success)?.data?.senderUid,
            recipientUid = activeUid,
            senderDisplayName = summarySenderName,
            recipientDisplayName = summaryRecipientName,
            createdAt = summaryCreatedAt,
            unlockAt = requirements.unlockAt,
            creationZone = (summaryRes as? OperationResult.Success)?.data?.creationZone,
            hasPhoto = (summaryRes as? OperationResult.Success)?.data?.hasPhoto ?: false
        )
        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed after unlock marker save.")
        }

        val contentRes = firestoreAdapter.readProtectedContent(cleanId, activeUid)
        if (!sessionTracker.isSessionValid(targetSession)) {
            return OperationResult.Denied("Account session changed during content read.")
        }

        if (contentRes is OperationResult.Success) {
            val content = contentRes.data
            if (!content.photoObjectPath.isNullOrBlank() && content.photoBytes == null) {
                return OperationResult.Unavailable("Photo download failed for photo capsule.")
            }
            if (content.text.isNullOrBlank() && content.photoBytes == null) {
                return OperationResult.Unavailable("Capsule has no readable content.")
            }
            val saveResult = try {
                retention?.saveRetainedContent(
                    targetSession = targetSession,
                    capsuleId = cleanId,
                    verifiedUnlockedAt = verifiedTime,
                    text = content.text,
                    photoBytes = content.photoBytes,
                    photoFormat = content.photoFormat,
                    title = summaryTitle,
                    lockMode = summaryLockMode ?: requirements.lockMode,
                    senderDisplayName = summarySenderName,
                    recipientDisplayName = summaryRecipientName,
                    createdAt = summaryCreatedAt,
                    unlockAt = requirements.unlockAt,
                    creationZone = (summaryRes as? OperationResult.Success)?.data?.creationZone
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: java.io.IOException) {
                OperationResult.Unavailable("Offline storage is unavailable.")
            }
            if (!sessionTracker.isSessionValid(targetSession)) {
                return OperationResult.Denied("Account session changed during content retention.")
            }
            return OperationResult.Success(content.copy(availableOffline = saveResult is OperationResult.Success))
        }

        return if (contentRes is OperationResult.Unavailable) contentRes else OperationResult.Denied("Unlock authorization required before reading content.")
    }
}
