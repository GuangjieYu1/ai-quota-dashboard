package com.codexbar.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codexbar.android.core.config.AppConfig
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.util.BatteryOptimizationHelper
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import com.codexbar.android.feature.dashboard.DashboardScreen
import com.codexbar.android.feature.dashboard.MockDashboardScreen
import com.codexbar.android.feature.settings.MockSettingsScreen
import com.codexbar.android.feature.settings.SettingsScreen
import com.codexbar.android.ui.theme.CodexBarTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefsManager: EncryptedPrefsManager

    private val batteryOptLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        WorkManagerInitializer.scheduleConfiguredRefresh(this, prefsManager)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (AppConfig.USE_MOCK_DATA) {
            showMockDashboard()
        } else {
            showRealDashboard()
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    private fun showRealDashboard() {
        setContent {
            CodexBarTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

                    LaunchedEffect(permissionState.status.isGranted) {
                        if (!permissionState.status.isGranted) {
                            permissionState.launchPermissionRequest()
                        }
                    }

                    LaunchedEffect(permissionState.status) {
                        if (!permissionState.status.isGranted) {
                            snackbarHostState.showSnackbar(
                                "Notification permission required for background quota updates"
                            )
                        }
                    }
                }

                var showBatteryDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this@MainActivity)) {
                        showBatteryDialog = true
                    }
                }

                if (showBatteryDialog) {
                    AlertDialog(
                        onDismissRequest = { showBatteryDialog = false },
                        title = { Text("Background Token Refresh") },
                        text = {
                            Text(
                                "To keep your API tokens up to date in the background, " +
                                    "please exempt this app from battery optimization."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                batteryOptLauncher.launch(
                                    BatteryOptimizationHelper
                                        .requestIgnoreBatteryOptimizationsIntent(this@MainActivity)
                                )
                            }) {
                                Text("Allow")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                try {
                                    batteryOptLauncher.launch(
                                        BatteryOptimizationHelper
                                            .openBatteryOptimizationSettingsIntent()
                                    )
                                } catch (_: Exception) { }
                            }) {
                                Text("Settings")
                            }
                        }
                    )
                }

                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
                    val refreshCounter = remember { mutableIntStateOf(0) }

                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                refreshTrigger = refreshCounter.intValue,
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = {
                                    refreshCounter.intValue += 1
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showMockDashboard() {
        setContent {
            CodexBarTheme {
                val navController = rememberNavController()
                Scaffold { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable("dashboard") {
                            MockDashboardScreen(
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        composable("settings") {
                            MockSettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
