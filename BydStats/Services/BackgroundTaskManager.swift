import Foundation
@preconcurrency import BackgroundTasks

final class BackgroundTaskManager {
    static let taskIdentifier = "com.ggpark.BydStats.refresh"

    static func registerTask(service: BydVehicleService, vin: String) {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            guard let refreshTask = task as? BGAppRefreshTask else { return }
            Self.handleRefresh(task: refreshTask, service: service, vin: vin)
        }
    }

    static func scheduleNextRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    private static func handleRefresh(task: BGAppRefreshTask, service: BydVehicleService, vin: String) {
        scheduleNextRefresh()

        let fetchTask = Task {
            do {
                let status = try await service.fetchVehicleStatus(vin: vin)
                // TODO: 백그라운드 ModelContext 생성 후 DataPoint 저장
                _ = status
                task.setTaskCompleted(success: true)
            } catch {
                task.setTaskCompleted(success: false)
            }
        }

        task.expirationHandler = {
            fetchTask.cancel()
        }
    }
}
