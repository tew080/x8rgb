// X8RGB file: MainActivity.kt
// Version: v0026
// Fully linked and operational controller for HyperOS RGB lights.
//
package com.x8rgb

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x8rgb.domain.engine.EffectEngine
import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.service.RgbForegroundService
import com.x8rgb.system.RingMode
import com.x8rgb.ui.AppItem
import com.x8rgb.ui.HsvColorPicker
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun setMaster(enabled: Boolean) {
        viewModel.toggleMaster(enabled)
        val intent = Intent(context, RgbForegroundService::class.java)
        if (enabled) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(painterResource(it.icon), contentDescription = stringResource(it.labelRes)) },
                    label = { Text(stringResource(it.labelRes)) },
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
                    onBaseColor = viewModel::setBaseColor,
                    onForceHold = viewModel::setForceHold,
                    onForceHoldColor = viewModel::setForceHoldColor,
                    onAmbientEffect = viewModel::setAmbientEffect,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onGrantShizuku = viewModel::grantViaShizuku,
                    onRequestAudio = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onTestHardware = viewModel::testHardware
                )
                AppDestinations.RULES -> RulesScreen(
                    state = state,
                    modifier = modifier,
                    onAdd = viewModel::addAppRule,
                    onUpdate = viewModel::updateRule,
                    onDelete = viewModel::deleteRule,
                    onPreview = viewModel::previewRule
                )
                AppDestinations.SETTINGS -> SettingsScreen(
                    state = state,
                    modifier = modifier,
                    onQuiet = viewModel::updateQuietHours,
                    onMusicSync = viewModel::toggleMusicSync,
                    onMotionReactive = viewModel::toggleMotionReactive
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: MainUiState,
    modifier: Modifier,
    onMaster: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onMode: (RingMode) -> Unit,
    onBaseColor: (RgbColor) -> Unit,
    onForceHold: (Boolean) -> Unit,
    onForceHoldColor: (RgbColor) -> Unit,
    onAmbientEffect: (Int) -> Unit,
    onRequestShizuku: () -> Unit,
    onGrantShizuku: () -> Unit,
    onRequestAudio: () -> Unit,
    onTestHardware: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

        if (!state.isLoading && !allPermissionsOk(state)) {
            PermissionsCard(state, onRequestShizuku, onGrantShizuku, onRequestAudio)
        }

        ServiceCard(
            enabled = state.settings.masterEnabled,
            brightness = state.settings.brightness,
            onEnabledChange = onMaster,
            onBrightnessChange = { onBrightness(it.toInt()) },
            onTest = onTestHardware
        )

        RingModeCard(
            selected = state.ringMode,
            system = state.systemMode,
            onSelect = onMode,
        )

        BaseAndHoldCard(
            baseColor = RgbColor(state.settings.baseColorPacked),
            forceHold = state.settings.forceHoldEnabled,
            forceColor = RgbColor(state.settings.forceHoldColorPacked),
            onBaseColor = onBaseColor,
            onForceHold = onForceHold,
            onForceHoldColor = onForceHoldColor,
        )

        AmbientEffectCard(
            effectId = state.settings.ambientEffectId,
            baseColor = RgbColor(state.settings.baseColorPacked),
            onEffect = onAmbientEffect,
            onBaseColor = onBaseColor,
        )
    }
}

private fun allPermissionsOk(state: MainUiState): Boolean =
    state.notificationListener && state.postNotifications && 
    state.writeSecureSettings && (!state.settings.musicSyncEnabled || state.recordAudio)

@Composable
private fun PermissionsCard(
    state: MainUiState,
    onRequestShizuku: () -> Unit,
    onGrantShizuku: () -> Unit,
    onRequestAudio: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.perm_required_title), style = MaterialTheme.typography.titleMedium)
            }
            if (!state.notificationListener) {
                Text(stringResource(R.string.perm_notif_listener), style = MaterialTheme.typography.bodySmall)
                val context = LocalContext.current
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                ) { Text(stringResource(R.string.perm_notif_listener_btn)) }
            }
            if (!state.postNotifications) {
                Text(stringResource(R.string.perm_post_notif), style = MaterialTheme.typography.bodySmall)
                val context = LocalContext.current
                Button(
                    onClick = { 
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                ) { Text(stringResource(R.string.perm_post_notif_btn)) }
            }
            if (state.settings.musicSyncEnabled && !state.recordAudio) {
                Text(stringResource(R.string.perm_audio), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onRequestAudio,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                ) { Text(stringResource(R.string.perm_audio_btn)) }
            }
            if (!state.writeSecureSettings) {
                Text(stringResource(R.string.perm_secure_settings), style = MaterialTheme.typography.bodySmall)
                if (state.shizukuAvailable) {
                    if (state.shizukuGranted) {
                        Button(onClick = onGrantShizuku, modifier = Modifier.padding(top = 4.dp)) 
                        { Text(stringResource(R.string.perm_shizuku_grant)) }
                    } else {
                        Button(onClick = onRequestShizuku, modifier = Modifier.padding(top = 4.dp)) 
                        { Text(stringResource(R.string.perm_shizuku_req)) }
                    }
                } else {
                    Text(
                        stringResource(R.string.perm_adb_instruction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
    onTest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.card_service_title), style = MaterialTheme.typography.titleLarge)
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Text(
                text = stringResource(if (enabled) R.string.status_running else R.string.status_stopped),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.label_brightness, brightness))
            Slider(
                value = brightness.toFloat(),
                onValueChange = onBrightnessChange,
                valueRange = 80f..255f,
                enabled = enabled,
            )
            if (enabled) {
                TextButton(onClick = onTest, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.btn_test_ring))
                }
            }
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
            Text(stringResource(R.string.card_ring_mode_title), style = MaterialTheme.typography.titleMedium)
            
            val systemLabel = if (system != null) modeLabel(system) else stringResource(R.string.state_unknown)
            Text(
                stringResource(R.string.label_system_state, systemLabel),
                style = MaterialTheme.typography.bodySmall,
            )
            
            if (selected == RingMode.OFF) {
                Text(
                    stringResource(R.string.warning_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            RingMode.entries.forEach { mode ->
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

@Composable
private fun BaseAndHoldCard(
    baseColor: RgbColor,
    forceHold: Boolean,
    forceColor: RgbColor,
    onBaseColor: (RgbColor) -> Unit,
    onForceHold: (Boolean) -> Unit,
    onForceHoldColor: (RgbColor) -> Unit,
) {
    var editBase by remember { mutableStateOf(false) }
    var editForce by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.card_base_hold_title), style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.label_base_color), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.hint_base_color), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                ColorDot(
                    color = if ((baseColor.r or baseColor.g or baseColor.b) != 0) baseColor else RgbColor(40, 40, 40),
                    selected = false,
                    size = 40.dp,
                    onClick = { editBase = true },
                )
            }
            TextButton(onClick = { onBaseColor(RgbColor.OFF) }) {
                Text(stringResource(R.string.btn_clear_base))
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.label_force_hold), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.hint_force_hold), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Switch(checked = forceHold, onCheckedChange = onForceHold)
            }
            if (forceHold) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.label_force_color), style = MaterialTheme.typography.bodyMedium)
                    ColorDot(
                        color = forceColor,
                        selected = true,
                        size = 40.dp,
                        onClick = { editForce = true },
                    )
                }
            }
        }
    }

    if (editBase) {
        HsvPickerDialog(
            title = stringResource(R.string.dialog_base_color),
            initial = if ((baseColor.r or baseColor.g or baseColor.b) != 0) baseColor else RgbColor(255, 255, 255),
            onPick = { onBaseColor(it); editBase = false },
            onDismiss = { editBase = false },
        )
    }
    if (editForce) {
        HsvPickerDialog(
            title = stringResource(R.string.dialog_force_color),
            initial = forceColor,
            onPick = { onForceHoldColor(it); editForce = false },
            onDismiss = { editForce = false },
        )
    }
}

@Composable
private fun HsvPickerDialog(
    title: String,
    initial: RgbColor,
    onPick: (RgbColor) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(current) }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
        title = { Text(title) },
        text = {
            HsvColorPicker(
                initial = initial,
                onColorChange = { current = it },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun AmbientEffectCard(
    effectId: Int,
    baseColor: RgbColor,
    onEffect: (Int) -> Unit,
    onBaseColor: (RgbColor) -> Unit,
) {
    var pickEffect by remember { mutableStateOf(false) }
    var pickColor by remember { mutableStateOf(false) }
    val name = EffectEngine.EFFECT_NAMES.getOrElse(effectId) { "Static" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.card_ambient_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.hint_ambient), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.label_ambient_effect, name), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { pickEffect = true }) { Text(stringResource(R.string.btn_pick_effect)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.label_ambient_color), style = MaterialTheme.typography.bodyMedium)
                ColorDot(
                    color = if ((baseColor.r or baseColor.g or baseColor.b) != 0) baseColor else RgbColor(255, 255, 255),
                    selected = true,
                    size = 36.dp,
                    onClick = { pickColor = true },
                )
            }
        }
    }

    if (pickEffect) {
        AlertDialog(
            onDismissRequest = { pickEffect = false },
            confirmButton = { TextButton(onClick = { pickEffect = false }) { Text(stringResource(R.string.btn_cancel)) } },
            title = { Text(stringResource(R.string.dialog_select_effect)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(EffectEngine.EFFECT_NAMES.size) { id ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEffect(id); pickEffect = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = effectId == id, onClick = { onEffect(id); pickEffect = false })
                            Text(EffectEngine.EFFECT_NAMES[id])
                        }
                    }
                }
            },
        )
    }
    if (pickColor) {
        HsvPickerDialog(
            title = stringResource(R.string.dialog_base_color),
            initial = if ((baseColor.r or baseColor.g or baseColor.b) != 0) baseColor else RgbColor(255, 255, 255),
            onPick = { onBaseColor(it); pickColor = false },
            onDismiss = { pickColor = false },
        )
    }
}

@Composable
private fun RulesScreen(
    state: MainUiState,
    modifier: Modifier,
    onAdd: (AppItem) -> Unit,
    onUpdate: (NotificationRule, RgbColor, Int) -> Unit,
    onDelete: (Long) -> Unit,
    onPreview: (NotificationRule) -> Unit
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NotificationRule?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.rules_desc), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { pickerOpen = true }) { Text(stringResource(R.string.btn_add_app)) }

        if (state.rules.isEmpty()) {
            Text(stringResource(R.string.empty_rules), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
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
            apps = state.appList,
            onPick = {
                onAdd(it)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }

    editing?.let { rule ->
        EditRuleDialog(
            rule = rule,
            onSave = { color, animId ->
                onUpdate(rule, color, animId)
                editing = null
            },
            onPreview = { onPreview(rule.copy(color = it.first, animationId = it.second)) },
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
                val effectName = EffectEngine.EFFECT_NAMES.getOrElse(rule.animationId) { "Static" }
                Text(
                    text = "$effectName • ${rule.key}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun EditRuleDialog(
    rule: NotificationRule,
    onSave: (RgbColor, Int) -> Unit,
    onPreview: (Pair<RgbColor, Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(rule.color) }
    var selectedAnim by remember { mutableStateOf(rule.animationId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(selectedColor, selectedAnim) }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(rule.displayName)
                Button(onClick = { onPreview(selectedColor to selectedAnim) }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    Text(stringResource(R.string.btn_preview))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.dialog_select_color), style = MaterialTheme.typography.titleSmall)
                HsvColorPicker(
                    initial = selectedColor,
                    onColorChange = { selectedColor = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                Text(stringResource(R.string.dialog_select_effect), style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(EffectEngine.EFFECT_NAMES.size) { id ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAnim = id }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedAnim == id, onClick = { selectedAnim = id })
                            Text(EffectEngine.EFFECT_NAMES[id])
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun AppPickerDialog(
    apps: List<AppItem>,
    onPick: (AppItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } },
        title = { Text(stringResource(R.string.dialog_select_app)) },
        text = {
            if (apps.isEmpty()) {
                Text(stringResource(R.string.loading_apps))
            } else {
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

@Composable
private fun SettingsScreen(
    state: MainUiState,
    modifier: Modifier,
    onQuiet: (Boolean, Int, Int) -> Unit,
    onMusicSync: (Boolean) -> Unit,
    onMotionReactive: (Boolean) -> Unit
) {
    val s = state.settings
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_extra_features), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_music_sync), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = s.musicSyncEnabled, onCheckedChange = onMusicSync)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_motion_reactive), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = s.motionReactiveEnabled, onCheckedChange = onMotionReactive)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_quiet_hours), style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = s.quietHoursEnabled,
                        onCheckedChange = { onQuiet(it, s.quietHoursStart, s.quietHoursEnd) },
                    )
                }
                HourStepper(
                    label = stringResource(R.string.label_start),
                    hour = s.quietHoursStart,
                    onChange = { onQuiet(s.quietHoursEnabled, it, s.quietHoursEnd) },
                )
                HourStepper(
                    label = stringResource(R.string.label_end),
                    hour = s.quietHoursEnd,
                    onChange = { onQuiet(s.quietHoursEnabled, s.quietHoursStart, it) },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleMedium)
                
                val savedModeLabel = modeLabel(state.ringMode)
                Text(stringResource(R.string.status_saved_mode, savedModeLabel), style = MaterialTheme.typography.bodySmall)
                
                val sysModeLabel = if (state.systemMode != null) modeLabel(state.systemMode) else stringResource(R.string.state_unknown)
                Text(
                    stringResource(R.string.label_system_state, sysModeLabel),
                    style = MaterialTheme.typography.bodySmall,
                )
                
                val accessLabel = stringResource(if (state.notificationListener) R.string.state_granted else R.string.state_not_granted)
                Text(
                    stringResource(R.string.status_notif_access, accessLabel),
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
        Text("$label ${stringResource(R.string.label_hour, hour)}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange((hour + 23) % 24) }) { Text(stringResource(R.string.btn_hour_minus)) }
            TextButton(onClick = { onChange((hour + 1) % 24) }) { Text(stringResource(R.string.btn_hour_plus)) }
        }
    }
}

@Composable
private fun modeLabel(mode: RingMode): String = when (mode) {
    RingMode.OFF -> stringResource(R.string.mode_off)
    RingMode.ALWAYS -> stringResource(R.string.mode_always)
    RingMode.FACE_DOWN -> stringResource(R.string.mode_face_down)
}

enum class AppDestinations(val labelRes: Int, val icon: Int) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    RULES(R.string.nav_rules, R.drawable.ic_favorite),
    SETTINGS(R.string.nav_settings, R.drawable.ic_account_box),
}
