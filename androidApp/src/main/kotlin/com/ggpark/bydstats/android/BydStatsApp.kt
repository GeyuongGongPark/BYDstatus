package com.ggpark.bydstats.android

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.ggpark.bydstats.model.VehicleStatus
import kotlinx.coroutines.flow.MutableStateFlow

val Context.appDataStore by preferencesDataStore(name = "settings")

class BydStatsApp : Application() {
    /** PollingService → AppViewModel 상태 공유 채널 */
    val statusFlow = MutableStateFlow<VehicleStatus?>(null)
    val errorFlow  = MutableStateFlow<String?>(null)
}
