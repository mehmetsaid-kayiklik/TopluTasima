package com.example.toplutasima.drive.assignment

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreRulesShapeTest {
    @Test
    fun assignmentRuleCannotBeBypassedByRecursiveOwnerRule() {
        val rules = locateRules().readText()
        assertTrue(rules.contains("match /users/{userId}/vehicleAssignments/{vehicleId}"))
        assertTrue(rules.contains("allow delete: if false"))
        assertTrue(rules.contains("data._serverUpdatedAt == request.time"))
        assertTrue(rules.contains("request.resource.data.revision >= resource.data.revision"))
        assertTrue(rules.contains("collection != 'vehicleAssignments'"))
        assertTrue(rules.contains("data.vehicleId == vehicleId"))
        assertTrue(rules.contains("data.schemaVersion == 1"))
        assertFalse(rules.contains("match /users/{userId}/{document=**}"))
    }

    private fun locateRules(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.map { File(it, "firestore.rules") }.firstOrNull(File::isFile)
        ?: error("firestore.rules not found")
}
