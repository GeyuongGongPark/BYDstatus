import Foundation
import WidgetKit

/// 메인 앱 → 위젯 데이터 공유 (App Group UserDefaults)
struct WidgetSnapshot: Codable {
    var batteryPercent: Int
    var isCharging: Bool
    var isDriving: Bool
    var drivingRangeKm: Double
    var instantPowerKw: Double   // 충전 중 kW
    var monthCostKrw: Double
    var lastUpdated: Date
}

enum WidgetDataStore {
    static let appGroupID = "group.com.ggpark.BydStats"
    static let snapshotKey = "widgetSnapshot"

    static func save(_ snapshot: WidgetSnapshot) {
        guard let defaults = UserDefaults(suiteName: appGroupID) else { return }
        if let data = try? JSONEncoder().encode(snapshot) {
            defaults.set(data, forKey: snapshotKey)
        }
        WidgetCenter.shared.reloadAllTimelines()
    }

    static func load() -> WidgetSnapshot? {
        guard let defaults = UserDefaults(suiteName: appGroupID),
              let data = defaults.data(forKey: snapshotKey) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }
}
