package com.example.timecapsule.data.adapter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.timecapsule.domain.model.ForegroundLocationSample
import com.example.timecapsule.domain.model.LocationSampleResult
import com.example.timecapsule.domain.model.LocationSampleStatus
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Concrete Android [LocationAdapter] using Google Play services FusedLocationProviderClient.
 * Fetches fresh, one-shot foreground location fixes with PRIORITY_HIGH_ACCURACY.
 *
 * @param context Application context used to check permissions and location services.
 * @param fusedLocationClient Optional injected client for testing, defaults to Play Services client.
 */
class DefaultLocationAdapter(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
) : LocationAdapter {

    companion object {
        /** Default timeout for one-shot location acquisition in milliseconds. */
        const val LOCATION_TIMEOUT_MS: Long = 10_000L
    }

    override fun isLocationAvailable(): Boolean {
        val fineGranted = isFineLocationGranted()
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasPermission = fineGranted || coarseGranted
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        return hasPermission && (isGpsEnabled || isNetworkEnabled)
    }

    override fun isFineLocationGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    override suspend fun getCurrentLocation(): LocationSampleResult {
        // 1. Check precise permission (ACCESS_FINE_LOCATION)
        if (!isFineLocationGranted()) {
            return LocationSampleResult(
                status = LocationSampleStatus.PERMISSION_DENIED,
                errorMessage = "Precise location permission (ACCESS_FINE_LOCATION) is required."
            )
        }

        // 2. Check system location service availability
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

        if (!isGpsEnabled && !isNetworkEnabled) {
            return LocationSampleResult(
                status = LocationSampleStatus.SERVICE_DISABLED,
                errorMessage = "Location services (GPS/Network) are disabled on the device."
            )
        }

        // 3. Request one-shot location fix with PRIORITY_HIGH_ACCURACY and cancellation support
        val cancellationTokenSource = CancellationTokenSource()
        return try {
            val result = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<LocationSampleResult> { continuation ->
                    continuation.invokeOnCancellation {
                        cancellationTokenSource.cancel()
                    }

                    try {
                        val request = CurrentLocationRequest.Builder()
                            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                            .setMaxUpdateAgeMillis(60_000L) // Accept recent location fix up to 60s
                            .build()

                        fusedLocationClient.getCurrentLocation(request, cancellationTokenSource.token)
                            .addOnSuccessListener { androidLocation ->
                                if (androidLocation != null) {
                                    val sampleNanos = androidLocation.elapsedRealtimeNanos
                                    if (sampleNanos <= 0L) {
                                        continuation.resume(
                                            LocationSampleResult(
                                                status = LocationSampleStatus.TIMEOUT_OR_NULL,
                                                errorMessage = "Location fix is missing a valid monotonic timestamp."
                                            )
                                        )
                                    } else {
                                        val accuracy = if (androidLocation.hasAccuracy()) androidLocation.accuracy else null
                                        val sample = ForegroundLocationSample(
                                            latitude = androidLocation.latitude,
                                            longitude = androidLocation.longitude,
                                            elapsedRealtimeNanos = sampleNanos,
                                            horizontalAccuracyMeters = accuracy
                                        )
                                        continuation.resume(
                                            LocationSampleResult(
                                                status = LocationSampleStatus.SUCCESS,
                                                sample = sample
                                            )
                                        )
                                    }
                                } else {
                                    continuation.resume(
                                        LocationSampleResult(
                                            status = LocationSampleStatus.TIMEOUT_OR_NULL,
                                            errorMessage = "Location provider returned a null fix."
                                        )
                                    )
                                }
                            }
                            .addOnFailureListener { exception ->
                                val status = if (exception is SecurityException) {
                                    LocationSampleStatus.SECURITY_EXCEPTION
                                } else {
                                    LocationSampleStatus.SERVICE_UNAVAILABLE
                                }
                                continuation.resume(
                                    LocationSampleResult(
                                        status = status,
                                        errorMessage = exception.message ?: "Failed to acquire location fix."
                                    )
                                )
                            }
                            .addOnCanceledListener {
                                continuation.resume(
                                    LocationSampleResult(
                                        status = LocationSampleStatus.TIMEOUT_OR_NULL,
                                        errorMessage = "Location request was cancelled."
                                    )
                                )
                            }
                    } catch (secEx: SecurityException) {
                        continuation.resume(
                            LocationSampleResult(
                                status = LocationSampleStatus.SECURITY_EXCEPTION,
                                errorMessage = secEx.message ?: "Security exception requesting location."
                            )
                        )
                    } catch (ex: Exception) {
                        continuation.resume(
                            LocationSampleResult(
                                status = LocationSampleStatus.SERVICE_UNAVAILABLE,
                                errorMessage = ex.message ?: "Unexpected error requesting location."
                            )
                        )
                    }
                }
            }

            result ?: LocationSampleResult(
                status = LocationSampleStatus.TIMEOUT_OR_NULL,
                errorMessage = "Location request timed out after ${LOCATION_TIMEOUT_MS / 1000} seconds."
            )
        } catch (cancelled: CancellationException) {
            cancellationTokenSource.cancel()
            throw cancelled
        } catch (secEx: SecurityException) {
            cancellationTokenSource.cancel()
            LocationSampleResult(
                status = LocationSampleStatus.SECURITY_EXCEPTION,
                errorMessage = secEx.message ?: "Security exception during location acquisition."
            )
        } catch (ex: Exception) {
            cancellationTokenSource.cancel()
            LocationSampleResult(
                status = LocationSampleStatus.SERVICE_UNAVAILABLE,
                errorMessage = ex.message ?: "Failed to acquire location fix."
            )
        }
    }
}
