package com.ggpark.bydstats.android.service

import com.ggpark.bydstats.android.data.AppDatabase
import com.ggpark.bydstats.android.data.entity.*
import com.ggpark.bydstats.model.VehicleStatus

class SessionDetector(
    private val db: AppDatabase,
    private val getElectricityRateAt: (Long) -> Double,
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
                drivingRangeKm = if (status.drivingRange > 0) status.drivingRange else null,
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
                val finalSoc = status.batteryPercentage
                val socDelta = maxOf(0, finalSoc - session.startSoc).toDouble()
                val energy = socDelta * batteryCapacityKwh / 100.0
                val duration = ((timestamp - session.startTime) / 60_000).toInt()
                // 충전 시작 시간 기준 시간대 요금 적용
                val rate = getElectricityRateAt(session.startTime)
                val updated = session.copy(
                    endTime = timestamp,
                    endSoc = finalSoc,
                    energyKwh = energy,
                    durationMinutes = duration,
                    estimatedCostKrw = energy * rate,
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

                val finalSoc = status.batteryPercentage
                val socDelta = maxOf(0, session.startSoc - finalSoc).toDouble()
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

                // 소비 에너지는 기록만, 비용 계산은 충전 세션에서 수행
                val updated = session.copy(
                    endTime = timestamp,
                    endSoc = finalSoc,
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
