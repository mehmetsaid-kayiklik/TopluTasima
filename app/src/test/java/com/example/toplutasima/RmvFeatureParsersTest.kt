package com.example.toplutasima

import com.example.toplutasima.model.LocationOptionKind
import com.example.toplutasima.network.RmvFeatureParsers
import com.example.toplutasima.network.rmv.RmvSegmentParser
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
        assertTrue(RmvFeatureParsers.lineMatchesAlert("OF-85", "OF-85: Rodgau-Weiskirchen wird nicht bedient"))
        assertFalse(RmvFeatureParsers.lineMatchesAlert("85", "Frankfurt Flughafen @X=8579062@Y=50053223"))
        assertFalse(RmvFeatureParsers.lineMatchesAlert("85", "Line 185 is delayed"))
        assertFalse(RmvFeatureParsers.lineMatchesAlert("85", "OF-85: Rodgau-Weiskirchen wird nicht bedient"))
        assertFalse(RmvFeatureParsers.lineMatchesAlert("85", "Linie 261: Umleitung in Kronberg"))
        assertFalse(RmvFeatureParsers.lineMatchesAlert("85", "Linie 42: Frankfurt-Bergen-Enkheim"))
        assertFalse(RmvFeatureParsers.lineMatchesAlert("85", "Die Linien M46 und 52 sind betroffen"))
        assertFalse(RmvFeatureParsers.lineMatchesAlert("15", "15 dakika gecikme"))
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
    fun `transit alert parser rejects compound and unrelated line messages`() {
        val json = Json.parseToJsonElement(
            """
            {
              "HimMessage": [
                {
                  "id": "koenigstein-85",
                  "head": "Linie 85: Königstein - Falkenstein",
                  "text": "Die Linie 85 fährt vorübergehend eine Umleitung."
                },
                {
                  "id": "rodgau-of-85",
                  "head": "OF-85: Rodgau-Weiskirchen",
                  "text": "Die Linie OF-85 wird umgeleitet."
                },
                {
                  "id": "kronberg-261",
                  "head": "Linie 261: Kronberg",
                  "text": "Haltestellen entfallen."
                },
                {
                  "id": "frankfurt-lines",
                  "head": "Frankfurt: Linien 42, M46 und 52",
                  "text": "Änderungen in Bergen-Enkheim."
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val alerts = RmvFeatureParsers.parseTransitAlerts(json, "85")

        assertEquals(listOf("koenigstein-85"), alerts.map { it.id })
    }

    @Test
    fun `journey alert parser keeps only attached messages with exact refs`() {
        val json = Json.parseToJsonElement(
            """
            {
              "JourneyDetail": {
                "Messages": {
                  "Message": [
                    {
                      "id": "attached-85",
                      "head": "Linie 85: Umleitung Königstein - Falkenstein",
                      "text": "Die Haltestelle Königstein Stadtmitte entfällt.",
                      "lineId": "vht:85:koenigstein",
                      "operatorCode": "VHT"
                    },
                    {
                      "id": "wrong-line-ref",
                      "head": "Linie 85: Rodgau-Weiskirchen",
                      "lineId": "kvgof:of-85:rodgau",
                      "operatorCode": "KVG-OF"
                    },
                    {
                      "id": "attached-without-product-metadata",
                      "head": "Kurzfristige Änderung auf dieser Fahrt",
                      "text": "Bitte beachten Sie die Ansage im Fahrzeug."
                    }
                  ]
                }
              },
              "HimMessages": {
                "HimMessage": [
                  {
                    "id": "unattached-frankfurt",
                    "head": "Frankfurt: Linien M46 und 52",
                    "lineId": "traffiQ:m46"
                  }
                ]
              }
            }
            """.trimIndent()
        ).jsonObject
        val decisions = mutableListOf<Triple<String, Boolean, String>>()

        val alerts = RmvFeatureParsers.parseJourneyTransitAlerts(
            root = json,
            displayLine = "85",
            lineRef = "vht:85:koenigstein",
            operatorRef = "VHT"
        ) { id, included, reason -> decisions += Triple(id, included, reason) }

        assertEquals(
            listOf("attached-85", "attached-without-product-metadata"),
            alerts.map { it.id }
        )
        assertTrue(decisions.any { it.first == "wrong-line-ref" && !it.second && "lineRef mismatch" in it.third })
        assertTrue(decisions.any { it.first == "unattached-frankfurt" && !it.second && "not attached" in it.third })
    }

    @Test
    fun `departure product parser preserves internal line and operator refs`() {
        val product = Json.parseToJsonElement(
            """
            {
              "name": "Bus 85",
              "num": "85",
              "prodCtx": {
                "lineId": "vht:85:koenigstein",
                "operatorCode": "VHT"
              }
            }
            """.trimIndent()
        ).jsonObject

        assertEquals("vht:85:koenigstein", RmvSegmentParser.extractLineRefKtx(product))
        assertEquals("VHT", RmvSegmentParser.extractOperatorRefKtx(product))
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

    @Test
    fun `journey match parser ignores stop and poi names containing digits`() {
        val json = Json.parseToJsonElement(
            """
            {
              "Journey": {
                "id": "j1",
                "name": "S 5",
                "direction": "Frankfurt",
                "confidence": 88,
                "Origin": { "id": "stop-1", "name": "Terminal 2" },
                "Destination": { "id": "stop-2", "name": "Platform 15" }
              },
              "NearbyLocations": {
                "CoordLocation": {
                  "id": "poi-15",
                  "name": "Museum 15",
                  "type": "POI",
                  "confidence": 99
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val candidates = RmvFeatureParsers.parseJourneyMatchCandidates(json, "req")

        assertEquals(listOf("S5"), candidates.map { it.line })
    }

    @Test
    fun `transit alert parser ignores standalone duration numbers`() {
        val json = Json.parseToJsonElement(
            """
            {
              "HimMessage": {
                "id": "delay-duration",
                "head": "15 dakika gecikme",
                "text": "Tahmini gecikme sÃ¼resi 15 dakika."
              }
            }
            """.trimIndent()
        ).jsonObject

        assertTrue(RmvFeatureParsers.parseTransitAlerts(json, "15").isEmpty())
    }
}
