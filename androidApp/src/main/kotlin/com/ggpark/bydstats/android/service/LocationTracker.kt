package com.ggpark.bydstats.android.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class LocationTracker(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null
    private var lastLocation: Location? = null
    private var accumulatedDistanceKm = 0.0

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (!hasPermission) {
            AppLogger.log("위치 권한 없음 — GPS 트래킹 스킵", tag = "GPS")
            return
        }
        accumulatedDistanceKm = 0.0
        lastLocation = null

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val new = result.lastLocation ?: return
                if (new.accuracy > 50f) return          // 정확도 낮으면 무시

                lastLocation?.let { last ->
                    val delta = new.distanceTo(last) / 1000.0
                    if (delta < 1.0) {                  // 1km 이상 점프는 노이즈
                        accumulatedDistanceKm += delta
                    }
                }
                lastLocation = new
            }
        }

        client.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
        AppLogger.log("GPS 트래킹 시작", tag = "GPS")
    }

    fun stopTracking(): Double {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        val dist = accumulatedDistanceKm
        accumulatedDistanceKm = 0.0
        lastLocation = null
        AppLogger.log("GPS 트래킹 종료 — 누적 ${String.format("%.2f", dist)} km", tag = "GPS")
        return dist
    }
}
