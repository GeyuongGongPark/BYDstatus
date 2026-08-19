package com.ggpark.bydstats.android.ui.battery

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ggpark.bydstats.android.data.entity.DataPointEntity
import com.ggpark.bydstats.android.viewmodel.AppViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// MARK: - 기간 필터

private enum class BattRange(val label: String, val durationMs: Long?) {
    DAY("24시간", 24 * 3600 * 1000L),
    WEEK("7일", 7 * 24 * 3600 * 1000L),
    MONTH("30일", 30 * 24 * 3600 * 1000L),
    ALL("전체", null),
    CUSTOM("직접", null),
}

// MARK: - 상태 색상

private val chargingColor = Color(0xFF2DB85B)   // 초록
private val drivingColor  = Color(0xFFF59021)   // 주황
private val parkedColor   = Color(0xFFAAAAAA)   // 회색

private fun stateColor(pt: DataPointEntity): Color = when {
    pt.isCharging -> chargingColor
    pt.isDriving  -> drivingColor
    else          -> parkedColor
}

private fun stateLabel(pt: DataPointEntity): String = when {
    pt.isCharging -> "충전 중"
    pt.isDriving  -> "주행 중"
    else          -> "주차 중"
}

// MARK: - 리샘플링 (기간별 버킷)

private fun resample30min(points: List<DataPointEntity>, bucketMin: Int = 30): List<DataPointEntity> {
    val bucketMs = bucketMin * 60 * 1000L
    return points
        .groupBy { it.timestamp / bucketMs }
        .toSortedMap()
        .values
        .map { it.last() }   // 버킷 내 마지막 포인트가 대표
}

private fun bucketMin(range: BattRange, customDurationMs: Long? = null): Int = when (range) {
    BattRange.DAY    -> 30
    BattRange.WEEK   -> 60
    BattRange.MONTH  -> 120
    BattRange.ALL    -> 120
    BattRange.CUSTOM -> {
        val dur = customDurationMs ?: (7 * 24 * 3600 * 1000L)
        when {
            dur <= 24 * 3600 * 1000L      -> 30
            dur <= 7 * 24 * 3600 * 1000L  -> 60
            else                           -> 120
        }
    }
}

// MARK: - Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryHistoryScreen(vm: AppViewModel) {
    val allPoints by vm.dataPoints.collectAsState(emptyList())
    var selectedRange by remember { mutableStateOf(BattRange.DAY) }
    var customStart by remember {
        mutableStateOf(LocalDateTime.now().minusDays(7))
    }
    var customEnd by remember { mutableStateOf(LocalDateTime.now()) }

    val now = remember { System.currentTimeMillis() }
    val filteredPoints = remember(allPoints, selectedRange, now, customStart, customEnd) {
        val (raw, customDurMs) = when (selectedRange) {
            BattRange.CUSTOM -> {
                val startMs = customStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMs   = customEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                allPoints.filter { it.timestamp in startMs..endMs } to (endMs - startMs)
            }
            else -> {
                val d = selectedRange.durationMs
                val pts = if (d == null) allPoints else allPoints.filter { it.timestamp >= now - d }
                pts to null
            }
        }
        resample30min(raw, bucketMin(selectedRange, customDurMs))
    }

    val latest = filteredPoints.lastOrNull()

    val stats = remember(filteredPoints) {
        filteredPoints.takeIf { it.isNotEmpty() }?.let { pts ->
            Triple(
                pts.maxOf { it.batteryPercent },
                pts.minOf { it.batteryPercent },
                pts.map { it.batteryPercent }.average().toInt(),
            )
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("배터리 이력") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 기간 필터
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BattRange.entries.forEach { range ->
                    FilterChip(
                        selected = selectedRange == range,
                        onClick = { selectedRange = range },
                        label = { Text(range.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 직접 날짜 선택
            if (selectedRange == BattRange.CUSTOM) {
                CustomDateRangePicker(
                    start = customStart,
                    end = customEnd,
                    onStartChange = { customStart = it },
                    onEndChange = { customEnd = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (filteredPoints.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.BarChart, null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("데이터 없음", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "폴링이 시작되면 데이터가 쌓입니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 최신 상태 헤더
                latest?.let { pt ->
                    item {
                        HeaderCard(pt)
                        Spacer(Modifier.height(6.dp))
                    }
                }

                // 통계 카드
                stats?.let { (max, min, avg) ->
                    item {
                        StatsCard(max, min, avg)
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // 배터리 바 목록 (최신순)
                items(filteredPoints.reversed()) { pt ->
                    BatteryBarRow(pt)
                }

                // 범례
                item {
                    Spacer(Modifier.height(8.dp))
                    LegendRow()
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// MARK: - 최신 상태 헤더

@Composable
private fun HeaderCard(pt: DataPointEntity) {
    val color = stateColor(pt)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("최종 상태", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${pt.batteryPercent}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    pt.drivingRangeKm?.takeIf { it > 0 }?.let { range ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "· ${range.toInt()} km 남음",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = color.copy(alpha = 0.13f),
            ) {
                Text(
                    stateLabel(pt),
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

// MARK: - 통계 카드

@Composable
private fun StatsCard(max: Int, min: Int, avg: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            StatItem("최고", max, Color(0xFF4CAF50), Modifier.weight(1f))
            VerticalDivider(modifier = Modifier.height(36.dp))
            StatItem("최저", min, Color(0xFFF44336), Modifier.weight(1f))
            VerticalDivider(modifier = Modifier.height(36.dp))
            StatItem("평균", avg, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatItem(label: String, pct: Int, color: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text("$pct%", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = color)
    }
}

// MARK: - 개별 바 행

@Composable
private fun BatteryBarRow(pt: DataPointEntity) {
    val timeStr = Instant.ofEpochMilli(pt.timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
    val color = stateColor(pt)
    val pct = pt.batteryPercent.coerceIn(0, 100)

    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 시간 라벨
        Text(
            timeStr,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(70.dp),
        )

        Spacer(Modifier.width(6.dp))

        // 배터리 바
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val filledWidth = maxWidth * pct / 100f
            Box(
                modifier = Modifier
                    .width(filledWidth.coerceAtLeast(4.dp))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = if (pt.isCharging || pt.isDriving) 0.82f else 0.45f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "$pct%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (pct > 15) Color.White else color,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        // 주행가능 km
        Text(
            pt.drivingRangeKm?.takeIf { it > 0 }?.let { "${it.toInt()}km" } ?: "",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

// MARK: - 직접 날짜 범위 선택

private val displayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
private fun CustomDateRangePicker(
    start: LocalDateTime,
    end: LocalDateTime,
    onStartChange: (LocalDateTime) -> Unit,
    onEndChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current

    fun pickDateTime(initial: LocalDateTime, maxDateTime: LocalDateTime?, onPick: (LocalDateTime) -> Unit) {
        DatePickerDialog(ctx, { _, y, m, d ->
            TimePickerDialog(ctx, { _, h, min ->
                val picked = LocalDateTime.of(LocalDate.of(y, m + 1, d), LocalTime.of(h, min))
                onPick(if (maxDateTime != null && picked.isAfter(maxDateTime)) maxDateTime else picked)
            }, initial.hour, initial.minute, true).show()
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("시작", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp))
                OutlinedButton(
                    onClick = { pickDateTime(start, end, onStartChange) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(start.format(displayFmt), style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("종료", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp))
                OutlinedButton(
                    onClick = { pickDateTime(end, null, onEndChange) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(end.format(displayFmt), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// MARK: - 범례

@Composable
private fun LegendRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(chargingColor, "충전")
        LegendDot(drivingColor, "주행")
        LegendDot(parkedColor, "주차")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.75f)),
        )
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
