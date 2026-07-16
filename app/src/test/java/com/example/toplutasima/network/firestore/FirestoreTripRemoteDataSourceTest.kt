package com.example.toplutasima.network.firestore

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreTripRemoteDataSourceTest {
    @Test
    fun `save rejects a trip whose document id was not persisted locally`() = runBlocking {
        val writer = RecordingDocumentWriter()
        val dataSource = FirestoreTripRemoteDataSource(writer)

        val result = runCatching {
            dataSource.saveTrip(mapOf("id" to "missing-document-id"), userId = "user-A")
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, writer.generatedIdCount)
        assertEquals(0, writer.writeCount)
    }

    @Test
    fun `save reuses a preassigned id and writes derived date fields`() = runBlocking {
        val writer = RecordingDocumentWriter()
        val dataSource = FirestoreTripRemoteDataSource(writer)
        val trip = mapOf(
            "id" to "trip-2",
            "firestoreDocId" to "persisted-document-id",
            "tarih" to "15.07.2026"
        )

        val documentId = dataSource.saveTrip(trip, userId = "user-B")

        assertEquals("persisted-document-id", documentId)
        assertEquals(0, writer.generatedIdCount)
        assertEquals(1, writer.writeCount)
        assertEquals(listOf("user-B"), writer.writeUserIds)
        val written = writer.documents[documentId]
        assertNotNull(written)
        assertEquals(documentId, written!!["firestoreDocId"])
        assertEquals("2026-07", written["yearMonth"])
        assertEquals("2026-07-15", written["sortDate"])
        assertTrue(written["updatedAt"] is Long)
    }

    @Test
    fun `retry after timeout writes the same generated document`() = runBlocking {
        val writer = TimeoutAfterFirstWriteDocumentWriter()
        val dataSource = FirestoreTripRemoteDataSource(writer)
        val localTrip = mutableMapOf<String, Any?>("id" to "trip-1")

        val generatedDocumentId = dataSource.newTripDocumentId()
        localTrip["firestoreDocId"] = generatedDocumentId

        val firstWrite = runCatching { dataSource.saveTrip(localTrip, userId = "user-A") }
        assertTrue(firstWrite.exceptionOrNull() is IOException)
        assertEquals(generatedDocumentId, localTrip["firestoreDocId"])

        val retryDocumentId = dataSource.saveTrip(localTrip, userId = "user-A")

        assertEquals(generatedDocumentId, retryDocumentId)
        assertEquals(1, writer.generatedIdCount)
        assertEquals(2, writer.writeCount)
        assertEquals(listOf("user-A", "user-A"), writer.writeUserIds)
        assertEquals(setOf(generatedDocumentId), writer.documents.keys)
    }

    private class TimeoutAfterFirstWriteDocumentWriter : TripDocumentWriter {
        val documents = linkedMapOf<String, Map<String, Any?>>()
        var generatedIdCount = 0
        var writeCount = 0
        val writeUserIds = mutableListOf<String?>()

        override fun newDocumentId(): String {
            generatedIdCount++
            return "generated-doc-id"
        }

        override suspend fun setDocument(
            userId: String?,
            documentId: String,
            data: Map<String, Any?>
        ) {
            writeCount++
            writeUserIds += userId
            documents[documentId] = data
            if (writeCount == 1) {
                throw IOException("Timed out after the server accepted the write")
            }
        }
    }

    private class RecordingDocumentWriter : TripDocumentWriter {
        val documents = linkedMapOf<String, Map<String, Any?>>()
        var generatedIdCount = 0
        var writeCount = 0
        val writeUserIds = mutableListOf<String?>()

        override fun newDocumentId(): String {
            generatedIdCount += 1
            return "generated-document-id"
        }

        override suspend fun setDocument(
            userId: String?,
            documentId: String,
            data: Map<String, Any?>
        ) {
            writeCount += 1
            writeUserIds += userId
            documents[documentId] = data
        }
    }
}
