package com.example.toplutasima.model

/**
 * Oturma durumu için merkezi enum.
 * [key] → Firestore'da saklanan değer.
 */
enum class SeatingStatus(val key: String) {
    YES("Evet"),
    NO("Hayır");

    companion object {
        fun fromKey(key: String): SeatingStatus =
            entries.find { it.key == key } ?: NO

        fun fromBoolean(seated: Boolean): SeatingStatus =
            if (seated) YES else NO
    }
}

/**
 * Bilet kontrolü durumu için merkezi enum.
 * [key] → Firestore'da saklanan değer.
 */
enum class TicketStatus(val key: String) {
    HAPPENED("Oldu"),
    DID_NOT("Olmadı");

    companion object {
        fun fromKey(key: String): TicketStatus =
            entries.find { it.key == key } ?: DID_NOT

        fun fromBoolean(happened: Boolean): TicketStatus =
            if (happened) HAPPENED else DID_NOT
    }
}
