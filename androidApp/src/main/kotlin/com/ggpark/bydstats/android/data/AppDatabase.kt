package com.ggpark.bydstats.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ggpark.bydstats.android.data.dao.*
import com.ggpark.bydstats.android.data.entity.*

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE data_points ADD COLUMN drivingRangeKm REAL")
    }
}

@Database(
    entities = [DataPointEntity::class, ChargingSessionEntity::class, DrivingSessionEntity::class],
    version = 2,
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
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
