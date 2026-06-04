package com.example.toplutasima.usecase

import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.network.RmvApiService
import com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
import com.example.toplutasima.repository.TransitRecordRepository
import com.example.toplutasima.viewmodel.rmvlog.LogMode
import com.example.toplutasima.viewmodel.rmvlog.ManualEntryState
import com.example.toplutasima.viewmodel.rmvlog.RmvLogUiState
import java.time.Duration
import java.time.LocalTime
import java.util.Locale
import java.util.UUID

class RecordSaveUseCase(
    private val transitRecordRepository: TransitRecordRepository,
    private val firestoreDataSource: FirestoreTripRemoteDataSource
) {
    data class RmvSaveResult(
        val firstId: String?,
        val lastId: String?,
        val segmentIds: List<String>?,
        val shouldStartNotification: Boolean
    )

    data class ActualRecordResult(
        val id: String,
        val time: String,
        val ok: Boolean
    )

    data class RestoredRecord(
        val state: RmvLogUiState,
        val manualEntryState: ManualEntryState,
        val firstId: String,
        val lastId: String
    )

    suspend fun saveRmvRecord(
        state: RmvLogUiState,
        updateProfileLink: suspend (id: String, profileId: String?, seatmateNote: String?) -> Unit
    ): RmvSaveResult {
        val trip = state.trip ?: throw IllegalStateException("Önce plan alınmalı.")
        if (state.segmentIds.isEmpty()) {
            val ids = trip.segments.mapIndexed { idx, seg ->
                val id = UUID.randomUUID().toString()
                val ok = transitRecordRepository.saveSegment(
                    id = id,
                    date = state.date,
                    seg = seg,
                    havaDurumu = state.segmentHavaDurumu[idx] ?: "Bilinmiyor",
                    oturabildim = state.segmentOturabildim[idx] ?: false,
                    biletKontrolu = state.segmentBiletKontrolu[idx] ?: false,
                    note = state.segmentNote[idx] ?: "",
                    profileId = state.segmentProfileId[idx].takeIf { !it.isNullOrBlank() },
                    seatmateNote = state.segmentSeatmateNote[idx].takeIf { !it.isNullOrBlank() }
                )
                if (!ok) throw Exception("Kayıt başarısız.")
                if (idx == state.selectedSegmentIndex &&
                    (state.customBindimTime.isNotBlank() || state.customIndimTime.isNotBlank())
                ) {
                    transitRecordRepository.updateActual(
                        id,
                        state.customBindimTime.takeIf { it.isNotBlank() }?.let { RmvApiService.formatTimeDigits(it) },
                        state.customIndimTime.takeIf { it.isNotBlank() }?.let { RmvApiService.formatTimeDigits(it) }
                    )
                }
                id
            }
            return RmvSaveResult(
                firstId = ids.firstOrNull().orEmpty(),
                lastId = ids.lastOrNull().orEmpty(),
                segmentIds = ids,
                shouldStartNotification = true
            )
        }

        state.segmentIds.forEachIndexed { idx, id ->
            val seg = trip.segments.getOrNull(idx) ?: return@forEachIndexed
            val planlananSure = TransitRecordCalculations.computeYolSuresi(
                seg.dep.ifBlank { null },
                seg.arr.ifBlank { null }
            )
            val ok = transitRecordRepository.updateExistingRecord(
                id,
                mapOf(
                    "planlananBinis" to seg.dep,
                    "planlananInis" to seg.arr,
                    "planlananYolSuresi" to planlananSure,
                    "havaDurumu" to (state.segmentHavaDurumu[idx] ?: "Bilinmiyor"),
                    "oturabildimMi" to SeatingStatus.fromBoolean(state.segmentOturabildim[idx] ?: false).key,
                    "biletKontrolü" to TicketStatus.fromBoolean(state.segmentBiletKontrolu[idx] ?: false).key,
                    "not" to (state.segmentNote[idx] ?: "")
                )
            )
            if (!ok) throw Exception("Kayıt başarısız.")
            updateProfileLink(
                id,
                state.segmentProfileId[idx].takeIf { !it.isNullOrBlank() },
                state.segmentSeatmateNote[idx].takeIf { !it.isNullOrBlank() }
            )
        }
        return RmvSaveResult(firstId = null, lastId = null, segmentIds = null, shouldStartNotification = true)
    }

    suspend fun saveManualRecord(
        state: RmvLogUiState,
        updateProfileLink: suspend (id: String, profileId: String?, seatmateNote: String?) -> Unit
    ): RmvSaveResult {
        val manual = state.manual
        if (manual.boardingStop.isBlank() || manual.alightingStop.isBlank() || manual.line.isBlank()) {
            throw IllegalStateException("Hat, biniş ve iniş durakları zorunludur.")
        }
        val distanceKm = manual.distance.replace(",", ".").toDoubleOrNull() ?: 0.0
        val stopCount = manual.stopCount.toIntOrNull() ?: 0
        val segment = Segment(
            typeTr = manual.typeTr,
            line = manual.line,
            direction = manual.direction,
            fromStop = manual.boardingStop,
            toStop = manual.alightingStop,
            dep = TransitTimeUtils.formatTime(manual.plannedDep),
            arr = TransitTimeUtils.formatTime(manual.plannedArr),
            distanceKm = distanceKm,
            stopCount = stopCount
        )
        if (state.segmentIds.isEmpty()) {
            val newId = UUID.randomUUID().toString()
            val ok = transitRecordRepository.saveSegment(
                id = newId,
                date = state.date,
                seg = segment,
                havaDurumu = manual.weather,
                oturabildim = manual.oturabildim,
                biletKontrolu = manual.biletKontrolu,
                note = manual.note,
                profileId = manual.profileId.takeIf { it.isNotBlank() },
                seatmateNote = manual.seatmateNote.takeIf { it.isNotBlank() }
            )
            if (ok) {
                val actDep = TransitTimeUtils.formatTime(manual.actualDep)
                val actArr = TransitTimeUtils.formatTime(manual.actualArr)
                if (actDep.isNotBlank() || actArr.isNotBlank()) {
                    transitRecordRepository.updateActual(newId, actDep.ifBlank { null }, actArr.ifBlank { null })
                }
            }
            if (!ok) throw Exception("Kayıt başarısız.")
            return RmvSaveResult(newId, newId, listOf(newId), shouldStartNotification = false)
        }

        val docId = state.segmentIds.first()
        val actDep = TransitTimeUtils.formatTime(manual.actualDep)
        val actArr = TransitTimeUtils.formatTime(manual.actualArr)
        val gecikme = TransitRecordCalculations.computeGecikme(segment.dep.ifBlank { null }, actDep.ifBlank { null })
        val planlananSure = TransitRecordCalculations.computeYolSuresi(segment.dep.ifBlank { null }, segment.arr.ifBlank { null })
        val gercekSure = TransitRecordCalculations.computeYolSuresi(actDep.ifBlank { null }, actArr.ifBlank { null })
        val mesafeText = if (distanceKm > 0) String.format(Locale.US, "%.2f km", distanceKm) else "Bilinmiyor"
        val updateMap = linkedMapOf<String, Any>(
            "tur" to manual.typeTr,
            "hat" to manual.line,
            "yon" to manual.direction,
            "binisDuragi" to manual.boardingStop,
            "inisDuragi" to manual.alightingStop,
            "planlananBinis" to segment.dep,
            "planlananInis" to segment.arr,
            "gercekBinis" to actDep,
            "gercekInis" to actArr,
            "gecikme" to gecikme,
            "planlananYolSuresi" to planlananSure,
            "gercekYolSuresi" to gercekSure,
            "mesafe" to mesafeText,
            "durakSayisi" to if (stopCount > 0) stopCount.toString() else "Bilinmiyor",
            "havaDurumu" to manual.weather,
            "oturabildimMi" to SeatingStatus.fromBoolean(manual.oturabildim).key,
            "biletKontrolü" to TicketStatus.fromBoolean(manual.biletKontrolu).key,
            "not" to manual.note
        )
        updateMap.putAll(TransitRecordCalculations.calculatedDistanceFields(distanceKm, resetRmvDistance = true))
        val ok = transitRecordRepository.updateExistingRecord(docId, updateMap)
        if (!ok) throw Exception("Kayıt başarısız.")
        updateProfileLink(
            docId,
            manual.profileId.takeIf { it.isNotBlank() },
            manual.seatmateNote.takeIf { it.isNotBlank() }
        )
        return RmvSaveResult(firstId = null, lastId = null, segmentIds = null, shouldStartNotification = false)
    }

    suspend fun clearRecord(id: String, clearDep: Boolean, clearArr: Boolean): Boolean =
        transitRecordRepository.clearActual(id, clearDep, clearArr)

    suspend fun deleteRecord(id: String): Boolean =
        firestoreDataSource.deleteTrip(id)

    suspend fun recordBindim(state: RmvLogUiState): ActualRecordResult {
        val segmentId = state.segmentIds.getOrElse(state.selectedSegmentIndex) { "" }
        if (segmentId.isBlank()) throw IllegalStateException("Önce plan alınmalı.")
        val time = if (state.customBindimTime.isNotBlank()) {
            RmvApiService.formatTimeDigits(state.customBindimTime)
        } else {
            LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        }
        return ActualRecordResult(segmentId, time, transitRecordRepository.updateActual(segmentId, time, null))
    }

    suspend fun recordIndim(state: RmvLogUiState): ActualRecordResult {
        val segmentId = state.segmentIds.getOrElse(state.selectedSegmentIndex) { "" }
        if (segmentId.isBlank()) throw IllegalStateException("Önce plan alınmalı.")
        val time = if (state.customIndimTime.isNotBlank()) {
            RmvApiService.formatTimeDigits(state.customIndimTime)
        } else {
            LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        }
        return ActualRecordResult(segmentId, time, transitRecordRepository.updateActual(segmentId, null, time))
    }

    fun restoreRecord(
        baseState: RmvLogUiState,
        record: Map<String, Any>,
        onRestored: (ManualEntryState) -> Unit
    ): RestoredRecord {
        val docId = record["firestoreDocId"]?.toString() ?: ""
        val customId = record["id"]?.toString() ?: docId
        val tarih = record["tarih"]?.toString() ?: ""
        val tur = record["tur"]?.toString() ?: ""
        val hat = record["hat"]?.toString() ?: ""
        val yon = record["yon"]?.toString() ?: ""
        val binisDuragi = record["binisDuragi"]?.toString() ?: ""
        val inisDuragi = record["inisDuragi"]?.toString() ?: ""
        val planlananBinis = record["planlananBinis"]?.toString() ?: ""
        val planlananInis = record["planlananInis"]?.toString() ?: ""
        val gercekBinis = record["gercekBinis"]?.toString() ?: ""
        val gercekInis = record["gercekInis"]?.toString() ?: ""
        val havaDurumu = record["havaDurumu"]?.toString() ?: "Bilinmiyor"
        val oturabildim = record["oturabildimMi"]?.toString() == SeatingStatus.YES.key
        val biletKontrolu = record["biletKontrolü"]?.toString() == TicketStatus.HAPPENED.key
        val not = record["not"]?.toString() ?: ""
        val mesafe = record["mesafe"]?.toString() ?: ""
        val durakSayisi = record["durakSayisi"]?.toString() ?: ""
        val distanceKm = TransitRecordCalculations.orsDistanceKm(record)
            ?: TransitRecordCalculations.parseDistanceKm(mesafe)
            ?: 0.0
        val stopCount = durakSayisi.toIntOrNull() ?: 0
        val manualDistance = run {
            val orsRaw = record[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM]?.toString()?.trim().orEmpty()
            val orsValue = orsRaw.replace(",", ".").toDoubleOrNull()
            if (orsValue != null && orsValue > 0.0) {
                orsRaw
            } else {
                mesafe.filter { it.isDigit() || it == '.' || it == ',' }
            }
        }
        val manualStopCount = durakSayisi.trim().takeIf { it.toIntOrNull() != null }.orEmpty()
        val segment = Segment(
            typeTr = tur,
            line = hat,
            direction = yon,
            fromStop = binisDuragi,
            toStop = inisDuragi,
            dep = planlananBinis,
            arr = planlananInis,
            distanceKm = distanceKm,
            stopCount = stopCount,
            journeyRef = record[TransitRecordCalculations.FIELD_JOURNEY_REF]?.toString().orEmpty(),
            fromStopId = record[TransitRecordCalculations.FIELD_FROM_STOP_ID]?.toString().orEmpty(),
            toStopId = record[TransitRecordCalculations.FIELD_TO_STOP_ID]?.toString().orEmpty()
        )
        val trip = com.example.toplutasima.model.TripResult(
            segments = listOf(segment),
            overallDep = planlananBinis,
            overallArr = planlananInis,
            durationMin = computeDuration(planlananBinis, planlananInis)
        )
        val manual = ManualEntryState(
            typeTr = tur,
            line = hat,
            direction = yon,
            boardingStop = binisDuragi,
            alightingStop = inisDuragi,
            plannedDep = TransitTimeUtils.toDigits(planlananBinis),
            actualDep = TransitTimeUtils.toDigits(gercekBinis),
            plannedArr = TransitTimeUtils.toDigits(planlananInis),
            actualArr = TransitTimeUtils.toDigits(gercekInis),
            distance = manualDistance,
            stopCount = manualStopCount,
            weather = record["havaDurumu"]?.toString().orEmpty(),
            oturabildim = oturabildim,
            biletKontrolu = biletKontrolu,
            note = not
        )
        onRestored(manual)
        val state = baseState.copy(
            date = tarih,
            from = binisDuragi,
            to = inisDuragi,
            trip = trip,
            segmentIds = if (customId.isNotBlank()) listOf(customId) else emptyList(),
            firstSavedId = customId,
            lastSavedId = customId,
            selectedSegmentIndex = 0,
            customBindimTime = if (gercekBinis.isNotBlank()) TransitTimeUtils.toDigits(gercekBinis) else "",
            customIndimTime = if (gercekInis.isNotBlank()) TransitTimeUtils.toDigits(gercekInis) else "",
            segmentHavaDurumu = mapOf(0 to havaDurumu),
            segmentOturabildim = mapOf(0 to oturabildim),
            segmentBiletKontrolu = mapOf(0 to biletKontrolu),
            segmentNote = mapOf(0 to not),
            mode = LogMode.MANUAL,
            manual = manual
        )
        return RestoredRecord(state, manual, customId, customId)
    }

    private fun computeDuration(start: String, end: String): Int =
        try {
            val departure = LocalTime.parse(start.take(5))
            val arrival = LocalTime.parse(end.take(5))
            var diff = Duration.between(departure, arrival).toMinutes().toInt()
            if (diff < 0) diff += 24 * 60
            diff
        } catch (_: Exception) {
            0
        }
}
