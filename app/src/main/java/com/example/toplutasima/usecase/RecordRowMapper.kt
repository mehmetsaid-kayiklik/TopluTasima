package com.example.toplutasima.usecase

import com.example.toplutasima.ui.util.vehicleIcon
import com.example.toplutasima.viewmodel.records.RecordRowUiModel
import java.util.UUID

object RecordRowMapper {
    fun fromFirestoreRecord(
        rec: Map<String, Any>,
        profileId: String = "",
        profileName: String = "",
        seatmateNote: String = ""
    ): RecordRowUiModel {
        val date = rec["tarih"]?.toString() ?: ""
        val dayName = rec["gun"]?.toString() ?: ""
        val turValue = rec["tur"]?.toString() ?: ""
        val typeDisplay = "${vehicleIcon(turValue)} $turValue"

        return RecordRowUiModel(
            id = rec["firestoreDocId"]?.toString()?.takeIf { it.isNotBlank() }
                ?: rec["id"]?.toString()
                ?: UUID.randomUUID().toString(),
            date = date,
            day = dayName,
            type = turValue,
            typeDisplay = typeDisplay,
            line = rec["hat"]?.toString() ?: "",
            direction = rec["yon"]?.toString() ?: "",
            boardingStop = rec["binisDuragi"]?.toString() ?: "",
            plannedDep = rec["planlananBinis"]?.toString() ?: "",
            actualDep = rec["gercekBinis"]?.toString() ?: "",
            delay = rec["gecikme"]?.toString() ?: "",
            alightingStop = rec["inisDuragi"]?.toString() ?: "",
            plannedArr = rec["planlananInis"]?.toString() ?: "",
            actualArr = rec["gercekInis"]?.toString() ?: "",
            dayType = rec["gununTipi"]?.toString() ?: "",
            weather = rec["havaDurumu"]?.toString() ?: "",
            seated = rec["oturabildimMi"]?.toString() ?: "",
            plannedDuration = rec["planlananYolSuresi"]?.toString() ?: "",
            actualDuration = rec["gercekYolSuresi"]?.toString() ?: "",
            note = rec["not"]?.toString() ?: "",
            ticketControl = rec["biletKontrolü"]?.toString() ?: "",
            distance = rec["mesafe"]?.toString() ?: "",
            orsDistance = rec[TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT]?.toString()?.takeIf { it.isNotBlank() }
                ?: rec["mesafe"]?.toString().orEmpty(),
            rmvDistance = rec[TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT]?.toString().orEmpty(),
            rmvDistanceStatus = rec[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS]?.toString().orEmpty(),
            stopCount = rec["durakSayisi"]?.toString() ?: "",
            originalRecord = rec,
            profileId = profileId,
            profileName = profileName,
            seatmateNote = seatmateNote
        )
    }
}
