import SwiftUI
import SwiftData

struct DashboardView: View {
    @Query(sort: \DataPoint.timestamp, order: .reverse) private var recentPoints: [DataPoint]
    @Query private var chargingSessions: [ChargingSession]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Text("대시보드")
                        .font(.title2)
                        .foregroundStyle(.secondary)
                    // TODO: 현재 배터리 %, 충전 중 여부
                    // TODO: 오늘 통계 (충전량, 주행거리, 소비 에너지)
                    // TODO: 이번 달 충전 비용 누적
                    // TODO: 최근 충전 세션 요약
                }
                .padding()
            }
            .navigationTitle("BYD Stats")
        }
    }
}
