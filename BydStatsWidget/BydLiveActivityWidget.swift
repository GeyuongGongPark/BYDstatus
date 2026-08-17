import ActivityKit
import WidgetKit
import SwiftUI

struct BydLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: BydLiveActivityAttributes.self) { context in
            BydLockScreenView(context: context)
                .activityBackgroundTint(Color.black.opacity(0.75))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    BydDILeadingView(context: context)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    BydDITrailingView(context: context)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    BydDIBottomView(context: context)
                }
            } compactLeading: {
                Image(systemName: context.attributes.sessionType == .driving ? "car.fill" : "bolt.fill")
                    .foregroundStyle(context.attributes.sessionType == .driving ? Color.blue : Color.green)
                    .font(.caption2)
            } compactTrailing: {
                Text("\(context.state.batteryPercent)%")
                    .font(.caption2).bold()
                    .monospacedDigit()
            } minimal: {
                Text("\(context.state.batteryPercent)")
                    .font(.caption2).bold()
                    .monospacedDigit()
            }
        }
    }
}

// MARK: - Lock Screen / Banner

private struct BydLockScreenView: View {
    let context: ActivityViewContext<BydLiveActivityAttributes>

    private var isDriving: Bool { context.attributes.sessionType == .driving }
    private var accentColor: Color { isDriving ? .blue : .green }

    var body: some View {
        HStack(spacing: 16) {
            // 좌: 아이콘 + 상태 + 경과시간
            VStack(spacing: 4) {
                Image(systemName: isDriving ? "car.fill" : "bolt.fill")
                    .font(.title2)
                    .foregroundStyle(accentColor)
                Text(isDriving ? "주행 중" : "충전 중")
                    .font(.caption2).bold()
                    .foregroundStyle(accentColor)
                Text(context.attributes.sessionStartDate, style: .timer)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
            .frame(width: 64)

            // 중: 배터리 % + 진행바
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline, spacing: 2) {
                    Text("\(context.state.batteryPercent)")
                        .font(.system(size: 42, weight: .bold, design: .rounded))
                        .monospacedDigit()
                    Text("%")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }
                ProgressView(value: Double(context.state.batteryPercent), total: 100)
                    .tint(accentColor)
                    .frame(width: 80)
                Text("시작 \(context.attributes.startSoc)%")
                    .font(.system(size: 10))
                    .foregroundStyle(.tertiary)
            }

            Spacer()

            // 우: 전력 + 주행가능거리
            VStack(alignment: .trailing, spacing: 8) {
                if context.state.instantPowerKw > 0 {
                    VStack(alignment: .trailing, spacing: 1) {
                        Text(String(format: "%.1f kW", context.state.instantPowerKw))
                            .font(.subheadline).bold()
                            .foregroundStyle(accentColor)
                        Text(isDriving ? "소비" : "충전")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                }
                if context.state.drivingRangeKm > 0 {
                    VStack(alignment: .trailing, spacing: 1) {
                        Text(String(format: "%.0f km", context.state.drivingRangeKm))
                            .font(.subheadline).bold()
                        Text("주행가능")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
    }
}

// MARK: - Dynamic Island Expanded

private struct BydDILeadingView: View {
    let context: ActivityViewContext<BydLiveActivityAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Label(
                context.attributes.sessionType == .driving ? "주행 중" : "충전 중",
                systemImage: context.attributes.sessionType == .driving ? "car.fill" : "bolt.fill"
            )
            .font(.caption2).bold()
            .foregroundStyle(context.attributes.sessionType == .driving ? Color.blue : Color.green)

            Text(context.attributes.sessionStartDate, style: .timer)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .monospacedDigit()
        }
        .padding(.leading, 4)
    }
}

private struct BydDITrailingView: View {
    let context: ActivityViewContext<BydLiveActivityAttributes>

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 1) {
            Text("\(context.state.batteryPercent)")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .monospacedDigit()
            Text("%")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(.trailing, 4)
    }
}

private struct BydDIBottomView: View {
    let context: ActivityViewContext<BydLiveActivityAttributes>

    private var isDriving: Bool { context.attributes.sessionType == .driving }

    var body: some View {
        HStack {
            if context.state.instantPowerKw > 0 {
                Label(
                    String(format: "%.1f kW", context.state.instantPowerKw),
                    systemImage: isDriving ? "gauge.with.dots.needle.bottom.50percent" : "bolt.fill"
                )
                .font(.caption2)
                .foregroundStyle(isDriving ? Color.blue : Color.green)
            }
            Spacer()
            if context.state.drivingRangeKm > 0 {
                Label(
                    String(format: "%.0f km", context.state.drivingRangeKm),
                    systemImage: "road.lanes"
                )
                .font(.caption2)
                .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 4)
    }
}
