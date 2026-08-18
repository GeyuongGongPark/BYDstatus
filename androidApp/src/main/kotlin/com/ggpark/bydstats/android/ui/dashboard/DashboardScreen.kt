package com.ggpark.bydstats.android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ggpark.bydstats.android.data.entity.ChargingSessionEntity
import com.ggpark.bydstats.android.data.entity.DrivingSessionEntity
import com.ggpark.bydstats.android.viewmodel.AppViewModel
import com.ggpark.bydstats.model.VehicleStatus
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val uiState by vm.uiState.collectAsState()
    val chargingSessions by vm.chargingSessions.collectAsState(emptyList())
    val drivingSessions by vm.drivingSessions.collectAsState(emptyList())

    // 이번 달 통계
    val now = System.currentTimeMillis()
    val monthStart = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).let { dt ->
        dt.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()
    }
    val thisMonthCharging = chargingSessions.filter { it.startTime >= monthStart && it.endTime != null }
    val thisMonthDriving  = drivingSessions.filter  { it.startTime >= monthStart && it.endTime != null }

    val monthChargingCost = thisMonthCharging.sumOf { it.estimatedCostKrw }
    val monthChargingKwh  = thisMonthCharging.sumOf { it.energyKwh }
    val monthDrivingKm    = thisMonthDriving.mapNotNull { it.distanceKm }.sum()
    val monthDrivingKwh   = thisMonthDriving.sumOf { it.energyKwh }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("BYD Stats", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 에러 메시지
            uiState.pollingError?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // 배터리 상태 카드
            uiState.status?.let { status ->
                BatteryCard(status)
            } ?: ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "차량 데이터 로딩 중",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 이번 달 통계
            Text(
                "이번 달",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            MonthlyStatGrid(
                chargingCost = monthChargingCost,
                chargingKwh  = monthChargingKwh,
                drivingKm    = monthDrivingKm,
                drivingKwh   = monthDrivingKwh,
            )
        }
    }
}

@Composable
private fun BatteryCard(status: VehicleStatus) {
    val battColor = when {
        status.batteryPercentage >= 60 -> Color(0xFF4CAF50)
        status.batteryPercentage >= 30 -> Color(0xFFFFC107)
        else                           -> Color(0xFFF44336)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 상단: % + 상태 칩
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 배터리 아이콘
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(battColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (status.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = battColor,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${status.batteryPercentage}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = battColor,
                        )
                        Text(
                            text = "%",
                            style = MaterialTheme.typography.headlineSmall,
                            color = battColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                StatusChip(status)
            }

            Spacer(Modifier.height(16.dp))

            // 프로그레스 바
            LinearProgressIndicator(
                progress = { status.batteryPercentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(MaterialTheme.shapes.small),
                color = battColor,
                trackColor = battColor.copy(alpha = 0.15f),
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // 하단 스탯
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BatteryStatItem(
                    label = "주행가능",
                    value = "${status.drivingRange.toInt()} km",
                    icon = Icons.Default.Route,
                )
                if (status.instantPowerW != 0.0) {
                    BatteryStatItem(
                        label = if (status.isCharging) "충전" else "소비",
                        value = "${"%.1f".format(kotlin.math.abs(status.instantPowerKw))} kW",
                        icon = if (status.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.Bolt,
                    )
                }
                if (status.speed > 0) {
                    BatteryStatItem(
                        label = "속도",
                        value = "${"%.0f".format(status.speed)} km/h",
                        icon = Icons.Default.Speed,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: VehicleStatus) {
    val (text, color) = when {
        status.isDriving  -> "주행 중" to Color(0xFF2196F3)
        status.isCharging -> "충전 중" to Color(0xFF4CAF50)
        else              -> "주차 중" to Color(0xFF9E9E9E)
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BatteryStatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthlyStatGrid(
    chargingCost: Double,
    chargingKwh: Double,
    drivingKm: Double,
    drivingKwh: Double,
) {
    val stats = listOf(
        Triple("충전 비용", "₩${"%.0f".format(chargingCost)}", Icons.Default.Payments) to Color(0xFF4CAF50),
        Triple("충전량",   "${"%.1f".format(chargingKwh)} kWh", Icons.Default.BatteryChargingFull) to Color(0xFF2196F3),
        Triple("주행거리", "${"%.0f".format(drivingKm)} km", Icons.Default.Route) to Color(0xFFFF9800),
        Triple("소비",     "${"%.1f".format(drivingKwh)} kWh", Icons.Default.Bolt) to Color(0xFF9C27B0),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stats.take(2).forEach { (triple, color) ->
                val (label, value, icon) = triple
                StatCard(label, value, icon, color, Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stats.drop(2).forEach { (triple, color) ->
                val (label, value, icon) = triple
                StatCard(label, value, icon, color, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
