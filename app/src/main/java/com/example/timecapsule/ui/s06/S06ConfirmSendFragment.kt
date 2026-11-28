package com.example.timecapsule.ui.s06

import com.example.timecapsule.domain.session.SessionSnapshot
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.databinding.FragmentS06ConfirmSendBinding
import com.example.timecapsule.ui.ViewModelFactory
import com.example.timecapsule.ui.draft.CapsuleDraftViewModel
import kotlinx.coroutines.launch
import com.example.timecapsule.ui.s03.CapsuleTab

/**
 * S-06 Confirm Send / Review ScreenFragment using native Views and ViewBinding.
 * Displays final summary of draft title, recipient identity, lock conditions, and content.
 * Observes the shared submission state; only the coordinator can confirm SENT or safe abandonment.
 */
class S06ConfirmSendFragment : Fragment() {

    companion object {
        private const val ARG_CAPSULE_ID = "arg_capsule_id"

        /** Keep the selected operation and its originating account together across recreation. */
        fun newInstance(capsuleId: String? = null, origin: SessionSnapshot? = null): S06ConfirmSendFragment {
            return S06ConfirmSendFragment().apply {
                arguments = Bundle().apply {
                    capsuleId?.let {
                        putString(ARG_CAPSULE_ID, it)
                        putString("originUid", origin?.uid)
                        putLong("originGeneration", origin?.sessionGeneration ?: -1)
                    }
                }
            }
        }
    }

    private var _binding: FragmentS06ConfirmSendBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CapsuleDraftViewModel

    var onBackToRecipientLockListener: (() -> Unit)? = null
    var onNavigateToMomentsListener: (() -> Unit)? = null
    var onNavigateToAccountListener: (() -> Unit)? = null

    /** Inflates review and publication controls for this view lifetime. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentS06ConfirmSendBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var activeTargetCapsuleId: String? = null

    /** Obtains the activity-scoped draft so rotations do not restart publication. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = requireActivity() as MainActivity
        activeTargetCapsuleId = arguments?.getString(ARG_CAPSULE_ID)
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(mainActivity.container.accountSessionManager,
                requireContext().applicationContext, mainActivity.container.repository,
                mainActivity.container.recoveryCoordinator, restorePhotoDraft = activeTargetCapsuleId == null)
        )[CapsuleDraftViewModel::class.java]

        if (activeTargetCapsuleId != null) {
            checkPublication()
        } else {
            viewModel.syncSessionState(mainActivity.container.repository, mainActivity.container.recoveryCoordinator)
        }

        setupClickListeners()
        observeViewModelState()
    }

    /** Rechecks saved outcomes on foreground return without automatically publishing again. */
    override fun onResume() {
        super.onResume()
        val mainActivity = activity as? MainActivity
        if (activeTargetCapsuleId != null) {
            checkPublication()
        } else if (mainActivity != null) {
            viewModel.syncSessionState(mainActivity.container.repository, mainActivity.container.recoveryCoordinator)
        } else {
            viewModel.syncSessionState()
        }
    }

    /** Rotation and foreground checks keep the account identity of the original list selection. */
    private fun checkPublication() {
        val id = activeTargetCapsuleId
        if (id == null) viewModel.recoverPublicationState()
        else viewModel.recoverPublicationState(id, origin = SessionSnapshot(
            arguments?.getString("originUid"), arguments?.getLong("originGeneration", -1L) ?: -1L))
    }

    /** Only an editable draft can return to recipient and lock editing. */
    fun canEditDraft() = ::viewModel.isInitialized && activeTargetCapsuleId == null && viewModel.uiState.value.editable

    /** Routes user actions through guarded ViewModel operations instead of Firebase calls. */
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackToRecipientLockListener?.invoke()
        }

        binding.btnEditDraft.setOnClickListener {
            onBackToRecipientLockListener?.invoke()
        }

        binding.btnSendCapsule.setOnClickListener {
            when (viewModel.uiState.value.publicationPhase) {
                PublicationPhase.SENT -> {
                    val activity = requireActivity() as MainActivity
                    activity.rememberTab(CapsuleTab.SENT)
                    activity.navigateToCapsuleList()
                }
                null -> if (activeTargetCapsuleId == null) viewModel.sendCapsule() else Unit
                else -> viewModel.retryPublication(activeTargetCapsuleId)
            }
        }
        binding.btnCheckPublication.setOnClickListener {
            if (viewModel.uiState.value.publicationPhase == PublicationPhase.SENT) {
                viewModel.startNewAfterSent(activeTargetCapsuleId)
                if (viewModel.uiState.value.editable) (requireActivity() as MainActivity).navigateToContentDraft()
            } else checkPublication()
        }
        binding.btnReplacePublication.setOnClickListener { viewModel.replaceFailedPublication(activeTargetCapsuleId) }

        binding.btnNavMoments.setOnClickListener {
            onNavigateToMomentsListener?.invoke()
        }

        binding.btnNavAccount.setOnClickListener {
            onNavigateToAccountListener?.invoke()
        }
    }

    /** Displays frozen results and hides raw sent content while disabling unsafe editing. */
    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (activeTargetCapsuleId != null && state.editable &&
                        state.publicationMessage == R.string.publication_replacement_ready) {
                        (requireActivity() as MainActivity).navigateToContentDraft()
                        return@collect
                    }
                    val busy = state.publicationBusy || state.recoveryBusy || state.photoBusy
                    val sent = state.publicationPhase == PublicationPhase.SENT
                    binding.layoutSummaryCard.visibility =
                        if (state.statusOnly || sent || state.recoveryBusy || state.recoveryBlocked) View.GONE else View.VISIBLE
                    val canSend = activeTargetCapsuleId == null && state.editable
                    val primary = when {
                        state.recoveryBlocked -> null
                        sent -> R.string.view_sent
                        state.canRetryUnchanged && !busy -> R.string.publication_retry
                        canSend -> R.string.s06_btn_send
                        else -> null
                    }
                    binding.btnSendCapsule.visibility = if (primary == null) View.GONE else View.VISIBLE
                    primary?.let(binding.btnSendCapsule::setText)
                    binding.btnSendCapsule.isEnabled = primary != null && !busy
                    binding.btnCheckPublication.visibility =
                        if (state.publicationPhase != null || state.recoveryBlocked) View.VISIBLE else View.GONE
                    binding.btnCheckPublication.isEnabled = !busy
                    binding.btnCheckPublication.setText(when {
                        state.recoveryBlocked -> R.string.publication_recover
                        sent -> R.string.publication_new
                        else -> R.string.publication_check
                    })
                    binding.btnReplacePublication.visibility = if (state.canAbandon && !state.recoveryBlocked) View.VISIBLE else View.GONE
                    binding.btnReplacePublication.isEnabled = !busy
                    binding.btnEditDraft.isEnabled = canSend && !busy
                    binding.btnBack.isEnabled = true
                    // Title
                    if (state.title.isNotBlank()) {
                        binding.tvReviewTitle.text = state.title
                    } else {
                        binding.tvReviewTitle.text = "(No optional title set)"
                    }

                    // Recipient
                    val recipientName = state.resolvedProfile?.takeIf { state.isRecipientConfirmed }?.displayName?.takeIf(String::isNotBlank) ?: getString(R.string.recipient_unconfirmed)
                    val recipientUid = state.resolvedProfile?.uid ?: state.recipientUidInput
                    binding.tvReviewRecipientName.text = recipientName
                    binding.tvReviewRecipientUid.text = recipientUid.takeIf(String::isNotBlank)?.let { "UID: $it" } ?: "UID unavailable"

                    // Message Text & Photo Content
                    if (state.text.isNotBlank()) {
                        binding.tvReviewMessageBody.text = state.text
                    } else {
                        binding.tvReviewMessageBody.text = "(No message body entered)"
                    }

                    if (!state.localPhotoUri.isNullOrBlank()) {
                        binding.tvReviewPhotoInfo.text = "Attached photo: ${state.photoName ?: "photo"}"
                        binding.tvReviewPhotoInfo.visibility = View.VISIBLE
                    } else {
                        binding.tvReviewPhotoInfo.text = ""
                        binding.tvReviewPhotoInfo.visibility = View.GONE
                    }

                    binding.tvReviewUnlockAt.text = reviewCondition(state)
                    binding.tvReviewLocation.visibility = View.GONE

                    val notice = if (state.recoveryBusy) R.string.publication_recovering
                        else state.publicationMessage
                    if (notice != null) {
                        binding.tvSendNoticeBanner.text = state.publicationFailure?.guidance ?: getString(notice)
                        binding.tvSendNoticeBanner.visibility = View.VISIBLE
                    } else {
                        binding.tvSendNoticeBanner.visibility = View.GONE
                    }
                }
            }
        }
    }

    /** Releases bindings; the activity-scoped send continues independently of this view. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
