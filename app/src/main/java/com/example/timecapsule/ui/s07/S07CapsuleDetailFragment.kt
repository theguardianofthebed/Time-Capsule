package com.example.timecapsule.ui.s07

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.databinding.FragmentS07CapsuleDetailBinding
import com.example.timecapsule.ui.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fragment for S-07 Capsule Detail screen.
 * Displays locked details, unlocked letter content, photo, and author copy variants.
 */
class S07CapsuleDetailFragment : Fragment() {

    companion object {
        private const val ARG_CAPSULE_ID = "arg_capsule_id"

        fun newInstance(capsuleId: String): S07CapsuleDetailFragment {
            return S07CapsuleDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CAPSULE_ID, capsuleId)
                }
            }
        }
    }

    private var _binding: FragmentS07CapsuleDetailBinding? = null
    private val binding get() = _binding!!

    private var imageRenderJob: Job? = null
    private var imageRenderToken: Long = 0L
    private var activeRenderCapsuleId: String? = null

    private val viewModel: S07CapsuleDetailViewModel by viewModels {
        val activity = requireActivity() as MainActivity
        ViewModelFactory(
            accountSessionManager = activity.container.accountSessionManager,
            repository = activity.container.repository,
            recoveryCoordinator = activity.container.recoveryCoordinator
        )
    }

    var onBackListener: (() -> Unit)? = null
    var onNavigateToMomentsListener: (() -> Unit)? = null
    var onCreateCapsuleListener: (() -> Unit)? = null
    var onAccountSettingsListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentS07CapsuleDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnS07Back.setOnClickListener { onBackListener?.invoke() }
        binding.btnS07NavMoments.setOnClickListener { onNavigateToMomentsListener?.invoke() }
        binding.btnS07NavCreate.setOnClickListener { onCreateCapsuleListener?.invoke() }
        binding.btnS07NavAccount.setOnClickListener { onAccountSettingsListener?.invoke() }

        val capsuleId = arguments?.getString(ARG_CAPSULE_ID) ?: ""
        if (capsuleId.isNotBlank()) {
            viewModel.loadDetail(capsuleId)
        }

        binding.btnS07Retry.setOnClickListener {
            if (capsuleId.isNotBlank()) {
                viewModel.loadDetail(capsuleId)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: CapsuleDetailUiState) {
        binding.pbS07Loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        // Cancel previous image render if capsule ID or session generation changed
        if (state.capsuleId != activeRenderCapsuleId) {
            imageRenderJob?.cancel()
            binding.ivS07Photo.setImageDrawable(null)
            binding.ivS07Photo.visibility = View.GONE
            activeRenderCapsuleId = state.capsuleId
        }

        if (state.summary != null) {
            val summary = state.summary
            val titleText = if (!summary.title.isNullOrBlank()) summary.title else "A little moment"
            binding.tvS07Headline.text = titleText

            val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a z").withZone(ZoneId.systemDefault())
            val dateStr = summary.createdAt?.let { formatter.format(it) } ?: "Recently"
            val senderName = summary.senderDisplayName?.takeIf { it.isNotBlank() } ?: summary.senderUid
            binding.tvS07Meta.text = "Sent $dateStr by $senderName"

            val unlockAtStr = summary.lock.unlockAt?.let { formatter.format(it) } ?: "Not specified"
            binding.tvS07LockCondition.text = "Unlock time: $unlockAtStr\nLock mode: ${summary.lock.mode.name}"
        } else {
            binding.tvS07Headline.text = ""
            binding.tvS07Meta.text = ""
            binding.tvS07LockCondition.text = ""
        }

        when (state.role) {
            ParticipantRole.AUTHOR_COPY -> {
                binding.tvS07StatusBadge.text = getString(R.string.s07_badge_author)
                binding.tvS07StatusBadge.setBackgroundResource(R.color.capsule_badge_bg)
            }
            ParticipantRole.RECIPIENT -> {
                if (state.isUnlocked) {
                    binding.tvS07StatusBadge.text = getString(R.string.s07_badge_opened)
                    binding.tvS07StatusBadge.setBackgroundResource(R.color.capsule_success_bg)
                } else {
                    binding.tvS07StatusBadge.text = getString(R.string.s07_badge_sealed)
                    binding.tvS07StatusBadge.setBackgroundResource(R.color.capsule_badge_bg)
                }
            }
            ParticipantRole.NONE -> {
                binding.tvS07StatusBadge.text = "Access denied"
            }
        }

        // Content card
        if (state.content != null) {
            binding.llS07ContentCard.visibility = View.VISIBLE
            binding.tvS07BodyText.text = state.content.text ?: ""

            val photoBytes = state.content.photoBytes
            if (photoBytes != null && photoBytes.isNotEmpty()) {
                imageRenderJob?.cancel()
                val token = ++imageRenderToken
                val expectedCapsuleId = state.capsuleId
                val expectedSession = state.sessionSnapshot
                val sessionTracker = (requireActivity() as MainActivity).container.sessionTracker

                imageRenderJob = viewLifecycleOwner.lifecycleScope.launch {
                    val bitmap = decodeSampledBitmap(photoBytes, 1024)
                    val currentSession = sessionTracker.currentSession()
                    val currentState = viewModel.uiState.value

                    // Revalidate live session, render token, capsule ID, and binding before rendering
                    if (_binding != null &&
                        token == imageRenderToken &&
                        currentSession == expectedSession &&
                        currentState.capsuleId == expectedCapsuleId &&
                        sessionTracker.isSessionValid(expectedSession ?: currentSession)) {
                        if (bitmap != null) {
                            binding.ivS07Photo.setImageBitmap(bitmap)
                            binding.ivS07Photo.visibility = View.VISIBLE
                        } else {
                            binding.ivS07Photo.setImageDrawable(null)
                            binding.ivS07Photo.visibility = View.GONE
                            Toast.makeText(requireContext(), "Failed to decode image.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        bitmap?.recycle()
                    }
                }
            } else {
                imageRenderJob?.cancel()
                binding.ivS07Photo.setImageDrawable(null)
                binding.ivS07Photo.visibility = View.GONE
            }

            val senderName = state.summary?.senderDisplayName?.takeIf { it.isNotBlank() } ?: state.summary?.senderUid ?: ""
            binding.tvS07SignOff.text = "With care,\n$senderName"
        } else {
            binding.llS07ContentCard.visibility = View.GONE
            binding.tvS07BodyText.text = ""
            binding.tvS07SignOff.text = ""
            imageRenderJob?.cancel()
            binding.ivS07Photo.setImageDrawable(null)
            binding.ivS07Photo.visibility = View.GONE
        }

        // Banner and Error / Retry
        if (state.isUnlocked && state.content != null) {
            binding.tvS07Banner.text = "This moment stays open, wherever you go."
            binding.tvS07Banner.visibility = View.VISIBLE
        } else {
            binding.tvS07Banner.visibility = View.GONE
        }

        if (!state.errorMessage.isNullOrBlank()) {
            Toast.makeText(requireContext(), state.errorMessage, Toast.LENGTH_SHORT).show()
        }

        binding.btnS07Retry.visibility = if (state.isAssetUnavailable || !state.isUnlocked) View.VISIBLE else View.GONE
    }

    private suspend fun decodeSampledBitmap(photoBytes: ByteArray, maxDim: Int = 1024): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var inSampleSize = 1
            while (bounds.outWidth / inSampleSize > maxDim || bounds.outHeight / inSampleSize > maxDim) {
                inSampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size, options)
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        imageRenderJob?.cancel()
        _binding?.ivS07Photo?.setImageDrawable(null)
        _binding = null
    }
}
