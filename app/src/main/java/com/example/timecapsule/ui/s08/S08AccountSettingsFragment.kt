package com.example.timecapsule.ui.s08

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.databinding.FragmentS08AccountSettingsBinding
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.ui.ViewModelFactory
import java.util.Locale

/**
 * Fragment implementation for S-08 Account & Settings screen matching the Stitch UI design.
 * Displays authentic user profile data, public Auth UID with one-tap clipboard copy,
 * system permissions status (Location, Notifications), profile updates, and session sign-out.
 */
class S08AccountSettingsFragment : Fragment() {

    private var _binding: FragmentS08AccountSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: S08AccountSettingsViewModel
    private var isUserEditingName = false

    var onSignOutSuccessListener: (() -> Unit)? = null
    var onCreateCapsuleListener: (() -> Unit)? = null

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

        if (!activeUid.isNullOrBlank()) {
            viewModel.checkProfile(activeUid)
        }

        // Profile check observer: updates header and populates field if user is not actively editing
        viewModel.profileCheckState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is OperationResult.Success -> {
                    val name = result.data.displayName
                    if (name.isNotBlank()) {
                        if (!isUserEditingName && !binding.etDisplayName.hasFocus()) {
                            binding.etDisplayName.setText(name)
                        }
                        updateHeaderProfileDisplay(name, activeEmail)
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
            if (result != null) {
                binding.btnUpdateProfile.isEnabled = true
            }
            when (result) {
                is OperationResult.Success -> {
                    binding.tvStatusMessage.text = getString(R.string.msg_profile_updated)
                    val savedName = viewModel.lastSubmittedName ?: binding.etDisplayName.text.toString().trim()
                    updateHeaderProfileDisplay(savedName, activeEmail)
                    isUserEditingName = false
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
            val newName = binding.etDisplayName.text.toString().trim()
            if (activeUid.isNullOrBlank()) {
                binding.tvStatusMessage.text = getString(R.string.err_session_unavailable)
                return@setOnClickListener
            }
            if (newName.isBlank()) {
                binding.tvStatusMessage.text = getString(R.string.err_name_required)
                return@setOnClickListener
            }
            binding.btnUpdateProfile.isEnabled = false
            viewModel.repairProfile(newName)
        }

        // Open System Settings listeners
        binding.btnOpenLocationSettings.setOnClickListener {
            openSystemAppSettings()
        }

        binding.btnOpenNotificationSettings.setOnClickListener {
            openSystemAppSettings()
        }

        // Navigate to S-04 Content Draft
        binding.btnCreateCapsule.setOnClickListener {
            onCreateCapsuleListener?.invoke()
        }

        // Sign out handler
        binding.btnSignOut.setOnClickListener {
            viewModel.signOut()
            binding.tvStatusMessage.text = getString(R.string.msg_signed_out)
            onSignOutSuccessListener?.invoke()
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
     * and displays a neutral non-configured state for location permissions not yet declared in manifest.
     */
    private fun checkAndroidPermissionsStatus() {
        val context = context ?: return

        // Location permission check
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (locationGranted) {
            binding.tvLocationStatusBadge.text = getString(R.string.s08_badge_allowed)
            binding.tvLocationStatusBadge.setBackgroundResource(R.drawable.bg_badge_success)
            binding.tvLocationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.capsule_success_text))
        } else {
            binding.tvLocationStatusBadge.text = getString(R.string.s08_badge_not_configured)
            binding.tvLocationStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral)
            binding.tvLocationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.capsule_text_muted))
        }

        // Notification permission & system settings check across all Android versions
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        if (notificationsEnabled) {
            binding.tvNotificationStatusBadge.text = getString(R.string.s08_badge_allowed)
            binding.tvNotificationStatusBadge.setBackgroundResource(R.drawable.bg_badge_success)
            binding.tvNotificationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.capsule_success_text))
        } else {
            binding.tvNotificationStatusBadge.text = getString(R.string.s08_badge_denied)
            binding.tvNotificationStatusBadge.setBackgroundResource(R.drawable.bg_badge_neutral)
            binding.tvNotificationStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.capsule_text_muted))
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
