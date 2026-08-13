import SwiftUI
import SwiftData

struct ChargingSessionsView: View {
    @Query(sort: \ChargingSession.startTime, order: .reverse) private var sessions: [ChargingSession]

    // MARK: - 월별 그룹

    private struct MonthGroup: Identifiable {
        let id: String          // "2026-08"
        let label: String       // "2026년 8월"
        let sessions: [ChargingSession]

        var totalKwh: Double       { sessions.reduce(0) { $0 + $1.energyKwh } }
        var totalCostKrw: Double   { sessions.reduce(0) { $0 + $1.estimatedCostKrw } }
        var count: Int             { sessions.count }
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
            .navigationTitle("충전 세션")
        }
    }

    private var emptyView: some View {
        ContentUnavailableView {
            Label("충전 세션 없음", systemImage: "bolt.slash")
        } description: {
            Text("충전 중에 앱이 실행되면 자동으로 기록됩니다.")
        }
    }

    private var sessionList: some View {
        List {
            ForEach(monthGroups) { group in
                Section {
                    ForEach(group.sessions) { session in
                        ChargingSessionRow(session: session)
                    }
                } header: {
                    MonthSummaryHeader(
                        label: group.label,
                        left:  String(format: "%.1f kWh", group.totalKwh),
                        right: String(format: "₩%.0f · %d회", group.totalCostKrw, group.count)
                    )
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

// MARK: - 충전 세션 행

private struct ChargingSessionRow: View {
    let session: ChargingSession

    var body: some View {
        HStack(spacing: 12) {
            // 아이콘
            Image(systemName: "bolt.fill")
                .font(.title3)
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(.green, in: Circle())

            // 정보
            VStack(alignment: .leading, spacing: 3) {
                Text(session.startTime, format: .dateTime.month().day().hour().minute())
                    .font(.subheadline)

                HStack(spacing: 4) {
                    Text("\(session.startSoc)% → \(session.endSoc)%")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    if session.durationMinutes > 0 {
                        Text("·")
                            .foregroundStyle(.secondary)
                        Text("\(session.durationMinutes)분")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    if session.latitude != nil {
                        Image(systemName: "location.fill")
                            .font(.caption2)
                            .foregroundStyle(.blue)
                    }
                }
            }

            Spacer()

            // 수치
            VStack(alignment: .trailing, spacing: 3) {
                if session.estimatedCostKrw > 0 {
                    Text(String(format: "₩%.0f", session.estimatedCostKrw))
                        .font(.subheadline).bold()
                }
                if session.energyKwh > 0 {
                    Text(String(format: "%.1f kWh", session.energyKwh))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 2)
    }
}

// MARK: - 월별 헤더

private struct MonthSummaryHeader: View {
    let label: String
    let left: String
    let right: String

    var body: some View {
        HStack {
            Text(label).font(.headline).foregroundStyle(.primary)
            Spacer()
            VStack(alignment: .trailing, spacing: 1) {
                Text(left).font(.caption).foregroundStyle(.secondary)
                Text(right).font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
