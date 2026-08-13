import Foundation
import SwiftData

@Model
final class DrivingSession {
    var startTime: Date
    var endTime: Date?
    var startSoc: Int
    var endSoc: Int
    var energyKwh: Double           // 소비 에너지
    var distanceKm: Double?         // GPS or 수동 입력
    var efficiencyKmPerKwh: Double? // distanceKm / energyKwh

    init(startTime: Date, startSoc: Int) {
        self.startTime = startTime
        self.startSoc = startSoc
        self.endSoc = startSoc
        self.energyKwh = 0
    }
}
