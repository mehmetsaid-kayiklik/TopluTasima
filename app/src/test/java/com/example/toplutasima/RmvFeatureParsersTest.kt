package com.example.toplutasima

import com.example.toplutasima.model.LocationOptionKind
import com.example.toplutasima.network.RmvFeatureParsers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RmvFeatureParsersTest {
    @Test
    fun `alert line matcher handles spaced and prefixed lines`() {
        assertTrue(RmvFeatureParsers.lineMatchesAlert("S5", "Delay on S 5 toward Frankfurt"))
        assertTrue(RmvFeatureParsers.lineMatchesAlert("36", "BUS 36 is diverted"))
        assertTrue(RmvFeatureParsers.lineMatchesAlert("Tram 7", "TRAM7 has works"))
    }

    @Test
    fun `location parser extracts stop and address options`() {
        val json = Json.parseToJsonElement(
            """
            {
              "stopLocationOrCoordLocation": [
                { "StopLocation": { "id": "stop-1", "name": "Darmstadt Hbf", "lat": 49.872, "lon": 8.632 } },
                { "CoordLocation": { "id": "adr-1", "name": "Luisenplatz 1", "type": "ADR", "lat": 49.872, "lon": 8.651 } }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val options = RmvFeatureParsers.parseLocationOptions(json)

        assertEquals(2, options.size)
        assertEquals(LocationOptionKind.STOP, options[0].kind)
        assertEquals(LocationOptionKind.ADDRESS, options[1].kind)
    }

    @Test
    fun `journey match parser extracts candidate line`() {
        val json = Json.parseToJsonElement(
            """
            {
              "Journey": {
                "id": "j1",
                "name": "S 5",
                "direction": "Frankfurt",
                "confidence": 88,
                "Origin": { "name": "A" },
                "Destination": { "name": "B" }
              }
            }
            """.trimIndent()
        ).jsonObject

        val candidates = RmvFeatureParsers.parseJourneyMatchCandidates(json, "req")

        assertEquals(1, candidates.size)
        assertEquals("S5", candidates.first().line)
        assertEquals(88, candidates.first().confidence)
    }
}
