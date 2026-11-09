package com.example.timecapsule.ui.s01

import android.os.Bundle
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
 * Fragment UI implementation for S-01 Sign in screen observing [S01SignInViewModel].
 */
class S01SignInFragment : Fragment() {

    private var _binding: FragmentS01SignInBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: S01SignInViewModel

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

        viewModel.signInState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is OperationResult.Success -> {
                    binding.tvStatusMessage.text = getString(R.string.msg_sign_in_success)
                    onSignInSuccessListener?.invoke(result.data)
                    viewModel.clearState()
                }
                is OperationResult.Invalid -> {
                    binding.tvStatusMessage.text = result.message
                }
                is OperationResult.Denied -> {
                    binding.tvStatusMessage.text = result.message
                }
                is OperationResult.Unavailable -> {
                    binding.tvStatusMessage.text = result.message
                }
                is OperationResult.OutcomeUnknown -> {
                    binding.tvStatusMessage.text = result.message
                }
                null -> {}
            }
        }

        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            binding.tvStatusMessage.text = getString(R.string.msg_signing_in)
            viewModel.signIn(email, password)
        }

        binding.btnGoToRegister.setOnClickListener {
            onGoToRegisterListener?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
