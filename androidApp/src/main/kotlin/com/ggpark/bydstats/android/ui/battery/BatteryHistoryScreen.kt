package com.ggpark.bydstats.android.ui.battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ggpark.bydstats.android.data.entity.DataPointEntity
import com.ggpark.bydstats.android.viewmodel.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class BattRange(val label: String, val durationMs: Long?) {
    DAY("24시간", 24 * 3600 * 1000L),
    WEEK("7일", 7 * 24 * 3600 * 1000L),
    MONTH("30일", 30 * 24 * 3600 * 1000L),
    ALL("전체", null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryHistoryScreen(vm: AppViewModel) {
    val allPoints by vm.dataPoints.collectAsState(emptyList())
    var selectedRange by remember { mutableStateOf(BattRange.DAY) }
    var selectedIdx by remember { mutableStateOf<Int?>(null) }

    val now = remember { System.currentTimeMillis() }
    val filteredPoints = remember(allPoints, selectedRange, now) {
        val d = selectedRange.durationMs
        if (d == null) allPoints else allPoints.filter { it.timestamp >= now - d }
    }

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
                .verticalScroll(rememberScrollState()),
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
                        onClick = { selectedRange = range; selectedIdx = null },
                        label = { Text(range.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (filteredPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.ShowChart, null,
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

            // 통계 카드
            stats?.let { (max, min, avg) ->
                ElevatedCard(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        BattStatItem("최고", max, Color(0xFF4CAF50), Modifier.weight(1f))
                        VerticalDivider(modifier = Modifier.height(40.dp))
                        BattStatItem("최저", min, Color(0xFFF44336), Modifier.weight(1f))
                        VerticalDivider(modifier = Modifier.height(40.dp))
                        BattStatItem("평균", avg, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 선택 포인트 정보
            selectedIdx?.let { idx ->
                filteredPoints.getOrNull(idx)?.let { pt ->
                    SelectedPointCard(pt)
                    Spacer(Modifier.height(8.dp))
                }
            }

            // 차트
            val primaryColor = MaterialTheme.colorScheme.primary
            val outlineColor = MaterialTheme.colorScheme.outlineVariant
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            val surfaceColor = MaterialTheme.colorScheme.surface
            val textMeasurer = rememberTextMeasurer()

            BatteryLineChart(
                points = filteredPoints,
                selectedIdx = selectedIdx,
                onSelectIdx = { selectedIdx = it },
                primaryColor = primaryColor,
                outlineColor = outlineColor,
                labelColor = labelColor,
                surfaceColor = surfaceColor,
                textMeasurer = textMeasurer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(12.dp))

            // 범례
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LegendDot(Color(0xFF4CAF50), "충전")
                LegendDot(Color(0xFF2196F3), "주행")
                LegendDot(Color(0xFF9E9E9E).copy(alpha = 0.5f), "주차")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BattStatItem(label: String, pct: Int, color: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "$pct%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun SelectedPointCard(pt: DataPointEntity) {
    val timeStr = Instant.ofEpochMilli(pt.timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
    val (stateLabel, stateColor) = when {
        pt.isCharging -> "충전 중" to Color(0xFF4CAF50)
        pt.isDriving  -> "주행 중" to Color(0xFF2196F3)
        else          -> "주차 중" to Color(0xFF9E9E9E)
    }

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${pt.batteryPercent}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(shape = MaterialTheme.shapes.small, color = stateColor.copy(alpha = 0.15f)) {
                Text(
                    stateLabel,
                    color = stateColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (pt.isCharging && (pt.chargingPowerKw ?: 0.0) > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "${"%.1f".format(pt.chargingPowerKw!!)} kW",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4CAF50),
                )
            }
        }
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
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BatteryLineChart(
    points: List<DataPointEntity>,
    selectedIdx: Int?,
    onSelectIdx: (Int?) -> Unit,
    primaryColor: Color,
    outlineColor: Color,
    labelColor: Color,
    surfaceColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    val labelStyle = TextStyle(
        color = labelColor,
        fontSize = 9.sp,
    )

    val minT = points.first().timestamp
    val maxT = points.last().timestamp
    val tRange = (maxT - minT).toFloat().coerceAtLeast(1f)

    Canvas(
        modifier = modifier.pointerInput(points) {
            detectTapGestures { offset ->
                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                val targetT = minT + frac * tRange
                val idx = points.indices.minByOrNull {
                    kotlin.math.abs(points[it].timestamp - targetT)
                }
                onSelectIdx(if (idx == selectedIdx) null else idx)
            }
        }
    ) {
        val w = size.width
        val h = size.height
        val padLeft = 28.dp.toPx()
        val padBottom = 16.dp.toPx()
        val chartW = w - padLeft
        val chartH = h - padBottom

        fun xOf(t: Long) = padLeft + (t - minT).toFloat() / tRange * chartW
        fun yOf(pct: Int) = chartH * (1f - pct / 100f)

        // 그리드 라인 + Y축 레이블
        for (pct in listOf(25, 50, 75, 100)) {
            val y = yOf(pct)
            drawLine(outlineColor.copy(alpha = 0.3f), Offset(padLeft, y), Offset(w, y), 1.dp.toPx())
            val measured = textMeasurer.measure("$pct%", labelStyle)
            drawText(measured, topLeft = Offset(0f, y - measured.size.height / 2f))
        }

        // 상태 배경 구간
        var segStart = points[0]
        for (i in 1 until points.size) {
            val pt = points[i]
            val changed = pt.isCharging != segStart.isCharging || pt.isDriving != segStart.isDriving
            if (changed) {
                val segColor = when {
                    segStart.isCharging -> Color(0xFF4CAF50)
                    segStart.isDriving  -> Color(0xFF2196F3)
                    else -> null
                }
                segColor?.let {
                    drawRect(
                        color = it.copy(alpha = 0.1f),
                        topLeft = Offset(xOf(segStart.timestamp), 0f),
                        size = Size(xOf(pt.timestamp) - xOf(segStart.timestamp), chartH),
                    )
                }
                segStart = pt
            }
        }
        val lastColor = when {
            segStart.isCharging -> Color(0xFF4CAF50)
            segStart.isDriving  -> Color(0xFF2196F3)
            else -> null
        }
        lastColor?.let {
            drawRect(
                color = it.copy(alpha = 0.1f),
                topLeft = Offset(xOf(segStart.timestamp), 0f),
                size = Size(w - xOf(segStart.timestamp), chartH),
            )
        }

        // 에어리어 그라디언트
        val areaPath = Path().apply {
            moveTo(xOf(points[0].timestamp), chartH)
            points.forEach { lineTo(xOf(it.timestamp), yOf(it.batteryPercent)) }
            lineTo(xOf(points.last().timestamp), chartH)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                listOf(primaryColor.copy(alpha = 0.3f), primaryColor.copy(alpha = 0.02f)),
                startY = 0f,
                endY = chartH,
            ),
        )

        // 라인
        val linePath = Path().apply {
            moveTo(xOf(points[0].timestamp), yOf(points[0].batteryPercent))
            for (i in 1 until points.size) {
                lineTo(xOf(points[i].timestamp), yOf(points[i].batteryPercent))
            }
        }
        drawPath(
            path = linePath,
            color = primaryColor,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 선택 포인트
        selectedIdx?.let { idx ->
            val pt = points.getOrNull(idx) ?: return@let
            val x = xOf(pt.timestamp)
            val y = yOf(pt.batteryPercent)
            drawLine(
                color = primaryColor.copy(alpha = 0.4f),
                start = Offset(x, 0f),
                end = Offset(x, chartH),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)),
            )
            drawCircle(surfaceColor, 8.dp.toPx(), Offset(x, y))
            drawCircle(primaryColor, 5.dp.toPx(), Offset(x, y))
        }
    }
}
