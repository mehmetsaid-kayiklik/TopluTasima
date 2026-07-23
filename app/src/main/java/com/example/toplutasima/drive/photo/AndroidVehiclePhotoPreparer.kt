package com.example.toplutasima.drive.photo

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import shared.vehiclephoto.contract.VehiclePhotoContractSpec

interface VehiclePhotoPreparer {
    suspend fun prepare(ownerUid: String, vehicleId: String, photoId: String, source: Uri): PreparedVehiclePhoto
}

class AndroidVehiclePhotoPreparer(
    context: Context,
    private val fileStore: VehiclePhotoFileStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maximumSourceBytes: Long = 40L * 1_024L * 1_024L,
    private val maximumLongEdge: Int = VehiclePhotoContractSpec.MAX_LONG_EDGE_PX,
    private val jpegQuality: Int = 85
) : VehiclePhotoPreparer {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    override suspend fun prepare(
        ownerUid: String,
        vehicleId: String,
        photoId: String,
        source: Uri
    ): PreparedVehiclePhoto = withContext(ioDispatcher) {
        coroutineContext.ensureActive()
        val declaredSize = sourceSize(source)
        if (declaredSize != null && declaredSize > maximumSourceBytes) {
            throw VehiclePhotoFailure.PhotoTooLarge()
        }
        val bounds = decodeBounds(source)
        val mimeType = resolver.getType(source)?.lowercase() ?: bounds.outMimeType?.lowercase()
        if (mimeType !in SUPPORTED_INPUT_TYPES) {
            throw VehiclePhotoFailure.UnsupportedPhotoType()
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw VehiclePhotoFailure.PhotoDecodeFailed()
        }
        val rawPixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (bounds.outWidth > MAX_SOURCE_EDGE || bounds.outHeight > MAX_SOURCE_EDGE ||
            rawPixels > MAX_SOURCE_PIXELS
        ) {
            throw VehiclePhotoFailure.PhotoTooLarge()
        }
        coroutineContext.ensureActive()
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maximumLongEdge)
        val sourceStream = try {
            resolver.openInputStream(source)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw VehiclePhotoFailure.PhotoSourceUnavailable()
        } ?: throw VehiclePhotoFailure.PhotoSourceUnavailable()
        val decoded = try {
            sourceStream.use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw VehiclePhotoFailure.PhotoDecodeFailed()
        } ?: throw VehiclePhotoFailure.PhotoDecodeFailed()
        val orientation = readOrientation(source)
        var transformed: Bitmap? = null
        var resized: Bitmap? = null
        val target = fileStore.preparedFile(ownerUid, vehicleId, photoId)
        val temporary = File(target.parentFile, ".${target.name}.preparing")
        try {
            coroutineContext.ensureActive()
            transformed = applyOrientation(decoded, orientation)
            resized = resize(transformed, maximumLongEdge)
            coroutineContext.ensureActive()
            encodeJpeg(resized, temporary)
            if (temporary.length() !in 1..VehiclePhotoContractSpec.MAX_PREPARED_BYTES) {
                temporary.delete()
                encodeJpeg(resized, temporary, quality = 72)
            }
            if (temporary.length() !in 1..VehiclePhotoContractSpec.MAX_PREPARED_BYTES) {
                throw VehiclePhotoFailure.PhotoTooLarge()
            }
            verifyMetadataRemoved(temporary)
            if (target.exists() && !target.delete()) throw VehiclePhotoFailure.PhotoPreparationFailed()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            PreparedVehiclePhoto(
                path = target.absolutePath,
                contentHash = sha256(target),
                mimeType = VehiclePhotoContractSpec.OUTPUT_MIME_TYPE,
                width = resized.width,
                height = resized.height,
                sizeBytes = target.length()
            )
        } catch (cancelled: CancellationException) {
            temporary.delete()
            target.delete()
            throw cancelled
        } catch (known: VehiclePhotoFailure) {
            temporary.delete()
            target.delete()
            throw known
        } catch (_: OutOfMemoryError) {
            temporary.delete()
            target.delete()
            throw VehiclePhotoFailure.PhotoDecodeFailed()
        } catch (_: Exception) {
            temporary.delete()
            target.delete()
            throw VehiclePhotoFailure.PhotoPreparationFailed()
        } finally {
            if (resized !== transformed && resized !== decoded) resized?.recycle()
            if (transformed !== decoded) transformed?.recycle()
            decoded.recycle()
        }
    }

    private fun sourceSize(uri: Uri): Long? = resolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }

    private fun decodeBounds(uri: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val sourceStream = try {
            resolver.openInputStream(uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw VehiclePhotoFailure.PhotoSourceUnavailable()
        } ?: throw VehiclePhotoFailure.PhotoSourceUnavailable()
        val decoded = try {
            sourceStream.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw VehiclePhotoFailure.PhotoDecodeFailed()
        }
        if (decoded != null) decoded.recycle()
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw VehiclePhotoFailure.PhotoDecodeFailed()
        }
        return options
    }

    private fun readOrientation(uri: Uri): Int = try {
        resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun resize(source: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = max(source.width, source.height)
        if (longEdge <= maxEdge) return source
        val scale = maxEdge.toDouble() / longEdge.toDouble()
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun encodeJpeg(bitmap: Bitmap, target: File, quality: Int = jpegQuality) {
        target.parentFile?.mkdirs()
        FileOutputStream(target, false).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                throw VehiclePhotoFailure.PhotoPreparationFailed()
            }
            output.fd.sync()
        }
    }

    private fun verifyMetadataRemoved(file: File) {
        val exif = ExifInterface(file.absolutePath)
        val latLong = FloatArray(2)
        if (exif.getLatLong(latLong) || SENSITIVE_EXIF_TAGS.any { exif.getAttribute(it) != null }) {
            throw VehiclePhotoFailure.PhotoPreparationFailed()
        }
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        if (orientation !in setOf(
                ExifInterface.ORIENTATION_UNDEFINED,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            throw VehiclePhotoFailure.PhotoPreparationFailed()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal companion object {
        val SUPPORTED_INPUT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        const val MAX_SOURCE_EDGE = 50_000
        const val MAX_SOURCE_PIXELS = 250_000_000L

        val SENSITIVE_EXIF_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_TIMESTAMP,
            "BodySerialNumber",
            "CameraOwnerName",
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME_ORIGINAL
        )

        fun calculateSampleSize(width: Int, height: Int, targetLongEdge: Int): Int {
            var sample = 1
            while (max(width / sample, height / sample) > targetLongEdge * 2 && sample <= 128) {
                sample *= 2
            }
            return sample
        }
    }
}
