package com.example.toplutasima.ui.util

import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess

object CrashLogger {

    private const val LOG_FILE_NAME = "crash_log.jsonl"
    private const val PREFS_NAME = "crash_logger_prefs"
    private const val KEY_LAST_CHECKED_TIMESTAMP = "last_checked_exit_timestamp"

    fun init(context: Context) {
        val appContext = context.applicationContext

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                writeCrashEntry(
                    context = appContext,
                    exceptionClass = throwable.javaClass.name,
                    exceptionMessage = throwable.message ?: throwable.javaClass.simpleName,
                    stackTrace = throwable.stackTraceToString()
                )
            } catch (loggingError: Throwable) {
                // Crash kaydi basarisiz olsa bile normal crash kapanisini biz tamamlariz.
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
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
                    exceptionClass = "ProcessExit.$reasonName",
                    exceptionMessage = info.description ?: "aciklama yok",
                    stackTrace = ""
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

    private fun writeCrashEntry(
        context: Context,
        exceptionClass: String,
        exceptionMessage: String,
        stackTrace: String
    ) {
        val entry = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("exceptionClass", exceptionClass)
            put("exceptionMessage", exceptionMessage)
            put("stackTrace", stackTrace)
        }
        val outputStream = FileOutputStream(File(context.filesDir, LOG_FILE_NAME), true)
        try {
            outputStream.write((entry.toString() + "\n").toByteArray(Charsets.UTF_8))
        } finally {
            outputStream.flush()
            outputStream.close()
        }
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
                        timestamp = json.getLong("timestamp"),
                        exceptionClass = json.optString(
                            "exceptionClass",
                            json.optString("type", "Unknown")
                        ),
                        exceptionMessage = json.optString(
                            "exceptionMessage",
                            json.optString("message", "")
                        ),
                        stackTrace = json.optString("stackTrace")
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.timestamp }
    }

    fun clearLogs(context: Context) {
        val file = File(context.applicationContext.filesDir, LOG_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }
}

data class CrashLogEntry(
    val timestamp: Long,
    val exceptionClass: String,
    val exceptionMessage: String,
    val stackTrace: String
)
