package com.example.timecapsule.ui.s05

import android.Manifest
import android.os.Bundle
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
import com.example.timecapsule.domain.model.LocationSampleStatus
import com.example.timecapsule.domain.model.LocationTarget
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
 * Manual pin selection works without any location permissions.
 * Tapping "Use current location" explicitly checks/requests ACCESS_FINE_LOCATION
 * and fetches a fresh fix via [LocationAdapter].
 */
class S05MapTargetDialogFragment : DialogFragment(), OnMapReadyCallback {

    private var _binding: DialogS05MapTargetBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var currentMarker: Marker? = null
    private var currentCircle: Circle? = null
    private var selectedLatLng: LatLng? = null

    private lateinit var locationAdapter: LocationAdapter

    /** Initial target coordinates if re-editing an existing draft selection. */
    var initialTarget: LocationTarget? = null

    /** Callback invoked only upon explicit user tap on "Confirm Location". */
    var onLocationConfirmed: ((target: LocationTarget, displayName: String) -> Unit)? = null

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

        val mainActivity = requireActivity() as MainActivity
        locationAdapter = mainActivity.container.locationAdapter

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
                val radius = initialTarget?.radiusMeters ?: 150.0
                val target = LocationTarget(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude,
                    radiusMeters = radius
                )
                val displayName = String.format(Locale.US, "%.4f, %.4f", latLng.latitude, latLng.longitude)
                onLocationConfirmed?.invoke(target, displayName)
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

        // Default initial location: initialTarget or Seoul default
        val initialLatLng = if (initialTarget != null) {
            LatLng(initialTarget!!.latitude, initialTarget!!.longitude)
        } else {
            LatLng(37.5665, 126.9780)
        }

        updatePinAndCircle(initialLatLng)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15f))

        map.setOnMapClickListener { latLng ->
            binding.tvMapStatus.visibility = View.GONE
            updatePinAndCircle(latLng)
        }
    }

    private fun updatePinAndCircle(latLng: LatLng) {
        selectedLatLng = latLng
        val map = googleMap ?: return

        currentMarker?.remove()
        currentCircle?.remove()

        val radiusMeters = initialTarget?.radiusMeters ?: 150.0

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

        binding.tvSelectedCoordinates.text = String.format(
            Locale.US,
            "Lat: %.6f, Lon: %.6f (%d m radius)",
            latLng.latitude,
            latLng.longitude,
            radiusMeters.toInt()
        )
    }

    private fun fetchAndMoveToCurrentLocation() {
        binding.mapProgressBar.visibility = View.VISIBLE
        binding.btnUseCurrentLocation.isEnabled = false
        binding.btnConfirmLocation.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = locationAdapter.getCurrentLocation()
            binding.mapProgressBar.visibility = View.GONE
            binding.btnUseCurrentLocation.isEnabled = true
            binding.btnConfirmLocation.isEnabled = true

            when (result.status) {
                LocationSampleStatus.SUCCESS -> {
                    val sample = result.sample
                    if (sample != null) {
                        val currentLatLng = LatLng(sample.latitude, sample.longitude)
                        updatePinAndCircle(currentLatLng)
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                    } else {
                        binding.tvMapStatus.text = getString(R.string.s05_map_acquisition_failed)
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
        _binding?.mapView?.onSaveInstanceState(outState)
    }
}
