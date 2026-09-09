package com.ggpark.bydstats.android.service

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val version: String,
    val apkUrl: String,
    val notes: String,
)

object AppUpdate {

    private const val REPO = "GeyuongGongPark/BYDstatus"
    private const val USER_AGENT = "BYDStats-Android"

    fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").substringBefore("-").substringBefore("+")

    fun compareVersions(a: String, b: String): Int {
        val pa = normalizeVersion(a).split('.').map { it.toIntOrNull() ?: 0 }
        val pb = normalizeVersion(b).split('.').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val da = pa.getOrElse(i) { 0 }
            val db = pb.getOrElse(i) { 0 }
            if (da != db) return da.compareTo(db)
        }
        return 0
    }

    fun isNewer(latest: String, current: String): Boolean = compareVersions(latest, current) > 0

    suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            parseRelease(body)
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseRelease(json: String): AppRelease? {
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name", "")
        val version = normalizeVersion(tag)
        if (version.isEmpty()) return null
        val assets = obj.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url", "")
                break
            }
        }
        if (apkUrl.isNullOrEmpty()) return null
        return AppRelease(
            version = version,
            apkUrl = apkUrl,
            notes = obj.optString("body", "").trim(),
        )
    }

    suspend fun downloadApk(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("다운로드 실패 (HTTP ${conn.responseCode})")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
        } finally {
            conn.disconnect()
        }
    }

    fun apkFile(context: Context): File = File(File(context.cacheDir, "update"), "latest.apk")

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.packageManager.canRequestPackageInstalls()
        else true

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
