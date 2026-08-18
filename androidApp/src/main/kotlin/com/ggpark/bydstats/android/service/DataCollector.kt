package com.ggpark.bydstats.android.service

import android.util.Log
import com.ggpark.bydstats.android.data.AppDatabase
import com.ggpark.bydstats.api.BydApiClient
import com.ggpark.bydstats.api.BydError
import com.ggpark.bydstats.model.VehicleStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "DataCollector"

class DataCollector(
    private val apiClient: BydApiClient,
    private val db: AppDatabase,
    private val getElectricityRate: () -> Double,
    private val getBatteryCapacityKwh: () -> Double,
    private val getParkingIntervalMs: () -> Long,
    private val locationTracker: LocationTracker? = null,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var detector: SessionDetector? = null

    private val _currentStatus = MutableStateFlow<VehicleStatus?>(null)
    val currentStatus: StateFlow<VehicleStatus?> = _currentStatus

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var vin: String? = null

    fun start(vin: String) {
        this.vin = vin
        detector = SessionDetector(db, getElectricityRate(), getBatteryCapacityKwh())
        scope.launch { detector?.recover() }
        scheduleNextPoll()
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun scheduleNextPoll() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            doPoll()
            val status = _currentStatus.value
            val interval = when {
                status?.isDriving == true  -> 60_000L
                status?.isCharging == true -> 120_000L
                else                        -> getParkingIntervalMs()
            }
            delay(interval)
            if (isActive) scheduleNextPoll()
        }
    }

    private suspend fun doPoll() {
        val v = vin ?: return
        try {
            var status = apiClient.fetchVehicleStatus(v)

            // totalMileage == 0이면 Energy API로 ODO 보완
            val wasOrIsDriving = (_currentStatus.value?.isDriving == true) || status.isDriving
            if (wasOrIsDriving && status.totalMileage == 0.0) {
                try {
                    val energy = apiClient.fetchEnergyConsumption(v)
                    if (energy.lifetimeMileageKm > 0) {
                        status = status.copy(totalMileage = energy.lifetimeMileageKm)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Energy API fallback 실패: ${e.message}")
                }
            }

            // GPS 트래킹 제어: 주행 시작/종료 감지
            val prevDriving = _currentStatus.value?.isDriving == true
            val nowDriving = status.isDriving
            val gpsDistanceKm: Double = when {
                nowDriving && !prevDriving -> {
                    locationTracker?.startTracking()
                    0.0
                }
                !nowDriving && prevDriving -> {
                    locationTracker?.stopTracking() ?: 0.0
                }
                else -> 0.0
            }

            _currentStatus.value = status
            _error.value = null
            detector?.process(status, System.currentTimeMillis(), gpsDistanceKm)

        } catch (e: BydError.ServerError) {
            Log.w(TAG, "서버 오류 ${e.code}: ${e.msg}")
            _error.value = "서버 오류: ${e.msg}"
        } catch (e: Exception) {
            Log.e(TAG, "폴링 실패: ${e.message}")
            _error.value = e.message
        }
    }
}
