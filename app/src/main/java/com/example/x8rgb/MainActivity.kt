package com.x8rgb

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x8rgb.service.RgbForegroundService
import com.x8rgb.ui.MainViewModel
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
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(painterResource(it.icon), contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "X8RGB Controller",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (!uiState.permissionsGranted && !uiState.isLoading) {
                    PermissionWarningCard()
                }

                DashboardCard(
                    enabled = uiState.settings.masterEnabled,
                    onEnabledChange = { enabled ->
                        viewModel.toggleMaster(enabled)
                        val intent = Intent(context, RgbForegroundService::class.java)
                        if (enabled) {
                            context.startForegroundService(intent)
                        } else {
                            context.stopService(intent)
                        }
                    },
                    brightness = uiState.settings.brightness,
                    onBrightnessChange = { viewModel.updateBrightness(it.toInt()) }
                )
            }
        }
    }
}

@Composable
fun PermissionWarningCard() {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Permission Required", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "App needs Notification Access to react to alerts.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Grant Notification Access")
            }
        }
    }
}

@Composable
fun DashboardCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    brightness: Int,
    onBrightnessChange: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Service Status", style = MaterialTheme.typography.titleLarge)
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            
            Text(
                text = if (enabled) "Running" else "Stopped",
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(24.dp))
            
            Text("Ring Brightness: $brightness")
            Slider(
                value = brightness.toFloat(),
                onValueChange = onBrightnessChange,
                valueRange = 80f..255f,
                enabled = enabled
            )
        }
    }
}

enum class AppDestinations(val label: String, val icon: Int) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}
