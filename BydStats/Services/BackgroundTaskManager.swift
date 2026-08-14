import Foundation
import BackgroundTasks

enum BackgroundTaskManager {
    static let taskIdentifier = "com.ggpark.BydStats.refresh"

    static func scheduleNextRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// BydStatsApp의 `.backgroundTask(.appRefresh(...))` modifier에서 호출
    static func handleRefresh() async {
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

        guard let status = try? await svc.fetchVehicleStatus(vin: vin) else { return }

        let snapshot = WidgetSnapshot(
            batteryPercent: status.batteryPercentage,
            isCharging:     status.isCharging,
            isDriving:      status.isDriving,
            drivingRangeKm: status.drivingRange,
            instantPowerKw: status.instantPowerKw,
            monthCostKrw:   0,
            lastUpdated:    Date()
        )
        WidgetDataStore.save(snapshot)
    }
}
