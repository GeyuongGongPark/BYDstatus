package com.ggpark.bydstats.android.widget

import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WidgetDataStoreTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearPrefs() {
        // 각 테스트 전 SharedPrefs 초기화
        context.getSharedPreferences("byd_widget_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ─── 초기값 ───────────────────────────────────────────────────────────────

    @Test
    fun `load returns defaults when nothing saved`() {
        val snap = WidgetDataStore.load(context)
        assertEquals(0, snap.batteryPercent)
        assertFalse(snap.isCharging)
        assertFalse(snap.isDriving)
        assertEquals(0.0, snap.drivingRangeKm)
        assertEquals(0.0, snap.instantPowerKw)
        assertEquals(0.0, snap.monthCostKrw)
        assertEquals(0L, snap.lastUpdated)
    }

    // ─── 저장/로드 왕복 ───────────────────────────────────────────────────────

    @Test
    fun `save and load roundtrip - parked state`() {
        val snap = WidgetSnapshot(
            batteryPercent = 78,
            isCharging = false,
            isDriving = false,
            drivingRangeKm = 320.5,
            instantPowerKw = 0.0,
            monthCostKrw = 12_500.0,
            lastUpdated = 1_000_000L,
        )
        WidgetDataStore.save(context, snap)
        val loaded = WidgetDataStore.load(context)

        assertEquals(78, loaded.batteryPercent)
        assertFalse(loaded.isCharging)
        assertFalse(loaded.isDriving)
        assertEquals(320.5, loaded.drivingRangeKm, 0.01)
        assertEquals(0.0, loaded.instantPowerKw)
        assertEquals(12_500.0, loaded.monthCostKrw, 0.1)
        assertEquals(1_000_000L, loaded.lastUpdated)
    }

    @Test
    fun `save and load roundtrip - charging state`() {
        val snap = WidgetSnapshot(
            batteryPercent = 45,
            isCharging = true,
            isDriving = false,
            drivingRangeKm = 185.0,
            instantPowerKw = 11.2,
            monthCostKrw = 3_200.0,
            lastUpdated = 2_000_000L,
        )
        WidgetDataStore.save(context, snap)
        val loaded = WidgetDataStore.load(context)

        assertEquals(45, loaded.batteryPercent)
        assertTrue(loaded.isCharging)
        assertFalse(loaded.isDriving)
        assertEquals(11.2, loaded.instantPowerKw, 0.01)
    }

    @Test
    fun `save and load roundtrip - driving state`() {
        val snap = WidgetSnapshot(
            batteryPercent = 62,
            isCharging = false,
            isDriving = true,
            drivingRangeKm = 250.0,
            instantPowerKw = 0.0,
            lastUpdated = 3_000_000L,
        )
        WidgetDataStore.save(context, snap)
        val loaded = WidgetDataStore.load(context)

        assertTrue(loaded.isDriving)
        assertFalse(loaded.isCharging)
        assertEquals(62, loaded.batteryPercent)
    }

    // ─── 경계값 ───────────────────────────────────────────────────────────────

    @Test
    fun `battery percent boundary - 0`() {
        WidgetDataStore.save(context, WidgetSnapshot(batteryPercent = 0))
        assertEquals(0, WidgetDataStore.load(context).batteryPercent)
    }

    @Test
    fun `battery percent boundary - 100`() {
        WidgetDataStore.save(context, WidgetSnapshot(batteryPercent = 100))
        assertEquals(100, WidgetDataStore.load(context).batteryPercent)
    }

    @Test
    fun `overwrite previous save`() {
        WidgetDataStore.save(context, WidgetSnapshot(batteryPercent = 50))
        WidgetDataStore.save(context, WidgetSnapshot(batteryPercent = 75))
        assertEquals(75, WidgetDataStore.load(context).batteryPercent)
    }

    @Test
    fun `large monthCost value preserved`() {
        val cost = 99_999.9
        WidgetDataStore.save(context, WidgetSnapshot(monthCostKrw = cost))
        val loaded = WidgetDataStore.load(context)
        assertEquals(cost, loaded.monthCostKrw, 1.0)  // Float precision 허용
    }
}
