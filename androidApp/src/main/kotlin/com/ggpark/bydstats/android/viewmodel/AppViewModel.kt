package com.ggpark.bydstats.android.viewmodel

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.ggpark.bydstats.android.data.AppDatabase
import com.ggpark.bydstats.android.data.ChargingRatePlan
import com.ggpark.bydstats.android.data.ratePlanById
import com.ggpark.bydstats.android.data.entity.ChargingSessionEntity
import com.ggpark.bydstats.android.data.entity.DataPointEntity
import com.ggpark.bydstats.android.data.entity.DrivingSessionEntity
import com.ggpark.bydstats.android.service.DataCollector
import com.ggpark.bydstats.android.service.LocationTracker
import com.ggpark.bydstats.android.widget.BydStatsWidget
import com.ggpark.bydstats.android.widget.WidgetDataStore
import com.ggpark.bydstats.android.widget.WidgetSnapshot
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

private val Context.dataStore by preferencesDataStore(name = "settings")

private object PrefKeys {
    val USERNAME         = stringPreferencesKey("username")
    val PASSWORD         = stringPreferencesKey("password")
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
    val electricityRate: Double = 180.0,  // "custom" 요금제일 때 사용
    val vehicleModel: String = "아토 3",
    val batteryCapacityKwh: Double = 60.48,
    val pollingIntervalMin: Int = 5,
    val ratePlanId: String = "kepco_low",
)

// iOS처럼 항상 탭바 앱 표시. 로그인 폼은 설정 탭 안에 포함.
data class AppUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val status: VehicleStatus? = null,
    val pollingError: String? = null,
    val vehicles: List<VehicleListItem> = emptyList(),
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AppDatabase.getInstance(context)
    private var apiClient: BydApiClient? = null
    private var dataCollector: DataCollector? = null
    private val locationTracker = LocationTracker(context)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    val dataPoints: Flow<List<DataPointEntity>> = db.dataPointDao().allFlow()
    val chargingSessions: Flow<List<ChargingSessionEntity>> = db.chargingSessionDao().allFlow()
    val drivingSessions: Flow<List<DrivingSessionEntity>> = db.drivingSessionDao().allFlow()

    init {
        viewModelScope.launch { loadSettings() }
    }

    // MARK: - Settings

    private suspend fun loadSettings() {
        val prefs = context.dataStore.data.first()
        val s = AppSettings(
            username           = prefs[PrefKeys.USERNAME] ?: "",
            password           = prefs[PrefKeys.PASSWORD] ?: "",
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
        startPolling(s.vin)
    }

    private fun initApiClient(settings: AppSettings) {
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
                    context.dataStore.edit { p ->
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

    // MARK: - Login (iOS처럼 설정 탭 안에서 처리)

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
                _uiState.update { it.copy(isLoggingIn = false, isLoggedIn = true, loginError = null, vehicles = vehicles) }
                startPolling(vin)
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
            startPolling(vin)
        }
    }

    // MARK: - Polling

    private fun startPolling(vin: String) {
        if (vin.isEmpty()) return
        val client = apiClient ?: return
        dataCollector?.stop()

        val collector = DataCollector(
            apiClient              = client,
            db                     = db,
            getElectricityRateAt   = { ts ->
                val s = _settings.value
                ratePlanById(s.ratePlanId, s.electricityRate).rateAt(ts)
            },
            getBatteryCapacityKwh  = { _settings.value.batteryCapacityKwh },
            getParkingIntervalMs   = { _settings.value.pollingIntervalMin * 60_000L },
            locationTracker        = locationTracker,
        )
        dataCollector = collector
        collector.start(vin)

        viewModelScope.launch {
            combine(collector.currentStatus, collector.error) { status, err ->
                status to err
            }.collect { (status, err) ->
                _uiState.update { it.copy(status = status, pollingError = err) }
                status?.let { s ->
                    WidgetDataStore.save(context, WidgetSnapshot(
                        batteryPercent = s.batteryPercentage,
                        isCharging     = s.isCharging,
                        isDriving      = s.isDriving,
                        drivingRangeKm = s.drivingRange,
                        instantPowerKw = s.instantPowerKw,
                        lastUpdated    = System.currentTimeMillis(),
                    ))
                    val manager = GlanceAppWidgetManager(context)
                    val ids = manager.getGlanceIds(BydStatsWidget::class.java)
                    ids.forEach { BydStatsWidget().update(context, it) }
                }
            }
        }
    }

    // MARK: - Settings Update

    fun updateRatePlan(planId: String) {
        viewModelScope.launch {
            saveSetting(PrefKeys.RATE_PLAN_ID, planId)
            updateSettings { it.copy(ratePlanId = planId) }
        }
    }

    fun updateElectricityRate(rate: Double) {
        viewModelScope.launch {
            saveSetting(PrefKeys.CUSTOM_RATE, rate.toString())
            updateSettings { it.copy(electricityRate = rate) }
        }
    }

    fun updateVehicle(name: String, kwh: Double) {
        viewModelScope.launch {
            saveSetting(PrefKeys.VEHICLE_MODEL, name)
            saveSetting(PrefKeys.BATTERY_CAPACITY, kwh.toString())
            updateSettings { it.copy(vehicleModel = name, batteryCapacityKwh = kwh) }
        }
    }

    fun updatePollingInterval(minutes: Int) {
        viewModelScope.launch {
            saveSetting(PrefKeys.POLLING_INTERVAL, minutes.toString())
            updateSettings { it.copy(pollingIntervalMin = minutes) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            dataCollector?.stop()
            context.dataStore.edit { it.clear() }
            _settings.value = AppSettings()
            _uiState.value = AppUiState(isLoading = false, isLoggedIn = false)
        }
    }

    // MARK: - DB Operations

    suspend fun deleteChargingSession(session: ChargingSessionEntity) = db.chargingSessionDao().delete(session)
    suspend fun updateChargingSession(session: ChargingSessionEntity) = db.chargingSessionDao().update(session)
    suspend fun deleteDrivingSession(session: DrivingSessionEntity)   = db.drivingSessionDao().delete(session)
    suspend fun updateDrivingSession(session: DrivingSessionEntity)   = db.drivingSessionDao().update(session)

    // MARK: - Helpers

    private fun updateSettings(transform: (AppSettings) -> AppSettings) { _settings.value = transform(_settings.value) }

    private suspend fun saveSetting(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    private suspend fun saveCredentials(s: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[PrefKeys.USERNAME] = s.username
            prefs[PrefKeys.PASSWORD] = s.password
            prefs[PrefKeys.REGION]   = s.region
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataCollector?.stop()
    }
}
