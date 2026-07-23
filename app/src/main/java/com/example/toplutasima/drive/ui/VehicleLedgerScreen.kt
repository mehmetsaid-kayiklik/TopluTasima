package com.example.toplutasima.drive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toplutasima.drive.ledger.VehicleLedgerPage
import com.example.toplutasima.drive.ledger.VehicleLedgerUnits
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.LocaleManager
import java.math.BigDecimal
import java.time.LocalDate
import shared.vehicleledger.contract.VehicleExpenseCategory
import shared.vehicleledger.contract.VehicleExpenseContract
import shared.vehicleledger.contract.VehicleExpenseTransactionKind
import shared.vehicleledger.contract.VehicleOdometerEntryContract
import shared.vehicleledger.contract.VehicleReminderContract
import shared.vehicleledger.contract.VehicleReminderType

internal object VehicleLedgerTestTags {
    const val DASHBOARD = "drive-ledger-dashboard"
    const val ODOMETER = "drive-ledger-odometer"
    const val EXPENSES = "drive-ledger-expenses"
    const val REMINDERS = "drive-ledger-reminders"
    const val ADD = "drive-ledger-add"
    const val RETRY = "drive-ledger-retry"
    const val OPEN_ODOMETER = "drive-ledger-open-odometer"
    const val OPEN_EXPENSES = "drive-ledger-open-expenses"
    const val OPEN_REMINDERS = "drive-ledger-open-reminders"
}

@Composable
internal fun VehicleLedgerDashboardSection(
    state: VehicleLedgerUiState,
    onOpen: (VehicleLedgerPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocaleManager.currentLanguage
    val current = state.dashboard.currentOdometer
    val totals = state.dashboard.monthExpenseTotals
        .groupBy { it.currencyCode to it.currencyExponent }
        .map { (key, rows) -> key to rows.fold(0L) { total, row ->
            runCatching { Math.addExact(total, row.signedAmountMinor) }.getOrDefault(total)
        } }
    Column(
        modifier = modifier.fillMaxWidth().testTag(VehicleLedgerTestTags.DASHBOARD),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (language == AppLanguage.TR) "Araç günlüğü" else "Vehicle ledger",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        LedgerSummaryCard(
            title = if (language == AppLanguage.TR) "Güncel kilometre" else "Current odometer",
            value = current.meters?.let {
                "${VehicleLedgerUnits.metersToKilometers(it).stripTrailingZeros().toPlainString()} km" +
                    if (current.isEstimated) {
                        if (language == AppLanguage.TR) " (tahmini)" else " (estimated)"
                    } else ""
            } ?: if (language == AppLanguage.TR) "Henüz ölçüm yok" else "No reading yet",
            onClick = { onOpen(VehicleLedgerPage.ODOMETER) },
            testTag = VehicleLedgerTestTags.OPEN_ODOMETER
        )
        LedgerSummaryCard(
            title = if (language == AppLanguage.TR) "Yaklaşan hatırlatmalar" else "Due reminders",
            value = state.dashboard.dueReminderCount.toString(),
            onClick = { onOpen(VehicleLedgerPage.REMINDERS) },
            testTag = VehicleLedgerTestTags.OPEN_REMINDERS
        )
        LedgerSummaryCard(
            title = if (language == AppLanguage.TR) "Bu ay genel giderler" else "General expenses this month",
            value = totals.takeIf(List<*>::isNotEmpty)?.joinToString(" · ") { (currency, amount) ->
                "${formatMinor(amount, currency.second)} ${currency.first}"
            } ?: if (language == AppLanguage.TR) "Kayıt yok" else "No records",
            onClick = { onOpen(VehicleLedgerPage.EXPENSES) },
            testTag = VehicleLedgerTestTags.OPEN_EXPENSES
        )
        if (state.dashboard.pendingOperationCount > 0 || state.dashboard.unresolvedConflictCount > 0) {
            Text(
                buildString {
                    if (state.dashboard.pendingOperationCount > 0) {
                        append(if (language == AppLanguage.TR) "Senkronizasyon bekliyor" else "Sync pending")
                        append(": ${state.dashboard.pendingOperationCount}")
                    }
                    if (state.dashboard.unresolvedConflictCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(if (language == AppLanguage.TR) "Çakışma" else "Conflict")
                        append(": ${state.dashboard.unresolvedConflictCount}")
                    }
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LedgerSummaryCard(title: String, value: String, onClick: () -> Unit, testTag: String) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun VehicleLedgerPageScreen(
    state: VehicleLedgerUiState,
    onBack: () -> Unit,
    onSaveOdometer: (String?, String) -> Unit,
    onDeleteOdometer: (String) -> Unit,
    onRestoreOdometer: (String) -> Unit,
    onSaveExpense: (
        String?, String, String, Int, VehicleExpenseCategory,
        VehicleExpenseTransactionKind, String?, String?
    ) -> Unit,
    onDeleteExpense: (String) -> Unit,
    onRestoreExpense: (String) -> Unit,
    onSaveReminder: (String?, String, Long?, String?, VehicleReminderType) -> Unit,
    onCompleteReminder: (String) -> Unit,
    onSnoozeReminder: (String, Long) -> Unit,
    onDisableReminder: (String) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocaleManager.currentLanguage
    val page = state.page ?: return
    var editingOdometer by remember { mutableStateOf<VehicleOdometerEntryContract?>(null) }
    var editingExpense by remember { mutableStateOf<VehicleExpenseContract?>(null) }
    var editingReminder by remember { mutableStateOf<VehicleReminderContract?>(null) }
    var addDialog by remember(page) { mutableStateOf(false) }
    var expenseFilter by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize()) {
        DriveScreenHeader(
            title = when (page) {
                VehicleLedgerPage.ODOMETER -> if (language == AppLanguage.TR) "Kilometre geçmişi" else "Odometer history"
                VehicleLedgerPage.EXPENSES -> if (language == AppLanguage.TR) "Genel giderler" else "General expenses"
                VehicleLedgerPage.REMINDERS -> if (language == AppLanguage.TR) "Hatırlatmalar" else "Reminders"
            },
            subtitle = if (language == AppLanguage.TR) "Offline-first araç günlüğü" else "Offline-first vehicle ledger",
            onBack = onBack
        )
        if (state.dashboard.pendingOperationCount > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (language == AppLanguage.TR) "Senkronizasyon bekliyor" else "Sync pending")
                TextButton(onClick = onRetry, modifier = Modifier.testTag(VehicleLedgerTestTags.RETRY)) {
                    Text(if (language == AppLanguage.TR) "Yeniden dene" else "Retry")
                }
            }
        }
        if (state.dashboard.unresolvedConflictCount > 0) {
            Text(
                if (language == AppLanguage.TR) {
                    "Çakışan yerel kayıt korundu; health ekranından daha sonra düzeltilebilir."
                } else {
                    "The conflicting local record was retained for later resolution."
                },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (page == VehicleLedgerPage.EXPENSES) {
            OutlinedTextField(
                value = expenseFilter,
                onValueChange = { expenseFilter = it },
                label = { Text(if (language == AppLanguage.TR) "Kategori veya satıcı ara" else "Search category or vendor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
        when (page) {
            VehicleLedgerPage.ODOMETER -> OdometerList(
                state.odometers, language, { editingOdometer = it; addDialog = true },
                onDeleteOdometer, onRestoreOdometer,
                Modifier.weight(1f).testTag(VehicleLedgerTestTags.ODOMETER)
            )
            VehicleLedgerPage.EXPENSES -> ExpenseList(
                state.expenses.filter {
                    expenseFilter.isBlank() || it.category.name.contains(expenseFilter, true) ||
                        it.vendorName.orEmpty().contains(expenseFilter, true)
                },
                language, { editingExpense = it; addDialog = true }, onDeleteExpense, onRestoreExpense,
                Modifier.weight(1f).testTag(VehicleLedgerTestTags.EXPENSES)
            )
            VehicleLedgerPage.REMINDERS -> ReminderList(
                state.reminders, language, { editingReminder = it; addDialog = true },
                onCompleteReminder, onSnoozeReminder, onDisableReminder, onDeleteReminder,
                Modifier.weight(1f).testTag(VehicleLedgerTestTags.REMINDERS)
            )
        }
        Button(
            onClick = { addDialog = true },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(VehicleLedgerTestTags.ADD)
        ) {
            Text(if (language == AppLanguage.TR) "Yeni kayıt" else "New record")
        }
    }

    if (addDialog) when (page) {
        VehicleLedgerPage.ODOMETER -> OdometerDialog(editingOdometer, language,
            onDismiss = { addDialog = false; editingOdometer = null },
            onSave = {
                onSaveOdometer(editingOdometer?.odometerEntryId, it)
                addDialog = false; editingOdometer = null
            })
        VehicleLedgerPage.EXPENSES -> ExpenseDialog(editingExpense, language,
            onDismiss = { addDialog = false; editingExpense = null },
            onSave = { amount, currency, exponent, category, kind, vendor, notes ->
                onSaveExpense(editingExpense?.expenseId, amount, currency, exponent,
                    category, kind, vendor, notes)
                addDialog = false; editingExpense = null
            })
        VehicleLedgerPage.REMINDERS -> ReminderDialog(editingReminder, language,
            onDismiss = { addDialog = false; editingReminder = null },
            onSave = { title, day, odometer, type ->
                onSaveReminder(editingReminder?.reminderId, title, day, odometer, type)
                addDialog = false; editingReminder = null
            })
    }
}

@Composable
private fun OdometerList(
    values: List<VehicleOdometerEntryContract>,
    language: AppLanguage,
    onEdit: (VehicleOdometerEntryContract) -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    modifier: Modifier
) = LedgerList(modifier, values, { it.odometerEntryId }) { value ->
    LedgerRecordCard(
        title = "${VehicleLedgerUnits.metersToKilometers(value.odometerMeters).stripTrailingZeros().toPlainString()} km",
        subtitle = buildString {
            append(value.quality.name)
            value.observedAt?.let { append(" · "); append(formatEpochMillis(it)) }
            if (value.envelope.serverUpdatedAt == null) append(if (language == AppLanguage.TR) " · bekliyor" else " · pending")
        },
        deleted = value.envelope.deletedAt != null,
        onEdit = { onEdit(value) }, onDelete = { onDelete(value.odometerEntryId) },
        onRestore = { onRestore(value.odometerEntryId) }, language = language
    )
}

@Composable
private fun ExpenseList(
    values: List<VehicleExpenseContract>,
    language: AppLanguage,
    onEdit: (VehicleExpenseContract) -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    modifier: Modifier
) = LedgerList(modifier, values, { it.expenseId }) { value ->
    val sign = if (value.transactionKind == VehicleExpenseTransactionKind.EXPENSE) "" else "−"
    LedgerRecordCard(
        title = "$sign${formatMinor(value.amountMinor, value.currencyExponent)} ${value.currencyCode}",
        subtitle = "${value.category.name.replace('_', ' ')} · ${formatEpochMillis(value.occurredAt)}" +
            value.vendorName?.let { " · $it" }.orEmpty() +
            if (value.envelope.serverUpdatedAt == null) {
                if (language == AppLanguage.TR) " · bekliyor" else " · pending"
            } else "",
        deleted = value.envelope.deletedAt != null,
        onEdit = { onEdit(value) }, onDelete = { onDelete(value.expenseId) },
        onRestore = { onRestore(value.expenseId) }, language = language
    )
}

@Composable
private fun ReminderList(
    values: List<VehicleReminderContract>,
    language: AppLanguage,
    onEdit: (VehicleReminderContract) -> Unit,
    onComplete: (String) -> Unit,
    onSnooze: (String, Long) -> Unit,
    onDisable: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier
) = LedgerList(modifier, values, { it.reminderId }) { value ->
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value.title, fontWeight = FontWeight.Bold)
            Text(buildString {
                append(value.status.name)
                value.dueEpochDay?.let { append(" · "); append(LocalDate.ofEpochDay(it)) }
                value.dueOdometerMeters?.let {
                    append(" · "); append(VehicleLedgerUnits.metersToKilometers(it).stripTrailingZeros()); append(" km")
                }
                if (value.envelope.serverUpdatedAt == null) append(if (language == AppLanguage.TR) " · bekliyor" else " · pending")
            })
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onEdit(value) }) { Text(if (language == AppLanguage.TR) "Düzenle" else "Edit") }
                TextButton(onClick = { onComplete(value.reminderId) }) { Text(if (language == AppLanguage.TR) "Tamamla" else "Complete") }
                TextButton(onClick = { onSnooze(value.reminderId, LocalDate.now().plusDays(7).toEpochDay()) }) {
                    Text(if (language == AppLanguage.TR) "7 gün ertele" else "Snooze 7d")
                }
                TextButton(onClick = { onDisable(value.reminderId) }) { Text(if (language == AppLanguage.TR) "Kapat" else "Disable") }
            }
            TextButton(onClick = { onDelete(value.reminderId) }) {
                Text(if (language == AppLanguage.TR) "Sil" else "Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun <T> LedgerList(
    modifier: Modifier,
    values: List<T>,
    key: (T) -> Any,
    content: @Composable (T) -> Unit
) {
    if (values.isEmpty()) {
        Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("—")
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { items(values, key = key) { content(it) } }
    }
}

@Composable
private fun LedgerRecordCard(
    title: String,
    subtitle: String,
    deleted: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    language: AppLanguage
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle)
            if (deleted) {
                Text(if (language == AppLanguage.TR) "Silinmiş kayıt" else "Deleted record",
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRestore) { Text(if (language == AppLanguage.TR) "Geri yükle" else "Restore") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit) { Text(if (language == AppLanguage.TR) "Düzenle" else "Edit") }
                    TextButton(onClick = onDelete) {
                        Text(if (language == AppLanguage.TR) "Sil" else "Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun OdometerDialog(
    current: VehicleOdometerEntryContract?,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var kilometers by remember(current) { mutableStateOf(current?.odometerMeters?.let {
        VehicleLedgerUnits.metersToKilometers(it).stripTrailingZeros().toPlainString()
    }.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (language == AppLanguage.TR) "Kilometre ölçümü" else "Odometer reading") },
        text = { OutlinedTextField(kilometers, { kilometers = it }, label = { Text("km") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(kilometers) }) { Text(if (language == AppLanguage.TR) "Kaydet" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (language == AppLanguage.TR) "İptal" else "Cancel") } })
}

@Composable
private fun ExpenseDialog(
    current: VehicleExpenseContract?,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, VehicleExpenseCategory, VehicleExpenseTransactionKind, String?, String?) -> Unit
) {
    var amount by remember(current) { mutableStateOf(current?.let { formatMinor(it.amountMinor, it.currencyExponent) }.orEmpty()) }
    var currency by remember(current) { mutableStateOf(current?.currencyCode ?: "EUR") }
    var exponent by remember(current) { mutableStateOf(current?.currencyExponent ?: 2) }
    var category by remember(current) { mutableStateOf(current?.category ?: VehicleExpenseCategory.OTHER) }
    var kind by remember(current) { mutableStateOf(current?.transactionKind ?: VehicleExpenseTransactionKind.EXPENSE) }
    var vendor by remember(current) { mutableStateOf(current?.vendorName.orEmpty()) }
    var notes by remember(current) { mutableStateOf(current?.notes.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (language == AppLanguage.TR) "Genel gider" else "General expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text(if (language == AppLanguage.TR) "Tutar" else "Amount") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(currency, { currency = it.take(3) }, label = { Text("ISO 4217") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(exponent.toString(), { exponent = it.toIntOrNull()?.coerceIn(0, 4) ?: exponent }, label = { Text(if (language == AppLanguage.TR) "Üs" else "Exponent") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text(if (language == AppLanguage.TR) "Kategori" else "Category")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(VehicleExpenseCategory.entries.filter { it != VehicleExpenseCategory.UNKNOWN }) { value ->
                        OutlinedButton(onClick = { category = value }, enabled = category != value) { Text(value.name.replace('_', ' ')) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(VehicleExpenseTransactionKind.EXPENSE, VehicleExpenseTransactionKind.REFUND,
                        VehicleExpenseTransactionKind.CREDIT).forEach { value ->
                        OutlinedButton(onClick = { kind = value }, enabled = kind != value) { Text(value.name) }
                    }
                }
                OutlinedTextField(vendor, { vendor = it }, label = { Text(if (language == AppLanguage.TR) "Satıcı" else "Vendor") })
                OutlinedTextField(notes, { notes = it }, label = { Text(if (language == AppLanguage.TR) "Not" else "Notes") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(amount, currency, exponent, category, kind, vendor, notes) }) { Text(if (language == AppLanguage.TR) "Kaydet" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (language == AppLanguage.TR) "İptal" else "Cancel") } })
}

@Composable
private fun ReminderDialog(
    current: VehicleReminderContract?,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String, Long?, String?, VehicleReminderType) -> Unit
) {
    var title by remember(current) { mutableStateOf(current?.title.orEmpty()) }
    var dueDay by remember(current) { mutableStateOf(current?.dueEpochDay?.toString().orEmpty()) }
    var dueKm by remember(current) { mutableStateOf(current?.dueOdometerMeters?.let {
        VehicleLedgerUnits.metersToKilometers(it).stripTrailingZeros().toPlainString()
    }.orEmpty()) }
    var type by remember(current) { mutableStateOf(current?.reminderType ?: VehicleReminderType.OTHER) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (language == AppLanguage.TR) "Hatırlatma" else "Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(if (language == AppLanguage.TR) "Başlık" else "Title") })
                OutlinedTextField(dueDay, { dueDay = it }, label = { Text(if (language == AppLanguage.TR) "Son gün (epoch day)" else "Due epoch day") }, singleLine = true)
                OutlinedTextField(dueKm, { dueKm = it }, label = { Text(if (language == AppLanguage.TR) "Son kilometre" else "Due odometer (km)") }, singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(VehicleReminderType.entries.filter { it != VehicleReminderType.UNKNOWN }) { value ->
                        OutlinedButton(onClick = { type = value }, enabled = type != value) { Text(value.name) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, dueDay.toLongOrNull(), dueKm, type) }) { Text(if (language == AppLanguage.TR) "Kaydet" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (language == AppLanguage.TR) "İptal" else "Cancel") } })
}

private fun formatMinor(amountMinor: Long, exponent: Int): String =
    BigDecimal.valueOf(amountMinor).movePointLeft(exponent).toPlainString()

private fun formatEpochMillis(value: Long): String =
    java.time.Instant.ofEpochMilli(value).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
