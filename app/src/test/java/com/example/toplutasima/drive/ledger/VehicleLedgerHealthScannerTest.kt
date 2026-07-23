package com.example.toplutasima.drive.ledger

import com.example.toplutasima.data.local.entity.DriveLedgerConflictEntity
import com.example.toplutasima.data.local.entity.DriveLedgerOperationEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import shared.vehicleledger.contract.OdometerQuality
import shared.vehicleledger.contract.OdometerReadingRole
import shared.vehicleledger.contract.VehicleExpenseCategory
import shared.vehicleledger.contract.VehicleExpenseContract
import shared.vehicleledger.contract.VehicleExpenseTransactionKind
import shared.vehicleledger.contract.VehicleLedgerEnvelope
import shared.vehicleledger.contract.VehicleLedgerSource
import shared.vehicleledger.contract.VehicleOdometerEntryContract
import shared.vehicleledger.contract.VehicleReminderContract
import shared.vehicleledger.contract.VehicleReminderRecurrenceAnchor
import shared.vehicleledger.contract.VehicleReminderStatus
import shared.vehicleledger.contract.VehicleReminderType

class VehicleLedgerHealthScannerTest {
    @Test
    fun `scanner reports nonmonotonic mirror duplicate reminder and durable sync issues`() {
        val oldHigh = odometer("old", 1, 200_000)
        val current = odometer("new", 2, 100_000)
        val expenseA = expense("a")
        val expenseB = expense("b")
        val reminder = reminder()
        val operation = DriveLedgerOperationEntity(
            OWNER, OP_A, "batch", "EXPENSE", "a", VEHICLE, "CREATE", 1,
            "SUCCEEDED", 1, 0, null, null, null, 0, 0
        )
        val conflict = DriveLedgerConflictEntity(
            OWNER, "conflict", "EXPENSE", "a", VEHICLE, OP_A, OP_B,
            1, 1, "{}", "{}", OP_B, "OPERATION_ID", 0, null
        )
        val codes = VehicleLedgerHealthScanner.scan(
            VEHICLE, true, false, 99.0, listOf(oldHigh, current),
            listOf(expenseA, expenseB), listOf(reminder), listOf(operation),
            emptyList(), listOf(conflict), 100_000_000, 10
        ).map { it.code }.toSet()

        assertTrue(VehicleLedgerHealthCode.ODOMETER_NON_MONOTONIC in codes)
        assertTrue(VehicleLedgerHealthCode.ODOMETER_MIRROR_STALE in codes)
        assertTrue(VehicleLedgerHealthCode.EXPENSE_DUPLICATE_SUSPECTED in codes)
        assertTrue(VehicleLedgerHealthCode.LEDGER_RECEIPT_MISSING in codes)
        assertTrue(VehicleLedgerHealthCode.LEDGER_CONFLICT_UNRESOLVED in codes)

        val missingCurrentCodes = VehicleLedgerHealthScanner.scan(
            VEHICLE, true, false, null, emptyList(),
            emptyList(), listOf(reminder), emptyList(),
            emptyList(), emptyList(), 100_000_000, 10
        ).map { it.code }.toSet()
        assertTrue(VehicleLedgerHealthCode.REMINDER_ODOMETER_STALE in missingCurrentCodes)
    }

    private fun envelope(id: String) = VehicleLedgerEnvelope(
        OWNER, VEHICLE, 1, 1,
        java.util.UUID.nameUUIDFromBytes(id.toByteArray()).toString(),
        VehicleLedgerSource.MANUAL, 1, 1, null, null
    )

    private fun odometer(id: String, observed: Long, meters: Long) = VehicleOdometerEntryContract(
        id, observed, meters, OdometerQuality.CONFIRMED, OdometerReadingRole.MANUAL,
        "series", null, null, null, null, null, envelope(id)
    )

    private fun expense(id: String) = VehicleExpenseContract(
        id, 1, VehicleExpenseCategory.PARKING, VehicleExpenseTransactionKind.EXPENSE,
        100, "EUR", 2, null, null, null, null, null, null, null, null, null,
        "duplicate", null, envelope(id)
    )

    private fun reminder() = VehicleReminderContract(
        "reminder", "Due", VehicleReminderType.OTHER, VehicleReminderStatus.ACTIVE,
        null, 500_000, null, null, VehicleReminderRecurrenceAnchor.LAST_COMPLETION,
        null, null, null, null, null, null, null, null, envelope("reminder")
    )

    private companion object {
        const val OWNER = "owner"
        const val VEHICLE = "vehicle"
        const val OP_A = "11111111-1111-4111-8111-111111111111"
        const val OP_B = "22222222-2222-4222-8222-222222222222"
    }
}
