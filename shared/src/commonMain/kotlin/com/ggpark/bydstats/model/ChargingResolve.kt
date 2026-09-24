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

/**
 * 회생제동 판별: 이전 폴링에서 주행 중이었고 현재 speed=0 + gl>0이면
 * 충전기 연결이 아닌 회생제동으로 판단 → isDriving=true 유지.
 * withChargingResolved() 이전에 호출할 것.
 */
fun VehicleStatus.withDrivingResolved(previous: VehicleStatus?): VehicleStatus {
    val isRegenerativeBraking = previous?.isDriving == true &&
        speed == 0.0 &&
        instantPowerW > 0
    return if (isRegenerativeBraking) copy(reportedDriving = true) else this
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
