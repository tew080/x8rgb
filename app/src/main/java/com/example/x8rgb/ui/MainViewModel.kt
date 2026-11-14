// X8RGB file: MainViewModel.kt
// Version: v0015
// Path: app/src/main/java/com/example/x8rgb/ui/MainViewModel.kt
// History:
//   0002: initial state (master, brightness, permissions).
//   0015: rules (add/color/delete), ring mode via RingSwitch, quiet hours, installed-app list.
//
package com.x8rgb.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.data.repository.RuleRepository
import com.x8rgb.data.repository.SettingsRepository
import com.x8rgb.domain.model.NotificationRule
import com.x8rgb.domain.model.RgbColor
import com.x8rgb.domain.model.RuleType
import com.x8rgb.system.PermissionStateChecker
import com.x8rgb.system.RingMode
import com.x8rgb.system.RingSwitch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An app that can be picked for a notification rule. */
data class AppItem(val packageName: String, val label: String)

/** Presets shown in the color picker. Index 0 is white. */
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
    val isLoading: Boolean = true,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ruleRepository: RuleRepository,
    private val permissionChecker: PermissionStateChecker,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Re-reads permissions and the system switch every 2 s while the screen is observed. */
    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(2000)
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        settingsRepository.observe(),
        ruleRepository.observeAll(),
        ticker,
    ) { settings, rules, _ ->
        MainUiState(
            settings = settings ?: AppSettingsEntity(),
            rules = rules.filter { it.type == RuleType.APP }.sortedBy { it.displayName.lowercase() },
            ringMode = RingSwitch.savedMode(context),
            systemMode = RingSwitch.systemMode(context),
            notificationListener = permissionChecker.check().notificationListener,
            writeSecureSettings = RingSwitch.hasPermission(context),
            postNotifications = context.getSystemService(NotificationManager::class.java)
                .areNotificationsEnabled(),
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(),
    )

    // ── Dashboard ─────────────────────────────────────────────────────────────

    fun toggleMaster(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateMasterEnabled(enabled) }
    }

    fun updateBrightness(brightness: Int) {
        viewModelScope.launch { settingsRepository.updateBrightness(brightness) }
    }

    /** Writes the system switch and remembers the choice. Returns false if permission is missing. */
    fun setRingMode(mode: RingMode): Boolean = RingSwitch.set(context, mode)

    // ── Quiet hours ───────────────────────────────────────────────────────────

    fun updateQuietHours(enabled: Boolean, start: Int, end: Int) {
        viewModelScope.launch { settingsRepository.updateQuietHours(enabled, start, end) }
    }

    // ── Notification rules (APP) ──────────────────────────────────────────────

    /** Adds a rule for the app, or keeps the existing rule's color if one exists. */
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
                )
            )
        }
    }

    fun setRuleColor(rule: NotificationRule, color: RgbColor) {
        viewModelScope.launch { ruleRepository.upsert(rule.copy(color = color)) }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch { ruleRepository.delete(id) }
    }

    /** Launchable apps, sorted by label. Synchronous; the list is small. */
    fun installedApps(): List<AppItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}