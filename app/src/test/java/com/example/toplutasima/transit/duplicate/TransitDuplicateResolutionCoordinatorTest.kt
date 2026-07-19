package com.example.toplutasima.transit.duplicate

import com.example.toplutasima.data.local.entity.TripEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitDuplicateResolutionCoordinatorTest {
    @Test
    fun `merged record is written before source is deleted`() = runBlocking {
        val calls = mutableListOf<String>()
        val result = TransitDuplicateResolutionCoordinator().execute(
            preview = validPreview(),
            saveMergedRecord = { calls += "save" },
            afterMergedRecordSaved = { calls += "metadata" },
            deleteSourceRecord = { calls += "delete" }
        )

        assertTrue(result is TransitDuplicateResolutionResult.Applied)
        assertEquals(listOf("save", "metadata", "delete"), calls)
    }

    @Test
    fun `write failure never reaches source deletion`() = runBlocking {
        var deleted = false
        val result = TransitDuplicateResolutionCoordinator().execute(
            preview = validPreview(),
            saveMergedRecord = { error("write failed") },
            deleteSourceRecord = { deleted = true }
        )

        assertTrue(result is TransitDuplicateResolutionResult.SaveFailed)
        assertFalse(deleted)
    }

    @Test
    fun `delete failure leaves successfully written target`() = runBlocking {
        var saved = false
        val result = TransitDuplicateResolutionCoordinator().execute(
            preview = validPreview(),
            saveMergedRecord = { saved = true },
            deleteSourceRecord = { error("delete failed") }
        )

        assertTrue(saved)
        assertTrue(result is TransitDuplicateResolutionResult.DeleteFailed)
    }

    @Test
    fun `critical validation blocks both mutations`() = runBlocking {
        var mutated = false
        val invalid = validPreview().copy(
            validationIssues = listOf(
                com.example.toplutasima.domain.transit.validation.ValidationIssue(
                    code = com.example.toplutasima.domain.transit.validation.ValidationIssueCode.SAME_STOP,
                    severity = com.example.toplutasima.domain.transit.validation.ValidationSeverity.CRITICAL,
                    target = com.example.toplutasima.domain.transit.validation.ValidationFieldTarget(
                        com.example.toplutasima.domain.transit.validation.TransitValidationField.BOARDING_STOP
                    ),
                    detail = "critical"
                )
            )
        )
        val result = TransitDuplicateResolutionCoordinator().execute(
            preview = invalid,
            saveMergedRecord = { mutated = true },
            deleteSourceRecord = { mutated = true }
        )

        assertTrue(result is TransitDuplicateResolutionResult.ValidationBlocked)
        assertFalse(mutated)
    }

    @Test
    fun `disabled health dependent coordinator performs no mutation`() = runBlocking {
        var mutated = false
        val result = TransitDuplicateResolutionCoordinator(enabled = false).execute(
            preview = validPreview(),
            saveMergedRecord = { mutated = true },
            deleteSourceRecord = { mutated = true }
        )

        assertTrue(result is TransitDuplicateResolutionResult.ValidationBlocked)
        assertFalse(mutated)
    }

    private fun validPreview() = TransitDuplicateMergePreview(
        targetRecordId = "first",
        sourceRecordIdToDelete = "second",
        mergedRecord = TripEntity(id = "first", userId = "uid"),
        selectedProvenanceByField = emptyMap(),
        validationIssues = emptyList()
    )
}
