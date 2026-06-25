package com.example.toplutasima.ui.util

import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File

object CrashLogger {

    private const val LOG_FILE_NAME = "crash_log.jsonl"
    private const val PREFS_NAME = "crash_logger_prefs"
    private const val KEY_LAST_CHECKED_TIMESTAMP = "last_checked_exit_timestamp"

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashEntry(
                    context = appContext,
                    type = "CRASH",
                    message = throwable.message ?: throwable.javaClass.simpleName,
                    stackTrace = throwable.stackTraceToString()
                )
            } catch (loggingError: Throwable) {
                // loglama basarisiz olsa da orijinal handler'i mutlaka cagir
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun checkExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val appContext = context.applicationContext
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastChecked = prefs.getLong(KEY_LAST_CHECKED_TIMESTAMP, 0L)

        val exitReasons = activityManager.getHistoricalProcessExitReasons(null, 0, 5)
        var newestTimestamp = lastChecked

        for (info in exitReasons) {
            if (info.timestamp <= lastChecked) continue

            exitReasonToString(info.reason)?.let { reasonName ->
                writeCrashEntry(
                    context = appContext,
                    type = "OS_KILL",
                    message = "$reasonName: ${info.description ?: "açıklama yok"}",
                    stackTrace = null
                )
            }
            if (info.timestamp > newestTimestamp) newestTimestamp = info.timestamp
        }

        if (newestTimestamp > lastChecked) {
            prefs.edit().putLong(KEY_LAST_CHECKED_TIMESTAMP, newestTimestamp).apply()
        }
    }

    private fun exitReasonToString(reason: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH (yakalanmamış exception)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY_KILL"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        else -> null
    }

    private fun writeCrashEntry(context: Context, type: String, message: String, stackTrace: String?) {
        val entry = JSONObject().apply {
            put("type", type)
            put("timestamp", System.currentTimeMillis())
            put("message", message)
            put("stackTrace", stackTrace ?: "")
        }
        File(context.filesDir, LOG_FILE_NAME).appendText(entry.toString() + "\n")
    }

    fun readLogs(context: Context): List<CrashLogEntry> {
        val file = File(context.applicationContext.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return emptyList()

        return file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    val json = JSONObject(line)
                    CrashLogEntry(
                        type = json.getString("type"),
                        timestamp = json.getLong("timestamp"),
                        message = json.getString("message"),
                        stackTrace = json.optString("stackTrace").takeIf { it.isNotBlank() }
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.timestamp }
    }
}

data class CrashLogEntry(
    val type: String,
    val timestamp: Long,
    val message: String,
    val stackTrace: String?
)
