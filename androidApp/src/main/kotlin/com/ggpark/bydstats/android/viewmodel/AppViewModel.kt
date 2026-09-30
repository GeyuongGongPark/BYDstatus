package com.ggpark.bydstats.android.viewmodel

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ggpark.bydstats.android.BuildConfig
import com.ggpark.bydstats.android.BydStatsApp
import com.ggpark.bydstats.android.appDataStore
import com.ggpark.bydstats.android.data.AppDatabase
import com.ggpark.bydstats.android.data.entity.ChargingSessionEntity
import com.ggpark.bydstats.android.data.entity.DataPointEntity
import com.ggpark.bydstats.android.data.entity.DrivingSessionEntity
import com.ggpark.bydstats.android.service.PollingService
import com.ggpark.bydstats.android.service.SecureStorage
import com.ggpark.bydstats.android.service.PushRegistrar
import com.google.firebase.messaging.FirebaseMessaging
import com.ggpark.bydstats.api.BydApiClient
import com.ggpark.bydstats.api.BydConfig
import com.ggpark.bydstats.api.BydError
import com.ggpark.bydstats.model.VehicleListItem
import com.ggpark.bydstats.model.VehicleStatus
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private object PrefKeys {
    val REGION           = stringPreferencesKey("region")
    val VIN              = stringPreferencesKey("vin")
    val ELECTRICITY_RATE = stringPreferencesKey("electricity_rate")
    val BATTERY_CAPACITY = stringPreferencesKey("battery_capacity")
    val VEHICLE_MODEL    = stringPreferencesKey("vehicle_model")
    val POLLING_INTERVAL = stringPreferencesKey("polling_interval")
    val USER_ID          = stringPreferencesKey("user_id")
    val SIGN_TOKEN       = stringPreferencesKey("sign_token")
    val ENCRY_TOKEN      = stringPreferencesKey("encry_token")
    val RATE_PLAN_ID     = stringPreferencesKey("rate_plan_id")
    val CUSTOM_RATE      = stringPreferencesKey("custom_rate")
}

data class AppSettings(
    val username: String = "",
    val password: String = "",
    val region: String = "KR",
    val vin: String = "",
    val electricityRate: Double = 180.0,
    val vehicleModel: String = "아토 3",
    val batteryCapacityKwh: Double = 60.48,
    val pollingIntervalMin: Int = 5,
    val ratePlanId: String = "kepco_low",
)

data class AppUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val status: VehicleStatus? = null,
    val pollingError: String? = null,
    val vehicles: List<VehicleListItem> = emptyList(),
    val isDemoMode: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AppDatabase.getInstance(context)
    private var apiClient: BydApiClient? = null

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dataPoints: Flow<List<DataPointEntity>> = _uiState
        .flatMapLatest { if (it.isDemoMode) flowOf(buildDemoDataPoints()) else db.dataPointDao().allFlow() }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chargingSessions: Flow<List<ChargingSessionEntity>> = _uiState
        .flatMapLatest { if (it.isDemoMode) flowOf(buildDemoChargingSessions()) else db.chargingSessionDao().allFlow() }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val drivingSessions: Flow<List<DrivingSessionEntity>> = _uiState
        .flatMapLatest { if (it.isDemoMode) flowOf(buildDemoDrivingSessions()) else db.drivingSessionDao().allFlow() }

    init {
        observeServiceStatus()
        viewModelScope.launch { loadSettings() }
    }

    // MARK: - 서비스 상태 구독

    private fun observeServiceStatus() {
        val app = getApplication<BydStatsApp>()
        viewModelScope.launch {
            combine(app.statusFlow, app.errorFlow) { s, e -> s to e }
                .collect { (status, err) ->
                    _uiState.update { it.copy(status = status, pollingError = err) }
                }
        }
    }

    // MARK: - Settings

    private suspend fun loadSettings() {
        val prefs = context.appDataStore.data.first()
        val s = AppSettings(
            username           = SecureStorage.get(context, SecureStorage.KEY_USERNAME) ?: "",
            password           = SecureStorage.get(context, SecureStorage.KEY_PASSWORD) ?: "",
            region             = prefs[PrefKeys.REGION] ?: "KR",
            vin                = prefs[PrefKeys.VIN] ?: "",
            electricityRate    = prefs[PrefKeys.CUSTOM_RATE]?.toDoubleOrNull()
                                    ?: prefs[PrefKeys.ELECTRICITY_RATE]?.toDoubleOrNull() ?: 180.0,
            vehicleModel       = prefs[PrefKeys.VEHICLE_MODEL] ?: "아토 3",
            batteryCapacityKwh = prefs[PrefKeys.BATTERY_CAPACITY]?.toDoubleOrNull() ?: 60.48,
            pollingIntervalMin = prefs[PrefKeys.POLLING_INTERVAL]?.toIntOrNull() ?: 5,
            ratePlanId         = prefs[PrefKeys.RATE_PLAN_ID] ?: "kepco_low",
        )
        _settings.value = s

        if (s.username.isEmpty() || s.password.isEmpty()) {
            _uiState.value = AppUiState(isLoading = false, isLoggedIn = false)
            return
        }

        initApiClient(s)
        val userId     = prefs[PrefKeys.USER_ID] ?: ""
        val signToken  = prefs[PrefKeys.SIGN_TOKEN] ?: ""
        val encryToken = prefs[PrefKeys.ENCRY_TOKEN] ?: ""
        if (userId.isNotEmpty() && signToken.isNotEmpty()) {
            apiClient?.restoreSession(userId, signToken, encryToken)
        }

        _uiState.value = AppUiState(isLoading = false, isLoggedIn = true)

        registerFcmToken()
        if (s.vin.isNotEmpty()) PollingService.start(context)
    }

    private fun initApiClient(settings: AppSettings) {
        apiClient?.close()
        val config = BydConfig.fromRegion(settings.region)
        val tableData = context.assets.open("bangcle_tables.bin").readBytes()
        val httpClient = HttpClient(Android) {
            engine { connectTimeout = 120_000; socketTimeout = 120_000 }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        apiClient = BydApiClient(config, tableData, httpClient).also { client ->
            client.setCredentials(settings.username, settings.password)
            client.onSessionUpdated = { uid, sign, encry ->
                viewModelScope.launch {
                    context.appDataStore.edit { p ->
                        p[PrefKeys.USER_ID]    = uid
                        p[PrefKeys.SIGN_TOKEN]  = sign
                        p[PrefKeys.ENCRY_TOKEN] = encry
                    }
                }
            }
            client.onSessionExpired = {
                _uiState.update { it.copy(isLoggedIn = false) }
            }
        }
    }

    // MARK: - Login

    fun login(username: String, password: String, region: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
            val newSettings = _settings.value.copy(username = username, password = password, region = region)
            _settings.value = newSettings
            saveCredentials(newSettings)
            initApiClient(newSettings)

            try {
                apiClient!!.login(username, password)
                val vehicles = apiClient!!.fetchVehicleList()
                val vin = vehicles.firstOrNull()?.vin ?: ""
                if (vin.isNotEmpty()) saveSetting(PrefKeys.VIN, vin)
                updateSettings { it.copy(vin = vin) }
                _uiState.update {
                    it.copy(isLoggingIn = false, isLoggedIn = true, loginError = null, vehicles = vehicles)
                }
                if (vin.isNotEmpty()) PollingService.start(context)
                registerFcmToken()
            } catch (e: BydError.ServerError) {
                _uiState.update { it.copy(isLoggingIn = false, loginError = "로그인 실패: ${e.msg}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoggingIn = false, loginError = "오류: ${e.message}") }
            }
        }
    }

    fun selectVin(vin: String) {
        viewModelScope.launch {
            saveSetting(PrefKeys.VIN, vin)
            updateSettings { it.copy(vin = vin) }
            PollingService.restart(context)
        }
    }

    // MARK: - Demo Mode

    fun enterDemoMode() {
        val demoStatus = VehicleStatus(
            batteryPercentage = 72,
            drivingRange      = 350.0,
            instantPowerW     = 7200.0,  // 7.2 kW 완속 충전 중
            totalMileage      = 12_480.0,
            reportedCharging  = true,
            reportedDriving   = false,
        )
        _uiState.update { it.copy(isDemoMode = true, isLoggedIn = true, status = demoStatus, pollingError = null) }
    }

    fun exitDemoMode() {
        _uiState.update { it.copy(isDemoMode = false, isLoggedIn = false, status = null) }
    }

    // MARK: - Settings Update

    fun updateRatePlan(planId: String) {
        viewModelScope.launch {
            saveSetting(PrefKeys.RATE_PLAN_ID, planId)
            updateSettings { it.copy(ratePlanId = planId) }
            PollingService.restart(context)
        }
    }

    fun updateElectricityRate(rate: Double) {
        viewModelScope.launch {
            saveSetting(PrefKeys.CUSTOM_RATE, rate.toString())
            updateSettings { it.copy(electricityRate = rate) }
            PollingService.restart(context)
        }
    }

    fun updateVehicle(name: String, kwh: Double) {
        viewModelScope.launch {
            saveSetting(PrefKeys.VEHICLE_MODEL, name)
            saveSetting(PrefKeys.BATTERY_CAPACITY, kwh.toString())
            updateSettings { it.copy(vehicleModel = name, batteryCapacityKwh = kwh) }
            PollingService.restart(context)
        }
    }

    fun updatePollingInterval(minutes: Int) {
        viewModelScope.launch {
            saveSetting(PrefKeys.POLLING_INTERVAL, minutes.toString())
            updateSettings { it.copy(pollingIntervalMin = minutes) }
            PollingService.restart(context)
        }
    }

    fun logout() {
        viewModelScope.launch {
            unregisterFcmToken()
            PollingService.stop(context)
            SecureStorage.clear(context)
            context.appDataStore.edit { it.clear() }
            _settings.value = AppSettings()
            _uiState.value = AppUiState(isLoading = false, isLoggedIn = false)
        }
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            viewModelScope.launch { PushRegistrar.register(context, token) }
        }
    }

    private fun unregisterFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            viewModelScope.launch { PushRegistrar.unregister(token) }
        }
    }

    // MARK: - DB Operations

    suspend fun deleteChargingSession(session: ChargingSessionEntity) = db.chargingSessionDao().delete(session)
    suspend fun updateChargingSession(session: ChargingSessionEntity) = db.chargingSessionDao().update(session)
    suspend fun deleteDrivingSession(session: DrivingSessionEntity)   = db.drivingSessionDao().delete(session)
    suspend fun updateDrivingSession(session: DrivingSessionEntity)   = db.drivingSessionDao().update(session)

    // MARK: - Demo Data

    private fun buildDemoChargingSessions(): List<ChargingSessionEntity> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val min = 60_000L
        return listOf(
            ChargingSessionEntity(id=1, startTime=now-1*day+8*3600_000L, endTime=now-1*day+8*3600_000L+96*min, startSoc=20, endSoc=80, energyKwh=36.4, durationMinutes=96,  estimatedCostKrw=7_280.0),
            ChargingSessionEntity(id=2, startTime=now-3*day+9*3600_000L, endTime=now-3*day+9*3600_000L+88*min, startSoc=35, endSoc=90, energyKwh=33.3, durationMinutes=88,  estimatedCostKrw=6_660.0),
            ChargingSessionEntity(id=3, startTime=now-5*day+7*3600_000L, endTime=now-5*day+7*3600_000L+144*min, startSoc=10, endSoc=100, energyKwh=54.5, durationMinutes=144, estimatedCostKrw=10_900.0),
            ChargingSessionEntity(id=4, startTime=now-10*day+8*3600_000L, endTime=now-10*day+8*3600_000L+57*min, startSoc=25, endSoc=60, energyKwh=21.4, durationMinutes=57, estimatedCostKrw=7_610.0),
        )
    }

    private fun buildDemoDrivingSessions(): List<DrivingSessionEntity> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val min = 60_000L
        return listOf(
            DrivingSessionEntity(id=1, startTime=now-2*day+10*3600_000L, endTime=now-2*day+10*3600_000L+50*min,  startSoc=55, endSoc=42, energyKwh=8.0,  distanceKm=100.0, efficiencyKmPerKwh=12.5,  startOdometer=12_380.0, endOdometer=12_480.0),
            DrivingSessionEntity(id=2, startTime=now-4*day+9*3600_000L,  endTime=now-4*day+9*3600_000L+75*min,   startSoc=70, endSoc=50, energyKwh=12.0, distanceKm=150.0, efficiencyKmPerKwh=12.5,  startOdometer=12_230.0, endOdometer=12_380.0),
            DrivingSessionEntity(id=3, startTime=now-6*day+8*3600_000L,  endTime=now-6*day+8*3600_000L+96*min,   startSoc=65, endSoc=39, energyKwh=16.0, distanceKm=200.0, efficiencyKmPerKwh=12.5,  startOdometer=12_030.0, endOdometer=12_230.0),
            DrivingSessionEntity(id=4, startTime=now-9*day+11*3600_000L, endTime=now-9*day+11*3600_000L+86*min,  startSoc=78, endSoc=54, energyKwh=14.4, distanceKm=180.0, efficiencyKmPerKwh=12.5,  startOdometer=11_850.0, endOdometer=12_030.0),
            DrivingSessionEntity(id=5, startTime=now-12*day+9*3600_000L, endTime=now-12*day+9*3600_000L+75*min,  startSoc=90, endSoc=70, energyKwh=12.0, distanceKm=150.0, efficiencyKmPerKwh=12.5,  startOdometer=11_700.0, endOdometer=11_850.0),
            DrivingSessionEntity(id=6, startTime=now-17*day+8*3600_000L, endTime=now-17*day+8*3600_000L+100*min, startSoc=72, endSoc=44, energyKwh=16.8, distanceKm=210.0, efficiencyKmPerKwh=12.5,  startOdometer=11_490.0, endOdometer=11_700.0),
            DrivingSessionEntity(id=7, startTime=now-22*day+10*3600_000L,endTime=now-22*day+10*3600_000L+114*min,startSoc=82, endSoc=51, energyKwh=19.0, distanceKm=250.0, efficiencyKmPerKwh=13.16, startOdometer=11_240.0, endOdometer=11_490.0),
        )
    }

    private fun buildDemoDataPoints(): List<DataPointEntity> {
        val now = System.currentTimeMillis()
        val h = 3_600_000L
        return listOf(
            DataPointEntity(id=1,  timestamp=now-24*h, batteryPercent=60, isCharging=false, isDriving=false, chargingPowerKw=null, hvacOn=false, drivingRangeKm=280.0),
            DataPointEntity(id=2,  timestamp=now-22*h, batteryPercent=40, isCharging=false, isDriving=true,  chargingPowerKw=null, hvacOn=false, drivingRangeKm=186.0),
            DataPointEntity(id=3,  timestamp=now-20*h, batteryPercent=32, isCharging=false, isDriving=false, chargingPowerKw=null, hvacOn=false, drivingRangeKm=149.0),
            DataPointEntity(id=4,  timestamp=now-18*h, batteryPercent=52, isCharging=true,  isDriving=false, chargingPowerKw=7.2,  hvacOn=false, drivingRangeKm=243.0),
            DataPointEntity(id=5,  timestamp=now-16*h, batteryPercent=68, isCharging=false, isDriving=false, chargingPowerKw=null, hvacOn=false, drivingRangeKm=317.0),
            DataPointEntity(id=6,  timestamp=now-14*h, batteryPercent=52, isCharging=false, isDriving=true,  chargingPowerKw=null, hvacOn=true,  drivingRangeKm=243.0),
            DataPointEntity(id=7,  timestamp=now-12*h, batteryPercent=45, isCharging=false, isDriving=false, chargingPowerKw=null, hvacOn=false, drivingRangeKm=210.0),
            DataPointEntity(id=8,  timestamp=now-10*h, batteryPercent=62, isCharging=true,  isDriving=false, chargingPowerKw=7.2,  hvacOn=false, drivingRangeKm=289.0),
            DataPointEntity(id=9,  timestamp=now-8*h,  batteryPercent=77, isCharging=false, isDriving=false, chargingPowerKw=null, hvacOn=false, drivingRangeKm=359.0),
            DataPointEntity(id=10, timestamp=now-6*h,  batteryPercent=65, isCharging=false, isDriving=true,  chargingPowerKw=null, hvacOn=true,  drivingRangeKm=303.0),
            DataPointEntity(id=11, timestamp=now-4*h,  batteryPercent=55, isCharging=false, isDriving=false, chargingPowerKw=null, hvacOn=false, drivingRangeKm=257.0),
            DataPointEntity(id=12, timestamp=now-2*h,  batteryPercent=65, isCharging=true,  isDriving=false, chargingPowerKw=7.2,  hvacOn=false, drivingRangeKm=303.0),
            DataPointEntity(id=13, timestamp=now,      batteryPercent=72, isCharging=true,  isDriving=false, chargingPowerKw=7.2,  hvacOn=false, drivingRangeKm=336.0),
        )
    }

    // MARK: - Helpers

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
    }

    private suspend fun saveSetting(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.appDataStore.edit { prefs -> prefs[key] = value }
    }

    private suspend fun saveCredentials(s: AppSettings) {
        SecureStorage.put(context, SecureStorage.KEY_USERNAME, s.username)
        SecureStorage.put(context, SecureStorage.KEY_PASSWORD, s.password)
        saveSetting(PrefKeys.REGION, s.region)
    }
}
