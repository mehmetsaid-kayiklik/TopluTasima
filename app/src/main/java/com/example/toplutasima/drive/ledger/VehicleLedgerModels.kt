package com.example.toplutasima.drive.ledger

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import shared.vehicleledger.contract.OdometerQuality
import shared.vehicleledger.contract.OdometerReadingRole
import shared.vehicleledger.contract.VehicleExpenseCategory
import shared.vehicleledger.contract.VehicleExpenseContract
import shared.vehicleledger.contract.VehicleExpenseTransactionKind
import shared.vehicleledger.contract.VehicleLedgerSource
import shared.vehicleledger.contract.VehicleOdometerEntryContract
import shared.vehicleledger.contract.VehicleReminderContract
import shared.vehicleledger.contract.VehicleReminderRecurrenceAnchor
import shared.vehicleledger.contract.VehicleReminderStatus
import shared.vehicleledger.contract.VehicleReminderType

enum class VehicleLedgerSyncState {
    LOCAL_PENDING,
    SYNCED,
    RETRYABLE_ERROR,
    FATAL_ERROR,
    CONFLICT,
    UNSUPPORTED
}

enum class VehicleLedgerOperationState {
    PENDING,
    RUNNING,
    RETRY,
    SUCCEEDED,
    FATAL,
    CONFLICT,
    SUPERSEDED
}

enum class VehicleLedgerReceiptStatus {
    STARTED,
    APPLIED,
    IDEMPOTENT,
    RETRY,
    FATAL,
    CONFLICT,
    SUPERSEDED
}

enum class VehicleLedgerHealthCode {
    LEDGER_VEHICLE_NOT_FOUND,
    LEDGER_VEHICLE_DELETED,
    LEDGER_OWNER_MISMATCH,
    LEDGER_UNSUPPORTED_SCHEMA,
    LEDGER_REMOTE_HARD_DELETE,
    LEDGER_CONFLICT_UNRESOLVED,
    LEDGER_OPERATION_STUCK,
    LEDGER_RECEIPT_MISSING,
    LEDGER_UNKNOWN_PROVENANCE,
    ODOMETER_NON_MONOTONIC,
    ODOMETER_IMPLAUSIBLE_JUMP,
    ODOMETER_SERIES_INVALID,
    ODOMETER_MIRROR_STALE,
    ODOMETER_SOURCE_DUPLICATE,
    ODOMETER_LEGACY_VALUE_INVALID,
    EXPENSE_INVALID_CURRENCY,
    EXPENSE_INVALID_PERIOD,
    EXPENSE_DUPLICATE_SUSPECTED,
    EXPENSE_AMOUNT_OVERFLOW,
    REMINDER_NO_TRIGGER,
    REMINDER_RECURRENCE_INVALID,
    REMINDER_OVERDUE,
    REMINDER_ODOMETER_STALE
}

sealed interface VehicleLedgerFailure {
    data object FeatureDisabled : VehicleLedgerFailure
    data object AuthenticationChanged : VehicleLedgerFailure
    data object AccountMismatch : VehicleLedgerFailure
    data object VehicleNotFound : VehicleLedgerFailure
    data object VehicleDeleted : VehicleLedgerFailure
    data object RecordNotFound : VehicleLedgerFailure
    data object RecordDeleted : VehicleLedgerFailure
    data object RevisionOverflow : VehicleLedgerFailure
    data class Validation(val fields: Set<String>) : VehicleLedgerFailure
    data object Conflict : VehicleLedgerFailure
    data object UnsupportedSchema : VehicleLedgerFailure
    data object RetryableStorage : VehicleLedgerFailure
    data object FatalStorage : VehicleLedgerFailure
}

sealed interface VehicleLedgerMutationResult<out T> {
    data class Success<T>(val value: T) : VehicleLedgerMutationResult<T>
    data class LocalSavedSyncSchedulingFailed<T>(val value: T) : VehicleLedgerMutationResult<T>
    data class Rejected(val failure: VehicleLedgerFailure) : VehicleLedgerMutationResult<Nothing>
}

data class OdometerDraft(
    val vehicleId: String,
    val observedAt: Long?,
    val odometerMeters: Long,
    val quality: OdometerQuality = OdometerQuality.CONFIRMED,
    val readingRole: OdometerReadingRole = OdometerReadingRole.MANUAL,
    val odometerSeriesId: String,
    val sourceRecordType: String? = null,
    val sourceRecordId: String? = null,
    val correctionOfEntryId: String? = null,
    val resetReason: String? = null,
    val notes: String? = null,
    val source: VehicleLedgerSource = VehicleLedgerSource.MANUAL
)

data class ExpenseDraft(
    val vehicleId: String,
    val occurredAt: Long,
    val category: VehicleExpenseCategory,
    val transactionKind: VehicleExpenseTransactionKind,
    val amountMinor: Long,
    val currencyCode: String,
    val currencyExponent: Int,
    val vendorName: String? = null,
    val notes: String? = null,
    val referenceNumber: String? = null,
    val periodStartEpochDay: Long? = null,
    val periodEndEpochDay: Long? = null,
    val dueEpochDay: Long? = null,
    val odometerEntryId: String? = null,
    val odometerMetersSnapshot: Long? = null,
    val splitGroupId: String? = null,
    val duplicateFingerprint: String? = null,
    val relatedExpenseId: String? = null,
    val source: VehicleLedgerSource = VehicleLedgerSource.MANUAL
)

data class ReminderDraft(
    val vehicleId: String,
    val title: String,
    val reminderType: VehicleReminderType,
    val status: VehicleReminderStatus = VehicleReminderStatus.ACTIVE,
    val dueEpochDay: Long? = null,
    val dueOdometerMeters: Long? = null,
    val recurrenceMonths: Int? = null,
    val recurrenceDistanceMeters: Long? = null,
    val recurrenceAnchor: VehicleReminderRecurrenceAnchor =
        VehicleReminderRecurrenceAnchor.LAST_COMPLETION,
    val leadDays: Int? = null,
    val leadDistanceMeters: Long? = null,
    val snoozedUntilEpochDay: Long? = null,
    val linkedServiceRecordId: String? = null,
    val lastCompletedServiceRecordId: String? = null,
    val lastCompletedAt: Long? = null,
    val lastCompletedOdometerMeters: Long? = null,
    val notes: String? = null,
    val source: VehicleLedgerSource = VehicleLedgerSource.MANUAL
)

data class CurrentOdometerProjection(
    val entry: VehicleOdometerEntryContract?,
    val meters: Long?,
    val isEstimated: Boolean,
    val mirrorStale: Boolean
)

enum class ReminderDueTrigger {
    DATE,
    ODOMETER,
    BOTH
}

data class DueReminderProjection(
    val reminder: VehicleReminderContract,
    val trigger: ReminderDueTrigger
)

data class ExpenseSummary(
    val category: VehicleExpenseCategory,
    val currencyCode: String,
    val currencyExponent: Int,
    val signedAmountMinor: Long,
    val recordCount: Int
)

data class VehicleLedgerDashboard(
    val currentOdometer: CurrentOdometerProjection,
    val dueReminderCount: Int,
    val monthExpenseTotals: List<ExpenseSummary>,
    val pendingOperationCount: Int,
    val unresolvedConflictCount: Int
)

data class VehicleLedgerHealthIssue(
    val entityType: String,
    val recordId: String,
    val vehicleId: String,
    val code: VehicleLedgerHealthCode
)

object VehicleLedgerUnits {
    private val thousand = BigDecimal.valueOf(1_000L)
    private val metersPerMile = BigDecimal("1609.344")

    fun legacyKilometersToMeters(value: Double?): Long? {
        if (value == null || !value.isFinite() || value < 0.0) return null
        return try {
            BigDecimal.valueOf(value)
                .multiply(thousand)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun decimalKilometersToMeters(value: String): Long? = try {
        BigDecimal(value.trim().replace(',', '.'))
            .multiply(thousand)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
            .takeIf { it >= 0L }
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    fun decimalMilesToMeters(value: String): Long? = try {
        BigDecimal(value.trim().replace(',', '.'))
            .multiply(metersPerMile)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
            .takeIf { it >= 0L }
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    fun metersToKilometers(value: Long): BigDecimal =
        BigDecimal.valueOf(value).divide(thousand)

    fun metersToCompatibilityKilometers(value: Long): Double =
        metersToKilometers(value).toDouble()

    fun decimalMoneyToMinor(value: String, exponent: Int): Long? {
        if (exponent !in 0..4) return null
        return try {
            BigDecimal(value.trim().replace(',', '.'))
                .movePointRight(exponent)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
                .takeIf { it > 0L }
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }
}

object VehicleExpenseFingerprint {
    fun compute(draft: ExpenseDraft): String {
        val canonical = listOf(
            draft.vehicleId,
            (draft.occurredAt / 60_000L).toString(),
            draft.category.name,
            draft.transactionKind.name,
            draft.amountMinor.toString(),
            draft.currencyCode.trim().uppercase(Locale.ROOT),
            draft.currencyExponent.toString(),
            draft.referenceNumber?.trim()?.lowercase(Locale.ROOT).orEmpty(),
            draft.vendorName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        ).joinToString("\u001f")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
    }
}

object CurrentOdometerSelector {
    fun select(entries: List<VehicleOdometerEntryContract>): VehicleOdometerEntryContract? {
        val dated = entries.filter { it.envelope.deletedAt == null && it.observedAt != null }
        val activeSeries = dated.maxWithOrNull(
            compareBy<VehicleOdometerEntryContract> { it.observedAt }
                .thenBy { it.envelope.clientUpdatedAt }
                .thenBy { it.odometerEntryId }
        )?.odometerSeriesId ?: return null
        val inSeries = dated.filter { it.odometerSeriesId == activeSeries }
        val confirmed = inSeries.filter { it.quality == OdometerQuality.CONFIRMED }
        val candidates = confirmed.ifEmpty { inSeries.filter { it.quality == OdometerQuality.ESTIMATED } }
        return candidates.maxWithOrNull(
            compareBy<VehicleOdometerEntryContract> { it.observedAt }
                .thenBy { it.envelope.serverUpdatedAt }
                .thenBy { it.envelope.operationId }
                .thenBy { it.odometerEntryId }
        )
    }
}

object VehicleReminderDuePolicy {
    fun evaluate(
        reminder: VehicleReminderContract,
        todayEpochDay: Long,
        currentOdometerMeters: Long?
    ): DueReminderProjection? {
        if (reminder.envelope.deletedAt != null ||
            reminder.status !in setOf(VehicleReminderStatus.ACTIVE, VehicleReminderStatus.SNOOZED)
        ) return null
        if (reminder.status == VehicleReminderStatus.SNOOZED &&
            reminder.snoozedUntilEpochDay?.let { it > todayEpochDay } == true
        ) return null
        val dateDue = reminder.dueEpochDay?.let { it <= todayEpochDay } == true
        val odometerDue = reminder.dueOdometerMeters?.let { due ->
            currentOdometerMeters?.let { it >= due }
        } == true
        val trigger = when {
            dateDue && odometerDue -> ReminderDueTrigger.BOTH
            dateDue -> ReminderDueTrigger.DATE
            odometerDue -> ReminderDueTrigger.ODOMETER
            else -> return null
        }
        return DueReminderProjection(reminder, trigger)
    }
}

fun currentMonthRange(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): LongRange {
    val date = now.atZone(zoneId).toLocalDate()
    val start = date.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val end = date.plusMonths(1).withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    return start until end
}

fun todayEpochDay(zoneId: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zoneId).toEpochDay()
