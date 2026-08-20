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
