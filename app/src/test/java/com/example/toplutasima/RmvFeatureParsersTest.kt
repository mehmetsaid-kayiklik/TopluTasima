package com.example.toplutasima

import com.example.toplutasima.model.LocationOptionKind
import com.example.toplutasima.network.RmvFeatureParsers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RmvFeatureParsersTest {
    @Test
    fun `alert line matcher handles spaced and prefixed lines`() {
        assertTrue(RmvFeatureParsers.lineMatchesAlert("S5", "Delay on S 5 toward Frankfurt"))
        assertTrue(RmvFeatureParsers.lineMatchesAlert("36", "BUS 36 is diverted"))
        assertTrue(RmvFeatureParsers.lineMatchesAlert("Tram 7", "TRAM7 has works"))
        assertTrue(RmvFeatureParsers.lineMatchesAlert("85", "Linie 85 faehrt eine Umleitung"))
        assertFalse(RmvFeatureParsers.lineMatchesAlert("85", "Frankfurt Flughafen @X=8579062@Y=50053223"))
        assertFalse(RmvFeatureParsers.lineMatchesAlert("85", "Line 185 is delayed"))
    }

    @Test
    fun `transit alert parser keeps readable selected line messages`() {
        val json = Json.parseToJsonElement(
            """
            {
              "HimMessage": [
                {
                  "id": "bus-85",
                  "head": "Linie 85: Umleitung Richtung Flughafen",
                  "text": "Bus 85 faehrt zwischen Terminal 1 und Tor 3 eine Umleitung."
                },
                {
                  "id": "tram-generic",
                  "head": "Darmstadt und Umland: Trams / Busse - Warnstreik",
                  "text": "#FFFFFF #EF7D00 prod_tram Tram 1 de:rmv:00000097"
                },
                {
                  "id": "stop-raw",
                  "name": "de:06412:2931 Frankfurt Flughafen Tor 3 A=1@X=8579062@Y=50053223"
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val alerts = RmvFeatureParsers.parseTransitAlerts(json, "85")

        assertEquals(1, alerts.size)
        assertEquals("Linie 85: Umleitung Richtung Flughafen", alerts.first().title)
        assertFalse(alerts.first().detail.contains("@X="))
        assertFalse(alerts.first().detail.contains("prod_tram"))
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
    fun `location parser converts HAFAS xy microdegree coordinates`() {
        val json = Json.parseToJsonElement(
            """
            {
              "StopLocation": {
                "id": "stop-1",
                "name": "Frankfurt Westbahnhof",
                "x": 8640884,
                "y": 50119568
              }
            }
            """.trimIndent()
        ).jsonObject

        val option = RmvFeatureParsers.parseLocationOptions(json).first()

        assertEquals(50.119568, option.lat ?: 0.0, 0.000001)
        assertEquals(8.640884, option.lon ?: 0.0, 0.000001)
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
