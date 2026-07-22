package com.example.toplutasima

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toplutasima.transit.export.TransitExportFormat
import com.example.toplutasima.transit.export.TransitExportPreparationResult
import com.example.toplutasima.transit.export.TransitExportRecord
import com.example.toplutasima.transit.export.TransitExportRequest
import com.example.toplutasima.transit.export.TransitExportScope
import com.example.toplutasima.transit.export.TransitExportUseCase
import com.example.toplutasima.transit.export.TransitExportWriteResult
import com.example.toplutasima.transit.export.TransitSafExportCoordinator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransitSafExportDeviceTest {

    @Test
    fun csvExportIsWrittenThroughContentResolver() = runBlocking {
        verifyDeviceExport(TransitExportFormat.CSV)
    }

    @Test
    fun jsonExportIsWrittenThroughContentResolver() = runBlocking {
        verifyDeviceExport(TransitExportFormat.JSON)
    }

    private suspend fun verifyDeviceExport(format: TransitExportFormat) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val document = (TransitExportUseCase(enabled = true).prepare(
            TransitExportRequest(
                format = format,
                scope = TransitExportScope.allTransit(),
                records = listOf(
                    TransitExportRecord(
                        localRecordId = "device-export-record",
                        dateIso = "2026-07-19",
                        line = "S8",
                        boardingStop = "Frankfurt Hbf",
                        alightingStop = "Offenbach Ost",
                        note = "Geräteprüfung – İstanbul"
                    )
                )
            )
        ) as TransitExportPreparationResult.Ready).document

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, document.suggestedFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, document.mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/TopluTasimaDeviceValidation"
            )
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        )

        try {
            val result = TransitSafExportCoordinator(context.contentResolver).write(uri, document)
            assertTrue(result is TransitExportWriteResult.Success)
            assertEquals(document.bytes.size, (result as TransitExportWriteResult.Success).bytesWritten)

            val persisted = requireNotNull(context.contentResolver.openInputStream(uri)).use {
                it.readBytes()
            }
            assertArrayEquals(document.bytes, persisted)

            val text = persisted.toString(Charsets.UTF_8)
            when (format) {
                TransitExportFormat.CSV -> assertTrue(text.startsWith("\uFEFFsection,"))
                TransitExportFormat.JSON -> assertTrue(
                    Json.parseToJsonElement(text).jsonObject.containsKey("metadata")
                )
            }
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }
}
