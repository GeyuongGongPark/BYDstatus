package com.ggpark.bydstats.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.ggpark.bydstats.android.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BydStatsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetDataStore.load(context)
        provideContent {
            WidgetContent(snapshot)
        }
    }
}

class BydStatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BydStatsWidget()
}

@Composable
private fun WidgetContent(snap: WidgetSnapshot) {
    val battColor = when {
        snap.batteryPercent >= 60 -> Color(0xFF4CAF50)
        snap.batteryPercent >= 30 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    val stateLabel = when {
        snap.isCharging -> "충전 중"
        snap.isDriving  -> "주행 중"
        else            -> "주차 중"
    }
    val stateColor = when {
        snap.isCharging -> Color(0xFF4CAF50)
        snap.isDriving  -> Color(0xFF2196F3)
        else            -> Color(0xFF9E9E9E)
    }

    val updatedStr = if (snap.lastUpdated > 0L) {
        val diff = (System.currentTimeMillis() - snap.lastUpdated) / 60_000
        if (diff < 1) "방금 전" else "${diff}분 전"
    } else "—"

    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF12121A))
                .clickable(actionStartActivity<MainActivity>())
                .padding(14.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // 상단: 앱 이름
                Text(
                    "BYD Stats",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF888899)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.height(6.dp))

                // 배터리 %
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${snap.batteryPercent}",
                        style = TextStyle(
                            color = ColorProvider(battColor),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        "%",
                        style = TextStyle(
                            color = ColorProvider(battColor.copy(alpha = 0.7f)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Spacer(GlanceModifier.height(4.dp))

                // 상태 뱃지
                Text(
                    stateLabel,
                    style = TextStyle(
                        color = ColorProvider(stateColor),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )

                Spacer(GlanceModifier.defaultWeight())

                // 하단: 주행가능 / kW
                Row {
                    if (snap.drivingRangeKm > 0) {
                        Text(
                            "🛣 ${snap.drivingRangeKm.toInt()} km",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFAAAAAA)),
                                fontSize = 10.sp,
                            ),
                        )
                    }
                    if (snap.isCharging && snap.instantPowerKw > 0) {
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            "⚡ ${"%.1f".format(snap.instantPowerKw)} kW",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF4CAF50)),
                                fontSize = 10.sp,
                            ),
                        )
                    }
                }
                Text(
                    updatedStr,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF555566)),
                        fontSize = 9.sp,
                    ),
                )
            }
        }
    }
}
