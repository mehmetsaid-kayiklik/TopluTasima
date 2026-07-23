package com.example.toplutasima.drive.photo

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DrivePhotoOperationEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.data.local.entity.DriveVehiclePhotoEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class OfflineFirstVehiclePhotoRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var database: AppDatabase
    private lateinit var ownerFlow: MutableStateFlow<String?>
    private lateinit var scheduler: RecordingScheduler
    private lateinit var fileStore: VehiclePhotoFileStore
    private var activeOwner: String? = OWNER

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.driveVehicleDao().upsert(vehicle())
        ownerFlow = MutableStateFlow(OWNER)
        scheduler = RecordingScheduler()
        fileStore = VehiclePhotoFileStore(context, Dispatchers.Unconfined)
        activeOwner = OWNER
    }

    @After
    fun tearDown() = runBlocking {
        database.close()
        fileStore.clearOutside(null)
    }

    @Test
    fun `add is room first creates durable upload and primary operations`() = runBlocking {
        val repository = repository(ids = listOf("photo-1", "upload-1", "primary-1"))

        val result = repository.add(VEHICLE, Uri.EMPTY)

        assertEquals(listOf("upload-1", "primary-1"), result.operationIds)
        val photo = database.driveVehiclePhotoDao().get(OWNER, "photo-1")
        assertEquals("PENDING_UPLOAD", photo?.uploadState)
        assertTrue(photo?.isPrimary == true)
        assertEquals("photo-1", database.driveVehicleDao().getVehicle(OWNER, VEHICLE)?.primaryPhotoId)
        assertEquals(2, database.drivePhotoOperationDao().pending(OWNER, 1_000, 10).size)
        assertEquals(listOf("upload-1", "primary-1"), scheduler.operationIds)
        assertEquals(listOf("photo-1"), repository.observePhotos(VEHICLE).first().map { it.photoId })
    }

    @Test
    fun `duplicate content is retained and surfaced as health without deduplication`() = runBlocking {
        repository(ids = listOf("photo-1", "upload-1", "primary-1")).add(VEHICLE, Uri.EMPTY)
        val second = repository(ids = listOf("photo-2", "upload-2")).add(VEHICLE, Uri.EMPTY)

        assertNotNull(second.photo)
        assertEquals("PHOTO_DUPLICATE_CONTENT", database.driveVehiclePhotoDao()
            .get(OWNER, "photo-2")?.healthCode)
        assertEquals(2, database.driveVehiclePhotoDao().getActiveForVehicle(OWNER, VEHICLE).size)
    }

    @Test
    fun `deleting primary keeps tombstone and selects deterministic replacement`() = runBlocking {
        repository(ids = listOf("photo-1", "upload-1", "primary-1")).add(VEHICLE, Uri.EMPTY)
        repository(ids = listOf("photo-2", "upload-2")).add(VEHICLE, Uri.EMPTY)
        val repository = repository(ids = listOf("delete-1", "replace-primary-1"), timestamp = 200)

        repository.delete(VEHICLE, "photo-1")

        assertEquals(200L, database.driveVehiclePhotoDao().get(OWNER, "photo-1")?.deletedAt)
        assertEquals("photo-2", database.driveVehicleDao().getVehicle(OWNER, VEHICLE)?.primaryPhotoId)
        assertTrue(database.driveVehiclePhotoDao().get(OWNER, "photo-2")?.isPrimary == true)
        assertEquals(listOf("delete-1", "replace-primary-1"), scheduler.operationIds.takeLast(2))
    }

    @Test
    fun `account change after preparation aborts without leaking row`() = runBlocking {
        val preparedFile = fileStore.preparedFile(OWNER, VEHICLE, "photo-account").apply {
            parentFile?.mkdirs()
            writeText("prepared")
        }
        val preparer = object : VehiclePhotoPreparer {
            override suspend fun prepare(
                ownerUid: String,
                vehicleId: String,
                photoId: String,
                source: Uri
            ): PreparedVehiclePhoto {
                activeOwner = "other-owner"
                ownerFlow.value = activeOwner
                return prepared(preparedFile)
            }
        }
        val repository = repository(
            ids = listOf("photo-account"),
            customPreparer = preparer
        )

        try {
            repository.add(VEHICLE, Uri.EMPTY)
            throw AssertionError("Account change must abort the mutation")
        } catch (_: VehiclePhotoFailure.AuthenticationChanged) {
            // Expected typed failure.
        }

        assertNull(database.driveVehiclePhotoDao().get(OWNER, "photo-account"))
        assertTrue(!preparedFile.exists())
    }

    private fun repository(
        ids: List<String>,
        timestamp: Long = 100,
        customPreparer: VehiclePhotoPreparer? = null
    ): OfflineFirstVehiclePhotoRepository {
        val queue = java.util.ArrayDeque(ids)
        val preparer = customPreparer ?: object : VehiclePhotoPreparer {
            override suspend fun prepare(
                ownerUid: String,
                vehicleId: String,
                photoId: String,
                source: Uri
            ): PreparedVehiclePhoto = prepared(fileStore.preparedFile(ownerUid, vehicleId, photoId))
        }
        return OfflineFirstVehiclePhotoRepository(
            database = database,
            currentUserId = { activeOwner },
            authenticatedUidChanges = ownerFlow,
            preparer = preparer,
            remote = FakeRemote,
            fileStore = fileStore,
            scheduler = scheduler,
            now = { timestamp },
            newId = { queue.removeFirst() },
            enabled = true
        )
    }

    private fun prepared(file: File) = PreparedVehiclePhoto(
        path = file.absolutePath,
        contentHash = "a".repeat(64),
        mimeType = "image/jpeg",
        width = 100,
        height = 80,
        sizeBytes = 10
    )

    private fun vehicle() = DriveVehicleEntity(
        id = VEHICLE,
        userId = OWNER,
        displayName = "Vehicle",
        createdAt = 1,
        updatedAt = 1,
        syncState = "SYNCED"
    )

    private class RecordingScheduler : VehiclePhotoSyncScheduler {
        val operationIds = mutableListOf<String>()

        override fun enqueue(ownerUid: String, operationId: String, photoId: String, vehicleId: String) {
            operationIds += operationId
        }

        override fun onAuthenticatedUserChanged(ownerUid: String?) = Unit
    }

    private object FakeRemote : VehiclePhotoRemoteDataSource {
        override suspend fun upload(
            ownerUid: String,
            photo: DriveVehiclePhotoEntity,
            operation: DrivePhotoOperationEntity
        ) = VehiclePhotoRemoteResult.Applied(null)

        override suspend fun updateMetadata(
            ownerUid: String,
            photo: DriveVehiclePhotoEntity,
            operation: DrivePhotoOperationEntity
        ) = VehiclePhotoRemoteResult.Applied(null)

        override suspend fun delete(
            ownerUid: String,
            photo: DriveVehiclePhotoEntity,
            operation: DrivePhotoOperationEntity
        ) = VehiclePhotoRemoteResult.Applied(null)

        override suspend fun setPrimary(
            ownerUid: String,
            operation: DrivePhotoOperationEntity
        ) = VehiclePhotoRemoteResult.Applied(null)

        override suspend fun downloadToCache(ownerUid: String, photo: DriveVehiclePhotoEntity): String = ""
    }

    companion object {
        private const val OWNER = "owner-a"
        private const val VEHICLE = "vehicle-a"
    }
}
