package com.x8rgb.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.data.repository.SettingsRepository
import com.x8rgb.domain.engine.EffectEngine
import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.domain.model.RuleType
import com.x8rgb.service.RgbForegroundService
import com.x8rgb.system.MusicLightManager
import com.x8rgb.system.PermissionStateChecker
import com.x8rgb.system.RingBrightnessController
import com.x8rgb.system.RingMode
import com.x8rgb.system.RingSwitch
import com.x8rgb.system.ShizukuGrantHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppItem(val packageName: String, val label: String)

val RING_PALETTE: List<RgbColor> = listOf(
    RgbColor(255, 255, 255),
    RgbColor(255, 0, 0),
    RgbColor(255, 120, 0),
    RgbColor(255, 220, 0),
    RgbColor(0, 200, 0),
    RgbColor(0, 200, 200),
    RgbColor(0, 80, 255),
    RgbColor(120, 80, 255),
)

data class MainUiState(
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val rules: List<NotificationRule> = emptyList(),
    val ringMode: RingMode = RingMode.ALWAYS,
    val systemMode: RingMode? = null,
    val notificationListener: Boolean = false,
    val writeSecureSettings: Boolean = false,
    val postNotifications: Boolean = true,
    val recordAudio: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false,
    val isLoading: Boolean = true,
    val appList: List<AppItem> = emptyList(),
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ruleRepository: RuleRepository,
    private val permissionChecker: PermissionStateChecker,
    private val brightnessController: RingBrightnessController,
    private val musicLightManager: MusicLightManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _appList = MutableStateFlow<List<AppItem>>(emptyList())

    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(500)   // เดิม 2000: ทำให้สถานะที่สลับจากระบบแสดงช้า
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        settingsRepository.observe(),
        ruleRepository.observeAll(),
        _appList,
        ticker,
    ) { settings, rules, apps, _ ->
        val p = permissionChecker.check()
        MainUiState(
            settings = settings ?: AppSettingsEntity(),
            rules = rules.filter { it.type == RuleType.APP }.sortedBy { it.displayName.lowercase() },
            ringMode = RingSwitch.savedMode(context),
            systemMode = RingSwitch.systemMode(context),
            notificationListener = p.notificationListener,
            writeSecureSettings = p.writeSecureSettings,
            postNotifications = p.postNotifications,
            recordAudio = p.recordAudio,
            shizukuAvailable = p.shizukuAvailable,
            shizukuGranted = p.shizukuGranted,
            isLoading = false,
            appList = apps
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(),
    )

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
            _appList.value = apps
        }
    }

    fun toggleMaster(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateMasterEnabled(enabled) }
    }

    fun updateBrightness(brightness: Int) {
        viewModelScope.launch {
            settingsRepository.updateBrightness(brightness)
            if (brightnessController.hasPermission()) {
                brightnessController.write(brightness)
            }
        }
    }

    fun setRingMode(mode: RingMode): Boolean = RingSwitch.set(context, mode)

    fun updateQuietHours(enabled: Boolean, start: Int, end: Int) {
        viewModelScope.launch { settingsRepository.updateQuietHours(enabled, start, end) }
    }

    /** ไม่ปิดไฟเพลงของระบบแล้ว: ให้ระบบใช้งานได้ตามปกติ แอปจะ yield เอง */
    fun toggleMusicSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateMusicSync(enabled)
            musicLightManager.setPackageEnabled(context.packageName, enabled)
        }
    }

    fun toggleMotionReactive(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateMotionReactive(enabled) }
    }

    fun setBaseColor(color: RgbColor) {
        viewModelScope.launch { settingsRepository.updateBaseColor(color.packed) }
    }

    fun setForceHold(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateForceHold(enabled) }
    }

    fun setForceHoldColor(color: RgbColor) {
        viewModelScope.launch { settingsRepository.updateForceHoldColor(color.packed) }
    }

    fun setForceHoldEffect(effectId: Int) {
        viewModelScope.launch { settingsRepository.updateForceHoldEffect(effectId) }
    }

    fun setAmbientEffect(effectId: Int) {
        viewModelScope.launch { settingsRepository.updateAmbientEffect(effectId) }
    }

    fun addAppRule(app: AppItem) {
        viewModelScope.launch {
            val existing = uiState.value.rules.firstOrNull { it.key == app.packageName }
            ruleRepository.upsert(
                NotificationRule(
                    id = existing?.id ?: 0L,
                    type = RuleType.APP,
                    key = app.packageName,
                    displayName = app.label,
                    color = existing?.color ?: RING_PALETTE[1],
                    animationId = existing?.animationId ?: EffectEngine.EFFECT_STATIC
                )
            )
        }
    }

    fun updateRule(rule: NotificationRule, color: RgbColor, animationId: Int) {
        viewModelScope.launch {
            ruleRepository.upsert(rule.copy(color = color, animationId = animationId))
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch { ruleRepository.delete(id) }
    }

    fun requestShizukuPermission() {
        ShizukuGrantHelper.requestPermission(1001)
    }

    fun grantViaShizuku() {
        viewModelScope.launch(Dispatchers.IO) {
            if (ShizukuGrantHelper.grantWriteSecureSettings(context.packageName)) {
                delay(500)
            }
        }
    }

    fun previewRule(rule: NotificationRule) {
        sendColorToService(rule.color, rule.animationId)
    }

    fun testHardware() {
        viewModelScope.launch {
            sendColorToService(RgbColor(255, 0, 0), EffectEngine.EFFECT_STATIC)
            delay(1000)
            sendColorToService(RgbColor(0, 255, 0), EffectEngine.EFFECT_STATIC)
            delay(1000)
            sendColorToService(RgbColor(0, 0, 255), EffectEngine.EFFECT_STATIC)
            delay(1000)
            sendColorToService(RgbColor.OFF, EffectEngine.EFFECT_STATIC)
        }
    }

    private fun sendColorToService(color: RgbColor, animationId: Int) {
        val intent = Intent(context, RgbForegroundService::class.java).apply {
            action = RgbForegroundService.ACTION_SET_COLOR
            putExtra(RgbForegroundService.EXTRA_COLOR_PACKED, color.packed)
            putExtra(RgbForegroundService.EXTRA_ANIMATION_ID, animationId)
        }
        context.startService(intent)
    }
}