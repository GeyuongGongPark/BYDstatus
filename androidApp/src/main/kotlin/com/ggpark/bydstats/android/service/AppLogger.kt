package com.ggpark.bydstats.android.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
) {
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    val formatted: String get() = "[${fmt.format(Date(timestamp))}] [$tag] $message"
}

object AppLogger {
    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun log(message: String, tag: String = "App") {
        val entry = LogEntry(tag = tag, message = message)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        android.util.Log.d(tag, message)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
