package com.example.toplutasima.network.rmv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Location search (location.name) ─────────────────────────────────────────

@Serializable
data class RmvStopLocation(
    val id: String = "",
    val name: String = "",
    val dist: Int = 0          // distance in meters (only from location.nearbystops)
)

@Serializable
data class RmvCoordWrapper(
    @SerialName("StopLocation") val stopLocation: RmvStopLocation? = null
)

/**
 * RMV may return "StopLocation" as either a single object OR an array.
 * We capture it as a raw JsonElement and handle both cases in the mapper.
 */
@Serializable
data class RmvLocationResponse(
    @SerialName("StopLocation") val stopLocation: JsonElement? = null,
    @SerialName("stopLocationOrCoordLocation") val coordLocations: List<RmvCoordWrapper>? = null
)

// ── Departure board (departureBoard) ─────────────────────────────────────────

@Serializable
data class RmvProduct(
    val name: String = "",
    val num: String = "",
    val catOut: String = "",
    val type: String = "",
    val cls: Int = 0
)

@Serializable
data class RmvJourneyDetailRef(val ref: String = "")

@Serializable
data class RmvDeparture(
    val time: String = "",
    val direction: String = "",
    val track: String = "",
    val name: String = "",
    @SerialName("Product") val product: RmvProduct? = null,
    @SerialName("ProductAtStop") val productAtStop: RmvProduct? = null,
    @SerialName("JourneyDetailRef") val journeyDetailRef: RmvJourneyDetailRef? = null
)

/**
 * "Departure" field can be a single object or array — captured as JsonElement.
 */
@Serializable
data class RmvDepartureBoardResponse(
    @SerialName("Departure") val departure: JsonElement? = null
)
