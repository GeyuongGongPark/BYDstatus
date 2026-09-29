package com.ggpark.bydstats.android.ui.log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ggpark.bydstats.android.service.AppLogger
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val REPORT_URL = "https://bydstatus-production.up.railway.app/api/reports"
private val CAR_OPTIONS = listOf("BYD Atto 3", "BYD Seal", "BYD Dolphin", "BYD Sealion 7", "기타")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    appVersion: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var car by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var carExpanded by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var didSubmit by remember { mutableStateOf(false) }

    val recentLogs = remember {
        AppLogger.entries.value.takeLast(80).joinToString("\n") { it.formatted }
    }

    LaunchedEffect(Unit) {
        body = "앱 버전: $appVersion\n플랫폼: Android\n\n--- 최근 로그 ---\n$recentLogs"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("버그 제보", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "닫기")
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("제목 (필수)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = carExpanded,
                onExpandedChange = { carExpanded = it },
            ) {
                OutlinedTextField(
                    value = car,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("차종 (필수)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = carExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = carExpanded,
                    onDismissRequest = { carExpanded = false },
                ) {
                    CAR_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                car = option
                                carExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("본문") },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )

            if (submitError != null) {
                Text(
                    text = submitError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        submitError = null
                        val ok = submitReport(
                            title = title.trim(),
                            car = car,
                            body = body.trim(),
                        )
                        isSubmitting = false
                        if (ok) didSubmit = true
                        else submitError = "제출에 실패했습니다. 잠시 후 다시 시도해주세요."
                    }
                },
                enabled = title.isNotBlank() && car.isNotBlank() && !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("제출")
                }
            }
        }
    }

    if (didSubmit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("제보 완료") },
            text = { Text("제보해 주셔서 감사합니다!") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("확인") }
            },
        )
    }
}

private fun submitReport(title: String, car: String, body: String): Boolean {
    return try {
        val payload = JSONObject().apply {
            put("title", title)
            put("app", "BYD Status")
            put("platform", "Android")
            put("car", car)
            put("body", body)
        }
        val conn = (URL(REPORT_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        conn.responseCode == 201
    } catch (e: Exception) {
        false
    }
}
