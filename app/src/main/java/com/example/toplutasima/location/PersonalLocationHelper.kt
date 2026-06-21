package com.example.toplutasima.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.diagnostics.PersonalTripTrackerLogger
import com.example.toplutasima.network.ApiErrors
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class RouteWaypoint(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double? = null,
    val accuracyM: Double? = null
)

/**
 * Kişisel biniş özelliği için konum yardımcısı.
 * Toplu taşımanın NearbyStopsManager'ından tamamen bağımsızdır.
 *
 * İki işlev:
 *  1. Tek seferlik GPS alma + Geocoder ile sokak adresine çevirme
 *  2. ORS API ile birden fazla waypoint arasındaki gerçek yol mesafesini hesaplama
 */
class PersonalLocationHelper(private val context: Context) {

    companion object {
        private const val TAG = "PersonalLocationHelper"
        private const val LOCATION_TIMEOUT_MS = 8_000L
        private const val ORS_DIRECTIONS_URL =
            "https://api.openrouteservice.org/v2/directions/driving-car"
        private const val DEFAULT_ORS_RADIUS_M = 50.0
        private const val MIN_ORS_RADIUS_M = 20.0
        private const val MAX_ORS_RADIUS_M = 50.0
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ── İzin kontrolü ───────────────────────────────────────────────────────

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    // ── Konum alma + sokak adresine çevirme ─────────────────────────────────

    /**
     * Mevcut konumu GPS'ten alır, Android Geocoder ile sokak adresine çevirir.
     * @return Triple(adres, lat, lng) veya null (izin yok / timeout / Geocoder başarısız)
     */
    suspend fun resolveCurrentLocation(): Triple<String, Double, Double>? {
        logTracker("resolveCurrentLocation start hasPermission=${hasPermission()}")
        if (!hasPermission()) {
            logTracker("resolveCurrentLocation aborted reason=no_location_permission")
            return null
        }
        val coords = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { getCurrentCoords() } ?: run {
            logTracker("resolveCurrentLocation failed reason=timeout_or_null timeoutMs=$LOCATION_TIMEOUT_MS")
            return null
        }
        val address = getAddressFromCoords(coords.first, coords.second)
        logTracker("resolveCurrentLocation success point=${formatPoint(coords)} addressChars=${address.length}")
        return Triple(address, coords.first, coords.second)
    }

    @Suppress("MissingPermission")
    private suspend fun getCurrentCoords(): Pair<Double, Double>? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val tokenSource = CancellationTokenSource()

        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { tokenSource.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        logTracker("getCurrentLocation success source=current ${formatLocation(loc)}")
                        cont.resume(loc.latitude to loc.longitude)
                    } else {
                        logTracker("getCurrentLocation returned null; reading lastLocation")
                        // Yedek: son bilinen konum
                        client.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) {
                                    logTracker("lastLocation success ${formatLocation(last)}")
                                } else {
                                    logTracker("lastLocation returned null")
                                }
                                cont.resume(if (last != null) last.latitude to last.longitude else null)
                            }
                            .addOnFailureListener {
                                logTracker("lastLocation failed message=${it.message}")
                                cont.resume(null)
                            }
                    }
                }
                .addOnFailureListener {
                    if (BuildConfig.DEBUG) Log.e(TAG, "getCurrentLocation failed: ${it.message}")
                    logTracker("getCurrentLocation failed message=${it.message}")
                    cont.resume(null)
                }
        }
    }

    private suspend fun getAddressFromCoords(lat: Double, lng: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    logTracker("Geocoder not present point=${formatPoint(lat to lng)}")
                    return@withContext "$lat, $lng"
                }
                @Suppress("DEPRECATION")
                val results = Geocoder(context, Locale.getDefault())
                    .getFromLocation(lat, lng, 1)
                if (!results.isNullOrEmpty()) {
                    val address = results[0].getAddressLine(0) ?: "$lat, $lng"
                    logTracker("Geocoder success point=${formatPoint(lat to lng)} addressChars=${address.length}")
                    address
                } else {
                    logTracker("Geocoder empty point=${formatPoint(lat to lng)}")
                    "$lat, $lng"
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Geocoder failed: ${e.message}")
                logTracker("Geocoder failed point=${formatPoint(lat to lng)} message=${e.message}")
                "$lat, $lng"
            }
        }
    }

    // ── ORS Yol Mesafesi ─────────────────────────────────────────────────────

    /**
     * Verilen waypoint listesinden ORS API ile gerçek yol mesafesini hesaplar.
     * @param waypoints rota noktaları — en az 2 nokta gerekmektedir
     * @return metre cinsinden yol mesafesi, veya null (hata / API limiti)
     */
    suspend fun fetchRouteDistanceMeters(waypoints: List<RouteWaypoint>): Double? {
        if (waypoints.size < 2) return null
        return withContext(Dispatchers.IO) {
            try {
                val requestId = ApiErrors.newRequestId()
                logTracker(
                    "ORS_HTTP request requestId=$requestId waypoints=${waypoints.size} " +
                        "first=${formatPoint(waypoints.first())} last=${formatPoint(waypoints.last())}"
                )
                // ORS beklentisi: [[lng, lat], [lng, lat], ...]
                val coords = JSONArray().apply {
                    waypoints.forEach { waypoint ->
                        put(JSONArray().put(waypoint.longitude).put(waypoint.latitude))
                    }
                }
                val radiuses = JSONArray().apply {
                    waypoints.forEach { waypoint ->
                        put(radiusForAccuracyM(waypoint.accuracyM))
                    }
                }
                val body = JSONObject()
                    .put("coordinates", coords)
                    .put("continue_straight", true)
                    .put("radiuses", radiuses)
                    .toString()

                val request = Request.Builder()
                    .url(ORS_DIRECTIONS_URL)
                    .addHeader("Authorization", BuildConfig.ORS_API_KEY)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json")
                    .addHeader("X-Request-Id", requestId)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    logTracker(
                        "ORS_HTTP response requestId=$requestId status=${response.code} " +
                            "successful=${response.isSuccessful} bodyChars=${responseBody.length}"
                    )
                    if (!response.isSuccessful) {
                        val apiError = ApiErrors.fromHttpStatus(
                            provider = "ORS",
                            endpoint = "directions/driving-car",
                            requestId = requestId,
                            statusCode = response.code,
                            body = responseBody
                        )
                        if (BuildConfig.DEBUG) Log.e(TAG, apiError.message ?: "ORS HTTP ${response.code}", apiError)
                        logTracker("ORS_HTTP failed requestId=$requestId status=${response.code} message=${apiError.message}")
                        return@withContext null
                    }

                    val json = JSONObject(responseBody)
                    val routes = json.optJSONArray("routes") ?: return@withContext null
                    val summary = routes.getJSONObject(0).getJSONObject("summary")
                    val distanceMeters = summary.getDouble("distance")
                    logTracker(
                        "ORS_HTTP success requestId=$requestId distanceKm=${String.format(Locale.US, "%.3f", distanceMeters / 1000.0)}"
                    )
                    distanceMeters // metre
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "fetchRouteDistanceMeters failed: ${e.message}")
                logTracker("ORS_HTTP exception message=${e.message}")
                null
            }
        }
    }

    /**
     * Metre cinsinden mesafeyi "X.X km" formatına çevirir.
     */
    fun formatKm(meters: Double): String =
        String.format(Locale.getDefault(), "%.1f km", meters / 1000.0)

    private fun logTracker(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
        PersonalTripTrackerLogger.log(context, TAG, message)
    }

    private fun formatLocation(location: Location): String =
        "lat=${formatCoord(location.latitude)} lng=${formatCoord(location.longitude)} " +
            "accuracyM=${if (location.hasAccuracy()) String.format(Locale.US, "%.1f", location.accuracy) else "n/a"} " +
            "speedMps=${if (location.hasSpeed()) String.format(Locale.US, "%.2f", location.speed) else "n/a"} " +
            "ageMs=${System.currentTimeMillis() - location.time} elapsedNanos=${location.elapsedRealtimeNanos}"

    private fun radiusForAccuracyM(accuracyM: Double?): Double =
        (accuracyM?.let { it * 2.0 } ?: DEFAULT_ORS_RADIUS_M)
            .coerceIn(MIN_ORS_RADIUS_M, MAX_ORS_RADIUS_M)

    private fun formatPoint(point: Pair<Double, Double>): String =
        "${formatCoord(point.first)},${formatCoord(point.second)}"

    private fun formatPoint(point: RouteWaypoint): String =
        "${formatCoord(point.latitude)},${formatCoord(point.longitude)}"

    private fun formatCoord(value: Double): String =
        String.format(Locale.US, "%.6f", value)
}
