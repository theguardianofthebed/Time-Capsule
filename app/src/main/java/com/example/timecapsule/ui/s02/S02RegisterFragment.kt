package com.example.timecapsule.ui.s02

import android.os.Bundle
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
 * Fragment UI implementation for S-02 Register & Profile Repair screen observing [S02RegisterViewModel].
 */
class S02RegisterFragment : Fragment() {

    private var _binding: FragmentS02RegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: S02RegisterViewModel

    var onRegisterSuccessListener: ((String) -> Unit)? = null

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
        val factory = ViewModelFactory(mainActivity.container.accountSessionManager)
        viewModel = ViewModelProvider(this, factory)[S02RegisterViewModel::class.java]

        viewModel.registerState.observe(viewLifecycleOwner) { result ->
            binding.btnRegister.isEnabled = true
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

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            val displayName = binding.etDisplayName.text.toString()

            binding.btnRegister.isEnabled = false
            binding.tvStatusMessage.text = getString(R.string.msg_creating_account)
            viewModel.registerAndCreateProfile(email, password, displayName)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
