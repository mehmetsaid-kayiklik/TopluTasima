package com.example.toplutasima.data.repository

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.network.FirestoreService

fun Map<String, Any?>.toEntity(): TripEntity {
    val firestoreDocId = this["firestoreDocId"]?.toString()?.takeIf { it.isNotBlank() }
    val idStr = this["id"]?.toString()?.takeIf { it.isNotBlank() }
        ?: firestoreDocId
        ?: java.util.UUID.randomUUID().toString()
    return TripEntity(
        id = idStr,
        firestoreDocId = firestoreDocId,
        tarih = this["tarih"]?.toString(),
        gun = this["gun"]?.toString(),
        tur = this["tur"]?.toString(),
        hat = this["hat"]?.toString(),
        yon = this["yon"]?.toString(),
        binisDuragi = this["binisDuragi"]?.toString(),
        planlananBinis = this["planlananBinis"]?.toString(),
        gercekBinis = this["gercekBinis"]?.toString(),
        gecikme = this["gecikme"]?.toString(),
        inisDuragi = this["inisDuragi"]?.toString(),
        planlananInis = this["planlananInis"]?.toString(),
        gercekInis = this["gercekInis"]?.toString(),
        gununTipi = this["gununTipi"]?.toString(),
        havaDurumu = this["havaDurumu"]?.toString(),
        oturabildimMi = this["oturabildimMi"]?.toString(),
        planlananYolSuresi = this["planlananYolSuresi"]?.toString(),
        gercekYolSuresi = this["gercekYolSuresi"]?.toString(),
        not = this["not"]?.toString(),
        biletKontrolu = this["biletKontrolü"]?.toString() ?: this["biletKontrolu"]?.toString(),
        mesafe = this["mesafe"]?.toString(),
        orsMesafeKm = this[FirestoreService.FIELD_ORS_DISTANCE_KM]?.toString()?.toDoubleOrNull(),
        orsMesafeText = this[FirestoreService.FIELD_ORS_DISTANCE_TEXT]?.toString(),
        rmvMesafeKm = this[FirestoreService.FIELD_RMV_DISTANCE_KM]?.toString()?.toDoubleOrNull(),
        rmvMesafeMetre = this[FirestoreService.FIELD_RMV_DISTANCE_METERS]?.toString()?.toDoubleOrNull()?.toInt(),
        rmvMesafeText = this[FirestoreService.FIELD_RMV_DISTANCE_TEXT]?.toString(),
        rmvMesafeDurumu = this[FirestoreService.FIELD_RMV_DISTANCE_STATUS]?.toString(),
        rmvMesafeGuncellemeTarihi = this[FirestoreService.FIELD_RMV_DISTANCE_UPDATED_AT]?.toString(),
        rmvApiVersion = this[FirestoreService.FIELD_RMV_API_VERSION]?.toString(),
        journeyRef = this[FirestoreService.FIELD_JOURNEY_REF]?.toString(),
        fromStopId = this[FirestoreService.FIELD_FROM_STOP_ID]?.toString(),
        toStopId = this[FirestoreService.FIELD_TO_STOP_ID]?.toString(),
        durakSayisi = this["durakSayisi"]?.toString(),
        yearMonth = this["yearMonth"]?.toString(),
        sortDate = this["sortDate"]?.toString(),
        seatmateUuid = this["seatmateUuid"]?.toString() ?: ""
    )
}

fun TripEntity.toMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "firestoreDocId" to firestoreDocId?.takeIf { it.isNotBlank() },
        "tarih" to tarih,
        "gun" to gun,
        "tur" to tur,
        "hat" to hat,
        "yon" to yon,
        "binisDuragi" to binisDuragi,
        "planlananBinis" to planlananBinis,
        "gercekBinis" to gercekBinis,
        "gecikme" to gecikme?.toIntOrNull(),
        "inisDuragi" to inisDuragi,
        "planlananInis" to planlananInis,
        "gercekInis" to gercekInis,
        "gununTipi" to gununTipi,
        "havaDurumu" to havaDurumu,
        "oturabildimMi" to oturabildimMi,
        "planlananYolSuresi" to planlananYolSuresi,
        "gercekYolSuresi" to gercekYolSuresi,
        "not" to not,
        "biletKontrolü" to biletKontrolu,
        "mesafe" to mesafe,
        FirestoreService.FIELD_ORS_DISTANCE_KM to orsMesafeKm,
        FirestoreService.FIELD_ORS_DISTANCE_TEXT to orsMesafeText,
        FirestoreService.FIELD_RMV_DISTANCE_KM to rmvMesafeKm,
        FirestoreService.FIELD_RMV_DISTANCE_METERS to rmvMesafeMetre,
        FirestoreService.FIELD_RMV_DISTANCE_TEXT to rmvMesafeText,
        FirestoreService.FIELD_RMV_DISTANCE_STATUS to rmvMesafeDurumu,
        FirestoreService.FIELD_RMV_DISTANCE_UPDATED_AT to rmvMesafeGuncellemeTarihi,
        FirestoreService.FIELD_RMV_API_VERSION to rmvApiVersion,
        FirestoreService.FIELD_JOURNEY_REF to journeyRef,
        FirestoreService.FIELD_FROM_STOP_ID to fromStopId,
        FirestoreService.FIELD_TO_STOP_ID to toStopId,
        "durakSayisi" to durakSayisi,
        "yearMonth" to yearMonth,
        "sortDate" to sortDate,
        "seatmateUuid" to seatmateUuid
    ).filterValues { it != null }
}
