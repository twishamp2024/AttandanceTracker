package com.twisham.attendance.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.twisham.attendance.AttendanceApp
import com.twisham.attendance.MainActivity
import com.twisham.attendance.R
import com.twisham.attendance.domain.model.AttendanceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Builds the "you have classes still unmarked" nudge.
 *
 * It fires only if there is actually something to mark, so the notification never
 * becomes background noise the user learns to swipe away. Everything runs inside
 * `goAsync()` so the database read is allowed to finish.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = (context.applicationContext as? AttendanceApp)?.container ?: run {
            pending.finish(); return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = container.settingsRepository.get()
                if (!settings.remindersEnabled) return@launch

                val semester = container.semesterRepository.getActive() ?: return@launch
                val today = container.today()
                if (!semester.contains(today)) return@launch

                container.sessionRepository.ensureMaterialised(semester, today, today)
                val unmarked = container.database.sessionDao()
                    .getForDay(semester.id, today.toEpochDay())
                    .count { it.status == AttendanceStatus.NOT_MARKED.name }

                if (unmarked > 0) notify(context, unmarked)
            } catch (_: Exception) {
                // A reminder that fails must never take the app down with it.
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, unmarked: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (unmarked == 1) {
            "1 class today is still unmarked."
        } else {
            "$unmarked classes today are still unmarked."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mark today's attendance")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Attendance reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "A daily nudge when classes are still unmarked."
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "attendance_reminders"
        private const val NOTIFICATION_ID = 2001
    }
}
