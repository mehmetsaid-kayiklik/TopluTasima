package com.example.toplutasima.usecase

/**
 * Veri sağlığı kontrol modülü.
 * Firestore kayıtlarında tutarsızlık, duplikasyon ve bozuk format tespiti yapar.
 * Tüm fonksiyonlar saf (pure) — test edilebilir, UI'dan bağımsız.
 */
object DataHealthChecker {

    enum class HealthIssueType {
        DUPLICATE,
        MISSING_FIELD,
        BAD_DATE,
        BAD_TIME,
        INCONSISTENT_DURATION,
        MISSING_DERIVED,
        ABNORMAL_DELAY
    }

    data class HealthIssue(
        val type: HealthIssueType,
        val docId: String,
        val tripSummary: String,   // "🚌 Bus 64 • 15.03.2025"
        val detail: String         // "Bozuk tarih: '15/03/2025'"
    )

    private val DATE_REGEX = Regex("""^\d{2}\.\d{2}\.\d{4}$""")
    private val TIME_REGEX = Regex("""^\d{1,2}:\d{2}$""")

    fun analyzeTrips(trips: List<Map<String, Any>>): List<HealthIssue> {
        val issues = mutableListOf<HealthIssue>()

        // ── Rule 1: Duplicate detection ──
        val fingerprints = mutableMapOf<String, MutableList<String>>()
        for (trip in trips) {
            val docId = trip["firestoreDocId"]?.toString() ?: trip["id"]?.toString() ?: ""
            val tarih = trip["tarih"]?.toString() ?: ""
            val hat = trip["hat"]?.toString() ?: ""
            val binis = trip["binisDuragi"]?.toString() ?: ""
            val plBinis = trip["planlananBinis"]?.toString() ?: ""
            val fp = "$tarih|$hat|$binis|$plBinis"
            fingerprints.getOrPut(fp) { mutableListOf() }.add(docId)
        }
        for ((fp, docIds) in fingerprints) {
            if (docIds.size > 1) {
                val parts = fp.split("|")
                val summary = "${parts.getOrElse(1) { "?" }} • ${parts.getOrElse(0) { "?" }}"
                for (docId in docIds) {
                    issues.add(HealthIssue(
                        type = HealthIssueType.DUPLICATE,
                        docId = docId,
                        tripSummary = summary,
                        detail = "${docIds.size}x tekrar"
                    ))
                }
            }
        }

        // Per-trip checks
        for (trip in trips) {
            val docId = trip["firestoreDocId"]?.toString() ?: trip["id"]?.toString() ?: "?"
            val tarih = trip["tarih"]?.toString() ?: ""
            val hat = trip["hat"]?.toString() ?: ""
            val tur = trip["tur"]?.toString() ?: ""
            val summary = buildTripSummary(tur, hat, tarih)

            // ── Rule 2: Missing required fields ──
            val requiredFields = listOf("hat", "binisDuragi", "inisDuragi", "tarih")
            for (field in requiredFields) {
                val value = trip[field]?.toString()
                if (value.isNullOrBlank()) {
                    issues.add(HealthIssue(
                        type = HealthIssueType.MISSING_FIELD,
                        docId = docId,
                        tripSummary = summary,
                        detail = "Eksik alan: $field"
                    ))
                }
            }

            // ── Rule 3: Bad date format ──
            if (tarih.isNotBlank() && !DATE_REGEX.matches(tarih)) {
                issues.add(HealthIssue(
                    type = HealthIssueType.BAD_DATE,
                    docId = docId,
                    tripSummary = summary,
                    detail = "Bozuk tarih: '$tarih'"
                ))
            }

            // ── Rule 4: Bad time format ──
            val timeFields = listOf("planlananBinis", "gercekBinis", "planlananInis", "gercekInis")
            for (field in timeFields) {
                val value = trip[field]?.toString()
                if (!value.isNullOrBlank() && !TIME_REGEX.matches(value)) {
                    issues.add(HealthIssue(
                        type = HealthIssueType.BAD_TIME,
                        docId = docId,
                        tripSummary = summary,
                        detail = "Bozuk saat ($field): '$value'"
                    ))
                }
            }

            // ── Rule 5: Inconsistent duration ──
            val gercekBinis = trip["gercekBinis"]?.toString()
            val gercekInis = trip["gercekInis"]?.toString()
            val storedDuration = trip["gercekYolSuresi"]?.toString()?.toIntOrNull()
            if (!gercekBinis.isNullOrBlank() && !gercekInis.isNullOrBlank() && storedDuration != null) {
                val computed = computeDurationMinutes(gercekBinis, gercekInis)
                if (computed != null && kotlin.math.abs(computed - storedDuration) > 2) {
                    issues.add(HealthIssue(
                        type = HealthIssueType.INCONSISTENT_DURATION,
                        docId = docId,
                        tripSummary = summary,
                        detail = "Süre tutarsız: kayıtlı=$storedDuration dk, hesaplanan=$computed dk"
                    ))
                }
            }

            // ── Rule 6: Missing derived fields ──
            val yearMonth = trip["yearMonth"]?.toString()
            val sortDate = trip["sortDate"]?.toString()
            if (yearMonth.isNullOrBlank() || sortDate.isNullOrBlank()) {
                val missing = mutableListOf<String>()
                if (yearMonth.isNullOrBlank()) missing.add("yearMonth")
                if (sortDate.isNullOrBlank()) missing.add("sortDate")
                issues.add(HealthIssue(
                    type = HealthIssueType.MISSING_DERIVED,
                    docId = docId,
                    tripSummary = summary,
                    detail = "Eksik türetilmiş: ${missing.joinToString(", ")}"
                ))
            }

            // ── Rule 7: Abnormal delay ──
            val gecikme = trip["gecikme"]?.toString()?.toIntOrNull() ?: 0
            if (gecikme > 60) {
                issues.add(HealthIssue(
                    type = HealthIssueType.ABNORMAL_DELAY,
                    docId = docId,
                    tripSummary = summary,
                    detail = "Anormal gecikme: $gecikme dk"
                ))
            }
        }

        return issues
    }

    /** Sorunları tip bazında gruplar */
    fun groupByType(issues: List<HealthIssue>): Map<HealthIssueType, List<HealthIssue>> {
        return issues.groupBy { it.type }
    }

    /** Sorunları özet text olarak döner: "3 yinelenen, 5 eksik alan, 2 bozuk tarih" */
    fun summaryText(issues: List<HealthIssue>): String {
        val grouped = groupByType(issues)
        return grouped.entries.joinToString(", ") { (type, list) ->
            "${list.size} ${typeLabel(type)}"
        }
    }

    fun typeLabel(type: HealthIssueType): String = when (type) {
        HealthIssueType.DUPLICATE -> "yinelenen"
        HealthIssueType.MISSING_FIELD -> "eksik alan"
        HealthIssueType.BAD_DATE -> "bozuk tarih"
        HealthIssueType.BAD_TIME -> "bozuk saat"
        HealthIssueType.INCONSISTENT_DURATION -> "tutarsız süre"
        HealthIssueType.MISSING_DERIVED -> "eksik türetilmiş"
        HealthIssueType.ABNORMAL_DELAY -> "anormal gecikme"
    }

    private fun buildTripSummary(tur: String, hat: String, tarih: String): String {
        val emoji = when (tur) {
            "Otobüs" -> "🚌"
            "S-Bahn" -> "🚆"
            "U-Bahn" -> "🚇"
            "Re/Rb" -> "🚂"
            "Fernzug" -> "🚄"
            "Straßenbahn" -> "🚋"
            else -> "🚏"
        }
        return "$emoji $hat • $tarih"
    }

    private fun computeDurationMinutes(start: String, end: String): Int? {
        return try {
            fun toMin(t: String): Int {
                val p = t.trim().split(":")
                if (p.size < 2) return 0
                return p[0].toInt() * 60 + p[1].toInt()
            }
            var diff = toMin(end) - toMin(start)
            if (diff < 0) diff += 24 * 60
            diff
        } catch (_: Exception) {
            null
        }
    }
}
