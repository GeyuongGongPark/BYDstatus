package com.ggpark.bydstats.android.service

import android.content.Context
import android.util.Log
import com.ggpark.bydstats.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PushRegistrar {

    private const val TAG = "PushRegistrar"

    suspend fun register(context: Context, token: String) = withContext(Dispatchers.IO) {
        send("POST", "/api/register", JSONObject().apply {
            put("token", token)
            put("platform", "android")
            put("sandbox", "0")
        })
    }

    suspend fun unregister(token: String) = withContext(Dispatchers.IO) {
        send("DELETE", "/api/unregister", JSONObject().apply {
            put("token", token)
        })
    }

    suspend fun registerSession(
        userId: String,
        signToken: String,
        encryToken: String,
        brokerHost: String,
        brokerPort: Int,
        clientId: String,
        vin: String?,
    ) = withContext(Dispatchers.IO) {
        send("POST", "/api/session/register", JSONObject().apply {
            put("user_id",    userId)
            put("sign_token", signToken)
            put("encry_token", encryToken)
            put("broker_host", brokerHost)
            put("broker_port", brokerPort)
            put("client_id",  clientId)
            if (vin != null) put("vin", vin)
        })
    }

    suspend fun updateSession(
        userId: String,
        signToken: String,
        encryToken: String,
    ) = withContext(Dispatchers.IO) {
        send("PUT", "/api/session/update", JSONObject().apply {
            put("user_id",    userId)
            put("sign_token", signToken)
            put("encry_token", encryToken)
        })
    }

    private fun send(method: String, path: String, body: JSONObject) {
        try {
            val url = URL(BuildConfig.PUSH_SERVER_URL + path)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.PUSH_API_KEY}")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            Log.d(TAG, "$method $path -> HTTP ${conn.responseCode}")
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "$method $path error: ${e.message}")
        }
    }
}
