import SwiftUI
import SwiftData

struct DrivingSessionsView: View {
    @Query(sort: \DrivingSession.startTime, order: .reverse) private var sessions: [DrivingSession]

    // MARK: - 월별 그룹

    fileprivate struct MonthGroup: Identifiable {
        let id: String
        let label: String
        let sessions: [DrivingSession]

        var totalDistanceKm: Double  { sessions.compactMap(\.distanceKm).reduce(0, +) }
        var totalEnergyKwh: Double   { sessions.reduce(0) { $0 + $1.energyKwh } }
        var avgEfficiency: Double? {
            let withDist = sessions.filter { ($0.distanceKm ?? 0) > 0 && $0.energyKwh > 0 }
            guard !withDist.isEmpty else { return nil }
            let totalDist = withDist.compactMap(\.distanceKm).reduce(0, +)
            let totalEnergy = withDist.reduce(0) { $0 + $1.energyKwh }
            return totalDist / totalEnergy
        }
        var count: Int { sessions.count }
    }

    private var monthGroups: [MonthGroup] {
        let cal = Calendar.current
        let grouped = Dictionary(grouping: sessions) { session -> String in
            let c = cal.dateComponents([.year, .month], from: session.startTime)
            return String(format: "%04d-%02d", c.year ?? 0, c.month ?? 0)
        }
        return grouped
            .sorted { $0.key > $1.key }
            .map { key, list in
                let parts = key.split(separator: "-")
                let label = parts.count == 2
                    ? "\(parts[0])년 \(Int(parts[1]) ?? 0)월"
                    : key
                return MonthGroup(
                    id: key,
                    label: label,
                    sessions: list.sorted { $0.startTime > $1.startTime }
                )
            }
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            Group {
                if sessions.isEmpty {
                    emptyView
                } else {
                    sessionList
                }
            }
            .navigationTitle("주행 세션")
        }
    }

    private var emptyView: some View {
        ContentUnavailableView {
            Label("주행 세션 없음", systemImage: "car.slash")
        } description: {
            Text("주행 중에 앱이 실행되면 자동으로 기록됩니다.")
        }
    }

    private var sessionList: some View {
        List {
            ForEach(monthGroups) { group in
                Section {
                    ForEach(group.sessions) { session in
                        DrivingSessionRow(session: session)
                    }
                } header: {
                    DrivingMonthHeader(group: group)
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

// MARK: - 주행 세션 행

private struct DrivingSessionRow: View {
    let session: DrivingSession

    private var durationText: String? {
        guard let end = session.endTime else { return nil }
        let min = Int(end.timeIntervalSince(session.startTime) / 60)
        guard min > 0 else { return nil }
        return min >= 60 ? "\(min / 60)시간 \(min % 60)분" : "\(min)분"
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            // 아이콘
            Image(systemName: "car.fill")
                .font(.title3)
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(.blue, in: Circle())

            VStack(alignment: .leading, spacing: 5) {
                // 날짜 + 주행 시간
                HStack {
                    Text(session.startTime.formatted(.dateTime.locale(Locale(identifier: "ko_KR")).month().day().hour().minute()))
                        .font(.subheadline).bold()
                    Spacer()
                    if let dur = durationText {
                        Text(dur)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                // 배터리 SOC
                HStack(spacing: 4) {
                    Image(systemName: "bolt.fill").font(.caption2).foregroundStyle(.blue)
                    Text("\(session.startSoc)% → \(session.endSoc)%")
                        .font(.caption).foregroundStyle(.secondary)
                }

                // ODO (있을 때만)
                if let startOdo = session.startOdometer, let endOdo = session.endOdometer {
                    HStack(spacing: 4) {
                        Image(systemName: "gauge.medium").font(.caption2).foregroundStyle(.blue)
                        Text(String(format: "%.0f km → %.0f km", startOdo, endOdo))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }

                // 주행 거리 / 소비 / 전비
                HStack(spacing: 16) {
                    if let dist = session.distanceKm, dist > 0 {
                        statLabel(value: String(format: "%.1f km", dist), label: "주행거리")
                    }
                    if session.energyKwh > 0 {
                        statLabel(value: String(format: "%.2f kWh", session.energyKwh), label: "소비")
                    }
                    if let eff = session.efficiencyKmPerKwh, eff > 0 {
                        statLabel(value: String(format: "%.1f km/kWh", eff), label: "전비")
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func statLabel(value: String, label: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(value).font(.caption).bold()
            Text(label).font(.caption2).foregroundStyle(.secondary)
        }
    }
}

// MARK: - 주행 월별 헤더

fileprivate struct DrivingMonthHeader: View {
    let group: DrivingSessionsView.MonthGroup

    var body: some View {
        HStack {
            Text(group.label).font(.headline).foregroundStyle(.primary)
            Spacer()
            VStack(alignment: .trailing, spacing: 1) {
                if group.totalDistanceKm > 0 {
                    Text(String(format: "%.0f km", group.totalDistanceKm))
                        .font(.caption).foregroundStyle(.secondary)
                }
                HStack(spacing: 4) {
                    if let avg = group.avgEfficiency {
                        Text(String(format: "평균 %.1f km/kWh", avg))
                    }
                    Text("· \(group.count)회")
                }
                .font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
