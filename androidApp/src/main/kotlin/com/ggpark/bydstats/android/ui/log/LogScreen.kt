package com.ggpark.bydstats.android.ui.log

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ggpark.bydstats.android.service.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val entries by AppLogger.entries.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    fun shareLog() {
        val text = entries.joinToString("\n") { it.formatted }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "BYD Stats 로그")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "로그 공유"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("앱 로그") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = ::shareLog, enabled = entries.isNotEmpty()) {
                        Icon(Icons.Default.Share, "공유")
                    }
                    IconButton(onClick = { AppLogger.clear() }) {
                        Icon(Icons.Default.DeleteSweep, "지우기")
                    }
                },
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("로그 없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(entries) { entry ->
                    Text(
                        text = entry.formatted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}
