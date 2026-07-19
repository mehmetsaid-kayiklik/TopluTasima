package com.example.toplutasima.ui.components.transit

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.transit.duplicate.TransitDuplicateReason
import com.example.toplutasima.ui.AppLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitDuplicatePresentationTextTest {

    @Test
    fun `similarity is described as evidence instead of a probability`() {
        val signal = TransitDuplicatePresentationText.similaritySignal(
            score = 0.94,
            reasonCount = 8,
            lang = AppLanguage.EN
        )

        assertTrue(signal.contains("signal", ignoreCase = true))
        assertFalse(signal.contains("%"))
        assertTrue(
            TransitDuplicatePresentationText.notCertainty(AppLanguage.EN)
                .contains("not a confirmed match", ignoreCase = true)
        )
    }

    @Test
    fun `reason labels explain the concrete matching signal`() {
        assertTrue(
            TransitDuplicatePresentationText.reason(
                TransitDuplicateReason.SAME_RMV_JOURNEY,
                AppLanguage.TR
            ).contains("RMV")
        )
        assertTrue(
            TransitDuplicatePresentationText.reason(
                TransitDuplicateReason.MANUAL_AND_AUTOMATIC_PAIR,
                AppLanguage.DE
            ).contains("automatisch", ignoreCase = true)
        )
    }

    @Test
    fun `comparison accessibility text contains both record summaries`() {
        val first = TripEntity(
            id = "first",
            tarih = "18.07.2026",
            hat = "U4",
            binisDuragi = "Bornheim Mitte",
            inisDuragi = "Konstablerwache",
            planlananBinis = "08:15"
        )
        val second = TripEntity(
            id = "second",
            tarih = "18.07.2026",
            hat = "U4",
            binisDuragi = "Bornheim Mitte",
            inisDuragi = "Konstablerwache",
            planlananBinis = "08:17"
        )

        val description = TransitDuplicatePresentationText.comparisonDescription(
            first,
            second,
            signal = "Strong similarity signal",
            lang = AppLanguage.EN
        )

        assertTrue(description.contains("08:15"))
        assertTrue(description.contains("08:17"))
        assertTrue(description.contains("First record"))
        assertTrue(description.contains("Second record"))
    }
}
