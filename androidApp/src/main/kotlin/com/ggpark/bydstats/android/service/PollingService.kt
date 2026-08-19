package com.ggpark.bydstats.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.ggpark.bydstats.android.BydStatsApp
import com.ggpark.bydstats.android.data.AppDatabase
import com.ggpark.bydstats.android.data.ChargingRatePlan
import com.ggpark.bydstats.android.data.ratePlanById
import com.ggpark.bydstats.android.widget.BydStatsWidget
import com.ggpark.bydstats.android.widget.WidgetDataStore
import com.ggpark.bydstats.android.widget.WidgetSnapshot
import com.ggpark.bydstats.api.BydApiClient
import com.ggpark.bydstats.api.BydConfig
import com.ggpark.bydstats.model.VehicleStatus
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

private object Keys {
    val USERNAME         = stringPreferencesKey("username")
    val PASSWORD         = stringPreferencesKey("password")
    val REGION           = stringPreferencesKey("region")
    val VIN              = stringPreferencesKey("vin")
    val USER_ID          = stringPreferencesKey("user_id")
    val SIGN_TOKEN       = stringPreferencesKey("sign_token")
    val ENCRY_TOKEN      = stringPreferencesKey("encry_token")
    val RATE_PLAN_ID     = stringPreferencesKey("rate_plan_id")
    val CUSTOM_RATE      = stringPreferencesKey("custom_rate")
    val BATTERY_CAPACITY = stringPreferencesKey("battery_capacity")
    val POLLING_INTERVAL = stringPreferencesKey("polling_interval")
}

class PollingService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var dataCollector: DataCollector? = null

    // MARK: - Companion

    companion object {
        private const val ACTION_START  = "com.ggpark.bydstats.START"
        private const val ACTION_STOP   = "com.ggpark.bydstats.STOP"
        private const val CHANNEL_ID    = "byd_polling"
        private const val NOTIF_ID      = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PollingService::class.java).apply { action = ACTION_START }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, PollingService::class.java).apply { action = ACTION_STOP }
            )
        }

        fun restart(context: Context) {
            stop(context)
            start(context)
        }
    }

    // MARK: - Lifecycle

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification(null, null))
                startCollecting()
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        dataCollector?.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // MARK: - 폴링 시작

    private fun startCollecting() {
        scope.launch {
            // DataStore에서 설정 로드
            val prefs = applicationContext.dataStore.data.first()
            val username    = prefs[Keys.USERNAME]  ?: return@launch
            val password    = prefs[Keys.PASSWORD]  ?: return@launch
            val vin         = prefs[Keys.VIN]       ?: return@launch
            val region      = prefs[Keys.REGION]    ?: "KR"
            val userId      = prefs[Keys.USER_ID]   ?: ""
            val signToken   = prefs[Keys.SIGN_TOKEN] ?: ""
            val encryToken  = prefs[Keys.ENCRY_TOKEN] ?: ""
            val ratePlanId  = prefs[Keys.RATE_PLAN_ID] ?: "kepco_low"
            val customRate  = prefs[Keys.CUSTOM_RATE]?.toDoubleOrNull() ?: 180.0
            val capacity    = prefs[Keys.BATTERY_CAPACITY]?.toDoubleOrNull() ?: 60.48
            val intervalMin = prefs[Keys.POLLING_INTERVAL]?.toIntOrNull() ?: 5

            // ApiClient 초기화
            val config = BydConfig.fromRegion(region)
            val tableData = assets.open("bangcle_tables.bin").readBytes()
            val httpClient = HttpClient(Android) {
                engine { connectTimeout = 120_000; socketTimeout = 120_000 }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
            val apiClient = BydApiClient(config, tableData, httpClient).also { client ->
                client.setCredentials(username, password)
                if (userId.isNotEmpty() && signToken.isNotEmpty()) {
                    client.restoreSession(userId, signToken, encryToken)
                }
                client.onSessionUpdated = { uid, sign, encry ->
                    scope.launch {
                        applicationContext.dataStore.edit { p ->
                            p[Keys.USER_ID]    = uid
                            p[Keys.SIGN_TOKEN]  = sign
                            p[Keys.ENCRY_TOKEN] = encry
                        }
                    }
                }
                client.onSessionExpired = {
                    (application as BydStatsApp).errorFlow.value = "세션 만료 — 재로그인 필요"
                }
            }

            // DataCollector 시작
            val db = AppDatabase.getInstance(applicationContext)
            val app = application as BydStatsApp
            val collector = DataCollector(
                apiClient             = apiClient,
                db                    = db,
                getElectricityRateAt  = { ts -> ratePlanById(ratePlanId, customRate).rateAt(ts) },
                getBatteryCapacityKwh = { capacity },
                getParkingIntervalMs  = { intervalMin * 60_000L },
                locationTracker       = LocationTracker(applicationContext),
            )
            dataCollector = collector
            collector.start(vin)

            // 상태 구독 → Application flow + 알림 + 위젯 갱신
            combine(collector.currentStatus, collector.error) { s, e -> s to e }
                .collect { (status, err) ->
                    app.statusFlow.value = status
                    app.errorFlow.value  = err
                    updateNotification(status, err)
                    status?.let { saveWidget(it) }
                }
        }
    }

    // MARK: - 알림

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "차량 모니터링",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "BYD 차량 실시간 상태 모니터링" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: VehicleStatus?, error: String?): Notification {
        val title = when {
            status != null -> "BYD Stats · ${status.batteryPercentage}%"
            else           -> "BYD Stats"
        }
        val body = when {
            error  != null      -> "오류: $error"
            status == null      -> "연결 중…"
            status.isCharging   -> "충전 중 · ${status.drivingRange.toInt()} km"
            status.isDriving    -> "주행 중 · ${status.drivingRange.toInt()} km"
            else                -> "주차 중 · ${status.drivingRange.toInt()} km 남음"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(status: VehicleStatus?, error: String?) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(status, error))
    }

    // MARK: - 위젯

    private suspend fun saveWidget(s: VehicleStatus) {
        WidgetDataStore.save(
            applicationContext,
            WidgetSnapshot(
                batteryPercent = s.batteryPercentage,
                isCharging     = s.isCharging,
                isDriving      = s.isDriving,
                drivingRangeKm = s.drivingRange,
                instantPowerKw = s.instantPowerKw,
                lastUpdated    = System.currentTimeMillis(),
            ),
        )
        val manager = GlanceAppWidgetManager(applicationContext)
        manager.getGlanceIds(BydStatsWidget::class.java)
            .forEach { BydStatsWidget().update(applicationContext, it) }
    }
}
