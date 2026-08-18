package com.ggpark.bydstats.android.service

import com.ggpark.bydstats.android.data.AppDatabase
import com.ggpark.bydstats.android.data.entity.*
import com.ggpark.bydstats.model.VehicleStatus

class SessionDetector(
    private val db: AppDatabase,
    private val electricityRate: Double,
    private val batteryCapacityKwh: Double,
) {
    private var activeCharging: ChargingSessionEntity? = null
    private var activeDriving: DrivingSessionEntity? = null

    suspend fun recover() {
        val now = System.currentTimeMillis()
        val oneHour = 3_600_000L

        db.chargingSessionDao().incomplete().forEach { session ->
            if (now - session.startTime < oneHour) {
                activeCharging = session
            } else {
                db.chargingSessionDao().update(session.copy(endTime = now))
            }
        }

        db.drivingSessionDao().incomplete().forEach { session ->
            if (now - session.startTime < oneHour) {
                activeDriving = session
            } else {
                db.drivingSessionDao().update(session.copy(endTime = now))
            }
        }
    }

    suspend fun process(status: VehicleStatus, timestamp: Long, gpsDistanceKm: Double = 0.0) {
        db.dataPointDao().insert(
            DataPointEntity(
                timestamp = timestamp,
                batteryPercent = status.batteryPercentage,
                isCharging = status.isCharging,
                isDriving = status.isDriving,
                chargingPowerKw = if (status.isCharging) status.instantPowerKw else null,
                hvacOn = status.isClimateOn,
            )
        )
        handleCharging(status, timestamp)
        handleDriving(status, timestamp, gpsDistanceKm)
    }

    private suspend fun handleCharging(status: VehicleStatus, timestamp: Long) {
        if (status.isCharging) {
            if (activeCharging == null) {
                val entity = ChargingSessionEntity(
                    startTime = timestamp,
                    endTime = null,
                    startSoc = status.batteryPercentage,
                    endSoc = status.batteryPercentage,
                    energyKwh = 0.0,
                    durationMinutes = 0,
                    estimatedCostKrw = 0.0,
                )
                val id = db.chargingSessionDao().insert(entity)
                activeCharging = entity.copy(id = id)
            } else {
                val updated = activeCharging!!.copy(endSoc = status.batteryPercentage)
                db.chargingSessionDao().update(updated)
                activeCharging = updated
            }
        } else {
            activeCharging?.let { session ->
                val socDelta = maxOf(0, session.endSoc - session.startSoc).toDouble()
                val energy = socDelta * batteryCapacityKwh / 100.0
                val duration = ((timestamp - session.startTime) / 60_000).toInt()
                val updated = session.copy(
                    endTime = timestamp,
                    energyKwh = energy,
                    durationMinutes = duration,
                    estimatedCostKrw = energy * electricityRate,
                )
                db.chargingSessionDao().update(updated)
                activeCharging = null
            }
        }
    }

    private suspend fun handleDriving(status: VehicleStatus, timestamp: Long, gpsDistanceKm: Double = 0.0) {
        if (status.isDriving) {
            if (activeDriving == null) {
                val entity = DrivingSessionEntity(
                    startTime = timestamp,
                    endTime = null,
                    startSoc = status.batteryPercentage,
                    endSoc = status.batteryPercentage,
                    energyKwh = 0.0,
                    distanceKm = null,
                    efficiencyKmPerKwh = null,
                    startOdometer = if (status.totalMileage > 0) status.totalMileage else null,
                    endOdometer = null,
                )
                val id = db.drivingSessionDao().insert(entity)
                activeDriving = entity.copy(id = id)
            } else {
                val updated = activeDriving!!.copy(endSoc = status.batteryPercentage)
                db.drivingSessionDao().update(updated)
                activeDriving = updated
            }
        } else {
            activeDriving?.let { session ->
                val duration = timestamp - session.startTime
                if (duration < 120_000) {
                    db.drivingSessionDao().delete(session)
                    activeDriving = null
                    return
                }

                val socDelta = maxOf(0, session.startSoc - session.endSoc).toDouble()
                val energy = socDelta * batteryCapacityKwh / 100.0

                val endOdo = if (status.totalMileage > 0) status.totalMileage else null
                var distKm: Double? = null
                var efficiency: Double? = null

                val startOdo = session.startOdometer
                if (startOdo != null && endOdo != null && endOdo > startOdo) {
                    distKm = endOdo - startOdo
                    if (energy > 0) efficiency = distKm!! / energy
                } else if (gpsDistanceKm > 0.1) {
                    distKm = gpsDistanceKm
                    if (energy > 0) efficiency = gpsDistanceKm / energy
                }

                val updated = session.copy(
                    endTime = timestamp,
                    energyKwh = energy,
                    distanceKm = distKm,
                    efficiencyKmPerKwh = efficiency,
                    endOdometer = endOdo,
                )
                db.drivingSessionDao().update(updated)
                activeDriving = null
            }
        }
    }

    fun hasActiveDrivingSession() = activeDriving != null
}
