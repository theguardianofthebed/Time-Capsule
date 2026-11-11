package com.example.timecapsule.ui.s08

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.databinding.FragmentS08AccountSettingsBinding
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.ui.ViewModelFactory

/**
 * Fragment UI implementation for S-08 Account & Settings screen observing [S08AccountSettingsViewModel].
 */
class S08AccountSettingsFragment : Fragment() {

    private var _binding: FragmentS08AccountSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: S08AccountSettingsViewModel

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
        val container = mainActivity.container
        val factory = ViewModelFactory(container.accountSessionManager)
        viewModel = ViewModelProvider(this, factory)[S08AccountSettingsViewModel::class.java]

        val activeUid = container.authAdapter.activeUid()
        val activeName = container.authAdapter.activeDisplayName() ?: ""

        binding.tvUserUid.text = activeUid ?: "Unauthenticated"
        binding.etDisplayName.setText(activeName)

        if (!activeUid.isNullOrBlank()) {
            viewModel.checkProfile(activeUid)
        }

        viewModel.profileCheckState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is OperationResult.Success -> {
                    if (result.data.displayName.isNotBlank()) {
                        binding.etDisplayName.setText(result.data.displayName)
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

        viewModel.updateState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is OperationResult.Success -> {
                    binding.tvStatusMessage.text = getString(R.string.msg_profile_updated)
                    viewModel.clearState()
                }
                is OperationResult.Invalid -> binding.tvStatusMessage.text = result.message
                is OperationResult.Denied -> binding.tvStatusMessage.text = result.message
                is OperationResult.Unavailable -> binding.tvStatusMessage.text = result.message
                is OperationResult.OutcomeUnknown -> binding.tvStatusMessage.text = result.message
                null -> {}
            }
        }

        // Copy UID to clipboard
        binding.btnCopyUid.setOnClickListener {
            val uid = binding.tvUserUid.text.toString()
            if (uid.isNotBlank() && uid != "Unauthenticated") {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Auth UID", uid)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Auth UID copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        // Update profile / Profile Repair execution
        binding.btnUpdateProfile.setOnClickListener {
            val newName = binding.etDisplayName.text.toString().trim()
            if (activeUid.isNullOrBlank()) {
                binding.tvStatusMessage.text = getString(R.string.err_session_unavailable)
                return@setOnClickListener
            }
            viewModel.repairProfile(newName)
        }

        // Navigate to S-04 Content Draft
        binding.btnCreateCapsule.setOnClickListener {
            onCreateCapsuleListener?.invoke()
        }

        // Sign out and clear session
        binding.btnSignOut.setOnClickListener {
            viewModel.signOut()
            binding.tvStatusMessage.text = getString(R.string.msg_signed_out)
            onSignOutSuccessListener?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
