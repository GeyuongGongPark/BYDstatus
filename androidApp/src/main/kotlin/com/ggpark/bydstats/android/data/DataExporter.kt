package com.ggpark.bydstats.android.data

import com.ggpark.bydstats.android.data.entity.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// MARK: - 공통 JSON 스키마 (iOS 호환)

@Serializable
data class ExportPayload(
    val version: Int = 1,
    val exportedAt: Long,
    val dataPoints: List<ExDataPoint>,
    val chargingSessions: List<ExChargingSession>,
    val drivingSessions: List<ExDrivingSession>,
)

@Serializable
data class ExDataPoint(
    val timestamp: Long,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isDriving: Boolean,
    val chargingPowerKw: Double?,
    val hvacOn: Boolean,
    val drivingRangeKm: Double?,
)

@Serializable
data class ExChargingSession(
    val startTime: Long,
    val endTime: Long?,
    val startSoc: Int,
    val endSoc: Int,
    val energyKwh: Double,
    val durationMinutes: Int,
    val estimatedCostKrw: Double,
    val latitude: Double? = null,   // iOS 호환 필드
    val longitude: Double? = null,
)

@Serializable
data class ExDrivingSession(
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

data class ImportResult(val dataPoints: Int, val chargingSessions: Int, val drivingSessions: Int)

private val exportJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

object DataExporter {

    suspend fun export(db: AppDatabase): String {
        val payload = ExportPayload(
            exportedAt = System.currentTimeMillis(),
            dataPoints = db.dataPointDao().getAll().map {
                ExDataPoint(it.timestamp, it.batteryPercent, it.isCharging, it.isDriving, it.chargingPowerKw, it.hvacOn, it.drivingRangeKm)
            },
            chargingSessions = db.chargingSessionDao().getAll().map {
                ExChargingSession(it.startTime, it.endTime, it.startSoc, it.endSoc, it.energyKwh, it.durationMinutes, it.estimatedCostKrw)
            },
            drivingSessions = db.drivingSessionDao().getAll().map {
                ExDrivingSession(it.startTime, it.endTime, it.startSoc, it.endSoc, it.energyKwh, it.distanceKm, it.efficiencyKmPerKwh, it.startOdometer, it.endOdometer)
            },
        )
        return exportJson.encodeToString(payload)
    }

    suspend fun importData(jsonStr: String, db: AppDatabase): ImportResult {
        val payload = exportJson.decodeFromString<ExportPayload>(jsonStr)

        val existingDP = db.dataPointDao().getAll().map { it.timestamp }.toHashSet()
        val existingCS = db.chargingSessionDao().getAll().map { it.startTime }.toHashSet()
        val existingDS = db.drivingSessionDao().getAll().map { it.startTime }.toHashSet()

        var dpCount = 0; var csCount = 0; var dsCount = 0

        for (dp in payload.dataPoints) {
            if (dp.timestamp in existingDP) continue
            db.dataPointDao().insert(DataPointEntity(
                timestamp      = dp.timestamp,
                batteryPercent = dp.batteryPercent,
                isCharging     = dp.isCharging,
                isDriving      = dp.isDriving,
                chargingPowerKw = dp.chargingPowerKw,
                hvacOn         = dp.hvacOn,
                drivingRangeKm = dp.drivingRangeKm,
            ))
            dpCount++
        }

        for (cs in payload.chargingSessions) {
            if (cs.startTime in existingCS) continue
            db.chargingSessionDao().insert(ChargingSessionEntity(
                startTime        = cs.startTime,
                endTime          = cs.endTime,
                startSoc         = cs.startSoc,
                endSoc           = cs.endSoc,
                energyKwh        = cs.energyKwh,
                durationMinutes  = cs.durationMinutes,
                estimatedCostKrw = cs.estimatedCostKrw,
            ))
            csCount++
        }

        for (ds in payload.drivingSessions) {
            if (ds.startTime in existingDS) continue
            db.drivingSessionDao().insert(DrivingSessionEntity(
                startTime          = ds.startTime,
                endTime            = ds.endTime,
                startSoc           = ds.startSoc,
                endSoc             = ds.endSoc,
                energyKwh          = ds.energyKwh,
                distanceKm         = ds.distanceKm,
                efficiencyKmPerKwh = ds.efficiencyKmPerKwh,
                startOdometer      = ds.startOdometer,
                endOdometer        = ds.endOdometer,
            ))
            dsCount++
        }

        return ImportResult(dpCount, csCount, dsCount)
    }
}
