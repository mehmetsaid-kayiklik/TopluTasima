package com.example.toplutasima.transit.export

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class TransitExportStreamWriterTest {
    @Test
    fun `completed prepared payload is written and flushed through IO boundary`() = runBlocking {
        val destination = MemoryDestination()
        val document = document("İstanbul – München".toByteArray(Charsets.UTF_8))

        val result = TransitExportStreamWriter(
            ioDispatcher = Dispatchers.Unconfined,
            chunkSize = 3
        ).write(document, destination)

        assertEquals(
            TransitExportWriteResult.Success(document.bytes.size, document.sha256),
            result
        )
        assertArrayEquals(document.bytes, destination.output.toByteArray())
        assertTrue(destination.output.flushed)
        assertFalse(destination.discarded)
    }

    @Test
    fun `write failure is reported and incomplete destination is discarded`() = runBlocking {
        val destination = FailingDestination()

        val result = TransitExportStreamWriter(
            ioDispatcher = Dispatchers.Unconfined,
            chunkSize = 2
        ).write(document(ByteArray(32) { 1 }), destination)

        assertTrue(result is TransitExportWriteResult.Failure)
        result as TransitExportWriteResult.Failure
        assertTrue(result.message.contains("disk full"))
        assertTrue(result.incompleteDestinationRemoved)
        assertTrue(destination.discarded)
    }

    @Test
    fun `provider refusing an output stream is not reported as success`() = runBlocking {
        val destination = NullDestination()

        val result = TransitExportStreamWriter(
            ioDispatcher = Dispatchers.Unconfined
        ).write(document(byteArrayOf(1, 2, 3)), destination)

        assertTrue(result is TransitExportWriteResult.Failure)
        assertTrue(destination.discarded)
    }

    @Test
    fun `cancelled write propagates cancellation and removes partial document`() = runBlocking {
        supervisorScope {
            lateinit var export: kotlinx.coroutines.Deferred<TransitExportWriteResult>
            val destination = CancellingDestination { export.cancel(CancellationException("user cancelled")) }
            val writer = TransitExportStreamWriter(
                ioDispatcher = Dispatchers.Unconfined,
                chunkSize = 4
            )
            export = async(start = CoroutineStart.LAZY) {
                writer.write(document(ByteArray(128) { 7 }), destination)
            }

            export.start()
            var cancelled = false
            try {
                export.await()
            } catch (_: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled)
            assertTrue(destination.discarded)
            assertTrue(destination.output.size() in 1 until 128)
        }
    }

    @Test
    fun `large prepared payload is copied completely in bounded chunks`() = runBlocking {
        val bytes = ByteArray(2 * 1024 * 1024) { index -> (index % 251).toByte() }
        val destination = MemoryDestination()

        val result = TransitExportStreamWriter(
            ioDispatcher = Dispatchers.Unconfined,
            chunkSize = 8 * 1024
        ).write(document(bytes), destination)

        assertTrue(result is TransitExportWriteResult.Success)
        assertArrayEquals(bytes, destination.output.toByteArray())
    }

    private fun document(bytes: ByteArray) = PreparedTransitExport(
        suggestedFileName = "transit.json",
        mimeType = "application/json",
        bytes = bytes,
        exportedRecordCount = 1,
        sha256 = "test-sha"
    )

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var flushed: Boolean = false

        override fun flush() {
            super.flush()
            flushed = true
        }
    }

    private class MemoryDestination : TransitExportDestination {
        val output = TrackingOutputStream()
        var discarded = false

        override fun openOutputStream(): OutputStream = output

        override fun discardIncomplete(): Boolean {
            discarded = true
            return true
        }
    }

    private class FailingDestination : TransitExportDestination {
        var discarded = false

        override fun openOutputStream(): OutputStream = object : OutputStream() {
            override fun write(value: Int) {
                throw IOException("disk full")
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                throw IOException("disk full")
            }
        }

        override fun discardIncomplete(): Boolean {
            discarded = true
            return true
        }
    }

    private class NullDestination : TransitExportDestination {
        var discarded = false

        override fun openOutputStream(): OutputStream? = null

        override fun discardIncomplete(): Boolean {
            discarded = true
            return true
        }
    }

    private class CancellingDestination(
        private val cancel: () -> Unit
    ) : TransitExportDestination {
        val output = ByteArrayOutputStream()
        var discarded = false
        private var cancelled = false

        override fun openOutputStream(): OutputStream = object : OutputStream() {
            override fun write(value: Int) {
                output.write(value)
                cancelOnce()
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                output.write(buffer, offset, length)
                cancelOnce()
            }

            private fun cancelOnce() {
                if (!cancelled) {
                    cancelled = true
                    cancel()
                }
            }
        }

        override fun discardIncomplete(): Boolean {
            discarded = true
            return true
        }
    }
}
