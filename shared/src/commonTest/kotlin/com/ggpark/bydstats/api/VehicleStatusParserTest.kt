package com.ggpark.bydstats.api

import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * parseVehicleStatus() / jsonInt() / jsonDouble() 화이트박스 테스트
 *
 * 핵심 버그 재현 및 수정 검증:
 *   - soc/speed 등이 JSON string으로 올 때 intOrNull=null → 0 파싱 실패 (구버그)
 *   - 수정 후 content.toIntOrNull() fallback으로 올바르게 파싱
 */
class VehicleStatusParserTest {

    private fun json(vararg pairs: Pair<String, JsonElement>): JsonObject =
        JsonObject(mapOf(*pairs))

    private fun num(v: Int) = JsonPrimitive(v)
    private fun num(v: Double) = JsonPrimitive(v)
    private fun str(v: String) = JsonPrimitive(v)

    // ─── jsonInt 확장함수 ────────────────────────────────────────────────────

    @Test fun `jsonInt returns value from JSON number`() {
        val r = json("soc" to num(75))
        assertEquals(75, r.jsonInt("soc"))
    }

    @Test fun `jsonInt parses JSON string - 구버그 재현`() {
        // BYD API가 "75" (string)으로 반환 → intOrNull=null → 이전에는 0 반환
        val r = json("soc" to str("75"))
        assertEquals(75, r.jsonInt("soc"), "String 숫자 파싱이 동작해야 함")
    }

    @Test fun `jsonInt falls back to second key`() {
        val r = json("elecPercent" to num(80))
        assertEquals(80, r.jsonInt("soc", "elecPercent"))
    }

    @Test fun `jsonInt falls back to second key as string`() {
        val r = json("elecPercent" to str("80"))
        assertEquals(80, r.jsonInt("soc", "elecPercent"))
    }

    @Test fun `jsonInt returns 0 when key missing`() {
        val r = json("speed" to num(0.0))
        assertEquals(0, r.jsonInt("soc", "elecPercent"))
    }

    // ─── jsonDouble 확장함수 ─────────────────────────────────────────────────

    @Test fun `jsonDouble returns value from JSON number`() {
        val r = json("speed" to num(60.0))
        assertEquals(60.0, r.jsonDouble("speed"), 0.001)
    }

    @Test fun `jsonDouble parses JSON string`() {
        val r = json("speed" to str("60.5"))
        assertEquals(60.5, r.jsonDouble("speed"), 0.001)
    }

    @Test fun `jsonDouble falls back to second key`() {
        val r = json("enduranceMileage" to num(320.0))
        assertEquals(320.0, r.jsonDouble("mileageEV", "enduranceMileage"), 0.001)
    }

    @Test fun `jsonDouble returns 0 when missing`() {
        val r = json("soc" to num(50))
        assertEquals(0.0, r.jsonDouble("speed"))
    }

    // ─── parseVehicleStatus — soc 파싱 ──────────────────────────────────────

    @Test fun `soc as JSON number`() {
        val s = parseVehicleStatus(json("soc" to num(75)))
        assertEquals(75, s.batteryPercentage)
    }

    @Test fun `soc as JSON string - 구버그 재현`() {
        val s = parseVehicleStatus(json("soc" to str("75")))
        assertEquals(75, s.batteryPercentage, "String soc가 올바르게 파싱돼야 함")
    }

    @Test fun `elecPercent fallback as number`() {
        val s = parseVehicleStatus(json("elecPercent" to num(80)))
        assertEquals(80, s.batteryPercentage)
    }

    @Test fun `elecPercent fallback as string`() {
        val s = parseVehicleStatus(json("elecPercent" to str("80")))
        assertEquals(80, s.batteryPercentage)
    }

    @Test fun `soc and elecPercent both missing defaults to 0`() {
        val s = parseVehicleStatus(json("speed" to num(0.0)))
        assertEquals(0, s.batteryPercentage)
    }

    // ─── parseVehicleStatus — powerGear / isDriving ──────────────────────────

    @Test fun `powerGear 3 as number = isDriving`() {
        val s = parseVehicleStatus(json("soc" to num(50), "powerGear" to num(3)))
        assertEquals(3, s.powerGear)
        assertTrue(s.isDriving)
    }

    @Test fun `powerGear 3 as string = isDriving`() {
        val s = parseVehicleStatus(json("soc" to num(50), "powerGear" to str("3")))
        assertEquals(3, s.powerGear)
        assertTrue(s.isDriving)
    }

    @Test fun `powerGear missing defaults to -1`() {
        val s = parseVehicleStatus(json("soc" to num(50)))
        assertEquals(-1, s.powerGear)
        assertFalse(s.isDriving)
    }

    // ─── parseVehicleStatus — speed ──────────────────────────────────────────

    @Test fun `speed as number`() {
        val s = parseVehicleStatus(json("soc" to num(50), "speed" to num(60.0)))
        assertEquals(60.0, s.speed, 0.001)
        assertTrue(s.isDriving)
    }

    @Test fun `speed as string`() {
        val s = parseVehicleStatus(json("soc" to num(50), "speed" to str("60.0")))
        assertEquals(60.0, s.speed, 0.001)
        assertTrue(s.isDriving)
    }

    // ─── parseVehicleStatus — gl (instantPowerW) ─────────────────────────────

    @Test fun `gl as number = isCharging`() {
        val s = parseVehicleStatus(json(
            "soc" to num(50), "gl" to num(7400.0), "powerGear" to num(1)
        ))
        assertEquals(7400.0, s.instantPowerW, 0.001)
        assertTrue(s.isCharging)
    }

    @Test fun `gl as string = isCharging`() {
        val s = parseVehicleStatus(json(
            "soc" to num(50), "gl" to str("7400"), "powerGear" to num(1)
        ))
        assertTrue(s.instantPowerW > 0)
        assertTrue(s.isCharging)
    }

    // ─── parseVehicleStatus — drivingRange ───────────────────────────────────

    @Test fun `mileageEV takes priority over enduranceMileage`() {
        val s = parseVehicleStatus(json(
            "soc" to num(50), "mileageEV" to num(350.0), "enduranceMileage" to num(100.0)
        ))
        assertEquals(350.0, s.drivingRange, 0.001)
    }

    @Test fun `enduranceMileage used when mileageEV absent`() {
        val s = parseVehicleStatus(json("soc" to num(50), "enduranceMileage" to num(280.0)))
        assertEquals(280.0, s.drivingRange, 0.001)
    }

    // ─── parseVehicleStatus — 잠금 상태 ──────────────────────────────────────

    @Test fun `all doors value 2 = isLocked`() {
        val s = parseVehicleStatus(json(
            "soc" to num(50),
            "leftFrontDoorLock" to num(2), "rightFrontDoorLock" to num(2),
            "leftRearDoorLock" to num(2), "rightRearDoorLock" to num(2),
        ))
        assertTrue(s.isLocked)
    }

    @Test fun `one door not 2 = not isLocked`() {
        val s = parseVehicleStatus(json(
            "soc" to num(50),
            "leftFrontDoorLock" to num(2), "rightFrontDoorLock" to num(1),
            "leftRearDoorLock" to num(2), "rightRearDoorLock" to num(2),
        ))
        assertFalse(s.isLocked)
    }

    @Test fun `all doors missing = not isLocked`() {
        val s = parseVehicleStatus(json("soc" to num(50)))
        assertFalse(s.isLocked)
    }

    // ─── parseVehicleStatus — 실내 온도 ──────────────────────────────────────

    @Test fun `tempInCar in normal range`() {
        val s = parseVehicleStatus(json("soc" to num(50), "tempInCar" to num(22.5)))
        assertEquals(22.5, s.interiorTemperature, 0.001)
    }

    @Test fun `interiorTemp takes priority over tempInCar`() {
        val s = parseVehicleStatus(json(
            "soc" to num(50), "interiorTemp" to num(25.0), "tempInCar" to num(22.0)
        ))
        assertEquals(25.0, s.interiorTemperature, 0.001)
    }

    @Test fun `tempInCar out of range (-129) zeroed`() {
        val s = parseVehicleStatus(json("soc" to num(50), "tempInCar" to num(-129.0)))
        assertEquals(0.0, s.interiorTemperature)
    }

    @Test fun `tempInCar out of range (200) zeroed`() {
        val s = parseVehicleStatus(json("soc" to num(50), "tempInCar" to num(200.0)))
        assertEquals(0.0, s.interiorTemperature)
    }
}
