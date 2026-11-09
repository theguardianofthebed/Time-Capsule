package com.example.timecapsule.data

import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.adapter.LocationAdapter
import com.example.timecapsule.data.adapter.StorageAdapter
import com.example.timecapsule.data.di.AppDependencyContainer
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleListsSnapshot
import com.example.timecapsule.domain.model.CapsuleLock
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.ProtectedCapsuleContent
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.model.SendStatus
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Fake implementation of [CapsuleRepository] exercising typed results and adapter seams
 * without connecting to external cloud services or network endpoints.
 */
class FakeCapsuleRepository(
    private val sessionTracker: DefaultSessionTracker,
) : CapsuleRepository {

    override fun session(): SessionSnapshot = sessionTracker.currentSession()

    override suspend fun getRecipient(uid: String): OperationResult<RecipientProfile> {
        if (!session().isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        if (uid == "nonexistent") return OperationResult.Invalid("Recipient not found")
        return OperationResult.Success(RecipientProfile(uid, "User $uid"))
    }

    override suspend fun publish(frozenOperation: CapsuleDraft): OperationResult<String> {
        val current = session()
        if (!current.isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        if (!frozenOperation.hasAnyContent()) return OperationResult.Invalid("Empty draft content")
        return OperationResult.Success(frozenOperation.capsuleId)
    }

    override suspend fun reconcile(capsuleId: String): OperationResult<SendStatus> {
        if (!session().isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        return OperationResult.Success(SendStatus.SENT)
    }

    override suspend fun lists(uid: String): OperationResult<CapsuleListsSnapshot> {
        val current = session()
        if (!current.isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        if (current.uid != uid) return OperationResult.Denied("Cross-account list access denied")
        return OperationResult.Success(CapsuleListsSnapshot(emptyList(), emptyList()))
    }

    override suspend fun verifyUnlock(capsuleId: String): OperationResult<Availability> {
        if (!session().isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        return OperationResult.Success(Availability.LOCKED)
    }

    override suspend fun readContent(capsuleId: String): OperationResult<ProtectedCapsuleContent> {
        if (!session().isAuthenticated) return OperationResult.Denied("Unauthenticated session")
        return OperationResult.Denied("Unlock authorization required before reading content")
    }
}

/**
 * Fake adapters for manual container dependency injection testing.
 */
class FakeAuthAdapter(private val tracker: DefaultSessionTracker) : AuthAdapter {
    override fun activeUid(): String? = tracker.currentSession().uid
    override fun signOut() = tracker.clearSession()
}

class FakeFirestoreAdapter : FirestoreAdapter {
    override fun isAvailable(): Boolean = true
    override fun firestoreInstance(): com.google.firebase.firestore.FirebaseFirestore? = null
}

class FakeStorageAdapter : StorageAdapter {
    override fun isAvailable(): Boolean = true
}

class FakeLocationAdapter : LocationAdapter {
    override fun isLocationAvailable(): Boolean = true
}

class FakeLocalRetentionAdapter : LocalRetentionAdapter {
    override suspend fun clearAccountData(uid: String) {}
}

/**
 * Unit tests for repository seams and manual dependency container injection.
 */
class RepositorySeamsTest {

    private val sessionTracker = DefaultSessionTracker()
    private val repository = FakeCapsuleRepository(sessionTracker)

    private val container = AppDependencyContainer(
        sessionTracker = sessionTracker,
        authAdapter = FakeAuthAdapter(sessionTracker),
        firestoreAdapter = FakeFirestoreAdapter(),
        storageAdapter = FakeStorageAdapter(),
        locationAdapter = FakeLocationAdapter(),
        retentionAdapter = FakeLocalRetentionAdapter(),
        repository = repository
    )

    @Test
    fun unauthenticatedOperationsReturnDenied() = runBlocking {
        sessionTracker.clearSession()

        val recipientRes = container.repository.getRecipient("user-b")
        assertTrue(recipientRes is OperationResult.Denied)

        val listsRes = container.repository.lists("user-a")
        assertTrue(listsRes is OperationResult.Denied)
    }

    @Test
    fun authenticatedOperationsSucceedWithTypedResults() = runBlocking {
        sessionTracker.startSession("user-a")

        val recipientRes = container.repository.getRecipient("user-b")
        assertTrue(recipientRes is OperationResult.Success)
        assertEquals("User user-b", (recipientRes as OperationResult.Success).data.displayName)

        val listsRes = container.repository.lists("user-a")
        assertTrue(listsRes is OperationResult.Success)

        val draft = CapsuleDraft(
            capsuleId = "capsule-1",
            recipientUid = "user-b",
            text = "Hello TimeCapsule",
            localPhotoUri = null,
            lock = CapsuleLock(LockMode.TIME, unlockAt = Instant.now())
        )
        val publishRes = container.repository.publish(draft)
        assertTrue(publishRes is OperationResult.Success)
        assertEquals("capsule-1", (publishRes as OperationResult.Success).data)
    }

    @Test
    fun crossAccountListRequestIsDenied() = runBlocking {
        sessionTracker.startSession("user-a")

        val res = container.repository.lists("user-b")
        assertTrue(res is OperationResult.Denied)
    }
}
