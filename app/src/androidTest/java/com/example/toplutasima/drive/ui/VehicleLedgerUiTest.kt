package com.example.toplutasima.drive.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.toplutasima.drive.ledger.CurrentOdometerProjection
import com.example.toplutasima.drive.ledger.ExpenseSummary
import com.example.toplutasima.drive.ledger.VehicleLedgerDashboard
import com.example.toplutasima.drive.ledger.VehicleLedgerPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import shared.vehicleledger.contract.VehicleExpenseCategory

class VehicleLedgerUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardShowsSeparateCurrencyTotalsAndOpensStablePage() {
        var opened: VehicleLedgerPage? = null
        compose.setContent {
            MaterialTheme {
                VehicleLedgerDashboardSection(
                    state = VehicleLedgerUiState(
                        dashboard = VehicleLedgerDashboard(
                            CurrentOdometerProjection(null, 123_000, false, false),
                            dueReminderCount = 2,
                            monthExpenseTotals = listOf(
                                ExpenseSummary(VehicleExpenseCategory.PARKING, "EUR", 2, 1000, 1),
                                ExpenseSummary(VehicleExpenseCategory.TOLL, "USD", 2, 500, 1)
                            ),
                            pendingOperationCount = 1,
                            unresolvedConflictCount = 1
                        )
                    ),
                    onOpen = { opened = it }
                )
            }
        }
        compose.onNodeWithTag(VehicleLedgerTestTags.OPEN_EXPENSES).performClick()
        assertEquals(VehicleLedgerPage.EXPENSES, opened)
    }

    @Test
    fun odometerPageExposesAddAndRetryWithoutTechnicalErrorText() {
        compose.setContent {
            MaterialTheme {
                VehicleLedgerPageScreen(
                    state = VehicleLedgerUiState(
                        vehicleId = "vehicle-1",
                        page = VehicleLedgerPage.ODOMETER,
                        dashboard = VehicleLedgerDashboard(
                            CurrentOdometerProjection(null, null, false, false),
                            0, emptyList(), 1, 0
                        )
                    ),
                    onBack = {}, onSaveOdometer = { _, _ -> }, onDeleteOdometer = {},
                    onRestoreOdometer = {},
                    onSaveExpense = { _, _, _, _, _, _, _, _ -> },
                    onDeleteExpense = {}, onRestoreExpense = {},
                    onSaveReminder = { _, _, _, _, _ -> }, onCompleteReminder = {},
                    onSnoozeReminder = { _, _ -> }, onDisableReminder = {},
                    onDeleteReminder = {}, onRetry = {}
                )
            }
        }
        compose.onNodeWithTag(VehicleLedgerTestTags.RETRY).performClick()
        compose.onNodeWithTag(VehicleLedgerTestTags.ADD).performClick()
    }
}
