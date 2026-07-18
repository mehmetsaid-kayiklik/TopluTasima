package com.example.toplutasima.viewmodel

import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.transit.provenance.TransitFieldProvenanceUseCase
import com.example.toplutasima.transit.provenance.TransitFieldSource
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceResolver
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceStore
import com.example.toplutasima.usecase.RecordSaveUseCase
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.viewmodel.rmvlog.LogMode
import com.example.toplutasima.viewmodel.rmvlog.ManualEntryState
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RmvLogProvenanceIntegrationTest {
    private val now = 10_000_000L
    private val provenanceUseCase = TransitFieldProvenanceUseCase(nowEpochMillis = { now })

    @Test
    fun `new RMV save keeps planned distance and manual actual field provenance`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        val segment = Segment(
            typeTr = "S-Bahn",
            line = "S5",
            direction = "Friedrichsdorf",
            fromStop = "Frankfurt Hbf",
            toStop = "Bad Homburg",
            dep = "10:00",
            arr = "10:30",
            distanceKm = 12.0,
            polyDistanceKm = 11.8,
            journeyRef = "journey-1",
            fromStopId = "stop-a",
            toStopId = "stop-b"
        )
        val state = RmvLogUiState(
            date = "22.05.2026",
            trip = TripResult(listOf(segment), "10:00", "10:30", 30),
            tripUpdatedAtEpochMillis = now,
            customBindimTime = "1005"
        )

        recordNewRmvSaveProvenance(
            store = store,
            provenanceUseCase = provenanceUseCase,
            userId = "uid-a",
            state = state,
            result = saveResult("local-1")
        )

        val fields = store.snapshotForRecord("uid-a", "local-1")!!.fields
        assertEquals(
            TransitFieldSource.PLANNED_RMV,
            fields.getValue(TransitRecordProvenanceResolver.FIELD_LINE).provenance.source
        )
        assertEquals(
            TransitFieldSource.ORS_DISTANCE,
            fields.getValue(TransitRecordProvenanceResolver.FIELD_DISTANCE).provenance.source
        )
        assertEquals(
            TransitFieldSource.RMV_DISTANCE,
            fields.getValue(TransitRecordCalculations.FIELD_RMV_DISTANCE_KM).provenance.source
        )
        assertEquals(
            TransitFieldSource.MANUAL,
            fields.getValue(TransitRecordProvenanceResolver.FIELD_ACTUAL_DEPARTURE).provenance.source
        )
    }

    @Test
    fun `new manual save marks only supplied transit values as manual`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        val state = RmvLogUiState(
            date = "22.05.2026",
            mode = LogMode.MANUAL,
            manual = ManualEntryState(
                isManualMode = true,
                line = "64",
                boardingStop = "Hauptbahnhof",
                alightingStop = "Baseler Platz",
                plannedDep = "1010",
                plannedArr = "1020",
                actualDep = "1012",
                actualArr = "1022",
                distance = "2.5"
            )
        )

        recordNewManualSaveProvenance(
            store = store,
            provenanceUseCase = provenanceUseCase,
            userId = "uid-a",
            state = state,
            result = saveResult("local-manual")
        )

        val fields = store.snapshotForRecord("uid-a", "local-manual")!!.fields
        assertTrue(fields.isNotEmpty())
        assertTrue(fields.values.all { it.provenance.source == TransitFieldSource.MANUAL })
        assertEquals(
            TransitFieldSource.MANUAL,
            fields.getValue(TransitRecordCalculations.FIELD_ORS_DISTANCE_KM).provenance.source
        )
    }

    @Test
    fun `disabled gate and update result do not overwrite session provenance`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        val state = RmvLogUiState(
            date = "22.05.2026",
            trip = TripResult(
                listOf(
                    Segment(
                        typeTr = "S-Bahn",
                        line = "S5",
                        direction = "North",
                        fromStop = "A",
                        toStop = "B",
                        dep = "10:00",
                        arr = "10:30"
                    )
                ),
                "10:00",
                "10:30",
                30
            )
        )

        recordNewRmvSaveProvenance(
            store,
            provenanceUseCase,
            "uid-a",
            state,
            saveResult("local-1"),
            enabled = false
        )
        recordNewRmvSaveProvenance(
            store,
            provenanceUseCase,
            "uid-a",
            state,
            RecordSaveUseCase.RmvSaveResult(null, null, null, shouldStartNotification = false)
        )

        assertTrue(store.snapshots.value.isEmpty())
    }

    private fun saveResult(localId: String) = RecordSaveUseCase.RmvSaveResult(
        firstId = localId,
        lastId = localId,
        segmentIds = listOf(localId),
        shouldStartNotification = false
    )
}
