package com.ggpark.bydstats.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ggpark.bydstats.android.viewmodel.AppViewModel

private val VEHICLE_BATTERY_MAP = linkedMapOf(
    "아토 3"           to 60.48,
    "돌핀 Standard"   to 44.9,
    "돌핀 Extended"   to 60.4,
    "씰"              to 82.56,
    "씨라이언 7"       to 82.56,
    "씨라이언 6"       to 87.23,
)

private val REGIONS = listOf("KR" to "한국", "EU" to "유럽", "JP" to "일본", "SG" to "싱가포르", "AU" to "호주", "BR" to "브라질", "MX" to "멕시코")
private val POLLING_OPTIONS = listOf(5, 10, 15)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val uiState  by vm.uiState.collectAsState()
    val settings by vm.settings.collectAsState()

    // 로그인 폼 상태
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var region   by remember { mutableStateOf("KR") }
    var showPwd  by remember { mutableStateOf(false) }

    // 기타 설정 상태
    var electricityRateStr by remember(settings.electricityRate) { mutableStateOf(settings.electricityRate.toInt().toString()) }
    var showLogoutDialog   by remember { mutableStateOf(false) }
    var vehicleMenuExpanded by remember { mutableStateOf(false) }
    var regionMenuExpanded  by remember { mutableStateOf(false) }

    val currentVehicleName = VEHICLE_BATTERY_MAP.entries
        .firstOrNull { it.value == settings.batteryCapacityKwh }?.key ?: "직접 선택"
    val currentRegionLabel = REGIONS.firstOrNull { it.first == settings.region }?.second ?: settings.region

    Scaffold(
        topBar = { TopAppBar(title = { Text("설정") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 0.dp),
        ) {

            // ─── BYD 계정 섹션 ───
            SectionHeader("BYD 계정")

            if (uiState.isLoggedIn) {
                // 로그인 된 상태
                PreferenceItem(
                    title = settings.username,
                    subtitle = "로그인됨 · ${settings.region}",
                    icon = Icons.Default.AccountCircle,
                )
                if (uiState.vehicles.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    uiState.vehicles.forEach { vehicle ->
                        PreferenceItem(
                            title = vehicle.modelName,
                            subtitle = vehicle.vin,
                            icon = Icons.Default.DirectionsCar,
                            trailing = {
                                if (vehicle.vin == settings.vin) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = { vm.selectVin(vehicle.vin) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    }
                }
                // 로그아웃
                ListItem(
                    headlineContent = {
                        Text("로그아웃", color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    supportingContent = null,
                )
                HorizontalDivider()

            } else {
                // 로그인 안 된 상태 — iOS처럼 폼을 설정 탭 안에 바로 표시
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // 지역 선택
                    Box {
                        OutlinedCard(
                            onClick = { regionMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ListItem(
                                headlineContent = { Text("지역") },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(currentRegionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            )
                        }
                        DropdownMenu(expanded = regionMenuExpanded, onDismissRequest = { regionMenuExpanded = false }) {
                            REGIONS.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (region == code) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                            else Spacer(Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(label)
                                        }
                                    },
                                    onClick = { region = code; regionMenuExpanded = false }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("아이디 (이메일)") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("비밀번호") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPwd = !showPwd }) {
                                Icon(if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    uiState.loginError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = { vm.login(username.trim(), password, region) },
                        enabled = username.isNotBlank() && password.isNotBlank() && !uiState.isLoggingIn,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isLoggingIn) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("로그인")
                    }
                }
                HorizontalDivider()
            }

            // ─── 차종 섹션 ───
            SectionHeader("차종 (배터리 용량 계산용)")
            Box {
                ListItem(
                    headlineContent = { Text("차종") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currentVehicleName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingContent = { Icon(Icons.Default.DirectionsCar, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
                Surface(onClick = { vehicleMenuExpanded = true }, modifier = Modifier.matchParentSize(), color = androidx.compose.ui.graphics.Color.Transparent) {}
                DropdownMenu(expanded = vehicleMenuExpanded, onDismissRequest = { vehicleMenuExpanded = false }) {
                    VEHICLE_BATTERY_MAP.forEach { (name, kwh) ->
                        DropdownMenuItem(
                            text = { Text("$name ($kwh kWh)") },
                            onClick = { vm.updateBatteryCapacity(kwh); vehicleMenuExpanded = false }
                        )
                    }
                }
            }
            HorizontalDivider()

            // ─── 전기요금 섹션 ───
            SectionHeader("전기요금")
            ListItem(
                headlineContent = { Text("일반 단가") },
                leadingContent = { Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = electricityRateStr,
                            onValueChange = { electricityRateStr = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("원/kWh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { electricityRateStr.toDoubleOrNull()?.let { vm.updateElectricityRate(it) } }) { Text("저장") }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            HorizontalDivider()

            // ─── 폴링 간격 섹션 ───
            SectionHeader("폴링 간격")
            ListItem(
                headlineContent = { Text("주차 중 간격") },
                leadingContent = { Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingContent = {
                    SegmentedButton(settings.pollingIntervalMin, POLLING_OPTIONS) { vm.updatePollingInterval(it) }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            HorizontalDivider()

            // ─── 앱 정보 ───
            Spacer(Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("BYD Stats v0.3.1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("로그아웃") },
            text = { Text("로그아웃하면 저장된 세션 정보가 초기화됩니다.") },
            confirmButton = {
                TextButton(onClick = { vm.logout(); showLogoutDialog = false }) {
                    Text("로그아웃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("취소") } }
        )
    }
}

// ─── 재사용 컴포넌트 ───

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun PreferenceItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = trailing,
        modifier = if (onClick != null) Modifier.fillMaxWidth() else Modifier,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
    if (onClick != null) {
        // 클릭 오버레이는 Surface로 처리
    }
}

@Composable
private fun SegmentedButton(selected: Int, options: List<Int>, onSelect: (Int) -> Unit) {
    Row {
        options.forEachIndexed { idx, opt ->
            val isFirst = idx == 0
            val isLast  = idx == options.lastIndex
            FilterChip(
                selected = selected == opt,
                onClick  = { onSelect(opt) },
                label    = { Text("${opt}분") },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
