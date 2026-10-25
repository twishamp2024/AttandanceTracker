package com.twisham.attendance

import android.app.Application
import com.twisham.attendance.di.AppContainer

/**
 * Owns the dependency container for the process.
 *
 * Nothing heavy happens here: the database is built lazily on first use, so a cold
 * start reaches the first frame without touching disk. Reminder alarms are re-armed
 * off the main thread because they need a settings read.
 */
class AttendanceApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
