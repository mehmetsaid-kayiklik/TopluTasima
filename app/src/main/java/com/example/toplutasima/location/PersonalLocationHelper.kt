package com.example.toplutasima.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.toplutasima.BuildConfig
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
import kotlin.coroutines.resume
import java.util.concurrent.TimeUnit

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
        if (!hasPermission()) return null
        val coords = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { getCurrentCoords() } ?: return null
        val address = getAddressFromCoords(coords.first, coords.second)
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
                        cont.resume(loc.latitude to loc.longitude)
                    } else {
                        // Yedek: son bilinen konum
                        client.lastLocation
                            .addOnSuccessListener { last ->
                                cont.resume(if (last != null) last.latitude to last.longitude else null)
                            }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    if (BuildConfig.DEBUG) Log.e(TAG, "getCurrentLocation failed: ${it.message}")
                    cont.resume(null)
                }
        }
    }

    private suspend fun getAddressFromCoords(lat: Double, lng: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext "$lat, $lng"
                @Suppress("DEPRECATION")
                val results = Geocoder(context, Locale.getDefault())
                    .getFromLocation(lat, lng, 1)
                if (!results.isNullOrEmpty()) {
                    results[0].getAddressLine(0) ?: "$lat, $lng"
                } else {
                    "$lat, $lng"
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Geocoder failed: ${e.message}")
                "$lat, $lng"
            }
        }
    }

    // ── ORS Yol Mesafesi ─────────────────────────────────────────────────────

    /**
     * Verilen waypoint listesinden ORS API ile gerçek yol mesafesini hesaplar.
     * @param waypoints (lat, lng) çiftlerinin listesi — en az 2 nokta gerekmektedir
     * @return metre cinsinden yol mesafesi, veya null (hata / API limiti)
     */
    suspend fun fetchRouteDistanceMeters(waypoints: List<Pair<Double, Double>>): Double? {
        if (waypoints.size < 2) return null
        return withContext(Dispatchers.IO) {
            try {
                // ORS beklentisi: [[lng, lat], [lng, lat], ...]
                val coords = JSONArray().apply {
                    waypoints.forEach { (lat, lng) ->
                        put(JSONArray().put(lng).put(lat))
                    }
                }
                val body = JSONObject().put("coordinates", coords).toString()

                val request = Request.Builder()
                    .url(ORS_DIRECTIONS_URL)
                    .addHeader("Authorization", BuildConfig.ORS_API_KEY)
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "ORS error ${response.code}: ${response.body?.string()}")
                        return@withContext null
                    }

                    val json = JSONObject(response.body?.string() ?: return@withContext null)
                    val routes = json.optJSONArray("routes") ?: return@withContext null
                    val summary = routes.getJSONObject(0).getJSONObject("summary")
                    summary.getDouble("distance") // metre
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "fetchRouteDistanceMeters failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Metre cinsinden mesafeyi "X.X km" formatına çevirir.
     */
    fun formatKm(meters: Double): String =
        String.format(Locale.getDefault(), "%.1f km", meters / 1000.0)
}
