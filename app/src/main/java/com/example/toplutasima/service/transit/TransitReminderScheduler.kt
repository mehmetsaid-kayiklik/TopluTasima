package com.example.toplutasima.service.transit

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.toplutasima.data.PrefsManager
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TransitReminderScheduler(
    context: Context,
    private val onImmediateReminder: () -> Unit,
    private val logDebug: (String) -> Unit
) {
    data class ReminderState(
        val line: String,
        val alightingStop: String,
        val plannedArr: String,
        val vehicleType: String,
        val tripId: String
    )

    private val context = context.applicationContext

    @SuppressLint("MissingPermission", "ScheduleExactAlarm")
    fun schedule(state: ReminderState) {
        if (state.plannedArr.isBlank()) {
            Log.w(TAG, "Planlanan varis saati yok, hatirlatma kurulamiyor")
            return
        }

        try {
            val timeString = state.plannedArr.take(5)
            val paddedTime = if (timeString.contains(":") && timeString.length < 5) {
                timeString.padStart(5, '0')
            } else {
                timeString
            }
            val arrTime = LocalTime.parse(paddedTime, DateTimeFormatter.ofPattern("HH:mm"))
            val offsetMinutes = PrefsManager.reminderOffsetMinutes.toLong()
            val reminderTime = arrTime.plusMinutes(offsetMinutes)
            val zoneId = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zoneId)
            var targetZdt = now.with(reminderTime)

            if (targetZdt.isBefore(now)) {
                if (ChronoUnit.HOURS.between(targetZdt, now) > 12) {
                    targetZdt = targetZdt.plusDays(1)
                } else {
                    onImmediateReminder()
                    return
                }
            }

            val triggerAt = targetZdt.toInstant().toEpochMilli()
            val delayMs = triggerAt - now.toInstant().toEpochMilli()

            if (delayMs < 5_000L) {
                onImmediateReminder()
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = TransitActionIntents.reminderTriggerPendingIntent(
                context,
                TransitActionIntents.ReminderAlarmData(
                    line = state.line,
                    alightingStop = state.alightingStop,
                    plannedArr = state.plannedArr,
                    vehicleType = state.vehicleType,
                    tripId = state.tripId
                )
            )
            val canUseExactAlarm =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

            if (canUseExactAlarm) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                    logDebug("Hatirlatma kuruldu: ${delayMs / 1000}s sonra ($reminderTime)")
                } catch (e: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    Log.w(TAG, "Exact alarm guvenlik hatasi, inexact alarm kullaniliyor: ${e.message}")
                }
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                Log.w(TAG, "Exact alarm izni yok, inexact alarm kullaniliyor")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hatirlatma kurulamadi: ${e.message}")
        }
    }

    fun cancel() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(TransitActionIntents.reminderTriggerPendingIntent(context))
    }

    private companion object {
        const val TAG = "TransitReminderSched"
    }
}
