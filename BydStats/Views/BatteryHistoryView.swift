import SwiftUI
import SwiftData

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
        case .all, .custom: return nil
        }
    }
}

// MARK: - 배터리 상태

private enum BattState: String {
    case charging = "충전"
    case driving  = "주행"
    case parked   = "주차"

    var color: Color {
        switch self {
        case .charging: Color(red: 0.18, green: 0.69, blue: 0.35)  // 초록
        case .driving:  Color(red: 0.96, green: 0.56, blue: 0.13)  // 주황
        case .parked:   Color(uiColor: .systemGray4)
        }
    }
}

private func battState(of p: DataPoint) -> BattState {
    if p.isCharging { return .charging }
    if p.isDriving  { return .driving }
    return .parked
}

// MARK: - 리샘플링 (기간별 버킷)

private func resample(_ points: [DataPoint], bucketMin: Int) -> [DataPoint] {
    let bucketSec: TimeInterval = TimeInterval(bucketMin * 60)
    var buckets: [Int: DataPoint] = [:]
    for pt in points {
        let key = Int(pt.timestamp.timeIntervalSince1970 / bucketSec)
        buckets[key] = pt
    }
    return buckets.sorted { $0.key < $1.key }.map { $0.value }
}

private func bucketMin(for range: TimeRange, customDuration: TimeInterval?) -> Int {
    switch range {
    case .day:    return 30
    case .week:   return 60
    case .month:  return 120
    case .all:    return 120
    case .custom:
        let dur = customDuration ?? (7 * 24 * 3600)
        if dur <= 24 * 3600        { return 30  }
        if dur <= 7 * 24 * 3600   { return 60  }
        return 120
    }
}

// MARK: - View

struct BatteryHistoryView: View {
    @Query(sort: \DataPoint.timestamp) private var allPoints: [DataPoint]

    @State private var selectedRange: TimeRange = .day
    @State private var customStart: Date = Calendar.current.date(byAdding: .day, value: -7, to: Date()) ?? Date()
    @State private var customEnd:   Date = Date()

    // MARK: Computed

    private var filteredPoints: [DataPoint] {
        let pts: [DataPoint]
        let customDuration: TimeInterval?
        switch selectedRange {
        case .custom:
            let end = min(customEnd, Date())
            pts = allPoints.filter { $0.timestamp >= customStart && $0.timestamp <= end }
            customDuration = end.timeIntervalSince(customStart)
        default:
            guard let duration = selectedRange.duration else { pts = allPoints; customDuration = nil; break }
            let cutoff = Date().addingTimeInterval(-duration)
            pts = allPoints.filter { $0.timestamp >= cutoff }
            customDuration = nil
        }
        return resample(pts, bucketMin: bucketMin(for: selectedRange, customDuration: customDuration))
    }

    private var latest: DataPoint? { filteredPoints.last }

    private var batteryStats: (max: Int, min: Int, avg: Int)? {
        let pcts = filteredPoints.map { $0.batteryPercent }
        guard !pcts.isEmpty else { return nil }
        let avg = Int((Double(pcts.reduce(0, +)) / Double(pcts.count)).rounded())
        return (pcts.max()!, pcts.min()!, avg)
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

                    if selectedRange == .custom {
                        customDatePicker
                            .padding(.horizontal)
                            .padding(.bottom, 8)
                    }

                    if filteredPoints.isEmpty {
                        emptyView
                    } else {
                        VStack(spacing: 10) {
                            // 최신 상태 헤더
                            if let pt = latest {
                                headerCard(pt)
                                    .padding(.horizontal)
                            }

                            // 통계 카드
                            if let stats = batteryStats {
                                statsCard(stats).padding(.horizontal)
                            }

                            // 바 차트 목록 (최신순)
                            barList
                                .padding(.horizontal)

                            // 범례
                            legendView.padding()
                        }
                    }
                }
            }
            .navigationTitle("배터리 이력")
        }
    }

    // MARK: - 최신 상태 헤더

    private func headerCard(_ pt: DataPoint) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("최종 상태")
                    .font(.caption2).foregroundStyle(.secondary)
                HStack(alignment: .firstTextBaseline, spacing: 3) {
                    Text("\(pt.batteryPercent)%")
                        .font(.title2).bold()
                        .foregroundStyle(battState(of: pt).color)
                    if let range = pt.drivingRangeKm, range > 0 {
                        Text("· \(Int(range)) km 남음")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            }
            Spacer()
            let state = battState(of: pt)
            Label(state.rawValue, systemImage: stateIcon(state))
                .font(.subheadline).bold()
                .foregroundStyle(state.color)
                .padding(.horizontal, 10).padding(.vertical, 5)
                .background(state.color.opacity(0.12), in: Capsule())
        }
        .padding(12)
        .background(.background, in: RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.06), radius: 6, x: 0, y: 2)
    }

    // MARK: - 통계 카드

    private func statsCard(_ s: (max: Int, min: Int, avg: Int)) -> some View {
        HStack(spacing: 0) {
            statItem("최고", s.max, .green)
            Divider().frame(height: 36)
            statItem("최저", s.min, .red)
            Divider().frame(height: 36)
            statItem("평균", s.avg, .blue)
        }
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(.background, in: RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.06), radius: 6, x: 0, y: 2)
    }

    private func statItem(_ label: String, _ value: Int, _ color: Color) -> some View {
        VStack(spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 1) {
                Text("\(value)").font(.title3).bold().foregroundStyle(color)
                Text("%").font(.caption).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - 바 목록

    private var barList: some View {
        LazyVStack(spacing: 4) {
            ForEach(filteredPoints.reversed()) { point in
                BatteryBarRow(point: point)
            }
        }
    }

    // MARK: - 범례

    private var legendView: some View {
        HStack(spacing: 20) {
            ForEach([BattState.charging, .driving, .parked], id: \.rawValue) { state in
                HStack(spacing: 5) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(state.color.opacity(state == .parked ? 0.5 : 0.8))
                        .frame(width: 16, height: 8)
                    Text(state.rawValue).font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
    }

    // MARK: - 날짜 직접 선택

    private var customDatePicker: some View {
        VStack(spacing: 8) {
            HStack {
                Text("시작").font(.caption).foregroundStyle(.secondary).frame(width: 30, alignment: .leading)
                DatePicker("", selection: $customStart, in: ...customEnd, displayedComponents: [.date, .hourAndMinute])
                    .labelsHidden()
            }
            HStack {
                Text("종료").font(.caption).foregroundStyle(.secondary).frame(width: 30, alignment: .leading)
                DatePicker("", selection: $customEnd, in: customStart..., displayedComponents: [.date, .hourAndMinute])
                    .labelsHidden()
            }
        }
        .padding(12)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
    }

    private var emptyView: some View {
        ContentUnavailableView {
            Label("데이터 없음", systemImage: "chart.bar.xaxis")
        } description: {
            Text("대시보드에서 새로고침하면 데이터가 쌓입니다.")
        }
        .frame(maxWidth: .infinity, minHeight: 300)
    }

    private func stateIcon(_ state: BattState) -> String {
        switch state {
        case .charging: "bolt.fill"
        case .driving:  "car.fill"
        case .parked:   "parkingsign"
        }
    }
}

// MARK: - 개별 바 행

private struct BatteryBarRow: View {
    let point: DataPoint

    private var state: BattState { battState(of: point) }

    private var timeLabel: String {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "ko_KR")
        fmt.dateFormat = "M/d HH:mm"
        return fmt.string(from: point.timestamp)
    }

    var body: some View {
        HStack(spacing: 8) {
            // 시간 라벨
            Text(timeLabel)
                .font(.system(size: 10, design: .monospaced))
                .foregroundStyle(.secondary)
                .frame(width: 72, alignment: .leading)
                .lineLimit(1)

            // 배터리 바
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    // 배경
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color(uiColor: .systemGray6))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)

                    // 채워진 바
                    RoundedRectangle(cornerRadius: 4)
                        .fill(state.color.opacity(state == .parked ? 0.45 : 0.8))
                        .frame(width: geo.size.width * CGFloat(point.batteryPercent) / 100.0,
                               height: geo.size.height)

                    // 퍼센트 텍스트 (바 안)
                    Text("\(point.batteryPercent)%")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(point.batteryPercent > 15 ? Color.white : state.color)
                        .padding(.leading, 6)
                }
            }
            .frame(height: 24)

            // 주행가능 km
            if let range = point.drivingRangeKm, range > 0 {
                Text("\(Int(range))km")
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
                    .frame(width: 46, alignment: .trailing)
            } else {
                Spacer().frame(width: 46)
            }
        }
    }
}
