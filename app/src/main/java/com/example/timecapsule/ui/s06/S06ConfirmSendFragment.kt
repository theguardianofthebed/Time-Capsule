package com.example.timecapsule.ui.s06

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
import com.example.timecapsule.databinding.FragmentS06ConfirmSendBinding
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.ui.ViewModelFactory
import com.example.timecapsule.ui.draft.CapsuleDraftViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * S-06 Confirm Send / Review ScreenFragment using native Views and ViewBinding.
 * Displays final summary of draft title, recipient identity, lock conditions, and content.
 * Handles Back navigation to edit draft and honestly represents downstream Issue #14 publication.
 */
class S06ConfirmSendFragment : Fragment() {

    private var _binding: FragmentS06ConfirmSendBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CapsuleDraftViewModel

    var onBackToRecipientLockListener: (() -> Unit)? = null
    var onNavigateToMomentsListener: (() -> Unit)? = null
    var onNavigateToAccountListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentS06ConfirmSendBinding.inflate(inflater, container, false)
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

        setupClickListeners()
        observeViewModelState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncSessionState()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackToRecipientLockListener?.invoke()
        }

        binding.btnEditDraft.setOnClickListener {
            onBackToRecipientLockListener?.invoke()
        }

        binding.btnSendCapsule.setOnClickListener {
            viewModel.triggerSendNotice()
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

                    // Content Summary
                    val hasText = state.text.isNotBlank()
                    val hasPhoto = !state.localPhotoUri.isNullOrBlank()
                    val contentSummary = when {
                        hasText && hasPhoto -> "Message text + 1 photo attached"
                        hasText -> "Message text"
                        hasPhoto -> "1 photo attached"
                        else -> "No content"
                    }
                    binding.tvReviewContentInfo.text = contentSummary

                    // Unlock Time
                    if (state.unlockAt != null) {
                        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy · HH:mm z")
                            .withZone(ZoneId.systemDefault())
                        binding.tvReviewUnlockAt.text = "Meant to open: ${formatter.format(state.unlockAt)}"
                    } else {
                        binding.tvReviewUnlockAt.text = "Meant to open: Instant unlock"
                    }

                    // Location Condition
                    if (state.lockMode == LockMode.LOCATION || state.lockMode == LockMode.TIME_AND_LOCATION) {
                        val placeName = state.locationName ?: "Selected location"
                        val radius = state.locationTarget?.radiusMeters?.toInt() ?: 150
                        binding.tvReviewLocation.text = "Location: $placeName · $radius m radius"
                        binding.tvReviewLocation.visibility = View.VISIBLE
                    } else {
                        binding.tvReviewLocation.visibility = View.GONE
                    }

                    // Notice Banner for Downstream Issue #14 Send Notice
                    if (!state.sendNotice.isNullOrBlank()) {
                        binding.tvSendNoticeBanner.text = state.sendNotice
                        binding.tvSendNoticeBanner.visibility = View.VISIBLE
                    } else {
                        binding.tvSendNoticeBanner.visibility = View.GONE
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
