// X8RGB file: MainActivity.kt
// Version: v0015
// Path: app/src/main/java/com/example/x8rgb/MainActivity.kt
// History:
//   0002: dashboard with master switch and brightness.
//   0015: three screens (home, rules, settings); ring mode; rule editor; quiet hours.
//
package com.x8rgb

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.service.RgbForegroundService
import com.x8rgb.system.RingMode
import com.x8rgb.ui.AppItem
import com.x8rgb.ui.MainUiState
import com.x8rgb.ui.MainViewModel
import com.x8rgb.ui.RING_PALETTE
import com.x8rgb.ui.theme.X8rgbTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            X8rgbTheme {
                X8rgbApp()
            }
        }
    }
}

@Composable
fun X8rgbApp(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var destination by remember { mutableStateOf(AppDestinations.HOME) }

    fun setMaster(enabled: Boolean) {
        viewModel.toggleMaster(enabled)
        val intent = Intent(context, RgbForegroundService::class.java)
        if (enabled) context.startForegroundService(intent) else context.stopService(intent)
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(painterResource(it.icon), contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == destination,
                    onClick = { destination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
            val modifier = Modifier.padding(inner)
            when (destination) {
                AppDestinations.HOME -> HomeScreen(
                    state = state,
                    modifier = modifier,
                    onMaster = ::setMaster,
                    onBrightness = viewModel::updateBrightness,
                    onMode = { viewModel.setRingMode(it) },
                )
                AppDestinations.FAVORITES -> RulesScreen(
                    state = state,
                    modifier = modifier,
                    loadApps = viewModel::installedApps,
                    onAdd = viewModel::addAppRule,
                    onColor = viewModel::setRuleColor,
                    onDelete = viewModel::deleteRule,
                )
                AppDestinations.PROFILE -> SettingsScreen(
                    state = state,
                    modifier = modifier,
                    onQuiet = viewModel::updateQuietHours,
                )
            }
        }
    }
}

// ── Home ──────────────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(
    state: MainUiState,
    modifier: Modifier,
    onMaster: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onMode: (RingMode) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("X8RGB Controller", style = MaterialTheme.typography.headlineMedium)

        if (!state.isLoading && !allPermissionsOk(state)) {
            PermissionsCard(state)
        }

        ServiceCard(
            enabled = state.settings.masterEnabled,
            brightness = state.settings.brightness,
            onEnabledChange = onMaster,
            onBrightnessChange = { onBrightness(it.toInt()) },
        )

        RingModeCard(
            selected = state.ringMode,
            system = state.systemMode,
            onSelect = onMode,
        )
    }
}

private fun allPermissionsOk(state: MainUiState): Boolean =
    state.notificationListener && state.postNotifications && state.writeSecureSettings

@Composable
private fun PermissionsCard(state: MainUiState) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("ต้องให้สิทธิ์เพิ่มเติม", style = MaterialTheme.typography.titleMedium)
            }
            if (!state.notificationListener) {
                Text("การเข้าถึงการแจ้งเตือน: ยังไม่เปิด", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                ) { Text("เปิดการเข้าถึงการแจ้งเตือน") }
            }
            if (!state.postNotifications) {
                Text("การแสดงการแจ้งเตือนของแอป: ปิดอยู่", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                ) { Text("เปิดการแจ้งเตือนของแอป") }
            }
            if (!state.writeSecureSettings) {
                Text(
                    "สิทธิ์ควบคุมเอฟเฟกต์ย้อนแสงยังไม่ได้รับ ให้รันครั้งเดียวจากคอมพิวเตอร์:\n" +
                            "adb shell pm grant com.x8rgb android.permission.WRITE_SECURE_SETTINGS",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ServiceCard(
    enabled: Boolean,
    brightness: Int,
    onEnabledChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("เปิดการควบคุมไฟ", style = MaterialTheme.typography.titleLarge)
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Text(
                text = if (enabled) "กำลังทำงาน" else "หยุดทำงาน",
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(16.dp))
            Text("ความสว่าง: $brightness")
            Slider(
                value = brightness.toFloat(),
                onValueChange = onBrightnessChange,
                valueRange = 80f..255f,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun RingModeCard(
    selected: RingMode,
    system: RingMode?,
    onSelect: (RingMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("เอฟเฟกต์ย้อนแสงของระบบ", style = MaterialTheme.typography.titleMedium)
            Text(
                "สถานะจริงของระบบ: ${system?.let { modeLabel(it) } ?: "อ่านไม่ได้"}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (selected == RingMode.OFF) {
                Text(
                    "ปิดอยู่ ไฟของแอปจะไม่ทำงานจนกว่าจะเลือกโหมดอื่น",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            RingMode.values().forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
                    Text(modeLabel(mode))
                }
            }
        }
    }
}

// ── Rules ─────────────────────────────────────────────────────────────────────

@Composable
private fun RulesScreen(
    state: MainUiState,
    modifier: Modifier,
    loadApps: () -> List<AppItem>,
    onAdd: (AppItem) -> Unit,
    onColor: (NotificationRule, RgbColor) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NotificationRule?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("กฎแจ้งเตือนตามแอป", style = MaterialTheme.typography.headlineSmall)
        Text(
            "แจ้งเตือนจากแอปที่มีกฎ จะเปลี่ยนไฟเป็นสีที่เลือก และคืนไฟเมื่อแจ้งเตือนหายไป",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = { pickerOpen = true }) { Text("เพิ่มแอป") }
        Text(
            "กฎตามผู้ติดต่อยังไม่รองรับในหน้านี้",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        if (state.rules.isEmpty()) {
            Text("ยังไม่มีกฎ", style = MaterialTheme.typography.bodyLarge)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.rules, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
                    onEdit = { editing = rule },
                    onDelete = { onDelete(rule.id) },
                )
            }
        }
    }

    if (pickerOpen) {
        AppPickerDialog(
            loadApps = loadApps,
            onPick = {
                onAdd(it)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }

    editing?.let { rule ->
        ColorDialog(
            current = rule.color,
            onPick = { c ->
                onColor(rule, c)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RuleRow(rule: NotificationRule, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorDot(rule.color, selected = false, size = 32.dp, onClick = onEdit)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(rule.displayName, style = MaterialTheme.typography.titleMedium)
                Text(rule.key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            TextButton(onClick = onDelete) { Text("ลบ") }
        }
    }
}

@Composable
private fun AppPickerDialog(
    loadApps: () -> List<AppItem>,
    onPick: (AppItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val apps = remember { loadApps() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("ปิด") } },
        title = { Text("เลือกแอป") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Text(
                        text = app.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun ColorDialog(
    current: RgbColor,
    onPick: (RgbColor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("ปิด") } },
        title = { Text("เลือกสี") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RING_PALETTE.chunked(4).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowColors.forEach { c ->
                            ColorDot(
                                color = c,
                                selected = c.packed shr 8 == current.packed shr 8,
                                size = 48.dp,
                                onClick = { onPick(c) },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ColorDot(
    color: RgbColor,
    selected: Boolean,
    size: Dp,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier.size(size).clip(CircleShape)
    val withClick = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(
        modifier = withClick
            .background(Color(color.r, color.g, color.b))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
    )
}

// ── Settings ──────────────────────────────────────────────────────────────────

@Composable
private fun SettingsScreen(
    state: MainUiState,
    modifier: Modifier,
    onQuiet: (Boolean, Int, Int) -> Unit,
) {
    val s = state.settings
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ตั้งค่า", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ช่วงเวลาห้ามรบกวน", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = s.quietHoursEnabled,
                        onCheckedChange = { onQuiet(it, s.quietHoursStart, s.quietHoursEnd) },
                    )
                }
                HourStepper(
                    label = "เริ่ม",
                    hour = s.quietHoursStart,
                    onChange = { onQuiet(s.quietHoursEnabled, it, s.quietHoursEnd) },
                )
                HourStepper(
                    label = "สิ้นสุด",
                    hour = s.quietHoursEnd,
                    onChange = { onQuiet(s.quietHoursEnabled, s.quietHoursStart, it) },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("สถานะ", style = MaterialTheme.typography.titleMedium)
                Text("โหมดที่บันทึก: ${modeLabel(state.ringMode)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "สถานะจริงของระบบ: ${state.systemMode?.let { modeLabel(it) } ?: "อ่านไม่ได้"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "สิทธิ์การเข้าถึงการแจ้งเตือน: ${if (state.notificationListener) "ได้รับแล้ว" else "ยังไม่ได้รับ"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$label ${"%02d".format(hour)}:00")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange((hour + 23) % 24) }) { Text("-1 ชม.") }
            TextButton(onClick = { onChange((hour + 1) % 24) }) { Text("+1 ชม.") }
        }
    }
}

// ── Helpers and navigation ────────────────────────────────────────────────────

private fun modeLabel(mode: RingMode): String = when (mode) {
    RingMode.OFF -> "ปิด"
    RingMode.ALWAYS -> "เปิดตลอด"
    RingMode.FACE_DOWN -> "เปิดเมื่อคว่ำจอ"
}

enum class AppDestinations(val label: String, val icon: Int) {
    HOME("หน้าหลัก", R.drawable.ic_home),
    FAVORITES("กฎแจ้งเตือน", R.drawable.ic_favorite),
    PROFILE("ตั้งค่า", R.drawable.ic_account_box),
}