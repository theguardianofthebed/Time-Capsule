package com.example.timecapsule.ui.s05

import android.Manifest
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.timecapsule.MainActivity
import com.example.timecapsule.R
import com.example.timecapsule.data.adapter.LocationAdapter
import com.example.timecapsule.databinding.DialogS05MapTargetBinding
import com.example.timecapsule.domain.location.LocationQualifier
import com.example.timecapsule.domain.model.LocationSampleResult
import com.example.timecapsule.domain.model.LocationSampleStatus
import com.example.timecapsule.domain.model.LocationTarget
import com.example.timecapsule.domain.model.QualificationFailureReason
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * DialogFragment embedding Google Maps SDK for selecting a WGS84 target coordinate
 * with a fixed 150m radius boundary.
 *
 * Communicates result back to host fragment via lifecycle-safe [setFragmentResult].
 * Persists pending selection in savedInstanceState across activity rotation/recreation.
 * Manual pin selection works without location permissions.
 * "Use current location" validates sample freshness and accuracy before selection.
 */
class S05MapTargetDialogFragment : DialogFragment(), OnMapReadyCallback {

    companion object {
        const val REQUEST_KEY_MAP_TARGET = "S05MapTargetResult"
        const val RESULT_KEY_LATITUDE = "key_latitude"
        const val RESULT_KEY_LONGITUDE = "key_longitude"
        const val RESULT_KEY_RADIUS = "key_radius"
        const val RESULT_KEY_DISPLAY_NAME = "key_display_name"
        const val RESULT_KEY_CAPSULE_ID = "key_capsule_id"
        const val RESULT_KEY_SESSION_UID = "key_session_uid"
        const val RESULT_KEY_SESSION_GENERATION = "key_session_generation"
        const val RESULT_KEY_REQUEST_ID = "key_request_id"

        private const val ARG_INITIAL_LAT = "arg_initial_lat"
        private const val ARG_INITIAL_LNG = "arg_initial_lng"
        private const val ARG_INITIAL_NAME = "arg_initial_name"
        private const val ARG_CAPSULE_ID = "arg_capsule_id"
        private const val ARG_SESSION_UID = "arg_session_uid"
        private const val ARG_SESSION_GENERATION = "arg_session_generation"
        private const val ARG_REQUEST_ID = "arg_request_id"

        private const val KEY_PENDING_LAT = "key_pending_lat"
        private const val KEY_PENDING_LNG = "key_pending_lng"
        private const val KEY_HAS_PENDING = "key_has_pending"
        private const val KEY_PENDING_NAME = "key_pending_name"

        /** Constructs a new dialog fragment with initial target, capsule ID, session, and request ID. */
        fun newInstance(
            initialTarget: LocationTarget?,
            initialName: String?,
            capsuleId: String? = null,
            sessionUid: String? = null,
            sessionGeneration: Long = -1L,
            requestId: String? = null
        ): S05MapTargetDialogFragment {
            return S05MapTargetDialogFragment().apply {
                arguments = Bundle().apply {
                    if (initialTarget != null) {
                        putDouble(ARG_INITIAL_LAT, initialTarget.latitude)
                        putDouble(ARG_INITIAL_LNG, initialTarget.longitude)
                        if (initialName != null) putString(ARG_INITIAL_NAME, initialName)
                    }
                    if (capsuleId != null) putString(ARG_CAPSULE_ID, capsuleId)
                    if (sessionUid != null) putString(ARG_SESSION_UID, sessionUid)
                    putLong(ARG_SESSION_GENERATION, sessionGeneration)
                    if (requestId != null) putString(ARG_REQUEST_ID, requestId)
                }
            }
        }
    }

    private var _binding: DialogS05MapTargetBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var currentMarker: Marker? = null
    private var currentCircle: Circle? = null

    private var selectedLatLng: LatLng? = null
    private var selectedLocationName: String? = null

    /** Allow injecting a mock [LocationAdapter] for testing environments. */
    var testLocationAdapter: LocationAdapter? = null

    private lateinit var locationAdapter: LocationAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            binding.tvMapStatus.visibility = View.GONE
            fetchAndMoveToCurrentLocation()
        } else {
            binding.tvMapStatus.text = getString(R.string.s05_map_permission_denied)
            binding.tvMapStatus.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogS05MapTargetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity
        locationAdapter = testLocationAdapter
            ?: mainActivity?.container?.locationAdapter
            ?: object : LocationAdapter {
                override fun isLocationAvailable(): Boolean = false
                override fun isFineLocationGranted(): Boolean = false
                override suspend fun getCurrentLocation(): LocationSampleResult =
                    LocationSampleResult(LocationSampleStatus.PERMISSION_DENIED)
            }

        // Restore pending selection from savedInstanceState or arguments
        if (savedInstanceState != null && savedInstanceState.getBoolean(KEY_HAS_PENDING, false)) {
            val lat = savedInstanceState.getDouble(KEY_PENDING_LAT)
            val lng = savedInstanceState.getDouble(KEY_PENDING_LNG)
            selectedLatLng = LatLng(lat, lng)
            selectedLocationName = savedInstanceState.getString(KEY_PENDING_NAME)
        } else {
            val args = arguments
            if (args != null && args.containsKey(ARG_INITIAL_LAT) && args.containsKey(ARG_INITIAL_LNG)) {
                val lat = args.getDouble(ARG_INITIAL_LAT)
                val lng = args.getDouble(ARG_INITIAL_LNG)
                selectedLatLng = LatLng(lat, lng)
                selectedLocationName = args.getString(ARG_INITIAL_NAME)
            }
        }

        // Synchronously set initial Confirm button state
        val hasSelection = selectedLatLng != null
        binding.btnConfirmLocation.isEnabled = hasSelection
        if (!hasSelection) {
            binding.tvSelectedCoordinates.text = getString(R.string.s05_map_hint_tap)
        }

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnCloseMap.setOnClickListener {
            dismiss()
        }

        binding.btnUseCurrentLocation.setOnClickListener {
            if (locationAdapter.isFineLocationGranted()) {
                binding.tvMapStatus.visibility = View.GONE
                fetchAndMoveToCurrentLocation()
            } else {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        binding.btnConfirmLocation.setOnClickListener {
            val latLng = selectedLatLng
            if (latLng != null) {
                val name = selectedLocationName ?: String.format(Locale.US, "%.4f, %.4f", latLng.latitude, latLng.longitude)
                val args = arguments
                val result = Bundle().apply {
                    putDouble(RESULT_KEY_LATITUDE, latLng.latitude)
                    putDouble(RESULT_KEY_LONGITUDE, latLng.longitude)
                    putDouble(RESULT_KEY_RADIUS, LocationTarget.FIXED_RADIUS_METERS)
                    putString(RESULT_KEY_DISPLAY_NAME, name)
                    args?.getString(ARG_CAPSULE_ID)?.let { putString(RESULT_KEY_CAPSULE_ID, it) }
                    args?.getString(ARG_SESSION_UID)?.let { putString(RESULT_KEY_SESSION_UID, it) }
                    if (args != null && args.containsKey(ARG_SESSION_GENERATION)) {
                        putLong(RESULT_KEY_SESSION_GENERATION, args.getLong(ARG_SESSION_GENERATION))
                    }
                    args?.getString(ARG_REQUEST_ID)?.let { putString(RESULT_KEY_REQUEST_ID, it) }
                }
                parentFragmentManager.setFragmentResult(REQUEST_KEY_MAP_TARGET, result)
                dismiss()
            } else {
                binding.tvMapStatus.text = getString(R.string.s05_map_hint_tap)
                binding.tvMapStatus.visibility = View.VISIBLE
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false

        val initialPos = selectedLatLng
        if (initialPos != null) {
            updatePinAndCircle(initialPos)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPos, 15f))
            binding.btnConfirmLocation.isEnabled = true
        } else {
            // Default camera view without an active marker or confirmed pin
            val defaultSeoul = LatLng(37.5665, 126.9780)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultSeoul, 12f))
            binding.tvSelectedCoordinates.text = getString(R.string.s05_map_hint_tap)
            binding.btnConfirmLocation.isEnabled = false
        }

        map.setOnMapClickListener { latLng ->
            binding.tvMapStatus.visibility = View.GONE
            selectedLocationName = String.format(Locale.US, "%.4f, %.4f", latLng.latitude, latLng.longitude)
            updatePinAndCircle(latLng)
            binding.btnConfirmLocation.isEnabled = true
        }
    }

    private fun updatePinAndCircle(latLng: LatLng) {
        selectedLatLng = latLng
        val map = googleMap ?: return

        currentMarker?.remove()
        currentCircle?.remove()

        val radiusMeters = LocationTarget.FIXED_RADIUS_METERS

        currentMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Target Location")
                .snippet("${radiusMeters.toInt()}m radius")
        )

        currentCircle = map.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(radiusMeters)
                .strokeColor(0xFF865B4B.toInt())
                .fillColor(0x22865B4B)
                .strokeWidth(4f)
        )

        val displayName = selectedLocationName ?: String.format(Locale.US, "%.4f, %.4f", latLng.latitude, latLng.longitude)
        binding.tvSelectedCoordinates.text = "$displayName (150 m radius)"
    }

    private fun fetchAndMoveToCurrentLocation() {
        binding.mapProgressBar.visibility = View.VISIBLE
        binding.btnUseCurrentLocation.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = locationAdapter.getCurrentLocation()
            binding.mapProgressBar.visibility = View.GONE
            binding.btnUseCurrentLocation.isEnabled = true

            when (result.status) {
                LocationSampleStatus.SUCCESS -> {
                    val sample = result.sample
                    val nowNanos = SystemClock.elapsedRealtimeNanos()
                    val quality = LocationQualifier.validateSampleQuality(sample, nowNanos)

                    if (sample != null && quality == QualificationFailureReason.NONE) {
                        val currentLatLng = LatLng(sample.latitude, sample.longitude)
                        selectedLocationName = String.format(Locale.US, "%.4f, %.4f", sample.latitude, sample.longitude)
                        updatePinAndCircle(currentLatLng)
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                        binding.btnConfirmLocation.isEnabled = true
                        binding.tvMapStatus.visibility = View.GONE
                    } else {
                        val errorMsg = when (quality) {
                            QualificationFailureReason.POOR_ACCURACY -> {
                                val acc = sample?.horizontalAccuracyMeters?.toInt() ?: 0
                                "Location accuracy too low (${acc} m > 100 m required). Select manually on the map."
                            }
                            QualificationFailureReason.STALE_SAMPLE -> "Location fix is stale. Select manually on the map."
                            else -> getString(R.string.s05_map_acquisition_failed)
                        }
                        binding.tvMapStatus.text = errorMsg
                        binding.tvMapStatus.visibility = View.VISIBLE
                    }
                }
                LocationSampleStatus.PERMISSION_DENIED -> {
                    binding.tvMapStatus.text = getString(R.string.s05_map_permission_denied)
                    binding.tvMapStatus.visibility = View.VISIBLE
                }
                LocationSampleStatus.SERVICE_DISABLED -> {
                    binding.tvMapStatus.text = getString(R.string.s05_map_service_disabled)
                    binding.tvMapStatus.visibility = View.VISIBLE
                }
                else -> {
                    binding.tvMapStatus.text = result.errorMessage ?: getString(R.string.s05_map_acquisition_failed)
                    binding.tvMapStatus.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroyView() {
        binding.mapView.onDestroy()
        super.onDestroyView()
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _binding?.mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val pos = selectedLatLng
        if (pos != null) {
            outState.putBoolean(KEY_HAS_PENDING, true)
            outState.putDouble(KEY_PENDING_LAT, pos.latitude)
            outState.putDouble(KEY_PENDING_LNG, pos.longitude)
            selectedLocationName?.let { outState.putString(KEY_PENDING_NAME, it) }
        }
        _binding?.mapView?.onSaveInstanceState(outState)
    }
}
