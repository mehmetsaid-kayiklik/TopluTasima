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

class RmvDistanceService {
    private val orsApiKey = BuildConfig.ORS_API_KEY

    suspend fun calculateDistanceORS(coords: List<Pair<Double, Double>>): Double {
        if (coords.size < 2) return 0.0
        return withContext(Dispatchers.IO) {
            try {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logE("ORSDiag", "ORS EXCEPTION: ${e.message}")
                0.0
            }
        }
    }

    suspend fun calculateDistanceRail(
        coords: List<Pair<Double, Double>>,
        allStopCoords: List<Pair<Double, Double>> = emptyList(),
        fromIdx: Int = -1,
        toIdx: Int = -1
    ): Double {
        if (coords.size < 2) return 0.0
        return withContext(Dispatchers.IO) {
            val directKm = railRouteWithPairwise(coords)
            if (directKm > 0.0) return@withContext directKm

            if (allStopCoords.size >= 3 && fromIdx >= 0 && toIdx >= 0) {
                val paddings = intArrayOf(2, 4, 8, 15, allStopCoords.size)
                for (pad in paddings) {
                    val extFrom = maxOf(0, fromIdx - pad)
                    val extTo = minOf(allStopCoords.size - 1, toIdx + pad)
                    if (extFrom == fromIdx && extTo == toIdx) continue

                    val pStart = allStopCoords[extFrom]
                    val pEnd = allStopCoords[extTo]

                    logD("RailDist", "Retry endpoints only: pad=$pad extFrom=$extFrom extTo=$extTo")
                    val polyline = railRoutePolyline(pStart, pEnd)
                    if (polyline.size > 1) {
                        val actualStart = allStopCoords[fromIdx]
                        val actualEnd = allStopCoords[toIdx]
                        val distA = closestVertexDistance(polyline, actualStart)
                        val distB = closestVertexDistance(polyline, actualEnd)
                        val segDist = kotlin.math.abs(distB - distA)
                        logD(
                            "RailDist",
                            "Endpoints succeeded. segDist=${String.format(java.util.Locale.US, "%.2f", segDist)}"
                        )
                        if (segDist > 0.01) return@withContext segDist
                    }
                }
            }
            0.0
        }
    }

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

    private fun railRoutePolyline(
        pStart: Pair<Double, Double>,
        pEnd: Pair<Double, Double>
    ): List<Pair<Double, Double>> {
        return try {
            val url =
                "https://routing.openrailrouting.org/route?point=${pStart.first},${pStart.second}&point=${pEnd.first},${pEnd.second}&profile=all_tracks&points_encoded=false"
            val req = Request.Builder().url(url).get().build()
            ApiClient.http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use emptyList()
                val body = res.body?.string().orEmpty()
                val json = JSONObject(body)
                val paths = json.optJSONArray("paths")
                if (paths != null && paths.length() > 0) {
                    val coords = paths.getJSONObject(0).optJSONObject("points")?.optJSONArray("coordinates")
                    if (coords != null) {
                        val poly = mutableListOf<Pair<Double, Double>>()
                        for (i in 0 until coords.length()) {
                            val pt = coords.optJSONArray(i)
                            if (pt != null && pt.length() >= 2) {
                                poly.add(Pair(pt.optDouble(1), pt.optDouble(0)))
                            }
                        }
                        return@use poly
                    }
                }
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun closestVertexDistance(poly: List<Pair<Double, Double>>, point: Pair<Double, Double>): Double {
        if (poly.isEmpty()) return 0.0
        var minIdx = 0
        var minDist = Double.MAX_VALUE
        for (i in poly.indices) {
            val distance = haversineKm(point, poly[i])
            if (distance < minDist) {
                minDist = distance
                minIdx = i
            }
        }
        var distance = 0.0
        for (i in 0 until minIdx) {
            distance += haversineKm(poly[i], poly[i + 1])
        }
        return distance
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

    private fun logD(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    private fun logE(tag: String, msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        }
    }
}
