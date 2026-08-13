import Foundation
import SwiftData

@Model
final class DrivingSession {
    var startTime: Date
    var endTime: Date?
    var startSoc: Int
    var endSoc: Int
    var energyKwh: Double           // 소비 에너지
    var distanceKm: Double?         // 종료 ODO - 시작 ODO
    var efficiencyKmPerKwh: Double? // distanceKm / energyKwh
    var startOdometer: Double?      // 주행 시작 시 총 주행거리(km)
    var endOdometer: Double?        // 주행 종료 시 총 주행거리(km)

    init(startTime: Date, startSoc: Int) {
        self.startTime = startTime
        self.startSoc = startSoc
        self.endSoc = startSoc
        self.energyKwh = 0
    }
}
