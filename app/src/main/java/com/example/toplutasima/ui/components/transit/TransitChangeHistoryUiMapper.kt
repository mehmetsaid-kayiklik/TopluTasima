package com.example.toplutasima.ui.components.transit

import com.example.toplutasima.transit.history.TransitChangeEvent
import com.example.toplutasima.transit.history.TransitChangeOperation
import com.example.toplutasima.transit.history.TransitChangeSource
import com.example.toplutasima.transit.history.TransitFieldChange
import com.example.toplutasima.transit.history.TransitHistoryProvenanceSource
import com.example.toplutasima.transit.history.TransitHistorySyncStatus
import com.example.toplutasima.ui.AppLanguage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object TransitChangeHistoryUiMapper {
    fun operationLabel(operation: TransitChangeOperation, lang: AppLanguage): String = when (operation) {
        TransitChangeOperation.CREATE -> tr(lang, "Kayıt oluşturuldu", "Eintrag erstellt", "Record created")
        TransitChangeOperation.MANUAL_EDIT -> tr(lang, "Manuel düzenleme", "Manuell bearbeitet", "Manual edit")
        TransitChangeOperation.REMOTE_UPDATE -> tr(
            lang,
            "Başka cihazdan güncellendi",
            "Von einem anderen Gerät aktualisiert",
            "Updated from another device"
        )
        TransitChangeOperation.AUTOMATIC_HEALTH_CORRECTION -> tr(
            lang,
            "Güvenli veri düzeltmesi",
            "Sichere Datenkorrektur",
            "Safe data correction"
        )
        TransitChangeOperation.USER_APPROVED_BULK_CORRECTION -> tr(
            lang,
            "Onaylı toplu düzeltme",
            "Bestätigte Sammelkorrektur",
            "Approved bulk correction"
        )
        TransitChangeOperation.LOCAL_DELETE -> tr(lang, "Yerelde silindi", "Lokal gelöscht", "Deleted locally")
        TransitChangeOperation.DELETE_SYNC -> tr(lang, "Silme senkronu", "Löschung synchronisiert", "Deletion sync")
        TransitChangeOperation.DELETE_ROLLBACK -> tr(
            lang,
            "Başarısız silme geri alındı",
            "Fehlgeschlagene Löschung zurückgenommen",
            "Failed deletion rolled back"
        )
        TransitChangeOperation.PROVENANCE_CHANGE -> tr(
            lang,
            "Veri kaynağı değişti",
            "Datenquelle geändert",
            "Data source changed"
        )
        TransitChangeOperation.DUPLICATE_MERGE -> tr(
            lang,
            "Tekrar kayıtlar birleştirildi",
            "Duplikate zusammengeführt",
            "Duplicate records merged"
        )
        TransitChangeOperation.UNDO -> tr(lang, "Değişiklik geri alındı", "Änderung rückgängig", "Change undone")
    }

    fun sourceLabel(source: TransitChangeSource, lang: AppLanguage): String = when (source) {
        TransitChangeSource.USER -> tr(lang, "Kullanıcı", "Benutzer", "User")
        TransitChangeSource.RMV -> "RMV"
        TransitChangeSource.DATA_HEALTH -> tr(lang, "Veri sağlığı", "Datenqualität", "Data health")
        TransitChangeSource.SYNC_WORKER -> tr(lang, "Senkronizasyon", "Synchronisierung", "Synchronization")
        TransitChangeSource.REMOTE -> tr(lang, "Başka cihaz veya bulut", "Anderes Gerät oder Cloud", "Another device or cloud")
        TransitChangeSource.DUPLICATE_RESOLUTION -> tr(
            lang,
            "Tekrar kayıt çözümleme",
            "Duplikatauflösung",
            "Duplicate resolution"
        )
        TransitChangeSource.SYSTEM -> tr(lang, "Sistem", "System", "System")
        TransitChangeSource.UNKNOWN -> tr(lang, "Bilinmiyor", "Unbekannt", "Unknown")
    }

    fun syncStatusLabel(status: TransitHistorySyncStatus, lang: AppLanguage): String = when (status) {
        TransitHistorySyncStatus.NOT_APPLICABLE -> tr(lang, "Senkron durumu yok", "Kein Synchronstatus", "No sync status")
        TransitHistorySyncStatus.LOCAL_ONLY -> tr(lang, "Yalnız yerel", "Nur lokal", "Local only")
        TransitHistorySyncStatus.PENDING -> tr(lang, "Senkron bekliyor", "Synchronisierung ausstehend", "Sync pending")
        TransitHistorySyncStatus.SYNCING -> tr(lang, "Senkronlanıyor", "Wird synchronisiert", "Syncing")
        TransitHistorySyncStatus.SYNCED -> tr(lang, "Senkronlandı", "Synchronisiert", "Synced")
        TransitHistorySyncStatus.TEMPORARY_ERROR -> tr(lang, "Geçici senkron hatası", "Temporärer Synchronfehler", "Temporary sync error")
        TransitHistorySyncStatus.PERMANENT_ERROR -> tr(lang, "Kalıcı senkron hatası", "Dauerhafter Synchronfehler", "Permanent sync error")
    }

    fun fieldLabel(fieldId: String, lang: AppLanguage): String = when (fieldId) {
        "tarih" -> tr(lang, "Tarih", "Datum", "Date")
        "tur" -> tr(lang, "Kayıt türü", "Eintragstyp", "Record type")
        "hat" -> tr(lang, "Hat", "Linie", "Line")
        "binisDuragi" -> tr(lang, "Başlangıç durağı", "Einstiegshaltestelle", "Origin stop")
        "inisDuragi" -> tr(lang, "Varış durağı", "Ausstiegshaltestelle", "Destination stop")
        "planlananBinis" -> tr(lang, "Planlanan kalkış", "Geplante Abfahrt", "Planned departure")
        "gercekBinis" -> tr(lang, "Gerçek kalkış", "Tatsächliche Abfahrt", "Actual departure")
        "planlananInis" -> tr(lang, "Planlanan varış", "Geplante Ankunft", "Planned arrival")
        "gercekInis" -> tr(lang, "Gerçek varış", "Tatsächliche Ankunft", "Actual arrival")
        "planlananYolSuresi" -> tr(lang, "Planlanan süre", "Geplante Dauer", "Planned duration")
        "gercekYolSuresi" -> tr(lang, "Gerçek süre", "Tatsächliche Dauer", "Actual duration")
        "gecikme" -> tr(lang, "Gecikme", "Verspätung", "Delay")
        "mesafe" -> tr(lang, "Mesafe", "Entfernung", "Distance")
        "orsMesafeKm" -> tr(lang, "ORS mesafesi", "ORS-Entfernung", "ORS distance")
        "rmvMesafeKm" -> tr(lang, "RMV mesafesi", "RMV-Entfernung", "RMV distance")
        "not" -> tr(lang, "Not", "Notiz", "Note")
        else -> fieldId
    }

    fun provenanceLabel(source: TransitHistoryProvenanceSource, lang: AppLanguage): String = when (source) {
        TransitHistoryProvenanceSource.LIVE_RMV -> tr(lang, "Canlı RMV", "RMV Live", "Live RMV")
        TransitHistoryProvenanceSource.PLANNED_RMV -> tr(lang, "Planlanan RMV", "RMV Plandaten", "Planned RMV")
        TransitHistoryProvenanceSource.TRANSIT_LOCATION -> tr(lang, "Transit konumu", "Transit-Position", "Transit location")
        TransitHistoryProvenanceSource.RMV_DISTANCE -> tr(lang, "RMV mesafesi", "RMV-Entfernung", "RMV distance")
        TransitHistoryProvenanceSource.ORS_DISTANCE -> tr(lang, "ORS mesafesi", "ORS-Entfernung", "ORS distance")
        TransitHistoryProvenanceSource.CACHE -> tr(lang, "Önbellek", "Cache", "Cache")
        TransitHistoryProvenanceSource.MANUAL -> tr(lang, "Manuel giriş", "Manuelle Eingabe", "Manual input")
        TransitHistoryProvenanceSource.UNKNOWN -> tr(lang, "Bilinmiyor", "Unbekannt", "Unknown")
    }

    fun changeDescription(change: TransitFieldChange, lang: AppLanguage): String {
        val field = fieldLabel(change.fieldId, lang)
        val oldValue = change.oldValue.displayValue(
            unknownLabel = tr(lang, "Bilinmiyor", "Unbekannt", "Unknown"),
            emptyLabel = tr(lang, "Boş", "Leer", "Empty")
        )
        val newValue = change.newValue.displayValue(
            unknownLabel = tr(lang, "Bilinmiyor", "Unbekannt", "Unknown"),
            emptyLabel = tr(lang, "Boş", "Leer", "Empty")
        )
        val valueChange = tr(
            lang,
            "$field: $oldValue değerinden $newValue değerine",
            "$field: von $oldValue zu $newValue",
            "$field: from $oldValue to $newValue"
        )
        val oldSource = change.oldProvenance?.source
        val newSource = change.newProvenance?.source
        if (oldSource == newSource || (oldSource == null && newSource == null)) return valueChange

        val sourceChange = tr(
            lang,
            "kaynak ${oldSource?.let { provenanceLabel(it, lang) } ?: "Bilinmiyor"} değerinden " +
                "${newSource?.let { provenanceLabel(it, lang) } ?: "Bilinmiyor"} değerine",
            "Quelle von ${oldSource?.let { provenanceLabel(it, lang) } ?: "Unbekannt"} zu " +
                "${newSource?.let { provenanceLabel(it, lang) } ?: "Unbekannt"}",
            "source from ${oldSource?.let { provenanceLabel(it, lang) } ?: "Unknown"} to " +
                "${newSource?.let { provenanceLabel(it, lang) } ?: "Unknown"}"
        )
        return "$valueChange; $sourceChange"
    }

    fun shortSummary(event: TransitChangeEvent, lang: AppLanguage): String {
        if (event.changes.isEmpty()) return operationLabel(event.operation, lang)
        val visible = event.changes.take(SUMMARY_CHANGE_LIMIT)
            .joinToString(separator = "; ") { changeDescription(it, lang) }
        val remaining = event.changes.size - visibleCount(event)
        if (remaining <= 0) return visible
        return visible + tr(
            lang,
            "; $remaining değişiklik daha",
            "; $remaining weitere Änderungen",
            "; $remaining more changes"
        )
    }

    fun eventContentDescription(
        event: TransitChangeEvent,
        lang: AppLanguage,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String = listOf(
        operationLabel(event.operation, lang),
        formatTimestamp(event.occurredAtEpochMillis, zoneId),
        tr(lang, "Kaynak: ${sourceLabel(event.source, lang)}", "Quelle: ${sourceLabel(event.source, lang)}", "Source: ${sourceLabel(event.source, lang)}"),
        syncStatusLabel(event.syncStatus, lang),
        event.changes.joinToString(separator = "; ") { changeDescription(it, lang) }
            .ifBlank { tr(lang, "Alan değişikliği yok", "Keine Feldänderung", "No field changes") }
    ).joinToString(separator = ". ")

    fun formatTimestamp(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        runCatching {
            Instant.ofEpochMilli(epochMillis.coerceAtLeast(0L))
                .atZone(zoneId)
                .format(TIMESTAMP_FORMATTER)
        }.getOrDefault("—")

    private fun visibleCount(event: TransitChangeEvent): Int = event.changes.size.coerceAtMost(SUMMARY_CHANGE_LIMIT)

    private fun tr(lang: AppLanguage, tr: String, de: String, en: String): String = when (lang) {
        AppLanguage.TR -> tr
        AppLanguage.DE -> de
        AppLanguage.EN -> en
    }

    private const val SUMMARY_CHANGE_LIMIT = 2
    private val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")
}
