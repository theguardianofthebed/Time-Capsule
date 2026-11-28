package com.example.timecapsule

import android.content.Intent
import com.example.timecapsule.domain.session.SessionSnapshot
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.ui.s03.CapsuleTab
import kotlinx.coroutines.Job
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
import com.example.timecapsule.ui.s03.S03CapsuleListFragment
import com.example.timecapsule.ui.s04.S04ContentDraftFragment
import com.example.timecapsule.ui.s05.S05RecipientLockFragment
import com.example.timecapsule.ui.s06.S06ConfirmSendFragment
import com.example.timecapsule.ui.s07.S07CapsuleDetailFragment
import com.example.timecapsule.ui.s08.S08AccountSettingsFragment
import kotlinx.coroutines.launch

/**
 * Main activity for the TimeCapsule application using native Views and ViewBinding.
 * Manages [AppDependencyContainer] and hosts S-01 through S-08 fragments.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastProcessedNotificationIntentKey: String? = null
    private var accountCheck: Job? = null
    private var readOnlySession: SessionSnapshot? = null
    private var verifiedProfile: SessionSnapshot? = null
    private var lastTab = CapsuleTab.RECEIVED
    private var tabOwner: SessionSnapshot? = null
    private var pending: Fragment? = null
    lateinit var container: AppDependencyContainer
        private set

    /**
     * Initializes dependency container, ViewBinding, restored session state, and initial fragment screen.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val edges = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(edges.left, edges.top, edges.right, edges.bottom)
            WindowInsetsCompat.CONSUMED
        }
        binding.fragmentContainer.visibility = View.INVISIBLE

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

        lastTab = if (savedInstanceState?.getBoolean("sentTab") == true) CapsuleTab.SENT else CapsuleTab.RECEIVED
        savedInstanceState?.getString("tabUid")?.let {
            tabOwner = SessionSnapshot(it, savedInstanceState.getLong("tabGeneration"))
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            /** Only restore a destination authorized by the live session. */
            override fun handleOnBackPressed() = navigateBack()
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.sessionTracker.observeSession().collect {
                    val current = currentScreen()
                    if (current != null && requiresSession(current) && !belongsToCurrentSession(current)) {
                        binding.fragmentContainer.visibility = View.INVISIBLE
                        resolveAccountScreen()
                    }
                }
            }
        }
    }

    /** Save the list direction together with the identity that chose it. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("sentTab", lastTab == CapsuleTab.SENT)
        outState.putString("tabUid", tabOwner?.uid)
        outState.putLong("tabGeneration", tabOwner?.sessionGeneration ?: -1)
        outState.putString("KEY_PROCESSED_NOTIFICATION_INTENT", lastProcessedNotificationIntentKey)
    }

    /** Processes incoming singleTop notification entry intent. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::container.isInitialized && verifiedProfile == container.sessionTracker.currentSession()) handleNotificationIntent(intent)
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

        val currentSession = container.sessionTracker.currentSession()
        val validator = NotificationEntryValidator(container.repository, container.sessionTracker)

        lifecycleScope.launch {
            when (val result = validator.validateEntryIntent(intent)) {
                is NotificationEntryResult.Valid -> {
                    if (this@MainActivity.intent === intent && container.sessionTracker.isSessionValid(currentSession) &&
                        container.sessionTracker.currentSession() == currentSession && verifiedProfile == currentSession &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !supportFragmentManager.isStateSaved) {
                        lastProcessedNotificationIntentKey = intentKey
                        navigateToCapsuleDetail(result.capsuleId)
                    }
                }
                is NotificationEntryResult.Invalid -> {
                    // Stale, malformed, or cross-account notification tap rejected safely without recording key or navigating
                }
            }
        }
    }

    /** Rechecks saved outcomes on foreground return without automatically publishing again. */
    override fun onResume() {
        super.onResume()
        if (::container.isInitialized) resolveAccountScreen()
    }

    /** While validating a restored account, keep old views hidden and explain the wait. */
    private fun showAccountCheck(checking: Boolean) {
        binding.fragmentContainer.visibility = if (checking) View.INVISIBLE else View.VISIBLE
        binding.accountCheckProgress.visibility = if (checking) View.VISIBLE else View.GONE
    }

    /** Foreground re-entry starts a new account read. */
    override fun onPause() {
        accountCheck?.cancel()
        super.onPause()
    }

    /** Cancel the old read so a Settings return, rotation, or account change cannot navigate twice. */
    fun resolveAccountScreen() {
        accountCheck?.cancel()
        val previous = currentScreen()
        if (previous == null || isProtected(previous) || previous.arguments?.getString("profileMode") != null) showAccountCheck(true)
        accountCheck = lifecycleScope.launch {
            val manager = container.accountSessionManager
            val uid = manager.restoreSessionIfAuthenticated()
            if (supportFragmentManager.isStateSaved) return@launch
            val screen = currentScreen()
            if (uid == null) {
                readOnlySession = null
                verifiedProfile = null
                if (screen !is S01SignInFragment && (screen !is S02RegisterFragment ||
                    screen.arguments?.getString("profileMode") != null)) navigateToSignIn()
                else showAccountCheck(false)
                return@launch
            }
            val snapshot = manager.currentSession()
            if (snapshot.uid != uid) return@launch
            if (screen is S02RegisterFragment && screen.submitting() &&
                (!requiresSession(screen) || belongsToCurrentSession(screen))) {
                showAccountCheck(false)
                return@launch
            }
            val profile = manager.checkProfile(uid)
            if (!manager.isSessionValid(snapshot) || supportFragmentManager.isStateSaved) return@launch
            if (profile is OperationResult.Success) {
                readOnlySession = null
                verifiedProfile = snapshot
                container.recoveryCoordinator?.triggerForegroundRecovery()
                val current = currentScreen()
                if (current == null || !isProtected(current) || !belongsToCurrentSession(current)) navigateToCapsuleList()
                else {
                    showAccountCheck(false)
                    when (current) {
                        is S03CapsuleListFragment -> current.refreshScreen()
                        is S07CapsuleDetailFragment -> current.refreshScreen()
                    }
                }
                handleNotificationIntent(intent)
            } else {
                verifiedProfile = null
                val mode = when (profile) {
                    is OperationResult.Invalid -> "missing"
                    is OperationResult.Denied -> "denied"
                    else -> "unavailable"
                }
                val current = currentScreen()
                if (mode == "unavailable" && readOnlySession == snapshot &&
                    (current is S03CapsuleListFragment || current is S07CapsuleDetailFragment) &&
                    belongsToCurrentSession(current)) {
                    showAccountCheck(false)
                    return@launch
                }
                readOnlySession = null
                if (current !is S02RegisterFragment || current.arguments?.getString("profileMode") != mode ||
                    !belongsToCurrentSession(current)) {
                    replaceFragment(S02RegisterFragment().apply {
                        arguments = Bundle().apply { putString("profileMode", mode) }
                    })
                } else showAccountCheck(false)
            }
        }
    }

    /** A restored screen carries the account and generation that originally opened it. */
    private fun belongsToCurrentSession(fragment: Fragment): Boolean {
        val owner = container.sessionTracker.currentSession()
        val args = fragment.arguments ?: return false
        return owner.isAuthenticated && owner.uid == args.getString("screenUid") &&
            owner.sessionGeneration == args.getLong("screenGeneration", -1)
    }

    /** Recovery controls belong to the account generation that opened the form. */
    fun recoverySession(form: S02RegisterFragment): SessionSnapshot? {
        if (currentScreen() !== form || !belongsToCurrentSession(form)) return null
        return SessionSnapshot(form.requireArguments().getString("screenUid"), form.requireArguments().getLong("screenGeneration"))
    }

    private fun requiresSession(fragment: Fragment) =
        isProtected(fragment) || (fragment is S02RegisterFragment && fragment.arguments?.getString("profileMode") != null)

    /** Protected destinations also require verified profile or offline read access. */
    private fun isProtected(fragment: Fragment) = fragment !is S01SignInFragment && fragment !is S02RegisterFragment

    /** Pending replacement is the destination until FragmentManager applies its transaction. */
    private fun currentScreen() = pending ?: supportFragmentManager.findFragmentById(R.id.fragmentContainer)

    /** Keep offline re-view available for this UID while directory verification is unavailable. */
    fun openCapsulesWhileOffline() {
        val screen = currentScreen() as? S02RegisterFragment ?: return
        if (screen.arguments?.getString("profileMode") != "unavailable" || !belongsToCurrentSession(screen)) return
        readOnlySession = container.sessionTracker.currentSession()
        navigateToCapsuleList()
    }

    /** Retain the selected list direction only for this session. */
    fun rememberTab(tab: CapsuleTab) {
        lastTab = tab
        tabOwner = container.sessionTracker.currentSession()
    }

    /** Visible Back controls share this policy with the system gesture. */
    fun navigateBack() {
        val screen = currentScreen()
        if (screen != null && requiresSession(screen) && !belongsToCurrentSession(screen)) {
            resolveAccountScreen()
            return
        }
        when (screen) {
            is S01SignInFragment -> if (!screen.submitting()) finish()
            is S02RegisterFragment -> if (!screen.submitting()) {
                if (container.sessionTracker.currentSession().isAuthenticated) finish() else navigateToSignIn()
            }
            is S04ContentDraftFragment, is S07CapsuleDetailFragment, is S08AccountSettingsFragment -> navigateToCapsuleList()
            is S05RecipientLockFragment -> navigateToContentDraft()
            is S06ConfirmSendFragment -> if (screen.canEditDraft()) navigateToRecipientLock() else navigateToCapsuleList()
            else -> finish()
        }
    }

    /** Attach once for new and restored fragments. */
    private fun attachFragmentListeners(fragment: Fragment) {
        when (fragment) {
            is S01SignInFragment -> {
                fragment.onGoToRegisterListener = { navigateToRegister() }
                fragment.onSignInSuccessListener = { resolveAccountScreen() }
            }
            is S02RegisterFragment -> {
                fragment.onRegisterSuccessListener = { resolveAccountScreen() }
                fragment.onGoToSignInListener = { navigateBack() }
            }
            is S03CapsuleListFragment -> {
                fragment.onOpenCapsuleDetailListener = { capsuleId -> navigateToCapsuleDetail(capsuleId) }
                fragment.onCreateCapsuleListener = { navigateToContentDraft() }
                fragment.onAccountSettingsListener = { navigateToAccountSettings() }
            }
            is S04ContentDraftFragment -> {
                fragment.onNextToRecipientLockListener = { navigateToRecipientLock() }
                fragment.onNavigateToMomentsListener = { navigateToCapsuleList() }
                fragment.onNavigateToAccountListener = { navigateToAccountSettings() }
            }
            is S05RecipientLockFragment -> {
                fragment.onBackToContentDraftListener = { navigateBack() }
                fragment.onNextToConfirmSendListener = { navigateToConfirmSend() }
                fragment.onNavigateToMomentsListener = { navigateToCapsuleList() }
                fragment.onNavigateToAccountListener = { navigateToAccountSettings() }
            }
            is S06ConfirmSendFragment -> {
                fragment.onBackToRecipientLockListener = { navigateBack() }
                fragment.onNavigateToMomentsListener = { navigateToCapsuleList() }
                fragment.onNavigateToAccountListener = { navigateToAccountSettings() }
            }
            is S07CapsuleDetailFragment -> {
                fragment.onBackListener = { navigateBack() }
                fragment.onNavigateToMomentsListener = { navigateToCapsuleList() }
                fragment.onCreateCapsuleListener = { navigateToContentDraft() }
                fragment.onAccountSettingsListener = { navigateToAccountSettings() }
            }
            is S08AccountSettingsFragment -> {
                fragment.onSignOutSuccessListener = { navigateToSignIn() }
                fragment.onCreateCapsuleListener = { navigateToContentDraft() }
            }
        }
    }

    /** Show sign-in after sign-out. */
    fun navigateToSignIn() {
        verifiedProfile = null
        readOnlySession = null
        replaceFragment(S01SignInFragment())
    }

    /** Open the registration form. */
    fun navigateToRegister() = replaceFragment(S02RegisterFragment())

    /** Restore the active account's list tab. */
    fun navigateToCapsuleList() {
        val tab = if (tabOwner == container.sessionTracker.currentSession()) lastTab else CapsuleTab.RECEIVED
        replaceFragment(S03CapsuleListFragment().apply {
            arguments = Bundle().apply { putBoolean("showSent", tab == CapsuleTab.SENT) }
        })
    }

    /** Enter the selected capsule through its normal authorization checks. */
    fun navigateToCapsuleDetail(capsuleId: String) = replaceFragment(S07CapsuleDetailFragment.newInstance(capsuleId))

    /** Open account settings. */
    fun navigateToAccountSettings() = replaceFragment(S08AccountSettingsFragment())

    /** Open the shared draft's content step. */
    fun navigateToContentDraft() = replaceFragment(S04ContentDraftFragment())

    /** Open recipient and lock selection. */
    fun navigateToRecipientLock() = replaceFragment(S05RecipientLockFragment())

    /** Keep a selected pending operation bound to its originating session. */
    fun navigateToConfirmSend(capsuleId: String? = null, origin: SessionSnapshot? = null) =
        replaceFragment(S06ConfirmSendFragment.newInstance(capsuleId, origin))

    /** A single pending destination avoids duplicate pushes. Protected screens never enter a back stack. */
    private fun replaceFragment(fragment: Fragment) {
        if (supportFragmentManager.isStateSaved) return
        val owner = container.sessionTracker.currentSession()
        val canRead = readOnlySession == owner && (fragment is S03CapsuleListFragment || fragment is S07CapsuleDetailFragment)
        if (isProtected(fragment) && (!owner.isAuthenticated || (verifiedProfile != owner && !canRead))) {
            readOnlySession = null
            resolveAccountScreen()
            return
        }
        val args = fragment.arguments ?: Bundle().also { fragment.arguments = it }
        args.putString("screenUid", owner.uid)
        args.putLong("screenGeneration", owner.sessionGeneration)
        val previous = currentScreen()
        if (previous?.javaClass == fragment.javaClass && previous.arguments?.keySet() == args.keySet() &&
            args.keySet().all { previous.arguments?.get(it) == args.get(it) }) {
            showAccountCheck(false)
            return
        }
        pending = fragment
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment)
            .runOnCommit {
                if (pending === fragment) {
                    pending = null
                    showAccountCheck(false)
                }
            }.commit()
    }
}
