package com.example.toplutasima.model

/**
 * Araç türleri için merkezi enum.
 * [key] değeri Firestore'da saklanan ve mevcut verilerle uyumlu olan string'dir.
 * Kod içinde bu enum üzerinden karşılaştırma / atama yapılır;
 * asla literal string yazılmaz.
 */
enum class VehicleType(val key: String) {
    BUS("Otobüs"),
    SBAHN("S-Bahn"),
    UBAHN("U-Bahn"),
    RERB("Re/Rb"),
    FERNZUG("Fernzug"),
    STRASSENBAHN("Straßenbahn");

    companion object {
        /** Firestore'dan okunan string'i enum'a çevirir. Tanınmayan değer → BUS. */
        fun fromKey(key: String): VehicleType =
            entries.find { it.key == key } ?: BUS

        /** Tüm araç türlerinin [key] listesi — UI dropdown'ları için. */
        val allKeys: List<String> get() = entries.map { it.key }
    }
}
