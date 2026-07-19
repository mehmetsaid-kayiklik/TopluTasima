package com.example.toplutasima.transit.export

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.transit.provenance.TransitFieldProvenance
import com.example.toplutasima.transit.sync.TransitSyncPhase
import com.example.toplutasima.usecase.TransitRecordCalculations
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Maps the Room transit entity into the privacy-reduced export boundary. */
object TransitExportRecordMapper {
    fun fromEntity(
        entity: TripEntity,
        syncPhase: TransitSyncPhase? = null,
        healthSeverity: TransitHealthSeverity? = null,
        provenance: List<TransitExportProvenanceInput> = emptyList(),
        matchesFilter: Boolean = true,
        isTombstoned: Boolean = false
    ): TransitExportRecord = TransitExportRecord(
        localRecordId = entity.id,
        dateIso = isoDate(entity),
        line = entity.hat.clean(),
        boardingStop = entity.binisDuragi.clean(),
        alightingStop = entity.inisDuragi.clean(),
        plannedDeparture = entity.planlananBinis.clean(),
        actualDeparture = entity.gercekBinis.clean(),
        plannedArrival = entity.planlananInis.clean(),
        actualArrival = entity.gercekInis.clean(),
        plannedDurationMinutes = entity.planlananYolSuresi.intOrNull(),
        actualDurationMinutes = entity.gercekYolSuresi.intOrNull(),
        delayMinutes = entity.gecikme.intOrNull(),
        distanceKm = sequenceOf(
            entity.rmvMesafeKm,
            entity.orsMesafeKm,
            TransitRecordCalculations.parseDistanceKm(entity.mesafe)
        ).filterNotNull().firstOrNull { it.isFinite() && it >= 0.0 },
        recordType = entity.tur.clean(),
        origin = if (!entity.journeyRef.isNullOrBlank()) {
            TransitExportRecordOrigin.RMV
        } else {
            TransitExportRecordOrigin.UNKNOWN_TRANSIT
        },
        syncPhase = syncPhase,
        healthSeverity = healthSeverity,
        provenance = provenance,
        note = entity.not.clean(),
        matchesFilter = matchesFilter,
        isTombstoned = isTombstoned
    )

    fun provenanceInputs(
        values: Map<String, TransitFieldProvenance>,
        evidence: TransitExportProvenanceEvidence
    ): List<TransitExportProvenanceInput> = values
        .toSortedMap()
        .map { (fieldId, value) ->
            TransitExportProvenanceInput(
                fieldId = fieldId,
                source = value.source,
                freshness = value.freshness,
                lastUpdatedAtEpochMillis = value.lastUpdatedAtEpochMillis,
                isFallback = value.isFallback,
                backingSource = value.backingSource,
                fallbackFor = value.fallbackFor,
                evidence = evidence
            )
        }

    private fun isoDate(entity: TripEntity): String {
        entity.sortDate?.trim()?.takeIf { it.isIsoDate() }?.let { return it }
        val displayed = entity.tarih?.trim().orEmpty()
        if (displayed.isBlank()) return ""
        return try {
            LocalDate.parse(displayed, DISPLAY_DATE_FORMATTER).toString()
        } catch (_: DateTimeParseException) {
            ""
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.intOrNull(): Int? = this?.trim()?.toIntOrNull()

    private fun String.isIsoDate(): Boolean = try {
        LocalDate.parse(this)
        true
    } catch (_: DateTimeParseException) {
        false
    }

    private val DISPLAY_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.uuuu")
}
