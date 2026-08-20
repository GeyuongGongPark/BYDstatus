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
        guard let container = modelContainer,
              let state     = appState,
              state.isLoggedIn else {
            completionHandler(.noData)
            return
        }

        let context = ModelContext(container)
        Task {
            await state.pollNow(modelContext: context)
            try? context.save()
            completionHandler(.newData)
        }
    }
}
