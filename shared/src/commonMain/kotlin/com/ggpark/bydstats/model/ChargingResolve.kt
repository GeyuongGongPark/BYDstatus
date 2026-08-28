package com.ggpark.bydstats.model

/**
 * 충전 여부 판정.
 *
 * 우선순위:
 * 1. 주행 중이면 충전 아님 (회생제동)
 * 2. gl(instantPowerW) > 0
 * 3. chargingState API == true
 * 4. API가 false여도 SOC가 올랐으면 1회 지연으로 충전 (API 지연)
 * 5. API 실패(null) 시: SOC 상승으로 시작, 이전 충전 + SOC 비하락으로 유지
 */
fun resolveIsCharging(
    isDriving: Boolean,
    instantPowerW: Double,
    batteryPercentage: Int,
    previousCharging: Boolean,
    previousSoc: Int?,
    apiIsCharging: Boolean?,
): Boolean {
    if (isDriving) return false
    if (instantPowerW > 0) return true
    if (apiIsCharging == true) return true

    val socUp = previousSoc != null && batteryPercentage > previousSoc
    if (apiIsCharging == false) return socUp

    if (socUp) return true
    if (previousCharging && previousSoc != null && batteryPercentage >= previousSoc) return true
    return false
}

fun VehicleStatus.withChargingResolved(
    previous: VehicleStatus?,
    apiIsCharging: Boolean?,
): VehicleStatus {
    val charging = resolveIsCharging(
        isDriving = isDriving,
        instantPowerW = instantPowerW,
        batteryPercentage = batteryPercentage,
        previousCharging = previous?.isCharging == true,
        previousSoc = previous?.batteryPercentage,
        apiIsCharging = apiIsCharging,
    )
    return copy(reportedCharging = charging)
}
