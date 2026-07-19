package com.example.toplutasima.transit.export

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.OutputStream

/** Values passed by Compose to ActivityResultContracts.CreateDocument. */
data class TransitSafCreateDocumentRequest(
    val mimeType: String,
    val suggestedFileName: String
)

sealed interface TransitExportWriteResult {
    data class Success(
        val bytesWritten: Int,
        val sha256: String
    ) : TransitExportWriteResult

    data class Failure(
        val message: String,
        /** False means the document provider refused best-effort cleanup of a partial target. */
        val incompleteDestinationRemoved: Boolean
    ) : TransitExportWriteResult
}

/**
 * Stream boundary kept separate from Android so write errors and cancellation can be tested
 * without a document provider.
 */
interface TransitExportDestination {
    fun openOutputStream(): OutputStream?
    fun discardIncomplete(): Boolean
}

class TransitExportStreamWriter(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
    }

    suspend fun write(
        document: PreparedTransitExport,
        destination: TransitExportDestination
    ): TransitExportWriteResult = withContext(ioDispatcher) {
        try {
            currentCoroutineContext().ensureActive()
            val output = destination.openOutputStream()
                ?: return@withContext TransitExportWriteResult.Failure(
                    message = "The document provider did not open the destination",
                    incompleteDestinationRemoved = destination.discardIncomplete()
                )

            output.use { stream ->
                var offset = 0
                while (offset < document.bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(chunkSize, document.bytes.size - offset)
                    stream.write(document.bytes, offset, count)
                    offset += count
                }
                currentCoroutineContext().ensureActive()
                stream.flush()
            }
            TransitExportWriteResult.Success(
                bytesWritten = document.bytes.size,
                sha256 = document.sha256
            )
        } catch (cancelled: CancellationException) {
            destination.discardIncomplete()
            throw cancelled
        } catch (error: Exception) {
            TransitExportWriteResult.Failure(
                message = error.message ?: error::class.java.simpleName,
                incompleteDestinationRemoved = destination.discardIncomplete()
            )
        }
    }

    private companion object {
        const val DEFAULT_CHUNK_SIZE = 16 * 1024
    }
}

/**
 * Android Storage Access Framework adapter. The entire payload is prepared before this class is
 * called. A provider write is not transactionally atomic, so failures and cancellation trigger a
 * best-effort delete instead of reporting a partial file as successful.
 */
class TransitSafExportCoordinator(
    private val contentResolver: ContentResolver,
    private val writer: TransitExportStreamWriter = TransitExportStreamWriter()
) {
    fun createDocumentRequest(document: PreparedTransitExport): TransitSafCreateDocumentRequest =
        TransitSafCreateDocumentRequest(
            mimeType = document.mimeType,
            suggestedFileName = document.suggestedFileName
        )

    suspend fun write(
        uri: Uri,
        document: PreparedTransitExport
    ): TransitExportWriteResult = writer.write(
        document = document,
        destination = SafDestination(contentResolver, uri)
    )

    private class SafDestination(
        private val contentResolver: ContentResolver,
        private val uri: Uri
    ) : TransitExportDestination {
        override fun openOutputStream(): OutputStream? = try {
            contentResolver.openOutputStream(uri, "rwt")
        } catch (_: FileNotFoundException) {
            contentResolver.openOutputStream(uri, "w")
        }

        override fun discardIncomplete(): Boolean = try {
            contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }
}
