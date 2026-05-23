package com.example.toplutasima.network.rmv

import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.model.TransitAlert
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.network.ApiErrors
import com.example.toplutasima.network.ApiRequestException
import com.example.toplutasima.network.RmvEndpointAvailability
import com.example.toplutasima.network.RmvFeatureParsers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

class RmvJourneyService {
    suspend fun fetchTransitAlerts(line: String, date: String = ""): List<TransitAlert> =
        withContext(Dispatchers.IO) {
            val endpoint = "himsearch"
            if (line.isBlank() || RmvEndpointAvailability.isUnavailable(endpoint)) {
                return@withContext emptyList()
            }
            try {
                val json = rmvCall(endpoint) { requestId ->
                    rmvApi.getTransitAlerts(line = line, date = date.ifBlank { null }, requestId = requestId)
                }
                RmvFeatureParsers.parseTransitAlerts(json, line)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiRequestException) {
                logE("RmvApi", "fetchTransitAlerts error: ${e.message}", e)
                RmvEndpointAvailability.markFromException(endpoint, e)
                emptyList()
            } catch (e: Exception) {
                logE("RmvApi", "fetchTransitAlerts error: ${e.message}", e)
                emptyList()
            }
        }

    suspend fun matchJourneyTrack(
        points: List<Pair<Double, Double>>,
        date: String,
        time: String
    ): List<JourneyMatchCandidate> = withContext(Dispatchers.IO) {
        val endpoint = "journeyTrackMatch"
        if (points.size < 2 || RmvEndpointAvailability.isUnavailable(endpoint)) {
            return@withContext emptyList()
        }
        val requestId = ApiErrors.newRequestId()
        try {
            val body = buildJsonObject {
                put("date", date)
                put("time", time)
                put("points", buildJsonArray {
                    points.forEach { (lat, lon) ->
                        add(buildJsonObject {
                            put("lat", lat)
                            put("lon", lon)
                        })
                    }
                })
            }
            logD("RmvRequest", "$endpoint requestId=$requestId points=${points.size}")
            val json = rmvApi.matchJourneyTrack(body = body, requestId = requestId)
            RmvEndpointAvailability.markSupported(endpoint)
            RmvFeatureParsers.parseJourneyMatchCandidates(json, requestId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val apiError = ApiErrors.fromThrowable("RMV", endpoint, requestId, e)
            RmvEndpointAvailability.markFromException(endpoint, apiError)
            logE("RmvApi", "matchJourneyTrack error: ${apiError.message}", apiError)
            emptyList()
        }
    }

    suspend fun fetchDepartureBoard(
        stopId: String,
        destId: String,
        date: String,
        time: String
    ): List<Departure> = withContext(Dispatchers.IO) {
        val validLineDirections = mutableMapOf<String, MutableSet<String>>()
        if (destId.isNotBlank()) {
            try {
                val tripJson = rmvCall("trip.departure-filter") { requestId ->
                    rmvApi.getTrip(
                        originId = stopId,
                        destId = destId,
                        date = date,
                        time = time,
                        numF = 6,
                        requestId = requestId
                    )
                }
                val trips = when (val trip = tripJson["Trip"]) {
                    is JsonArray -> trip
                    is JsonObject -> JsonArray(listOf(trip))
                    else -> JsonArray(emptyList())
                }
                trips.forEach { tripElement ->
                    val trip = tripElement.jsonObject
                    val legs = try {
                        RmvSegmentParser.legsArrayKtx(trip)
                    } catch (_: Exception) {
                        return@forEach
                    }
                    for (legElement in legs) {
                        val leg = legElement.jsonObject
                        val type = leg["type"]?.jsonPrimitive?.content?.trim() ?: ""
                        if (type.equals("WALK", ignoreCase = true)) continue
                        val code = RmvSegmentParser.extractPublicLineCodeKtx(leg)
                        if (code.isNotBlank()) {
                            val normCode = RmvSegmentParser.normalizeLineCode(code)
                            val dir = RmvSegmentParser.extractDirectionKtx(leg)
                            validLineDirections.getOrPut(normCode) { mutableSetOf() }.add(dir)
                        }
                        break
                    }
                }
                logD("DepartureBoard", "Valid line+direction combos: $validLineDirections")
            } catch (e: ApiRequestException) {
                throw e
            } catch (_: Exception) {
            }
        }

        val minTime = try {
            LocalTime.parse(time.trim().take(5))
        } catch (_: Exception) {
            null
        }

        val response = try {
            rmvCall("departureBoard") { requestId ->
                rmvApi.getDepartures(
                    stopId = stopId,
                    date = date,
                    time = time,
                    requestId = requestId
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiRequestException) {
            logE("RmvApi", "fetchDepartureBoard error: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            logE("RmvApi", "fetchDepartureBoard error: ${e.message}")
            return@withContext emptyList()
        }

        val rawDep = response.departure ?: return@withContext emptyList()
        val depArray: JsonArray = when (rawDep) {
            is JsonArray -> rawDep
            is JsonObject -> JsonArray(listOf(rawDep))
            else -> return@withContext emptyList()
        }

        val departures = mutableListOf<Departure>()
        val lineRx = Regex(
            """\b([A-Za-z]{1,4}-?\s?\d{1,3}|RB\s?\d{1,3}|RE\s?\d{1,3}|S\s?\d{1,2}|U\s?\d{1,2}|X\s?\d{1,3}|\d{1,3})\b""",
            RegexOption.IGNORE_CASE
        )

        for (depElement in depArray) {
            val dep = depElement.jsonObject
            val depTime = RmvTimeUtils.normalizeRmvClock(dep["time"]?.jsonPrimitive?.content.orEmpty())
            val realtime = RmvTimeUtils.normalizeRmvClock(
                dep["rtTime"]?.jsonPrimitive?.content
                    ?: dep["realTime"]?.jsonPrimitive?.content
                    ?: dep["estimatedTime"]?.jsonPrimitive?.content
                    ?: ""
            )
            val realtimeDate = dep["rtDate"]?.jsonPrimitive?.content?.trim().orEmpty()
            val cancelled = dep["cancelled"]?.jsonPrimitive?.content?.equals("true", ignoreCase = true) == true
            val direction = dep["direction"]?.jsonPrimitive?.content?.trim().orEmpty()
            val track = dep["track"]?.jsonPrimitive?.content?.trim().orEmpty()
            val displayTime = realtime.ifBlank { depTime }

            if (minTime != null && displayTime.length >= 5) {
                try {
                    if (LocalTime.parse(displayTime.take(5)).isBefore(minTime)) continue
                } catch (_: Exception) {
                }
            }

            val product = RmvSegmentParser.safeProductKtx(dep["Product"] ?: dep["ProductAtStop"])
            val rawName = product?.get("name")?.jsonPrimitive?.content?.trim()
                ?: dep["name"]?.jsonPrimitive?.content?.trim().orEmpty()
            val rawNum = product?.get("num")?.jsonPrimitive?.content?.trim().orEmpty()

            var line = ""
            for (candidate in listOf(rawName, rawNum)) {
                val match = lineRx.find(candidate ?: "")?.value ?: continue
                line = match.replace(" ", "").uppercase()
                if (line.startsWith("BUS")) line = line.removePrefix("BUS")
                break
            }
            if (line.isBlank() && rawName?.isNotBlank() == true) line = rawName
            if (line.isBlank()) continue

            if (validLineDirections.isNotEmpty()) {
                val normalizedLine = RmvSegmentParser.normalizeLineCode(line)
                val validDirs = validLineDirections[normalizedLine]
                if (validDirs == null) {
                    logD("DepartureBoard", "SKIP '$normalizedLine' dir='$direction' - line not in valid set")
                    continue
                }
                if (validDirs.isNotEmpty() && direction.isNotBlank()) {
                    val dirMatch = validDirs.any { validDir ->
                        validDir.isBlank() ||
                            direction.contains(validDir, ignoreCase = true) ||
                            validDir.contains(direction, ignoreCase = true)
                    }
                    if (!dirMatch) {
                        logD("DepartureBoard", "SKIP '$normalizedLine' dir='$direction' - direction mismatch")
                        continue
                    }
                }
                logD("DepartureBoard", "KEEP '$normalizedLine' dir='$direction'")
            }

            val typeTr = RmvSegmentParser.mapTypeTrKtx(product, line)
            val cleanLine = if (typeTr == VehicleType.STRASSENBAHN.key && line.startsWith("TRAM")) {
                line.removePrefix("TRAM")
            } else {
                line
            }

            val journeyDetailRef = dep["JourneyDetailRef"]?.jsonObject?.get("ref")
                ?.jsonPrimitive?.content.orEmpty()

            departures += Departure(
                cleanLine,
                direction,
                depTime,
                track,
                typeTr,
                journeyDetailRef,
                realtime,
                realtimeDate,
                cancelled
            )
        }

        departures.forEach {
            logD("DEBUG_BOARD", "time: ${it.time}, rt=${it.realtime}, ref: ${it.journeyDetailRef}")
        }
        departures
    }

    suspend fun fetchTripBasic(
        originId: String,
        destId: String,
        date: String,
        time: String,
        preferredLine: String = ""
    ): TripResult = withContext(Dispatchers.IO) {
        val numTrips = if (preferredLine.isNotBlank()) 6 else 1
        val body = rmvCall("trip") { requestId ->
            rmvApi.getTrip(
                originId = originId,
                destId = destId,
                date = date,
                time = time,
                numF = numTrips,
                requestId = requestId
            )
        }.toString()
        val json = JSONObject(body)
        val trips = when (val trip = json.opt("Trip")) {
            is JSONArray -> trip
            is JSONObject -> JSONArray().put(trip)
            else -> throw IllegalStateException("Trip yok")
        }
        if (trips.length() == 0) throw IllegalStateException("Trip boş")
        val normalizedPref = RmvSegmentParser.normalizeLineCode(preferredLine)
        var bestResult: TripResult? = null
        var fallbackResult: TripResult? = null
        for (index in 0 until trips.length()) {
            val trip = trips.getJSONObject(index)
            val tripResult = RmvSegmentParser.parseTripObject(trip) ?: continue
            if (fallbackResult == null) fallbackResult = tripResult
            if (normalizedPref.isNotBlank()) {
                val firstLine = RmvSegmentParser.normalizeLineCode(tripResult.segments.firstOrNull()?.line.orEmpty())
                if (firstLine == normalizedPref) {
                    bestResult = tripResult
                    break
                }
            } else {
                bestResult = tripResult
                break
            }
        }
        bestResult ?: fallbackResult ?: throw IllegalStateException("Toplu taşıma segment bulunamadı")
    }

    private suspend fun <T> rmvCall(endpoint: String, block: suspend (String) -> T): T {
        val requestId = ApiErrors.newRequestId()
        logD("RmvRequest", "$endpoint requestId=$requestId")
        return try {
            val result = block(requestId)
            RmvEndpointAvailability.markSupported(endpoint)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val apiError = ApiErrors.fromThrowable("RMV", endpoint, requestId, e)
            RmvEndpointAvailability.markFromException(endpoint, apiError)
            throw apiError
        }
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
