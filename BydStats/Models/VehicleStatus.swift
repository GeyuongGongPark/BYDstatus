import Foundation

struct VehicleStatus {
    var batteryPercentage: Int = 0
    var drivingRange: Double = 0.0
    var isLocked: Bool = false
    var isClimateOn: Bool = false
    var interiorTemperature: Double = 0.0
    var powerGear: Int = -1         // -1: 알 수 없음, 1: OFF, 3: ON
    var epb: Int = -1               // -1: 알 수 없음, 0: 해제, 1: 체결
    var speed: Double = 0.0
    var instantPowerW: Double = 0.0  // gl 필드 (W), 양수=충전, 음수=방전 추정 (실 검증 필요)
    var totalMileage: Double = 0.0   // 누적 주행거리 (km)
    /// withChargingResolved()로 설정되는 보정 충전 상태. nil이면 gl 기반 fallback 사용.
    var resolvedCharging: Bool? = nil
    /// withDrivingResolved()로 설정되는 보정 주행 상태. nil이면 computed 사용.
    var resolvedDriving: Bool? = nil

    var isDriving: Bool {
        if let resolved = resolvedDriving { return resolved }
        if speed > 0 { return true }          // 이동 중
        if instantPowerW > 0 { return false } // 정차 + 충전기 연결 (powerGear=3이어도 충전 중)
        return powerGear == 3                  // D단 신호 대기 등
    }
    /// gl > 0이거나 chargingState API/SOC 상승으로 보정된 충전 상태
    var isCharging: Bool { resolvedCharging ?? (instantPowerW > 0 && !isDriving) }
    var instantPowerKw: Double { instantPowerW / 1000.0 }

    // MARK: - 주행 상태 보정 (회생제동)
    //
    // 이전 폴링에서 주행 중이었고 현재 speed=0 + gl>0이면 회생제동으로 판단 → isDriving=true 유지.
    // withChargingResolved() 이전에 호출할 것.
    func withDrivingResolved(previous: VehicleStatus?) -> VehicleStatus {
        var copy = self
        let isRegenerativeBraking = (previous?.isDriving == true) && speed == 0 && instantPowerW > 0
        if isRegenerativeBraking { copy.resolvedDriving = true }
        return copy
    }

    // MARK: - 충전 상태 보정
    //
    // 우선순위:
    // 1. 주행 중 → 충전 아님
    // 2. gl > 0 → 충전
    // 3. chargingState API == true → 충전
    // 4. SOC 상승 → 충전 (API 지연 / 버그 대응)
    // 5. 이전 충전 중이었고 SOC 비하락 → 충전 유지
    //    (API false 포함: 완속 충전 시 API가 false를 반환하는 경우 대응)
    func withChargingResolved(previous: VehicleStatus?, apiIsCharging: Bool?) -> VehicleStatus {
        var copy = self
        copy.resolvedCharging = resolveIsCharging(
            isDriving: isDriving,
            instantPowerW: instantPowerW,
            batteryPercentage: batteryPercentage,
            previousCharging: previous?.isCharging ?? false,
            previousSoc: previous?.batteryPercentage,
            apiIsCharging: apiIsCharging
        )
        return copy
    }
}

private func resolveIsCharging(
    isDriving: Bool,
    instantPowerW: Double,
    batteryPercentage: Int,
    previousCharging: Bool,
    previousSoc: Int?,
    apiIsCharging: Bool?
) -> Bool {
    if isDriving { return false }
    if instantPowerW > 0 { return true }
    if apiIsCharging == true { return true }
    let socUp = previousSoc.map { batteryPercentage > $0 } ?? false
    if socUp { return true }
    if previousCharging, let prev = previousSoc, batteryPercentage >= prev { return true }
    return false
}

struct ChargingStatus {
    var isCharging: Bool = false
    var isConnected: Bool = false
    var batteryPercentage: Int = 0
    var remainingHours: Int = -1
    var remainingMinutes: Int = -1
    var chargeRate: Double = 0.0
}

struct HvacStatus {
    var isAcOn: Bool = false
    var interiorTemperature: Double = 0.0
    var exteriorTemperature: Double = 0.0
    var targetTemperature: Double = 22.0
    var windLevel: Int = 0
    var cycleMode: Int = 2
    var airConditioningMode: Int = 1
}

struct EnergyConsumptionData {
    var dailyConsumption: [DailyEnergyConsumption] = []
    var lifetimeAvgKwhPer100km: Double = 0.0
    var lifetimeMileageKm: Double = 0.0
    var recent50kmKwhPer100km: Double = 0.0
}

struct DailyEnergyConsumption: Identifiable {
    let id = UUID()
    var date: String         // "YYYY-MM-DD"
    var kwhPer100km: Double
}

struct VehicleListItem: Identifiable, Sendable {
    let id: String   // vin
    let vin: String
    let modelName: String
}
