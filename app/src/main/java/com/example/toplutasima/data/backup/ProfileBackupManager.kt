package com.example.toplutasima.data.backup

import android.content.Context
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.data.local.entity.TripProfileLinkEntity
import com.example.toplutasima.network.firestore.FirestorePersonService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Arrays

class ProfileBackupManager(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {

    private val profileDao = database.profileDao()
    private val linkDao = database.tripProfileLinkDao()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }

    data class ImportResult(
        val addedProfiles: Int = 0,
        val updatedProfiles: Int = 0,
        val addedLinks: Int = 0,
        val updatedLinks: Int = 0,
        val skippedLinks: Int = 0,
        val error: String? = null
    )

    /**
     * Exports profiles and links to an encrypted byte array.
     */
    suspend fun exportBackup(password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val profiles = profileDao.getAllProfiles().map { it.toBackupModel() }
        val links = linkDao.getAllLinks().map { it.toBackupModel() }

        val envelope = BackupEnvelope(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = getAppVersionName(),
            profiles = profiles,
            tripProfileLinks = links
        )

        val jsonString = json.encodeToString(BackupEnvelope.serializer(), envelope)
        val plaintextBytes = jsonString.toByteArray(Charsets.UTF_8)

        val encryptedBytes: ByteArray
        try {
            encryptedBytes = BackupEncryptor.encrypt(plaintextBytes, password)
        } finally {
            // Memory safety: wipe plaintext JSON bytes
            Arrays.fill(plaintextBytes, 0.toByte())
        }

        encryptedBytes
    }

    /**
     * Imports profiles and links from an encrypted byte array.
     */
    suspend fun importBackup(encryptedBytes: ByteArray, password: CharArray): ImportResult = withContext(Dispatchers.Default) {
        val plaintextBytes: ByteArray
        try {
            plaintextBytes = BackupEncryptor.decrypt(encryptedBytes, password)
        } catch (e: Exception) {
            return@withContext ImportResult(error = "Şifre yanlış veya yedek dosyası bozuk: ${e.localizedMessage}")
        }

        val envelope: BackupEnvelope
        try {
            val jsonString = String(plaintextBytes, Charsets.UTF_8)
            envelope = json.decodeFromString(BackupEnvelope.serializer(), jsonString)
        } catch (e: Exception) {
            return@withContext ImportResult(error = "Yedek dosyası ayrıştırılamadı: ${e.localizedMessage}")
        } finally {
            // Memory safety: wipe plaintext JSON bytes
            Arrays.fill(plaintextBytes, 0.toByte())
        }

        if (envelope.schemaVersion > CURRENT_SCHEMA_VERSION) {
            return@withContext ImportResult(error = "Desteklenmeyen yedek sürümü (Sürüm: ${envelope.schemaVersion})")
        }

        var addedProfiles = 0
        var updatedProfiles = 0
        var addedLinks = 0
        var updatedLinks = 0
        var skippedLinks = 0

        // 1. Profile merging
        val localProfilesMap = profileDao.getAllProfiles().associateBy { it.id }
        val profilesToUpsert = mutableListOf<ProfileEntity>()

        for (backupProfile in envelope.profiles) {
            val localProfile = localProfilesMap[backupProfile.id]
            if (localProfile == null) {
                profilesToUpsert.add(backupProfile.toEntity())
                addedProfiles++
            } else {
                if (backupProfile.updatedAt > localProfile.updatedAt) {
                    profilesToUpsert.add(backupProfile.toEntity())
                    updatedProfiles++
                }
            }
        }

        if (profilesToUpsert.isNotEmpty()) {
            profileDao.upsertAll(profilesToUpsert)
            profilesToUpsert.forEach { profile ->
                FirestorePersonService.upsertPerson(profile)
            }
        }

        // We need an updated set of all profile IDs currently present (local + newly imported)
        val allProfileIds = (localProfilesMap.keys + profilesToUpsert.map { it.id }).toSet()

        // 2. Link merging
        val localLinksMap = linkDao.getAllLinks().associateBy { it.id }
        val linksToUpsert = mutableListOf<TripProfileLinkEntity>()

        for (backupLink in envelope.tripProfileLinks) {
            // Orphan relationship check: profile must exist in local profiles or in the backup envelope
            if (backupLink.profileId !in allProfileIds) {
                skippedLinks++
                continue
            }

            val localLink = localLinksMap[backupLink.id]
            if (localLink == null) {
                linksToUpsert.add(backupLink.toEntity())
                addedLinks++
            } else {
                if (backupLink.updatedAt > localLink.updatedAt) {
                    linksToUpsert.add(backupLink.toEntity())
                    updatedLinks++
                }
            }
        }

        if (linksToUpsert.isNotEmpty()) {
            linkDao.upsertAll(linksToUpsert)
        }

        ImportResult(
            addedProfiles = addedProfiles,
            updatedProfiles = updatedProfiles,
            addedLinks = addedLinks,
            updatedLinks = updatedLinks,
            skippedLinks = skippedLinks
        )
    }

    /**
     * Deletes all local profiles and links.
     */
    suspend fun clearBackupData() = withContext(Dispatchers.IO) {
        linkDao.deleteAllLinks()
        profileDao.deleteAllProfiles()
    }

    private fun getAppVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
}
