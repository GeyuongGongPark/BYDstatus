package com.ggpark.bydstats.android.service

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EncryptedSharedPreferences 기반 민감 정보 저장소.
 * BYD 계정 username/password만 여기에 저장하고, 나머지 설정은 DataStore를 사용한다.
 */
object SecureStorage {

    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        "secure_creds",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    suspend fun put(context: Context, key: String, value: String) =
        withContext(Dispatchers.IO) { prefs(context).edit().putString(key, value).apply() }

    suspend fun get(context: Context, key: String): String? =
        withContext(Dispatchers.IO) { prefs(context).getString(key, null) }

    suspend fun clear(context: Context) =
        withContext(Dispatchers.IO) { prefs(context).edit().clear().apply() }
}
