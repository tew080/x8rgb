package com.x8rgb.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x8rgb.data.db.entity.AppSettingsEntity
import com.x8rgb.data.repository.SettingsRepository
import com.x8rgb.system.PermissionStateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val permissionsGranted: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val permissionChecker: PermissionStateChecker
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = settingsRepository.observe()
        .map { settings ->
            MainUiState(
                settings = settings ?: AppSettingsEntity(),
                permissionsGranted = permissionChecker.check().notificationListener,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainUiState()
        )

    fun toggleMaster(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateMasterEnabled(enabled)
        }
    }

    fun updateBrightness(brightness: Int) {
        viewModelScope.launch {
            settingsRepository.updateBrightness(brightness)
        }
    }
}
