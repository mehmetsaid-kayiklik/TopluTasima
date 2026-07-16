package com.example.toplutasima.data

import android.app.Application
import android.util.Log
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class AppEventBusTest {
    @Test
    fun `logs a warning when an event is emitted without an active collector`() {
        ShadowLog.clear()

        AppEventBus.emit(
            AppEventBus.Event.JourneyMatchCompleted(
                candidates = emptyList(),
                message = "Journey match completed"
            )
        )

        val warningWasLogged = ShadowLog.getLogsForTag("AppEventBus").any { logItem ->
            logItem.type == Log.WARN &&
                logItem.msg.contains("JourneyMatchCompleted") &&
                logItem.msg.contains("no active subscriber")
        }
        assertTrue(warningWasLogged)
    }
}
