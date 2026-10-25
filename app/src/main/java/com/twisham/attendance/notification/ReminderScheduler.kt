package com.twisham.attendance.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.twisham.attendance.domain.model.AppSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules the single optional daily reminder.
 *
 * Deliberately uses an **inexact** alarm. An exact alarm would require the
 * `SCHEDULE_EXACT_ALARM` permission and a user-facing grant, which is a lot of
 * friction for a nudge whose usefulness does not change if it arrives at 18:34
 * instead of 18:30. Inexact alarms also let the system batch it, costing no battery.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? get() = context.getSystemService()

    fun apply(settings: AppSettings) {
        if (settings.remindersEnabled) schedule(settings) else cancel()
    }

    fun schedule(settings: AppSettings) {
        val manager = alarmManager ?: return
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(LocalDate.now(), settings.reminderTime)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        runCatching {
            manager.setInexactRepeating(
                AlarmManager.RTC,
                triggerAt,
                AlarmManager.INTERVAL_DAY,
                pendingIntent(),
            )
        }
    }

    fun cancel() {
        runCatching { alarmManager?.cancel(pendingIntent()) }
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REMIND = "com.twisham.attendance.REMIND"
        private const val REQUEST_CODE = 1001
    }
}
