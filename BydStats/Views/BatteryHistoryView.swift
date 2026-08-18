import SwiftUI
import SwiftData
import Charts

// MARK: - 기간 필터

enum TimeRange: String, CaseIterable {
    case day    = "24시간"
    case week   = "7일"
    case month  = "30일"
    case all    = "전체"
    case custom = "직접"

    var duration: TimeInterval? {
        switch self {
        case .day:    return 24 * 3600
        case .week:   return 7 * 24 * 3600
        case .month:  return 30 * 24 * 3600
        case .all:    return nil
        case .custom: return nil
        }
    }

    var xAxisFormat: Date.FormatStyle {
        let ko = Locale(identifier: "ko_KR")
        switch self {
        case .day:    return .dateTime.locale(ko).hour().minute()
        case .week:   return .dateTime.locale(ko).month().day().hour()
        case .month:  return .dateTime.locale(ko).month().day()
        case .all:    return .dateTime.locale(ko).year().month().day()
        case .custom: return .dateTime.locale(ko).month().day()
        }
    }
}

// MARK: - 배터리 상태

enum BatteryState: String {
    case charging = "충전"
    case driving  = "주행"
    case parked   = "주차"

    var color: Color {
        switch self {
        case .charging: .green
        case .driving:  .blue
        case .parked:   .gray
        }
    }
}

// MARK: - 상태 구간

struct StateRange: Identifiable {
    let id = UUID()
    let start: Date
    let end: Date
    let state: BatteryState
}

// MARK: - View

struct BatteryHistoryView: View {
    @Query(sort: \DataPoint.timestamp) private var allPoints: [DataPoint]

    @State private var selectedRange: TimeRange = .day
    @State private var selectedDate: Date?
    @State private var customStart: Date = Calendar.current.date(byAdding: .day, value: -7, to: Date()) ?? Date()
    @State private var customEnd: Date   = Date()

    // MARK: Computed

    private var filteredPoints: [DataPoint] {
        switch selectedRange {
        case .custom:
            let end = min(customEnd, Date())
            return allPoints.filter { $0.timestamp >= customStart && $0.timestamp <= end }
        default:
            guard let duration = selectedRange.duration else { return allPoints }
            let cutoff = Date().addingTimeInterval(-duration)
            return allPoints.filter { $0.timestamp >= cutoff }
        }
    }

    private var stateRanges: [StateRange] {
        let points = filteredPoints
        guard points.count >= 2 else { return [] }
        var ranges = [StateRange]()
        var segStart = points[0].timestamp
        var segState = batteryState(of: points[0])
        for i in 1..<points.count {
            let curr = batteryState(of: points[i])
            if curr != segState {
                ranges.append(StateRange(start: segStart, end: points[i].timestamp, state: segState))
                segStart = points[i].timestamp
                segState = curr
            }
        }
        ranges.append(StateRange(start: segStart, end: points.last!.timestamp, state: segState))
        return ranges
    }

    private var selectedPoint: DataPoint? {
        guard let date = selectedDate else { return nil }
        return filteredPoints.min(by: {
            abs($0.timestamp.timeIntervalSince(date)) < abs($1.timestamp.timeIntervalSince(date))
        })
    }

    private var batteryStats: (max: Int, min: Int, avg: Int)? {
        let percents = filteredPoints.map { $0.batteryPercent }
        guard !percents.isEmpty else { return nil }
        let avg = Int((Double(percents.reduce(0, +)) / Double(percents.count)).rounded())
        return (percents.max()!, percents.min()!, avg)
    }

    private func batteryState(of point: DataPoint) -> BatteryState {
        if point.isCharging { return .charging }
        if point.isDriving  { return .driving }
        return .parked
    }

    // MARK: Body

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    // 기간 필터
                    Picker("기간", selection: $selectedRange) {
                        ForEach(TimeRange.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .padding()
                    .onChange(of: selectedRange) { selectedDate = nil }

                    // 직접 날짜 선택
                    if selectedRange == .custom {
                        customDatePicker
                            .padding(.horizontal)
                            .padding(.bottom, 8)
                    }

                    if filteredPoints.isEmpty {
                        emptyView
                    } else {
                        VStack(spacing: 12) {
                            // 통계 타일
                            if let stats = batteryStats {
                                statsCard(stats)
                                    .padding(.horizontal)
                            }

                            // 선택 포인트 정보
                            selectionInfoView
                                .padding(.horizontal)

                            // 차트
                            batteryChart
                                .padding(.horizontal)

                            // 범례
                            legendView
                                .padding()
                        }
                    }
                }
            }
            .navigationTitle("배터리 이력")
        }
    }

    // MARK: - 날짜 직접 선택

    private var customDatePicker: some View {
        VStack(spacing: 8) {
            HStack {
                Text("시작")
                    .font(.caption).foregroundStyle(.secondary)
                    .frame(width: 30, alignment: .leading)
                DatePicker("", selection: $customStart, in: ...customEnd, displayedComponents: [.date, .hourAndMinute])
                    .labelsHidden()
                    .onChange(of: customStart) { selectedDate = nil }
            }
            HStack {
                Text("종료")
                    .font(.caption).foregroundStyle(.secondary)
                    .frame(width: 30, alignment: .leading)
                DatePicker("", selection: $customEnd, in: customStart..., displayedComponents: [.date, .hourAndMinute])
                    .labelsHidden()
                    .onChange(of: customEnd) { selectedDate = nil }
            }
        }
        .padding(12)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - 통계 타일

    private func statsCard(_ stats: (max: Int, min: Int, avg: Int)) -> some View {
        HStack(spacing: 0) {
            statItem(label: "최고", value: stats.max, color: .green)
            Divider().frame(height: 36)
            statItem(label: "최저", value: stats.min, color: .red)
            Divider().frame(height: 36)
            statItem(label: "평균", value: stats.avg, color: .blue)
        }
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(.background, in: RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.06), radius: 6, x: 0, y: 2)
    }

    private func statItem(label: String, value: Int, color: Color) -> some View {
        VStack(spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 1) {
                Text("\(value)").font(.title3).bold().foregroundStyle(color)
                Text("%").font(.caption).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - 빈 상태

    private var emptyView: some View {
        ContentUnavailableView {
            Label("데이터 없음", systemImage: "chart.line.uptrend.xyaxis")
        } description: {
            Text("대시보드에서 새로고침하면 데이터가 쌓입니다.")
        }
        .frame(maxWidth: .infinity, minHeight: 300)
    }

    // MARK: - 선택 포인트 정보

    @ViewBuilder
    private var selectionInfoView: some View {
        if let point = selectedPoint {
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(point.timestamp.formatted(.dateTime.locale(Locale(identifier: "ko_KR")).month().day().hour().minute()))
                        .font(.caption).foregroundStyle(.secondary)
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text("\(point.batteryPercent)")
                            .font(.title2).bold()
                        Text("%").font(.subheadline).foregroundStyle(.secondary)
                    }
                }

                Divider().frame(height: 32)

                let state = batteryState(of: point)
                Label(state.rawValue, systemImage: stateIcon(state))
                    .font(.subheadline)
                    .foregroundStyle(state.color)

                if let kw = point.chargingPowerKw, kw > 0 {
                    Divider().frame(height: 32)
                    Label(String(format: "%.1f kW", kw), systemImage: "bolt.fill")
                        .font(.subheadline)
                        .foregroundStyle(.green)
                }

                Spacer()
            }
            .padding(10)
            .background(.quaternary, in: RoundedRectangle(cornerRadius: 10))
            .transition(.opacity.combined(with: .move(edge: .top)))
        }
    }

    private func stateIcon(_ state: BatteryState) -> String {
        switch state {
        case .charging: "bolt.fill"
        case .driving:  "car.fill"
        case .parked:   "parkingsign"
        }
    }

    // MARK: - 차트

    private var batteryChart: some View {
        Chart {
            ForEach(stateRanges.filter { $0.state != .parked }) { range in
                RectangleMark(
                    xStart: .value("시작", range.start),
                    xEnd:   .value("종료", range.end),
                    yStart: .value("", 0),
                    yEnd:   .value("", 100)
                )
                .foregroundStyle(range.state.color.opacity(0.12))
            }

            ForEach(filteredPoints) { point in
                AreaMark(
                    x: .value("시간", point.timestamp),
                    yStart: .value("", 0),
                    yEnd:   .value("배터리", point.batteryPercent)
                )
                .interpolationMethod(.catmullRom)
                .foregroundStyle(
                    LinearGradient(
                        colors: [.blue.opacity(0.25), .blue.opacity(0.02)],
                        startPoint: .top, endPoint: .bottom
                    )
                )
            }

            ForEach(filteredPoints) { point in
                LineMark(
                    x: .value("시간", point.timestamp),
                    y: .value("배터리", point.batteryPercent)
                )
                .interpolationMethod(.catmullRom)
                .foregroundStyle(.blue)
                .lineStyle(StrokeStyle(lineWidth: 2))
            }

            if let point = selectedPoint {
                PointMark(x: .value("시간", point.timestamp), y: .value("배터리", point.batteryPercent))
                    .symbolSize(80).foregroundStyle(.white)
                PointMark(x: .value("시간", point.timestamp), y: .value("배터리", point.batteryPercent))
                    .symbolSize(40).foregroundStyle(.blue)
                RuleMark(x: .value("선택", point.timestamp))
                    .foregroundStyle(.blue.opacity(0.3))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4]))
            }
        }
        .chartYScale(domain: 0...100)
        .chartYAxis {
            AxisMarks(values: [0, 25, 50, 75, 100]) { value in
                AxisGridLine()
                AxisValueLabel { Text("\(value.as(Int.self) ?? 0)%").font(.caption2) }
            }
        }
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 5)) { value in
                AxisGridLine()
                if let date = value.as(Date.self) {
                    AxisValueLabel {
                        Text(date, format: selectedRange.xAxisFormat).font(.caption2)
                    }
                }
            }
        }
        .chartXSelection(value: $selectedDate)
        .frame(height: 260)
        .animation(.easeInOut(duration: 0.2), value: selectedRange)
    }

    // MARK: - 범례

    private var legendView: some View {
        HStack(spacing: 20) {
            ForEach([BatteryState.charging, .driving, .parked], id: \.rawValue) { state in
                HStack(spacing: 4) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(state.color.opacity(state == .parked ? 0.4 : 0.7))
                        .frame(width: 16, height: 8)
                    Text(state.rawValue).font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
    }
}
