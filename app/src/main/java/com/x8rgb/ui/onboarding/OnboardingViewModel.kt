package com.x8rgb.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x8rgb.system.PermissionStateChecker
import com.x8rgb.system.ShizukuGrantHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val notificationListenerGranted: Boolean = false,
    val writeSecureSettingsGranted: Boolean  = false,
    val shizukuAvailable: Boolean            = false,
    val shizukuGranted: Boolean              = false,
    val isCheckingPermissions: Boolean       = false,
    val isComplete: Boolean                  = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val checker: PermissionStateChecker,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state = _state.asStateFlow()

    init { refreshPermissions() }

    fun refreshPermissions() {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingPermissions = true) }
            val p = checker.check()
            _state.update {
                it.copy(
                    notificationListenerGranted = p.notificationListener,
                    writeSecureSettingsGranted  = p.writeSecureSettings,
                    shizukuAvailable            = p.shizukuAvailable,
                    shizukuGranted              = p.shizukuGranted,
                    isCheckingPermissions       = false,
                    isComplete = p.notificationListener && p.writeSecureSettings,
                )
            }
        }
    }

    fun requestShizukuPermission(requestCode: Int) {
        ShizukuGrantHelper.requestPermission(requestCode)
    }

    fun grantViaShizuku(packageName: String) {
        viewModelScope.launch {
            if (ShizukuGrantHelper.grantWriteSecureSettings(packageName)) {
                delay(300)
                refreshPermissions()
            }
        }
    }
}
