package com.example.toplutasima.network.rmv

import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.model.VehicleType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject

object RmvSegmentParser {
    fun parseTripObject(trip0: JSONObject): TripResult? {
        val legs = try {
            legsArray(trip0)
        } catch (_: Exception) {
            return null
        }
        val segments = mutableListOf<Segment>()
        var overallDep = ""
        var overallArr = ""
        for (i in 0 until legs.length()) {
            val leg = legs.getJSONObject(i)
            val type = leg.optString("type", "").trim()
            if (type.equals("WALK", ignoreCase = true)) continue
            val origin = leg.optJSONObject("Origin")
            val dest = leg.optJSONObject("Destination")
            val dep = origin?.optString("time", "")?.take(5).orEmpty()
            val arr = dest?.optString("time", "")?.take(5).orEmpty()
            val fromStop = origin?.optString("name", "").orEmpty()
            val toStop = dest?.optString("name", "").orEmpty()
            val fromStopId = origin?.optString("id", "").orEmpty()
            val toStopId = dest?.optString("id", "").orEmpty()
            val rawLine = extractPublicLineCode(leg)
            if (rawLine.isBlank()) continue
            val direction = extractDirection(leg)
            val typeTr = mapTypeTr(leg, rawLine)
            val line = if (typeTr == VehicleType.STRASSENBAHN.key && rawLine.startsWith("TRAM")) {
                rawLine.removePrefix("TRAM")
            } else {
                rawLine
            }
            if (overallDep.isBlank() && dep.isNotBlank()) overallDep = dep
            if (arr.isNotBlank()) overallArr = arr
            val journeyRef = leg.optJSONObject("JourneyDetailRef")?.optString("ref", "").orEmpty()
            segments += Segment(
                typeTr,
                line,
                direction,
                fromStop,
                toStop,
                dep,
                arr,
                journeyRef = journeyRef,
                fromStopId = fromStopId,
                toStopId = toStopId
            )
        }
        if (segments.isEmpty()) return null
        return TripResult(
            segments,
            overallDep,
            overallArr,
            RmvTimeUtils.diffMinutesFlexible(overallDep, overallArr)
        )
    }

    fun safeProductKtx(el: JsonElement?): JsonObject? {
        return when (el) {
            is JsonObject -> el
            is JsonArray -> el.firstOrNull() as? JsonObject
            else -> null
        }
    }

    fun legsArrayKtx(trip: JsonObject): JsonArray {
        fun asArr(el: JsonElement): JsonArray = when (el) {
            is JsonArray -> el
            is JsonObject -> JsonArray(listOf(el))
            else -> throw IllegalStateException("Leg format")
        }
        trip["Leg"]?.let { return asArr(it) }
        trip["Legs"]?.jsonObject?.get("Leg")?.let { return asArr(it) }
        trip["LegList"]?.jsonObject?.get("Leg")?.let { return asArr(it) }
        trip["legList"]?.jsonObject?.get("leg")?.let { return asArr(it) }
        throw IllegalStateException("Leg yok")
    }

    fun extractPublicLineCodeKtx(leg: JsonObject): String {
        val candidates = mutableListOf<String>()
        candidates += leg["name"]?.jsonPrimitive?.content.orEmpty()
        safeProductKtx(leg["Product"])?.let { product ->
            candidates += product["name"]?.jsonPrimitive?.content.orEmpty()
            candidates += product["num"]?.jsonPrimitive?.content.orEmpty()
        }
        return extractPublicLineCode(candidates)
    }

    fun extractDirectionKtx(leg: JsonObject): String {
        val directDirection = leg["direction"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (directDirection.isNotBlank()) return directDirection
        return leg["Transportation"]?.jsonObject?.get("direction")?.jsonPrimitive?.content?.trim().orEmpty()
    }

    fun mapTypeTrKtx(product: JsonObject?, line: String): String {
        val inferred = inferVehicleTypeFromLine(line)
        if (inferred != null) return inferred
        val catOut = product?.get("catOut")?.jsonPrimitive?.content?.trim().orEmpty()
        val typeStr = product?.get("type")?.jsonPrimitive?.content?.trim().orEmpty()
        val cls = product?.get("cls")?.jsonPrimitive?.intOrNull ?: 0
        if (isTramProduct(catOut, typeStr, cls)) return VehicleType.STRASSENBAHN.key
        return VehicleType.BUS.key
    }

    fun normalizeLineCode(raw: String): String {
        var value = raw.trim().uppercase().replace(" ", "")
        for (prefix in LINE_PREFIXES_TO_STRIP) {
            if (value.startsWith(prefix)) value = value.removePrefix(prefix)
        }
        return value
    }

    private fun mapTypeTr(leg: JSONObject, line: String): String {
        val inferred = inferVehicleTypeFromLine(line)
        if (inferred != null) return inferred
        val product = leg.optJSONObject("Product")
        val catOut = product?.optString("catOut", "")?.trim().orEmpty()
        val typeStr = product?.optString("type", "")?.trim().orEmpty()
        val cls = product?.optInt("cls", 0) ?: 0
        if (isTramProduct(catOut, typeStr, cls)) return VehicleType.STRASSENBAHN.key
        return VehicleType.BUS.key
    }

    private fun extractPublicLineCode(leg: JSONObject): String {
        val candidates = mutableListOf<String>()
        candidates += leg.optString("name", "")
        leg.optJSONObject("Product")?.let { product ->
            candidates += product.optString("name", "")
            candidates += product.optString("num", "")
        }
        return extractPublicLineCode(candidates)
    }

    private fun extractPublicLineCode(candidates: List<String>): String {
        for (raw in candidates) {
            val match = LINE_CODE_REGEX.find(raw.trim())?.value ?: continue
            var cleaned = match.replace(" ", "").uppercase()
            if (cleaned.startsWith("BUS")) cleaned = cleaned.removePrefix("BUS")
            return cleaned
        }
        return ""
    }

    private fun extractDirection(leg: JSONObject): String {
        val directDirection = leg.optString("direction", "").trim()
        if (directDirection.isNotBlank()) return directDirection
        return leg.optJSONObject("Transportation")?.optString("direction", "")?.trim().orEmpty()
    }

    private fun legsArray(trip0: JSONObject): JSONArray {
        fun asArray(any: Any): JSONArray = when (any) {
            is JSONArray -> any
            is JSONObject -> JSONArray().put(any)
            else -> throw IllegalStateException("Leg format beklenmedik")
        }
        trip0.opt("Leg")?.let { return asArray(it) }
        trip0.optJSONObject("Legs")?.opt("Leg")?.let { return asArray(it) }
        trip0.optJSONObject("LegList")?.opt("Leg")?.let { return asArray(it) }
        trip0.optJSONObject("legList")?.opt("leg")?.let { return asArray(it) }
        throw IllegalStateException("Leg yok")
    }

    private fun inferVehicleTypeFromLine(line: String): String? = when {
        line.startsWith("U") -> VehicleType.UBAHN.key
        line.startsWith("S") -> VehicleType.SBAHN.key
        line.startsWith("RB") || line.startsWith("RE") -> VehicleType.RERB.key
        line.startsWith("ICE") || line.startsWith("IC") ||
            line.startsWith("ECE") || line.startsWith("EC") -> VehicleType.FERNZUG.key
        line.startsWith("STR") || line.startsWith("TRAM") -> VehicleType.STRASSENBAHN.key
        else -> null
    }

    private fun isTramProduct(catOut: String, typeStr: String, cls: Int): Boolean =
        catOut.contains("Tram", ignoreCase = true) ||
            catOut.contains("Stra\u00dfenbahn", ignoreCase = true) ||
            typeStr.contains("Tram", ignoreCase = true) ||
            cls == 16

    private val LINE_CODE_REGEX = Regex(
        """\b([A-Za-z]{1,4}-?\s?\d{1,3}|RB\s?\d{1,3}|RE\s?\d{1,3}|S\s?\d{1,2}|U\s?\d{1,2}|X\s?\d{1,3}|\d{1,3})\b""",
        RegexOption.IGNORE_CASE
    )

    private val LINE_PREFIXES_TO_STRIP = listOf(
        "BUS",
        "TRAM",
        "U-BAHN",
        "S-BAHN",
        "STRA\u1e9eENBAHN",
        "STRASSENBAHN"
    )
}
