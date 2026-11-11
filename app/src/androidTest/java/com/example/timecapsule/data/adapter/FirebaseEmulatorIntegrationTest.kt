package com.example.timecapsule.data.adapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.timecapsule.config.FirebaseConfig
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Instrumentation Integration tests executing against live Firebase Auth & Firestore Emulators.
 * Verifies Issue #10 exact recipient lookup, profile repair, and Firestore security constraints.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseEmulatorIntegrationTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupEmulatorConnection() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            try {
                InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                    context.packageName,
                    "android.permission.ACCESS_LOCAL_NETWORK",
                )
            } catch (_: Exception) {
                // Ignore runtime permission grant fallback on platforms where not enforced
            }
            val initialized = FirebaseConfig.setupDebugEmulatorConnection(context, true)
            assertTrue("Firebase debug emulator connection must succeed for tests", initialized)
        }
    }

    @get:Rule
    val grantLocalNetworkPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant("android.permission.ACCESS_LOCAL_NETWORK")

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var authAdapter: DefaultAuthAdapter
    private lateinit var firestoreAdapter: DefaultFirestoreAdapter

    @Before
    fun setUp() {
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        authAdapter = DefaultAuthAdapter { auth }
        firestoreAdapter = DefaultFirestoreAdapter { InstrumentationRegistry.getInstrumentation().targetContext }
    }

    private fun createAndSignInUser(email: String, name: String): String = runBlocking {
        val result = authAdapter.signUp(email, "password123", name)
        if (result !is OperationResult.Success) {
            val signInResult = authAdapter.signIn(email, "password123")
            if (signInResult is OperationResult.Success) {
                return@runBlocking signInResult.data
            }
            fail("Failed to setup test user ($email): $result")
            error("Unreachable")
        } else {
            return@runBlocking result.data
        }
    }

    @Test
    fun test1_authenticatedGetOtherUserProfileSucceeds() = runBlocking {
        val uidA = createAndSignInUser("userA_test1@example.com", "User A")
        val saveRes = firestoreAdapter.saveRecipientProfile(RecipientProfile(uidA, "User A"))
        assertTrue("User A profile save must succeed", saveRes is OperationResult.Success)

        createAndSignInUser("userB_test1@example.com", "User B")
        val getRes = firestoreAdapter.getRecipientProfile(uidA)
        assertTrue("Authenticated User B should get User A profile", getRes is OperationResult.Success)
        assertEquals("User A", (getRes as OperationResult.Success).data.displayName)
    }

    @Test
    fun test2_listRequestDenied() = runBlocking {
        createAndSignInUser("user_test2@example.com", "User Test 2")
        try {
            firestore.collection("recipientDirectory").get().await()
            fail("List query on recipientDirectory must be denied by firestore.rules")
        } catch (e: FirebaseFirestoreException) {
            assertEquals("List query failure must be PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        } catch (e: Exception) {
            fail("List query failure must be FirebaseFirestoreException PERMISSION_DENIED, got: $e")
        }
    }

    @Test
    fun test3_unauthenticatedGetDenied() = runBlocking {
        auth.signOut()
        val getRes = firestoreAdapter.getRecipientProfile("some-target-uid")
        assertTrue("Unauthenticated get must be denied", getRes !is OperationResult.Success)
    }

    @Test
    fun test4_ownerCreateAndUpdateProfileSucceeds() = runBlocking {
        val uid = createAndSignInUser("user_test4@example.com", "Initial Name")
        val createRes = firestoreAdapter.saveRecipientProfile(RecipientProfile(uid, "Initial Name"))
        assertTrue(createRes is OperationResult.Success)

        val updateRes = firestoreAdapter.saveRecipientProfile(RecipientProfile(uid, "Updated Name"))
        assertTrue(updateRes is OperationResult.Success)

        val getRes = firestoreAdapter.getRecipientProfile(uid)
        assertTrue(getRes is OperationResult.Success)
        assertEquals("Updated Name", (getRes as OperationResult.Success).data.displayName)
    }

    @Test
    fun test5_otherUserCreateAndUpdateProfileDenied() = runBlocking {
        val uidA = createAndSignInUser("userA_test5@example.com", "User A")
        createAndSignInUser("userB_test5@example.com", "User B")

        val updateRes = firestoreAdapter.saveRecipientProfile(RecipientProfile(uidA, "User B Hacked Name"))
        assertTrue("Other user update must be denied", updateRes !is OperationResult.Success)
    }

    @Test
    fun test6_forbiddenFieldsDenied() = runBlocking {
        val uid = createAndSignInUser("user_test6@example.com", "User 6")
        try {
            firestore.collection("recipientDirectory")
                .document(uid)
                .set(mapOf("uid" to uid, "displayName" to "User 6", "role" to "admin"))
                .await()
            fail("Forbidden fields must be denied")
        } catch (e: FirebaseFirestoreException) {
            assertEquals("Forbidden fields set must be PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        } catch (e: Exception) {
            fail("Forbidden fields set must be FirebaseFirestoreException PERMISSION_DENIED, got: $e")
        }
    }

    @Test
    fun test7_mismatchedPathAndDocumentUidDenied() = runBlocking {
        val uid = createAndSignInUser("user_test7@example.com", "User 7")
        try {
            firestore.collection("recipientDirectory")
                .document("mismatched-path-uid")
                .set(mapOf("uid" to uid, "displayName" to "User 7"))
                .await()
            fail("Mismatched path UID and document UID must be denied")
        } catch (e: FirebaseFirestoreException) {
            assertEquals("Mismatched path UID set must be PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        } catch (e: Exception) {
            fail("Mismatched path UID set must be FirebaseFirestoreException PERMISSION_DENIED, got: $e")
        }
    }
}
