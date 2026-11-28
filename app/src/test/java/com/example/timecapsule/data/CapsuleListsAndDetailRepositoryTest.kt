package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.adapter.RetainedContent
import com.example.timecapsule.data.adapter.RetainedSummary
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.OriginalPhotoFormat
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.RecipientUnlockRequirements
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Checkpoints A and B: account-scoped lists, capsule summary retrieval,
 * role-aware protected content reads, and offline retention fallback.
 */
class CapsuleListsAndDetailRepositoryTest {

    private lateinit var sessionTracker: DefaultSessionTracker
    private lateinit var fakeAuthAdapter: FakeAuthAdapter
    private lateinit var fakeFirestoreAdapter: FakeFirestoreAdapter
    private lateinit var fakeRetentionAdapter: FakeRetentionAdapter
    private lateinit var repository: DefaultCapsuleRepository

    private val userA = "user-a"
    private val userB = "user-b"

    @Before
    fun setUp() {
        sessionTracker = DefaultSessionTracker()
        sessionTracker.startSession(userA)
        fakeAuthAdapter = FakeAuthAdapter(userA)
        fakeFirestoreAdapter = FakeFirestoreAdapter()
        fakeRetentionAdapter = FakeRetentionAdapter()
        repository = DefaultCapsuleRepository(
            sessionTracker = sessionTracker,
            authAdapter = fakeAuthAdapter,
            firestoreAdapter = fakeFirestoreAdapter,
            retentionAdapter = fakeRetentionAdapter
        )
    }

    @Test
    fun `lists returns sent and received summaries for authenticated user`() = runBlocking {
        val now = Instant.now()
        val cap1 = CapsuleSummary(
            capsuleId = "cap-1",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(mode = LockMode.TIME, unlockAt = now.plusSeconds(3600)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED,
            title = "To User B",
            createdAt = now
        )
        val cap2 = CapsuleSummary(
            capsuleId = "cap-2",
            senderUid = userB,
            recipientUid = userA,
            lock = CapsuleLock(mode = LockMode.TIME, unlockAt = now.minusSeconds(3600)),
            sendStatus = SendStatus.SENT,
            availability = Availability.AVAILABLE,
            title = "From User B",
            createdAt = now.minusSeconds(100)
        )
        fakeFirestoreAdapter.sentList[userA] = listOf(cap1)
        fakeFirestoreAdapter.receivedList[userA] = listOf(cap2)

        val result = repository.lists(userA)
        assertTrue(result is OperationResult.Success)
        val snapshot = (result as OperationResult.Success).data
        assertEquals(1, snapshot.sent.size)
        assertEquals("cap-1", snapshot.sent.first().capsuleId)
        assertEquals(1, snapshot.received.size)
        assertEquals("cap-2", snapshot.received.first().capsuleId)
    }

    @Test
    fun `lists denies when requested for non-matching UID`() = runBlocking {
        val result = repository.lists(userB)
        assertTrue(result is OperationResult.Denied)
    }

    @Test
    fun `getSummary returns summary for participant`() = runBlocking {
        val now = Instant.now()
        val cap = CapsuleSummary(
            capsuleId = "cap-100",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(mode = LockMode.TIME),
            sendStatus = SendStatus.SENT,
            title = "Secret Capsule",
            createdAt = now
        )
        fakeFirestoreAdapter.summaries["cap-100"] = cap

        val res = repository.getSummary("cap-100")
        assertTrue(res is OperationResult.Success)
        assertEquals("Secret Capsule", (res as OperationResult.Success).data.title)
    }

    @Test
    fun `readContent for Author Copy returns content without recipient unlock transaction`() = runBlocking {
        val now = Instant.now()
        val cap = CapsuleSummary(
            capsuleId = "cap-author-copy",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(mode = LockMode.TIME, unlockAt = now.plusSeconds(3600)),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED
        )
        fakeFirestoreAdapter.summaries["cap-author-copy"] = cap
        fakeFirestoreAdapter.protectedContent["cap-author-copy"] = ProtectedCapsuleContent(
            capsuleId = "cap-author-copy",
            text = "Author copy private note",
            photoObjectPath = null
        )

        val res = repository.readContent("cap-author-copy")
        assertTrue(res is OperationResult.Success)
        val content = (res as OperationResult.Success).data
        assertEquals("Author copy private note", content.text)
        assertEquals(false, content.availableOffline)
    }

    @Test
    fun `readContent for recipient checks unlock requirements before reading`() = runBlocking {
        sessionTracker.startSession(userB)

        val cap = CapsuleSummary(
            capsuleId = "cap-locked",
            senderUid = userA,
            recipientUid = userB,
            lock = CapsuleLock(mode = LockMode.TIME),
            sendStatus = SendStatus.SENT,
            availability = Availability.LOCKED
        )
        fakeFirestoreAdapter.summaries["cap-locked"] = cap
        fakeFirestoreAdapter.requirements["cap-locked"] = RecipientUnlockRequirements(
            capsuleId = "cap-locked",
            recipientUid = userB,
            unlocked = false,
            lockMode = LockMode.TIME
        )

        val res = repository.readContent("cap-locked")
        assertTrue(res is OperationResult.Denied)
    }

    @Test
    fun `offline fallback returns retained recipient entries when network unavailable`() = runBlocking {
        fakeFirestoreAdapter.isNetworkDown = true
        fakeRetentionAdapter.retainedEntries.add(
            RetainedContent(
                uid = userA,
                capsuleId = "cap-retained",
                text = "Retained offline text",
                photoBytes = null,
                photoFormat = null,
                verifiedUnlockedAt = Instant.now()
            )
        )

        val res = repository.lists(userA)
        assertTrue(res is OperationResult.Success)
        val snapshot = (res as OperationResult.Success).data
        assertEquals(1, snapshot.received.size)
        assertEquals("cap-retained", snapshot.received.first().capsuleId)
    }

    // --- Fake Test Collaborators ---

    private class FakeAuthAdapter(private var activeUid: String?) : AuthAdapter {
        override fun activeUid(): String? = activeUid
        override fun activeEmail(): String = "test@example.com"
        override fun activeDisplayName(): String = "Test User"
        override suspend fun signIn(email: String, password: String): OperationResult<String> = OperationResult.Success(activeUid ?: "")
        override suspend fun signUp(email: String, password: String, displayName: String): OperationResult<String> = OperationResult.Success(activeUid ?: "")
        override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> = OperationResult.Success(Unit)
        override fun signOut() { activeUid = null }
    }

    private class FakeFirestoreAdapter : FirestoreAdapter {
        var isNetworkDown = false
        val sentList = mutableMapOf<String, List<CapsuleSummary>>()
        val receivedList = mutableMapOf<String, List<CapsuleSummary>>()
        val summaries = mutableMapOf<String, CapsuleSummary>()
        val requirements = mutableMapOf<String, RecipientUnlockRequirements>()
        val protectedContent = mutableMapOf<String, ProtectedCapsuleContent>()

        override fun isAvailable(): Boolean = !isNetworkDown
        override fun firestoreInstance(): FirebaseFirestore? = null
        override suspend fun getRecipientProfile(uid: String) = OperationResult.Invalid("Not needed")
        override suspend fun saveRecipientProfile(profile: RecipientProfile) = OperationResult.Success(Unit)

        override suspend fun getSentCapsuleSummaries(senderUid: String): OperationResult<List<CapsuleSummary>> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            return OperationResult.Success(sentList[senderUid] ?: emptyList())
        }

        override suspend fun getReceivedCapsuleSummaries(recipientUid: String): OperationResult<List<CapsuleSummary>> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            return OperationResult.Success(receivedList[recipientUid] ?: emptyList())
        }

        override suspend fun getCapsuleSummary(capsuleId: String, uid: String): OperationResult<CapsuleSummary> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            val sum = summaries[capsuleId] ?: return OperationResult.Invalid("Not found")
            if (sum.senderUid != uid && sum.recipientUid != uid) return OperationResult.Denied("Not participant")
            return OperationResult.Success(sum)
        }

        override suspend fun readRecipientUnlockRequirements(
            capsuleId: String,
            recipientUid: String
        ): OperationResult<RecipientUnlockRequirements> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            val req = requirements[capsuleId] ?: return OperationResult.Invalid("Not found")
            if (req.recipientUid != recipientUid) return OperationResult.Denied("Not recipient")
            return OperationResult.Success(req)
        }

        override suspend fun readProtectedContent(
            capsuleId: String,
            recipientUid: String
        ): OperationResult<ProtectedCapsuleContent> {
            if (isNetworkDown) return OperationResult.Unavailable("Network down")
            val content = protectedContent[capsuleId] ?: return OperationResult.Invalid("Not found")
            return OperationResult.Success(content)
        }
    }

    private class FakeRetentionAdapter : LocalRetentionAdapter {
        val retainedEntries = mutableListOf<RetainedContent>()

        override suspend fun saveRetainedContent(
            targetSession: SessionSnapshot,
            capsuleId: String,
            verifiedUnlockedAt: Instant,
            text: String?,
            photoBytes: ByteArray?,
            photoFormat: OriginalPhotoFormat?,
            title: String?,
            lockMode: LockMode?,
            senderUid: String?,
            recipientUid: String?,
            senderDisplayName: String?,
            recipientDisplayName: String?,
            createdAt: Instant?,
            unlockAt: Instant?,
            creationZone: String?,
            location: LocationTarget?
        ): OperationResult<Unit> {
            retainedEntries.add(
                RetainedContent(
                    uid = targetSession.uid ?: "",
                    capsuleId = capsuleId,
                    text = text,
                    photoBytes = photoBytes,
                    photoFormat = photoFormat,
                    verifiedUnlockedAt = verifiedUnlockedAt
                )
            )
            return OperationResult.Success(Unit)
        }

        override suspend fun readRetainedContent(
            targetSession: SessionSnapshot,
            capsuleId: String
        ): OperationResult<RetainedContent> {
            val entry = retainedEntries.firstOrNull { it.uid == targetSession.uid && it.capsuleId == capsuleId }
                ?: return OperationResult.Denied("Not found locally")
            return OperationResult.Success(entry)
        }

        override suspend fun listVerifiedRecipientEntries(
            targetSession: SessionSnapshot
        ): OperationResult<List<RetainedSummary>> {
            val userEntries = retainedEntries.filter { it.uid == targetSession.uid }.map {
                RetainedSummary(
                    uid = it.uid,
                    capsuleId = it.capsuleId,
                    verifiedUnlockedAt = it.verifiedUnlockedAt,
                    hasText = !it.text.isNullOrBlank(),
                    hasPhoto = it.photoBytes != null,
                    isContentAvailable = true
                )
            }
            return OperationResult.Success(userEntries)
        }

        override suspend fun clearAccountData(uid: String) {
            retainedEntries.removeAll { it.uid == uid }
        }

        override suspend fun clearAllData() {
            retainedEntries.clear()
        }
    }
}
