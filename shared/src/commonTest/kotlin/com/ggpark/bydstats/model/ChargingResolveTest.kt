package com.ggpark.bydstats.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChargingResolveTest {

    @Test fun `driving is never charging`() {
        assertFalse(
            resolveIsCharging(
                isDriving = true,
                instantPowerW = 7400.0,
                batteryPercentage = 50,
                previousCharging = false,
                previousSoc = 49,
                apiIsCharging = true,
            )
        )
    }

    @Test fun `gl positive is charging`() {
        assertTrue(
            resolveIsCharging(
                isDriving = false,
                instantPowerW = 500.0,
                batteryPercentage = 50,
                previousCharging = false,
                previousSoc = 50,
                apiIsCharging = false,
            )
        )
    }

    @Test fun `api charging with gl zero`() {
        assertTrue(
            resolveIsCharging(
                isDriving = false,
                instantPowerW = 0.0,
                batteryPercentage = 50,
                previousCharging = false,
                previousSoc = 50,
                apiIsCharging = true,
            )
        )
    }

    @Test fun `api false but previously charging and soc flat keeps charging`() {
        // API false = "불확실"로 취급: 이전 충전 중 + SOC 비하락이면 충전 유지
        assertTrue(
            resolveIsCharging(
                isDriving = false,
                instantPowerW = 0.0,
                batteryPercentage = 50,
                previousCharging = true,
                previousSoc = 50,
                apiIsCharging = false,
            )
        )
    }

    @Test fun `api false and not previously charging and soc flat is not charging`() {
        // 이전에 충전 중이 아니었고 SOC 변화도 없으면 충전 아님
        assertFalse(
            resolveIsCharging(
                isDriving = false,
                instantPowerW = 0.0,
                batteryPercentage = 50,
                previousCharging = false,
                previousSoc = 50,
                apiIsCharging = false,
            )
        )
    }

    @Test fun `api false but soc rose still charging`() {
        assertTrue(
            resolveIsCharging(
                isDriving = false,
                instantPowerW = 0.0,
                batteryPercentage = 51,
                previousCharging = false,
                previousSoc = 50,
                apiIsCharging = false,
            )
        )
    }

    @Test fun `api unknown soc rise starts charging`() {
        assertTrue(
            resolveIsCharging(
                isDriving = false,
                instantPowerW = 0.0,
                batteryPercentage = 41,
                previousCharging = false,
                previousSoc = 40,
                apiIsCharging = null,
            )
        )
    }

    @Test fun `api unknown sticky while soc flat`() {
        assertTrue(
            resolveIsCharging(
                isDriving = false,
                instantPowerW = 0.0,
                batteryPercentage = 80,
                previousCharging = true,
                previousSoc = 80,
                apiIsCharging = null,
            )
        )
    }

    @Test fun `api unknown ends when soc drops`() {
        assertFalse(
            resolveIsCharging(
                isDriving = false,
                instantPowerW = 0.0,
                batteryPercentage = 79,
                previousCharging = true,
                previousSoc = 80,
                apiIsCharging = null,
            )
        )
    }

    @Test fun `withChargingResolved sets reportedCharging`() {
        val parked = VehicleStatus(powerGear = 1, speed = 0.0, instantPowerW = 0.0, batteryPercentage = 50)
        val resolved = parked.withChargingResolved(previous = null, apiIsCharging = true)
        assertTrue(resolved.reportedCharging)
        assertTrue(resolved.isCharging)
    }

    @Test fun `gl zero parked without signals is not charging`() {
        val parked = VehicleStatus(powerGear = 1, speed = 0.0, instantPowerW = 0.0, batteryPercentage = 50)
        val resolved = parked.withChargingResolved(previous = null, apiIsCharging = false)
        assertFalse(resolved.isCharging)
    }
}
