package com.example.toplutasima.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.entity.DrivePhotoOperationEntity
import com.example.toplutasima.data.local.entity.DrivePhotoSyncReceiptEntity
import com.example.toplutasima.data.local.entity.DriveVehiclePhotoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class VehiclePhotoDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `photo flow is uid scoped ordered and keeps tombstones`() = runBlocking {
        val dao = database.driveVehiclePhotoDao()
        dao.upsert(photo(USER_A, "photo-b", sortOrder = 2))
        dao.upsert(photo(USER_A, "photo-a", sortOrder = 1))
        dao.upsert(photo(USER_B, "photo-a", sortOrder = 0))
        dao.upsert(photo(USER_A, "photo-deleted", sortOrder = 3, deletedAt = 9L))

        assertEquals(
            listOf("photo-a", "photo-b", "photo-deleted"),
            dao.observeForVehicle(USER_A, VEHICLE_ID).first().map { it.photoId }
        )
        assertEquals(listOf("photo-a"), dao.getAll(USER_B).map { it.photoId })
        assertEquals(9L, dao.get(USER_A, "photo-deleted")?.deletedAt)
        assertNull(dao.get(USER_B, "photo-b"))
    }

    @Test
    fun `primary projection keeps exactly one deterministic replacement`() = runBlocking {
        val dao = database.driveVehiclePhotoDao()
        dao.upsert(photo(USER_A, "photo-c", sortOrder = 2, createdAt = 3))
        dao.upsert(photo(USER_A, "photo-a", sortOrder = 0, createdAt = 2))
        dao.upsert(photo(USER_A, "photo-b", sortOrder = 0, createdAt = 1))

        dao.projectPrimary(USER_A, VEHICLE_ID, "photo-c", 10)
        assertEquals(listOf("photo-c"), dao.getActiveForVehicle(USER_A, VEHICLE_ID)
            .filter { it.isPrimary }.map { it.photoId })
        assertEquals(
            "photo-b",
            dao.deterministicReplacement(USER_A, VEHICLE_ID, "photo-c")?.photoId
        )
    }

    @Test
    fun `operation claim and receipt are idempotent across restart`() = runBlocking {
        val operations = database.drivePhotoOperationDao()
        val operation = operation()
        assertTrue(operations.insert(operation) >= 0)
        assertEquals(-1L, operations.insert(operation))
        assertEquals(1, operations.pending(USER_A, now = 1, limit = 10).size)
        assertEquals(1, operations.claim(USER_A, OPERATION_ID, now = 2, staleBefore = -1))
        assertEquals(0, operations.claim(USER_A, OPERATION_ID, now = 3, staleBefore = -1))

        val receipts = database.drivePhotoSyncReceiptDao()
        val receipt = receipt()
        assertTrue(receipts.insert(receipt) >= 0)
        assertEquals(-1L, receipts.insert(receipt))
        assertEquals("SUCCEEDED", receipts.getForOperation(USER_A, OPERATION_ID)?.status)
    }

    @Test
    fun `account cleanup never removes active uid rows`() = runBlocking {
        val dao = database.driveVehiclePhotoDao()
        dao.upsert(photo(USER_A, "photo-a"))
        dao.upsert(photo(USER_B, "photo-b"))

        dao.deleteAllExceptUser(USER_B)

        assertTrue(dao.getAll(USER_A).isEmpty())
        assertEquals(listOf("photo-b"), dao.getAll(USER_B).map { it.photoId })
    }

    private fun photo(
        uid: String,
        photoId: String,
        sortOrder: Int = 0,
        createdAt: Long = 1,
        deletedAt: Long? = null
    ) = DriveVehiclePhotoEntity(
        ownerUid = uid,
        photoId = photoId,
        vehicleId = VEHICLE_ID,
        localUri = null,
        localPreparedPath = null,
        storagePath = "users/$uid/vehicles/$VEHICLE_ID/photos/$photoId.jpg",
        contentHash = "a".repeat(64),
        mimeType = "image/jpeg",
        width = 100,
        height = 100,
        sizeBytes = 100,
        sortOrder = sortOrder,
        isPrimary = false,
        schemaVersion = 1,
        revision = 1,
        operationId = "op-$photoId",
        source = "TOPLU_TASIMA",
        clientUpdatedAt = createdAt,
        serverUpdatedAtSeconds = null,
        serverUpdatedAtNanos = null,
        deletedAt = deletedAt,
        uploadState = "SYNCED",
        remoteState = if (deletedAt == null) "PRESENT" else "TOMBSTONED",
        createdAt = createdAt,
        updatedAt = createdAt,
        lastErrorCode = null,
        healthCode = null
    )

    private fun operation() = DrivePhotoOperationEntity(
        ownerUid = USER_A,
        operationId = OPERATION_ID,
        photoId = "photo-a",
        vehicleId = VEHICLE_ID,
        type = "UPLOAD",
        targetRevision = 1,
        targetPrimaryPhotoId = null,
        expectedContentHash = "a".repeat(64),
        state = "PENDING",
        createdAt = 1,
        updatedAt = 1,
        attemptCount = 0,
        nextAttemptAt = 0,
        claimedAt = null,
        lastErrorCode = null
    )

    private fun receipt() = DrivePhotoSyncReceiptEntity(
        ownerUid = USER_A,
        receiptId = "receipt-a",
        operationId = OPERATION_ID,
        photoId = "photo-a",
        vehicleId = VEHICLE_ID,
        kind = "UPLOAD",
        status = "SUCCEEDED",
        provenance = "TOPLU_TASIMA",
        revision = 1,
        winningOperationId = OPERATION_ID,
        attemptCount = 1,
        createdAt = 1,
        finishedAt = 2,
        errorCode = null
    )

    companion object {
        private const val USER_A = "uid-a"
        private const val USER_B = "uid-b"
        private const val VEHICLE_ID = "vehicle-1"
        private const val OPERATION_ID = "operation-a"
    }
}
