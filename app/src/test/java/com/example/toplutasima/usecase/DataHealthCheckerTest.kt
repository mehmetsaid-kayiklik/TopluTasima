package com.example.toplutasima.usecase

import com.example.toplutasima.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataHealthCheckerTest {

    @Test
    fun `analyzeTrips returns no issues for healthy trip`() {
        val issues = DataHealthChecker.analyzeTrips(listOf(healthyTrip("doc-1")))

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `analyzeTrips reports duplicate fingerprints`() {
        val issues = DataHealthChecker.analyzeTrips(
            listOf(
                healthyTrip("doc-1"),
                healthyTrip("doc-2")
            )
        )

        val duplicates = issues.filter { it.type == DataHealthChecker.HealthIssueType.DUPLICATE }
        assertEquals(2, duplicates.size)
        assertTrue(duplicates.all { it.detail == "2x tekrar" })
    }

    @Test
    fun `analyzeTrips reports bad and missing fields`() {
        val issues = DataHealthChecker.analyzeTrips(
            listOf(
                mapOf(
                    "id" to "bad-1",
                    "tur" to VehicleType.BUS.key,
                    "hat" to "X26",
                    "binisDuragi" to "Frankfurt Flughafen Terminal 1",
                    "tarih" to "2026-05-22",
                    "planlananBinis" to "bad",
                    "gercekBinis" to "07:00",
                    "planlananInis" to "07:30",
                    "gercekInis" to "07:30",
                    "gercekYolSuresi" to "10",
                    "gecikme" to "75"
                )
            )
        )

        val issueTypes = issues.map { it.type }.toSet()
        assertTrue(DataHealthChecker.HealthIssueType.MISSING_FIELD in issueTypes)
        assertTrue(DataHealthChecker.HealthIssueType.BAD_DATE in issueTypes)
        assertTrue(DataHealthChecker.HealthIssueType.BAD_TIME in issueTypes)
        assertTrue(DataHealthChecker.HealthIssueType.INCONSISTENT_DURATION in issueTypes)
        assertTrue(DataHealthChecker.HealthIssueType.MISSING_DERIVED in issueTypes)
        assertTrue(DataHealthChecker.HealthIssueType.ABNORMAL_DELAY in issueTypes)
    }

    private fun healthyTrip(docId: String): Map<String, Any> = mapOf(
        "firestoreDocId" to docId,
        "tur" to VehicleType.SBAHN.key,
        "hat" to "S5",
        "binisDuragi" to "Frankfurt Hauptbahnhof",
        "inisDuragi" to "Bad Homburg",
        "tarih" to "22.05.2026",
        "planlananBinis" to "18:22",
        "gercekBinis" to "18:30",
        "planlananInis" to "18:44",
        "gercekInis" to "19:00",
        "gercekYolSuresi" to "30",
        "gecikme" to "8",
        "yearMonth" to "2026-05",
        "sortDate" to "2026-05-22"
    )
}
