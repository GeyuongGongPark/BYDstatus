package com.ggpark.bydstats.android.data

import java.util.Calendar

// ─── 계절 ───────────────────────────────────────────────────────────────────

enum class Season {
    SUMMER,      // 6-8월
    SPRING_FALL, // 3-5월, 9-10월
    WINTER;      // 11-2월

    companion object {
        fun of(month: Int): Season = when (month) {
            6, 7, 8       -> SUMMER
            11, 12, 1, 2  -> WINTER
            else          -> SPRING_FALL
        }
    }
}

// ─── 시간대 슬롯 ─────────────────────────────────────────────────────────────

data class RateSlot(
    val startHour: Int,  // inclusive
    val endHour: Int,    // exclusive (24 = 자정)
    val rate: Double,    // 원/kWh
    val label: String,
) {
    /** hour가 이 슬롯에 포함되는지 확인 */
    fun contains(hour: Int): Boolean =
        if (startHour < endHour) hour in startHour until endHour
        else hour >= startHour || hour < endHour  // 자정 넘기는 구간 (예: 22-08)
}

// ─── 요금제 ──────────────────────────────────────────────────────────────────

sealed class ChargingRatePlan {
    abstract val id: String
    abstract val name: String
    abstract val provider: String

    /** 해당 epoch ms 시점의 요금(원/kWh) */
    abstract fun rateAt(timestampMs: Long): Double

    /** 설정 화면 표시용: 현재 계절 기준 슬롯 */
    abstract fun currentSlots(): List<RateSlot>

    // 단일 단가
    data class Flat(
        override val id: String,
        override val name: String,
        override val provider: String,
        val rate: Double,
    ) : ChargingRatePlan() {
        override fun rateAt(timestampMs: Long) = rate
        override fun currentSlots() = listOf(RateSlot(0, 24, rate, "단일 단가"))
    }

    // 계절별 시간대 차등
    data class TimeOfUse(
        override val id: String,
        override val name: String,
        override val provider: String,
        val seasonalSlots: Map<Season, List<RateSlot>>,
    ) : ChargingRatePlan() {
        override fun rateAt(timestampMs: Long): Double {
            val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
            val month = cal.get(Calendar.MONTH) + 1
            val hour  = cal.get(Calendar.HOUR_OF_DAY)
            val slots = seasonalSlots[Season.of(month)] ?: return 0.0
            return slots.firstOrNull { it.contains(hour) }?.rate ?: slots.first().rate
        }

        override fun currentSlots(): List<RateSlot> {
            val month = Calendar.getInstance().get(Calendar.MONTH) + 1
            return seasonalSlots[Season.of(month)] ?: emptyList()
        }
    }

    // 직접 입력
    class Custom(val rate: Double) : ChargingRatePlan() {
        override val id = "custom"
        override val name = "직접 입력"
        override val provider = "기타"
        override fun rateAt(timestampMs: Long) = rate
        override fun currentSlots() = listOf(RateSlot(0, 24, rate, "단일 단가"))
    }
}

// ─── 사전 정의 요금제 ─────────────────────────────────────────────────────────
// 출처: 한국전력 전기자동차 충전전력요금(자가소비용), 2026.4.16 시행

// 한전 비공용 저압 (가정용 비공용 완속)
private val KEPCO_LOW_SLOTS = mapOf(
    Season.SUMMER to listOf(
        RateSlot(0, 8,  84.3, "경부하"),   // 00~08
        RateSlot(8, 15, 172.0, "중간부하"), // 08~15
        RateSlot(15, 21, 259.2, "최대부하"), // 15~21
        RateSlot(21, 22, 172.0, "중간부하"), // 21~22
        RateSlot(22, 24, 84.3, "경부하"),   // 22~24
    ),
    Season.SPRING_FALL to listOf(
        RateSlot(0, 8,  85.4, "경부하"),
        RateSlot(8, 15, 97.2, "중간부하"),
        RateSlot(15, 21, 102.1, "최대부하"),
        RateSlot(21, 22, 97.2, "중간부하"),
        RateSlot(22, 24, 85.4, "경부하"),
    ),
    Season.WINTER to listOf(
        RateSlot(0, 8,  107.4, "경부하"),
        RateSlot(8, 9,  154.9, "중간부하"), // 08~09
        RateSlot(9, 12, 217.5, "최대부하"), // 09~12
        RateSlot(12, 16, 154.9, "중간부하"), // 12~16
        RateSlot(16, 19, 217.5, "최대부하"), // 16~19
        RateSlot(19, 22, 154.9, "중간부하"), // 19~22
        RateSlot(22, 24, 107.4, "경부하"),
    ),
)

// 한전 비공용 고압
private val KEPCO_HIGH_SLOTS = mapOf(
    Season.SUMMER to listOf(
        RateSlot(0, 8,  79.2, "경부하"),
        RateSlot(8, 15, 137.4, "중간부하"),
        RateSlot(15, 21, 190.4, "최대부하"),
        RateSlot(21, 22, 137.4, "중간부하"),
        RateSlot(22, 24, 79.2, "경부하"),
    ),
    Season.SPRING_FALL to listOf(
        RateSlot(0, 8,  80.2, "경부하"),
        RateSlot(8, 15, 91.0, "중간부하"),
        RateSlot(15, 21, 94.9, "최대부하"),
        RateSlot(21, 22, 91.0, "중간부하"),
        RateSlot(22, 24, 80.2, "경부하"),
    ),
    Season.WINTER to listOf(
        RateSlot(0, 8,  96.6, "경부하"),
        RateSlot(8, 9,  127.7, "중간부하"),
        RateSlot(9, 12, 165.5, "최대부하"),
        RateSlot(12, 16, 127.7, "중간부하"),
        RateSlot(16, 19, 165.5, "최대부하"),
        RateSlot(19, 22, 127.7, "중간부하"),
        RateSlot(22, 24, 96.6, "경부하"),
    ),
)

val PREDEFINED_RATE_PLANS: List<ChargingRatePlan> = listOf(
    ChargingRatePlan.TimeOfUse(
        id = "kepco_low",
        name = "한전 비공용 저압",
        provider = "한국전력 (가정용 완속)",
        seasonalSlots = KEPCO_LOW_SLOTS,
    ),
    ChargingRatePlan.TimeOfUse(
        id = "kepco_high",
        name = "한전 비공용 고압",
        provider = "한국전력 (상업용 고압)",
        seasonalSlots = KEPCO_HIGH_SLOTS,
    ),
)

fun ratePlanById(id: String, customRate: Double = 180.0): ChargingRatePlan =
    PREDEFINED_RATE_PLANS.firstOrNull { it.id == id } ?: ChargingRatePlan.Custom(customRate)
