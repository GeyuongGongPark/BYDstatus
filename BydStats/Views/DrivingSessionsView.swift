import SwiftUI
import SwiftData
import Charts

struct DrivingSessionsView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \DrivingSession.startTime, order: .reverse) private var sessions: [DrivingSession]

    @State private var editingSession: DrivingSession?
    @State private var selectedMonthKey: String = Self.currentMonthKey()

    // MARK: - 월별 그룹

    fileprivate struct MonthGroup: Identifiable {
        let id: String
        let label: String
        let sessions: [DrivingSession]

        var totalDistanceKm: Double { sessions.compactMap(\.distanceKm).reduce(0, +) }
        var totalEnergyKwh: Double  { sessions.reduce(0) { $0 + $1.energyKwh } }
        var avgEfficiency: Double? {
            let valid = sessions.filter { ($0.distanceKm ?? 0) > 0 && $0.energyKwh > 0 }
            guard !valid.isEmpty else { return nil }
            let dist   = valid.compactMap(\.distanceKm).reduce(0, +)
            let energy = valid.reduce(0) { $0 + $1.energyKwh }
            return dist / energy
        }
        var count: Int { sessions.count }

        // 일별 주행거리 집계
        var dailyDistances: [DailyDistance] {
            let cal = Calendar.current
            var byDay: [String: Double] = [:]
            for session in sessions {
                guard let dist = session.distanceKm, dist > 0 else { continue }
                let comps = cal.dateComponents([.year, .month, .day], from: session.startTime)
                let key = String(format: "%04d-%02d-%02d", comps.year ?? 0, comps.month ?? 0, comps.day ?? 0)
                byDay[key, default: 0] += dist
            }
            return byDay.sorted { $0.key < $1.key }.compactMap { key, dist in
                let parts = key.split(separator: "-")
                guard parts.count == 3,
                      let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]),
                      let date = Calendar.current.date(from: DateComponents(year: y, month: m, day: d))
                else { return nil }
                return DailyDistance(date: date, distanceKm: dist)
            }
        }
    }

    fileprivate struct DailyDistance: Identifiable {
        let id = UUID()
        let date: Date
        let distanceKm: Double
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
        monthGroups.first { $0.id == selectedMonthKey } ?? monthGroups.first
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
            .navigationTitle("주행 세션")
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
            .sheet(item: $editingSession) { DrivingSessionEditSheet(session: $0) }
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
            if let group = selectedGroup {
                // 상단 요약 카드
                Section {
                    summaryCard(group)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }

                // 일별 주행거리 바차트
                if !group.dailyDistances.isEmpty {
                    Section {
                        dailyBarChart(group)
                            .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
                    } header: {
                        Text("일별 주행거리")
                    }
                }

                // 세션 목록
                Section {
                    ForEach(group.sessions) { session in
                        DrivingSessionRow(session: session)
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
                    Text("\(group.count)회 주행")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: - 요약 카드

    private func summaryCard(_ group: MonthGroup) -> some View {
        HStack(spacing: 0) {
            summaryItem(
                value: String(format: "%.0f", group.totalDistanceKm),
                unit: "km",
                label: "주행거리",
                icon: "road.lanes",
                color: .blue
            )
            Divider().frame(height: 40)
            summaryItem(
                value: String(format: "%.1f", group.totalEnergyKwh),
                unit: "kWh",
                label: "소비",
                icon: "flame.fill",
                color: .orange
            )
            Divider().frame(height: 40)
            if let eff = group.avgEfficiency {
                summaryItem(
                    value: String(format: "%.1f", eff),
                    unit: "km/kWh",
                    label: "평균 전비",
                    icon: "gauge.with.dots.needle.bottom.50percent",
                    color: .green
                )
            } else {
                summaryItem(value: "—", unit: "", label: "평균 전비", icon: "gauge.with.dots.needle.bottom.50percent", color: .green)
            }
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

    // MARK: - 일별 바차트

    private func dailyBarChart(_ group: MonthGroup) -> some View {
        Chart(group.dailyDistances) { day in
            BarMark(
                x: .value("날짜", day.date, unit: .day),
                y: .value("km", day.distanceKm)
            )
            .foregroundStyle(.blue.gradient)
            .cornerRadius(4)
        }
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 6)) { value in
                AxisGridLine()
                if let date = value.as(Date.self) {
                    AxisValueLabel {
                        Text(date.formatted(.dateTime.locale(Locale(identifier: "ko_KR")).day()))
                            .font(.caption2)
                    }
                }
            }
        }
        .chartYAxis {
            AxisMarks(position: .leading) { value in
                AxisGridLine()
                AxisValueLabel {
                    if let v = value.as(Double.self) {
                        Text("\(Int(v))km").font(.caption2)
                    }
                }
            }
        }
        .frame(height: 140)
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
            Image(systemName: "car.fill")
                .font(.title3)
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(.blue, in: Circle())

            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text(session.startTime.formatted(.dateTime.locale(Locale(identifier: "ko_KR")).month().day().hour().minute()))
                        .font(.subheadline).bold()
                    Spacer()
                    if let dur = durationText {
                        Text(dur).font(.caption).foregroundStyle(.secondary)
                    }
                }

                HStack(spacing: 4) {
                    Image(systemName: "bolt.fill").font(.caption2).foregroundStyle(.blue)
                    Text("\(session.startSoc)% → \(session.endSoc)%")
                        .font(.caption).foregroundStyle(.secondary)
                }

                if let startOdo = session.startOdometer, let endOdo = session.endOdometer {
                    HStack(spacing: 4) {
                        Image(systemName: "gauge.medium").font(.caption2).foregroundStyle(.blue)
                        Text(String(format: "%.0f km → %.0f km", startOdo, endOdo))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }

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

// MARK: - 주행 세션 수정 Sheet

private struct DrivingSessionEditSheet: View {
    @Bindable var session: DrivingSession
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("SOC") {
                    Stepper("시작  \(session.startSoc)%", value: $session.startSoc, in: 0...100)
                    Stepper("종료  \(session.endSoc)%",  value: $session.endSoc,   in: 0...100)
                }
                Section("주행") {
                    HStack {
                        Text("주행 거리")
                        Spacer()
                        TextField("km", value: Binding(
                            get: { session.distanceKm ?? 0 },
                            set: {
                                session.distanceKm = $0 > 0 ? $0 : nil
                                if $0 > 0 && session.energyKwh > 0 {
                                    session.efficiencyKmPerKwh = $0 / session.energyKwh
                                }
                            }
                        ), format: .number)
                            .multilineTextAlignment(.trailing).keyboardType(.decimalPad).frame(width: 80)
                        Text("km").foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("소비")
                        Spacer()
                        TextField("kWh", value: Binding(
                            get: { session.energyKwh },
                            set: {
                                session.energyKwh = $0
                                if let dist = session.distanceKm, dist > 0 && $0 > 0 {
                                    session.efficiencyKmPerKwh = dist / $0
                                }
                            }
                        ), format: .number)
                            .multilineTextAlignment(.trailing).keyboardType(.decimalPad).frame(width: 80)
                        Text("kWh").foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("전비").foregroundStyle(.secondary)
                        Spacer()
                        if let eff = session.efficiencyKmPerKwh, eff > 0 {
                            Text(String(format: "%.2f", eff))
                        } else {
                            Text("—").foregroundStyle(.tertiary)
                        }
                        Text("km/kWh").foregroundStyle(.secondary)
                    }
                }
                Section("ODO") {
                    HStack {
                        Text("시작")
                        Spacer()
                        TextField("km", value: Binding(
                            get: { session.startOdometer ?? 0 },
                            set: { session.startOdometer = $0 > 0 ? $0 : nil }
                        ), format: .number)
                            .multilineTextAlignment(.trailing).keyboardType(.decimalPad).frame(width: 100)
                        Text("km").foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("종료")
                        Spacer()
                        TextField("km", value: Binding(
                            get: { session.endOdometer ?? 0 },
                            set: { session.endOdometer = $0 > 0 ? $0 : nil }
                        ), format: .number)
                            .multilineTextAlignment(.trailing).keyboardType(.decimalPad).frame(width: 100)
                        Text("km").foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("주행 세션 수정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("완료") { dismiss() } }
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
            }
        }
    }
}
