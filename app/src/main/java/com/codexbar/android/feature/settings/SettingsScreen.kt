package com.codexbar.android.feature.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.LoginActivity
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.DashboardThemeStyle
import com.codexbar.android.core.network.codex.CodexUrls
import com.codexbar.android.ui.components.ServiceBrandIcon
import com.codexbar.android.ui.theme.dashboardThemePalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val loginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.reloadCredentials()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NotificationsSection(
                enabled = uiState.notificationsEnabled,
                onToggle = { viewModel.setNotificationsEnabled(it) }
            )

            ProviderSelectionSection(
                enabledServices = uiState.enabledServices,
                onToggleProvider = { service, enabled -> viewModel.setProviderEnabled(service, enabled) }
            )

            ThemeSelectionSection(
                selectedTheme = uiState.dashboardTheme,
                onThemeSelected = { viewModel.setDashboardTheme(it) }
            )

            AiService.entries.filter { uiState.enabledServices.contains(it) }.forEach { service ->
                val state = uiState.serviceStates[service] ?: ServiceCredentialState()
                ServiceCredentialSection(
                    service = service,
                    state = state,
                    onFieldChange = { field, value -> viewModel.updateField(service, field, value) },
                    onToggleBackendMode = { useBackend -> viewModel.updateUseBackendMode(service, useBackend) },
                    onValidate = { viewModel.validateCredential(service) },
                    onLoginClick = {
                        val intent = Intent(context, LoginActivity::class.java).apply {
                            putExtra(LoginActivity.EXTRA_SERVICE, service.name)
                        }
                        loginLauncher.launch(intent)
                    }
                )
            }

            RefreshIntervalSection(
                currentMinutes = uiState.refreshIntervalMinutes,
                onIntervalChange = { viewModel.setRefreshInterval(it) }
            )

            Button(
                onClick = {
                    viewModel.saveAll()
                    scope.launch { snackbarHostState.showSnackbar("Credentials saved") }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save All Credentials")
            }

            DangerZoneSection(onDeleteAll = { viewModel.showDeleteConfirmDialog() })
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (uiState.showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            onConfirm = {
                viewModel.deleteAllCredentials()
                viewModel.dismissDeleteConfirmDialog()
            },
            onDismiss = { viewModel.dismissDeleteConfirmDialog() }
        )
    }
}

@Composable
private fun ProviderSelectionSection(
    enabledServices: Set<AiService>,
    onToggleProvider: (AiService, Boolean) -> Unit
) {
    SettingsCard {
        Text("Providers", style = MaterialTheme.typography.titleMedium)
        AiService.entries.forEach { service ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ServiceBrandIcon(service = service, size = 28.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(service.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (service.requiresManualCredentials) "Manual credentials" else "Browser login available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = enabledServices.contains(service),
                    onCheckedChange = { enabled -> onToggleProvider(service, enabled) }
                )
            }
        }
    }
}

@Composable
private fun ThemeSelectionSection(
    selectedTheme: DashboardThemeStyle,
    onThemeSelected: (DashboardThemeStyle) -> Unit
) {
    SettingsCard {
        Text("Dashboard Theme", style = MaterialTheme.typography.titleMedium)
        DashboardThemeStyle.entries.forEach { theme ->
            FilterChip(
                selected = theme == selectedTheme,
                onClick = { onThemeSelected(theme) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemePreviewDots(theme)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(theme.displayName)
                            Text(
                                theme.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ThemePreviewDots(theme: DashboardThemeStyle) {
    val palette = dashboardThemePalette(theme)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ThemePreviewDot(palette.backgroundColor)
        ThemePreviewDot(palette.cardContainerColor)
        ThemePreviewDot(palette.accentColor)
    }
}

@Composable
private fun ThemePreviewDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color = color, shape = CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
                shape = CircleShape
            )
    )
}

@Composable
private fun ServiceCredentialSection(
    service: AiService,
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onToggleBackendMode: (Boolean) -> Unit,
    onValidate: () -> Unit,
    onLoginClick: () -> Unit
) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServiceBrandIcon(service = service, size = 28.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(service.displayName, style = MaterialTheme.typography.titleMedium)
        }

        when (service) {
            AiService.CLAUDE -> ClaudeFields(state, onFieldChange)
            AiService.CODEX -> CodexFields(state, onFieldChange, onLoginClick)
            AiService.CODEX_FEELOL -> CodexFeelolFields(state, onFieldChange, onLoginClick)
            AiService.GEMINI -> GeminiFields(state, onFieldChange)
            AiService.DEEPSEEK -> DeepSeekFields(state, onFieldChange, onLoginClick)
            AiService.CHATGPT_PLUS -> ChatGPTPlusFields(state, onFieldChange, onLoginClick)
            AiService.MIMO -> MiMoFields(state, onFieldChange, onToggleBackendMode, onLoginClick)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onValidate, enabled = !state.isValidating) {
                if (state.isValidating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Validate")
            }
            Spacer(modifier = Modifier.width(12.dp))
            when (state.validationResult) {
                is ValidationResult.Success -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Valid", tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Valid", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                }
                is ValidationResult.Failure -> {
                    Icon(Icons.Default.Error, contentDescription = "Invalid", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        state.validationResult.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun ClaudeFields(state: ServiceCredentialState, onFieldChange: (String, String) -> Unit) {
    TokenField("Access Token", state.accessToken) { onFieldChange("accessToken", it) }
    TokenField("Refresh Token", state.refreshToken) { onFieldChange("refreshToken", it) }
}

@Composable
private fun CodexFields(
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onLoginClick: () -> Unit
) {
    Text(
        "Paste the ChatGPT session JSON from ${CodexUrls.SESSION_API}. The app extracts accessToken and calls Codex usage with Authorization: Bearer.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = onLoginClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A37F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Login with Browser")
    }
    JsonField("Session Response JSON", state.manualResponse) {
        onFieldChange("manualResponse", it)
    }
    TokenField("Access Token", state.accessToken) {
        onFieldChange("accessToken", it.removePrefix("Bearer ").trim())
    }
    TokenField("Refresh Token", state.refreshToken) { onFieldChange("refreshToken", it) }
    TextFieldLine("Account ID (optional)", state.accountId) { onFieldChange("accountId", it) }
}

@Composable
private fun CodexFeelolFields(
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onLoginClick: () -> Unit
) {
    Text(
        "Log in to feea.lol/subscriptions. Paste the Bearer token from the subscriptions API request, or paste the full /api/v1/subscriptions JSON response.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = onLoginClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15A05D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Login with feelol")
    }
    TokenField("Bearer Access Token", state.accessToken) {
        onFieldChange("accessToken", it.removePrefix("Bearer ").trim())
    }
    JsonField("Subscription Response JSON", state.manualResponse) {
        onFieldChange("manualResponse", it)
    }
}

@Composable
private fun GeminiFields(state: ServiceCredentialState, onFieldChange: (String, String) -> Unit) {
    TokenField("Access Token", state.accessToken) { onFieldChange("accessToken", it) }
    TokenField("Refresh Token", state.refreshToken) { onFieldChange("refreshToken", it) }
    TextFieldLine("OAuth Client ID", state.oauthClientId) { onFieldChange("oauthClientId", it) }
    TokenField("OAuth Client Secret", state.oauthClientSecret) { onFieldChange("oauthClientSecret", it) }
    if (state.expiresAtDisplay.isNotBlank()) {
        ReadOnlyLine("Token Expiry", state.expiresAtDisplay)
    }
}

@Composable
private fun DeepSeekFields(
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onLoginClick: () -> Unit
) {
    Button(
        onClick = onLoginClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F9CF5)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Login with DeepSeek")
    }
    JsonField("Platform Cookie", state.sessionCookie) { onFieldChange("sessionCookie", it) }
    TokenField("API Key", state.accessToken) { onFieldChange("accessToken", it) }
    TextFieldLine("Base URL", state.baseUrl.ifBlank { "https://api.deepseek.com" }) {
        onFieldChange("baseUrl", it)
    }
    TextFieldLine("Initial Total (optional)", state.initialTotal) { onFieldChange("initialTotal", it) }
}

@Composable
private fun ChatGPTPlusFields(
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onLoginClick: () -> Unit
) {
    TextFieldLine("Plan Name", state.planName) { onFieldChange("planName", it) }
    TextFieldLine("Last Renewal Date", state.renewalDate) { onFieldChange("renewalDate", it) }
    val periods = listOf("Monthly" to "Monthly", "Yearly" to "Yearly")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        periods.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = state.billingPeriod == value,
                onClick = { onFieldChange("billingPeriod", value) },
                shape = SegmentedButtonDefaults.itemShape(index, periods.size)
            ) {
                Text(label)
            }
        }
    }
    JsonField("Session Response (JSON)", state.manualSessionResponse) {
        onFieldChange("manualSessionResponse", it)
    }
    Button(
        onClick = onLoginClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A37F))
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Login with Browser")
    }
    TextFieldLine("Notes (optional)", state.notes) { onFieldChange("notes", it) }
}

@Composable
private fun MiMoFields(
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onToggleBackendMode: (Boolean) -> Unit,
    onLoginClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = state.useBackendMode,
            onClick = { onToggleBackendMode(true) },
            label = { Text("Backend URL") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(
            selected = !state.useBackendMode,
            onClick = { onToggleBackendMode(false) },
            label = { Text("Direct Cookie") }
        )
    }
    if (state.useBackendMode) {
        TextFieldLine("Backend URL", state.backendUrl) { onFieldChange("backendUrl", it) }
        TokenField("Backend Token (optional)", state.backendToken) { onFieldChange("backendToken", it) }
    } else {
        JsonField("Cookie", state.directCookie) { onFieldChange("directCookie", it) }
    }
    Button(
        onClick = onLoginClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6700))
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Login with Browser")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIntervalSection(
    currentMinutes: Long,
    onIntervalChange: (Long) -> Unit
) {
    SettingsCard {
        Text("Refresh Interval", style = MaterialTheme.typography.titleMedium)
        val options = listOf(15L to "15 min", 30L to "30 min", 60L to "1 hour", 0L to "Manual")
        val selectedIndex = options.indexOfFirst { it.first == currentMinutes }.takeIf { it >= 0 } ?: 1
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (minutes, label) ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { onIntervalChange(minutes) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun NotificationsSection(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Quota status and reset alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun DangerZoneSection(onDeleteAll: () -> Unit) {
    SettingsCard(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)) {
        Text("Danger Zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = onDeleteAll,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete All Credentials")
        }
    }
}

@Composable
private fun SettingsCard(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun TextFieldLine(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun TokenField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun JsonField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        maxLines = 8,
        singleLine = false
    )
}

@Composable
private fun ReadOnlyLine(label: String, value: String) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        enabled = false,
        singleLine = true
    )
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var confirmText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete All Credentials") },
        text = {
            Column {
                Text("This will permanently delete all saved credentials. Type DELETE to confirm.")
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Type DELETE") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmText == "DELETE") {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
