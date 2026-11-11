package com.example.timecapsule

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.timecapsule.config.FirebaseConfig
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.DefaultCapsuleRepository
import com.example.timecapsule.data.adapter.DefaultAuthAdapter
import com.example.timecapsule.data.adapter.DefaultFirestoreAdapter
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.adapter.LocationAdapter
import com.example.timecapsule.data.adapter.StorageAdapter
import com.example.timecapsule.data.di.AppDependencyContainer
import com.example.timecapsule.data.session.DefaultSessionTracker
import com.example.timecapsule.databinding.ActivityMainBinding
import com.example.timecapsule.ui.s01.S01SignInFragment
import com.example.timecapsule.ui.s02.S02RegisterFragment
import com.example.timecapsule.ui.s04.S04ContentDraftFragment
import com.example.timecapsule.ui.s05.S05RecipientLockFragment
import com.example.timecapsule.ui.s06.S06ConfirmSendFragment
import com.example.timecapsule.ui.s08.S08AccountSettingsFragment

/**
 * Main activity for the TimeCapsule application using native Views and ViewBinding.
 * Manages [AppDependencyContainer] and hosts S-01 through S-08 fragments.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var container: AppDependencyContainer
        private set

    /**
     * Initializes dependency container, ViewBinding, restored session state, and initial fragment screen.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvMainTitle.text = getString(R.string.app_name)

        // Connect debug emulator endpoints in DEBUG builds; verify config availability in RELEASE builds
        val isDebug = BuildConfig.DEBUG
        if (isDebug) {
            val emulatorInitialized = FirebaseConfig.setupDebugEmulatorConnection(applicationContext, true)
            if (!emulatorInitialized) {
                binding.tvMainSubtitle.text = "Firebase Initialization Failed (Fail-Closed)"
                return
            }
        } else {
            if (!FirebaseConfig.isFirebaseAvailable(applicationContext)) {
                binding.tvMainSubtitle.text = "Firebase Configuration Missing (Fail-Closed)"
                return
            }
        }

        // Initialize lightweight dependency container and AccountSessionManager
        val sessionTracker = DefaultSessionTracker()
        val authAdapter = DefaultAuthAdapter()
        val firestoreAdapter = DefaultFirestoreAdapter { applicationContext }
        val accountSessionManager = AccountSessionManager(authAdapter, firestoreAdapter, sessionTracker)

        val storageAdapter = object : StorageAdapter {
            override fun isAvailable(): Boolean = false
        }
        val locationAdapter = object : LocationAdapter {
            override fun isLocationAvailable(): Boolean = false
        }
        val retentionAdapter = object : LocalRetentionAdapter {
            override suspend fun clearAccountData(uid: String) {}
        }

        val repository = DefaultCapsuleRepository(sessionTracker, authAdapter, firestoreAdapter)

        container = AppDependencyContainer(
            sessionTracker = sessionTracker,
            authAdapter = authAdapter,
            firestoreAdapter = firestoreAdapter,
            storageAdapter = storageAdapter,
            locationAdapter = locationAdapter,
            retentionAdapter = retentionAdapter,
            repository = repository,
            accountSessionManager = accountSessionManager
        )

        // Unconditionally restore session into SessionTracker on every Activity/container creation
        val activeUid = container.accountSessionManager.restoreSessionIfAuthenticated()

        // Register Fragment attach listener to re-wire navigation callbacks on Activity recreation/rotation
        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            attachFragmentListeners(fragment)
        }

        // Re-wire callbacks for fragments restored prior to listener registration
        supportFragmentManager.fragments.forEach { fragment ->
            attachFragmentListeners(fragment)
        }

        if (savedInstanceState == null) {
            if (activeUid.isNullOrBlank()) {
                navigateToSignIn()
            } else {
                navigateToContentDraft()
            }
        }
    }

    private fun attachFragmentListeners(fragment: Fragment) {
        when (fragment) {
            is S01SignInFragment -> {
                fragment.onGoToRegisterListener = { navigateToRegister() }
                fragment.onSignInSuccessListener = { navigateToContentDraft() }
            }
            is S02RegisterFragment -> {
                fragment.onRegisterSuccessListener = { navigateToContentDraft() }
            }
            is S04ContentDraftFragment -> {
                fragment.onNextToRecipientLockListener = { navigateToRecipientLock() }
                fragment.onNavigateToMomentsListener = { handleMomentsClick() }
                fragment.onNavigateToAccountListener = { navigateToAccountSettings() }
            }
            is S05RecipientLockFragment -> {
                fragment.onBackToContentDraftListener = { navigateToContentDraft() }
                fragment.onNextToConfirmSendListener = { navigateToConfirmSend() }
                fragment.onNavigateToMomentsListener = { handleMomentsClick() }
                fragment.onNavigateToAccountListener = { navigateToAccountSettings() }
            }
            is S06ConfirmSendFragment -> {
                fragment.onBackToRecipientLockListener = { navigateToRecipientLock() }
                fragment.onNavigateToMomentsListener = { handleMomentsClick() }
                fragment.onNavigateToAccountListener = { navigateToAccountSettings() }
            }
            is S08AccountSettingsFragment -> {
                fragment.onSignOutSuccessListener = { navigateToSignIn() }
                fragment.onCreateCapsuleListener = { navigateToContentDraft() }
            }
        }
    }

    private fun handleMomentsClick() {
        val activeUid = container.accountSessionManager.restoreSessionIfAuthenticated()
        if (activeUid.isNullOrBlank()) {
            navigateToSignIn()
        } else {
            Toast.makeText(
                this,
                "Moments list is unavailable until Issue #21 (Lists and protected detail).",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Navigates to S-01 Sign in screen. */
    fun navigateToSignIn() {
        val fragment = S01SignInFragment().apply {
            onGoToRegisterListener = { navigateToRegister() }
            onSignInSuccessListener = { navigateToContentDraft() }
        }
        replaceFragment(fragment)
    }

    /** Navigates to S-02 Register / Profile Repair screen. */
    fun navigateToRegister() {
        val fragment = S02RegisterFragment().apply {
            onRegisterSuccessListener = { navigateToContentDraft() }
        }
        replaceFragment(fragment)
    }

    /** Navigates to S-08 Account and Settings screen. */
    fun navigateToAccountSettings() {
        val fragment = S08AccountSettingsFragment().apply {
            onSignOutSuccessListener = { navigateToSignIn() }
        }
        replaceFragment(fragment)
    }

    /** Navigates to S-04 Content Creation Draft screen. */
    fun navigateToContentDraft() {
        val fragment = S04ContentDraftFragment().apply {
            onNextToRecipientLockListener = { navigateToRecipientLock() }
            onNavigateToMomentsListener = { navigateToSignIn() }
            onNavigateToAccountListener = { navigateToAccountSettings() }
        }
        replaceFragment(fragment)
    }

    /** Navigates to S-05 Recipient & Lock Selection screen. */
    fun navigateToRecipientLock() {
        val fragment = S05RecipientLockFragment().apply {
            onBackToContentDraftListener = { navigateToContentDraft() }
            onNextToConfirmSendListener = { navigateToConfirmSend() }
            onNavigateToMomentsListener = { navigateToSignIn() }
            onNavigateToAccountListener = { navigateToAccountSettings() }
        }
        replaceFragment(fragment)
    }

    /** Navigates to S-06 Confirm Send Review screen. */
    fun navigateToConfirmSend() {
        val fragment = S06ConfirmSendFragment().apply {
            onBackToRecipientLockListener = { navigateToRecipientLock() }
            onNavigateToMomentsListener = { navigateToSignIn() }
            onNavigateToAccountListener = { navigateToAccountSettings() }
        }
        replaceFragment(fragment)
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
