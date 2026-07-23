package shared.vehicleledger

import com.example.toplutasima.drive.ledger.CurrentOdometerSelector
import com.example.toplutasima.drive.ledger.ExpenseDraft
import com.example.toplutasima.drive.ledger.VehicleExpenseFingerprint
import com.example.toplutasima.drive.ledger.VehicleLedgerRoute
import com.example.toplutasima.drive.ledger.VehicleLedgerUnits
import com.example.toplutasima.drive.ledger.VehicleReminderDuePolicy
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import shared.vehicleledger.contract.LedgerServerTimestamp
import shared.vehicleledger.contract.OdometerQuality
import shared.vehicleledger.contract.OdometerReadingRole
import shared.vehicleledger.contract.VehicleExpenseCategory
import shared.vehicleledger.contract.VehicleExpenseTransactionKind
import shared.vehicleledger.contract.VehicleLedgerConflictResolver
import shared.vehicleledger.contract.VehicleLedgerContractSpec
import shared.vehicleledger.contract.VehicleLedgerEnvelope
import shared.vehicleledger.contract.VehicleLedgerParseResult
import shared.vehicleledger.contract.VehicleLedgerParser
import shared.vehicleledger.contract.VehicleLedgerSource
import shared.vehicleledger.contract.VehicleLedgerWinnerReason
import shared.vehicleledger.contract.VehicleOdometerEntryContract
import shared.vehicleledger.contract.VehicleReminderContract
import shared.vehicleledger.contract.VehicleReminderRecurrenceAnchor
import shared.vehicleledger.contract.VehicleReminderStatus
import shared.vehicleledger.contract.VehicleReminderType
import shared.vehicleledger.contract.toFields

class VehicleLedgerContractTest {
    @Test
    fun `paths schema and identity remain stable`() {
        assertEquals(1, VehicleLedgerContractSpec.SCHEMA_VERSION)
        assertEquals(
            "users/owner/vehicleOdometerEntries/entry",
            VehicleLedgerContractSpec.odometerPath("owner", "entry")
        )
        assertEquals("drive/vehicle/vehicle-1/expenses",
            VehicleLedgerRoute.parse("drive/vehicle/vehicle-1/expenses")?.path)
        assertNull(VehicleLedgerRoute.parse("drive/vehicle/bad/id/expenses"))
    }

    @Test
    fun `parser preserves unknown fields and falls back unknown enum`() {
        val source = odometer().toFields().toMutableMap().apply {
            put(VehicleLedgerContractSpec.FIELD_SOURCE, "FUTURE_SOURCE")
            put(VehicleLedgerContractSpec.FIELD_QUALITY, "FUTURE_QUALITY")
            put("futureField", "untouched")
        }
        val result = VehicleLedgerParser.parseOdometer("entry-1", source, "owner")
        assertTrue(result is VehicleLedgerParseResult.Valid)
        val value = (result as VehicleLedgerParseResult.Valid).value
        assertEquals(VehicleLedgerSource.UNKNOWN, value.envelope.source)
        assertEquals(OdometerQuality.UNKNOWN, value.quality)
        assertEquals("untouched", source["futureField"])
    }

    @Test
    fun `document id mismatch is rejected`() {
        val result = VehicleLedgerParser.parseOdometer("different", odometer().toFields(), "owner")
        assertTrue(result is VehicleLedgerParseResult.Invalid)
    }

    @Test
    fun `future schema is retained as unsupported instead of deleted`() {
        val fields = odometer().toFields().toMutableMap().apply {
            put(VehicleLedgerContractSpec.FIELD_SCHEMA_VERSION, 99)
        }
        val result = VehicleLedgerParser.parseOdometer("entry-1", fields, "owner")
        assertTrue(result is VehicleLedgerParseResult.Unsupported)
        assertEquals(99, (result as VehicleLedgerParseResult.Unsupported).schemaVersion)
    }

    @Test
    fun `same operation is idempotent and tombstone wins equal revision`() {
        val local = odometer(operationId = OP_A)
        val same = local.copy(envelope = local.envelope.copy(serverUpdatedAt = LedgerServerTimestamp(2, 0)))
        val idempotent = resolve(local, same)
        assertTrue(idempotent.idempotent)
        assertEquals(VehicleLedgerWinnerReason.SAME_OPERATION, idempotent.reason)

        val tombstone = odometer(operationId = OP_B).copy(
            envelope = odometer(operationId = OP_B).envelope.copy(deletedAt = 10)
        )
        val deleted = resolve(local, tombstone)
        assertEquals(tombstone, deleted.winner)
        assertEquals(VehicleLedgerWinnerReason.TOMBSTONE, deleted.reason)
    }

    @Test
    fun `revision timestamp and operation id tie breaks are deterministic`() {
        val lower = odometer(revision = 1, operationId = OP_A)
        val higher = odometer(revision = 2, operationId = OP_B)
        assertEquals(higher, resolve(lower, higher).winner)

        val timestampA = lower.copy(envelope = lower.envelope.copy(
            revision = 2, serverUpdatedAt = LedgerServerTimestamp(4, 0)
        ))
        val timestampB = higher.copy(envelope = higher.envelope.copy(
            serverUpdatedAt = LedgerServerTimestamp(5, 0)
        ))
        assertEquals(timestampB, resolve(timestampA, timestampB).winner)

        val operationTieA = timestampA.copy(envelope = timestampA.envelope.copy(serverUpdatedAt = null))
        val operationTieB = timestampB.copy(envelope = timestampB.envelope.copy(serverUpdatedAt = null))
        assertEquals(operationTieB, resolve(operationTieA, operationTieB).winner)
    }

    @Test
    fun `vehicle tombstone prevents active child winning`() {
        val resolution = VehicleLedgerConflictResolver.resolve(
            odometer(), null, "owner", "vehicle-1", true
        ) { it.envelope }
        assertNull(resolution.winner)
        assertTrue(resolution.conflict)
        assertEquals(VehicleLedgerWinnerReason.VEHICLE_TOMBSTONE, resolution.reason)
    }

    @Test
    fun `checked unit conversion rejects invalid legacy values`() {
        assertEquals(12_346L, VehicleLedgerUnits.legacyKilometersToMeters(12.3456))
        assertNull(VehicleLedgerUnits.legacyKilometersToMeters(Double.NaN))
        assertNull(VehicleLedgerUnits.legacyKilometersToMeters(Double.POSITIVE_INFINITY))
        assertNull(VehicleLedgerUnits.legacyKilometersToMeters(-1.0))
        assertEquals(1_609L, VehicleLedgerUnits.decimalMilesToMeters("1"))
        assertEquals(12_345L, VehicleLedgerUnits.decimalKilometersToMeters("12,345"))
        assertEquals(1234L, VehicleLedgerUnits.decimalMoneyToMinor("12.34", 2))
        assertNull(VehicleLedgerUnits.decimalMoneyToMinor("12.345", 2))
    }

    @Test
    fun `old date high value does not become current and reset series is selected`() {
        val oldHigh = odometer(id = "old", observedAt = 10, meters = 900_000)
        val newer = odometer(id = "new", observedAt = 20, meters = 100_000)
        assertEquals("new", CurrentOdometerSelector.select(listOf(oldHigh, newer))?.odometerEntryId)

        val reset = odometer(id = "reset", observedAt = 30, meters = 2_000, series = "series-2")
        assertEquals("reset", CurrentOdometerSelector.select(listOf(oldHigh, newer, reset))?.odometerEntryId)
    }

    @Test
    fun `reminder becomes due when either date or odometer threshold is reached`() {
        val reminder = reminder(dueDay = 20, dueMeters = 200_000)
        assertNull(VehicleReminderDuePolicy.evaluate(reminder, 19, 199_999))
        assertEquals(
            com.example.toplutasima.drive.ledger.ReminderDueTrigger.DATE,
            VehicleReminderDuePolicy.evaluate(reminder, 20, 199_999)?.trigger
        )
        assertEquals(
            com.example.toplutasima.drive.ledger.ReminderDueTrigger.ODOMETER,
            VehicleReminderDuePolicy.evaluate(reminder, 19, 200_000)?.trigger
        )
    }

    @Test
    fun `duplicate fingerprint is stable but does not become an identity`() {
        val draft = ExpenseDraft(
            "vehicle-1", 60_000, VehicleExpenseCategory.PARKING,
            VehicleExpenseTransactionKind.EXPENSE, 500, "eur", 2, vendorName = "Garage"
        )
        assertEquals(VehicleExpenseFingerprint.compute(draft), VehicleExpenseFingerprint.compute(draft.copy()))
        assertFalse(VehicleExpenseFingerprint.compute(draft).isBlank())
    }

    private fun resolve(a: VehicleOdometerEntryContract, b: VehicleOdometerEntryContract) =
        VehicleLedgerConflictResolver.resolve(a, b, "owner", "vehicle-1", false) { it.envelope }

    private fun odometer(
        id: String = "entry-1",
        revision: Long = 1,
        operationId: String = OP_A,
        observedAt: Long = 1,
        meters: Long = 100_000,
        series: String = "series-1"
    ) = VehicleOdometerEntryContract(
        id, observedAt, meters, OdometerQuality.CONFIRMED, OdometerReadingRole.MANUAL,
        series, null, null, null, null, null,
        VehicleLedgerEnvelope(
            "owner", "vehicle-1", 1, revision, operationId, VehicleLedgerSource.MANUAL,
            1, 1, null, null
        )
    )

    private fun reminder(dueDay: Long?, dueMeters: Long?) = VehicleReminderContract(
        "reminder-1", "Inspection", VehicleReminderType.INSPECTION,
        VehicleReminderStatus.ACTIVE, dueDay, dueMeters, null, null,
        VehicleReminderRecurrenceAnchor.LAST_COMPLETION, null, null, null,
        null, null, null, null, null,
        VehicleLedgerEnvelope("owner", "vehicle-1", 1, 1, OP_A,
            VehicleLedgerSource.MANUAL, 1, 1, null, null)
    )

    private companion object {
        const val OP_A = "11111111-1111-4111-8111-111111111111"
        const val OP_B = "22222222-2222-4222-8222-222222222222"
    }
}
