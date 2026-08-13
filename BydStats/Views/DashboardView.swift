import SwiftUI
import SwiftData

struct DashboardView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.modelContext) private var modelContext

    @Query(sort: \ChargingSession.startTime, order: .reverse) private var allChargingSessions: [ChargingSession]
    @Query(sort: \DrivingSession.startTime, order: .reverse)  private var allDrivingSessions: [DrivingSession]

    @AppStorage("vehicleModel")    private var vehicleModelRaw = VehicleModel.atto3.rawValue
    @AppStorage("electricityRate") private var electricityRate = 180.0
    @AppStorage("pollingInterval") private var pollingInterval = 5

    private var vehicleModel: VehicleModel { VehicleModel(rawValue: vehicleModelRaw) ?? .atto3 }

    private var todayCharging: [ChargingSession] {
        allChargingSessions.filter { Calendar.current.isDateInToday($0.startTime) }
    }
    private var todayDriving: [DrivingSession] {
        allDrivingSessions.filter { Calendar.current.isDateInToday($0.startTime) }
    }
    private var thisMonthCharging: [ChargingSession] {
        allChargingSessions.filter {
            Calendar.current.isDate($0.startTime, equalTo: Date(), toGranularity: .month)
        }
    }
    private var recentCharging: [ChargingSession] {
        Array(allChargingSessions.prefix(3))
    }

    var body: some View {
        NavigationStack {
            Group {
                if !appState.isLoggedIn {
                    notLoggedInView
                } else if appState.selectedVin == nil {
                    noVinView
                } else {
                    dashboardContent
                }
            }
            .navigationTitle("BYD Stats")
            .toolbar {
                if appState.isLoggedIn, appState.selectedVin != nil {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button {
                            Task { await appState.pollNow(modelContext: modelContext) }
                        } label: {
                            if appState.isPolling {
                                ProgressView().scaleEffect(0.8)
                            } else {
                                Image(systemName: "arrow.clockwise")
                            }
                        }
                        .disabled(appState.isPolling)
                    }
                }
            }
        }
        .task(id: appState.selectedVin) {
            guard appState.isLoggedIn, appState.selectedVin != nil else { return }
            appState.startPolling(
                modelContext: modelContext,
                pollingInterval: pollingInterval,
                electricityRate: electricityRate,
                batteryCapacityKwh: vehicleModel.batteryCapacityKwh
            )
        }
    }

    // MARK: - 비로그인

    private var notLoggedInView: some View {
        ContentUnavailableView {
            Label("로그인 필요", systemImage: "person.crop.circle.badge.exclamationmark")
        } description: {
            Text("설정에서 BYD 계정으로 로그인하세요.")
        }
    }

    // MARK: - VIN 미선택

    private var noVinView: some View {
        ContentUnavailableView {
            Label("차량을 선택하세요", systemImage: "car.2")
        } description: {
            Text("설정에서 차량을 선택하세요.")
        }
    }

    // MARK: - 대시보드 본문

    private var dashboardContent: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let err = appState.pollError {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                        Text(err)
                            .font(.caption)
                            .foregroundStyle(.primary)
                        Spacer()
                    }
                    .padding(12)
                    .background(.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
                }
                batteryCard
                todaySummaryCard
                monthlyCard
                if !recentCharging.isEmpty {
                    recentChargingCard
                }
            }
            .padding()
        }
        .refreshable {
            await appState.pollNow(modelContext: modelContext)
        }
    }

    // MARK: - 배터리 카드

    private var batteryCard: some View {
        CardView {
            VStack(spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    Text(appState.currentStatus.map { "\($0.batteryPercentage)" } ?? "--")
                        .font(.system(size: 64, weight: .bold, design: .rounded))
                    Text("%")
                        .font(.title)
                        .foregroundStyle(.secondary)
                    Spacer()
                    statusBadge
                }

                // 배터리 바
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.quaternary)
                        Capsule()
                            .fill(batteryColor)
                            .frame(width: geo.size.width * batteryFraction)
                    }
                }
                .frame(height: 12)

                HStack {
                    if let status = appState.currentStatus {
                        Label(String(format: "%.0f km", status.drivingRange), systemImage: "road.lanes")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        if status.isCharging {
                            Spacer()
                            Label(String(format: "%.1f kW", abs(status.instantPowerKw)),
                                  systemImage: "bolt.fill")
                                .font(.subheadline)
                                .foregroundStyle(.green)
                        }
                    } else {
                        Text(appState.isPolling ? "데이터 로딩 중…" : "새로고침을 눌러 데이터를 가져오세요")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                }
            }
        }
    }

    private var batteryFraction: Double {
        guard let s = appState.currentStatus else { return 0 }
        return Double(s.batteryPercentage) / 100.0
    }

    private var batteryColor: Color {
        guard let s = appState.currentStatus else { return .gray }
        if s.isCharging { return .green }
        if s.batteryPercentage < 20 { return .red }
        if s.batteryPercentage < 40 { return .orange }
        return .blue
    }

    @ViewBuilder
    private var statusBadge: some View {
        if let s = appState.currentStatus {
            if s.isCharging {
                Label("충전 중", systemImage: "bolt.fill")
                    .font(.caption).bold()
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(.green, in: Capsule())
            } else if s.isDriving {
                Label("주행 중", systemImage: "car.fill")
                    .font(.caption).bold()
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(.blue, in: Capsule())
            } else {
                Label("주차 중", systemImage: "parkingsign")
                    .font(.caption).bold()
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(.quaternary, in: Capsule())
            }
        }
    }

    // MARK: - 오늘 요약 카드

    private var todaySummaryCard: some View {
        CardView {
            VStack(alignment: .leading, spacing: 12) {
                Text("오늘")
                    .font(.headline)

                HStack(spacing: 0) {
                    todayStat(
                        value: String(format: "%.1f", todayCharging.reduce(0) { $0 + $1.energyKwh }),
                        unit: "kWh",
                        label: "충전량",
                        icon: "bolt.fill",
                        color: .green
                    )
                    Divider().frame(height: 40)
                    todayStat(
                        value: String(format: "%.0f", todayDriving.compactMap(\.distanceKm).reduce(0, +)),
                        unit: "km",
                        label: "주행거리",
                        icon: "road.lanes",
                        color: .blue
                    )
                    Divider().frame(height: 40)
                    todayStat(
                        value: String(format: "%.1f", todayDriving.reduce(0) { $0 + $1.energyKwh }),
                        unit: "kWh",
                        label: "소비",
                        icon: "flame.fill",
                        color: .orange
                    )
                }
            }
        }
    }

    private func todayStat(value: String, unit: String, label: String, icon: String, color: Color) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon).foregroundStyle(color).font(.caption)
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(value).font(.title3).bold()
                Text(unit).font(.caption).foregroundStyle(.secondary)
            }
            Text(label).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - 이번 달 카드

    private var monthlyCard: some View {
        CardView {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("이번 달 충전 비용")
                        .font(.headline)
                    let totalCost = thisMonthCharging.reduce(0) { $0 + $1.estimatedCostKrw }
                    Text(String(format: "₩%.0f", totalCost))
                        .font(.title2).bold()
                    Text("\(thisMonthCharging.count)회 충전")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "wonsign.circle.fill")
                    .font(.system(size: 40))
                    .foregroundStyle(.green.opacity(0.8))
            }
        }
    }

    // MARK: - 최근 충전 세션 카드

    private var recentChargingCard: some View {
        CardView {
            VStack(alignment: .leading, spacing: 12) {
                Text("최근 충전")
                    .font(.headline)

                ForEach(recentCharging) { session in
                    recentChargingRow(session)
                    if session.id != recentCharging.last?.id {
                        Divider()
                    }
                }
            }
        }
    }

    private func recentChargingRow(_ session: ChargingSession) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(session.startTime, style: .date)
                    .font(.subheadline)
                Text("\(session.startSoc)% → \(session.endSoc)%  •  \(String(format: "%.1f kWh", session.energyKwh))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(String(format: "₩%.0f", session.estimatedCostKrw))
                    .font(.subheadline).bold()
                if session.durationMinutes > 0 {
                    Text("\(session.durationMinutes)분")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

// MARK: - 카드 컨테이너

struct CardView<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.background, in: RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.06), radius: 8, x: 0, y: 2)
    }
}
