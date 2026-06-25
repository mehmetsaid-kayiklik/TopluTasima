package com.example.toplutasima.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.network.firestore.FirestoreHelper
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

object AppErrorReporter {
    private const val TAG = "AppErrorReporter"
    private const val PREFS_NAME = "app_diagnostics"
    private const val KEY_LAST_CRASH = "last_crash"
    private const val KEY_LAST_NON_FATAL = "last_non_fatal"
    private const val LAST_CRASH_FILE_NAME = "last_crash.log"
    private const val MAX_PREF_REPORT_CHARS = 64_000
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = format("crash", thread.name, throwable)
                Log.e(TAG, report)
                saveCrash(appContext, report)
            } catch (reportingError: Throwable) {
                Log.e(TAG, "Crash report could not be persisted", reportingError)
            } finally {
                previousHandler?.uncaughtException(thread, throwable) ?: run {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }

    fun recordNonFatal(context: Context, source: String, throwable: Throwable) {
        save(context.applicationContext, KEY_LAST_NON_FATAL, format(source, Thread.currentThread().name, throwable))
    }

    fun lastCrash(context: Context): String =
        readCrashFile(context).ifBlank {
            prefs(context).getString(KEY_LAST_CRASH, "").orEmpty()
        }

    fun lastNonFatal(context: Context): String =
        prefs(context).getString(KEY_LAST_NON_FATAL, "").orEmpty()

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_LAST_CRASH)
            .remove(KEY_LAST_NON_FATAL)
            .apply()
        deleteCrashFile(context)
    }

    suspend fun uploadPendingCrashReport(context: Context): Boolean {
        val appContext = context.applicationContext
        val report = lastCrash(appContext)
        if (report.isBlank()) return false

        return try {
            FirestoreHelper.userRoot()
                .collection("crash_reports")
                .add(
                    mapOf(
                        "source" to "crash",
                        "report" to report,
                        "uploadedAt" to System.currentTimeMillis(),
                        "appVersionName" to BuildConfig.VERSION_NAME,
                        "appVersionCode" to BuildConfig.VERSION_CODE,
                        "deviceManufacturer" to Build.MANUFACTURER,
                        "deviceModel" to Build.MODEL,
                        "sdkInt" to Build.VERSION.SDK_INT
                    )
                )
                .await()
            clearCrash(appContext)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Pending crash report could not be uploaded", e)
            false
        }
    }

    private fun save(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value.take(MAX_PREF_REPORT_CHARS)).apply()
    }

    private fun saveCrash(context: Context, value: String) {
        crashFile(context).writeText(value)
        prefs(context).edit()
            .putString(KEY_LAST_CRASH, value.take(MAX_PREF_REPORT_CHARS))
            .commit()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun crashFile(context: Context): File =
        File(context.applicationContext.filesDir, LAST_CRASH_FILE_NAME)

    private fun readCrashFile(context: Context): String =
        try {
            val file = crashFile(context)
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            Log.w(TAG, "Crash report file could not be read", e)
            ""
        }

    private fun deleteCrashFile(context: Context) {
        try {
            val file = crashFile(context)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Crash report file could not be deleted", e)
        }
    }

    private fun clearCrash(context: Context) {
        prefs(context).edit().remove(KEY_LAST_CRASH).apply()
        deleteCrashFile(context)
    }

    private fun format(source: String, threadName: String, throwable: Throwable): String {
        val stack = throwable.stackTraceToString()
        return buildString {
            appendLine(
                "time=${Instant.now()} source=$source thread=$threadName " +
                    "app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                    "device=${Build.MANUFACTURER} ${Build.MODEL}, sdk=${Build.VERSION.SDK_INT}"
            )
            appendLine("exceptionClass=${throwable.javaClass.name}")
            appendLine("exceptionMessage=${throwable.message.orEmpty()}")
            appendLine("causeChain=${formatCauseChain(throwable)}")
            appendLine("stackTrace:")
            append(stack)
        }
    }

    private fun formatCauseChain(throwable: Throwable): String {
        val causes = mutableListOf<String>()
        val seen = mutableSetOf<Throwable>()
        var cause = throwable.cause
        while (cause != null && seen.add(cause)) {
            causes += "${cause.javaClass.name}: ${cause.message.orEmpty()}"
            cause = cause.cause
        }
        return causes.joinToString(" <- ").ifBlank { "none" }
    }
}
