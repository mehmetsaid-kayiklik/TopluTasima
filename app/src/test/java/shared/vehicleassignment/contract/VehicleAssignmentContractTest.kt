package shared.vehicleassignment.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleAssignmentContractTest {
    @Test
    fun fieldNamesPathAndSchemaAreStable() {
        assertEquals("vehicleAssignments", VehicleAssignmentContractSpec.ASSIGNMENTS_COLLECTION)
        assertEquals(1, VehicleAssignmentContractSpec.CURRENT_SCHEMA_VERSION)
        assertEquals(
            "users/uid-1/vehicleAssignments/vehicle-1",
            VehicleAssignmentContractSpec.assignmentPath("uid-1", "vehicle-1")
        )
        assertEquals(
            setOf(
                "vehicleId",
                "personId",
                "schemaVersion",
                "revision",
                "operationId",
                "source",
                "clientUpdatedAt",
                "_serverUpdatedAt",
                "deletedAt"
            ),
            VehicleAssignmentContractSpec.assignmentFields
        )
    }

    @Test
    fun assignmentV1ParsesAndPreservesUnknownFields() {
        val result = VehicleAssignmentParser.parse(
            "vehicle-1",
            VehicleAssignmentGoldenFixtures.unknownExtraField
        ) as VehicleAssignmentParseResult.Valid

        assertEquals("person-1", result.assignment.personId)
        assertEquals(3L, result.assignment.revision)
        assertEquals(VehicleAssignmentSource.BELLEK, result.assignment.source)
        assertEquals("preserve-me", result.unknownFields["futureField"])
        assertTrue(VehicleAssignmentParseWarning.UNKNOWN_FIELDS_PRESERVED in result.warnings)
    }

    @Test
    fun unknownSourceFallsBackWithoutDroppingRecord() {
        val result = VehicleAssignmentParser.parse(
            "vehicle-1",
            VehicleAssignmentGoldenFixtures.unknownSource
        ) as VehicleAssignmentParseResult.Valid

        assertEquals(VehicleAssignmentSource.UNKNOWN, result.assignment.source)
        assertTrue(VehicleAssignmentParseWarning.UNKNOWN_SOURCE in result.warnings)
    }

    @Test
    fun tombstoneRetainsPersonForAuditAndParsesDeterministically() {
        val result = VehicleAssignmentParser.parse(
            "vehicle-1",
            VehicleAssignmentGoldenFixtures.tombstone
        ) as VehicleAssignmentParseResult.Valid

        assertEquals(VehicleAssignmentState.TOMBSTONE, result.assignment.state)
        assertEquals("person-1", result.assignment.personId)
        assertEquals(4L, result.assignment.revision)
        assertTrue(result.assignment.deletedAt != null)
    }

    @Test
    fun missingServerTimestampIsAWarningNotSilentFabrication() {
        val result = VehicleAssignmentParser.parse(
            "vehicle-1",
            VehicleAssignmentGoldenFixtures.missingServerTimestamp
        ) as VehicleAssignmentParseResult.Valid

        assertNull(result.serverUpdatedAt)
        assertTrue(VehicleAssignmentParseWarning.MISSING_SERVER_UPDATED_AT in result.warnings)
    }

    @Test
    fun documentIdPayloadMismatchIsRejected() {
        val result = VehicleAssignmentParser.parse(
            "different-vehicle",
            VehicleAssignmentGoldenFixtures.assignmentV1
        ) as VehicleAssignmentParseResult.Invalid

        assertTrue(VehicleAssignmentValidationIssue.INVALID_DOCUMENT_ID in result.issues)
    }

    @Test
    fun revisionRulesRejectNegativeAndAdvanceOnce() {
        val invalid = VehicleAssignmentGoldenFixtures.assignmentV1 + ("revision" to -1L)
        val result = VehicleAssignmentParser.parse("vehicle-1", invalid)
            as VehicleAssignmentParseResult.Invalid
        assertTrue(VehicleAssignmentValidationIssue.INVALID_REVISION in result.issues)
        assertEquals(8L, AssignmentRevision.next(7L))
    }

    @Test
    fun duplicateOperationIsIdempotent() {
        val parsed = VehicleAssignmentParser.parse(
            "vehicle-1",
            VehicleAssignmentGoldenFixtures.assignmentV1
        ) as VehicleAssignmentParseResult.Valid
        val first = VersionedVehicleAssignment(
            parsed.assignment,
            AssignmentServerTimestamp.fromEpochMillis(10L)
        )
        val repeated = first.copy(
            serverUpdatedAt = AssignmentServerTimestamp.fromEpochMillis(20L)
        )

        val resolution = VehicleAssignmentConflictResolver.resolve(first, repeated)

        assertEquals(VehicleAssignmentResolutionReason.SAME_OPERATION, resolution.reason)
        assertNull(resolution.loser)
        assertEquals(3L, resolution.winner?.assignment?.revision)
    }

    @Test
    fun higherRevisionThenServerTimestampThenOperationIdBreakTies() {
        val parsed = VehicleAssignmentParser.parse(
            "vehicle-1",
            VehicleAssignmentGoldenFixtures.assignmentV1
        ) as VehicleAssignmentParseResult.Valid
        val old = VersionedVehicleAssignment(
            parsed.assignment,
            AssignmentServerTimestamp(10L, 0)
        )
        val higher = old.copy(assignment = old.assignment.copy(revision = 4L, operationId = "op-b"))
        assertSame(higher, VehicleAssignmentConflictResolver.resolve(old, higher).winner)

        val sameRevisionOlder = higher.copy(serverUpdatedAt = AssignmentServerTimestamp(11L, 0))
        val sameRevisionNewer = higher.copy(
            assignment = higher.assignment.copy(operationId = "op-c"),
            serverUpdatedAt = AssignmentServerTimestamp(12L, 0)
        )
        assertSame(
            sameRevisionNewer,
            VehicleAssignmentConflictResolver.resolve(sameRevisionOlder, sameRevisionNewer).winner
        )

        val tieA = sameRevisionNewer.copy(
            assignment = sameRevisionNewer.assignment.copy(operationId = "op-a")
        )
        val tieZ = sameRevisionNewer.copy(
            assignment = sameRevisionNewer.assignment.copy(operationId = "op-z")
        )
        assertSame(tieZ, VehicleAssignmentConflictResolver.resolve(tieA, tieZ).winner)
    }

    @Test
    fun vehicleTombstoneAlwaysRejectsAssignmentMutation() {
        val parsed = VehicleAssignmentParser.parse(
            "vehicle-1",
            VehicleAssignmentGoldenFixtures.assignmentV1
        ) as VehicleAssignmentParseResult.Valid
        val candidate = VersionedVehicleAssignment(parsed.assignment, null)
        val result = VehicleAssignmentConflictResolver.resolve(
            candidate,
            candidate.copy(assignment = candidate.assignment.copy(operationId = "other")),
            vehicleDeleted = true
        )

        assertNull(result.winner)
        assertEquals(VehicleAssignmentResolutionReason.VEHICLE_TOMBSTONE, result.reason)
    }

    @Test
    fun operationAndOpaqueIdsRejectPathInjection() {
        assertFalse(OpaqueDocumentId.isValid("vehicle/other"))
        assertFalse(AssignmentOperationId.isValid("operation/other"))
        assertTrue(OpaqueDocumentId.isValid("550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun vehicleGoldenFixturesRetainCompatibilitySignals() {
        assertEquals(
            "person-stale",
            VehicleAssignmentGoldenFixtures.staleMirrorVehicle["assignedPersonId"]
        )
        assertEquals(
            "preserve-me",
            VehicleAssignmentGoldenFixtures.currentVehicle["futureVehicleField"]
        )
        assertEquals(
            "person-payload",
            VehicleAssignmentGoldenFixtures.personIdMismatch["id"]
        )
    }
}
