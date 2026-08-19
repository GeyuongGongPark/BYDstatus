import Foundation

// ─── 계절 ───────────────────────────────────────────────────────────────────

enum Season {
    case summer      // 6-8월
    case springFall  // 3-5월, 9-10월
    case winter      // 11-2월

    static func of(month: Int) -> Season {
        switch month {
        case 6, 7, 8:       return .summer
        case 11, 12, 1, 2:  return .winter
        default:             return .springFall
        }
    }
}

// ─── 시간대 슬롯 ─────────────────────────────────────────────────────────────

struct RateSlot: Identifiable {
    let id = UUID()
    let startHour: Int   // inclusive
    let endHour: Int     // exclusive
    let rate: Double     // 원/kWh
    let label: String

    var timeString: String {
        String(format: "%02d:00~%02d:00", startHour, endHour)
    }

    func contains(hour: Int) -> Bool {
        startHour < endHour ? (startHour..<endHour).contains(hour)
                            : hour >= startHour || hour < endHour
    }
}

// ─── 요금제 ──────────────────────────────────────────────────────────────────

enum ChargingRatePlan: Equatable {
    case flat(id: String, name: String, provider: String, rate: Double)
    case timeOfUse(id: String, name: String, provider: String, slots: [Season: [RateSlot]])
    case custom(rate: Double)

    var id: String {
        switch self {
        case .flat(let id, _, _, _):       return id
        case .timeOfUse(let id, _, _, _):  return id
        case .custom:                      return "custom"
        }
    }

    var name: String {
        switch self {
        case .flat(_, let name, _, _):       return name
        case .timeOfUse(_, let name, _, _):  return name
        case .custom:                        return "직접 입력"
        }
    }

    var provider: String {
        switch self {
        case .flat(_, _, let p, _):       return p
        case .timeOfUse(_, _, let p, _):  return p
        case .custom:                     return "기타"
        }
    }

    /// 해당 시점의 요금 (원/kWh)
    func rate(at date: Date) -> Double {
        switch self {
        case .flat(_, _, _, let r):           return r
        case .custom(let r):                  return r
        case .timeOfUse(_, _, _, let slots):
            let cal = Calendar.current
            let month = cal.component(.month, from: date)
            let hour  = cal.component(.hour, from: date)
            let season = Season.of(month: month)
            let s = slots[season] ?? []
            return s.first(where: { $0.contains(hour: hour) })?.rate ?? s.first?.rate ?? 0
        }
    }

    /// 현재 계절 기준 슬롯 (설정 화면 표시용)
    func currentSlots() -> [RateSlot] {
        switch self {
        case .flat(_, _, _, let r):
            return [RateSlot(startHour: 0, endHour: 24, rate: r, label: "단일 단가")]
        case .custom(let r):
            return [RateSlot(startHour: 0, endHour: 24, rate: r, label: "단일 단가")]
        case .timeOfUse(_, _, _, let slots):
            let month = Calendar.current.component(.month, from: Date())
            return slots[Season.of(month: month)] ?? []
        }
    }

    static func == (lhs: ChargingRatePlan, rhs: ChargingRatePlan) -> Bool {
        lhs.id == rhs.id
    }
}

// ─── 사전 정의 요금제 ─────────────────────────────────────────────────────────
// 출처: 한국전력 전기자동차 충전전력요금(자가소비용), 2026.4.16 시행

private let kepcoLowSlots: [Season: [RateSlot]] = [
    .summer: [
        RateSlot(startHour:  0, endHour:  8, rate:  84.3, label: "경부하"),
        RateSlot(startHour:  8, endHour: 15, rate: 172.0, label: "중간부하"),
        RateSlot(startHour: 15, endHour: 21, rate: 259.2, label: "최대부하"),
        RateSlot(startHour: 21, endHour: 22, rate: 172.0, label: "중간부하"),
        RateSlot(startHour: 22, endHour: 24, rate:  84.3, label: "경부하"),
    ],
    .springFall: [
        RateSlot(startHour:  0, endHour:  8, rate:  85.4, label: "경부하"),
        RateSlot(startHour:  8, endHour: 15, rate:  97.2, label: "중간부하"),
        RateSlot(startHour: 15, endHour: 21, rate: 102.1, label: "최대부하"),
        RateSlot(startHour: 21, endHour: 22, rate:  97.2, label: "중간부하"),
        RateSlot(startHour: 22, endHour: 24, rate:  85.4, label: "경부하"),
    ],
    .winter: [
        RateSlot(startHour:  0, endHour:  8, rate: 107.4, label: "경부하"),
        RateSlot(startHour:  8, endHour:  9, rate: 154.9, label: "중간부하"),
        RateSlot(startHour:  9, endHour: 12, rate: 217.5, label: "최대부하"),
        RateSlot(startHour: 12, endHour: 16, rate: 154.9, label: "중간부하"),
        RateSlot(startHour: 16, endHour: 19, rate: 217.5, label: "최대부하"),
        RateSlot(startHour: 19, endHour: 22, rate: 154.9, label: "중간부하"),
        RateSlot(startHour: 22, endHour: 24, rate: 107.4, label: "경부하"),
    ],
]

private let kepcoHighSlots: [Season: [RateSlot]] = [
    .summer: [
        RateSlot(startHour:  0, endHour:  8, rate:  79.2, label: "경부하"),
        RateSlot(startHour:  8, endHour: 15, rate: 137.4, label: "중간부하"),
        RateSlot(startHour: 15, endHour: 21, rate: 190.4, label: "최대부하"),
        RateSlot(startHour: 21, endHour: 22, rate: 137.4, label: "중간부하"),
        RateSlot(startHour: 22, endHour: 24, rate:  79.2, label: "경부하"),
    ],
    .springFall: [
        RateSlot(startHour:  0, endHour:  8, rate:  80.2, label: "경부하"),
        RateSlot(startHour:  8, endHour: 15, rate:  91.0, label: "중간부하"),
        RateSlot(startHour: 15, endHour: 21, rate:  94.9, label: "최대부하"),
        RateSlot(startHour: 21, endHour: 22, rate:  91.0, label: "중간부하"),
        RateSlot(startHour: 22, endHour: 24, rate:  80.2, label: "경부하"),
    ],
    .winter: [
        RateSlot(startHour:  0, endHour:  8, rate:  96.6, label: "경부하"),
        RateSlot(startHour:  8, endHour:  9, rate: 127.7, label: "중간부하"),
        RateSlot(startHour:  9, endHour: 12, rate: 165.5, label: "최대부하"),
        RateSlot(startHour: 12, endHour: 16, rate: 127.7, label: "중간부하"),
        RateSlot(startHour: 16, endHour: 19, rate: 165.5, label: "최대부하"),
        RateSlot(startHour: 19, endHour: 22, rate: 127.7, label: "중간부하"),
        RateSlot(startHour: 22, endHour: 24, rate:  96.6, label: "경부하"),
    ],
]

let predefinedRatePlans: [ChargingRatePlan] = [
    .timeOfUse(id: "kepco_low",  name: "한전 비공용 저압", provider: "한국전력 (가정용 완속)", slots: kepcoLowSlots),
    .timeOfUse(id: "kepco_high", name: "한전 비공용 고압", provider: "한국전력 (상업용 고압)", slots: kepcoHighSlots),
]

func ratePlan(id: String, customRate: Double = 180.0) -> ChargingRatePlan {
    predefinedRatePlans.first(where: { $0.id == id }) ?? .custom(rate: customRate)
}
