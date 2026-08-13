import SwiftUI
import SwiftData

struct ChargingSessionsView: View {
    @Query(sort: \ChargingSession.startTime, order: .reverse) private var sessions: [ChargingSession]

    var body: some View {
        NavigationStack {
            List {
                // TODO: 월별 합계 섹션
                // TODO: 세션 목록 (시작/종료, SOC, kWh, 비용, 위치)
                if sessions.isEmpty {
                    Text("충전 세션 없음")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("충전 세션")
        }
    }
}
