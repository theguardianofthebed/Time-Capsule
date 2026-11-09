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
        val appContainer = mainActivity.container
        val factory = ViewModelFactory(appContainer.accountSessionManager)
        viewModel = ViewModelProvider(this, factory)[S02RegisterViewModel::class.java]

        val activeUid = appContainer.authAdapter.activeUid()
        val isProfileRepairMode = !activeUid.isNullOrBlank()

        if (isProfileRepairMode) {
            binding.btnRegister.text = getString(R.string.btn_repair_profile)
            binding.etEmail.visibility = View.GONE
            binding.etPassword.visibility = View.GONE
            binding.etConfirmPassword.visibility = View.GONE
            val activeName = appContainer.authAdapter.activeDisplayName() ?: ""
            binding.etDisplayName.setText(activeName)
        } else {
            binding.btnRegister.text = getString(R.string.btn_register)
            binding.etEmail.visibility = View.VISIBLE
            binding.etPassword.visibility = View.VISIBLE
            binding.etConfirmPassword.visibility = View.VISIBLE
        }

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
            val displayName = binding.etDisplayName.text.toString().trim()

            if (isProfileRepairMode) {
                binding.btnRegister.isEnabled = false
                binding.tvStatusMessage.text = getString(R.string.msg_creating_account)
                viewModel.repairProfile(displayName)
            } else {
                val email = binding.etEmail.text.toString()
                val password = binding.etPassword.text.toString()
                val confirmPassword = binding.etConfirmPassword.text.toString()

                if (password != confirmPassword) {
                    binding.tvStatusMessage.text = getString(R.string.err_password_mismatch)
                    return@setOnClickListener
                }

                binding.btnRegister.isEnabled = false
                binding.tvStatusMessage.text = getString(R.string.msg_creating_account)
                viewModel.registerAndCreateProfile(email, password, displayName)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
