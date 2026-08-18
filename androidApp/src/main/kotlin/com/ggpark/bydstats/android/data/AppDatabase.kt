package com.ggpark.bydstats.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ggpark.bydstats.android.data.dao.*
import com.ggpark.bydstats.android.data.entity.*

@Database(
    entities = [DataPointEntity::class, ChargingSessionEntity::class, DrivingSessionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dataPointDao(): DataPointDao
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun drivingSessionDao(): DrivingSessionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "bydstats.db",
            ).build().also { instance = it }
        }
    }
}
