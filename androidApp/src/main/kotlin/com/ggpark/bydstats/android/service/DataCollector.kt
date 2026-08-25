package com.ggpark.bydstats.android.service

import android.util.Log
import com.ggpark.bydstats.android.data.AppDatabase
import com.ggpark.bydstats.android.service.AppLogger
import com.ggpark.bydstats.api.BydApiClient
import com.ggpark.bydstats.api.BydError
import com.ggpark.bydstats.model.VehicleStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.UnknownHostException

private const val TAG = "DataCollector"

class DataCollector(
    private val apiClient: BydApiClient,
    private val db: AppDatabase,
    private val getElectricityRateAt: (Long) -> Double,
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
        detector = SessionDetector(db, getElectricityRateAt, getBatteryCapacityKwh())
        scope.launch {
            detector?.recover()   // recover 완료 후 폴링 시작 (race condition 방지)
            scheduleNextPoll()
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        scope.cancel()
    }

    /** FCM silent push 수신 시 즉시 1회 폴링 */
    suspend fun pollOnce() = doPoll()

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

            // soc=0은 API 준비 미완료로 간주 — 상태 불확실하므로 GPS/세션 처리 없이 건너뜀
            if (status.batteryPercentage == 0) {
                Log.d(TAG, "poll skip: soc=0")
                AppLogger.log("poll skip: soc=0", TAG)
                return
            }

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
            val pollMsg = "poll ok: soc=${status.batteryPercentage} isDriving=$nowDriving powerGear=${status.powerGear} speed=${status.speed} prevDriving=$prevDriving"
            Log.d(TAG, pollMsg)
            AppLogger.log(pollMsg, TAG)
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

        } catch (e: BydError.ControlTimeout) {
            Log.w(TAG, "차량 응답 시간 초과")
            AppLogger.log("poll error: ControlTimeout", TAG)
            _error.value = "차량이 응답하지 않습니다 (절전 모드일 수 있음)"
        } catch (e: BydError.ServerError) {
            Log.w(TAG, "서버 오류 ${e.code}: ${e.msg}")
            AppLogger.log("poll error: ServerError code=${e.code} msg=${e.msg}", TAG)
            _error.value = when (e.code) {
                "1008" -> "차량이 응답하지 않습니다 (절전 모드일 수 있음)"
                else   -> "서버 오류: ${e.msg}"
            }
        } catch (e: UnknownHostException) {
            Log.w(TAG, "DNS 조회 실패: ${e.message}")
            AppLogger.log("poll error: DNS 실패 ${e.message}", TAG)
            _error.value = "네트워크 오류: 서버에 연결할 수 없습니다"
        } catch (e: Exception) {
            Log.e(TAG, "폴링 실패: ${e.message}")
            AppLogger.log("poll error: ${e::class.simpleName} ${e.message}", TAG)
            _error.value = "오류: ${e.message ?: "알 수 없는 오류"}"
        }
    }
}
