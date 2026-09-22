package com.ggpark.bydstats.model

/**
 * 충전 여부 판정.
 *
 * 우선순위:
 * 1. 주행 중이면 충전 아님 (회생제동)
 * 2. gl(instantPowerW) > 0 → 충전
 * 3. chargingState API == true → 충전
 * 4. SOC 상승 → 충전 (API 지연 / 버그 대응)
 * 5. 이전 충전 중이었고 SOC 비하락 → 충전 유지
 *    (API false 포함: 완속 충전 시 API가 false를 반환하는 경우 대응)
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
