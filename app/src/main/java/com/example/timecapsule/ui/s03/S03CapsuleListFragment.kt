package com.example.timecapsule.ui.s03

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnTabReceived.setOnClickListener { viewModel.selectTab(CapsuleTab.RECEIVED) }
        binding.btnTabSent.setOnClickListener { viewModel.selectTab(CapsuleTab.SENT) }
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

    private fun renderUiState(state: CapsuleListUiState) {
        binding.pbLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        // Tab selection styling
        if (state.selectedTab == CapsuleTab.RECEIVED) {
            binding.btnTabReceived.setTextColor(ContextCompat.getColor(requireContext(), R.color.capsule_primary))
            binding.btnTabSent.setTextColor(ContextCompat.getColor(requireContext(), R.color.capsule_text_muted))
        } else {
            binding.btnTabReceived.setTextColor(ContextCompat.getColor(requireContext(), R.color.capsule_text_muted))
            binding.btnTabSent.setTextColor(ContextCompat.getColor(requireContext(), R.color.capsule_primary))
        }

        // Stale & error banners
        if (state.isStale) {
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
        if (state.selectedTab == CapsuleTab.SENT && state.unresolvedOperations.isNotEmpty()) {
            renderUnresolvedOperations(container, state.unresolvedOperations)
        }

        if (currentList.isEmpty() && state.unresolvedOperations.isEmpty() && !state.isLoading) {
            binding.tvEmptyList.text = if (state.selectedTab == CapsuleTab.RECEIVED)
                getString(R.string.s03_empty_received)
            else
                getString(R.string.s03_empty_sent)
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

    private fun renderUnresolvedOperations(
        container: LinearLayout,
        unresolved: List<RecoveredPublication>
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
                PublicationPhase.OUTCOME_UNKNOWN -> "Result unconfirmed — check result or retry"
                PublicationPhase.FAILED -> "Send failed — action required"
                PublicationPhase.ABANDONED -> "Send abandoned"
                PublicationPhase.SENT -> "Sent"
            }

            val statusView = TextView(context).apply {
                text = statusText
                setTextColor(ContextCompat.getColor(context, R.color.capsule_text_muted))
                textSize = 12f
                setPadding(0, 4, 0, 8)
            }
            card.addView(statusView)

            val btnAction = Button(context).apply {
                text = if (pub.phase == PublicationPhase.OUTCOME_UNKNOWN) "Check / Retry" else "Open Confirm Send (S-06)"
                setOnClickListener {
                    (requireActivity() as MainActivity).navigateToConfirmSend()
                }
            }
            card.addView(btnAction)

            container.addView(card)
        }
    }

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

        itemBinding.tvItemStatusBadge.text = if (isOpened) getString(R.string.s03_status_opened) else getString(R.string.s03_status_sealed)
        itemBinding.tvItemStatusBadge.setBackgroundResource(
            if (isOpened) R.color.capsule_success_bg else R.color.capsule_badge_bg
        )

        val lockConditionText = when (summary.lock.mode) {
            LockMode.TIME -> "Time lock"
            LockMode.LOCATION -> "Place lock"
            LockMode.TIME_AND_LOCATION -> "Time + place"
        }
        itemBinding.tvItemConditionText.text = lockConditionText

        itemBinding.itemCapsuleRoot.setOnClickListener {
            onOpenCapsuleDetailListener?.invoke(summary.capsuleId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
