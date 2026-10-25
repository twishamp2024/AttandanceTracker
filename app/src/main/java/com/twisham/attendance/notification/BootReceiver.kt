package com.twisham.attendance.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.twisham.attendance.AttendanceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms do not survive a reboot or an app update, so re-arm them afterwards. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val container = (context.applicationContext as? AttendanceApp)?.container ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminderScheduler.apply(container.settingsRepository.get())
            } catch (_: Exception) {
                // Nothing useful to do; the reminder simply stays off until next change.
            } finally {
                pending.finish()
            }
        }
    }
}
