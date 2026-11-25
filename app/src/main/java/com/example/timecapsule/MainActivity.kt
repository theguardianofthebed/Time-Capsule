package com.example.timecapsule

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.timecapsule.data.di.AppDependencyContainer
import com.example.timecapsule.data.notification.DefaultNotificationAdapter
import com.example.timecapsule.data.notification.NotificationEntryResult
import com.example.timecapsule.data.notification.NotificationEntryValidator
import com.example.timecapsule.databinding.ActivityMainBinding
import com.example.timecapsule.ui.s01.S01SignInFragment
import com.example.timecapsule.ui.s02.S02RegisterFragment
import com.example.timecapsule.ui.s04.S04ContentDraftFragment
import com.example.timecapsule.ui.s05.S05RecipientLockFragment
import com.example.timecapsule.ui.s06.S06ConfirmSendFragment
import com.example.timecapsule.ui.s08.S08AccountSettingsFragment
import kotlinx.coroutines.launch

/**
 * Main activity for the TimeCapsule application using native Views and ViewBinding.
 * Manages [AppDependencyContainer] and hosts S-01 through S-08 fragments.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastProcessedNotificationIntentKey: String? = null
    lateinit var container: AppDependencyContainer
        private set

    /**
     * Initializes dependency container, ViewBinding, restored session state, and initial fragment screen.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase configuration and retrieve application-scoped dependency container
        val isDebug = BuildConfig.DEBUG
        val app = application as TimeCapsuleApplication
        val initSuccess = app.initializeFirebaseAndContainer()
        val appContainer = app.container
        if (!initSuccess || appContainer == null) {
            binding.tvInitError.text = if (isDebug)
                "Firebase Initialization Failed (Fail-Closed)"
            else
                "Firebase Configuration Missing (Fail-Closed)"
            binding.tvInitError.visibility = View.VISIBLE
            return
        }

        container = appContainer

        // Register Fragment attach listener to re-wire navigation callbacks on Activity recreation/rotation
        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            attachFragmentListeners(fragment)
        }

        // Re-wire callbacks for fragments restored prior to listener registration
        supportFragmentManager.fragments.forEach { fragment ->
            attachFragmentListeners(fragment)
        }

        if (savedInstanceState != null) {
            lastProcessedNotificationIntentKey = savedInstanceState.getString("KEY_PROCESSED_NOTIFICATION_INTENT")
        }

        lifecycleScope.launch {
            val activeUid = container.accountSessionManager.restoreSessionIfAuthenticated()
            if (!activeUid.isNullOrBlank()) {
                container.recoveryCoordinator?.triggerForegroundRecovery()
            }
            if (savedInstanceState == null) {
                if (activeUid.isNullOrBlank()) {
                    navigateToSignIn()
                } else {
                    navigateToContentDraft()
                }
            }
            handleNotificationIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("KEY_PROCESSED_NOTIFICATION_INTENT", lastProcessedNotificationIntentKey)
    }

    /** Processes incoming singleTop notification entry intent. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Inspects and authoritatively revalidates notification entry [intent] against current active session.
     * Prevents duplicate intent processing after Activity recreation.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null || !::container.isInitialized) return
        val action = intent.action ?: return
        if (action != DefaultNotificationAdapter.ACTION_NOTIFICATION_TAP) return

        val uid = intent.getStringExtra(DefaultNotificationAdapter.EXTRA_ACCOUNT_UID) ?: ""
        val capsuleId = intent.getStringExtra(DefaultNotificationAdapter.EXTRA_CAPSULE_ID) ?: ""
        val intentKey = "$action:$uid:$capsuleId"

        if (lastProcessedNotificationIntentKey == intentKey) return
        lastProcessedNotificationIntentKey = intentKey

        val currentSession = container.sessionTracker.currentSession()
        val validator = NotificationEntryValidator(container.repository, container.sessionTracker)

        lifecycleScope.launch {
            when (val result = validator.validateEntryIntent(intent)) {
                is NotificationEntryResult.Valid -> {
                    if (container.sessionTracker.isSessionValid(currentSession) &&
                        container.sessionTracker.currentSession() == currentSession) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.s07_title_capsule_ready),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                is NotificationEntryResult.Invalid -> {
                    // Stale, malformed, or cross-account notification tap rejected safely without navigation
                }
            }
        }
    }

    /** Rechecks saved outcomes on foreground return without automatically publishing again. */
    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) {
            lifecycleScope.launch {
                val activeUid = container.accountSessionManager.restoreSessionIfAuthenticated()
                if (!activeUid.isNullOrBlank()) {
                    container.recoveryCoordinator?.triggerForegroundRecovery()
                }
            }
        }
    }

    private fun attachFragmentListeners(fragment: Fragment) {
        when (fragment) {
            is S01SignInFragment -> {
                fragment.onGoToRegisterListener = { navigateToRegister() }
                fragment.onSignInSuccessListener = {
                    lifecycleScope.launch {
                        val uid = container.accountSessionManager.restoreSessionIfAuthenticated()
                        if (!uid.isNullOrBlank()) {
                            container.recoveryCoordinator?.triggerForegroundRecovery()
                        }
                    }
                    navigateToContentDraft()
                }
            }
            is S02RegisterFragment -> {
                fragment.onRegisterSuccessListener = {
                    lifecycleScope.launch {
                        val uid = container.accountSessionManager.restoreSessionIfAuthenticated()
                        if (!uid.isNullOrBlank()) {
                            container.recoveryCoordinator?.triggerForegroundRecovery()
                        }
                    }
                    navigateToContentDraft()
                }
                fragment.onGoToSignInListener = { navigateToSignIn() }
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
        lifecycleScope.launch {
            val activeUid = container.accountSessionManager.restoreSessionIfAuthenticated()
            if (activeUid.isNullOrBlank()) {
                navigateToSignIn()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Your capsule list isn't available yet.",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
            onGoToSignInListener = { navigateToSignIn() }
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
