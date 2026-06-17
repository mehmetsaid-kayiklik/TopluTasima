package com.example.toplutasima.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TransitTrackerLogger {
    private const val TAG = "TransitTrackerLogger"
    private const val FILE_PREFIX = "tracker_log_"
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

    fun log(message: String) {
        log(TAG, message)
    }

    /**
     * Günün tarihini temel alarak yerel bir dosyaya log ekler.
     */
    fun log(context: Context, logTag: String, message: String) {
        try {
            val dateStr = LocalDate.now().toString() // yyyy-MM-dd
            val fileName = "$FILE_PREFIX$dateStr$FILE_SUFFIX"
            val logFile = File(context.filesDir, fileName)

            val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            logFile.appendText("[$timeStr] [$logTag] $message\n")
        } catch (e: Exception) {
            Log.w(TAG, "Log dosyaya yazılamadı: ${e.message}")
        }
    }

    /**
     * Belirtilen gün sınırından (varsayılan son 2 gün) eski olan tüm log dosyalarını temizler.
     */
    fun cleanOldLogs(context: Context, maxDaysToKeep: Int = 2) {
        try {
            val directory = context.filesDir
            val logFiles = directory.listFiles { _, name ->
                name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)
            } ?: return

            val thresholdDate = LocalDate.now().minusDays(maxDaysToKeep.toLong())

            for (file in logFiles) {
                val datePart = file.name
                    .removePrefix(FILE_PREFIX)
                    .removeSuffix(FILE_SUFFIX)

                try {
                    val fileDate = LocalDate.parse(datePart)
                    if (fileDate.isBefore(thresholdDate)) {
                        if (file.delete()) {
                            Log.d(TAG, "Eski log dosyası silindi: ${file.name}")
                        }
                    }
                } catch (pe: Exception) {
                    // Tarih formatı uyuşmayan veya hatalı adlandırılmış dosyaları da güvenlik için temizle
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Eski loglar temizlenirken hata oluştu: ${e.message}")
        }
    }

    /**
     * Mevcut tüm log dosyalarını tarih sırasına göre listeler (en yeni en başta).
     */
    fun getLogFiles(context: Context): List<File> {
        return try {
            val directory = context.filesDir
            val files = directory.listFiles { _, name ->
                name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)
            } ?: emptyArray()

            files.sortedByDescending { it.name }
        } catch (e: Exception) {
            Log.w(TAG, "Log dosyaları listelenemedi: ${e.message}")
            emptyList()
        }
    }

    /**
     * Belirli bir log dosyasının içeriğini okur.
     */
    fun readLogFile(file: File): String {
        return try {
            if (file.exists()) {
                file.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Log dosyası okunamadı: ${file.name}, ${e.message}")
            ""
        }
    }

    /**
     * Belirli bir log dosyasını siler.
     */
    fun deleteLogFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Log dosyası silinemedi: ${file.name}, ${e.message}")
            false
        }
    }
}
