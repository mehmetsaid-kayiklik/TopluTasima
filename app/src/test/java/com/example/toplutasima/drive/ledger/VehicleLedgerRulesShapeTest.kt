package com.example.toplutasima.drive.ledger

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static guard only; the Firebase Emulator test lives in firestore-rules-tests. */
class VehicleLedgerRulesShapeTest {
    @Test
    fun `explicit ledger collections cannot fall through generic owner rule`() {
        val rules = locateRules().readText()
        assertTrue(rules.contains("match /users/{userId}/vehicleOdometerEntries/{documentId}"))
        assertTrue(rules.contains("match /users/{userId}/vehicleExpenses/{documentId}"))
        assertTrue(rules.contains("match /users/{userId}/vehicleReminders/{documentId}"))
        assertTrue(rules.contains("collection != 'vehicleOdometerEntries'"))
        assertTrue(rules.contains("collection != 'vehicleExpenses'"))
        assertTrue(rules.contains("collection != 'vehicleReminders'"))
        assertTrue(rules.contains("data.ownerUid == userId"))
        assertTrue(rules.contains("data._serverUpdatedAt == request.time"))
        assertTrue(rules.contains("data.amountMinor is int && data.amountMinor > 0"))
        assertTrue(rules.contains("data.odometerMeters is int && data.odometerMeters >= 0"))
        assertFalse(rules.contains("match /users/{userId}/{document=**}"))
    }

    private fun locateRules(): File = generateSequence(
        File(requireNotNull(System.getProperty("user.dir")))
    ) { it.parentFile }
        .map { File(it, "firestore.rules") }
        .firstOrNull(File::isFile)
        ?: error("firestore.rules not found")
}
