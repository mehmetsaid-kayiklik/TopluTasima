package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    indices = [
        Index(value = ["yearMonth"]),
        Index(value = ["sortDate"]),
        Index(value = ["firestoreDocId"], unique = false)
    ]
)
data class TripEntity(
    @PrimaryKey
    val id: String,
    val firestoreDocId: String? = null,
    val tarih: String? = null,
    val gun: String? = null,
    val tur: String? = null,
    val hat: String? = null,
    val yon: String? = null,
    val binisDuragi: String? = null,
    val planlananBinis: String? = null,
    val gercekBinis: String? = null,
    val gecikme: String? = null,
    val inisDuragi: String? = null,
    val planlananInis: String? = null,
    val gercekInis: String? = null,
    val gununTipi: String? = null,
    val havaDurumu: String? = null,
    val oturabildimMi: String? = null,
    val planlananYolSuresi: String? = null,
    val gercekYolSuresi: String? = null,
    val not: String? = null,
    val biletKontrolu: String? = null, // Firestore'da "biletKontrolü" olarak da geçebiliyor, parser halledecek
    val mesafe: String? = null,
    val orsMesafeKm: Double? = null,
    val orsMesafeText: String? = null,
    val rmvMesafeKm: Double? = null,
    val rmvMesafeMetre: Int? = null,
    val rmvMesafeText: String? = null,
    val rmvMesafeDurumu: String? = null,
    val rmvMesafeGuncellemeTarihi: String? = null,
    val rmvApiVersion: String? = null,
    val journeyRef: String? = null,
    val fromStopId: String? = null,
    val toStopId: String? = null,
    val durakSayisi: String? = null,
    val yearMonth: String? = null,
    val sortDate: String? = null,
    val seatmateUuid: String = ""
)
