package com.ggpark.bydstats.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_points")
data class DataPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isDriving: Boolean,
    val chargingPowerKw: Double?,
    val hvacOn: Boolean,
)

@Entity(tableName = "charging_sessions")
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val startSoc: Int,
    val endSoc: Int,
    val energyKwh: Double,
    val durationMinutes: Int,
    val estimatedCostKrw: Double,
)

@Entity(tableName = "driving_sessions")
data class DrivingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val startSoc: Int,
    val endSoc: Int,
    val energyKwh: Double,
    val distanceKm: Double?,
    val efficiencyKmPerKwh: Double?,
    val startOdometer: Double?,
    val endOdometer: Double?,
)
