package shared.vehiclephoto.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiclePhotoContractTest {
    @Test
    fun pathsAndFieldNamesAreStable() {
        assertEquals(
            "users/u/vehicles/v/photos/p",
            VehiclePhotoContractSpec.firestorePath("u", "v", "p")
        )
        assertEquals(
            "users/u/vehicles/v/photos/p.jpg",
            VehiclePhotoContractSpec.storagePath("u", "v", "p")
        )
        assertEquals(1, VehiclePhotoContractSpec.CURRENT_SCHEMA_VERSION)
        assertTrue(VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT in VehiclePhotoContractSpec.REQUIRED_FIELDS)
    }

    @Test
    fun goldenActiveFixtureParsesAndPreservesUnknownFields() {
        val parsed = VehiclePhotoParser.parse(
            "photo-fixture",
            VehiclePhotoGoldenFixtures.active,
            "owner-fixture"
        ) as VehiclePhotoParseResult.Valid
        assertEquals(VehiclePhotoState.ACTIVE, parsed.value.photo.state)
        assertEquals("preserve-on-merge", parsed.unknownFields["futureField"])
        assertEquals(VehiclePhotoSource.TOPLU_TASIMA, parsed.value.photo.source)
    }

    @Test
    fun tombstoneParsesWithoutHardDelete() {
        val parsed = VehiclePhotoParser.parse(
            "photo-fixture",
            VehiclePhotoGoldenFixtures.tombstone,
            "owner-fixture"
        ) as VehiclePhotoParseResult.Valid
        assertEquals(VehiclePhotoState.TOMBSTONE, parsed.value.photo.state)
        assertTrue(parsed.value.photo.deletedAt != null)
    }

    @Test
    fun unknownSourceFallsBackWithoutRejectingOtherwiseValidRecord() {
        val parsed = VehiclePhotoParser.parse(
            "photo-fixture",
            VehiclePhotoGoldenFixtures.active + mapOf(
                VehiclePhotoContractSpec.FIELD_SOURCE to "FUTURE_CLIENT"
            ),
            "owner-fixture"
        ) as VehiclePhotoParseResult.Valid
        assertEquals(VehiclePhotoSource.UNKNOWN, parsed.value.photo.source)
    }

    @Test
    fun documentIdAndOwnerMismatchAreRejected() {
        val wrongDocument = VehiclePhotoParser.parse(
            "other-photo",
            VehiclePhotoGoldenFixtures.active,
            "owner-fixture"
        ) as VehiclePhotoParseResult.Invalid
        assertTrue(VehiclePhotoValidationIssue.PHOTO_ID_DOCUMENT_ID_MISMATCH in wrongDocument.issues)

        val wrongOwner = VehiclePhotoParser.parse(
            "photo-fixture",
            VehiclePhotoGoldenFixtures.active,
            "another-owner"
        ) as VehiclePhotoParseResult.Invalid
        assertTrue(VehiclePhotoValidationIssue.INVALID_OWNER_ID in wrongOwner.issues)
    }

    @Test
    fun unknownSchemaIsRetainedAsUnsupported() {
        val result = VehiclePhotoParser.parse(
            "photo-fixture",
            VehiclePhotoGoldenFixtures.active + mapOf(
                VehiclePhotoContractSpec.FIELD_SCHEMA_VERSION to 9
            )
        )
        assertTrue(result is VehiclePhotoParseResult.Unsupported)
    }

    @Test
    fun duplicateOperationIsIdempotentAndDeleteWins() {
        val active = valid(VehiclePhotoGoldenFixtures.active)
        val duplicate = VehiclePhotoConflictResolver.resolve(active, active, vehicleDeleted = false)
        assertEquals(VehiclePhotoWinnerReason.IDENTICAL, duplicate.reason)
        assertFalse(duplicate.conflict)

        val tombstone = valid(VehiclePhotoGoldenFixtures.tombstone)
        val deleteWins = VehiclePhotoConflictResolver.resolve(active, tombstone, vehicleDeleted = false)
        assertEquals(VehiclePhotoWinnerReason.PHOTO_TOMBSTONE, deleteWins.reason)
        assertEquals(VehiclePhotoState.TOMBSTONE, deleteWins.winner?.photo?.state)
    }

    @Test
    fun vehicleTombstoneSuppressesPhotoMutation() {
        val result = VehiclePhotoConflictResolver.resolve(
            valid(VehiclePhotoGoldenFixtures.active),
            valid(VehiclePhotoGoldenFixtures.tombstone),
            vehicleDeleted = true
        )
        assertEquals(VehiclePhotoWinnerReason.VEHICLE_TOMBSTONE, result.reason)
        assertNull(result.winner)
    }

    @Test
    fun revisionRulesRejectNegativeAndAdvanceOnce() {
        assertEquals(8L, VehiclePhotoRevision.next(7L))
        val invalid = validContract().copy(revision = -1L)
        assertTrue(VehiclePhotoValidationIssue.NEGATIVE_REVISION in invalid.validate())
    }

    private fun valid(fields: Map<String, Any?>): VersionedVehiclePhoto =
        (VehiclePhotoParser.parse("photo-fixture", fields, "owner-fixture") as VehiclePhotoParseResult.Valid).value

    private fun validContract(): VehiclePhotoContract = valid(VehiclePhotoGoldenFixtures.active).photo
}
