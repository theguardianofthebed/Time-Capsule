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

    /** Render registration, same-account repair, or a read-only retry after profile verification. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = requireActivity() as MainActivity
        val appContainer = mainActivity.container
        val factory = ViewModelFactory(appContainer.accountSessionManager)
        viewModel = ViewModelProvider(this, factory)[S02RegisterViewModel::class.java]

        val mode = arguments?.getString("profileMode")
        val isProfileRepairMode = mode == "missing"
        val verificationRequired = mode == "unavailable" || mode == "denied"

        // Configure layout and field visibility depending on whether profile repair mode is active
        if (isProfileRepairMode || verificationRequired) {
            binding.tvS02Title.text = getString(R.string.s02_headline_repair)
            binding.tvS02Subtitle.text = getString(R.string.s02_subtitle_repair)
            binding.btnRegister.text = getString(R.string.btn_repair_profile)
            binding.layoutEmailContainer.visibility = View.GONE
            binding.layoutPasswordContainer.visibility = View.GONE
            binding.layoutConfirmPasswordContainer.visibility = View.GONE
            binding.layoutSignInLink.visibility = View.GONE
            val activeName = appContainer.authAdapter.activeDisplayName() ?: ""
            binding.etDisplayName.setText(activeName)
            if (verificationRequired) {
                binding.tvS02Title.setText(R.string.profile_check_title)
                binding.tvS02Subtitle.setText(if (mode == "denied") R.string.profile_check_denied else R.string.profile_check_unavailable)
                binding.btnRegister.setText(R.string.profile_check_retry)
                if (mode == "unavailable") {
                    binding.layoutSignInLink.visibility = View.VISIBLE
                    binding.existingAccountLabel.visibility = View.GONE
                    binding.tvGoToSignIn.text = "View saved capsules"
                }
                binding.nameLabel.visibility = View.GONE
                binding.layoutNameContainer.visibility = View.GONE
            }
        } else {
            binding.tvS02Title.text = getString(R.string.s02_headline)
            binding.tvS02Subtitle.text = getString(R.string.s02_subtitle)
            binding.btnRegister.text = getString(R.string.s02_btn_submit)
            binding.layoutEmailContainer.visibility = View.VISIBLE
            binding.layoutPasswordContainer.visibility = View.VISIBLE
            binding.layoutConfirmPasswordContainer.visibility = View.VISIBLE
            binding.layoutSignInLink.visibility = View.VISIBLE
        }

        binding.btnExitRecovery.visibility = if (mode == null) View.GONE else View.VISIBLE
        binding.btnExitRecovery.setOnClickListener {
            val origin = mainActivity.recoverySession(this)
            if (origin != null) viewModel.signOut(origin) else mainActivity.resolveAccountScreen()
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
                if (isResumed && viewModel.busy.value != true) {
                    viewModel.clearState()
                    binding.tvStatusMessage.text = ""
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etDisplayName.addTextChangedListener(textWatcher)
        binding.etEmail.addTextChangedListener(textWatcher)
        binding.etPassword.addTextChangedListener(textWatcher)
        binding.etConfirmPassword.addTextChangedListener(textWatcher)

        // Observe registration and profile repair ViewModel state
        viewModel.busy.observe(viewLifecycleOwner) { busy ->
            binding.btnExitRecovery.isEnabled = !busy
            binding.btnRegister.isEnabled = !busy
            binding.tvGoToSignIn.isEnabled = !busy
            binding.pbLoading.visibility = if (busy) View.VISIBLE else View.GONE
            if (busy) binding.tvStatusMessage.setText(if (mode != null) R.string.account_operation_progress else R.string.msg_creating_account)
        }
        viewModel.result.observe(viewLifecycleOwner) { result ->
            when (result) {
                is OperationResult.Success -> {
                    binding.tvStatusMessage.text = getString(R.string.msg_account_created)
                    if (result.data.isEmpty() && !appContainer.accountSessionManager.currentSession().isAuthenticated) {
                        mainActivity.navigateToSignIn()
                    } else if (appContainer.accountSessionManager.currentSession().uid == result.data) {
                        onRegisterSuccessListener?.invoke(result.data)
                    }
                    viewModel.clearState()
                }
                is OperationResult.Invalid -> binding.tvStatusMessage.text = result.message
                is OperationResult.Denied -> binding.tvStatusMessage.text = result.message
                is OperationResult.Unavailable -> {
                    binding.tvStatusMessage.setText(R.string.profile_check_unavailable)
                    if (mode == null && appContainer.accountSessionManager.currentSession().isAuthenticated) {
                        viewModel.clearState()
                        mainActivity.resolveAccountScreen()
                    }
                }
                is OperationResult.OutcomeUnknown -> binding.tvStatusMessage.setText(R.string.profile_check_unavailable)
                null -> {}
            }
        }

        // Submit button action (Registration or Profile Repair) with field-specific validation
        binding.btnRegister.setOnClickListener {
            val origin = if (mode == null) null else mainActivity.recoverySession(this)
            if (verificationRequired || (mode != null && origin == null)) {
                mainActivity.resolveAccountScreen()
                return@setOnClickListener
            }
            val displayName = binding.etDisplayName.text.toString().trim()

            if (isProfileRepairMode && origin != null) {
                if (displayName.isBlank()) {
                    binding.etDisplayName.error = getString(R.string.err_name_required)
                    binding.tvStatusMessage.text = getString(R.string.err_name_required)
                    return@setOnClickListener
                }
                viewModel.repairProfile(displayName, origin)
            } else {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()
                val confirmPassword = binding.etConfirmPassword.text.toString()

                if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
                    listOf(binding.etEmail, binding.etPassword, binding.etDisplayName)
                        .first { it.text.isNullOrBlank() }.error = getString(R.string.err_required_field)
                    binding.tvStatusMessage.text = getString(R.string.err_required_field)
                    return@setOnClickListener
                }

                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    binding.etEmail.error = getString(R.string.err_invalid_email)
                    binding.tvStatusMessage.text = getString(R.string.err_invalid_email)
                    return@setOnClickListener
                }

                if (password.length < 6) {
                    binding.etPassword.error = getString(R.string.err_password_too_short)
                    binding.tvStatusMessage.text = getString(R.string.err_password_too_short)
                    return@setOnClickListener
                }

                if (password != confirmPassword) {
                    binding.etConfirmPassword.error = getString(R.string.err_password_mismatch)
                    binding.tvStatusMessage.text = getString(R.string.err_password_mismatch)
                    return@setOnClickListener
                }

                viewModel.registerAndCreateProfile(email, password, displayName)
            }
        }

        // Navigation back to S-01 Sign in
        binding.tvGoToSignIn.setOnClickListener {
            if (mode == "unavailable") mainActivity.openCapsulesWhileOffline() else onGoToSignInListener?.invoke()
        }
    }

    /** The host can leave the form once its account transition has finished. */
    fun submitting() = ::viewModel.isInitialized && viewModel.busy.value == true

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
