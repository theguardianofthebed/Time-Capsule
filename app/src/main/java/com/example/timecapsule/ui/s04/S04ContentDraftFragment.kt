package com.example.timecapsule.ui.s04

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.databinding.FragmentS04ContentDraftBinding
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.ui.ViewModelFactory
import com.example.timecapsule.ui.draft.CapsuleDraftViewModel
import kotlinx.coroutines.launch

/**
 * S-04 Content Creation ScreenFragment using native Android Views and ViewBinding.
 * Allows drafting message text, optional title, and attaching one photo (max 5 MiB).
 * Enforces text or photo content validation before proceeding to S-05 Recipient & Lock selection.
 */
class S04ContentDraftFragment : Fragment() {

    private var _binding: FragmentS04ContentDraftBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CapsuleDraftViewModel

    var onNextToRecipientLockListener: (() -> Unit)? = null
    var onNavigateToMomentsListener: (() -> Unit)? = null
    var onNavigateToAccountListener: (() -> Unit)? = null

    private var isSyncingText = false

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val contentResolver = requireContext().contentResolver
            val size = try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            } catch (e: Exception) {
                0L
            }
            val fileName = uri.lastPathSegment ?: "photo.jpg"
            val result = viewModel.attachPhoto(uri.toString(), fileName, size)
            if (result is OperationResult.Invalid) {
                binding.tvS04Error.text = result.message
                binding.tvS04Error.visibility = View.VISIBLE
            } else {
                binding.tvS04Error.visibility = View.GONE
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentS04ContentDraftBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = requireActivity() as MainActivity
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(mainActivity.container.accountSessionManager)
        )[CapsuleDraftViewModel::class.java]

        viewModel.syncSessionState()

        setupTextWatchers()
        setupClickListeners()
        observeViewModelState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncSessionState()
    }

    private fun setupTextWatchers() {
        binding.etCapsuleTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isSyncingText) {
                    viewModel.updateTitle(s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etCapsuleBody.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isSyncingText) {
                    viewModel.updateText(s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupClickListeners() {
        binding.btnAttachPhoto.setOnClickListener {
            binding.tvS04Error.text = getString(R.string.s04_photo_notice)
            binding.tvS04Error.visibility = View.VISIBLE
        }

        binding.btnRemovePhoto.setOnClickListener {
            viewModel.removePhoto()
        }

        binding.btnContinue.setOnClickListener {
            val validation = viewModel.validateS04Content()
            if (validation is OperationResult.Success) {
                binding.tvS04Error.visibility = View.GONE
                onNextToRecipientLockListener?.invoke()
            } else if (validation is OperationResult.Invalid) {
                binding.tvS04Error.text = validation.message
                binding.tvS04Error.visibility = View.VISIBLE
            }
        }

        binding.btnDiscardDraft.setOnClickListener {
            viewModel.discardDraft()
            binding.tvS04Error.visibility = View.GONE
        }

        binding.btnNavMoments.setOnClickListener {
            onNavigateToMomentsListener?.invoke()
        }

        binding.btnNavAccount.setOnClickListener {
            onNavigateToAccountListener?.invoke()
        }
    }

    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    isSyncingText = true
                    if (binding.etCapsuleTitle.text.toString() != state.title) {
                        binding.etCapsuleTitle.setText(state.title)
                    }
                    if (binding.etCapsuleBody.text.toString() != state.text) {
                        binding.etCapsuleBody.setText(state.text)
                    }
                    isSyncingText = false

                    if (!state.localPhotoUri.isNullOrBlank()) {
                        binding.layoutPhotoAttached.visibility = View.VISIBLE
                        binding.tvPhotoName.text = state.photoName ?: "Photo attached"
                        val sizeMb = (state.photoSizeBytes ?: 0L) / (1024.0 * 1024.0)
                        binding.tvPhotoSize.text = String.format("%.2f MiB", sizeMb)
                    } else {
                        binding.layoutPhotoAttached.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
