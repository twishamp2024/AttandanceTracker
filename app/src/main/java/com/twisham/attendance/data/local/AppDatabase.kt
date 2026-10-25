package com.twisham.attendance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.twisham.attendance.data.local.dao.MaterialisedDayDao
import com.twisham.attendance.data.local.dao.SemesterDao
import com.twisham.attendance.data.local.dao.SessionDao
import com.twisham.attendance.data.local.dao.SettingsDao
import com.twisham.attendance.data.local.dao.SubjectDao
import com.twisham.attendance.data.local.dao.TimetableDao
import com.twisham.attendance.data.local.entity.ClassSessionEntity
import com.twisham.attendance.data.local.entity.MaterialisedDayEntity
import com.twisham.attendance.data.local.entity.SemesterEntity
import com.twisham.attendance.data.local.entity.SettingsEntity
import com.twisham.attendance.data.local.entity.SubjectEntity
import com.twisham.attendance.data.local.entity.TimetableSlotEntity

/**
 * The single source of truth. No network layer exists anywhere in the app, so the
 * database is not a cache — losing it means losing the data, which is why there is
 * no `fallbackToDestructiveMigration` here. A future schema change must ship a real
 * migration; an app that silently wipes a semester of attendance is worse than one
 * that refuses to open.
 */
@Database(
    entities = [
        SemesterEntity::class,
        SubjectEntity::class,
        TimetableSlotEntity::class,
        ClassSessionEntity::class,
        SettingsEntity::class,
        MaterialisedDayEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun semesterDao(): SemesterDao
    abstract fun subjectDao(): SubjectDao
    abstract fun timetableDao(): TimetableDao
    abstract fun sessionDao(): SessionDao
    abstract fun materialisedDayDao(): MaterialisedDayDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "attendance.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Room enables this per-connection, but being explicit keeps
                        // the cascade deletes honest if the callback order changes.
                        db.execSQL("PRAGMA foreign_keys = ON;")
                    }
                })
                .build()
    }
}
