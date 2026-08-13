import WidgetKit
import SwiftUI

// MARK: - 공유 데이터 (메인 앱의 WidgetDataStore 복사)

struct WidgetSnapshot: Codable {
    var batteryPercent: Int
    var isCharging: Bool
    var isDriving: Bool
    var drivingRangeKm: Double
    var instantPowerKw: Double
    var monthCostKrw: Double
    var lastUpdated: Date
}

enum DataStore {
    static let appGroupID  = "group.com.ggpark.BydStats"
    static let snapshotKey = "widgetSnapshot"

    static func load() -> WidgetSnapshot? {
        guard let defaults = UserDefaults(suiteName: appGroupID),
              let data = defaults.data(forKey: snapshotKey) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }
}

// MARK: - Timeline

struct BydStatsEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
}

struct BydStatsProvider: TimelineProvider {
    func placeholder(in context: Context) -> BydStatsEntry {
        BydStatsEntry(date: .now, snapshot: WidgetSnapshot(  // swiftlint:disable:this line_length
            batteryPercent: 72, isCharging: false, isDriving: false,
            drivingRangeKm: 180, instantPowerKw: 0, monthCostKrw: 14800, lastUpdated: .now
        ))
    }

    func getSnapshot(in context: Context, completion: @escaping (BydStatsEntry) -> Void) {
        completion(BydStatsEntry(date: .now, snapshot: DataStore.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<BydStatsEntry>) -> Void) {
        let entry = BydStatsEntry(date: .now, snapshot: DataStore.load())
        // 15분 후 갱신 (메인 앱 폴링 간격과 동기화)
        let next = Calendar.current.date(byAdding: .minute, value: 15, to: .now) ?? .now
        completion(Timeline(entries: [entry], policy: .after(next)))
    }
}

// MARK: - Widget Definition

@main
struct BydStatsWidget: Widget {
    let kind = "BydStatsWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: BydStatsProvider()) { entry in
            BydStatsWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("BYD Stats")
        .description("배터리 상태와 이번 달 충전 비용을 확인합니다.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Widget View

struct BydStatsWidgetView: View {
    @Environment(\.widgetFamily) var family
    let entry: BydStatsEntry

    var body: some View {
        switch family {
        case .systemSmall:  smallView
        case .systemMedium: mediumView
        default:            smallView
        }
    }

    // MARK: Small — 배터리 % + 상태

    private var smallView: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: "bolt.fill")
                    .font(.caption2).bold()
                    .foregroundStyle(statusColor)
                Text("BYD Stats")
                    .font(.caption2).bold()
                    .foregroundStyle(.secondary)
            }

            Spacer()

            // 배터리 %
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(entry.snapshot.map { "\($0.batteryPercent)" } ?? "--")
                    .font(.system(size: 48, weight: .bold, design: .rounded))
                Text("%")
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }

            // 상태 배지
            if let snap = entry.snapshot {
                Label(statusLabel(snap), systemImage: statusIcon(snap))
                    .font(.caption2).bold()
                    .foregroundStyle(statusColor)
            }

            // 주행가능거리
            if let snap = entry.snapshot, snap.drivingRangeKm > 0 {
                Label(String(format: "%.0f km", snap.drivingRangeKm), systemImage: "road.lanes")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            // 업데이트 시각
            if let snap = entry.snapshot {
                Text(snap.lastUpdated, style: .relative)
                    .font(.system(size: 9))
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    // MARK: Medium — 배터리 + 충전 전력 + 이번 달 비용

    private var mediumView: some View {
        HStack(spacing: 0) {
            // 좌측: 배터리
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Image(systemName: "bolt.fill")
                        .font(.caption2).bold()
                        .foregroundStyle(statusColor)
                    Text("BYD Stats")
                        .font(.caption2).bold()
                        .foregroundStyle(.secondary)
                }

                Spacer()

                HStack(alignment: .firstTextBaseline, spacing: 2) {
                    Text(entry.snapshot.map { "\($0.batteryPercent)" } ?? "--")
                        .font(.system(size: 44, weight: .bold, design: .rounded))
                    Text("%").font(.title3).foregroundStyle(.secondary)
                }

                if let snap = entry.snapshot {
                    Label(statusLabel(snap), systemImage: statusIcon(snap))
                        .font(.caption2).bold()
                        .foregroundStyle(statusColor)
                }

                if let snap = entry.snapshot {
                    Text(snap.lastUpdated, style: .relative)
                        .font(.system(size: 9))
                        .foregroundStyle(.tertiary)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)

            Divider().padding(.vertical, 12)

            // 우측: 주행가능거리 + 충전 전력 + 이번 달 비용
            VStack(alignment: .leading, spacing: 10) {
                if let snap = entry.snapshot {
                    if snap.drivingRangeKm > 0 {
                        mediumStat(icon: "road.lanes", color: .blue,
                                   value: String(format: "%.0f km", snap.drivingRangeKm),
                                   label: "주행가능")
                    }
                    if snap.isCharging && snap.instantPowerKw > 0 {
                        mediumStat(icon: "bolt.fill", color: .green,
                                   value: String(format: "%.1f kW", snap.instantPowerKw),
                                   label: "충전 중")
                    }
                    if snap.monthCostKrw > 0 {
                        mediumStat(icon: "wonsign", color: .orange,
                                   value: String(format: "₩%.0f", snap.monthCostKrw),
                                   label: "이번 달")
                    }
                } else {
                    Text("데이터 없음").font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
    }

    private func mediumStat(icon: String, color: Color, value: String, label: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.caption2)
                .foregroundStyle(color)
                .frame(width: 14)
            VStack(alignment: .leading, spacing: 0) {
                Text(value).font(.subheadline).bold()
                Text(label).font(.system(size: 9)).foregroundStyle(.secondary)
            }
        }
    }

    // MARK: Helpers

    private var statusColor: Color {
        guard let snap = entry.snapshot else { return .gray }
        if snap.isCharging { return .green }
        if snap.isDriving  { return .blue }
        return .gray
    }

    private func statusLabel(_ snap: WidgetSnapshot) -> String {
        if snap.isCharging { return "충전 중" }
        if snap.isDriving  { return "주행 중" }
        return "주차 중"
    }

    private func statusIcon(_ snap: WidgetSnapshot) -> String {
        if snap.isCharging { return "bolt.fill" }
        if snap.isDriving  { return "car.fill" }
        return "parkingsign"
    }
}
