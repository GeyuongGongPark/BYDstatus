import UIKit
import SwiftData
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate {

    /// BydStatsApp에서 modelContainer를 주입
    var modelContainer: ModelContainer?
    var appState: AppState?

    // MARK: - Remote Notification 등록

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in }
        application.registerForRemoteNotifications()
        return true
    }

    // MARK: - Device Token

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        print("[AppDelegate] got device token (last16): \(token.suffix(16))")
        PushRegistrar.register(tokenData: deviceToken)
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("[AppDelegate] registration failed: \(error.localizedDescription)")
    }

    // MARK: - Silent Push 수신 → 폴링

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        guard let container = modelContainer else {
            completionHandler(.noData)
            return
        }

        // AppState.pollNow() 대신 BackgroundTaskManager 경로를 사용:
        // pollNow()는 startPolling()이 호출된 경우에만 sessionDetector가 있어
        // 백그라운드 wake-up 시 sessionDetector == nil → 세션이 기록되지 않는 문제가 있음.
        // handleRefresh()는 매번 자체 SessionDetector를 생성하므로 항상 정상 동작.
        Task {
            await BackgroundTaskManager.handleRefresh(modelContainer: container)
            completionHandler(.newData)
        }
    }
}
