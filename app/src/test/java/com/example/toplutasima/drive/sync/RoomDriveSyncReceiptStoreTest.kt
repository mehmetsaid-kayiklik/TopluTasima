package com.example.toplutasima.drive.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import com.example.toplutasima.drive.model.DriveSyncReceiptStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class RoomDriveSyncReceiptStoreTest {
    private lateinit var database: AppDatabase
    private lateinit var store: RoomDriveSyncReceiptStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        store = RoomDriveSyncReceiptStore(database.driveSyncReceiptDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `outbound retry and fatal receipts retain timing and attempt count`() = runBlocking {
        val operation = operation("operation")
        store.startOutbound(operation, 100L)
        store.retry(USER_ID, operation.operationId, 110L, "NETWORK")
        store.startOutbound(operation, 120L)
        store.fatal(USER_ID, operation.operationId, 130L, "PERMISSION_DENIED")

        val receipt = requireNotNull(database.driveSyncReceiptDao()
            .get(USER_ID, operation.operationId))
        assertEquals(100L, receipt.startedAt)
        assertEquals(130L, receipt.finishedAt)
        assertEquals(2, receipt.attemptCount)
        assertEquals(DriveSyncReceiptStatus.FATAL.name, receipt.status)
        assertEquals("PERMISSION_DENIED", receipt.errorCode)
    }

    @Test
    fun `receipt history is bounded per UID`() = runBlocking {
        repeat(105) { index ->
            val operation = operation("operation-$index")
            store.startOutbound(operation, index.toLong())
            store.succeed(USER_ID, operation.operationId, index.toLong() + 1L)
        }
        assertEquals(100, database.driveSyncReceiptDao().observeRecent(USER_ID, 200).first().size)
    }

    private fun operation(id: String) = DriveSyncOperationEntity(
        operationId = id,
        userId = USER_ID,
        entityType = DriveSyncEntityType.VEHICLE.name,
        recordId = "vehicle",
        operationType = DriveSyncOperationType.UPDATE_VEHICLE.name,
        createdAt = 100L,
        updatedAt = 100L
    )

    private companion object {
        const val USER_ID = "owner"
    }
}
