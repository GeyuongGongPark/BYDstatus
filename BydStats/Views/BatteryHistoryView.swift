import SwiftUI
import SwiftData
import Charts

struct BatteryHistoryView: View {
    @Query(sort: \DataPoint.timestamp) private var dataPoints: [DataPoint]

    var body: some View {
        NavigationStack {
            VStack {
                // TODO: 기간 필터 (24h / 7d / 30d / 전체)
                // TODO: Swift Charts LineChart — X: 시간, Y: 배터리 %
                // TODO: 구간 색상 (충전=초록, 주행=파랑, 주차=회색)
                Text("배터리 이력 그래프")
                    .foregroundStyle(.secondary)
            }
            .navigationTitle("배터리 이력")
        }
    }
}
