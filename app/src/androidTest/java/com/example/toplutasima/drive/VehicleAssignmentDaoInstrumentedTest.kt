package com.example.toplutasima.drive

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveVehicleAssignmentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleAssignmentDaoInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun flowIsUidIsolatedAndUpsertKeepsSingleVehicleRow() = runBlocking {
        val dao = database.driveVehicleAssignmentDao()
        dao.upsert(assignment("uid-a", "person-a", 1, null))
        dao.upsert(assignment("uid-b", "person-b", 1, null))
        dao.upsert(assignment("uid-a", "person-c", 2, 20))

        val ownerA = dao.observeAll("uid-a").first()
        assertEquals(1, ownerA.size)
        assertEquals(2L, ownerA.single().revision)
        assertEquals(20L, ownerA.single().deletedAt)
        assertEquals("person-c", ownerA.single().personId)
        assertEquals("person-b", dao.get("uid-b", "vehicle-1")?.personId)
        assertNull(dao.get("uid-c", "vehicle-1"))
    }

    private fun assignment(
        uid: String,
        personId: String,
        revision: Long,
        deletedAt: Long?
    ) = DriveVehicleAssignmentEntity(
        ownerUid = uid,
        vehicleId = "vehicle-1",
        personId = personId,
        schemaVersion = 1,
        revision = revision,
        operationId = "$uid-operation-$revision",
        source = "TOPLU_TASIMA",
        clientUpdatedAt = revision,
        serverUpdatedAtSeconds = null,
        serverUpdatedAtNanos = null,
        deletedAt = deletedAt,
        syncState = if (deletedAt == null) "PENDING" else "SYNCED"
    )
}
