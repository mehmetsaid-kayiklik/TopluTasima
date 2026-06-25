package com.example.toplutasima.model

/**
 * Kişisel araç yolculuğu veri modeli.
 * Firestore "personaltrips" koleksiyonunda saklanır.
 * Toplu taşıma kayıtlarından (trips) tamamen bağımsızdır.
 */
data class PersonalTrip(
    val id: String = "",
    val firestoreDocId: String = "",    // Firestore doküman ID'si (güncelleme/silme için)

    // ── Temel Bilgiler ─────────────────────────────────────────────────────
    val tarih: String = "",             // "DD.MM.YYYY"
    val aracTuru: String = "",          // "Otomobil", "Taksi", vb.
    val plaka: String = "",
    val plakaUlkesi: String = PlateCountries.DEFAULT,
    val havaDurumu: String = "Bilinmiyor",

    // ── Kalkış ─────────────────────────────────────────────────────────────
    val kaldigiYer: String = "",        // Geocoder sokak adresi
    val kaldigiLat: Double? = null,
    val kaldigiLng: Double? = null,
    val kaldigiSaat: String = "",       // "HH:mm"

    // ── Varış ──────────────────────────────────────────────────────────────
    val varisYeri: String = "",         // Geocoder sokak adresi
    val varisLat: Double? = null,
    val varisLng: Double? = null,
    val varisSaat: String = "",         // "HH:mm"

    // ── Hesaplanan Değerler ─────────────────────────────────────────────────
    val mesafe: String = "",            // "18.4 km" — ORS yol mesafesi
    val yolSuresi: String = "",         // "45" — dakika (kaldigiSaat-varisSaat farkından)

    // ── Ek Bilgiler ─────────────────────────────────────────────────────────
    val surucu: Boolean? = null,        // Sürücü ben miydim?
    val yolcuSayisi: Int? = null,
    val not: String = "",

    // ── Durum ───────────────────────────────────────────────────────────────
    val durum: String = DURUM_BEKLEMEDE, // "beklemede" | "aktif" | "tamamlandi"

    // ── Firestore Sıralama Alanları ─────────────────────────────────────────
    val sortDate: String = "",          // "YYYY-MM-DD"
    val yearMonth: String = "",         // "YYYY-MM"
    val createdAt: Long = 0L
) {
    companion object {
        const val DURUM_BEKLEMEDE  = "beklemede"
        const val DURUM_AKTIF      = "aktif"
        const val DURUM_TAMAMLANDI = "tamamlandi"
    }
}
