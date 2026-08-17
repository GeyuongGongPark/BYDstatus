import ActivityKit
import Foundation

struct BydLiveActivityAttributes: ActivityAttributes {

    // MARK: - 동적 상태 (폴링마다 업데이트)

    struct ContentState: Codable, Hashable {
        var batteryPercent: Int
        var instantPowerKw: Double   // 충전 중: 충전 kW, 주행 중: 소비 kW (abs)
        var drivingRangeKm: Double
    }

    // MARK: - 정적 속성 (세션 시작 시 고정)

    enum SessionType: String, Codable, Hashable {
        case driving, charging
    }

    var sessionType: SessionType
    var startSoc: Int
    var sessionStartDate: Date
}
