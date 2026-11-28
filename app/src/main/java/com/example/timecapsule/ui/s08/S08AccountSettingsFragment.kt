package com.example.timecapsule.ui.s08

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.data.notification.DefaultNotificationAdapter
import com.example.timecapsule.databinding.FragmentS08AccountSettingsBinding
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import com.example.timecapsule.ui.ViewModelFactory
import com.example.timecapsule.ui.requestOrOpenSettings
import java.util.Locale
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat

/**
 * Fragment implementation for S-08 Account & Settings screen matching the Stitch UI design.
 * Displays authentic user profile data, public Auth UID with one-tap clipboard copy,
 * system permissions status (Location, Notifications), profile updates, and session sign-out.
 */
class S08AccountSettingsFragment : Fragment() {

    private fun getOriginatingSession(): SessionSnapshot? {
        val args = arguments ?: return null
        val uid = args.getString("screenUid") ?: return null
        val gen = args.getLong("screenGeneration", -1L)
        if (gen == -1L) return null
        return SessionSnapshot(uid, gen)
    }

    private var _binding: FragmentS08AccountSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: S08AccountSettingsViewModel
    private var isUserEditingName = false

    var onSignOutSuccessListener: (() -> Unit)? = null
    var onCreateCapsuleListener: (() -> Unit)? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        checkAndroidPermissionsStatus()
        if (!isGranted) {
            Toast.makeText(
                context,
                "You can still open eligible capsules in the app.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentS08AccountSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = requireActivity() as MainActivity
        val appContainer = mainActivity.container
        val factory = ViewModelFactory(appContainer.accountSessionManager)
        viewModel = ViewModelProvider(this, factory)[S08AccountSettingsViewModel::class.java]

        val activeUid = appContainer.authAdapter.activeUid()
        val activeName = appContainer.authAdapter.activeDisplayName() ?: ""
        val activeEmail = appContainer.authAdapter.activeEmail() ?: ""

        binding.tvUserUid.text = activeUid ?: "Unauthenticated"
        binding.etDisplayName.setText(activeName)
        binding.tvUserEmail.text = activeEmail

        updateHeaderProfileDisplay(activeName, activeEmail)
        checkAndroidPermissionsStatus()

        // Track user edits on display name field to prevent race condition on late profile checks
        binding.etDisplayName.doOnTextChanged { _, _, _, _ ->
            if (binding.etDisplayName.hasFocus()) {
                isUserEditingName = true
            }
        }

        val origin = getOriginatingSession()
        if (!activeUid.isNullOrBlank() && origin != null) {
            viewModel.checkProfile(activeUid, origin)
        }

        // Profile check observer: updates header and populates field if user is not actively editing
        viewModel.profileCheckState.observe(viewLifecycleOwner) { result ->
            val screenOrigin = getOriginatingSession()
            val currentSession = appContainer.accountSessionManager.currentSession()
            if (screenOrigin == null || screenOrigin != currentSession || !appContainer.accountSessionManager.isSessionValid(screenOrigin)) {
                return@observe
            }
            when (result) {
                is OperationResult.Success -> {
                    val name = result.data.displayName
                    if (name.isNotBlank()) {
                        if (!isUserEditingName && !binding.etDisplayName.hasFocus()) {
                            binding.etDisplayName.setText(name)
                        }
                        if (viewModel.lastSubmittedName == null) {
                            updateHeaderProfileDisplay(name, activeEmail)
                        }
                    }
                }
                is OperationResult.Invalid -> {
                    binding.tvStatusMessage.text = getString(R.string.msg_recipient_profile_missing)
                }
                is OperationResult.Unavailable -> {
                    binding.tvStatusMessage.text = result.message
                }
                is OperationResult.Denied -> {
                    binding.tvStatusMessage.text = result.message
                }
                null -> {}
                else -> {}
            }
        }

        // Profile update observer: re-enables button and updates header using confirmed submitted name
        viewModel.updateState.observe(viewLifecycleOwner) { result ->
            val origin = getOriginatingSession()
            val current = appContainer.accountSessionManager.currentSession()
            if (origin == null || origin != current || !appContainer.accountSessionManager.isSessionValid(origin)) {
                return@observe
            }
            if (result != null) {
                binding.btnUpdateProfile.isEnabled = true
            }
            when (result) {
                is OperationResult.Success -> {
                    binding.tvStatusMessage.text = getString(R.string.msg_profile_updated)
                    val savedName = viewModel.lastSubmittedName ?: binding.etDisplayName.text.toString().trim()
                    updateHeaderProfileDisplay(savedName, activeEmail)
                    if (!isUserEditingName) {
                        binding.etDisplayName.setText(savedName)
                    }
                    binding.etDisplayName.clearFocus()
                    viewModel.clearState()
                }
                is OperationResult.Invalid -> binding.tvStatusMessage.text = result.message
                is OperationResult.Denied -> binding.tvStatusMessage.text = result.message
                is OperationResult.Unavailable -> binding.tvStatusMessage.text = result.message
                is OperationResult.OutcomeUnknown -> binding.tvStatusMessage.text = result.message
                null -> {}
            }
        }

        // Copy UID button handler
        binding.btnCopyUid.setOnClickListener {
            val currentOrigin = getOriginatingSession()
            val currentSession = appContainer.accountSessionManager.currentSession()
            if (currentOrigin == null || currentOrigin != currentSession || !appContainer.accountSessionManager.isSessionValid(currentOrigin)) {
                Toast.makeText(requireContext(), getString(R.string.err_session_unavailable), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val uid = binding.tvUserUid.text.toString()
            if (uid.isNotBlank() && uid != "Unauthenticated") {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Auth UID", uid)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), getString(R.string.s08_copied_toast), Toast.LENGTH_SHORT).show()
            }
        }

        // Update profile display name handler with field-specific validation and duplicate submission prevention
        binding.btnUpdateProfile.setOnClickListener {
            val currentOrigin = getOriginatingSession()
            if (currentOrigin == null || !appContainer.accountSessionManager.isSessionValid(currentOrigin)) {
                binding.tvStatusMessage.text = getString(R.string.err_session_unavailable)
                return@setOnClickListener
            }
            val newName = binding.etDisplayName.text.toString().trim()
            if (newName.isBlank()) {
                binding.tvStatusMessage.text = getString(R.string.err_name_required)
                return@setOnClickListener
            }
            isUserEditingName = false
            binding.btnUpdateProfile.isEnabled = false
            viewModel.repairProfile(newName, currentOrigin)
        }

        // Open System Settings listeners
        binding.btnOpenLocationSettings.setOnClickListener {
            if (!locationServicesEnabled()) startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else openSystemAppSettings()
        }

        binding.btnOpenNotificationSettings.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestOrOpenSettings(Manifest.permission.POST_NOTIFICATIONS, ::openSystemNotificationSettings) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                openSystemNotificationSettings()
            }
        }

        // Sign out observer: awaits result and navigates to sign in only on Success
        viewModel.signOutState.observe(viewLifecycleOwner) { result ->
            val origin = getOriginatingSession()
            val current = appContainer.accountSessionManager.currentSession()
            if (origin == null || origin != current || !appContainer.accountSessionManager.isSessionValid(origin)) {
                return@observe
            }
            if (result != null) {
                binding.btnSignOut.isEnabled = true
            }
            when (result) {
                is OperationResult.Success -> {
                    binding.tvStatusMessage.text = getString(R.string.msg_signed_out)
                    onSignOutSuccessListener?.invoke()
                }
                is OperationResult.Unavailable -> {
                    binding.tvStatusMessage.text = result.message
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                is OperationResult.Denied -> {
                    binding.tvStatusMessage.text = result.message
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                null -> {}
                else -> {}
            }
        }

        // Navigate to S-04 Content Draft
        binding.btnCreateCapsule.setOnClickListener {
            onCreateCapsuleListener?.invoke()
        }

        // Sign out handler
        binding.btnSignOut.setOnClickListener {
            val currentOrigin = getOriginatingSession()
            if (currentOrigin == null || !appContainer.accountSessionManager.isSessionValid(currentOrigin)) {
                binding.tvStatusMessage.text = getString(R.string.err_session_unavailable)
                return@setOnClickListener
            }
            binding.btnSignOut.isEnabled = false
            viewModel.signOut(currentOrigin)
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndroidPermissionsStatus()
    }

    /**
     * Updates header avatar initial and greeting text formatted with [displayName] and [email].
     */
    private fun updateHeaderProfileDisplay(displayName: String, email: String) {
        val nameToShow = if (displayName.isNotBlank()) displayName else "User"
        binding.tvGreetingName.text = getString(R.string.s08_greeting, nameToShow)

        val initialChar = if (displayName.isNotBlank()) {
            displayName.first().toString().uppercase(Locale.getDefault())
        } else if (email.isNotBlank()) {
            email.first().toString().uppercase(Locale.getDefault())
        } else {
            "A"
        }
        binding.tvAvatarInitial.text = initialChar
    }

    /**
     * Inspects current Android OS permission and notification status truthfully.
     * Uses [NotificationManagerCompat.areNotificationsEnabled] for notification grants across API levels,
     * and reports system location, precise/approximate access, and the reminder channel independently.
     */
    private fun checkAndroidPermissionsStatus() {
        val context = context ?: return

        // Location permission check - require ACCESS_FINE_LOCATION for precise qualification
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!locationServicesEnabled()) {
            binding.tvLocationStatusBadge.text = "Location services off"
            binding.tvLocationStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral)
        } else if (fineGranted) {
            binding.tvLocationStatusBadge.text = getString(R.string.s08_badge_allowed)
            binding.tvLocationStatusBadge.setBackgroundResource(R.drawable.bg_badge_success)
            binding.tvLocationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.capsule_success_text))
        } else if (coarseGranted) {
            binding.tvLocationStatusBadge.text = getString(R.string.s08_badge_approximate)
            binding.tvLocationStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral)
            binding.tvLocationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.capsule_text_body))
        } else {
            binding.tvLocationStatusBadge.text = "Permission required"
            binding.tvLocationStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral)
            binding.tvLocationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.capsule_text_body))
        }

        // Notification permission & system settings check across all Android versions
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val postPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.getNotificationChannel(DefaultNotificationAdapter.CHANNEL_ID)
        } else null
        val channelEnabled = channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE

        if (notificationsEnabled && postPermissionGranted && channelEnabled) {
            binding.tvNotificationStatusBadge.text = getString(R.string.s08_badge_allowed)
            binding.tvNotificationStatusBadge.setBackgroundResource(R.drawable.bg_badge_success)
            binding.tvNotificationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.capsule_success_text))
        } else {
            binding.tvNotificationStatusBadge.text = when {
                !postPermissionGranted -> "Permission required"
                !notificationsEnabled -> "App notifications off"
                channel == null -> "Channel not created yet"
                else -> "Reminder channel off"
            }
            binding.tvNotificationStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral)
            binding.tvNotificationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.capsule_text_body))
        }
    }

    /** Check the system location switch independently of precise/approximate permission. */
    private fun locationServicesEnabled(): Boolean {
        val manager = requireContext().getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /** Launches system application notification or details settings page for permission management. */
    private fun openSystemNotificationSettings() {
        val context = context ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                val blocked = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                    manager?.getNotificationChannel(DefaultNotificationAdapter.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
                val intent = Intent(if (blocked) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    if (blocked) putExtra(Settings.EXTRA_CHANNEL_ID, DefaultNotificationAdapter.CHANNEL_ID)
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                startActivity(intent)
            } else {
                openSystemAppSettings()
            }
        } catch (_: Exception) {
            openSystemAppSettings()
        }
    }

    /** Launches system application details settings page for permission management. */
    private fun openSystemAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
