package com.example.toplutasima.transit.duplicate

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.repository.toEntity
import com.example.toplutasima.data.repository.toMap
import com.example.toplutasima.domain.transit.validation.TransitRecordSegmentInput
import com.example.toplutasima.domain.transit.validation.TransitRecordValidationInput
import com.example.toplutasima.transit.provenance.TransitFieldProvenance
import com.example.toplutasima.transit.provenance.TransitFieldSource
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceResolver
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceStore
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.usecase.transit.TransitRecordValidationUseCase

/** Builds and validates a merge preview. It never writes or deletes a record. */
class TransitDuplicateMergeUseCase(
    private val validationUseCase: TransitRecordValidationUseCase = TransitRecordValidationUseCase(),
    private val provenanceResolver: TransitRecordProvenanceResolver = TransitRecordProvenanceResolver(),
    private val enabled: Boolean = true
) {
    fun preview(
        first: TripEntity,
        second: TripEntity,
        selection: TransitDuplicateMergeSelection,
        provenanceStore: TransitRecordProvenanceStore? = null
    ): TransitDuplicateMergePreview? {
        if (!enabled || first.userId.isBlank() || first.userId != second.userId || first.id == second.id) {
            return null
        }
        val firstMap = first.toMap()
        val secondMap = second.toMap()
        val merged = firstMap.toMutableMap()
        selection.valueSourceByField.forEach { (field, source) ->
            if (field !in MERGEABLE_FIELDS) return@forEach
            merged[field] = when (source) {
                TransitMergeValueSource.FIRST -> firstMap[field]
                TransitMergeValueSource.SECOND -> secondMap[field]
            }
        }
        merged["id"] = first.id
        merged["firestoreDocId"] = first.firestoreDocId.orEmpty()
        recalculateDerivedFields(merged)
        val entity = merged.toEntity(first.userId)
        val validation = validationUseCase.validate(
            TransitRecordValidationInput(
                segments = listOf(entity.toValidationSegment()),
                actualTimesRequired = true
            )
        )
        return TransitDuplicateMergePreview(
            targetRecordId = first.id,
            sourceRecordIdToDelete = second.id,
            mergedRecord = entity,
            selectedProvenanceByField = selectProvenance(
                first,
                second,
                firstMap,
                secondMap,
                merged,
                selection,
                provenanceStore
            ),
            validationIssues = validation.issues
        )
    }

    fun acknowledgeWarnings(
        preview: TransitDuplicateMergePreview,
        warningIds: Set<String>
    ): TransitDuplicateMergePreview {
        val allowed = preview.validationIssues.filter { it.canOverride }.mapTo(mutableSetOf()) { it.id }
        return preview.copy(acknowledgedWarningIds = warningIds.intersect(allowed))
    }

    private fun selectProvenance(
        first: TripEntity,
        second: TripEntity,
        firstMap: Map<String, Any?>,
        secondMap: Map<String, Any?>,
        mergedMap: Map<String, Any?>,
        selection: TransitDuplicateMergeSelection,
        store: TransitRecordProvenanceStore?
    ): Map<String, TransitFieldProvenance> {
        val firstProvenance = provenanceResolver.resolveFields(
            first.userId, first.id, firstMap, store, fieldIds = MERGEABLE_FIELDS
        )
        val secondProvenance = provenanceResolver.resolveFields(
            second.userId, second.id, secondMap, store, fieldIds = MERGEABLE_FIELDS
        )
        return MERGEABLE_FIELDS.associateWith { field ->
            val selectedFromSecond = selection.valueSourceByField[field] == TransitMergeValueSource.SECOND
            val selected = if (selectedFromSecond) secondProvenance[field] else firstProvenance[field]
            val other = if (selectedFromSecond) firstProvenance[field] else secondProvenance[field]
            val selectedValue = if (selectedFromSecond) secondMap[field] else firstMap[field]
            val otherValue = if (selectedFromSecond) firstMap[field] else secondMap[field]
            if (
                selected?.source == TransitFieldSource.UNKNOWN &&
                other?.source != null && other.source != TransitFieldSource.UNKNOWN &&
                selectedValue?.toString() == otherValue?.toString()
            ) {
                other.copy(fieldId = field)
            } else {
                selected?.copy(fieldId = field)
                    ?: provenanceResolver.resolveFields(
                        first.userId,
                        first.id,
                        mergedMap,
                        null,
                        fieldIds = setOf(field)
                    ).getValue(field)
            }
        }
    }

    private fun recalculateDerivedFields(record: MutableMap<String, Any?>) {
        val date = record["tarih"]?.toString().orEmpty()
        if (date.isNotBlank()) {
            record["gun"] = TransitRecordCalculations.computeGun(date)
            record["gununTipi"] = TransitRecordCalculations.computeGununTipi(date)
            record["sortDate"] = TransitRecordCalculations.computeSortDate(date)
            record["yearMonth"] = TransitRecordCalculations.computeYearMonth(date)
        }
        val plannedDeparture = record["planlananBinis"]?.toString()
        val plannedArrival = record["planlananInis"]?.toString()
        val actualDeparture = record["gercekBinis"]?.toString()
        val actualArrival = record["gercekInis"]?.toString()
        record["planlananYolSuresi"] =
            TransitRecordCalculations.computeYolSuresi(plannedDeparture, plannedArrival)
        record["gercekYolSuresi"] =
            TransitRecordCalculations.computeYolSuresi(actualDeparture, actualArrival)
        record["gecikme"] = TransitRecordCalculations.computeGecikme(plannedDeparture, actualDeparture)
    }

    private fun TripEntity.toValidationSegment() = TransitRecordSegmentInput(
        boardingStop = binisDuragi.orEmpty(),
        alightingStop = inisDuragi.orEmpty(),
        plannedDeparture = planlananBinis.orEmpty(),
        plannedArrival = planlananInis.orEmpty(),
        actualDeparture = gercekBinis.orEmpty(),
        actualArrival = gercekInis.orEmpty(),
        boardingStopId = fromStopId.orEmpty(),
        alightingStopId = toStopId.orEmpty(),
        distanceKm = mesafe,
        rmvDistanceKm = rmvMesafeKm,
        orsDistanceKm = orsMesafeKm,
        storedPlannedDurationMinutes = planlananYolSuresi?.toIntOrNull(),
        storedActualDurationMinutes = gercekYolSuresi?.toIntOrNull()
    )

    companion object {
        val MERGEABLE_FIELDS: Set<String> = linkedSetOf(
            "tarih", "tur", "hat", "yon", "binisDuragi", "inisDuragi",
            "planlananBinis", "planlananInis", "gercekBinis", "gercekInis",
            "havaDurumu", "oturabildimMi", "biletKontrolü", "mesafe",
            TransitRecordCalculations.FIELD_ORS_DISTANCE_KM,
            TransitRecordCalculations.FIELD_RMV_DISTANCE_KM,
            "durakSayisi", "not", TransitRecordCalculations.FIELD_FROM_STOP_ID,
            TransitRecordCalculations.FIELD_TO_STOP_ID, "journeyRef"
        )
    }
}
