package com.ggpark.bydstats.android.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BydFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 토큰 갱신 시 서버에 재등록 */
    override fun onNewToken(token: String) {
        Log.d("FCM", "new token: ${token.takeLast(8)}")
        scope.launch { PushRegistrar.register(applicationContext, token) }
    }

    /** silent push 수신 → PollingService 즉시 폴링 트리거 */
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM", "message received, triggering poll")
        PollingService.pollNow(applicationContext)
    }
}
