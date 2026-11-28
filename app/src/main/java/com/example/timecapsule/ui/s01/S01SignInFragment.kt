package com.example.timecapsule.ui.s01

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
import com.example.timecapsule.databinding.FragmentS01SignInBinding
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.ui.ViewModelFactory

/**
 * Fragment implementation for S-01 Sign in screen matching the Stitch UI design.
 * Collects user credentials, handles password visibility toggling with dynamic accessibility
 * content descriptions, and authenticates via [S01SignInViewModel] while displaying detailed,
 * field-specific error and loading states.
 */
class S01SignInFragment : Fragment() {

    private var _binding: FragmentS01SignInBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: S01SignInViewModel
    private var isPasswordVisible = false

    var onGoToRegisterListener: (() -> Unit)? = null
    var onSignInSuccessListener: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentS01SignInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = requireActivity() as MainActivity
        val factory = ViewModelFactory(mainActivity.container.accountSessionManager)
        viewModel = ViewModelProvider(this, factory)[S01SignInViewModel::class.java]

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

        // Clear status message when user modifies credentials
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvStatusMessage.text = ""
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etEmail.addTextChangedListener(textWatcher)
        binding.etPassword.addTextChangedListener(textWatcher)

        // Observe ViewModel sign-in state mutations
        viewModel.signInState.observe(viewLifecycleOwner) { result ->
            binding.btnSignIn.isEnabled = true
            binding.pbLoading.visibility = View.GONE
            when (result) {
                is OperationResult.Success -> {
                    binding.tvStatusMessage.text = getString(R.string.msg_sign_in_success)
                    onSignInSuccessListener?.invoke(result.data)
                    viewModel.clearState()
                }
                is OperationResult.Invalid -> binding.tvStatusMessage.text = result.message
                is OperationResult.Denied -> binding.tvStatusMessage.text = result.message
                is OperationResult.Unavailable -> binding.tvStatusMessage.text = result.message
                is OperationResult.OutcomeUnknown -> binding.tvStatusMessage.text = result.message
                null -> {}
            }
        }

        // Sign in CTA execution with field-specific validation
        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                binding.tvStatusMessage.text = getString(R.string.err_email_password_required)
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tvStatusMessage.text = getString(R.string.err_invalid_email)
                return@setOnClickListener
            }

            binding.btnSignIn.isEnabled = false
            binding.pbLoading.visibility = View.VISIBLE
            binding.tvStatusMessage.text = getString(R.string.msg_signing_in)
            viewModel.signIn(email, password)
        }

        // Navigate to S-02 Register screen
        binding.btnGoToRegister.setOnClickListener {
            onGoToRegisterListener?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
