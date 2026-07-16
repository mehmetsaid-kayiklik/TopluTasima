package com.example.toplutasima.network.rmv

import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.network.ApiClient
import com.example.toplutasima.network.ApiErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class SegmentDistanceResult(
    val apiDistanceKm: Double?,
    val polyDistanceKm: Double?
)

class RmvDistanceService(
    private val orsDistanceCalculator: (suspend (List<Pair<Double, Double>>) -> Double)? = null,
    private val railDistanceCalculator: ((List<Pair<Double, Double>>) -> Double)? = null
) {
    private val orsApiKey = BuildConfig.ORS_API_KEY

    suspend fun calculateDistanceORS(
        coords: List<Pair<Double, Double>>,
        polylineCoords: List<Pair<Double, Double>> = emptyList()
    ): SegmentDistanceResult {
        if (coords.size < 2) return SegmentDistanceResult(null, null)
        return withContext(Dispatchers.IO) {
            val apiDistanceKm = try {
                (orsDistanceCalculator?.invoke(coords) ?: requestOrsDistance(coords))
                    .takeIf { it > 0.0 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logE("ORSDiag", "ORS EXCEPTION: ${e.message}")
                null
            }
            val polyDistanceKm = calculateDistanceFromPoly(polylineCoords, coords.first(), coords.last())
            SegmentDistanceResult(apiDistanceKm, polyDistanceKm)
        }
    }

    private fun requestOrsDistance(coords: List<Pair<Double, Double>>): Double {
        return try {
            val requestId = ApiErrors.newRequestId()
            val coordArray = org.json.JSONArray()
            for (coord in coords) coordArray.put(org.json.JSONArray().put(coord.second).put(coord.first))
            val jsonBody = JSONObject().put("coordinates", coordArray).toString()
            val reqBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("https://api.openrouteservice.org/v2/directions/driving-car/geojson")
                .addHeader("Authorization", orsApiKey)
                .addHeader("X-Request-Id", requestId)
                .post(reqBody)
                .build()
            ApiClient.http.newCall(req).execute().use { res ->
                val respBody = res.body?.string().orEmpty()
                if (res.isSuccessful) {
                    val json = JSONObject(respBody)
                    val segments = json.optJSONArray("features")
                        ?.optJSONObject(0)
                        ?.optJSONObject("properties")
                        ?.optJSONArray("segments") ?: org.json.JSONArray()
                    var meters = 0.0
                    for (i in 0 until segments.length()) {
                        meters += segments.optJSONObject(i)?.optDouble("distance", 0.0) ?: 0.0
                    }
                    meters / 1000.0
                } else {
                    val apiError = ApiErrors.fromHttpStatus(
                        provider = "ORS",
                        endpoint = "directions/driving-car",
                        requestId = requestId,
                        statusCode = res.code,
                        body = respBody
                    )
                    logE("ORSDiag", apiError.message ?: "ORS HTTP ${res.code}", apiError)
                    0.0
                }
            }
        } catch (e: Exception) {
            logE("ORSDiag", "ORS EXCEPTION: ${e.message}")
            0.0
        }
    }

    suspend fun calculateDistanceRail(
        coords: List<Pair<Double, Double>>,
        allStopCoords: List<Pair<Double, Double>> = emptyList(),
        fromIdx: Int = -1,
        toIdx: Int = -1,
        polylineCoords: List<Pair<Double, Double>> = emptyList()
    ): SegmentDistanceResult {
        if (coords.size < 2) return SegmentDistanceResult(null, null)
        return withContext(Dispatchers.IO) {
            val exactStart = coords.first()
            val exactEnd = coords.last()
            val apiDistanceKm = try {
                (railDistanceCalculator?.invoke(coords) ?: railRouteWithPairwise(coords))
                    .takeIf { it > 0.0 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logD("RailDist", "API route failed: ${e.message}")
                null
            }
            val polyDistanceKm = calculateDistanceFromPoly(polylineCoords, exactStart, exactEnd)

            if (polyDistanceKm == null) {
                logD(
                    "RailDist",
                    "No usable HAFAS polyline distance. directKm=${formatKm(haversineKm(exactStart, exactEnd))} " +
                        "fromIdx=$fromIdx toIdx=$toIdx allStops=${allStopCoords.size} " +
                        "polylineCoords=${polylineCoords.size}"
                )
            }
            SegmentDistanceResult(apiDistanceKm, polyDistanceKm)
        }
    }

    fun calculateDistanceFromPoly(
        polylineCoords: List<Pair<Double, Double>>,
        fromExact: Pair<Double, Double>,
        toExact: Pair<Double, Double>
    ): Double? {
        return try {
            if (polylineCoords.size < 2) return null

            val fromIdx = closestVertexIndex(polylineCoords, fromExact)
            val toIdx = closestVertexIndex(polylineCoords, toExact)
            val slice = when {
                fromIdx <= toIdx -> polylineCoords.subList(fromIdx, toIdx + 1)
                else -> polylineCoords.subList(toIdx, fromIdx + 1).asReversed()
            }
            if (slice.isEmpty()) return null

            val route = buildList {
                add(fromExact)
                addAll(slice)
                add(toExact)
            }

            var distance = 0.0
            for (i in 0 until route.size - 1) {
                distance += haversineKm(route[i], route[i + 1])
            }

            val directKm = haversineKm(fromExact, toExact)
            if (directKm < 3.0 && distance > directKm * 3.0) {
                logW(
                    "PolyDist",
                    "Suspicious HAFAS polyline distance. directKm=${formatKm(directKm)} " +
                        "polylineKm=${formatKm(distance)} polylineCoords=${polylineCoords.size}"
                )
                return null
            }

            distance.takeIf { it > 0.0 && !it.isNaN() && !it.isInfinite() }
        } catch (e: Exception) {
            logD("PolyDist", "Polyline distance failed: ${e.message}")
            null
        }
    }

    private fun closestVertexIndex(polylineCoords: List<Pair<Double, Double>>, point: Pair<Double, Double>): Int {
        var minIdx = 0
        var minDist = Double.MAX_VALUE
        for (i in polylineCoords.indices) {
            val distance = haversineKm(point, polylineCoords[i])
            if (distance < minDist) {
                minDist = distance
                minIdx = i
            }
        }
        return minIdx
    }

    // API/legacy mesafe kaynağı, orsMesafeKm'i besler.
    private fun railRouteMultiWaypoint(coords: List<Pair<Double, Double>>): Double {
        if (coords.size < 2) return 0.0
        return try {
            val pointParams = coords.joinToString("&") { "point=${it.first},${it.second}" }
            val url =
                "https://routing.openrailrouting.org/route?$pointParams&profile=all_tracks&points_encoded=false"
            val req = Request.Builder().url(url).get().build()
            ApiClient.http.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (res.isSuccessful) {
                    val json = JSONObject(body)
                    val paths = json.optJSONArray("paths")
                    if (paths != null && paths.length() > 0) {
                        val distance = paths.getJSONObject(0).optDouble("distance", 0.0)
                        if (distance > 0) return@use distance / 1000.0
                    }
                } else {
                    logD("RailDist", "HTTP ${res.code} for ${coords.size} coords: ${body.take(150)}")
                }
                0.0
            }
        } catch (e: Exception) {
            logD("RailDist", "Route failed: ${e.message}")
            0.0
        }
    }

    // API/legacy mesafe kaynağı, orsMesafeKm'i besler.
    private fun railRouteWithPairwise(coords: List<Pair<Double, Double>>): Double {
        if (coords.size < 2) return 0.0
        val multiKm = railRouteMultiWaypoint(coords)
        if (multiKm > 0.0) return multiKm
        var totalMeters = 0.0
        for (i in 0 until coords.size - 1) {
            try {
                val from = coords[i]
                val to = coords[i + 1]
                val url =
                    "https://routing.openrailrouting.org/route?point=${from.first},${from.second}&point=${to.first},${to.second}&profile=all_tracks&points_encoded=false"
                ApiClient.http.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
                    if (res.isSuccessful) {
                        val json = JSONObject(res.body?.string().orEmpty())
                        val paths = json.optJSONArray("paths")
                        if (paths != null && paths.length() > 0) {
                            totalMeters += paths.getJSONObject(0).optDouble("distance", 0.0)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return totalMeters / 1000.0
    }

    private fun haversineKm(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val radiusKm = 6371.0
        val lat1 = Math.toRadians(p1.first)
        val lat2 = Math.toRadians(p2.first)
        val dLat = Math.toRadians(p2.first - p1.first)
        val dLon = Math.toRadians(p2.second - p1.second)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return radiusKm * c
    }

    private fun formatKm(value: Double): String =
        String.format(java.util.Locale.US, "%.3f", value)

    private fun logD(tag: String, msg: String) {
        if (!BuildConfig.DEBUG) return
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
        }
    }

    private fun logE(tag: String, msg: String, t: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        try {
            if (t != null) {
                Log.e(tag, msg, t)
            } else {
                Log.e(tag, msg)
            }
        } catch (_: Throwable) {
        }
    }

    private fun logW(tag: String, msg: String) {
        if (!BuildConfig.DEBUG) return
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {
        }
    }
}
