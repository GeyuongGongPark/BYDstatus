import Foundation
import BackgroundTasks
import SwiftData
@preconcurrency import ActivityKit

enum BackgroundTaskManager {
    static let taskIdentifier = "com.ggpark.BydStats.refresh"

    static func scheduleNextRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// BydStatsApp의 `.backgroundTask(.appRefresh(...))` modifier에서 호출
    static func handleRefresh(modelContainer: ModelContainer) async {
        scheduleNextRefresh()

        guard let region     = KeychainHelper.load(forKey: "byd.region"),
              let userId     = KeychainHelper.load(forKey: "byd.userId"),
              let signToken  = KeychainHelper.load(forKey: "byd.signToken"),
              let encryToken = KeychainHelper.load(forKey: "byd.encryToken"),
              let vin        = KeychainHelper.load(forKey: "byd.vin"),
              let username   = KeychainHelper.load(forKey: "byd.username"),
              let password   = KeychainHelper.load(forKey: "byd.password") else { return }

        guard let svc = try? BydVehicleService(config: BydConfig.fromRegion(region)) else { return }
        await svc.restoreSession(userId: userId, signToken: signToken, encryToken: encryToken)
        await svc.setCredentials(username: username, password: password)

        // UserDefaults에서 설정 읽기
        let defaults = UserDefaults.standard
        let ratePlanId  = defaults.string(forKey: "ratePlanId") ?? "kepco_low"
        let customRate  = defaults.double(forKey: "electricityRate").nonZero ?? 180.0
        let modelRaw    = defaults.string(forKey: "vehicleModel") ?? VehicleModel.atto3.rawValue
        let capacity    = VehicleModel(rawValue: modelRaw)?.batteryCapacityKwh ?? 60.48
        let plan        = ratePlan(id: ratePlanId, customRate: customRate)

        // SwiftData context를 먼저 생성 (DataPoint 조회에도 사용)
        let context = ModelContext(modelContainer)

        guard var status = try? await svc.fetchVehicleStatus(vin: vin) else { return }
        guard status.batteryPercentage > 0 else { return }

        // totalMileage == 0 보완
        if status.totalMileage == 0,
           let energy = try? await svc.fetchEnergyConsumption(vin: vin),
           energy.lifetimeMileageKm > 0 {
            status.totalMileage = energy.lifetimeMileageKm
        }

        // 주차 중: 직전 DataPoint의 SOC·충전상태로 gl=0 완속 충전 보완
        // API 추가 호출 없이 SOC delta + 이전 충전 플래그로 판단
        if !status.isDriving {
            var descriptor = FetchDescriptor<DataPoint>(
                sortBy: [SortDescriptor(\.timestamp, order: .reverse)]
            )
            descriptor.fetchLimit = 1
            if let last = try? context.fetch(descriptor).first {
                var prev = VehicleStatus()
                prev.batteryPercentage = last.batteryPercent
                prev.resolvedCharging = last.isCharging
                status = status.withChargingResolved(previous: prev, apiIsCharging: nil)
            }
        }
        let detector = SessionDetector(
            modelContext: context,
            getRateAt: { date in plan.rate(at: date) },
            batteryCapacityKwh: capacity,
            gpsEnabled: false   // 백그라운드에서 GPS 비활성화
        )
        detector.process(status: status, at: Date())
        try? context.save()

        // 주행/충전이 끝난 경우 Live Activity 종료
        if !status.isDriving && !status.isCharging {
            for activity in Activity<BydLiveActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }

        // 위젯 스냅샷 업데이트
        let snapshot = WidgetSnapshot(
            batteryPercent: status.batteryPercentage,
            isCharging:     status.isCharging,
            isDriving:      status.isDriving,
            drivingRangeKm: status.drivingRange,
            instantPowerKw: status.instantPowerKw,
            monthCostKrw:   monthChargingCost(context: context),
            lastUpdated:    Date()
        )
        WidgetDataStore.save(snapshot)
    }

    private static func monthChargingCost(context: ModelContext) -> Double {
        let cal = Calendar.current
        let descriptor = FetchDescriptor<ChargingSession>()
        let all = (try? context.fetch(descriptor)) ?? []
        return all
            .filter { cal.isDate($0.startTime, equalTo: Date(), toGranularity: .month) }
            .reduce(0) { $0 + $1.estimatedCostKrw }
    }
}

private extension Double {
    var nonZero: Double? { self == 0 ? nil : self }
}
