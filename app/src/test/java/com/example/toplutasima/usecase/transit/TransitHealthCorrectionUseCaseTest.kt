package com.example.toplutasima.usecase.transit

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.domain.transit.health.TransitHealthFieldTarget
import com.example.toplutasima.domain.transit.health.TransitHealthIssue
import com.example.toplutasima.domain.transit.health.TransitHealthIssueCode
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.domain.transit.validation.TransitValidationField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitHealthCorrectionUseCaseTest {
    private val useCase = TransitHealthCorrectionUseCase()

    @Test
    fun `safe duration correction is only proposed and previewed without mutation`() {
        val record = trip("one", "-5")
        val issue = durationIssue(record.id)

        val correction = useCase.propose(listOf(record), listOf(issue)).single()
        val original = mapOf<String, Any?>("gercekYolSuresi" to "-5", "hat" to "S5")
        val preview = useCase.preview(original, correction)

        assertEquals("-5", original["gercekYolSuresi"])
        assertEquals("30", preview["gercekYolSuresi"])
        assertEquals("S5", preview["hat"])
    }

    @Test
    fun `ambiguous duplicate or reversed time never receives automatic correction`() {
        val reversed = trip("one", null).copy(gercekBinis = "18:00", gercekInis = "17:00")
        val duplicateIssue = TransitHealthIssue(
            code = TransitHealthIssueCode.POSSIBLE_DUPLICATE,
            severity = TransitHealthSeverity.WARNING,
            localRecordId = reversed.id,
            target = TransitHealthFieldTarget(TransitValidationField.RECORD)
        )
        val mismatchIssue = durationIssue(reversed.id)

        val corrections = useCase.propose(listOf(reversed), listOf(duplicateIssue, mismatchIssue))

        assertTrue(corrections.isEmpty())
    }

    private fun durationIssue(id: String) = TransitHealthIssue(
        code = TransitHealthIssueCode.NEGATIVE_DURATION,
        severity = TransitHealthSeverity.CRITICAL,
        localRecordId = id,
        target = TransitHealthFieldTarget(TransitValidationField.ACTUAL_ARRIVAL)
    )

    private fun trip(id: String, storedDuration: String?): TripEntity = TripEntity(
        id = id,
        firestoreDocId = "doc-$id",
        tarih = "22.05.2026",
        hat = "S5",
        binisDuragi = "Frankfurt Hbf",
        inisDuragi = "Bad Homburg",
        planlananBinis = "10:00",
        planlananInis = "10:30",
        gercekBinis = "10:00",
        gercekInis = "10:30",
        gercekYolSuresi = storedDuration,
        userId = "uid-a"
    )
}
