package com.ggpark.bydstats.android.viewmodel

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ggpark.bydstats.android.BydStatsApp
import com.ggpark.bydstats.android.data.AppDatabase
import com.ggpark.bydstats.android.data.entity.ChargingSessionEntity
import com.ggpark.bydstats.android.data.entity.DataPointEntity
import com.ggpark.bydstats.android.data.entity.DrivingSessionEntity
import com.ggpark.bydstats.android.service.PollingService
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
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AppDatabase.getInstance(context)
    private var apiClient: BydApiClient? = null

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    val dataPoints: Flow<List<DataPointEntity>> = db.dataPointDao().allFlow()
    val chargingSessions: Flow<List<ChargingSessionEntity>> = db.chargingSessionDao().allFlow()
    val drivingSessions: Flow<List<DrivingSessionEntity>> = db.drivingSessionDao().allFlow()

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

        if (s.vin.isNotEmpty()) PollingService.start(context)
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
            PollingService.stop(context)
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

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
    }

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
}
