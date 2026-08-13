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
            TabView {
                DashboardView()
                    .tabItem { Label("대시보드", systemImage: "gauge.with.dots.needle.bottom.50percent") }

                BatteryHistoryView()
                    .tabItem { Label("배터리", systemImage: "bolt.fill") }

                ChargingSessionsView()
                    .tabItem { Label("충전", systemImage: "ev.plug.dc.ccs1") }

                DrivingSessionsView()
                    .tabItem { Label("주행", systemImage: "car.fill") }

                SettingsView()
                    .tabItem { Label("설정", systemImage: "gearshape.fill") }
            }
            .environment(appState)
        }
        .modelContainer(modelContainer)
    }
}
