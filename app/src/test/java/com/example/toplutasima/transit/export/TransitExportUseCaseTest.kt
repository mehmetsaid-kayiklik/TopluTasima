package com.example.toplutasima.transit.export

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.transit.provenance.TransitFieldFreshness
import com.example.toplutasima.transit.provenance.TransitFieldSource
import com.example.toplutasima.transit.sync.TransitSyncPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TransitExportUseCaseTest {
    private val fixedNow = Instant.parse("2026-07-18T12:34:56Z").toEpochMilli()

    @Test
    fun `CSV escapes delimiters quotes newlines and keeps Turkish and German characters`() =
        runBlocking {
            val record = record(
                id = "id,1",
                line = "S8;S9",
                boarding = "İstanbul \"Merkez\"",
                alighting = "München Hbf",
                note = "ilk satır\nikinci satır"
            )

            val csv = ready(
                useCase().prepare(request(TransitExportFormat.CSV, listOf(record)))
            ).bytes.toString(Charsets.UTF_8)

            assertTrue(csv.startsWith("\uFEFF"))
            assertTrue(csv.contains("\"id,1\""))
            assertTrue(csv.contains("\"S8;S9\""))
            assertTrue(csv.contains("\"İstanbul \"\"Merkez\"\"\""))
            assertTrue(csv.contains("München Hbf"))
            assertTrue(csv.contains("\"ilk satır\nikinci satır\""))
            assertTrue(csv.contains("\r\n"))
        }

    @Test
    fun `CSV neutralizes every spreadsheet formula prefix including leading whitespace`() =
        runBlocking {
            val records = listOf(
                record("equals", line = "=HYPERLINK(\"x\")"),
                record("plus", line = "+SUM(1,2)"),
                record("minus", line = "-1+2"),
                record("at", line = "@cmd"),
                record("space", line = "  =1+1"),
                record("tab", line = "\t+1+1"),
                record("unicode-space", line = "\u00A0@cmd")
            )

            val csv = ready(
                useCase().prepare(request(TransitExportFormat.CSV, records))
            ).bytes.toString(Charsets.UTF_8)

            assertTrue(csv.contains("'=HYPERLINK"))
            assertTrue(csv.contains("'+SUM"))
            assertTrue(csv.contains("'-1+2"))
            assertTrue(csv.contains("'@cmd"))
            assertTrue(csv.contains("'  =1+1"))
            assertTrue(csv.contains("'\t+1+1"))
            assertTrue(csv.contains("'\u00A0@cmd"))
            assertFalse(csv.lines().any { it.startsWith("=HYPERLINK") })
        }

    @Test
    fun `generated negative numeric values remain machine readable`() = runBlocking {
        val csv = ready(
            useCase().prepare(
                request(
                    TransitExportFormat.CSV,
                    listOf(record("early").copy(delayMinutes = -3, distanceKm = 12.5))
                )
            )
        ).bytes.toString(Charsets.UTF_8)

        assertTrue(csv.contains(",-3,12.5,"))
        assertFalse(csv.contains("'-3"))
    }

    @Test
    fun `selected month exports only that month and reports its full range`() = runBlocking {
        val result = ready(
            useCase().prepare(
                request(
                    format = TransitExportFormat.JSON,
                    records = listOf(
                        record("may", date = "2026-05-31"),
                        record("june", date = "2026-06-01")
                    ),
                    scope = TransitExportScope.selectedMonth("2026-05")
                )
            )
        )
        val root = Json.parseToJsonElement(result.bytes.toString(Charsets.UTF_8)).jsonObject

        assertEquals(1, root.getValue("records").jsonArray.size)
        assertEquals("2026-05-01", root.metadata("startDate"))
        assertEquals("2026-05-31", root.metadata("endDate"))
    }

    @Test
    fun `custom date range is inclusive and invalid dates cannot leak into it`() = runBlocking {
        val records = listOf(
            record("before", date = "2026-05-31"),
            record("start", date = "2026-06-01"),
            record("end", date = "2026-06-30"),
            record("after", date = "2026-07-01"),
            record("invalid", date = "01.06.2026")
        )

        val result = ready(
            useCase().prepare(
                request(
                    TransitExportFormat.JSON,
                    records,
                    TransitExportScope.dateRange("2026-06-01", "2026-06-30")
                )
            )
        )
        val ids = Json.parseToJsonElement(result.bytes.toString(Charsets.UTF_8))
            .jsonObject.getValue("records").jsonArray
            .map { it.jsonObject.getValue("localRecordId").jsonPrimitive.content }

        assertEquals(listOf("start", "end"), ids)
    }

    @Test
    fun `filtered scope preserves caller order and excludes nonmatching records`() = runBlocking {
        val result = ready(
            useCase().prepare(
                request(
                    TransitExportFormat.JSON,
                    listOf(
                        record("second", matchesFilter = true),
                        record("hidden", matchesFilter = false),
                        record("first", matchesFilter = true)
                    ),
                    TransitExportScope.filtered(
                        filterDescription = "line=S8",
                        sortDescription = "delay_desc"
                    )
                )
            )
        )
        val root = Json.parseToJsonElement(result.bytes.toString(Charsets.UTF_8)).jsonObject
        val ids = root.getValue("records").jsonArray.map {
            it.jsonObject.getValue("localRecordId").jsonPrimitive.content
        }

        assertEquals(listOf("second", "first"), ids)
        assertEquals("line=S8", root.metadata("filter"))
        assertEquals("delay_desc", root.metadata("sort"))
    }

    @Test
    fun `record and store tombstones are always excluded`() = runBlocking {
        val result = ready(
            useCase().prepare(
                request(
                    format = TransitExportFormat.JSON,
                    records = listOf(
                        record("kept"),
                        record("inline", tombstoned = true),
                        record("store")
                    ),
                    tombstonedRecordIds = setOf("store")
                )
            )
        )
        val json = result.bytes.toString(Charsets.UTF_8)

        assertEquals(1, result.exportedRecordCount)
        assertTrue(json.contains("\"kept\""))
        assertFalse(json.contains("\"inline\""))
        assertFalse(json.contains("\"store\""))
    }

    @Test
    fun `JSON is versioned portable and contains accurate metadata`() = runBlocking {
        val result = ready(
            useCase().prepare(
                request(
                    TransitExportFormat.JSON,
                    listOf(record("one", date = "2026-05-01"), record("two", date = "2026-05-02"))
                )
            )
        )
        val root = Json.parseToJsonElement(result.bytes.toString(Charsets.UTF_8)).jsonObject

        assertEquals("transit-export", root.metadata("schema"))
        assertEquals("1", root.metadata("formatVersion"))
        assertEquals("2026-07-18T12:34:56Z", root.metadata("exportedAt"))
        assertEquals("2", root.metadata("recordCount"))
        assertEquals("2026-05-01", root.metadata("startDate"))
        assertEquals("2026-05-02", root.metadata("endDate"))
        assertEquals(64, result.sha256.length)
    }

    @Test
    fun `JSON output is deterministic for the same ordered portable input`() = runBlocking {
        val request = request(
            TransitExportFormat.JSON,
            listOf(
                record(
                    id = "deterministic",
                    provenance = listOf(
                        TransitExportProvenanceInput("z", TransitFieldSource.MANUAL),
                        TransitExportProvenanceInput("a", TransitFieldSource.RMV_DISTANCE)
                    )
                )
            )
        )

        val first = ready(useCase().prepare(request))
        val second = ready(useCase().prepare(request))

        assertArrayEquals(first.bytes, second.bytes)
        assertEquals(first.sha256, second.sha256)
    }

    @Test
    fun `JSON exports field provenance while session-only evidence becomes unknown`() = runBlocking {
        val result = ready(
            useCase().prepare(
                request(
                    TransitExportFormat.JSON,
                    listOf(
                        record(
                            id = "p",
                            provenance = listOf(
                                TransitExportProvenanceInput(
                                    fieldId = "hat",
                                    source = TransitFieldSource.LIVE_RMV,
                                    freshness = TransitFieldFreshness.FRESH,
                                    lastUpdatedAtEpochMillis = fixedNow,
                                    evidence = TransitExportProvenanceEvidence.RECORD_DERIVED
                                ),
                                TransitExportProvenanceInput(
                                    fieldId = "not",
                                    source = TransitFieldSource.MANUAL,
                                    freshness = TransitFieldFreshness.FRESH,
                                    lastUpdatedAtEpochMillis = fixedNow,
                                    evidence = TransitExportProvenanceEvidence.SESSION_ONLY
                                )
                            )
                        )
                    )
                )
            )
        )
        val provenance = Json.parseToJsonElement(result.bytes.toString(Charsets.UTF_8))
            .jsonObject.getValue("records").jsonArray.single().jsonObject
            .getValue("provenance").jsonArray.associateBy {
                it.jsonObject.getValue("fieldId").jsonPrimitive.content
            }

        assertEquals("live_rmv", provenance.getValue("hat").jsonObject.getValue("source").jsonPrimitive.content)
        assertEquals("unknown", provenance.getValue("not").jsonObject.getValue("source").jsonPrimitive.content)
        assertTrue(provenance.getValue("not").jsonObject.getValue("lastUpdatedAt").toString() == "null")
    }

    @Test
    fun `unknown values remain explicit and empty values are not guessed`() = runBlocking {
        val result = ready(
            useCase().prepare(request(TransitExportFormat.JSON, listOf(record("unknown"))))
        )
        val item = Json.parseToJsonElement(result.bytes.toString(Charsets.UTF_8))
            .jsonObject.getValue("records").jsonArray.single().jsonObject

        assertEquals("unknown", item.getValue("syncStatus").jsonPrimitive.content)
        assertEquals("unknown", item.getValue("healthStatus").jsonPrimitive.content)
        assertEquals("null", item.getValue("actualDeparture").toString())
        assertTrue(item.getValue("provenance").jsonArray.isEmpty())
    }

    @Test
    fun `summary insights and health sections are independently portable`() = runBlocking {
        val request = TransitExportRequest(
            format = TransitExportFormat.JSON,
            scope = TransitExportScope.allTransit(),
            records = emptyList(),
            sections = setOf(
                TransitExportSection.SUMMARY,
                TransitExportSection.INSIGHTS,
                TransitExportSection.DATA_HEALTH
            ),
            summary = listOf(TransitExportMetric("trip_count", "Trips", "12")),
            insights = listOf(
                TransitExportInsight(
                    id = "top_line",
                    title = "Most used line",
                    result = "S8",
                    period = "2026-06",
                    confidence = "high",
                    explanation = "6 of 12 records",
                    recordCount = 12
                )
            ),
            healthSummary = TransitExportHealthSummary(12, 10, 1, 1, 0)
        )

        val root = Json.parseToJsonElement(
            ready(useCase().prepare(request)).bytes.toString(Charsets.UTF_8)
        ).jsonObject

        assertEquals(0, root.getValue("records").jsonArray.size)
        assertEquals("trip_count", root.getValue("summary").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("top_line", root.getValue("insights").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("12", root.getValue("dataHealth").jsonObject.getValue("scannedRecordCount").jsonPrimitive.content)
    }

    @Test
    fun `feature gate disabled performs no preparation`() = runBlocking {
        var clockRead = false
        val disabled = TransitExportUseCase(
            enabled = false,
            calculationDispatcher = Dispatchers.Unconfined,
            nowEpochMillis = {
                clockRead = true
                fixedNow
            }
        )

        val result = disabled.prepare(request(TransitExportFormat.JSON, listOf(record("x"))))

        assertEquals(TransitExportPreparationResult.Disabled, result)
        assertFalse(clockRead)
    }

    @Test
    fun `cancellation is propagated and never converted to export failure`() = runBlocking {
        val useCase = TransitExportUseCase(
            enabled = true,
            calculationDispatcher = Dispatchers.Unconfined,
            nowEpochMillis = { throw CancellationException("cancel export") }
        )

        var cancelled = false
        try {
            useCase.prepare(request(TransitExportFormat.JSON, listOf(record("x"))))
        } catch (error: CancellationException) {
            cancelled = error.message == "cancel export"
        }
        assertTrue(cancelled)
    }

    @Test
    fun `large fixture produces every transit record without locale-dependent decimals`() =
        runBlocking {
            val records = List(10_000) { index ->
                record(
                    id = "large-$index",
                    date = "2026-06-${(index % 28 + 1).toString().padStart(2, '0')}",
                    distance = 12.75,
                    note = "large-fixture-payload-${"x".repeat(100)}"
                )
            }

            val result = ready(
                useCase().prepare(request(TransitExportFormat.CSV, records))
            )
            val csv = result.bytes.toString(Charsets.UTF_8)

            assertEquals(10_000, result.exportedRecordCount)
            assertTrue(csv.contains("12.75"))
            assertFalse(csv.contains("12,75"))
            assertTrue(result.bytes.size > 1_000_000)
        }

    @Test
    fun `entity mapper drops UID Firestore id and queue-shaped data from JSON`() = runBlocking {
        val entity = TripEntity(
            id = "local-1",
            firestoreDocId = "firestore-secret",
            tarih = "18.07.2026",
            hat = "S8",
            not = "normal note",
            sortDate = "2026-07-18",
            userId = "uid-secret"
        )
        val mapped = TransitExportRecordMapper.fromEntity(entity)
        val json = ready(
            useCase().prepare(request(TransitExportFormat.JSON, listOf(mapped)))
        ).bytes.toString(Charsets.UTF_8)

        assertFalse(json.contains("uid-secret"))
        assertFalse(json.contains("firestore-secret"))
        assertFalse(json.contains("queueActionId"))
        assertFalse(json.contains("payload"))
        assertFalse(json.contains("firestoreDocId"))
        assertFalse(json.contains("gps", ignoreCase = true))
        assertFalse(json.contains("activityRecognition", ignoreCase = true))
        assertFalse(json.contains("personalTrip", ignoreCase = true))
        assertFalse(TransitExportRecord::class.java.declaredFields.any {
            it.name.contains("personal", ignoreCase = true) || it.name == "userId"
        })
    }

    @Test
    fun `entity mapper uses ISO machine date and existing transit distance evidence`() {
        val mapped = TransitExportRecordMapper.fromEntity(
            TripEntity(
                id = "mapped",
                tarih = "03.07.2026",
                rmvMesafeKm = 14.25,
                orsMesafeKm = 15.5,
                userId = "not-exported"
            ),
            syncPhase = TransitSyncPhase.SYNCED,
            healthSeverity = TransitHealthSeverity.WARNING
        )

        assertEquals("2026-07-03", mapped.dateIso)
        assertEquals(14.25, mapped.distanceKm!!, 0.0)
        assertEquals(TransitSyncPhase.SYNCED, mapped.syncPhase)
        assertEquals(TransitHealthSeverity.WARNING, mapped.healthSeverity)
    }

    private fun useCase() = TransitExportUseCase(
        enabled = true,
        calculationDispatcher = Dispatchers.Unconfined,
        nowEpochMillis = { fixedNow }
    )

    private fun request(
        format: TransitExportFormat,
        records: List<TransitExportRecord>,
        scope: TransitExportScope = TransitExportScope.allTransit(),
        tombstonedRecordIds: Set<String> = emptySet()
    ) = TransitExportRequest(
        format = format,
        scope = scope,
        records = records,
        tombstonedRecordIds = tombstonedRecordIds
    )

    private fun record(
        id: String,
        date: String = "2026-06-15",
        line: String? = null,
        boarding: String? = null,
        alighting: String? = null,
        note: String? = null,
        distance: Double? = null,
        matchesFilter: Boolean = true,
        tombstoned: Boolean = false,
        provenance: List<TransitExportProvenanceInput> = emptyList()
    ) = TransitExportRecord(
        localRecordId = id,
        dateIso = date,
        line = line,
        boardingStop = boarding,
        alightingStop = alighting,
        distanceKm = distance,
        note = note,
        matchesFilter = matchesFilter,
        isTombstoned = tombstoned,
        provenance = provenance
    )

    private fun ready(result: TransitExportPreparationResult): PreparedTransitExport {
        assertTrue(result is TransitExportPreparationResult.Ready)
        return (result as TransitExportPreparationResult.Ready).document
    }

    private fun kotlinx.serialization.json.JsonObject.metadata(key: String): String {
        val value = getValue("metadata").jsonObject.getValue(key)
        assertNotNull(value)
        return value.jsonPrimitive.content
    }
}
