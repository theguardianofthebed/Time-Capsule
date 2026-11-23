package com.example.timecapsule.ui.s05

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.databinding.FragmentS05RecipientLockBinding
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.LockMode
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.time.TimeConversionHelper
import com.example.timecapsule.ui.ViewModelFactory
import com.example.timecapsule.ui.draft.CapsuleDraftViewModel
import com.example.timecapsule.ui.draft.RecipientLookupStatus
import com.example.timecapsule.ui.draft.RecipientMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * S-05 Recipient Selection & Lock Conditions ScreenFragment using native Views and ViewBinding.
 * Provides recipient choice (Future me vs Someone else exact UID lookup and explicit confirmation)
 * and lock condition configuration (Time, Location, Both).
 */
class S05RecipientLockFragment : Fragment() {

    private var _binding: FragmentS05RecipientLockBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CapsuleDraftViewModel

    var onBackToContentDraftListener: (() -> Unit)? = null
    var onNextToConfirmSendListener: (() -> Unit)? = null
    var onNavigateToMomentsListener: (() -> Unit)? = null
    var onNavigateToAccountListener: (() -> Unit)? = null

    private var isSyncingText = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentS05RecipientLockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = requireActivity() as MainActivity
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(mainActivity.container.accountSessionManager,
                requireContext().applicationContext, mainActivity.container.repository)
        )[CapsuleDraftViewModel::class.java]

        viewModel.syncSessionState()

        parentFragmentManager.setFragmentResultListener(
            S05MapTargetDialogFragment.REQUEST_KEY_MAP_TARGET,
            viewLifecycleOwner
        ) { _, bundle ->
            val lat = bundle.getDouble(S05MapTargetDialogFragment.RESULT_KEY_LATITUDE)
            val lng = bundle.getDouble(S05MapTargetDialogFragment.RESULT_KEY_LONGITUDE)
            val name = bundle.getString(S05MapTargetDialogFragment.RESULT_KEY_DISPLAY_NAME)
            val target = LocationTarget(latitude = lat, longitude = lng, radiusMeters = LocationTarget.FIXED_RADIUS_METERS)
            viewModel.updateLocation(target, name)
            binding.tvS05Error.visibility = View.GONE
        }

        setupClickListeners()
        setupTextWatcher()
        observeViewModelState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncSessionState()
    }

    private fun setupTextWatcher() {
        binding.etRecipientUid.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isSyncingText) {
                    viewModel.updateRecipientUidInput(s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackToContentDraftListener?.invoke()
        }

        binding.btnModeFutureMe.setOnClickListener {
            viewModel.setRecipientMode(RecipientMode.FUTURE_ME)
        }

        binding.btnModeSomeoneElse.setOnClickListener {
            viewModel.setRecipientMode(RecipientMode.SOMEONE_ELSE)
        }

        binding.btnLookupRecipient.setOnClickListener {
            viewModel.performRecipientLookup()
        }

        binding.btnConfirmRecipient.setOnClickListener {
            viewModel.confirmRecipient()
        }

        binding.btnLockTime.setOnClickListener {
            viewModel.updateLockMode(LockMode.TIME)
            binding.tvS05Error.visibility = View.GONE
        }

        binding.btnLockPlace.setOnClickListener {
            viewModel.updateLockMode(LockMode.LOCATION)
            binding.tvS05Error.visibility = View.GONE
        }

        binding.btnLockBoth.setOnClickListener {
            viewModel.updateLockMode(LockMode.TIME_AND_LOCATION)
            binding.tvS05Error.visibility = View.GONE
        }

        binding.btnSelectDateTime.setOnClickListener {
            showDateTimePicker()
        }

        binding.btnSelectPlace.setOnClickListener {
            val currentTarget = viewModel.uiState.value.locationTarget
            val currentName = viewModel.uiState.value.locationName
            val dialog = S05MapTargetDialogFragment.newInstance(currentTarget, currentName)
            dialog.show(parentFragmentManager, "S05MapTargetDialog")
        }

        binding.btnReviewCapsule.setOnClickListener {
            val validation = viewModel.validateS05RecipientAndLocks()
            if (validation is OperationResult.Success) {
                binding.tvS05Error.visibility = View.GONE
                onNextToConfirmSendListener?.invoke()
            } else if (validation is OperationResult.Invalid) {
                binding.tvS05Error.text = validation.message
                binding.tvS05Error.visibility = View.VISIBLE
            }
        }

        binding.btnNavMoments.setOnClickListener {
            onNavigateToMomentsListener?.invoke()
        }

        binding.btnNavAccount.setOnClickListener {
            onNavigateToAccountListener?.invoke()
        }
    }

    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            val timePickerDialog = TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                val localDate = LocalDate.of(selectedYear, selectedMonth + 1, selectedDay)
                val localTime = LocalTime.of(selectedHour, selectedMinute)
                val zoneId = ZoneId.systemDefault()
                val localDateTime = LocalDateTime.of(localDate, localTime)
                val validOffsets = zoneId.rules.getValidOffsets(localDateTime)

                if (validOffsets.size > 1) {
                    val offsetItems = validOffsets.map { TimeConversionHelper.formatOffset(it) }.toTypedArray()
                    AlertDialog.Builder(requireContext())
                        .setTitle("Select Daylight Saving Offset")
                        .setSingleChoiceItems(offsetItems, 0) { dialog, which ->
                            val chosenOffset = validOffsets[which]
                            val conversionRes = TimeConversionHelper.toUtcInstant(localDate, localTime, zoneId, chosenOffset)
                            if (conversionRes is OperationResult.Success) {
                                val data = conversionRes.data
                                viewModel.updateUnlockAt(data.instant, data.zoneId.id)
                                binding.tvS05Error.visibility = View.GONE
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val conversionRes = TimeConversionHelper.toUtcInstant(localDate, localTime, zoneId)
                    if (conversionRes is OperationResult.Success) {
                        val data = conversionRes.data
                        viewModel.updateUnlockAt(data.instant, data.zoneId.id)
                        binding.tvS05Error.visibility = View.GONE
                    } else if (conversionRes is OperationResult.Invalid) {
                        binding.tvS05Error.text = conversionRes.message
                        binding.tvS05Error.visibility = View.VISIBLE
                    }
                }
            }, hour, minute, true)

            timePickerDialog.show()
        }, year, month, day)

        datePickerDialog.show()
    }

    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    listOf(binding.btnModeFutureMe, binding.btnModeSomeoneElse,
                        binding.btnLookupRecipient, binding.btnConfirmRecipient,
                        binding.btnLockBoth, binding.btnLockPlace, binding.btnLockTime,
                        binding.btnSelectDateTime, binding.btnSelectPlace,
                        binding.etRecipientUid).forEach { it.isEnabled = state.editable }
                    // Recipient Mode toggle formatting
                    if (state.recipientMode == RecipientMode.FUTURE_ME) {
                        binding.btnModeFutureMe.setTextColor(requireContext().getColor(android.R.color.black))
                        binding.btnModeSomeoneElse.setTextColor(requireContext().getColor(android.R.color.darker_gray))
                        binding.layoutUidInputSection.visibility = View.GONE
                    } else {
                        binding.btnModeFutureMe.setTextColor(requireContext().getColor(android.R.color.darker_gray))
                        binding.btnModeSomeoneElse.setTextColor(requireContext().getColor(android.R.color.black))
                        binding.layoutUidInputSection.visibility = View.VISIBLE
                    }

                    // UID Input field sync
                    isSyncingText = true
                    if (binding.etRecipientUid.text.toString() != state.recipientUidInput) {
                        binding.etRecipientUid.setText(state.recipientUidInput)
                    }
                    isSyncingText = false

                    // Lookup status & recipient result card
                    when (state.lookupStatus) {
                        RecipientLookupStatus.IDLE -> {
                            if (state.recipientMode == RecipientMode.FUTURE_ME) {
                                binding.layoutRecipientResult.visibility = View.VISIBLE
                                binding.tvLookupResultName.text = "Found Future Me"
                                binding.tvLookupResultUid.text = "UID: ${state.recipientUidInput}"
                                binding.tvLookupResultBadge.text = "Self"
                                binding.btnConfirmRecipient.visibility = View.GONE
                                binding.layoutRecipientConfirmed.visibility = View.VISIBLE
                                binding.tvConfirmedMessage.text = "Recipient confirmed: Future Me (${state.recipientUidInput})"
                                binding.tvRecipientNote.text = "Capsule will open for your account."
                            } else {
                                binding.layoutRecipientResult.visibility = View.GONE
                            }
                        }
                        RecipientLookupStatus.LOOKING_UP -> {
                            binding.layoutRecipientResult.visibility = View.VISIBLE
                            binding.tvLookupResultName.text = "Looking up recipient…"
                            binding.tvLookupResultUid.text = state.recipientUidInput
                            binding.tvLookupResultBadge.text = "Searching"
                            binding.btnConfirmRecipient.visibility = View.GONE
                            binding.layoutRecipientConfirmed.visibility = View.GONE
                            binding.tvRecipientNote.text = "Checking recipient directory…"
                        }
                        RecipientLookupStatus.FOUND -> {
                            binding.layoutRecipientResult.visibility = View.VISIBLE
                            val profile = state.resolvedProfile
                            val name = profile?.displayName ?: "Found User"
                            val uid = profile?.uid ?: state.recipientUidInput
                            binding.tvLookupResultName.text = "Found $name"
                            binding.tvLookupResultUid.text = "UID: $uid"
                            binding.tvLookupResultBadge.text = "Found"

                            if (state.isRecipientConfirmed) {
                                binding.btnConfirmRecipient.visibility = View.GONE
                                binding.layoutRecipientConfirmed.visibility = View.VISIBLE
                                binding.tvConfirmedMessage.text = "Recipient confirmed: $name ($uid)"
                                binding.tvRecipientNote.text = "Recipient identity confirmed."
                            } else {
                                binding.btnConfirmRecipient.visibility = View.VISIBLE
                                binding.layoutRecipientConfirmed.visibility = View.GONE
                                binding.tvRecipientNote.text = "Tap confirm to seal this capsule for $name."
                            }
                        }
                        RecipientLookupStatus.NONEXISTENT -> {
                            binding.layoutRecipientResult.visibility = View.VISIBLE
                            binding.tvLookupResultName.text = "User not found"
                            binding.tvLookupResultUid.text = state.recipientUidInput
                            binding.tvLookupResultBadge.text = "Not found"
                            binding.btnConfirmRecipient.visibility = View.GONE
                            binding.layoutRecipientConfirmed.visibility = View.GONE
                            binding.tvRecipientNote.text = state.lookupErrorMessage ?: "No user found with exact UID."
                        }
                        RecipientLookupStatus.ERROR -> {
                            binding.layoutRecipientResult.visibility = View.VISIBLE
                            binding.tvLookupResultName.text = "Lookup Error"
                            binding.tvLookupResultUid.text = state.recipientUidInput
                            binding.tvLookupResultBadge.text = "Error"
                            binding.btnConfirmRecipient.visibility = View.GONE
                            binding.layoutRecipientConfirmed.visibility = View.GONE
                            binding.tvRecipientNote.text = state.lookupErrorMessage ?: "Network/service error looking up recipient."
                        }
                    }

                    // Lock mode formatting
                    when (state.lockMode) {
                        LockMode.TIME -> {
                            binding.layoutTimeSection.visibility = View.VISIBLE
                            binding.layoutPlaceSection.visibility = View.GONE
                            binding.tvBothNotice.visibility = View.GONE
                        }
                        LockMode.LOCATION -> {
                            binding.layoutTimeSection.visibility = View.GONE
                            binding.layoutPlaceSection.visibility = View.VISIBLE
                            binding.tvBothNotice.visibility = View.GONE
                        }
                        LockMode.TIME_AND_LOCATION -> {
                            binding.layoutTimeSection.visibility = View.VISIBLE
                            binding.layoutPlaceSection.visibility = View.VISIBLE
                            binding.tvBothNotice.visibility = View.VISIBLE
                        }
                    }

                    // Date & Time display
                    if (state.unlockAt != null) {
                        val zoneId = try { ZoneId.of(state.creationZone) } catch (_: Exception) { ZoneId.systemDefault() }
                        binding.btnSelectDateTime.text = TimeConversionHelper.formatZonedDateTime(state.unlockAt, zoneId)
                    } else {
                        binding.btnSelectDateTime.text = "Select future date & time"
                    }

                    // Location display
                    if (state.locationTarget != null) {
                        val name = state.locationName ?: "Selected Location"
                        binding.btnSelectPlace.text = "$name (${state.locationTarget.radiusMeters.toInt()} m radius)"
                        binding.tvLocationSubtext.text = "Opens within ${state.locationTarget.radiusMeters.toInt()} m of $name."
                    } else {
                        binding.btnSelectPlace.text = "Select place (150 m radius)"
                    }

                    // Primary Review CTA Enable / Text
                    val validationRes = viewModel.validateS05RecipientAndLocks()
                    if (validationRes is OperationResult.Success) {
                        binding.btnReviewCapsule.isEnabled = true
                        binding.btnReviewCapsule.text = getString(R.string.s05_btn_review)
                    } else {
                        binding.btnReviewCapsule.isEnabled = false
                        binding.btnReviewCapsule.text = getString(R.string.s05_btn_review_disabled)
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
