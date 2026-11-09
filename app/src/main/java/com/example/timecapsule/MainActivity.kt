package com.example.timecapsule

import android.os.Bundle
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

        if (savedInstanceState == null) {
            val activeUid = container.accountSessionManager.restoreSessionIfAuthenticated()
            if (activeUid.isNullOrBlank()) {
                navigateToSignIn()
            } else {
                navigateToAccountSettings()
            }
        }
    }

    /** Navigates to S-01 Sign in screen. */
    fun navigateToSignIn() {
        val fragment = S01SignInFragment().apply {
            onGoToRegisterListener = { navigateToRegister() }
            onSignInSuccessListener = { navigateToAccountSettings() }
        }
        replaceFragment(fragment)
    }

    /** Navigates to S-02 Register / Profile Repair screen. */
    fun navigateToRegister() {
        val fragment = S02RegisterFragment().apply {
            onRegisterSuccessListener = { navigateToAccountSettings() }
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

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
