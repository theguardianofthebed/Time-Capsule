package com.example.timecapsule.ui.s04

import android.os.Bundle
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import com.example.timecapsule.data.media.AndroidPhotoInput
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
import kotlinx.coroutines.flow.collectLatest

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
    private lateinit var photoInput: AndroidPhotoInput

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.selectPhoto(photoInput, uri.toString())
    }

    private val documentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.selectPhoto(photoInput, uri.toString())
    }

    /** Inflates the content form and selected-photo preview. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentS04ContentDraftBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Connects the shared draft to private media storage and lifecycle-bound rendering. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        photoInput = AndroidPhotoInput(requireContext())

        val mainActivity = requireActivity() as MainActivity
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(mainActivity.container.accountSessionManager,
                requireContext().applicationContext, mainActivity.container.repository)
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

    /** Launches one-image selection and handles editable draft actions without publishing. */
    private fun setupClickListeners() {
        binding.btnAttachPhoto.setOnClickListener {
            // Older devices use OPEN_DOCUMENT; the private copy removes future URI dependency.
            if (Build.VERSION.SDK_INT >= 33) {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                documentLauncher.launch(arrayOf("image/jpeg", "image/png"))
            }
        }

        binding.btnRemovePhoto.setOnClickListener {
            viewModel.removePhoto()
        }

        binding.btnContinue.setOnClickListener {
            if (viewModel.uiState.value.recoveryBlocked) {
                viewModel.recoverPublicationState(restoreEditable = true)
                return@setOnClickListener
            }
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

    /** Cancels obsolete previews and observes state only while this view is started. */
    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previewedPhoto: com.example.timecapsule.domain.model.RetainedOriginalPhoto? = null
                var openedRecovery = false
                viewModel.uiState.collectLatest { state ->
                    if (state.publicationPhase != null && !state.publicationBusy &&
                        !state.recoveryBusy && !openedRecovery) {
                        openedRecovery = true
                        (requireActivity() as MainActivity).navigateToConfirmSend()
                        return@collectLatest
                    }
                    binding.btnAttachPhoto.isEnabled = state.editable && !state.photoBusy
                    binding.btnRemovePhoto.isEnabled = state.editable && !state.photoBusy
                    binding.btnDiscardDraft.isEnabled = state.editable && !state.photoBusy
                    binding.btnContinue.isEnabled = !state.photoBusy && !state.recoveryBusy
                    binding.btnContinue.setText(if (state.recoveryBlocked)
                        R.string.publication_recover else R.string.s04_btn_continue)
                    binding.etCapsuleTitle.isEnabled = state.editable && !state.photoBusy
                    binding.etCapsuleBody.isEnabled = state.editable && !state.photoBusy
                    val error = state.photoError ?: if (state.recoveryBlocked)
                        R.string.publication_recovery_failure else null
                    binding.tvS04Error.text = error?.let { getString(it) }
                    binding.tvS04Error.visibility = if (error == null) View.GONE else View.VISIBLE
                    val photo = state.originalPhoto
                    if (photo != previewedPhoto) {
                        val preview = if (photo != null) photoInput.preview(photo) else null
                        binding.ivPhotoPreview.setImageBitmap(preview)
                        previewedPhoto = photo
                    }
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

    /** Releases view references; draft work remains owned by the activity ViewModel. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
