import SwiftUI
import SwiftData

@main
struct BydStatsApp: App {
    let modelContainer: ModelContainer
    let appState = AppState()

    init() {
        do {
            modelContainer = try ModelContainer(for: DataPoint.self, ChargingSession.self, DrivingSession.self)
        } catch {
            fatalError("SwiftData ModelContainer 초기화 실패: \(error)")
        }
    }

    var body: some Scene {
        WindowGroup {
            DashboardView()
                .environment(appState)
        }
        .modelContainer(modelContainer)
    }
}
