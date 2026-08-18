package com.ggpark.bydstats.android.ui.driving

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ggpark.bydstats.android.data.entity.DrivingSessionEntity
import com.ggpark.bydstats.android.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DRIVING_COLOR = Color(0xFF2196F3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivingSessionsScreen(vm: AppViewModel) {
    val sessions by vm.drivingSessions.collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    val zoneId = ZoneId.systemDefault()
    val grouped = sessions
        .filter { it.endTime != null }
        .groupBy { session ->
            val dt = Instant.ofEpochMilli(session.startTime).atZone(zoneId)
            "%04d-%02d".format(dt.year, dt.monthValue)
        }
    val monthKeys = grouped.keys.sortedDescending()
    var selectedMonth by remember { mutableStateOf(monthKeys.firstOrNull() ?: "") }
    var editingSession by remember { mutableStateOf<DrivingSessionEntity?>(null) }

    val monthSessions = grouped[selectedMonth] ?: emptyList()
    val monthLabel = selectedMonth.let { key ->
        if (key.length == 7) "${key.take(4)}년 ${key.drop(5).trimStart('0')}월" else key
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("주행 세션") },
                actions = {
                    if (monthKeys.size > 1) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(monthLabel)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                monthKeys.forEach { key ->
                                    val label = if (key.length == 7)
                                        "${key.take(4)}년 ${key.drop(5).trimStart('0')}월"
                                    else key
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (key == selectedMonth)
                                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                                else
                                                    Spacer(Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(label)
                                            }
                                        },
                                        onClick = { selectedMonth = key; menuExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (monthSessions.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.DirectionsCar, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("주행 세션 없음", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "주행 중 앱이 실행되면 자동으로 기록됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                DrivingSummaryCard(monthSessions)
                Spacer(Modifier.height(16.dp))
            }
            item {
                Text(
                    "${monthSessions.size}회 주행",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            items(monthSessions, key = { it.id }) { session ->
                DrivingSessionItem(
                    session  = session,
                    onEdit   = { editingSession = session },
                    onDelete = { scope.launch { vm.deleteDrivingSession(session) } },
                )
                Spacer(Modifier.height(8.dp))
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    editingSession?.let { session ->
        DrivingEditDialog(
            session   = session,
            onDismiss = { editingSession = null },
            onSave    = { updated ->
                scope.launch { vm.updateDrivingSession(updated) }
                editingSession = null
            },
        )
    }
}

@Composable
private fun DrivingSummaryCard(sessions: List<DrivingSessionEntity>) {
    val totalKm  = sessions.mapNotNull { it.distanceKm }.sum()
    val totalKwh = sessions.sumOf { it.energyKwh }
    val avgEff   = sessions.filter { (it.distanceKm ?: 0.0) > 0 && it.energyKwh > 0 }.let { valid ->
        if (valid.isEmpty()) null
        else valid.mapNotNull { it.distanceKm }.sum() / valid.sumOf { it.energyKwh }
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            SummaryItem("${"%.0f".format(totalKm)} km", "주행거리", Modifier.weight(1f))
            VerticalDivider(modifier = Modifier.height(48.dp))
            SummaryItem("${"%.1f".format(totalKwh)} kWh", "소비", Modifier.weight(1f))
            VerticalDivider(modifier = Modifier.height(48.dp))
            SummaryItem(avgEff?.let { "${"%.1f".format(it)} km/kWh" } ?: "—", "평균 전비", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryItem(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DrivingSessionItem(
    session: DrivingSessionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val startStr = Instant.ofEpochMilli(session.startTime)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
    val duration = session.endTime?.let { ((it - session.startTime) / 60_000).toInt() }

    var showDeleteDialog by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(DRIVING_COLOR),
            )
            Row(
                modifier = Modifier.padding(12.dp).weight(1f),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Text(startStr, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        duration?.let { dur ->
                            val durStr = if (dur >= 60) "${dur / 60}시간 ${dur % 60}분" else "${dur}분"
                            Text(durStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${session.startSoc}% → ${session.endSoc}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (session.startOdometer != null && session.endOdometer != null) {
                        Text(
                            "${"%.0f".format(session.startOdometer)} → ${"%.0f".format(session.endOdometer)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        session.distanceKm?.let { LabeledValue("${"%.1f".format(it)} km", "주행거리") }
                        if (session.energyKwh > 0) LabeledValue("${"%.2f".format(session.energyKwh)} kWh", "소비")
                        session.efficiencyKmPerKwh?.let { LabeledValue("${"%.1f".format(it)} km/kWh", "전비") }
                    }
                }
                Column {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("삭제") },
            text = { Text("이 주행 세션을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun LabeledValue(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DrivingEditDialog(
    session: DrivingSessionEntity,
    onDismiss: () -> Unit,
    onSave: (DrivingSessionEntity) -> Unit,
) {
    var startSoc    by remember { mutableStateOf(session.startSoc) }
    var endSoc      by remember { mutableStateOf(session.endSoc) }
    var distanceStr by remember { mutableStateOf(session.distanceKm?.toString() ?: "") }
    var energyStr   by remember { mutableStateOf(if (session.energyKwh > 0) session.energyKwh.toString() else "") }

    val distKm     = distanceStr.toDoubleOrNull()
    val energy     = energyStr.toDoubleOrNull()
    val efficiency = if ((distKm ?: 0.0) > 0 && (energy ?: 0.0) > 0) distKm!! / energy!! else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("주행 세션 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SOC", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("시작", modifier = Modifier.width(40.dp))
                    Slider(value = startSoc.toFloat(), onValueChange = { startSoc = it.toInt() }, valueRange = 0f..100f, steps = 99, modifier = Modifier.weight(1f))
                    Text("${startSoc}%", modifier = Modifier.width(40.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("종료", modifier = Modifier.width(40.dp))
                    Slider(value = endSoc.toFloat(), onValueChange = { endSoc = it.toInt() }, valueRange = 0f..100f, steps = 99, modifier = Modifier.weight(1f))
                    Text("${endSoc}%", modifier = Modifier.width(40.dp))
                }
                OutlinedTextField(
                    value = distanceStr,
                    onValueChange = { distanceStr = it },
                    label = { Text("주행거리 (km)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = energyStr,
                    onValueChange = { energyStr = it },
                    label = { Text("소비 (kWh)") },
                    singleLine = true,
                )
                efficiency?.let {
                    Text(
                        "전비: ${"%.2f".format(it)} km/kWh",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(session.copy(
                    startSoc = startSoc,
                    endSoc = endSoc,
                    distanceKm = distKm,
                    energyKwh = energy ?: session.energyKwh,
                    efficiencyKmPerKwh = efficiency,
                ))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
