package com.ggpark.bydstats.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class VehicleStatusTest {

    // ─── isDriving ───────────────────────────────────────────────────────────

    @Test fun `isDriving when powerGear is 3`() {
        val s = VehicleStatus(powerGear = 3, speed = 0.0)
        assertTrue(s.isDriving)
    }

    @Test fun `isDriving when speed is positive`() {
        val s = VehicleStatus(powerGear = 1, speed = 0.1)
        assertTrue(s.isDriving)
    }

    @Test fun `isDriving when both powerGear 3 and speed positive`() {
        val s = VehicleStatus(powerGear = 3, speed = 50.0)
        assertTrue(s.isDriving)
    }

    @Test fun `not isDriving when powerGear is 1 and speed is 0`() {
        val s = VehicleStatus(powerGear = 1, speed = 0.0)
        assertFalse(s.isDriving)
    }

    @Test fun `not isDriving when powerGear is unknown (-1)`() {
        val s = VehicleStatus(powerGear = -1, speed = 0.0)
        assertFalse(s.isDriving)
    }

    @Test fun `isDriving boundary - speed exactly 0 is not driving`() {
        val s = VehicleStatus(powerGear = 1, speed = 0.0)
        assertFalse(s.isDriving)
    }

    // ─── isCharging ──────────────────────────────────────────────────────────

    @Test fun `isCharging when power positive and not driving`() {
        val s = VehicleStatus(powerGear = 1, speed = 0.0, instantPowerW = 500.0)
        assertTrue(s.isCharging)
    }

    @Test fun `not isCharging when driving even with positive power`() {
        // 회생제동 등으로 gl > 0이어도 주행 중이면 충전 아님
        val s = VehicleStatus(powerGear = 3, speed = 50.0, instantPowerW = 500.0)
        assertFalse(s.isCharging)
    }

    @Test fun `not isCharging when power is zero`() {
        val s = VehicleStatus(powerGear = 1, speed = 0.0, instantPowerW = 0.0)
        assertFalse(s.isCharging)
    }

    @Test fun `not isCharging when power is negative`() {
        val s = VehicleStatus(powerGear = 1, speed = 0.0, instantPowerW = -100.0)
        assertFalse(s.isCharging)
    }

    @Test fun `not isCharging when parked with no power`() {
        val s = VehicleStatus(powerGear = 1, speed = 0.0, instantPowerW = 0.0)
        assertFalse(s.isCharging)
        assertFalse(s.isDriving)
    }

    // ─── instantPowerKw ──────────────────────────────────────────────────────

    @Test fun `instantPowerKw converts watts to kilowatts`() {
        val s = VehicleStatus(instantPowerW = 7400.0)
        assertEquals(7.4, s.instantPowerKw, absoluteTolerance = 0.001)
    }

    @Test fun `instantPowerKw is 0 when power is 0`() {
        val s = VehicleStatus(instantPowerW = 0.0)
        assertEquals(0.0, s.instantPowerKw)
    }

    @Test fun `instantPowerKw handles large DC fast charging values`() {
        val s = VehicleStatus(instantPowerW = 100_000.0) // 100 kW DC
        assertEquals(100.0, s.instantPowerKw, absoluteTolerance = 0.001)
    }

    // ─── 복합 시나리오 ───────────────────────────────────────────────────────

    @Test fun `parked state - all false`() {
        val s = VehicleStatus(powerGear = 1, speed = 0.0, instantPowerW = 0.0)
        assertFalse(s.isDriving)
        assertFalse(s.isCharging)
    }

    @Test fun `driving state - isDriving true, isCharging false`() {
        val s = VehicleStatus(powerGear = 3, speed = 60.0, instantPowerW = 0.0)
        assertTrue(s.isDriving)
        assertFalse(s.isCharging)
    }

    @Test fun `charging state - isCharging true, isDriving false`() {
        val s = VehicleStatus(powerGear = 1, speed = 0.0, instantPowerW = 11_000.0)
        assertFalse(s.isDriving)
        assertTrue(s.isCharging)
    }
}
