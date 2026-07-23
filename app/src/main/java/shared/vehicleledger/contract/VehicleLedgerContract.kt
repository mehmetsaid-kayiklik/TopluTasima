package shared.vehicleledger.contract

import java.util.Locale
import java.util.UUID

object VehicleLedgerContractSpec {
    const val SCHEMA_VERSION = 1
    const val USERS_COLLECTION = "users"
    const val ODOMETER_COLLECTION = "vehicleOdometerEntries"
    const val EXPENSE_COLLECTION = "vehicleExpenses"
    const val REMINDER_COLLECTION = "vehicleReminders"

    const val FIELD_OWNER_UID = "ownerUid"
    const val FIELD_VEHICLE_ID = "vehicleId"
    const val FIELD_SCHEMA_VERSION = "schemaVersion"
    const val FIELD_REVISION = "revision"
    const val FIELD_OPERATION_ID = "operationId"
    const val FIELD_SOURCE = "source"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_CLIENT_UPDATED_AT = "clientUpdatedAt"
    const val FIELD_SERVER_UPDATED_AT = "_serverUpdatedAt"
    const val FIELD_DELETED_AT = "deletedAt"

    const val FIELD_ODOMETER_ENTRY_ID = "odometerEntryId"
    const val FIELD_OBSERVED_AT = "observedAt"
    const val FIELD_ODOMETER_METERS = "odometerMeters"
    const val FIELD_QUALITY = "quality"
    const val FIELD_READING_ROLE = "readingRole"
    const val FIELD_ODOMETER_SERIES_ID = "odometerSeriesId"
    const val FIELD_SOURCE_RECORD_TYPE = "sourceRecordType"
    const val FIELD_SOURCE_RECORD_ID = "sourceRecordId"
    const val FIELD_CORRECTION_OF_ENTRY_ID = "correctionOfEntryId"
    const val FIELD_RESET_REASON = "resetReason"
    const val FIELD_NOTES = "notes"

    const val FIELD_EXPENSE_ID = "expenseId"
    const val FIELD_OCCURRED_AT = "occurredAt"
    const val FIELD_CATEGORY = "category"
    const val FIELD_TRANSACTION_KIND = "transactionKind"
    const val FIELD_AMOUNT_MINOR = "amountMinor"
    const val FIELD_CURRENCY_CODE = "currencyCode"
    const val FIELD_CURRENCY_EXPONENT = "currencyExponent"
    const val FIELD_VENDOR_NAME = "vendorName"
    const val FIELD_REFERENCE_NUMBER = "referenceNumber"
    const val FIELD_PERIOD_START_EPOCH_DAY = "periodStartEpochDay"
    const val FIELD_PERIOD_END_EPOCH_DAY = "periodEndEpochDay"
    const val FIELD_DUE_EPOCH_DAY = "dueEpochDay"
    const val FIELD_ODOMETER_METERS_SNAPSHOT = "odometerMetersSnapshot"
    const val FIELD_SPLIT_GROUP_ID = "splitGroupId"
    const val FIELD_DUPLICATE_FINGERPRINT = "duplicateFingerprint"
    const val FIELD_RELATED_EXPENSE_ID = "relatedExpenseId"

    const val FIELD_REMINDER_ID = "reminderId"
    const val FIELD_TITLE = "title"
    const val FIELD_REMINDER_TYPE = "reminderType"
    const val FIELD_STATUS = "status"
    const val FIELD_DUE_ODOMETER_METERS = "dueOdometerMeters"
    const val FIELD_RECURRENCE_MONTHS = "recurrenceMonths"
    const val FIELD_RECURRENCE_DISTANCE_METERS = "recurrenceDistanceMeters"
    const val FIELD_RECURRENCE_ANCHOR = "recurrenceAnchor"
    const val FIELD_LEAD_DAYS = "leadDays"
    const val FIELD_LEAD_DISTANCE_METERS = "leadDistanceMeters"
    const val FIELD_SNOOZED_UNTIL_EPOCH_DAY = "snoozedUntilEpochDay"
    const val FIELD_LINKED_SERVICE_RECORD_ID = "linkedServiceRecordId"
    const val FIELD_LAST_COMPLETED_SERVICE_RECORD_ID = "lastCompletedServiceRecordId"
    const val FIELD_LAST_COMPLETED_AT = "lastCompletedAt"
    const val FIELD_LAST_COMPLETED_ODOMETER_METERS = "lastCompletedOdometerMeters"

    val COMMON_FIELDS = setOf(
        FIELD_OWNER_UID,
        FIELD_VEHICLE_ID,
        FIELD_SCHEMA_VERSION,
        FIELD_REVISION,
        FIELD_OPERATION_ID,
        FIELD_SOURCE,
        FIELD_CREATED_AT,
        FIELD_CLIENT_UPDATED_AT,
        FIELD_SERVER_UPDATED_AT,
        FIELD_DELETED_AT
    )

    val ODOMETER_FIELDS = COMMON_FIELDS + setOf(
        FIELD_ODOMETER_ENTRY_ID,
        FIELD_OBSERVED_AT,
        FIELD_ODOMETER_METERS,
        FIELD_QUALITY,
        FIELD_READING_ROLE,
        FIELD_ODOMETER_SERIES_ID,
        FIELD_SOURCE_RECORD_TYPE,
        FIELD_SOURCE_RECORD_ID,
        FIELD_CORRECTION_OF_ENTRY_ID,
        FIELD_RESET_REASON,
        FIELD_NOTES
    )

    val EXPENSE_FIELDS = COMMON_FIELDS + setOf(
        FIELD_EXPENSE_ID,
        FIELD_OCCURRED_AT,
        FIELD_CATEGORY,
        FIELD_TRANSACTION_KIND,
        FIELD_AMOUNT_MINOR,
        FIELD_CURRENCY_CODE,
        FIELD_CURRENCY_EXPONENT,
        FIELD_VENDOR_NAME,
        FIELD_NOTES,
        FIELD_REFERENCE_NUMBER,
        FIELD_PERIOD_START_EPOCH_DAY,
        FIELD_PERIOD_END_EPOCH_DAY,
        FIELD_DUE_EPOCH_DAY,
        FIELD_ODOMETER_ENTRY_ID,
        FIELD_ODOMETER_METERS_SNAPSHOT,
        FIELD_SPLIT_GROUP_ID,
        FIELD_DUPLICATE_FINGERPRINT,
        FIELD_RELATED_EXPENSE_ID
    )

    val REMINDER_FIELDS = COMMON_FIELDS + setOf(
        FIELD_REMINDER_ID,
        FIELD_TITLE,
        FIELD_REMINDER_TYPE,
        FIELD_STATUS,
        FIELD_DUE_EPOCH_DAY,
        FIELD_DUE_ODOMETER_METERS,
        FIELD_RECURRENCE_MONTHS,
        FIELD_RECURRENCE_DISTANCE_METERS,
        FIELD_RECURRENCE_ANCHOR,
        FIELD_LEAD_DAYS,
        FIELD_LEAD_DISTANCE_METERS,
        FIELD_SNOOZED_UNTIL_EPOCH_DAY,
        FIELD_LINKED_SERVICE_RECORD_ID,
        FIELD_LAST_COMPLETED_SERVICE_RECORD_ID,
        FIELD_LAST_COMPLETED_AT,
        FIELD_LAST_COMPLETED_ODOMETER_METERS,
        FIELD_NOTES
    )

    fun odometerPath(ownerUid: String, entryId: String) =
        "$USERS_COLLECTION/$ownerUid/$ODOMETER_COLLECTION/$entryId"

    fun expensePath(ownerUid: String, expenseId: String) =
        "$USERS_COLLECTION/$ownerUid/$EXPENSE_COLLECTION/$expenseId"

    fun reminderPath(ownerUid: String, reminderId: String) =
        "$USERS_COLLECTION/$ownerUid/$REMINDER_COLLECTION/$reminderId"
}

enum class VehicleLedgerSource {
    MANUAL,
    MIGRATED,
    SYSTEM_DERIVED,
    IMPORTED,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): VehicleLedgerSource =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class VehicleLedgerEntityType {
    ODOMETER,
    EXPENSE,
    REMINDER,
    ODOMETER_MIRROR,
    UNKNOWN
}

enum class VehicleLedgerOperationKind {
    CREATE,
    UPDATE,
    TOMBSTONE,
    RESTORE,
    RECONCILE_MIRROR,
    UNKNOWN
}

enum class OdometerQuality {
    CONFIRMED,
    ESTIMATED,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?) =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class OdometerReadingRole {
    AT_EVENT,
    TRIP_START,
    TRIP_END,
    MANUAL,
    MIGRATED,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?) =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class VehicleExpenseCategory {
    INSURANCE,
    TAX,
    REGISTRATION_FEE,
    ROAD_FEE,
    TOLL,
    PARKING,
    CAR_WASH,
    CHARGING_UNMETERED,
    ACCESSORY,
    FINE,
    OTHER,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?) =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class VehicleExpenseTransactionKind {
    EXPENSE,
    REFUND,
    CREDIT,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?) =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class VehicleReminderType {
    MAINTENANCE,
    INSPECTION,
    INSURANCE,
    TAX,
    REGISTRATION,
    OTHER,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?) =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class VehicleReminderStatus {
    ACTIVE,
    SNOOZED,
    DISABLED,
    COMPLETED,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?) =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class VehicleReminderRecurrenceAnchor {
    LAST_COMPLETION,
    FIXED_SCHEDULE,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?) =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

data class LedgerServerTimestamp(
    val seconds: Long,
    val nanoseconds: Int
) : Comparable<LedgerServerTimestamp> {
    init {
        require(nanoseconds in 0..999_999_999)
    }

    override fun compareTo(other: LedgerServerTimestamp): Int =
        compareValuesBy(this, other, LedgerServerTimestamp::seconds, LedgerServerTimestamp::nanoseconds)
}

data class VehicleLedgerEnvelope(
    val ownerUid: String,
    val vehicleId: String,
    val schemaVersion: Int = VehicleLedgerContractSpec.SCHEMA_VERSION,
    val revision: Long,
    val operationId: String,
    val source: VehicleLedgerSource,
    val createdAt: Long,
    val clientUpdatedAt: Long,
    val serverUpdatedAt: LedgerServerTimestamp?,
    val deletedAt: Long?
)

data class VehicleOdometerEntryContract(
    val odometerEntryId: String,
    val observedAt: Long?,
    val odometerMeters: Long,
    val quality: OdometerQuality,
    val readingRole: OdometerReadingRole,
    val odometerSeriesId: String,
    val sourceRecordType: String?,
    val sourceRecordId: String?,
    val correctionOfEntryId: String?,
    val resetReason: String?,
    val notes: String?,
    val envelope: VehicleLedgerEnvelope
)

data class VehicleExpenseContract(
    val expenseId: String,
    val occurredAt: Long,
    val category: VehicleExpenseCategory,
    val transactionKind: VehicleExpenseTransactionKind,
    val amountMinor: Long,
    val currencyCode: String,
    val currencyExponent: Int,
    val vendorName: String?,
    val notes: String?,
    val referenceNumber: String?,
    val periodStartEpochDay: Long?,
    val periodEndEpochDay: Long?,
    val dueEpochDay: Long?,
    val odometerEntryId: String?,
    val odometerMetersSnapshot: Long?,
    val splitGroupId: String?,
    val duplicateFingerprint: String?,
    val relatedExpenseId: String?,
    val envelope: VehicleLedgerEnvelope
)

data class VehicleReminderContract(
    val reminderId: String,
    val title: String,
    val reminderType: VehicleReminderType,
    val status: VehicleReminderStatus,
    val dueEpochDay: Long?,
    val dueOdometerMeters: Long?,
    val recurrenceMonths: Int?,
    val recurrenceDistanceMeters: Long?,
    val recurrenceAnchor: VehicleReminderRecurrenceAnchor,
    val leadDays: Int?,
    val leadDistanceMeters: Long?,
    val snoozedUntilEpochDay: Long?,
    val linkedServiceRecordId: String?,
    val lastCompletedServiceRecordId: String?,
    val lastCompletedAt: Long?,
    val lastCompletedOdometerMeters: Long?,
    val notes: String?,
    val envelope: VehicleLedgerEnvelope
)

enum class VehicleLedgerValidationIssue {
    ID_DOCUMENT_ID_MISMATCH,
    OWNER_MISMATCH,
    INVALID_OWNER,
    INVALID_VEHICLE_ID,
    INVALID_SCHEMA_VERSION,
    NEGATIVE_REVISION,
    INITIAL_REVISION_TOO_LOW,
    INVALID_OPERATION_ID,
    INVALID_CREATED_AT,
    INVALID_CLIENT_UPDATED_AT,
    NEGATIVE_ODOMETER,
    ODOMETER_OBSERVED_AT_REQUIRED,
    INVALID_ODOMETER_SERIES,
    SOURCE_RECORD_PAIR_MISMATCH,
    INVALID_AMOUNT,
    INVALID_CURRENCY_CODE,
    INVALID_CURRENCY_EXPONENT,
    INVALID_EXPENSE_PERIOD,
    NEGATIVE_ODOMETER_SNAPSHOT,
    REMINDER_TITLE_REQUIRED,
    REMINDER_NO_TRIGGER,
    REMINDER_RECURRENCE_INVALID,
    REMINDER_LEAD_INVALID,
    REMINDER_SNOOZE_INVALID
}

sealed interface VehicleLedgerParseResult<out T> {
    data class Valid<T>(val value: T) : VehicleLedgerParseResult<T>
    data class Invalid(val issues: Set<VehicleLedgerValidationIssue>) : VehicleLedgerParseResult<Nothing>
    data class Unsupported(val schemaVersion: Int) : VehicleLedgerParseResult<Nothing>
}

object VehicleLedgerIds {
    private val safeId = Regex("^[^/\\s]{1,128}$")

    fun isSafe(value: String?): Boolean = value != null && safeId.matches(value)

    fun isUuid(value: String?): Boolean = try {
        value != null && UUID.fromString(value).toString().equals(value, ignoreCase = true)
    } catch (_: IllegalArgumentException) {
        false
    }
}

object VehicleLedgerValidator {
    private val currencyCode = Regex("^[A-Z]{3}$")

    fun validateCommon(
        documentId: String,
        payloadId: String,
        expectedOwnerUid: String,
        envelope: VehicleLedgerEnvelope
    ): Set<VehicleLedgerValidationIssue> = buildSet {
        if (documentId != payloadId) add(VehicleLedgerValidationIssue.ID_DOCUMENT_ID_MISMATCH)
        if (envelope.ownerUid != expectedOwnerUid) add(VehicleLedgerValidationIssue.OWNER_MISMATCH)
        if (!VehicleLedgerIds.isSafe(envelope.ownerUid)) add(VehicleLedgerValidationIssue.INVALID_OWNER)
        if (!VehicleLedgerIds.isSafe(envelope.vehicleId)) add(VehicleLedgerValidationIssue.INVALID_VEHICLE_ID)
        if (envelope.schemaVersion != VehicleLedgerContractSpec.SCHEMA_VERSION) {
            add(VehicleLedgerValidationIssue.INVALID_SCHEMA_VERSION)
        }
        if (envelope.revision < 0) add(VehicleLedgerValidationIssue.NEGATIVE_REVISION)
        if (envelope.revision < 1) add(VehicleLedgerValidationIssue.INITIAL_REVISION_TOO_LOW)
        if (!VehicleLedgerIds.isUuid(envelope.operationId)) {
            add(VehicleLedgerValidationIssue.INVALID_OPERATION_ID)
        }
        if (envelope.createdAt < 0) add(VehicleLedgerValidationIssue.INVALID_CREATED_AT)
        if (envelope.clientUpdatedAt < 0) add(VehicleLedgerValidationIssue.INVALID_CLIENT_UPDATED_AT)
    }

    fun validate(
        documentId: String,
        expectedOwnerUid: String,
        value: VehicleOdometerEntryContract
    ): Set<VehicleLedgerValidationIssue> = buildSet {
        addAll(validateCommon(documentId, value.odometerEntryId, expectedOwnerUid, value.envelope))
        if (value.odometerMeters < 0) add(VehicleLedgerValidationIssue.NEGATIVE_ODOMETER)
        if (!VehicleLedgerIds.isSafe(value.odometerSeriesId)) {
            add(VehicleLedgerValidationIssue.INVALID_ODOMETER_SERIES)
        }
        if (value.observedAt == null &&
            !(value.envelope.source == VehicleLedgerSource.MIGRATED &&
                value.readingRole == OdometerReadingRole.MIGRATED)
        ) {
            add(VehicleLedgerValidationIssue.ODOMETER_OBSERVED_AT_REQUIRED)
        }
        if ((value.sourceRecordType == null) != (value.sourceRecordId == null)) {
            add(VehicleLedgerValidationIssue.SOURCE_RECORD_PAIR_MISMATCH)
        }
    }

    fun validate(
        documentId: String,
        expectedOwnerUid: String,
        value: VehicleExpenseContract
    ): Set<VehicleLedgerValidationIssue> = buildSet {
        addAll(validateCommon(documentId, value.expenseId, expectedOwnerUid, value.envelope))
        if (value.amountMinor <= 0) add(VehicleLedgerValidationIssue.INVALID_AMOUNT)
        if (!currencyCode.matches(value.currencyCode)) {
            add(VehicleLedgerValidationIssue.INVALID_CURRENCY_CODE)
        }
        if (value.currencyExponent !in 0..4) {
            add(VehicleLedgerValidationIssue.INVALID_CURRENCY_EXPONENT)
        }
        if (value.periodStartEpochDay != null && value.periodEndEpochDay != null &&
            value.periodEndEpochDay < value.periodStartEpochDay
        ) {
            add(VehicleLedgerValidationIssue.INVALID_EXPENSE_PERIOD)
        }
        if (value.odometerMetersSnapshot?.let { it < 0 } == true) {
            add(VehicleLedgerValidationIssue.NEGATIVE_ODOMETER_SNAPSHOT)
        }
    }

    fun validate(
        documentId: String,
        expectedOwnerUid: String,
        value: VehicleReminderContract
    ): Set<VehicleLedgerValidationIssue> = buildSet {
        addAll(validateCommon(documentId, value.reminderId, expectedOwnerUid, value.envelope))
        if (value.title.isBlank()) add(VehicleLedgerValidationIssue.REMINDER_TITLE_REQUIRED)
        if (value.dueEpochDay == null && value.dueOdometerMeters == null) {
            add(VehicleLedgerValidationIssue.REMINDER_NO_TRIGGER)
        }
        if (value.dueOdometerMeters?.let { it < 0 } == true ||
            value.lastCompletedOdometerMeters?.let { it < 0 } == true
        ) {
            add(VehicleLedgerValidationIssue.NEGATIVE_ODOMETER)
        }
        if (value.recurrenceMonths?.let { it <= 0 } == true ||
            value.recurrenceDistanceMeters?.let { it <= 0 } == true
        ) {
            add(VehicleLedgerValidationIssue.REMINDER_RECURRENCE_INVALID)
        }
        if (value.leadDays?.let { it < 0 } == true ||
            value.leadDistanceMeters?.let { it < 0 } == true
        ) {
            add(VehicleLedgerValidationIssue.REMINDER_LEAD_INVALID)
        }
        if (value.status == VehicleReminderStatus.SNOOZED && value.snoozedUntilEpochDay == null) {
            add(VehicleLedgerValidationIssue.REMINDER_SNOOZE_INVALID)
        }
    }
}

object VehicleLedgerParser {
    fun parseOdometer(
        documentId: String,
        fields: Map<String, Any?>,
        expectedOwnerUid: String
    ): VehicleLedgerParseResult<VehicleOdometerEntryContract> {
        val schema = fields.integer(VehicleLedgerContractSpec.FIELD_SCHEMA_VERSION) ?: -1
        if (schema > VehicleLedgerContractSpec.SCHEMA_VERSION) {
            return VehicleLedgerParseResult.Unsupported(schema)
        }
        val value = VehicleOdometerEntryContract(
            odometerEntryId = fields.text(VehicleLedgerContractSpec.FIELD_ODOMETER_ENTRY_ID).orEmpty(),
            observedAt = fields.long(VehicleLedgerContractSpec.FIELD_OBSERVED_AT),
            odometerMeters = fields.long(VehicleLedgerContractSpec.FIELD_ODOMETER_METERS) ?: -1,
            quality = OdometerQuality.fromWire(fields.text(VehicleLedgerContractSpec.FIELD_QUALITY)),
            readingRole = OdometerReadingRole.fromWire(fields.text(VehicleLedgerContractSpec.FIELD_READING_ROLE)),
            odometerSeriesId = fields.text(VehicleLedgerContractSpec.FIELD_ODOMETER_SERIES_ID).orEmpty(),
            sourceRecordType = fields.text(VehicleLedgerContractSpec.FIELD_SOURCE_RECORD_TYPE),
            sourceRecordId = fields.text(VehicleLedgerContractSpec.FIELD_SOURCE_RECORD_ID),
            correctionOfEntryId = fields.text(VehicleLedgerContractSpec.FIELD_CORRECTION_OF_ENTRY_ID),
            resetReason = fields.text(VehicleLedgerContractSpec.FIELD_RESET_REASON),
            notes = fields.text(VehicleLedgerContractSpec.FIELD_NOTES),
            envelope = fields.envelope(schema)
        )
        return validated(value, VehicleLedgerValidator.validate(documentId, expectedOwnerUid, value))
    }

    fun parseExpense(
        documentId: String,
        fields: Map<String, Any?>,
        expectedOwnerUid: String
    ): VehicleLedgerParseResult<VehicleExpenseContract> {
        val schema = fields.integer(VehicleLedgerContractSpec.FIELD_SCHEMA_VERSION) ?: -1
        if (schema > VehicleLedgerContractSpec.SCHEMA_VERSION) {
            return VehicleLedgerParseResult.Unsupported(schema)
        }
        val value = VehicleExpenseContract(
            expenseId = fields.text(VehicleLedgerContractSpec.FIELD_EXPENSE_ID).orEmpty(),
            occurredAt = fields.long(VehicleLedgerContractSpec.FIELD_OCCURRED_AT) ?: -1,
            category = VehicleExpenseCategory.fromWire(fields.text(VehicleLedgerContractSpec.FIELD_CATEGORY)),
            transactionKind = VehicleExpenseTransactionKind.fromWire(
                fields.text(VehicleLedgerContractSpec.FIELD_TRANSACTION_KIND)
            ),
            amountMinor = fields.long(VehicleLedgerContractSpec.FIELD_AMOUNT_MINOR) ?: -1,
            currencyCode = fields.text(VehicleLedgerContractSpec.FIELD_CURRENCY_CODE)
                ?.uppercase(Locale.ROOT).orEmpty(),
            currencyExponent = fields.integer(VehicleLedgerContractSpec.FIELD_CURRENCY_EXPONENT) ?: -1,
            vendorName = fields.text(VehicleLedgerContractSpec.FIELD_VENDOR_NAME),
            notes = fields.text(VehicleLedgerContractSpec.FIELD_NOTES),
            referenceNumber = fields.text(VehicleLedgerContractSpec.FIELD_REFERENCE_NUMBER),
            periodStartEpochDay = fields.long(VehicleLedgerContractSpec.FIELD_PERIOD_START_EPOCH_DAY),
            periodEndEpochDay = fields.long(VehicleLedgerContractSpec.FIELD_PERIOD_END_EPOCH_DAY),
            dueEpochDay = fields.long(VehicleLedgerContractSpec.FIELD_DUE_EPOCH_DAY),
            odometerEntryId = fields.text(VehicleLedgerContractSpec.FIELD_ODOMETER_ENTRY_ID),
            odometerMetersSnapshot = fields.long(VehicleLedgerContractSpec.FIELD_ODOMETER_METERS_SNAPSHOT),
            splitGroupId = fields.text(VehicleLedgerContractSpec.FIELD_SPLIT_GROUP_ID),
            duplicateFingerprint = fields.text(VehicleLedgerContractSpec.FIELD_DUPLICATE_FINGERPRINT),
            relatedExpenseId = fields.text(VehicleLedgerContractSpec.FIELD_RELATED_EXPENSE_ID),
            envelope = fields.envelope(schema)
        )
        return validated(value, VehicleLedgerValidator.validate(documentId, expectedOwnerUid, value))
    }

    fun parseReminder(
        documentId: String,
        fields: Map<String, Any?>,
        expectedOwnerUid: String
    ): VehicleLedgerParseResult<VehicleReminderContract> {
        val schema = fields.integer(VehicleLedgerContractSpec.FIELD_SCHEMA_VERSION) ?: -1
        if (schema > VehicleLedgerContractSpec.SCHEMA_VERSION) {
            return VehicleLedgerParseResult.Unsupported(schema)
        }
        val value = VehicleReminderContract(
            reminderId = fields.text(VehicleLedgerContractSpec.FIELD_REMINDER_ID).orEmpty(),
            title = fields.text(VehicleLedgerContractSpec.FIELD_TITLE).orEmpty(),
            reminderType = VehicleReminderType.fromWire(fields.text(VehicleLedgerContractSpec.FIELD_REMINDER_TYPE)),
            status = VehicleReminderStatus.fromWire(fields.text(VehicleLedgerContractSpec.FIELD_STATUS)),
            dueEpochDay = fields.long(VehicleLedgerContractSpec.FIELD_DUE_EPOCH_DAY),
            dueOdometerMeters = fields.long(VehicleLedgerContractSpec.FIELD_DUE_ODOMETER_METERS),
            recurrenceMonths = fields.integer(VehicleLedgerContractSpec.FIELD_RECURRENCE_MONTHS),
            recurrenceDistanceMeters = fields.long(VehicleLedgerContractSpec.FIELD_RECURRENCE_DISTANCE_METERS),
            recurrenceAnchor = VehicleReminderRecurrenceAnchor.fromWire(
                fields.text(VehicleLedgerContractSpec.FIELD_RECURRENCE_ANCHOR)
            ),
            leadDays = fields.integer(VehicleLedgerContractSpec.FIELD_LEAD_DAYS),
            leadDistanceMeters = fields.long(VehicleLedgerContractSpec.FIELD_LEAD_DISTANCE_METERS),
            snoozedUntilEpochDay = fields.long(VehicleLedgerContractSpec.FIELD_SNOOZED_UNTIL_EPOCH_DAY),
            linkedServiceRecordId = fields.text(VehicleLedgerContractSpec.FIELD_LINKED_SERVICE_RECORD_ID),
            lastCompletedServiceRecordId = fields.text(
                VehicleLedgerContractSpec.FIELD_LAST_COMPLETED_SERVICE_RECORD_ID
            ),
            lastCompletedAt = fields.long(VehicleLedgerContractSpec.FIELD_LAST_COMPLETED_AT),
            lastCompletedOdometerMeters = fields.long(
                VehicleLedgerContractSpec.FIELD_LAST_COMPLETED_ODOMETER_METERS
            ),
            notes = fields.text(VehicleLedgerContractSpec.FIELD_NOTES),
            envelope = fields.envelope(schema)
        )
        return validated(value, VehicleLedgerValidator.validate(documentId, expectedOwnerUid, value))
    }

    private fun <T> validated(
        value: T,
        issues: Set<VehicleLedgerValidationIssue>
    ): VehicleLedgerParseResult<T> =
        if (issues.isEmpty()) VehicleLedgerParseResult.Valid(value)
        else VehicleLedgerParseResult.Invalid(issues)
}

fun VehicleOdometerEntryContract.toFields(): Map<String, Any?> = commonFields(envelope) + mapOf(
    VehicleLedgerContractSpec.FIELD_ODOMETER_ENTRY_ID to odometerEntryId,
    VehicleLedgerContractSpec.FIELD_OBSERVED_AT to observedAt,
    VehicleLedgerContractSpec.FIELD_ODOMETER_METERS to odometerMeters,
    VehicleLedgerContractSpec.FIELD_QUALITY to quality.name,
    VehicleLedgerContractSpec.FIELD_READING_ROLE to readingRole.name,
    VehicleLedgerContractSpec.FIELD_ODOMETER_SERIES_ID to odometerSeriesId,
    VehicleLedgerContractSpec.FIELD_SOURCE_RECORD_TYPE to sourceRecordType,
    VehicleLedgerContractSpec.FIELD_SOURCE_RECORD_ID to sourceRecordId,
    VehicleLedgerContractSpec.FIELD_CORRECTION_OF_ENTRY_ID to correctionOfEntryId,
    VehicleLedgerContractSpec.FIELD_RESET_REASON to resetReason,
    VehicleLedgerContractSpec.FIELD_NOTES to notes
)

fun VehicleExpenseContract.toFields(): Map<String, Any?> = commonFields(envelope) + mapOf(
    VehicleLedgerContractSpec.FIELD_EXPENSE_ID to expenseId,
    VehicleLedgerContractSpec.FIELD_OCCURRED_AT to occurredAt,
    VehicleLedgerContractSpec.FIELD_CATEGORY to category.name,
    VehicleLedgerContractSpec.FIELD_TRANSACTION_KIND to transactionKind.name,
    VehicleLedgerContractSpec.FIELD_AMOUNT_MINOR to amountMinor,
    VehicleLedgerContractSpec.FIELD_CURRENCY_CODE to currencyCode,
    VehicleLedgerContractSpec.FIELD_CURRENCY_EXPONENT to currencyExponent,
    VehicleLedgerContractSpec.FIELD_VENDOR_NAME to vendorName,
    VehicleLedgerContractSpec.FIELD_NOTES to notes,
    VehicleLedgerContractSpec.FIELD_REFERENCE_NUMBER to referenceNumber,
    VehicleLedgerContractSpec.FIELD_PERIOD_START_EPOCH_DAY to periodStartEpochDay,
    VehicleLedgerContractSpec.FIELD_PERIOD_END_EPOCH_DAY to periodEndEpochDay,
    VehicleLedgerContractSpec.FIELD_DUE_EPOCH_DAY to dueEpochDay,
    VehicleLedgerContractSpec.FIELD_ODOMETER_ENTRY_ID to odometerEntryId,
    VehicleLedgerContractSpec.FIELD_ODOMETER_METERS_SNAPSHOT to odometerMetersSnapshot,
    VehicleLedgerContractSpec.FIELD_SPLIT_GROUP_ID to splitGroupId,
    VehicleLedgerContractSpec.FIELD_DUPLICATE_FINGERPRINT to duplicateFingerprint,
    VehicleLedgerContractSpec.FIELD_RELATED_EXPENSE_ID to relatedExpenseId
)

fun VehicleReminderContract.toFields(): Map<String, Any?> = commonFields(envelope) + mapOf(
    VehicleLedgerContractSpec.FIELD_REMINDER_ID to reminderId,
    VehicleLedgerContractSpec.FIELD_TITLE to title,
    VehicleLedgerContractSpec.FIELD_REMINDER_TYPE to reminderType.name,
    VehicleLedgerContractSpec.FIELD_STATUS to status.name,
    VehicleLedgerContractSpec.FIELD_DUE_EPOCH_DAY to dueEpochDay,
    VehicleLedgerContractSpec.FIELD_DUE_ODOMETER_METERS to dueOdometerMeters,
    VehicleLedgerContractSpec.FIELD_RECURRENCE_MONTHS to recurrenceMonths,
    VehicleLedgerContractSpec.FIELD_RECURRENCE_DISTANCE_METERS to recurrenceDistanceMeters,
    VehicleLedgerContractSpec.FIELD_RECURRENCE_ANCHOR to recurrenceAnchor.name,
    VehicleLedgerContractSpec.FIELD_LEAD_DAYS to leadDays,
    VehicleLedgerContractSpec.FIELD_LEAD_DISTANCE_METERS to leadDistanceMeters,
    VehicleLedgerContractSpec.FIELD_SNOOZED_UNTIL_EPOCH_DAY to snoozedUntilEpochDay,
    VehicleLedgerContractSpec.FIELD_LINKED_SERVICE_RECORD_ID to linkedServiceRecordId,
    VehicleLedgerContractSpec.FIELD_LAST_COMPLETED_SERVICE_RECORD_ID to lastCompletedServiceRecordId,
    VehicleLedgerContractSpec.FIELD_LAST_COMPLETED_AT to lastCompletedAt,
    VehicleLedgerContractSpec.FIELD_LAST_COMPLETED_ODOMETER_METERS to lastCompletedOdometerMeters,
    VehicleLedgerContractSpec.FIELD_NOTES to notes
)

private fun commonFields(envelope: VehicleLedgerEnvelope): Map<String, Any?> = mapOf(
    VehicleLedgerContractSpec.FIELD_OWNER_UID to envelope.ownerUid,
    VehicleLedgerContractSpec.FIELD_VEHICLE_ID to envelope.vehicleId,
    VehicleLedgerContractSpec.FIELD_SCHEMA_VERSION to envelope.schemaVersion,
    VehicleLedgerContractSpec.FIELD_REVISION to envelope.revision,
    VehicleLedgerContractSpec.FIELD_OPERATION_ID to envelope.operationId,
    VehicleLedgerContractSpec.FIELD_SOURCE to envelope.source.name,
    VehicleLedgerContractSpec.FIELD_CREATED_AT to envelope.createdAt,
    VehicleLedgerContractSpec.FIELD_CLIENT_UPDATED_AT to envelope.clientUpdatedAt,
    VehicleLedgerContractSpec.FIELD_SERVER_UPDATED_AT to envelope.serverUpdatedAt?.let {
        mapOf("seconds" to it.seconds, "nanoseconds" to it.nanoseconds)
    },
    VehicleLedgerContractSpec.FIELD_DELETED_AT to envelope.deletedAt
)

enum class VehicleLedgerWinnerReason {
    ONLY_LOCAL,
    ONLY_REMOTE,
    OWNER_OR_PATH_MISMATCH,
    VEHICLE_TOMBSTONE,
    SAME_OPERATION,
    TOMBSTONE,
    HIGHER_REVISION,
    SERVER_TIMESTAMP,
    OPERATION_ID
}

data class VehicleLedgerResolution<T>(
    val winner: T?,
    val loser: T?,
    val reason: VehicleLedgerWinnerReason,
    val idempotent: Boolean,
    val conflict: Boolean
)

object VehicleLedgerConflictResolver {
    fun <T> resolve(
        local: T?,
        remote: T?,
        expectedOwnerUid: String,
        expectedVehicleId: String,
        vehicleDeleted: Boolean,
        envelope: (T) -> VehicleLedgerEnvelope
    ): VehicleLedgerResolution<T> {
        if (local == null && remote == null) {
            return VehicleLedgerResolution(null, null, VehicleLedgerWinnerReason.ONLY_REMOTE, false, false)
        }
        if (local == null) {
            val remoteEnvelope = envelope(requireNotNull(remote))
            if (!remoteEnvelope.matches(expectedOwnerUid, expectedVehicleId)) {
                return VehicleLedgerResolution(null, remote, VehicleLedgerWinnerReason.OWNER_OR_PATH_MISMATCH, false, true)
            }
            if (vehicleDeleted && remoteEnvelope.deletedAt == null) {
                return VehicleLedgerResolution(null, remote, VehicleLedgerWinnerReason.VEHICLE_TOMBSTONE, false, true)
            }
            return VehicleLedgerResolution(remote, null, VehicleLedgerWinnerReason.ONLY_REMOTE, false, false)
        }
        if (remote == null) {
            val localEnvelope = envelope(local)
            if (!localEnvelope.matches(expectedOwnerUid, expectedVehicleId)) {
                return VehicleLedgerResolution(null, local, VehicleLedgerWinnerReason.OWNER_OR_PATH_MISMATCH, false, true)
            }
            if (vehicleDeleted && localEnvelope.deletedAt == null) {
                return VehicleLedgerResolution(null, local, VehicleLedgerWinnerReason.VEHICLE_TOMBSTONE, false, true)
            }
            return VehicleLedgerResolution(local, null, VehicleLedgerWinnerReason.ONLY_LOCAL, false, false)
        }

        val left = envelope(local)
        val right = envelope(remote)
        if (!left.matches(expectedOwnerUid, expectedVehicleId) ||
            !right.matches(expectedOwnerUid, expectedVehicleId)
        ) {
            return VehicleLedgerResolution(null, local, VehicleLedgerWinnerReason.OWNER_OR_PATH_MISMATCH, false, true)
        }
        if (vehicleDeleted && (left.deletedAt == null || right.deletedAt == null)) {
            val tombstone = listOf(local, remote).firstOrNull { envelope(it).deletedAt != null }
            return VehicleLedgerResolution(tombstone, if (tombstone === local) remote else local,
                VehicleLedgerWinnerReason.VEHICLE_TOMBSTONE, false, true)
        }
        if (left.operationId == right.operationId) {
            return VehicleLedgerResolution(remote, local, VehicleLedgerWinnerReason.SAME_OPERATION, true, false)
        }

        if ((left.deletedAt != null) != (right.deletedAt != null)) {
            val tombstoneRecord = if (left.deletedAt != null) local else remote
            val activeRecord = if (left.deletedAt == null) local else remote
            val tombstoneEnvelope = envelope(tombstoneRecord)
            val activeEnvelope = envelope(activeRecord)
            if (tombstoneEnvelope.revision >= activeEnvelope.revision) {
                return VehicleLedgerResolution(
                    tombstoneRecord,
                    activeRecord,
                    VehicleLedgerWinnerReason.TOMBSTONE,
                    false,
                    true
                )
            }
        }

        if (left.revision != right.revision) {
            val winner = if (left.revision > right.revision) local else remote
            return VehicleLedgerResolution(
                winner,
                if (winner === local) remote else local,
                VehicleLedgerWinnerReason.HIGHER_REVISION,
                false,
                true
            )
        }

        val timestampComparison = compareValues(left.serverUpdatedAt, right.serverUpdatedAt)
        if (timestampComparison != 0) {
            val winner = if (timestampComparison > 0) local else remote
            return VehicleLedgerResolution(
                winner,
                if (winner === local) remote else local,
                VehicleLedgerWinnerReason.SERVER_TIMESTAMP,
                false,
                true
            )
        }

        val winner = if (left.operationId >= right.operationId) local else remote
        return VehicleLedgerResolution(
            winner,
            if (winner === local) remote else local,
            VehicleLedgerWinnerReason.OPERATION_ID,
            false,
            true
        )
    }

    private fun VehicleLedgerEnvelope.matches(ownerUid: String, vehicleId: String): Boolean =
        this.ownerUid == ownerUid && this.vehicleId == vehicleId
}

object VehicleLedgerRevision {
    fun next(highestKnown: Long): Long {
        require(highestKnown >= 0)
        return Math.addExact(highestKnown, 1L)
    }
}

private fun Map<String, Any?>.envelope(schemaVersion: Int) = VehicleLedgerEnvelope(
    ownerUid = text(VehicleLedgerContractSpec.FIELD_OWNER_UID).orEmpty(),
    vehicleId = text(VehicleLedgerContractSpec.FIELD_VEHICLE_ID).orEmpty(),
    schemaVersion = schemaVersion,
    revision = long(VehicleLedgerContractSpec.FIELD_REVISION) ?: -1,
    operationId = text(VehicleLedgerContractSpec.FIELD_OPERATION_ID).orEmpty(),
    source = VehicleLedgerSource.fromWire(text(VehicleLedgerContractSpec.FIELD_SOURCE)),
    createdAt = long(VehicleLedgerContractSpec.FIELD_CREATED_AT) ?: -1,
    clientUpdatedAt = long(VehicleLedgerContractSpec.FIELD_CLIENT_UPDATED_AT) ?: -1,
    serverUpdatedAt = timestamp(VehicleLedgerContractSpec.FIELD_SERVER_UPDATED_AT),
    deletedAt = long(VehicleLedgerContractSpec.FIELD_DELETED_AT)
)

private fun Map<String, Any?>.text(field: String): String? = (this[field] as? String)?.takeIf(String::isNotBlank)

private fun Map<String, Any?>.integer(field: String): Int? = long(field)?.let {
    if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else null
}

private fun Map<String, Any?>.long(field: String): Long? = when (val value = this[field]) {
    is Long -> value
    is Int -> value.toLong()
    is Short -> value.toLong()
    is Byte -> value.toLong()
    else -> null
}

private fun Map<String, Any?>.timestamp(field: String): LedgerServerTimestamp? {
    val value = this[field] as? Map<*, *> ?: return null
    val seconds = when (val raw = value["seconds"]) {
        is Long -> raw
        is Int -> raw.toLong()
        else -> return null
    }
    val nanos = when (val raw = value["nanoseconds"] ?: value["nanos"]) {
        is Int -> raw
        is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: return null
        else -> return null
    }
    return LedgerServerTimestamp(seconds, nanos)
}
