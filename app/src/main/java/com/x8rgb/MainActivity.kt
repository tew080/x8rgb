package com.x8rgb

import android.app.LocaleManager
import android.content.Intent
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.domain.engine.EffectEngine
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.service.RgbForegroundService
import com.x8rgb.system.RingMode
import com.x8rgb.ui.EffectPickerDialog
import com.x8rgb.ui.HsvColorPicker
import com.x8rgb.ui.InfoDialog
import com.x8rgb.ui.MainUiState
import com.x8rgb.ui.MainViewModel
import com.x8rgb.ui.SectionCard
import com.x8rgb.ui.effectName
import com.x8rgb.ui.theme.X8rgbTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

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

/** Top-level destinations. Each has an icon, a title and an explanation for the info button. */
enum class AppDestinations(val labelRes: Int, val icon: ImageVector, val infoRes: Int) {
    HOME(R.string.nav_home, Icons.Default.Home, R.string.info_home),
    SETTINGS(R.string.nav_settings, Icons.Default.Settings, R.string.info_settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun X8rgbApp(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showInfo by rememberSaveable { mutableStateOf(false) }

    fun setMaster(enabled: Boolean) {
        viewModel.toggleMaster(enabled)
        val intent = Intent(context, RgbForegroundService::class.java)
        if (enabled) context.startForegroundService(intent)
        else context.stopService(intent)
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { dest ->
                item(
                    icon = { Icon(dest.icon, contentDescription = null) },
                    label = { Text(stringResource(dest.labelRes)) },
                    selected = dest == destination,
                    onClick = { destination = dest },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(destination.labelRes)) },
                    actions = {
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.info_action))
                        }
                    },
                )
            },
        ) { inner ->
            val modifier = Modifier.padding(inner)
            when (destination) {
                AppDestinations.HOME -> HomeScreen(
                    state = state,
                    modifier = modifier,
                    onMaster = ::setMaster,
                    onBrightness = viewModel::updateBrightness,
                    onMode = { viewModel.setRingMode(it) },
                    onBaseColor = viewModel::setBaseColor,
                    onAmbientEffect = viewModel::setAmbientEffect,
                    onForceHold = viewModel::setForceHold,
                    onForceHoldColor = viewModel::setForceHoldColor,
                    onForceHoldEffect = viewModel::setForceHoldEffect,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onGrantShizuku = viewModel::grantViaShizuku,
                    onTestHardware = viewModel::testHardware,
                )
                AppDestinations.SETTINGS -> SettingsScreen(
                    state = state,
                    modifier = modifier,
                )
            }
        }
    }

    if (showInfo) {
        InfoDialog(
            title = stringResource(destination.labelRes),
            body = stringResource(destination.infoRes),
            onDismiss = { showInfo = false },
        )
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
    onAmbientEffect: (Int) -> Unit,
    onForceHold: (Boolean) -> Unit,
    onForceHoldColor: (RgbColor) -> Unit,
    onForceHoldEffect: (Int) -> Unit,
    onRequestShizuku: () -> Unit,
    onGrantShizuku: () -> Unit,
    onTestHardware: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!state.isLoading && !allPermissionsOk(state)) {
            PermissionsCard(state, onRequestShizuku, onGrantShizuku)
        }

        ServiceCard(
            enabled = state.settings.masterEnabled,
            brightness = state.settings.brightness,
            onEnabledChange = onMaster,
            onBrightnessChange = { onBrightness(it.toInt()) },
            onTest = onTestHardware,
        )

        RingPreviewCard(settings = state.settings, ringMode = state.ringMode)

        RingModeCard(
            selected = state.ringMode,
            system = state.systemMode,
            onSelect = onMode,
        )

        AmbientEffectCard(
            effectId = state.settings.ambientEffectId,
            baseColor = RgbColor(state.settings.baseColorPacked),
            onEffect = onAmbientEffect,
            onBaseColor = onBaseColor,
        )

        LockCard(
            enabled = state.settings.forceHoldEnabled,
            color = RgbColor(state.settings.forceHoldColorPacked),
            effectId = state.settings.forceHoldEffectId,
            onEnabled = onForceHold,
            onColor = onForceHoldColor,
            onEffect = onForceHoldEffect,
        )
    }
}

private fun allPermissionsOk(state: MainUiState): Boolean =
    state.notificationListener && state.postNotifications && state.writeSecureSettings

@Composable
private fun PermissionsCard(
    state: MainUiState,
    onRequestShizuku: () -> Unit,
    onGrantShizuku: () -> Unit,
) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.perm_required_title), style = MaterialTheme.typography.titleMedium)
            }
            if (!state.notificationListener) {
                Text(stringResource(R.string.perm_notif_listener), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                ) { Text(stringResource(R.string.perm_notif_listener_btn)) }
            }
            if (!state.postNotifications) {
                Text(stringResource(R.string.perm_post_notif), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                ) { Text(stringResource(R.string.perm_post_notif_btn)) }
            }
            if (!state.writeSecureSettings) {
                Text(stringResource(R.string.perm_secure_settings), style = MaterialTheme.typography.bodySmall)
                if (state.shizukuAvailable) {
                    if (state.shizukuGranted) {
                        Button(onClick = onGrantShizuku, modifier = Modifier.padding(top = 4.dp)) {
                            Text(stringResource(R.string.perm_shizuku_grant))
                        }
                    } else {
                        Button(onClick = onRequestShizuku, modifier = Modifier.padding(top = 4.dp)) {
                            Text(stringResource(R.string.perm_shizuku_req))
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.perm_adb_instruction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
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
    onTest: () -> Unit,
) {
    SectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.card_service_title), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Text(
                text = stringResource(if (enabled) R.string.status_running else R.string.status_stopped),
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.label_brightness, brightness), style = MaterialTheme.typography.bodyMedium)
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
    SectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.card_ring_mode_title), style = MaterialTheme.typography.titleMedium)
            val systemLabel = if (system != null) modeLabel(system) else stringResource(R.string.state_unknown)
            Text(
                stringResource(R.string.label_system_state, systemLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
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
                    Text(modeLabel(mode), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
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

    SectionCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.card_ambient_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.hint_ambient),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.label_ambient_effect, effectName(effectId)), style = MaterialTheme.typography.bodyLarge)
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
        EffectPickerDialog(
            title = stringResource(R.string.dialog_select_effect),
            ids = EffectEngine.SELECTABLE_IDS,
            selectedId = effectId,
            onPick = { onEffect(it); pickEffect = false },
            onDismiss = { pickEffect = false },
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
private fun LockCard(
    enabled: Boolean,
    color: RgbColor,
    effectId: Int,
    onEnabled: (Boolean) -> Unit,
    onColor: (RgbColor) -> Unit,
    onEffect: (Int) -> Unit,
) {
    var pickEffect by remember { mutableStateOf(false) }
    var pickColor by remember { mutableStateOf(false) }

    SectionCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.card_lock_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.hint_lock),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.label_force_hold), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }
            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.label_force_color), style = MaterialTheme.typography.bodyMedium)
                    ColorDot(color = color, selected = true, size = 36.dp, onClick = { pickColor = true })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.label_lock_effect, lockEffectLabel(effectId)), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { pickEffect = true }) { Text(stringResource(R.string.btn_pick_effect)) }
                }
            }
        }
    }

    if (pickEffect) {
        EffectPickerDialog(
            title = stringResource(R.string.dialog_select_effect),
            ids = LOCK_EFFECT_ORDER,
            selectedId = effectId,
            onPick = { onEffect(it); pickEffect = false },
            onDismiss = { pickEffect = false },
            labelOf = { lockEffectLabel(it) },
        )
    }
    if (pickColor) {
        HsvPickerDialog(
            title = stringResource(R.string.dialog_force_color),
            initial = color,
            onPick = { onColor(it); pickColor = false },
            onDismiss = { pickColor = false },
        )
    }
}

/** Lock effect picker order: "no effect" first, then the animated effects. */
private val LOCK_EFFECT_ORDER: List<Int> =
    listOf(EffectEngine.EFFECT_STATIC) + EffectEngine.SELECTABLE_IDS.filter { it != EffectEngine.EFFECT_STATIC }

@Composable
private fun lockEffectLabel(id: Int): String =
    if (id == EffectEngine.EFFECT_STATIC) stringResource(R.string.lock_no_effect) else effectName(id)

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

/** Language and permission status. Nothing else lives in Settings. */
@Composable
private fun SettingsScreen(state: MainUiState, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LanguageCard()
        PermissionStatusCard(state)
    }
}

/**
 * Per-app language: follows the system by default, or forced to Thai / English.
 * Changing it does not recreate the activity (see configChanges in the manifest), so no black restart flash.
 */
@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    val localeManager = context.getSystemService(LocaleManager::class.java)
    var selected by remember { mutableStateOf(localeManager.applicationLocales.toLanguageTags()) }

    fun choose(tag: String) {
        selected = tag
        localeManager.applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    SectionCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            listOf("" to R.string.lang_system, "th" to R.string.lang_th, "en" to R.string.lang_en).forEach { (tag, labelRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { choose(tag) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == tag, onClick = { choose(tag) })
                    Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(state: MainUiState) {
    SectionCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.perm_status_title), style = MaterialTheme.typography.titleMedium)
            PermissionRow(stringResource(R.string.perm_status_notif), state.notificationListener)
            PermissionRow(stringResource(R.string.perm_status_post), state.postNotifications)
            PermissionRow(stringResource(R.string.perm_status_secure), state.writeSecureSettings)
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            stringResource(if (granted) R.string.state_granted else R.string.state_not_granted),
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
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
private fun modeLabel(mode: RingMode): String = when (mode) {
    RingMode.OFF -> stringResource(R.string.mode_off)
    RingMode.ALWAYS -> stringResource(R.string.mode_always)
    RingMode.FACE_DOWN -> stringResource(R.string.mode_face_down)
}

@Composable
private fun RingPreviewCard(settings: AppSettingsEntity, ringMode: RingMode) {
    val engine = remember { EffectEngine() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(50L)   // same rate as the service loop
            tick++
        }
    }
    val c = previewFrame(settings, ringMode, tick, engine)
    val ringColor = Color(c.r, c.g, c.b)

    SectionCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.card_preview_title), style = MaterialTheme.typography.titleMedium)
            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val cy = size.height / 2f
                val lensR = 22.dp.toPx()
                val ringR = lensR + 10.dp.toPx()
                val glowW = 14.dp.toPx()
                val coreW = 5.dp.toPx()
                // two lenses side by side
                for (cx in listOf(size.width * 0.35f, size.width * 0.65f)) {
                    drawCircle(Color(0xFF0B0B0B), radius = lensR, center = Offset(cx, cy))
                    val topLeft = Offset(cx - ringR, cy - ringR)
                    val arcSize = Size(ringR * 2, ringR * 2)
                    for (seg in 0 until 4) {
                        val start = seg * 90f + 6f   // 4 segments, 12 degree gap
                        drawArc(ringColor.copy(alpha = 0.25f), start, 78f, false, topLeft, arcSize,
                            style = Stroke(width = glowW, cap = StrokeCap.Round))
                        drawArc(ringColor, start, 78f, false, topLeft, arcSize,
                            style = Stroke(width = coreW, cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}

/** Mirrors the decision order of RgbForegroundService: master → lock → ambient effect → brightness. */
private fun previewFrame(s: AppSettingsEntity, mode: RingMode, t: Long, engine: EffectEngine): RgbColor {
    if (!s.masterEnabled || mode == RingMode.OFF) return RgbColor.OFF
    val frame = if (s.forceHoldEnabled) {
        val lock = RgbColor(s.forceHoldColorPacked)
        if (s.forceHoldEffectId == EffectEngine.EFFECT_STATIC) lock else engine.compute(s.forceHoldEffectId, t, lock)
    } else {
        val base = RgbColor(s.baseColorPacked)
        val seed = if (base != RgbColor.OFF) base else RgbColor(255, 255, 255)
        when {
            s.ambientEffectId == EffectEngine.EFFECT_STATIC && base == RgbColor.OFF -> RgbColor.OFF
            s.ambientEffectId == EffectEngine.EFFECT_STATIC -> seed
            else -> engine.compute(s.ambientEffectId, t, seed)
        }
    }
    val k = s.brightness / 255f
    return RgbColor((frame.r * k).toInt(), (frame.g * k).toInt(), (frame.b * k).toInt())
}
