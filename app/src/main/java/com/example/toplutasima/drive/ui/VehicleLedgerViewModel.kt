package com.example.toplutasima.drive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.drive.ledger.ExpenseDraft
import com.example.toplutasima.drive.ledger.OdometerDraft
import com.example.toplutasima.drive.ledger.ReminderDraft
import com.example.toplutasima.drive.ledger.VehicleLedgerDashboard
import com.example.toplutasima.drive.ledger.VehicleLedgerFailure
import com.example.toplutasima.drive.ledger.VehicleLedgerHealthIssue
import com.example.toplutasima.drive.ledger.VehicleLedgerMutationResult
import com.example.toplutasima.drive.ledger.VehicleLedgerPage
import com.example.toplutasima.drive.ledger.VehicleLedgerRepository
import com.example.toplutasima.drive.ledger.VehicleLedgerUnits
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

enum class VehicleLedgerUiMessage {
    SAVED,
    DELETED,
    RESTORED,
    SYNC_PENDING,
    INVALID_INPUT,
    AUTHENTICATION_CHANGED,
    VEHICLE_NOT_FOUND,
    VEHICLE_DELETED,
    RECORD_NOT_FOUND,
    CONFLICT,
    STORAGE_FAILURE
}

data class VehicleLedgerUiState(
    val vehicleId: String? = null,
    val page: VehicleLedgerPage? = null,
    val dashboard: VehicleLedgerDashboard = VehicleLedgerDashboard(
        com.example.toplutasima.drive.ledger.CurrentOdometerProjection(null, null, false, false),
        0, emptyList(), 0, 0
    ),
    val odometers: List<VehicleOdometerEntryContract> = emptyList(),
    val expenses: List<VehicleExpenseContract> = emptyList(),
    val reminders: List<VehicleReminderContract> = emptyList(),
    val health: List<VehicleLedgerHealthIssue> = emptyList(),
    val busy: Boolean = false,
    val message: VehicleLedgerUiMessage? = null
)

private data class LedgerRecords(
    val odometers: List<VehicleOdometerEntryContract>,
    val expenses: List<VehicleExpenseContract>,
    val reminders: List<VehicleReminderContract>
)

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleLedgerViewModel(
    private val repository: VehicleLedgerRepository
) : ViewModel() {
    private val vehicleId = MutableStateFlow<String?>(null)
    private val page = MutableStateFlow<VehicleLedgerPage?>(null)
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<VehicleLedgerUiMessage?>(null)

    private val dashboard = vehicleId.flatMapLatest { id ->
        id?.let(repository::observeDashboard) ?: flowOf(VehicleLedgerUiState().dashboard)
    }
    private val records = vehicleId.flatMapLatest { id ->
        if (id == null) flowOf(LedgerRecords(emptyList(), emptyList(), emptyList()))
        else combine(
            repository.observeOdometerHistory(id),
            repository.observeExpenses(id, 0L, Long.MAX_VALUE),
            repository.observeReminders(id)
        ) { odometers, expenses, reminders -> LedgerRecords(odometers, expenses, reminders) }
    }
    private val health = vehicleId.flatMapLatest { id ->
        id?.let(repository::observeHealth) ?: flowOf(emptyList())
    }

    private val content = combine(
        vehicleId, page, dashboard, records, health
    ) { selectedVehicleId, selectedPage, dashboardValue, recordValues, healthValues ->
        VehicleLedgerUiState(
            vehicleId = selectedVehicleId,
            page = selectedPage,
            dashboard = dashboardValue,
            odometers = recordValues.odometers,
            expenses = recordValues.expenses,
            reminders = recordValues.reminders,
            health = healthValues
        )
    }

    val state: StateFlow<VehicleLedgerUiState> = combine(content, busy, message) {
            contentValue, busyValue, messageValue ->
        contentValue.copy(busy = busyValue, message = messageValue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleLedgerUiState())

    fun bindVehicle(id: String?) {
        vehicleId.value = id
        if (id == null) page.value = null
    }

    fun open(page: VehicleLedgerPage) {
        if (vehicleId.value != null) this.page.value = page
    }

    fun close() {
        page.value = null
    }

    fun clearMessage() {
        message.value = null
    }

    fun retrySync() = repository.schedulePendingSync()

    fun saveOdometer(
        entryId: String?,
        kilometers: String,
        observedAt: Long = System.currentTimeMillis()
    ) {
        val id = vehicleId.value ?: return
        val meters = VehicleLedgerUnits.decimalKilometersToMeters(kilometers)
        if (meters == null) return invalid()
        val current = state.value.odometers.firstOrNull { it.odometerEntryId == entryId }
        val draft = OdometerDraft(
            vehicleId = id,
            observedAt = observedAt,
            odometerMeters = meters,
            quality = current?.quality ?: OdometerQuality.CONFIRMED,
            readingRole = current?.readingRole ?: OdometerReadingRole.MANUAL,
            odometerSeriesId = current?.odometerSeriesId
                ?: state.value.dashboard.currentOdometer.entry?.odometerSeriesId
                ?: UUID.randomUUID().toString(),
            sourceRecordType = current?.sourceRecordType,
            sourceRecordId = current?.sourceRecordId,
            correctionOfEntryId = current?.correctionOfEntryId,
            resetReason = current?.resetReason,
            notes = current?.notes,
            source = current?.envelope?.source ?: VehicleLedgerSource.MANUAL
        )
        mutate {
            if (entryId == null) repository.createOdometer(draft)
            else repository.updateOdometer(entryId, draft)
        }
    }

    fun deleteOdometer(entryId: String) = mutate { repository.tombstoneOdometer(entryId) }
    fun restoreOdometer(entryId: String) = mutate { repository.restoreOdometer(entryId) }

    fun saveExpense(
        expenseId: String?,
        amount: String,
        currencyCode: String,
        currencyExponent: Int,
        category: VehicleExpenseCategory,
        transactionKind: VehicleExpenseTransactionKind,
        vendorName: String?,
        notes: String?
    ) {
        val id = vehicleId.value ?: return
        val amountMinor = VehicleLedgerUnits.decimalMoneyToMinor(amount, currencyExponent)
        val currency = currencyCode.trim().uppercase(Locale.ROOT)
        if (amountMinor == null || !currency.matches(Regex("^[A-Z]{3}$"))) return invalid()
        val current = state.value.expenses.firstOrNull { it.expenseId == expenseId }
        val draft = ExpenseDraft(
            vehicleId = id,
            occurredAt = current?.occurredAt ?: System.currentTimeMillis(),
            category = category,
            transactionKind = transactionKind,
            amountMinor = amountMinor,
            currencyCode = currency,
            currencyExponent = currencyExponent,
            vendorName = vendorName,
            notes = notes,
            referenceNumber = current?.referenceNumber,
            periodStartEpochDay = current?.periodStartEpochDay,
            periodEndEpochDay = current?.periodEndEpochDay,
            dueEpochDay = current?.dueEpochDay,
            odometerEntryId = current?.odometerEntryId,
            odometerMetersSnapshot = current?.odometerMetersSnapshot,
            splitGroupId = current?.splitGroupId,
            duplicateFingerprint = null,
            relatedExpenseId = current?.relatedExpenseId,
            source = current?.envelope?.source ?: VehicleLedgerSource.MANUAL
        )
        mutate {
            if (expenseId == null) repository.createExpense(draft)
            else repository.updateExpense(expenseId, draft)
        }
    }

    fun deleteExpense(expenseId: String) = mutate { repository.tombstoneExpense(expenseId) }
    fun restoreExpense(expenseId: String) = mutate { repository.restoreExpense(expenseId) }

    fun saveReminder(
        reminderId: String?,
        title: String,
        dueEpochDay: Long?,
        dueOdometerKilometers: String?,
        type: VehicleReminderType
    ) {
        val id = vehicleId.value ?: return
        val dueMeters = dueOdometerKilometers?.takeIf(String::isNotBlank)?.let {
            VehicleLedgerUnits.decimalKilometersToMeters(it)
        }
        if (title.isBlank() || (dueEpochDay == null && dueMeters == null)) return invalid()
        val current = state.value.reminders.firstOrNull { it.reminderId == reminderId }
        val draft = ReminderDraft(
            vehicleId = id,
            title = title,
            reminderType = type,
            status = current?.status ?: VehicleReminderStatus.ACTIVE,
            dueEpochDay = dueEpochDay,
            dueOdometerMeters = dueMeters,
            recurrenceMonths = current?.recurrenceMonths,
            recurrenceDistanceMeters = current?.recurrenceDistanceMeters,
            recurrenceAnchor = current?.recurrenceAnchor ?: VehicleReminderRecurrenceAnchor.LAST_COMPLETION,
            leadDays = current?.leadDays,
            leadDistanceMeters = current?.leadDistanceMeters,
            snoozedUntilEpochDay = current?.snoozedUntilEpochDay,
            linkedServiceRecordId = current?.linkedServiceRecordId,
            lastCompletedServiceRecordId = current?.lastCompletedServiceRecordId,
            lastCompletedAt = current?.lastCompletedAt,
            lastCompletedOdometerMeters = current?.lastCompletedOdometerMeters,
            notes = current?.notes,
            source = current?.envelope?.source ?: VehicleLedgerSource.MANUAL
        )
        mutate {
            if (reminderId == null) repository.createReminder(draft)
            else repository.updateReminder(reminderId, draft)
        }
    }

    fun completeReminder(reminderId: String) = mutate {
        repository.completeReminder(reminderId, System.currentTimeMillis(),
            state.value.dashboard.currentOdometer.meters)
    }

    fun snoozeReminder(reminderId: String, untilEpochDay: Long) = mutate {
        repository.snoozeReminder(reminderId, untilEpochDay)
    }

    fun disableReminder(reminderId: String) = mutate { repository.disableReminder(reminderId) }
    fun deleteReminder(reminderId: String) = mutate { repository.tombstoneReminder(reminderId) }

    private fun invalid() {
        message.value = VehicleLedgerUiMessage.INVALID_INPUT
    }

    private fun mutate(block: suspend () -> VehicleLedgerMutationResult<*>) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                message.value = when (val result = block()) {
                    is VehicleLedgerMutationResult.Success -> VehicleLedgerUiMessage.SAVED
                    is VehicleLedgerMutationResult.LocalSavedSyncSchedulingFailed ->
                        VehicleLedgerUiMessage.SYNC_PENDING
                    is VehicleLedgerMutationResult.Rejected -> result.failure.toMessage()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                busy.value = false
            }
        }
    }

    private fun VehicleLedgerFailure.toMessage(): VehicleLedgerUiMessage = when (this) {
        VehicleLedgerFailure.AuthenticationChanged,
        VehicleLedgerFailure.AccountMismatch -> VehicleLedgerUiMessage.AUTHENTICATION_CHANGED
        VehicleLedgerFailure.VehicleNotFound -> VehicleLedgerUiMessage.VEHICLE_NOT_FOUND
        VehicleLedgerFailure.VehicleDeleted -> VehicleLedgerUiMessage.VEHICLE_DELETED
        VehicleLedgerFailure.RecordNotFound,
        VehicleLedgerFailure.RecordDeleted -> VehicleLedgerUiMessage.RECORD_NOT_FOUND
        VehicleLedgerFailure.Conflict -> VehicleLedgerUiMessage.CONFLICT
        is VehicleLedgerFailure.Validation,
        VehicleLedgerFailure.RevisionOverflow -> VehicleLedgerUiMessage.INVALID_INPUT
        else -> VehicleLedgerUiMessage.STORAGE_FAILURE
    }
}
