package com.example.toplutasima.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object RmvMesafeBackfillLogger {
    private const val TAG = "RmvMesafeBackfillLogger"
    private const val FILE_PREFIX = "rmv_mesafe_backfill_log_"
    private const val FILE_SUFFIX = ".txt"
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun log(logTag: String, message: String) {
        val context = appContext
        if (context != null) {
            log(context, logTag, message)
        } else {
            Log.d(logTag, message)
        }
    }

    fun log(context: Context, logTag: String, message: String) {
        try {
            val dateStr = LocalDate.now().toString()
            val logFile = File(context.filesDir, "$FILE_PREFIX$dateStr$FILE_SUFFIX")
            val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            logFile.appendText("[$timeStr] [$logTag] $message\n")
        } catch (e: Exception) {
            Log.w(TAG, "Log dosyaya yazılamadı: ${e.message}")
        }
    }

    fun cleanOldLogs(context: Context, maxDaysToKeep: Int = 7) {
        try {
            val logFiles = context.filesDir.listFiles { _, name ->
                name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)
            } ?: return
            val thresholdDate = LocalDate.now().minusDays(maxDaysToKeep.toLong())

            for (file in logFiles) {
                val datePart = file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX)
                try {
                    if (LocalDate.parse(datePart).isBefore(thresholdDate)) {
                        file.delete()
                    }
                } catch (_: Exception) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Eski loglar temizlenirken hata oluştu: ${e.message}")
        }
    }

    fun getLogFiles(context: Context): List<File> {
        return try {
            val files = context.filesDir.listFiles { _, name ->
                name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)
            } ?: emptyArray()
            files.sortedByDescending { it.name }
        } catch (e: Exception) {
            Log.w(TAG, "Log dosyaları listelenemedi: ${e.message}")
            emptyList()
        }
    }

    fun readLogFile(file: File): String {
        return try {
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            Log.w(TAG, "Log dosyası okunamadı: ${file.name}, ${e.message}")
            ""
        }
    }

    fun deleteLogFile(file: File): Boolean {
        return try {
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            Log.w(TAG, "Log dosyası silinemedi: ${file.name}, ${e.message}")
            false
        }
    }
}
