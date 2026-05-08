package com.example.toplutasima.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.toplutasima.network.ApiRequestException
import com.example.toplutasima.network.RmvApiService
import com.example.toplutasima.repository.TripRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Manages device location retrieval and RMV nearby stops lookup.
 * Designed to fail silently — returns empty list if anything goes wrong.
 */
class NearbyStopsManager(
    private val context: Context,
    private val repository: TripRepository
) {
    companion object {
        private const val TAG = "NearbyStops"
        private const val LOCATION_TIMEOUT_MS = 5_000L
        private const val DEFAULT_RADIUS_METERS = 500
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    suspend fun fetchNearbyStops(radiusMeters: Int = DEFAULT_RADIUS_METERS): List<RmvApiService.NearbyStop> {
        if (!hasLocationPermission()) {
            Log.d(TAG, "No location permission")
            return emptyList()
        }

        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { getCurrentLocation() }
        if (location == null) {
            Log.d(TAG, "Location timeout or unavailable")
            return emptyList()
        }

        return try {
            repository.searchNearbyStops(location.first, location.second, radiusMeters)
        } catch (e: ApiRequestException) {
            Log.e(TAG, "Nearby stops API error: ${e.message}", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Nearby stops API error: ${e.message}")
            emptyList()
        }
    }

    @Suppress("MissingPermission") // We check permission before calling
    private suspend fun getCurrentLocation(): Pair<Double, Double>? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val tokenSource = CancellationTokenSource()

        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { tokenSource.cancel() }

            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc.latitude to loc.longitude)
                    } else {
                        // Fallback: try lastLocation
                        client.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) cont.resume(last.latitude to last.longitude)
                                else cont.resume(null)
                            }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    Log.e(TAG, "getCurrentLocation failed: ${it.message}")
                    cont.resume(null)
                }
        }
    }
}
