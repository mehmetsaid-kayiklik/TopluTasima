package com.example.toplutasima.diagnostics

import android.content.Context
import android.os.Build
import com.example.toplutasima.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import kotlin.system.exitProcess

object AppErrorReporter {
    private const val PREFS_NAME = "app_diagnostics"
    private const val KEY_LAST_CRASH = "last_crash"
    private const val KEY_LAST_NON_FATAL = "last_non_fatal"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            save(appContext, KEY_LAST_CRASH, format("crash", thread.name, throwable))
            previousHandler?.uncaughtException(thread, throwable) ?: run {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun recordNonFatal(context: Context, source: String, throwable: Throwable) {
        save(context.applicationContext, KEY_LAST_NON_FATAL, format(source, Thread.currentThread().name, throwable))
    }

    fun lastCrash(context: Context): String =
        prefs(context).getString(KEY_LAST_CRASH, "").orEmpty()

    fun lastNonFatal(context: Context): String =
        prefs(context).getString(KEY_LAST_NON_FATAL, "").orEmpty()

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_LAST_CRASH)
            .remove(KEY_LAST_NON_FATAL)
            .apply()
    }

    private fun save(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value.take(4_000)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun format(source: String, threadName: String, throwable: Throwable): String {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("time=${Instant.now()}")
            appendLine("source=$source")
            appendLine("thread=$threadName")
            appendLine("app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}, sdk=${Build.VERSION.SDK_INT}")
            appendLine("error=${throwable::class.java.name}: ${throwable.message.orEmpty()}")
            append(stack)
        }
    }
}
