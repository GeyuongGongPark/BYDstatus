package com.ggpark.bydstats.android.widget

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "byd_widget_prefs"
private const val KEY_BATTERY = "battery_pct"
private const val KEY_IS_CHARGING = "is_charging"
private const val KEY_IS_DRIVING = "is_driving"
private const val KEY_RANGE_KM = "range_km"
private const val KEY_POWER_KW = "power_kw"
private const val KEY_MONTH_COST = "month_cost"
private const val KEY_UPDATED = "last_updated"

data class WidgetSnapshot(
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val isDriving: Boolean = false,
    val drivingRangeKm: Double = 0.0,
    val instantPowerKw: Double = 0.0,
    val monthCostKrw: Double = 0.0,
    val lastUpdated: Long = 0L,
)

object WidgetDataStore {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, snapshot: WidgetSnapshot) {
        prefs(context).edit().apply {
            putInt(KEY_BATTERY, snapshot.batteryPercent)
            putBoolean(KEY_IS_CHARGING, snapshot.isCharging)
            putBoolean(KEY_IS_DRIVING, snapshot.isDriving)
            putFloat(KEY_RANGE_KM, snapshot.drivingRangeKm.toFloat())
            putFloat(KEY_POWER_KW, snapshot.instantPowerKw.toFloat())
            putFloat(KEY_MONTH_COST, snapshot.monthCostKrw.toFloat())
            putLong(KEY_UPDATED, snapshot.lastUpdated)
            apply()
        }
    }

    fun load(context: Context): WidgetSnapshot {
        val p = prefs(context)
        return WidgetSnapshot(
            batteryPercent = p.getInt(KEY_BATTERY, 0),
            isCharging     = p.getBoolean(KEY_IS_CHARGING, false),
            isDriving      = p.getBoolean(KEY_IS_DRIVING, false),
            drivingRangeKm = p.getFloat(KEY_RANGE_KM, 0f).toDouble(),
            instantPowerKw = p.getFloat(KEY_POWER_KW, 0f).toDouble(),
            monthCostKrw   = p.getFloat(KEY_MONTH_COST, 0f).toDouble(),
            lastUpdated    = p.getLong(KEY_UPDATED, 0L),
        )
    }
}
