import Foundation
import SwiftData

@Model
final class DataPoint {
    var timestamp: Date
    var batteryPercent: Int
    var isCharging: Bool
    var isDriving: Bool
    var chargingPowerKw: Double?
    var hvacOn: Bool
    var drivingRangeKm: Double?

    init(timestamp: Date, batteryPercent: Int, isCharging: Bool, isDriving: Bool, chargingPowerKw: Double? = nil, hvacOn: Bool = false, drivingRangeKm: Double? = nil) {
        self.timestamp = timestamp
        self.batteryPercent = batteryPercent
        self.isCharging = isCharging
        self.isDriving = isDriving
        self.chargingPowerKw = chargingPowerKw
        self.hvacOn = hvacOn
        self.drivingRangeKm = drivingRangeKm
    }
}
