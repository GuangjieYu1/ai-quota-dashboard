package com.codexbar.android.feature.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.codexbar.android.core.network.codex.CodexDto
import com.codexbar.android.core.network.codex.CodexUsageResponseValidator
import com.codexbar.android.core.network.codex.JsonSessionResponse
import com.codexbar.android.ui.components.ServiceBrandIcon
import com.codexbar.android.ui.theme.dashboardThemePalette
import kotlinx.serialization.json.Json
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

            SaveAllButton(
                onSave = {
                    viewModel.saveAll()
                    scope.launch {
                        snackbarHostState.showSnackbar("Credentials saved")
                    }
                }
            )

            DangerZoneSection(
                onDeleteAll = { viewModel.showDeleteConfirmDialog() }
            )

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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Providers",
                style = MaterialTheme.typography.titleMedium
            )
            AiService.entries.forEach { service ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        ServiceBrandIcon(service = service, size = 28.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = service.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (service.requiresManualCredentials) "Manual credentials" else "Browser login available",
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
}

@Composable
private fun ThemeSelectionSection(
    selectedTheme: DashboardThemeStyle,
    onThemeSelected: (DashboardThemeStyle) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Dashboard Theme",
                style = MaterialTheme.typography.titleMedium
            )
            DashboardThemeStyle.entries.forEach { theme ->
                FilterChip(
                    selected = theme == selectedTheme,
                    onClick = { onThemeSelected(theme) },
                    label = {
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ThemePreviewDots(theme = theme)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(theme.displayName)
                                Text(
                                    text = theme.description,
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
}

@Composable
private fun ThemePreviewDots(theme: DashboardThemeStyle) {
    val palette = dashboardThemePalette(theme)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ThemePreviewDot(color = palette.backgroundColor)
        ThemePreviewDot(color = palette.cardContainerColor)
        ThemePreviewDot(color = palette.accentColor)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceBrandIcon(service = service, size = 28.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (service) {
                AiService.CLAUDE -> ClaudeFields(state, onFieldChange)
                AiService.CODEX -> CodexFields(state, onFieldChange, onLoginClick)
                AiService.GEMINI -> GeminiFields(state, onFieldChange)
                AiService.DEEPSEEK -> DeepSeekFields(state, onFieldChange, onLoginClick)
                AiService.CHATGPT_PLUS -> ChatGPTPlusFields(state, onFieldChange, onLoginClick)
                AiService.MIMO -> MiMoFields(state, onFieldChange, onToggleBackendMode, onLoginClick)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onValidate,
                    enabled = !state.isValidating
                ) {
                    if (state.isValidating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Validate")
                }

                Spacer(modifier = Modifier.width(12.dp))

                when (state.validationResult) {
                    is ValidationResult.Success -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Valid",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Valid", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                    }
                    is ValidationResult.Failure -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Invalid",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            state.validationResult.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    null -> {}
                }
            }
        }
    }
}

@Composable
private fun ClaudeFields(state: ServiceCredentialState, onFieldChange: (String, String) -> Unit) {
    OutlinedTextField(
        value = state.accessToken,
        onValueChange = { onFieldChange("accessToken", it) },
        label = { Text("Access Token") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.refreshToken,
        onValueChange = { onFieldChange("refreshToken", it) },
        label = { Text("Refresh Token") },
        supportingText = { Text("Required for auto-refresh (tokens expire every 8h)") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun CodexFields(
    state: ServiceCredentialState,
    onFieldChange: (String, String) -> Unit,
    onLoginClick: () -> Unit
) {
    val context = LocalContext.current

    Text(
        text = "Option 1: Browser helper",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "Open ChatGPT in your regular browser, then open ${CodexUrls.SESSION_API}. The app extracts accessToken and requests Codex usage with a bearer token.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = onLoginClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A37F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Login with Browser")
    }
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Option 2: Paste session JSON",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "Open ${CodexUrls.SESSION_API} while signed in to chatgpt.com, copy the full session JSON, and paste it below.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { openExternalUrl(context, CodexUrls.CHATGPT_HOME) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A37F)),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open ChatGPT")
        }
        OutlinedButton(
            onClick = { openExternalUrl(context, CodexUrls.SESSION_API) },
            modifier = Modifier.weight(1f)
        ) {
            Text("Open Session")
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.manualResponse,
        onValueChange = { onFieldChange("manualResponse", it) },
        label = { Text("Session Response JSON") },
        placeholder = { Text("Paste the full JSON response here") },
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        maxLines = 8,
        singleLine = false
    )
    if (state.manualResponse.isNotBlank()) {
        val validationMessage = codexResponseValidationMessage(state.manualResponse)
        val isValid = validationMessage == CodexUsageResponseValidator.VALID_RESPONSE_MESSAGE
        Text(
            text = validationMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Option 3: Manual token input (API call mode)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "Log in to chatgpt.com, then open DevTools (F12) → Application → Cookies →\n" +
            "find __Secure-next-auth.session-token for session / visit /api/auth/session for accessToken.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.accessToken,
        onValueChange = { onFieldChange("accessToken", it) },
        label = { Text("Access Token") },
        placeholder = { Text("Bearer token from chatgpt.com OAuth") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.refreshToken,
        onValueChange = { onFieldChange("refreshToken", it) },
        label = { Text("Refresh Token") },
        placeholder = { Text("OAuth refresh token (auto-renew when expired)") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.accountId,
        onValueChange = { onFieldChange("accountId", it) },
        label = { Text("Account ID (optional)") },
        placeholder = { Text("ChatGPT-Account-Id header value") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
}

private val codexJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private fun codexResponseValidationMessage(response: String): String {
    if (parseCodexSessionAccessToken(response) != null) {
        return CodexUsageResponseValidator.VALID_RESPONSE_MESSAGE
    }

    val usageResponse = runCatching {
        codexJson.decodeFromString<CodexDto.UsageResponse>(response)
    }.getOrNull()

    return if (usageResponse != null && CodexUsageResponseValidator.hasUsageData(usageResponse)) {
        CodexUsageResponseValidator.VALID_RESPONSE_MESSAGE
    } else if (response.contains("unauthorized", ignoreCase = true)) {
        "Unauthorized response cannot be saved. Paste session JSON from ${CodexUrls.SESSION_API}."
    } else {
        "Invalid Codex response"
    }
}

private fun parseCodexSessionAccessToken(response: String): String? {
    return runCatching {
        codexJson.decodeFromString<JsonSessionResponse>(response).accessToken?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

@Composable
private fun GeminiFields(state: ServiceCredentialState, onFieldChange: (String, String) -> Unit) {
    OutlinedTextField(
        value = state.accessToken,
        onValueChange = { onFieldChange("accessToken", it) },
        label = { Text("Access Token") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.refreshToken,
        onValueChange = { onFieldChange("refreshToken", it) },
        label = { Text("Refresh Token") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.oauthClientId,
        onValueChange = { onFieldChange("oauthClientId", it) },
        label = { Text("OAuth Client ID") },
        supportingText = { Text("From Google Cloud Console") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.oauthClientSecret,
        onValueChange = { onFieldChange("oauthClientSecret", it) },
        label = { Text("OAuth Client Secret") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    if (state.expiresAtDisplay.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.expiresAtDisplay,
            onValueChange = {},
            label = { Text("Token Expiry") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = false,
            singleLine = true
        )
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
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sessionCookie,
        onValueChange = { onFieldChange("sessionCookie", it) },
        label = { Text("Platform Cookie") },
        placeholder = { Text("Saved automatically after browser login") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "API key fallback",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.accessToken,
        onValueChange = { onFieldChange("accessToken", it) },
        label = { Text("API Key") },
        placeholder = { Text("sk-...") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.baseUrl,
        onValueChange = { onFieldChange("baseUrl", it) },
        label = { Text("Base URL") },
        placeholder = { Text("https://api.deepseek.com") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.initialTotal,
        onValueChange = { onFieldChange("initialTotal", it) },
        label = { Text("Initial Total (optional)") },
        placeholder = { Text("e.g. 50.00 — total credits when account started") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
    if (state.initialTotal.isNotBlank()) {
        Text(
            text = "Used for calculating utilization progress bar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChatGPTPlusFields(state: ServiceCredentialState, onFieldChange: (String, String) -> Unit, onLoginClick: () -> Unit) {
    OutlinedTextField(
        value = state.planName,
        onValueChange = { onFieldChange("planName", it) },
        label = { Text("Plan Name") },
        placeholder = { Text("Plus / Pro / Team / Custom") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.renewalDate,
        onValueChange = { onFieldChange("renewalDate", it) },
        label = { Text("Last Renewal Date") },
        placeholder = { Text("yyyy-MM-dd (date of your last payment)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
    )
    Spacer(modifier = Modifier.height(8.dp))
    val periods = listOf("Monthly" to "Monthly", "Yearly" to "Yearly")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        periods.forEach { (value, label) ->
            SegmentedButton(
                selected = state.billingPeriod == value,
                onClick = { onFieldChange("billingPeriod", value) },
                shape = SegmentedButtonDefaults.itemShape(
                    periods.indexOfFirst { it.first == value },
                    periods.size
                )
            ) {
                Text(label)
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Option 1: Paste session JSON (recommended)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "Open chatgpt.com → open a new tab → paste this URL:\n" +
            "https://chatgpt.com/api/auth/session\n" +
            "Copy the JSON response and paste it below.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.manualSessionResponse,
        onValueChange = { onFieldChange("manualSessionResponse", it) },
        label = { Text("Session Response (JSON)") },
        placeholder = { Text("Paste the full JSON response here") },
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        maxLines = 6,
        singleLine = false
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onLoginClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF10A37F)
        )
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Login with Browser")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.notes,
        onValueChange = { onFieldChange("notes", it) },
        label = { Text("Notes (optional)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
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

    Spacer(modifier = Modifier.height(8.dp))

    if (state.useBackendMode) {
        OutlinedTextField(
            value = state.backendUrl,
            onValueChange = { onFieldChange("backendUrl", it) },
            label = { Text("Backend URL") },
            placeholder = { Text("https://your-server/api/quota/mimo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.backendToken,
            onValueChange = { onFieldChange("backendToken", it) },
            label = { Text("Backend Token (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    } else {
        OutlinedTextField(
            value = state.directCookie,
            onValueChange = { onFieldChange("directCookie", it) },
            label = { Text("Cookie") },
            placeholder = { Text("api-platform_serviceToken=...") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "⚠️ Advanced: Cookie stored locally, use at your own risk",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Refresh Interval",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

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
}

@Composable
private fun NotificationsSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Quota status and reset alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun SaveAllButton(onSave: () -> Unit) {
    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Save All Credentials")
    }
}

@Composable
private fun DangerZoneSection(onDeleteAll: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDeleteAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete All Credentials")
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
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
            TextButton(
                onClick = onConfirm,
                enabled = confirmText == "DELETE"
            ) {
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
