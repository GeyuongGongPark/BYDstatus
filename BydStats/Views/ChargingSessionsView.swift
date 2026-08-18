import SwiftUI
import SwiftData

struct ChargingSessionsView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ChargingSession.startTime, order: .reverse) private var sessions: [ChargingSession]

    @State private var editingSession: ChargingSession?
    @State private var selectedMonthKey: String = Self.currentMonthKey()

    // MARK: - 월별 그룹

    private struct MonthGroup: Identifiable {
        let id: String
        let label: String
        let sessions: [ChargingSession]

        var totalKwh: Double     { sessions.reduce(0) { $0 + $1.energyKwh } }
        var totalCostKrw: Double { sessions.reduce(0) { $0 + $1.estimatedCostKrw } }
        var count: Int           { sessions.count }
    }

    private var monthGroups: [MonthGroup] {
        let cal = Calendar.current
        let grouped = Dictionary(grouping: sessions) { s -> String in
            let c = cal.dateComponents([.year, .month], from: s.startTime)
            return String(format: "%04d-%02d", c.year ?? 0, c.month ?? 0)
        }
        return grouped
            .sorted { $0.key > $1.key }
            .map { key, list in
                let parts = key.split(separator: "-")
                let label = parts.count == 2 ? "\(parts[0])년 \(Int(parts[1]) ?? 0)월" : key
                return MonthGroup(id: key, label: label,
                                  sessions: list.sorted { $0.startTime > $1.startTime })
            }
    }

    private var selectedGroup: MonthGroup? {
        monthGroups.first { $0.id == selectedMonthKey }
            ?? monthGroups.first
    }

    private static func currentMonthKey() -> String {
        let c = Calendar.current.dateComponents([.year, .month], from: Date())
        return String(format: "%04d-%02d", c.year ?? 0, c.month ?? 0)
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
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        ForEach(monthGroups) { group in
                            Button {
                                selectedMonthKey = group.id
                            } label: {
                                if group.id == selectedMonthKey {
                                    Label(group.label, systemImage: "checkmark")
                                } else {
                                    Text(group.label)
                                }
                            }
                        }
                    } label: {
                        Label(selectedGroup?.label ?? "월 선택", systemImage: "calendar")
                            .font(.subheadline)
                    }
                }
            }
            .sheet(item: $editingSession) { ChargingSessionEditSheet(session: $0) }
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
            // 상단 요약 카드
            if let group = selectedGroup {
                Section {
                    summaryCard(group)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
            }

            // 세션 목록
            if let group = selectedGroup {
                Section {
                    ForEach(group.sessions) { session in
                        ChargingSessionRow(session: session)
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) {
                                    modelContext.delete(session)
                                } label: {
                                    Label("삭제", systemImage: "trash")
                                }
                                Button {
                                    editingSession = session
                                } label: {
                                    Label("수정", systemImage: "pencil")
                                }
                                .tint(.blue)
                            }
                    }
                } header: {
                    Text("\(group.count)회 충전")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: - 요약 카드

    private func summaryCard(_ group: MonthGroup) -> some View {
        HStack(spacing: 0) {
            summaryItem(
                value: String(format: "₩%.0f", group.totalCostKrw),
                unit: "",
                label: "충전 비용",
                icon: "wonsign",
                color: .green
            )
            Divider().frame(height: 40)
            summaryItem(
                value: String(format: "%.1f", group.totalKwh),
                unit: "kWh",
                label: "충전량",
                icon: "bolt.fill",
                color: .green
            )
            Divider().frame(height: 40)
            summaryItem(
                value: "\(group.count)",
                unit: "회",
                label: "충전 횟수",
                icon: "repeat",
                color: .blue
            )
        }
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(.background, in: RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.06), radius: 6, x: 0, y: 2)
        .padding(.horizontal, 4)
        .padding(.vertical, 4)
    }

    private func summaryItem(value: String, unit: String, label: String, icon: String, color: Color) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon).foregroundStyle(color).font(.caption)
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(value).font(.title3).bold()
                if !unit.isEmpty {
                    Text(unit).font(.caption).foregroundStyle(.secondary)
                }
            }
            Text(label).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - 충전 세션 행

private struct ChargingSessionRow: View {
    let session: ChargingSession

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "bolt.fill")
                .font(.title3)
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(.green, in: Circle())

            VStack(alignment: .leading, spacing: 3) {
                Text(session.startTime.formatted(.dateTime.locale(Locale(identifier: "ko_KR")).month().day().hour().minute()))
                    .font(.subheadline)

                HStack(spacing: 4) {
                    Text("\(session.startSoc)% → \(session.endSoc)%")
                        .font(.caption).foregroundStyle(.secondary)

                    if session.durationMinutes > 0 {
                        Text("·").foregroundStyle(.secondary)
                        Text("\(session.durationMinutes)분")
                            .font(.caption).foregroundStyle(.secondary)
                    }

                    if session.latitude != nil {
                        Image(systemName: "location.fill")
                            .font(.caption2).foregroundStyle(.blue)
                    }
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 3) {
                if session.estimatedCostKrw > 0 {
                    Text(String(format: "₩%.0f", session.estimatedCostKrw))
                        .font(.subheadline).bold()
                }
                if session.energyKwh > 0 {
                    Text(String(format: "%.1f kWh", session.energyKwh))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 2)
    }
}

// MARK: - 충전 세션 수정 Sheet

private struct ChargingSessionEditSheet: View {
    @Bindable var session: ChargingSession
    @Environment(\.dismiss) private var dismiss
    @AppStorage("electricityRate") private var electricityRate = 180.0

    var body: some View {
        NavigationStack {
            Form {
                Section("SOC") {
                    Stepper("시작  \(session.startSoc)%", value: $session.startSoc, in: 0...100)
                    Stepper("종료  \(session.endSoc)%",  value: $session.endSoc,   in: 0...100)
                }
                Section("충전") {
                    HStack {
                        Text("충전량")
                        Spacer()
                        TextField("kWh", value: Binding(
                            get: { session.energyKwh },
                            set: {
                                session.energyKwh = $0
                                if $0 > 0 {
                                    session.estimatedCostKrw = $0 * electricityRate
                                }
                            }
                        ), format: .number)
                            .multilineTextAlignment(.trailing).keyboardType(.decimalPad).frame(width: 80)
                        Text("kWh").foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("충전 시간")
                        Spacer()
                        TextField("분", value: $session.durationMinutes, format: .number)
                            .multilineTextAlignment(.trailing).keyboardType(.numberPad).frame(width: 60)
                        Text("분").foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("비용")
                            .foregroundStyle(.secondary)
                        Spacer()
                        if session.estimatedCostKrw > 0 {
                            Text(String(format: "₩%.0f", session.estimatedCostKrw))
                        } else {
                            Text("—").foregroundStyle(.tertiary)
                        }
                    }
                }
                Section {
                    Text("충전량 입력 시 설정의 전기요금(₩\(Int(electricityRate))/kWh) 기준으로 비용이 자동 계산됩니다.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("충전 세션 수정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("완료") { dismiss() } }
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
            }
        }
    }
}
