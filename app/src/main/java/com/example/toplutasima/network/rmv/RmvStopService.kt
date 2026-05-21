package com.example.toplutasima.network.rmv

import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.model.LocationOption
import com.example.toplutasima.model.LocationOptionKind
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.network.ApiErrors
import com.example.toplutasima.network.ApiRequestException
import com.example.toplutasima.network.RmvApiService
import com.example.toplutasima.network.RmvEndpointAvailability
import com.example.toplutasima.network.RmvFeatureParsers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RmvStopService {
    suspend fun searchStopOptions(input: String, max: Int = 3): List<StopOption> {
        if (input.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val response = rmvCall("location.name") { requestId ->
                    rmvApi.searchStops(
                        input = input,
                        maxNo = max,
                        requestId = requestId
                    )
                }
                val stops = mutableListOf<StopOption>()

                fun addRmvStop(stop: RmvStopLocation) {
                    if (stop.id.isNotBlank() && stop.name.isNotBlank()) {
                        stops += StopOption(stop.id, stop.name)
                    }
                }

                response.stopLocation?.let { element ->
                    when (element) {
                        is JsonArray -> element.take(max).forEach { item ->
                            val obj = item.jsonObject
                            addRmvStop(
                                RmvStopLocation(
                                    id = obj["id"]?.jsonPrimitive?.content.orEmpty().trim(),
                                    name = obj["name"]?.jsonPrimitive?.content.orEmpty().trim()
                                )
                            )
                        }
                        is JsonObject -> addRmvStop(
                            RmvStopLocation(
                                id = element["id"]?.jsonPrimitive?.content.orEmpty().trim(),
                                name = element["name"]?.jsonPrimitive?.content.orEmpty().trim()
                            )
                        )
                        else -> {}
                    }
                    return@withContext stops
                }

                response.coordLocations?.take(max)?.forEach { wrapper ->
                    wrapper.stopLocation?.let { addRmvStop(it) }
                }
                stops
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiRequestException) {
                logE("RmvApi", "searchStopOptions error: ${e.message}", e)
                throw e
            } catch (e: Exception) {
                logE("RmvApi", "searchStopOptions error: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun searchNearbyStops(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 500,
        max: Int = 8
    ): List<RmvApiService.NearbyStop> {
        return withContext(Dispatchers.IO) {
            try {
                val json = rmvCall("location.nearbystops") { requestId ->
                    rmvApi.getNearbyStops(
                        lat = lat,
                        lon = lon,
                        maxNo = max,
                        radiusMeters = radiusMeters,
                        requestId = requestId
                    )
                }
                val stops = mutableListOf<RmvApiService.NearbyStop>()

                fun extractStop(obj: JsonObject) {
                    val id = obj["id"]?.jsonPrimitive?.content.orEmpty().trim()
                    val name = obj["name"]?.jsonPrimitive?.content.orEmpty().trim()
                    val distance = obj["dist"]?.jsonPrimitive?.intOrNull ?: 0
                    if (id.isNotBlank() && name.isNotBlank()) {
                        stops += RmvApiService.NearbyStop(id, name, distance)
                    }
                }

                val wrapperElement = json["stopLocationOrCoordLocation"]
                if (wrapperElement != null) {
                    val wrappers = when (wrapperElement) {
                        is JsonArray -> wrapperElement.mapNotNull { it as? JsonObject }
                        is JsonObject -> listOf(wrapperElement)
                        else -> emptyList()
                    }
                    wrappers.take(max).forEach { wrapper ->
                        val stopLoc = wrapper["StopLocation"]
                        if (stopLoc is JsonObject) extractStop(stopLoc)
                    }
                }

                if (stops.isEmpty()) {
                    val directStops = json["StopLocation"]
                    if (directStops != null) {
                        when (directStops) {
                            is JsonArray -> directStops.take(max).forEach { item ->
                                if (item is JsonObject) extractStop(item)
                            }
                            is JsonObject -> extractStop(directStops)
                            else -> {}
                        }
                    }
                }

                logD("RmvApi", "searchNearbyStops: found ${stops.size} stops")
                stops.sortedBy { it.distanceMeters }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiRequestException) {
                logE("RmvApi", "searchNearbyStops error: ${e.message}", e)
                throw e
            } catch (e: Exception) {
                logE("RmvApi", "searchNearbyStops error: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun searchLocationOptions(input: String, max: Int = 8): List<LocationOption> {
        if (input.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val stopFallback = runCatching {
                searchStopOptions(input, max).map {
                    LocationOption(
                        it.id,
                        it.name,
                        LocationOptionKind.STOP,
                        it.lat,
                        it.lon,
                        it.resolvedStopId,
                        it.resolvedStopName
                    )
                }
            }.getOrDefault(emptyList())

            val endpoint = "location.search"
            if (RmvEndpointAvailability.isUnavailable(endpoint)) return@withContext stopFallback

            try {
                val searchJson = rmvCall(endpoint) { requestId ->
                    rmvApi.searchLocations(input = input, maxNo = max, requestId = requestId)
                }
                val addressJson = runCatching {
                    rmvCall("location.addresslookup") { requestId ->
                        rmvApi.lookupAddress(input = input, maxNo = max, requestId = requestId)
                    }
                }.getOrNull()
                val parsed = (RmvFeatureParsers.parseLocationOptions(searchJson) +
                    (addressJson?.let { RmvFeatureParsers.parseLocationOptions(it) } ?: emptyList()))
                    .distinctBy { "${it.kind}:${it.id}:${it.name}" }
                    .take(max)

                val resolved = parsed.map { option ->
                    if (option.kind == LocationOptionKind.STOP || option.resolvedStopId.isNotBlank()) {
                        option
                    } else {
                        val lat = option.lat
                        val lon = option.lon
                        val nearest = if (lat != null && lon != null) {
                            runCatching {
                                searchNearbyStops(lat, lon, radiusMeters = 700, max = 1).firstOrNull()
                            }.getOrNull()
                        } else {
                            null
                        }
                        if (nearest != null) {
                            option.copy(
                                resolvedStopId = nearest.id,
                                resolvedStopName = nearest.name
                            )
                        } else {
                            option
                        }
                    }
                }.filter { it.kind == LocationOptionKind.STOP || it.resolvedStopId.isNotBlank() }

                (resolved + stopFallback)
                    .distinctBy { it.resolvedStopId.ifBlank { it.id } }
                    .take(max)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiRequestException) {
                logE("RmvApi", "searchLocationOptions error: ${e.message}", e)
                RmvEndpointAvailability.markFromException(endpoint, e)
                stopFallback
            } catch (e: Exception) {
                logE("RmvApi", "searchLocationOptions error: ${e.message}", e)
                stopFallback
            }
        }
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
