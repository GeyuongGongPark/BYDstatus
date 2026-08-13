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

    var isDriving: Bool { powerGear == 3 || speed > 0.0 }
    var isCharging: Bool { instantPowerW > 0 }           // gl 부호 검증 전 임시 기준
    var instantPowerKw: Double { instantPowerW / 1000.0 }
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
