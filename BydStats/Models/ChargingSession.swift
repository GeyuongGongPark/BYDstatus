import Foundation
import SwiftData

@Model
final class ChargingSession {
    var startTime: Date
    var endTime: Date?
    var startSoc: Int
    var endSoc: Int
    var energyKwh: Double       // (endSoc - startSoc) × 배터리용량 / 100
    var durationMinutes: Int
    var estimatedCostKrw: Double // energyKwh × 요금단가
    var latitude: Double?
    var longitude: Double?

    init(startTime: Date, startSoc: Int) {
        self.startTime = startTime
        self.startSoc = startSoc
        self.endSoc = startSoc
        self.energyKwh = 0
        self.durationMinutes = 0
        self.estimatedCostKrw = 0
    }
}
