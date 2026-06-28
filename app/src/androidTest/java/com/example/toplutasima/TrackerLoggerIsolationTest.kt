package com.example.toplutasima

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.diagnostics.PersonalTripTrackerLogger
import com.example.toplutasima.diagnostics.RmvMesafeBackfillLogger
import com.example.toplutasima.diagnostics.TransitTrackerLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TrackerLoggerIsolationTest {
    private lateinit var root: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        val targetContext = TestDatabaseFactory.targetContext()
        root = File(targetContext.cacheDir, "tracker-logger-test-${System.nanoTime()}")
        root.mkdirs()
        context = object : ContextWrapper(targetContext) {
            override fun getFilesDir(): File = root
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun loggersListOnlyFilesMatchingTheirOwnPrefix() {
        val date = LocalDate.now().toString()
        TransitTrackerLogger.log(context, "TransitTest", "transit")
        PersonalTripTrackerLogger.log(context, "PersonalTest", "personal")
        RmvMesafeBackfillLogger.log(context, "BackfillTest", "backfill")

        assertEquals(
            listOf("tracker_log_$date.txt"),
            TransitTrackerLogger.getLogFiles(context).map { it.name }
        )
        assertEquals(
            listOf("personal_trip_log_$date.txt"),
            PersonalTripTrackerLogger.getLogFiles(context).map { it.name }
        )
        assertEquals(
            listOf("rmv_mesafe_backfill_log_$date.txt"),
            RmvMesafeBackfillLogger.getLogFiles(context).map { it.name }
        )
    }
}
