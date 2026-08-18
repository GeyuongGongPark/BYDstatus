package com.ggpark.bydstats.model

data class VehicleStatus(
    val batteryPercentage: Int = 0,
    val drivingRange: Double = 0.0,
    val isLocked: Boolean = false,
    val isClimateOn: Boolean = false,
    val interiorTemperature: Double = 0.0,
    val powerGear: Int = -1,       // -1: unknown, 1: OFF, 3: ON
    val epb: Int = -1,             // -1: unknown, 0: released, 1: engaged
    val speed: Double = 0.0,
    val instantPowerW: Double = 0.0,
    val totalMileage: Double = 0.0,
) {
    val isDriving: Boolean get() = powerGear == 3 || speed > 0.0
    val isCharging: Boolean get() = instantPowerW > 0 && !isDriving
    val instantPowerKw: Double get() = instantPowerW / 1000.0
}

data class ChargingStatus(
    val isCharging: Boolean = false,
    val isConnected: Boolean = false,
    val batteryPercentage: Int = 0,
    val remainingHours: Int = -1,
    val remainingMinutes: Int = -1,
    val chargeRate: Double = 0.0,
)

data class HvacStatus(
    val isAcOn: Boolean = false,
    val interiorTemperature: Double = 0.0,
    val exteriorTemperature: Double = 0.0,
    val targetTemperature: Double = 22.0,
    val windLevel: Int = 0,
    val cycleMode: Int = 2,
    val airConditioningMode: Int = 1,
)

data class EnergyConsumptionData(
    val dailyConsumption: List<DailyEnergyConsumption> = emptyList(),
    val lifetimeAvgKwhPer100km: Double = 0.0,
    val lifetimeMileageKm: Double = 0.0,
    val recent50kmKwhPer100km: Double = 0.0,
)

data class DailyEnergyConsumption(
    val date: String,          // "YYYY-MM-DD"
    val kwhPer100km: Double,
)

data class VehicleListItem(
    val vin: String,
    val modelName: String,
)

// Session models (stored in Room on Android)
data class ChargingSessionData(
    val id: Long = 0,
    val startTime: Long = 0L,     // epoch millis
    val endTime: Long? = null,
    val startSoc: Int = 0,
    val endSoc: Int = 0,
    val energyKwh: Double = 0.0,
    val durationMinutes: Int = 0,
    val estimatedCostKrw: Double = 0.0,
)

data class DrivingSessionData(
    val id: Long = 0,
    val startTime: Long = 0L,     // epoch millis
    val endTime: Long? = null,
    val startSoc: Int = 0,
    val endSoc: Int = 0,
    val energyKwh: Double = 0.0,
    val distanceKm: Double? = null,
    val efficiencyKmPerKwh: Double? = null,
    val startOdometer: Double? = null,
    val endOdometer: Double? = null,
)

data class DataPointData(
    val id: Long = 0,
    val timestamp: Long = 0L,
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val isDriving: Boolean = false,
    val chargingPowerKw: Double? = null,
    val hvacOn: Boolean = false,
)
