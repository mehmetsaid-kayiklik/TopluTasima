package com.example.toplutasima.usecase

import com.example.toplutasima.viewmodel.records.DayGroup
import com.example.toplutasima.viewmodel.records.RecordRowUiModel

/**
 * Kayıt filtreleme state'i.
 * Tüm alanlar varsayılan olarak "filtre yok" durumundadır.
 */
data class RecordFilterState(
    val searchQuery: String = "",          // hat, durak adı, yön gibi serbest metin araması
    val vehicleType: String = "",          // boş = tümü
    val weather: String = "",              // boş = tümü
    val seatedFilter: Boolean? = null,     // null = tümü, true = oturdu, false = oturamadı
    val ticketFilter: Boolean? = null,     // null = tümü, true = kontrol oldu, false = olmadı
    val minDelay: Int? = null,             // gecikme alt sınırı (dk)
    val maxDelay: Int? = null,             // gecikme üst sınırı (dk)
    val stopName: String = ""              // biniş veya iniş durağı adı araması
) {
    /** Herhangi bir filtre aktif mi? */
    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() ||
                vehicleType.isNotBlank() ||
                weather.isNotBlank() ||
                seatedFilter != null ||
                ticketFilter != null ||
                minDelay != null ||
                maxDelay != null ||
                stopName.isNotBlank()

    /** Aktif filtre sayısı (chip gösterimi için) */
    val activeFilterCount: Int
        get() {
            var count = 0
            if (searchQuery.isNotBlank()) count++
            if (vehicleType.isNotBlank()) count++
            if (weather.isNotBlank()) count++
            if (seatedFilter != null) count++
            if (ticketFilter != null) count++
            if (minDelay != null || maxDelay != null) count++
            if (stopName.isNotBlank()) count++
            return count
        }
}

/**
 * Saf filtreleme fonksiyonları — state'e bağımlı değil, kolayca test edilebilir.
 */
object RecordFilterUtils {

    /**
     * Tek bir kaydı filtre kriterlerine göre değerlendirir.
     * @return true ise kayıt filtreyi geçer.
     */
    fun matchesFilter(record: RecordRowUiModel, filter: RecordFilterState): Boolean {
        // Serbest metin araması: hat, yön, biniş/iniş durağı üzerinde
        if (filter.searchQuery.isNotBlank()) {
            val q = filter.searchQuery.lowercase()
            val searchable = listOf(
                record.line, record.direction,
                record.boardingStop, record.alightingStop,
                record.type,
                record.note
            ).joinToString(" ").lowercase()
            if (!searchable.contains(q)) return false
        }

        // Araç türü filtresi
        if (filter.vehicleType.isNotBlank() && record.type != filter.vehicleType) return false

        // Hava durumu filtresi
        if (filter.weather.isNotBlank() && record.weather != filter.weather) return false

        // Oturma durumu filtresi
        if (filter.seatedFilter != null) {
            val seated = record.seated == "Evet"
            if (seated != filter.seatedFilter) return false
        }

        // Bilet kontrolü filtresi
        if (filter.ticketFilter != null) {
            val ticket = record.ticketControl == "Oldu"
            if (ticket != filter.ticketFilter) return false
        }

        // Gecikme aralığı filtresi
        val delay = record.delay.toIntOrNull() ?: 0
        if (filter.minDelay != null && delay < filter.minDelay) return false
        if (filter.maxDelay != null && delay > filter.maxDelay) return false

        // Durak adı filtresi (biniş veya iniş)
        if (filter.stopName.isNotBlank()) {
            val q = filter.stopName.lowercase()
            if (!record.boardingStop.lowercase().contains(q) &&
                !record.alightingStop.lowercase().contains(q)) return false
        }

        return true
    }

    /**
     * DayGroup listesini filtreleyerek yeni liste döndürür.
     * Boş grup kalmaz.
     */
    fun filterDayGroups(
        dayGroups: List<DayGroup>,
        filter: RecordFilterState
    ): List<DayGroup> {
        if (!filter.hasActiveFilters) return dayGroups

        return dayGroups.mapNotNull { group ->
            val filtered = group.trips.filter { matchesFilter(it, filter) }
            if (filtered.isEmpty()) null
            else group.copy(trips = filtered)
        }
    }

    /**
     * Filtrelenmiş toplam kayıt sayısını hesaplar.
     */
    fun countFilteredRecords(dayGroups: List<DayGroup>): Int =
        dayGroups.sumOf { it.trips.size }

    /**
     * Gerçek biniş veya gerçek iniş saati eksik olan kayıtları tespit eder.
     */
    fun findIncompleteRecords(dayGroups: List<DayGroup>): List<RecordRowUiModel> =
        dayGroups.flatMap { group ->
            group.trips.filter { record ->
                record.actualDep.isBlank() || record.actualArr.isBlank()
            }
        }
}
