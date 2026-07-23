package com.example.toplutasima.drive.photo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class AndroidVehiclePhotoPreparerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val fileStore = VehiclePhotoFileStore(context, Dispatchers.Unconfined)

    @After
    fun clean() = runBlocking { fileStore.clearOutside(null) }

    @Test
    fun `jpeg png and webp are reencoded as metadata-free jpeg`() = runBlocking {
        listOf(
            "jpeg" to Bitmap.CompressFormat.JPEG,
            "png" to Bitmap.CompressFormat.PNG,
            "webp" to Bitmap.CompressFormat.WEBP
        ).forEachIndexed { index, (extension, format) ->
            val source = bitmapFile("input-$index.$extension", 120, 80, format)
            val prepared = preparer().prepare(OWNER, VEHICLE, "photo-$index", Uri.fromFile(source))
            val output = File(prepared.path)
            assertEquals("image/jpeg", prepared.mimeType)
            assertTrue(output.readBytes().take(3) == listOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))
            assertTrue(BitmapFactory.decodeFile(output.absolutePath) != null)
            assertTrue(output.length() in 1..(5L * 1_024L * 1_024L))
            assertEquals(sha256(output), prepared.contentHash)
        }
    }

    @Test
    fun `resize bounds long edge and sampling is oom defensive`() = runBlocking {
        val source = bitmapFile("large.png", 640, 320, Bitmap.CompressFormat.PNG)
        val prepared = preparer(maxEdge = 128).prepare(OWNER, VEHICLE, "photo-resize", Uri.fromFile(source))

        assertEquals(128, prepared.width)
        assertEquals(64, prepared.height)
        assertEquals(2, AndroidVehiclePhotoPreparer.calculateSampleSize(8192, 4096, 2048))
    }

    @Test
    fun `orientation is applied and sensitive exif is removed`() = runBlocking {
        val source = bitmapFile("oriented.jpg", 80, 40, Bitmap.CompressFormat.JPEG)
        ExifInterface(source.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "50/1,0/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_USER_COMMENT, "private")
            saveAttributes()
        }

        val prepared = preparer().prepare(OWNER, VEHICLE, "photo-oriented", Uri.fromFile(source))
        val exif = ExifInterface(prepared.path)
        val location = FloatArray(2)
        assertEquals(40, prepared.width)
        assertEquals(80, prepared.height)
        assertFalse(exif.getLatLong(location))
        assertEquals(null, exif.getAttribute(ExifInterface.TAG_USER_COMMENT))
        assertTrue(
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            ) in setOf(ExifInterface.ORIENTATION_UNDEFINED, ExifInterface.ORIENTATION_NORMAL)
        )
    }

    @Test(expected = VehiclePhotoFailure.UnsupportedPhotoType::class)
    fun `unsupported mime is rejected`(): Unit = runBlocking {
        val gif = File(context.cacheDir, "unsupported.gif").apply {
            writeBytes(java.util.Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=="))
        }
        preparer().prepare(OWNER, VEHICLE, "photo-gif", Uri.fromFile(gif))
    }

    @Test(expected = VehiclePhotoFailure.PhotoDecodeFailed::class)
    fun `corrupt source is rejected without a prepared file`(): Unit = runBlocking {
        val corrupt = bitmapFile("corrupt.jpg", 100, 100, Bitmap.CompressFormat.JPEG).apply {
            val original = readBytes()
            writeBytes(original.copyOf(original.size / 2))
        }
        try {
            preparer().prepare(OWNER, VEHICLE, "photo-corrupt", Uri.fromFile(corrupt))
        } finally {
            assertFalse(fileStore.preparedFile(OWNER, VEHICLE, "photo-corrupt").exists())
        }
    }

    private fun preparer(maxEdge: Int = 2048) = AndroidVehiclePhotoPreparer(
        context = context,
        fileStore = fileStore,
        ioDispatcher = Dispatchers.Unconfined,
        maximumLongEdge = maxEdge
    )

    private fun bitmapFile(name: String, width: Int, height: Int, format: Bitmap.CompressFormat): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(file).use { output -> assertTrue(bitmap.compress(format, 90, output)) }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8_192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val OWNER = "owner-test"
        private const val VEHICLE = "vehicle-test"
    }
}
