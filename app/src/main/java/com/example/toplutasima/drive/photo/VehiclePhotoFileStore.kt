package com.example.toplutasima.drive.photo

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import shared.vehiclephoto.contract.VehiclePhotoOpaqueId

class VehiclePhotoFileStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val filesRoot = File(context.applicationContext.filesDir, ROOT_NAME)
    private val cacheRoot = File(context.applicationContext.cacheDir, ROOT_NAME)

    fun preparedFile(ownerUid: String, vehicleId: String, photoId: String): File =
        scopedFile(filesRoot, ownerUid, vehicleId, "$photoId.jpg")

    fun cacheFile(
        ownerUid: String,
        vehicleId: String,
        photoId: String,
        contentHash: String
    ): File {
        require(contentHash.matches(Regex("^[a-f0-9]{64}$")))
        return scopedFile(cacheRoot, ownerUid, vehicleId, "$photoId-$contentHash.jpg")
    }

    fun cacheKey(ownerUid: String, vehicleId: String, photoId: String, contentHash: String?): String =
        listOf(ownerUid, vehicleId, photoId, contentHash.orEmpty()).joinToString(":")

    suspend fun deletePhoto(ownerUid: String, vehicleId: String, photoId: String) =
        withContext(ioDispatcher) {
            requireIds(ownerUid, vehicleId, photoId)
            preparedFile(ownerUid, vehicleId, photoId).delete()
            val cacheDirectory = scopedDirectory(cacheRoot, ownerUid, vehicleId)
            cacheDirectory.listFiles()
                ?.filter { it.isFile && it.name.startsWith("$photoId-") }
                ?.forEach(File::delete)
        }

    suspend fun clearOutside(activeOwnerUid: String?) = withContext(ioDispatcher) {
        activeOwnerUid?.let { require(VehiclePhotoOpaqueId.isValid(it)) }
        listOf(filesRoot, cacheRoot).forEach { root ->
            val canonicalRoot = root.canonicalFile
            canonicalRoot.mkdirs()
            canonicalRoot.listFiles()?.forEach { ownerDirectory ->
                if (activeOwnerUid == null || ownerDirectory.name != activeOwnerUid) {
                    check(ownerDirectory.canonicalPath.startsWith(canonicalRoot.canonicalPath + File.separator))
                    ownerDirectory.deleteRecursively()
                }
            }
        }
    }

    fun isScopedPath(path: String?, ownerUid: String, vehicleId: String): Boolean {
        if (path.isNullOrBlank() || !VehiclePhotoOpaqueId.isValid(ownerUid) ||
            !VehiclePhotoOpaqueId.isValid(vehicleId)
        ) return false
        return try {
            val candidate = File(path).canonicalFile
            val preparedDirectory = scopedDirectory(filesRoot, ownerUid, vehicleId).canonicalFile
            val cachedDirectory = scopedDirectory(cacheRoot, ownerUid, vehicleId).canonicalFile
            candidate.path.startsWith(preparedDirectory.path + File.separator) ||
                candidate.path.startsWith(cachedDirectory.path + File.separator)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private fun scopedFile(root: File, ownerUid: String, vehicleId: String, fileName: String): File {
        requireIds(ownerUid, vehicleId, fileName.substringBefore('.').substringBefore('-'))
        val directory = scopedDirectory(root, ownerUid, vehicleId)
        directory.mkdirs()
        val candidate = File(directory, fileName).canonicalFile
        check(candidate.path.startsWith(directory.canonicalPath + File.separator))
        return candidate
    }

    private fun scopedDirectory(root: File, ownerUid: String, vehicleId: String): File {
        require(VehiclePhotoOpaqueId.isValid(ownerUid) && VehiclePhotoOpaqueId.isValid(vehicleId))
        val canonicalRoot = root.canonicalFile
        val candidate = File(File(canonicalRoot, ownerUid), vehicleId).canonicalFile
        check(candidate.path.startsWith(canonicalRoot.path + File.separator))
        return candidate
    }

    private fun requireIds(ownerUid: String, vehicleId: String, photoId: String) {
        require(
            VehiclePhotoOpaqueId.isValid(ownerUid) &&
                VehiclePhotoOpaqueId.isValid(vehicleId) &&
                VehiclePhotoOpaqueId.isValid(photoId)
        )
    }

    internal companion object {
        const val ROOT_NAME = "vehicle_photos"

        fun stableScopeHash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }
}
