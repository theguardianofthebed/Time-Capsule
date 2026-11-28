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
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.time.TimeConversionHelper
import com.example.timecapsule.ui.ViewModelFactory
import com.example.timecapsule.ui.draft.CapsuleDraftViewModel
import java.time.ZoneId
import kotlinx.coroutines.launch

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
                    viewModel.startNewAfterSent(activeTargetCapsuleId)
                    if (viewModel.uiState.value.editable) {
                        (requireActivity() as MainActivity).navigateToContentDraft()
                    }
                }
                null -> if (activeTargetCapsuleId == null) viewModel.sendCapsule() else Unit
                else -> viewModel.retryPublication(activeTargetCapsuleId)
            }
        }
        binding.btnCheckPublication.setOnClickListener { checkPublication() }
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
                    binding.btnSendCapsule.isEnabled = !busy && !state.recoveryBlocked
                    binding.btnSendCapsule.setText(when {
                        sent -> R.string.publication_new
                        state.publicationPhase != null -> R.string.publication_retry
                        else -> R.string.s06_btn_send
                    })
                    binding.btnCheckPublication.visibility =
                        if (state.publicationPhase != null || state.recoveryBlocked) View.VISIBLE else View.GONE
                    binding.btnCheckPublication.isEnabled = !busy
                    binding.btnCheckPublication.setText(if (state.recoveryBlocked)
                        R.string.publication_recover else R.string.publication_check)
                    binding.btnReplacePublication.visibility =
                        if (state.canAbandon) View.VISIBLE else View.GONE
                    binding.btnReplacePublication.isEnabled = !busy && !state.recoveryBlocked
                    binding.btnEditDraft.isEnabled = state.editable && !busy
                    binding.btnBack.isEnabled = state.editable && !busy
                    // Title
                    if (state.title.isNotBlank()) {
                        binding.tvReviewTitle.text = state.title
                    } else {
                        binding.tvReviewTitle.text = "(No optional title set)"
                    }

                    // Recipient
                    val recipientName = state.resolvedProfile?.displayName ?: "Me"
                    val recipientUid = state.resolvedProfile?.uid ?: state.recipientUidInput
                    binding.tvReviewRecipientName.text = recipientName
                    binding.tvReviewRecipientUid.text = "UID: $recipientUid"

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

                    // Unlock Condition Presentation
                    val timeFormatted = if (state.unlockAt != null) {
                        val zoneId = try { ZoneId.of(state.creationZone) } catch (_: Exception) { ZoneId.systemDefault() }
                        TimeConversionHelper.formatZonedDateTime(state.unlockAt, zoneId)
                    } else null

                    val placeFormatted = if (state.lockMode == LockMode.LOCATION || state.lockMode == LockMode.TIME_AND_LOCATION) {
                        val placeName = state.locationName ?: "Selected location"
                        val radius = state.locationTarget?.radiusMeters?.toInt() ?: 150
                        "$placeName ($radius m radius)"
                    } else null

                    when (state.lockMode) {
                        LockMode.TIME -> {
                            binding.tvReviewUnlockAt.text = if (timeFormatted != null) {
                                "Meant to open: $timeFormatted"
                            } else {
                                "Meant to open: Specified future time"
                            }
                            binding.tvReviewLocation.visibility = View.GONE
                        }
                        LockMode.LOCATION -> {
                            binding.tvReviewUnlockAt.text = if (placeFormatted != null) {
                                "Meant to open: At $placeFormatted"
                            } else {
                                "Meant to open: At target location"
                            }
                            binding.tvReviewLocation.visibility = View.GONE
                        }
                        LockMode.TIME_AND_LOCATION -> {
                            val timeText = timeFormatted ?: "Specified future time"
                            val placeText = placeFormatted ?: "Target location"
                            binding.tvReviewUnlockAt.text = "Meant to open: $timeText AND at $placeText"
                            binding.tvReviewLocation.visibility = View.GONE
                        }
                    }

                    val notice = if (state.recoveryBusy) R.string.publication_recovering
                        else state.publicationMessage
                    if (notice != null) {
                        binding.tvSendNoticeBanner.setText(notice)
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
