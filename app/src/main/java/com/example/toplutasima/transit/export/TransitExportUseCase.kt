package com.example.toplutasima.transit.export

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Produces a complete in-memory payload before Storage Access Framework receives a destination.
 * Data access remains the caller's responsibility and should run on IO; transformation runs here
 * on [calculationDispatcher].
 */
class TransitExportUseCase(
    private val enabled: Boolean,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val json: Json = DEFAULT_JSON
) {
    suspend fun prepare(request: TransitExportRequest): TransitExportPreparationResult {
        if (!enabled) return TransitExportPreparationResult.Disabled

        return withContext(calculationDispatcher) {
            currentCoroutineContext().ensureActive()
            validateRequest(request)

            val exportedAtEpochMillis = nowEpochMillis()
            val records = selectRecords(request)
            val dto = buildEnvelope(request, records, exportedAtEpochMillis)
            currentCoroutineContext().ensureActive()

            val bytes = when (request.format) {
                TransitExportFormat.CSV -> renderCsv(dto).toByteArray(Charsets.UTF_8)
                TransitExportFormat.JSON -> json.encodeToString(dto).toByteArray(Charsets.UTF_8)
            }
            currentCoroutineContext().ensureActive()

            val prepared = PreparedTransitExport(
                suggestedFileName = suggestedFileName(
                    request = request,
                    exportedAtEpochMillis = exportedAtEpochMillis
                ),
                mimeType = request.format.mimeType,
                bytes = bytes,
                exportedRecordCount = records.size,
                sha256 = bytes.sha256()
            )
            TransitExportPreparationResult.Ready(prepared)
        }
    }

    private fun validateRequest(request: TransitExportRequest) {
        require(request.sections.isNotEmpty()) { "At least one export section is required" }
        when (request.scope.type) {
            TransitExportScopeType.SELECTED_MONTH -> parseYearMonth(
                requireNotNull(request.scope.selectedMonthIso) {
                    "selectedMonthIso is required for SELECTED_MONTH"
                }
            )

            TransitExportScopeType.DATE_RANGE -> {
                val start = parseDate(requireNotNull(request.scope.startDateIso) {
                    "startDateIso is required for DATE_RANGE"
                })
                val end = parseDate(requireNotNull(request.scope.endDateIso) {
                    "endDateIso is required for DATE_RANGE"
                })
                require(!end.isBefore(start)) { "Export end date must not be before start date" }
            }

            TransitExportScopeType.FILTERED -> {
                val start = request.scope.startDateIso?.let(::parseDate)
                val end = request.scope.endDateIso?.let(::parseDate)
                require(start == null || end == null || !end.isBefore(start)) {
                    "Export end date must not be before start date"
                }
            }

            TransitExportScopeType.ALL_TRANSIT -> Unit
        }
        require(request.insights.all { it.recordCount >= 0 }) {
            "Insight record counts must not be negative"
        }
        request.healthSummary?.let { health ->
            require(
                health.scannedRecordCount >= 0 &&
                    health.healthyRecordCount >= 0 &&
                    health.informationalIssueCount >= 0 &&
                    health.warningIssueCount >= 0 &&
                    health.criticalIssueCount >= 0
            ) { "Health summary counts must not be negative" }
        }
    }

    private suspend fun selectRecords(request: TransitExportRequest): List<TransitExportRecord> {
        val month = request.scope.selectedMonthIso?.let(::parseYearMonth)
        val start = request.scope.startDateIso?.let(::parseDate)
        val end = request.scope.endDateIso?.let(::parseDate)
        val selected = ArrayList<TransitExportRecord>(request.records.size)

        request.records.forEachIndexed { index, record ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) {
                currentCoroutineContext().ensureActive()
            }
            if (record.isTombstoned || record.localRecordId in request.tombstonedRecordIds) {
                return@forEachIndexed
            }

            val recordDate = record.dateIso.toLocalDateOrNull()
            val inScope = when (request.scope.type) {
                TransitExportScopeType.SELECTED_MONTH ->
                    recordDate != null && YearMonth.from(recordDate) == month

                TransitExportScopeType.DATE_RANGE ->
                    recordDate != null && recordDate.inRange(start, end)

                TransitExportScopeType.ALL_TRANSIT -> true
                TransitExportScopeType.FILTERED ->
                    record.matchesFilter && (
                        (start == null && end == null) ||
                            (recordDate != null && recordDate.inRange(start, end))
                        )
            }
            if (inScope) selected += record
        }
        return selected
    }

    private suspend fun buildEnvelope(
        request: TransitExportRequest,
        records: List<TransitExportRecord>,
        exportedAtEpochMillis: Long
    ): TransitExportEnvelopeDto {
        val range = metadataRange(request.scope, records)
        val recordDtos = if (TransitExportSection.RECORDS in request.sections) {
            ArrayList<TransitExportRecordDto>(records.size).also { destination ->
                records.forEachIndexed { index, record ->
                    if (index % CANCELLATION_CHECK_INTERVAL == 0) {
                        currentCoroutineContext().ensureActive()
                    }
                    destination += record.toDto()
                }
            }
        } else {
            emptyList()
        }

        return TransitExportEnvelopeDto(
            metadata = TransitExportMetadataDto(
                formatVersion = EXPORT_FORMAT_VERSION,
                exportedAt = Instant.ofEpochMilli(exportedAtEpochMillis).toString(),
                scope = request.scope.type.name.lowercase(Locale.ROOT),
                startDate = range.first,
                endDate = range.second,
                recordCount = records.size,
                filter = request.scope.filterDescription,
                sort = request.scope.sortDescription,
                includedSections = request.sections
                    .sortedBy { it.ordinal }
                    .map { it.name.lowercase(Locale.ROOT) }
            ),
            records = recordDtos,
            summary = if (TransitExportSection.SUMMARY in request.sections) {
                request.summary.sortedBy { it.id }.map {
                    TransitExportMetricDto(it.id, it.label, it.value, it.unit)
                }
            } else {
                emptyList()
            },
            insights = if (TransitExportSection.INSIGHTS in request.sections) {
                request.insights.map {
                    TransitExportInsightDto(
                        id = it.id,
                        title = it.title,
                        result = it.result,
                        period = it.period,
                        confidence = it.confidence,
                        explanation = it.explanation,
                        recordCount = it.recordCount
                    )
                }
            } else {
                emptyList()
            },
            dataHealth = if (TransitExportSection.DATA_HEALTH in request.sections) {
                request.healthSummary?.let {
                    TransitExportHealthSummaryDto(
                        scannedRecordCount = it.scannedRecordCount,
                        healthyRecordCount = it.healthyRecordCount,
                        informationalIssueCount = it.informationalIssueCount,
                        warningIssueCount = it.warningIssueCount,
                        criticalIssueCount = it.criticalIssueCount
                    )
                }
            } else {
                null
            }
        )
    }

    private fun TransitExportRecord.toDto(): TransitExportRecordDto = TransitExportRecordDto(
        localRecordId = localRecordId,
        date = dateIso,
        line = line,
        boardingStop = boardingStop,
        alightingStop = alightingStop,
        plannedDeparture = plannedDeparture,
        actualDeparture = actualDeparture,
        plannedArrival = plannedArrival,
        actualArrival = actualArrival,
        plannedDurationMinutes = plannedDurationMinutes,
        actualDurationMinutes = actualDurationMinutes,
        delayMinutes = delayMinutes,
        distanceKm = distanceKm?.takeIf { it.isFinite() },
        recordType = recordType,
        recordOrigin = origin.name.lowercase(Locale.ROOT),
        syncStatus = syncPhase?.name?.lowercase(Locale.ROOT) ?: UNKNOWN,
        healthStatus = healthSeverity?.name?.lowercase(Locale.ROOT) ?: UNKNOWN,
        provenance = normalizedProvenance(provenance),
        note = note
    )

    private fun normalizedProvenance(
        provenance: List<TransitExportProvenanceInput>
    ): List<TransitExportProvenanceDto> = provenance
        .associateBy { it.fieldId }
        .toSortedMap()
        .map { (fieldId, input) ->
            val durable = input.evidence == TransitExportProvenanceEvidence.RECORD_DERIVED
            TransitExportProvenanceDto(
                fieldId = fieldId,
                source = if (durable) input.source.exportName() else UNKNOWN,
                freshness = if (durable) input.freshness.exportName() else UNKNOWN,
                lastUpdatedAt = if (durable) {
                    input.lastUpdatedAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() }
                } else {
                    null
                },
                fallback = durable && input.isFallback,
                backingSource = if (durable) input.backingSource?.exportName() else null,
                fallbackFor = if (durable) input.fallbackFor?.exportName() else null
            )
        }

    private suspend fun renderCsv(envelope: TransitExportEnvelopeDto): String {
        val csv = StringBuilder(CSV_INITIAL_CAPACITY)
        csv.append(UTF8_BOM)
        appendCsvRow(csv, listOf("section", "key", "value"), userControlled = false)
        appendCsvRow(csv, listOf("metadata", "schema", envelope.metadata.schema))
        appendCsvRow(csv, listOf("metadata", "format_version", envelope.metadata.formatVersion.toString()))
        appendCsvRow(csv, listOf("metadata", "exported_at", envelope.metadata.exportedAt))
        appendCsvRow(csv, listOf("metadata", "scope", envelope.metadata.scope))
        appendCsvRow(csv, listOf("metadata", "start_date", envelope.metadata.startDate.orEmpty()))
        appendCsvRow(csv, listOf("metadata", "end_date", envelope.metadata.endDate.orEmpty()))
        appendCsvRow(csv, listOf("metadata", "record_count", envelope.metadata.recordCount.toString()))
        appendCsvRow(csv, listOf("metadata", "filter", envelope.metadata.filter.orEmpty()))
        appendCsvRow(csv, listOf("metadata", "sort", envelope.metadata.sort.orEmpty()))
        appendCsvRow(
            csv,
            listOf("metadata", "included_sections", envelope.metadata.includedSections.joinToString(";"))
        )

        if (envelope.metadata.includedSections.contains(TransitExportSection.RECORDS.exportName())) {
            csv.append(CRLF)
            csv.append("records").append(CRLF)
            appendCsvRow(csv, RECORD_HEADERS, userControlled = false)
            envelope.records.forEachIndexed { index, record ->
                if (index % CANCELLATION_CHECK_INTERVAL == 0) {
                    currentCoroutineContext().ensureActive()
                }
                appendCsvRecordRow(csv, record)
            }
        }

        if (envelope.metadata.includedSections.contains(TransitExportSection.SUMMARY.exportName())) {
            csv.append(CRLF).append("summary").append(CRLF)
            appendCsvRow(csv, listOf("metric_id", "label", "value", "unit"), false)
            envelope.summary.forEach {
                appendCsvRow(csv, listOf(it.id, it.label, it.value, it.unit.orEmpty()))
            }
        }

        if (envelope.metadata.includedSections.contains(TransitExportSection.INSIGHTS.exportName())) {
            csv.append(CRLF).append("insights").append(CRLF)
            appendCsvRow(
                csv,
                listOf(
                    "insight_id",
                    "title",
                    "result",
                    "period",
                    "confidence",
                    "record_count",
                    "explanation"
                ),
                false
            )
            envelope.insights.forEach {
                appendCsvRow(
                    csv,
                    listOf(
                        it.id,
                        it.title,
                        it.result,
                        it.period,
                        it.confidence,
                        it.recordCount.toString(),
                        it.explanation
                    )
                )
            }
        }

        if (envelope.metadata.includedSections.contains(TransitExportSection.DATA_HEALTH.exportName())) {
            envelope.dataHealth?.let { health ->
                csv.append(CRLF).append("data_health").append(CRLF)
                appendCsvRow(csv, listOf("metric", "value"), false)
                appendCsvRow(csv, listOf("scanned_record_count", health.scannedRecordCount.toString()))
                appendCsvRow(csv, listOf("healthy_record_count", health.healthyRecordCount.toString()))
                appendCsvRow(csv, listOf("informational_issue_count", health.informationalIssueCount.toString()))
                appendCsvRow(csv, listOf("warning_issue_count", health.warningIssueCount.toString()))
                appendCsvRow(csv, listOf("critical_issue_count", health.criticalIssueCount.toString()))
            }
        }

        return csv.toString()
    }

    private fun appendCsvRow(
        target: StringBuilder,
        fields: List<String>,
        userControlled: Boolean = true
    ) {
        fields.forEachIndexed { index, field ->
            if (index > 0) target.append(CSV_DELIMITER)
            target.append(escapeCsv(field, userControlled))
        }
        target.append(CRLF)
    }

    private fun appendCsvRecordRow(target: StringBuilder, record: TransitExportRecordDto) {
        val fields = listOf(
            record.localRecordId to true,
            record.date to false,
            record.line.orEmpty() to true,
            record.boardingStop.orEmpty() to true,
            record.alightingStop.orEmpty() to true,
            record.plannedDeparture.orEmpty() to true,
            record.actualDeparture.orEmpty() to true,
            record.plannedArrival.orEmpty() to true,
            record.actualArrival.orEmpty() to true,
            record.plannedDurationMinutes?.toString().orEmpty() to false,
            record.actualDurationMinutes?.toString().orEmpty() to false,
            record.delayMinutes?.toString().orEmpty() to false,
            record.distanceKm.machineDecimal() to false,
            record.recordType.orEmpty() to true,
            record.syncStatus to false,
            record.healthStatus to false,
            provenanceSummary(record.provenance) to false,
            record.note.orEmpty() to true
        )
        fields.forEachIndexed { index, (value, userControlled) ->
            if (index > 0) target.append(CSV_DELIMITER)
            target.append(escapeCsv(value, userControlled))
        }
        target.append(CRLF)
    }

    /** Quotes delimiter/newline characters and neutralizes spreadsheet formula prefixes. */
    internal fun escapeCsv(value: String, userControlled: Boolean = true): String {
        val startsLikeFormula = value.dropWhile {
            it.isWhitespace() || Character.isSpaceChar(it)
        }.firstOrNull()
            ?.let { it in FORMULA_PREFIXES }
            ?: false
        val safe = if (userControlled && startsLikeFormula) {
            "'$value"
        } else {
            value
        }
        val mustQuote = safe.any {
            it == CSV_DELIMITER || it == ';' || it == '\"' || it == '\n' || it == '\r'
        }
        return if (mustQuote) "\"${safe.replace("\"", "\"\"")}\"" else safe
    }

    private fun provenanceSummary(values: List<TransitExportProvenanceDto>): String =
        if (values.isEmpty()) {
            UNKNOWN
        } else {
            values.joinToString(";") { provenance ->
                buildString {
                    append(provenance.fieldId)
                    append('=')
                    append(provenance.source)
                    if (provenance.fallback) append("(fallback)")
                }
            }
        }

    private fun metadataRange(
        scope: TransitExportScope,
        records: List<TransitExportRecord>
    ): Pair<String?, String?> = when (scope.type) {
        TransitExportScopeType.SELECTED_MONTH -> {
            val month = parseYearMonth(requireNotNull(scope.selectedMonthIso))
            month.atDay(1).toString() to month.atEndOfMonth().toString()
        }

        TransitExportScopeType.DATE_RANGE -> scope.startDateIso to scope.endDateIso
        TransitExportScopeType.FILTERED -> {
            val validDates = records.mapNotNull { it.dateIso.toLocalDateOrNull() }
            (scope.startDateIso ?: validDates.minOrNull()?.toString()) to
                (scope.endDateIso ?: validDates.maxOrNull()?.toString())
        }

        TransitExportScopeType.ALL_TRANSIT -> {
            val validDates = records.mapNotNull { it.dateIso.toLocalDateOrNull() }
            validDates.minOrNull()?.toString() to validDates.maxOrNull()?.toString()
        }
    }

    private fun suggestedFileName(
        request: TransitExportRequest,
        exportedAtEpochMillis: Long
    ): String {
        val scope = when (request.scope.type) {
            TransitExportScopeType.SELECTED_MONTH -> request.scope.selectedMonthIso.orEmpty()
            TransitExportScopeType.DATE_RANGE ->
                "${request.scope.startDateIso}_${request.scope.endDateIso}"
            TransitExportScopeType.ALL_TRANSIT -> "all"
            TransitExportScopeType.FILTERED -> "filtered"
        }.replace(Regex("[^A-Za-z0-9_-]"), "-")
        val timestamp = Instant.ofEpochMilli(exportedAtEpochMillis)
            .atZone(ZoneOffset.UTC)
            .format(FILE_TIMESTAMP_FORMATTER)
        return "transit_${scope}_${timestamp}.${request.format.extension}"
    }

    private fun Double?.machineDecimal(): String = when {
        this == null || !isFinite() -> ""
        else -> BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
    }

    private fun LocalDate.inRange(start: LocalDate?, end: LocalDate?): Boolean =
        (start == null || !isBefore(start)) && (end == null || !isAfter(end))

    private fun String.toLocalDateOrNull(): LocalDate? = try {
        LocalDate.parse(this)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (error: DateTimeParseException) {
        throw IllegalArgumentException("Expected ISO date but was '$value'", error)
    }

    private fun parseYearMonth(value: String): YearMonth = try {
        YearMonth.parse(value)
    } catch (error: DateTimeParseException) {
        throw IllegalArgumentException("Expected ISO year-month but was '$value'", error)
    }

    private fun Enum<*>.exportName(): String = name.lowercase(Locale.ROOT)

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

    companion object {
        const val EXPORT_FORMAT_VERSION: Int = 1
        private const val UNKNOWN = "unknown"
        private const val UTF8_BOM = "\uFEFF"
        private const val CSV_DELIMITER = ','
        private const val CRLF = "\r\n"
        private const val CANCELLATION_CHECK_INTERVAL = 128
        private const val CSV_INITIAL_CAPACITY = 8 * 1024
        private val FORMULA_PREFIXES = setOf('=', '+', '-', '@')
        private val FILE_TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss")
        private val RECORD_HEADERS = listOf(
            "local_record_id",
            "date",
            "line",
            "boarding_stop",
            "alighting_stop",
            "planned_departure",
            "actual_departure",
            "planned_arrival",
            "actual_arrival",
            "planned_duration_minutes",
            "actual_duration_minutes",
            "delay_minutes",
            "distance_km",
            "record_type",
            "sync_status",
            "health_status",
            "provenance_summary",
            "note"
        )
        private val DEFAULT_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
