package com.ggpark.bydstats.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ggpark.bydstats.android.viewmodel.UpdateUiState

@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val release = state.release ?: return
    if (!state.showDialog) return

    AlertDialog(
        onDismissRequest = { if (!state.downloading) onDismiss() },
        title = { Text("새 버전 ${release.version}") },
        text = {
            Column {
                if (state.downloading) {
                    Text("다운로드 중…")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { state.progress })
                } else {
                    Text("앱에서 바로 받아 설치할 수 있습니다.")
                    if (release.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(release.notes.take(400))
                    }
                    if (state.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload, enabled = !state.downloading) {
                Text(if (state.error != null) "다시 시도" else "다운로드 · 설치")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.downloading) {
                Text("나중에")
            }
        },
    )
}
