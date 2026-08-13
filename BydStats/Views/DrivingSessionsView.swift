import SwiftUI
import SwiftData

struct DrivingSessionsView: View {
    @Query(sort: \DrivingSession.startTime, order: .reverse) private var sessions: [DrivingSession]

    var body: some View {
        NavigationStack {
            List {
                // TODO: 월별 합계 섹션
                // TODO: 세션 목록 (시작/종료, SOC, kWh, 거리, 전비)
                if sessions.isEmpty {
                    Text("주행 세션 없음")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("주행 세션")
        }
    }
}
