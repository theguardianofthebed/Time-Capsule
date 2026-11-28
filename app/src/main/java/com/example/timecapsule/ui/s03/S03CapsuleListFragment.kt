package com.example.timecapsule.ui.s03

import com.example.timecapsule.domain.model.ListProvenance
import com.example.timecapsule.domain.session.SessionSnapshot
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.databinding.FragmentS03CapsuleListBinding
import com.example.timecapsule.databinding.ItemCapsuleSummaryBinding
import com.example.timecapsule.domain.model.Availability
import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.model.PublicationPhase
import com.example.timecapsule.domain.model.RecoveredPublication
import com.example.timecapsule.ui.ViewModelFactory
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.graphics.Typeface

/**
 * Fragment for S-03 Capsule List screen.
 * Displays received and sent capsule summaries with tab filtering and navigation.
 */
class S03CapsuleListFragment : Fragment() {

    private var _binding: FragmentS03CapsuleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: S03CapsuleListViewModel by viewModels {
        val activity = requireActivity() as MainActivity
        ViewModelFactory(
            accountSessionManager = activity.container.accountSessionManager,
            repository = activity.container.repository,
            recoveryCoordinator = activity.container.recoveryCoordinator
        )
    }

    fun refreshScreen() {
        if (_binding != null) {
            viewModel.loadLists(explicitRefresh = false)
        }
    }

    var onOpenCapsuleDetailListener: ((String) -> Unit)? = null
    var onCreateCapsuleListener: (() -> Unit)? = null
    var onAccountSettingsListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentS03CapsuleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Restore list direction and subscribe to this view's account-scoped rows. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val host = requireActivity() as MainActivity
        binding.btnTabReceived.setOnClickListener {
            host.rememberTab(CapsuleTab.RECEIVED)
            viewModel.selectTab(CapsuleTab.RECEIVED)
        }
        if ((savedInstanceState ?: arguments)?.getBoolean("showSent") == true) viewModel.selectTab(CapsuleTab.SENT)
        binding.btnTabSent.setOnClickListener {
            host.rememberTab(CapsuleTab.SENT)
            viewModel.selectTab(CapsuleTab.SENT)
        }
        binding.btnHeaderRefresh.setOnClickListener { viewModel.loadLists(explicitRefresh = true) }
        binding.btnHeaderAccount.setOnClickListener { onAccountSettingsListener?.invoke() }

        binding.btnNavMoments.setOnClickListener { /* Already on Moments */ }
        binding.btnNavCreate.setOnClickListener { onCreateCapsuleListener?.invoke() }
        binding.btnNavAccount.setOnClickListener { onAccountSettingsListener?.invoke() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    /** Render the selected query independently from pending operations in Sent. */
    private fun renderUiState(state: CapsuleListUiState) {
        binding.pbLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        binding.btnTabReceived.isSelected = state.selectedTab == CapsuleTab.RECEIVED
        binding.btnTabSent.isSelected = state.selectedTab == CapsuleTab.SENT
        binding.btnTabReceived.setTypeface(null, if (binding.btnTabReceived.isSelected) Typeface.BOLD else Typeface.NORMAL)
        binding.btnTabSent.setTypeface(null, if (binding.btnTabSent.isSelected) Typeface.BOLD else Typeface.NORMAL)
        // Tab selection styling
        if (state.selectedTab == CapsuleTab.RECEIVED) {
            binding.btnTabReceived.setTextColor(ContextCompat.getColor(requireContext(), R.color.capsule_primary))
            binding.btnTabSent.setTextColor(ContextCompat.getColor(requireContext(), R.color.capsule_text_body))
        } else {
            binding.btnTabReceived.setTextColor(ContextCompat.getColor(requireContext(), R.color.capsule_text_body))
            binding.btnTabSent.setTextColor(ContextCompat.getColor(requireContext(), R.color.capsule_primary))
        }

        // Stale & error banners
        if (state.isStale && state.selectedProvenance == ListProvenance.OFFLINE_RETAINED) {
            binding.tvStaleBanner.visibility = View.VISIBLE
        } else {
            binding.tvStaleBanner.visibility = View.GONE
        }

        if (!state.errorMessage.isNullOrBlank()) {
            binding.tvErrorMessage.text = state.errorMessage
            binding.tvErrorMessage.visibility = View.VISIBLE
        } else {
            binding.tvErrorMessage.visibility = View.GONE
        }

        // Render current list
        val currentList = if (state.selectedTab == CapsuleTab.RECEIVED) state.receivedList else state.sentList
        val container = binding.llCapsuleListContainer
        container.removeAllViews()

        // Render unresolved operations if on SENT tab
        val hasVisibleOperations = state.selectedTab == CapsuleTab.SENT && state.unresolvedOperations.isNotEmpty()
        if (hasVisibleOperations) {
            renderUnresolvedOperations(container, state.unresolvedOperations, state.sessionSnapshot)
        }

        if (currentList.isEmpty() && !hasVisibleOperations && !state.isLoading) {
            binding.tvEmptyList.text = if (state.isStale) {
                state.errorMessage ?: "Unable to connect to network for this list. Tap refresh to try again."
            } else if (state.selectedTab == CapsuleTab.RECEIVED) {
                getString(R.string.s03_empty_received)
            } else {
                getString(R.string.s03_empty_sent)
            }
            binding.tvEmptyList.visibility = View.VISIBLE
            container.addView(binding.tvEmptyList)
        } else {
            binding.tvEmptyList.visibility = View.GONE
            val inflater = LayoutInflater.from(requireContext())
            for (summary in currentList) {
                val itemBinding = ItemCapsuleSummaryBinding.inflate(inflater, container, false)
                bindCapsuleItem(itemBinding, summary, state.selectedTab)
                container.addView(itemBinding.root)
            }
        }
    }

    /** Bind each recovery button to the same generation that supplied its row. */
    private fun renderUnresolvedOperations(
        container: LinearLayout,
        unresolved: List<UnresolvedPublicationSummary>,
        origin: SessionSnapshot?
    ) {
        val context = requireContext()
        val sectionHeader = TextView(context).apply {
            text = "Pending & Unresolved Publications"
            setTextColor(ContextCompat.getColor(context, R.color.capsule_primary))
            textSize = 13f
            setPadding(0, 16, 0, 8)
        }
        container.addView(sectionHeader)

        for (pub in unresolved) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundResource(R.drawable.bg_card_surface)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
                layoutParams = params
            }

            val titleView = TextView(context).apply {
                text = "Capsule ID: ${pub.capsuleId}"
                setTextColor(ContextCompat.getColor(context, R.color.capsule_text_dark))
                textSize = 14f
            }
            card.addView(titleView)

            val statusText = when (pub.phase) {
                PublicationPhase.PREPARED, PublicationPhase.UPLOADING, PublicationPhase.PUBLISHING -> "Sending in progress..."
                PublicationPhase.OUTCOME_UNKNOWN -> "Result unconfirmed — check original send"
                PublicationPhase.FAILED -> "Send failed — action required"
                PublicationPhase.ABANDONED -> "Send abandoned"
                PublicationPhase.SENT -> "Sent"
            }

            val statusView = TextView(context).apply {
                text = statusText
                setTextColor(ContextCompat.getColor(context, R.color.capsule_text_body))
                textSize = 12f
                setPadding(0, 4, 0, 8)
            }
            card.addView(statusView)

            val btnAction = Button(context).apply {
                text = if (pub.phase == PublicationPhase.OUTCOME_UNKNOWN) "Check result" else "Open Confirm Send (S-06)"
                setOnClickListener {
                    (requireActivity() as MainActivity).navigateToConfirmSend(pub.capsuleId, origin)
                }
            }
            card.addView(btnAction)

            container.addView(card)
        }
    }

    /** Present available summary metadata, including older records with unknown locks. */
    private fun bindCapsuleItem(
        itemBinding: ItemCapsuleSummaryBinding,
        summary: CapsuleSummary,
        tab: CapsuleTab
    ) {
        val isOpened = (summary.availability == Availability.AVAILABLE)

        itemBinding.ivItemLockIcon.setImageResource(
            if (isOpened) R.drawable.ic_hero_envelope else R.drawable.ic_lock
        )

        val recipientName = summary.recipientDisplayName?.takeIf { it.isNotBlank() } ?: summary.recipientUid
        val senderName = summary.senderDisplayName?.takeIf { it.isNotBlank() } ?: summary.senderUid

        val titleText = when {
            !summary.title.isNullOrBlank() -> summary.title
            tab == CapsuleTab.SENT -> "To $recipientName"
            else -> "From $senderName"
        }
        itemBinding.tvItemTitle.text = titleText

        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault())
        val dateStr = summary.createdAt?.let { formatter.format(it) } ?: "A moment, kept"
        itemBinding.tvItemSubtitle.text = dateStr

        itemBinding.tvItemStatusBadge.text = when {
            tab == CapsuleTab.SENT && summary.senderUid != summary.recipientUid -> "Sender copy"
            isOpened -> getString(R.string.s03_status_opened)
            summary.availability == Availability.UNKNOWN -> "Verification required"
            else -> "Locked · Check conditions"
        }
        itemBinding.tvItemStatusBadge.setBackgroundResource(
            if (isOpened) R.color.capsule_success_bg else R.color.capsule_badge_bg
        )

        val lockConditionText = when (summary.lock?.mode) {
            null -> "Lock details unavailable"
            LockMode.TIME -> "Time lock"
            LockMode.LOCATION -> "Place lock"
            LockMode.TIME_AND_LOCATION -> "Time + place"
        }
        itemBinding.tvItemConditionText.text = lockConditionText

        itemBinding.itemCapsuleRoot.setOnClickListener {
            onOpenCapsuleDetailListener?.invoke(summary.capsuleId)
        }
    }

    /** Rotation preserves the user's latest tab, even when the original entry requested another tab. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("showSent", viewModel.uiState.value.selectedTab == CapsuleTab.SENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
