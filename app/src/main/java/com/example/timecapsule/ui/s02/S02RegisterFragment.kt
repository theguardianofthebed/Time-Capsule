package com.example.timecapsule.ui.s02

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.databinding.FragmentS02RegisterBinding
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.ui.ViewModelFactory

/**
 * Fragment implementation for S-02 Register & Profile Repair screen matching the Stitch UI design.
 * Handles new Firebase email/password account creation with display name, as well as the
 * missing-profile repair variant mode when an authenticated session lacks a valid Firestore profile.
 */
class S02RegisterFragment : Fragment() {

    private var _binding: FragmentS02RegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: S02RegisterViewModel
    private var isPasswordVisible = false

    var onRegisterSuccessListener: ((String) -> Unit)? = null
    var onGoToSignInListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentS02RegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = requireActivity() as MainActivity
        val appContainer = mainActivity.container
        val factory = ViewModelFactory(appContainer.accountSessionManager)
        viewModel = ViewModelProvider(this, factory)[S02RegisterViewModel::class.java]

        val activeUid = appContainer.authAdapter.activeUid()
        val isProfileRepairMode = !activeUid.isNullOrBlank()

        // Configure layout and field visibility depending on whether profile repair mode is active
        if (isProfileRepairMode) {
            binding.tvS02Title.text = getString(R.string.s02_headline_repair)
            binding.tvS02Subtitle.text = getString(R.string.s02_subtitle_repair)
            binding.btnRegister.text = getString(R.string.btn_repair_profile)
            binding.layoutEmailContainer.visibility = View.GONE
            binding.layoutPasswordContainer.visibility = View.GONE
            binding.layoutConfirmPasswordContainer.visibility = View.GONE
            binding.layoutSignInLink.visibility = View.GONE
            val activeName = appContainer.authAdapter.activeDisplayName() ?: ""
            binding.etDisplayName.setText(activeName)
        } else {
            binding.tvS02Title.text = getString(R.string.s02_headline)
            binding.tvS02Subtitle.text = getString(R.string.s02_subtitle)
            binding.btnRegister.text = getString(R.string.s02_btn_submit)
            binding.layoutEmailContainer.visibility = View.VISIBLE
            binding.layoutPasswordContainer.visibility = View.VISIBLE
            binding.layoutConfirmPasswordContainer.visibility = View.VISIBLE
            binding.layoutSignInLink.visibility = View.VISIBLE
        }

        // Initialize password visibility accessibility content description
        binding.btnTogglePassword.contentDescription = getString(R.string.cd_show_password)

        // Password visibility toggle action
        binding.btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                binding.btnTogglePassword.contentDescription = getString(R.string.cd_hide_password)
            } else {
                binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                binding.btnTogglePassword.contentDescription = getString(R.string.cd_show_password)
            }
            binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
        }

        // Clear status message when user modifies any input field
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvStatusMessage.text = ""
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etDisplayName.addTextChangedListener(textWatcher)
        binding.etEmail.addTextChangedListener(textWatcher)
        binding.etPassword.addTextChangedListener(textWatcher)
        binding.etConfirmPassword.addTextChangedListener(textWatcher)

        // Observe registration and profile repair ViewModel state
        viewModel.registerState.observe(viewLifecycleOwner) { result ->
            binding.btnRegister.isEnabled = true
            binding.pbLoading.visibility = View.GONE
            when (result) {
                is OperationResult.Success -> {
                    binding.tvStatusMessage.text = getString(R.string.msg_account_created)
                    onRegisterSuccessListener?.invoke(result.data)
                    viewModel.clearState()
                }
                is OperationResult.Invalid -> binding.tvStatusMessage.text = result.message
                is OperationResult.Denied -> binding.tvStatusMessage.text = result.message
                is OperationResult.Unavailable -> binding.tvStatusMessage.text = result.message
                is OperationResult.OutcomeUnknown -> binding.tvStatusMessage.text = result.message
                null -> {}
            }
        }

        // Submit button action (Registration or Profile Repair) with field-specific validation
        binding.btnRegister.setOnClickListener {
            val displayName = binding.etDisplayName.text.toString().trim()

            if (isProfileRepairMode) {
                if (displayName.isBlank()) {
                    binding.tvStatusMessage.text = getString(R.string.err_name_required)
                    return@setOnClickListener
                }
                binding.btnRegister.isEnabled = false
                binding.pbLoading.visibility = View.VISIBLE
                binding.tvStatusMessage.text = getString(R.string.msg_creating_account)
                viewModel.repairProfile(displayName)
            } else {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()
                val confirmPassword = binding.etConfirmPassword.text.toString()

                if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
                    binding.tvStatusMessage.text = getString(R.string.err_required_field)
                    return@setOnClickListener
                }

                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    binding.tvStatusMessage.text = getString(R.string.err_invalid_email)
                    return@setOnClickListener
                }

                if (password.length < 6) {
                    binding.tvStatusMessage.text = getString(R.string.err_password_too_short)
                    return@setOnClickListener
                }

                if (password != confirmPassword) {
                    binding.tvStatusMessage.text = getString(R.string.err_password_mismatch)
                    return@setOnClickListener
                }

                binding.btnRegister.isEnabled = false
                binding.pbLoading.visibility = View.VISIBLE
                binding.tvStatusMessage.text = getString(R.string.msg_creating_account)
                viewModel.registerAndCreateProfile(email, password, displayName)
            }
        }

        // Navigation back to S-01 Sign in
        binding.tvGoToSignIn.setOnClickListener {
            onGoToSignInListener?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
