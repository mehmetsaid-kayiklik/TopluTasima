package com.example.toplutasima.ui.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class CrashLoggerTest {

    private lateinit var context: Context
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CrashLogger.clearLogs(context)
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        CrashLogger.clearLogs(context)
    }

    @Test
    fun `custom logging completes before previous handler is invoked`() {
        val crashingThread = Thread.currentThread()
        val throwable = IllegalStateException("test crash")
        var delegated = false

        Thread.setDefaultUncaughtExceptionHandler { thread, delegatedThrowable ->
            val logs = CrashLogger.readLogs(context)
            assertEquals(1, logs.size)
            assertEquals(IllegalStateException::class.java.name, logs.single().exceptionClass)
            assertEquals("test crash", logs.single().exceptionMessage)
            assertSame(crashingThread, thread)
            assertSame(throwable, delegatedThrowable)
            delegated = true
        }

        CrashLogger.init(context)
        val customHandler = Thread.getDefaultUncaughtExceptionHandler()

        customHandler?.uncaughtException(crashingThread, throwable)

        assertTrue("The previous uncaught-exception handler was not invoked", delegated)
    }
}
