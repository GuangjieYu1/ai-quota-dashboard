package com.codexbar.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.Credential
import com.codexbar.android.core.network.codex.CodexUrls
import com.codexbar.android.core.network.codex.CodexUsageResponseValidator
import com.codexbar.android.core.network.codex.JsonSessionResponse
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.ui.components.ServiceBrandIcon
import com.codexbar.android.ui.theme.CodexBarTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    @Inject lateinit var prefsManager: EncryptedPrefsManager

    companion object {
        const val EXTRA_SERVICE = "extra_service"

        private val LOGIN_URLS = mapOf(
            AiService.CODEX to CodexUrls.LOGIN,
            AiService.CHATGPT_PLUS to CodexUrls.LOGIN,
            AiService.DEEPSEEK to AiService.DEEPSEEK.homeUrl,
            AiService.MIMO to "https://platform.xiaomimimo.com/auth/login"
        )

        private val API_URLS = mapOf(
            AiService.CHATGPT_PLUS to CodexUrls.SESSION_API,
            AiService.CODEX to CodexUrls.SESSION_API
        )

        fun loginUrl(service: AiService): String =
            LOGIN_URLS[service] ?: error("No login URL for $service")

        private fun apiUrl(service: AiService): String? = API_URLS[service]
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceName = intent.getStringExtra(EXTRA_SERVICE) ?: ""
        val service = AiService.entries.find { it.name == serviceName } ?: AiService.CHATGPT_PLUS

        setContent {
            CodexBarTheme {
                if (service == AiService.CODEX) {
                    CodexBrowserHelperScreen(onCancel = { finish() })
                } else {
                    LoginScreen(
                        service = service,
                        initialUrl = loginUrl(service),
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun CodexBrowserHelperScreen(onCancel: () -> Unit) {
        var statusText by remember { mutableStateOf("") }
        var manualInput by remember { mutableStateOf("") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServiceBrandIcon(service = AiService.CODEX, size = 24.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Login: ${AiService.CODEX.displayName}")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Open ChatGPT in your regular browser, then open ${CodexUrls.SESSION_API}. Copy the full session JSON and paste it below.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "The app extracts accessToken from the session JSON and requests Codex usage with the required bearer token.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { openExternalUrl(CodexUrls.CHATGPT_HOME) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open ChatGPT")
                    }
                    OutlinedButton(
                        onClick = { openExternalUrl(apiUrl(AiService.CODEX) ?: loginUrl(AiService.CODEX)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open Session")
                    }
                }

                OutlinedTextField(
                    value = manualInput,
                    onValueChange = {
                        manualInput = it
                        statusText = if (it.isBlank()) "" else validateCodexManualInput(it)
                    },
                    label = { Text("Session Response JSON") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    minLines = 5,
                    maxLines = 12
                )

                if (statusText.isNotBlank()) {
                    val isValid = statusText == CodexUsageResponseValidator.VALID_RESPONSE_MESSAGE
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isValid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        val error = saveManualResponse(AiService.CODEX, manualInput)
                        if (error != null) {
                            statusText = error
                        }
                    },
                    enabled = manualInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Finish")
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun LoginScreen(
        service: AiService,
        initialUrl: String,
        onCancel: () -> Unit
    ) {
        var isLoading by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf("") }
        var isOnLoginPage by remember { mutableStateOf(true) }
        var showPasteForm by remember { mutableStateOf(false) }
        var manualInput by remember { mutableStateOf("") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServiceBrandIcon(service = service, size = 24.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Login: ${service.displayName}")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        if (service == AiService.CODEX || service == AiService.CHATGPT_PLUS) {
                            IconButton(onClick = { showPasteForm = !showPasteForm }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Paste response")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isOnLoginPage && (service == AiService.CODEX || service == AiService.CHATGPT_PLUS)) {
                    Text(
                        text = "⚠️ If the page shows a Google login error, use **Email** login instead, or paste the API response manually (tap the paste icon \uD83D\uDCCB above).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.113 Mobile Safari/537.36"
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        when {
                                            url?.contains("auth/login") == true -> {
                                                isOnLoginPage = true
                                                statusText = "Please log in using Email (not Google)"
                                            }
                                            else -> {
                                                statusText = "Loading..."
                                            }
                                        }
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        isLoading = false
                                        val onLoginPage = url.contains("auth/login")
                                        isOnLoginPage = onLoginPage

                                        if (onLoginPage) return

                                        val apiTarget = apiUrl(service)
                                        when {
                                            apiTarget?.let { url.startsWith(it) } == true -> {
                                                statusText = "Extracting data..."
                                                extractJsonFromPage(view, service) { message ->
                                                    statusText = message
                                                    showPasteForm = true
                                                }
                                            }
                                            service == AiService.MIMO || service == AiService.DEEPSEEK -> {
                                                attemptExtractData(view, service)
                                            }
                                            url.contains("chatgpt.com") && !url.contains("auth/") -> {
                                                if (apiTarget != null) {
                                                    statusText = "Navigating..."
                                                    view.loadUrl(apiTarget)
                                                }
                                            }
                                        }
                                    }
                                }

                                loadUrl(initialUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                        )
                    }

                    if (statusText.isNotBlank() && !isLoading && !showPasteForm) {
                        Box(
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (showPasteForm) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Auto-fetch failed. Paste the API response manually:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Paste API Response",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.size(8.dp))

                        val instructions = when (service) {
                            AiService.CODEX -> {
                                "1. Open ${CodexUrls.CHATGPT_HOME} in your browser and confirm you are signed in\n" +
                                "2. Open ${CodexUrls.SESSION_API}\n" +
                                "3. Copy the entire JSON response and paste it below"
                            }
                            AiService.CHATGPT_PLUS -> {
                                "1. Open ${CodexUrls.SESSION_API} in your browser (logged in)\n" +
                                "2. Copy the entire JSON response\n" +
                                "3. Paste it below"
                            }
                            else -> "Paste the response JSON below:"
                        }
                        Text(
                            text = instructions,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = { manualInput = it },
                            label = { Text("Response JSON") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            minLines = 5,
                            maxLines = 15
                        )

                        Button(
                            onClick = {
                                val error = saveManualResponse(service, manualInput)
                                if (error != null) {
                                    statusText = error
                                }
                            },
                            enabled = manualInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save & Finish")
                        }
                    }
                }
            }
        }
    }

    private fun saveManualResponse(service: AiService, response: String): String? {
        return try {
            when (service) {
                AiService.CODEX -> {
                    saveCodexManualResponse(response)?.let { return it }
                }
                AiService.CHATGPT_PLUS -> {
                    val sessionJson = json.decodeFromString<JsonSessionResponse>(response)
                    val credential = Credential.ChatGPTPlusCredential(
                        accessToken = sessionJson.accessToken ?: "",
                        planName = sessionJson.plan?.title ?: sessionJson.plan?.id ?: "Plus",
                        renewalDate = sessionJson.plan?.renewalDate ?: "",
                        billingPeriod = when (sessionJson.plan?.interval?.lowercase()) {
                            "year" -> "Yearly"
                            else -> "Monthly"
                        },
                        manualSessionResponse = response
                    )
                    prefsManager.saveCredential(AiService.CHATGPT_PLUS, credential)
                }
                AiService.MIMO -> {
                    val credential = Credential.MiMoCredential(directCookie = response)
                    prefsManager.saveCredential(AiService.MIMO, credential)
                }
                AiService.DEEPSEEK -> {
                    val credential = Credential.DeepSeekCredential(
                        accessToken = "",
                        sessionCookie = response
                    )
                    prefsManager.saveCredential(AiService.DEEPSEEK, credential)
                }
                else -> return null
            }
            setResult(Activity.RESULT_OK)
            finish()
            null
        } catch (e: Exception) {
            "Invalid JSON: ${e.message}"
        }
    }

    private fun extractJsonFromPage(
        view: WebView,
        service: AiService,
        onInvalidResponse: (String) -> Unit
    ) {
        view.evaluateJavascript("document.body ? document.body.innerText.trim() : ''") { result ->
            val rawText = decodeJavascriptString(result) ?: return@evaluateJavascript
            if (rawText.length < 10) {
                onInvalidResponse("No usable response was found. Open the API URL in a logged-in browser and paste the JSON response.")
                return@evaluateJavascript
            }

            when (service) {
                AiService.CHATGPT_PLUS -> {
                    try {
                        val sessionJson = json.decodeFromString<JsonSessionResponse>(rawText)
                        if (sessionJson.accessToken.isNullOrBlank() && sessionJson.plan == null) {
                            onInvalidResponse("The session endpoint did not return account data. Log in to chatgpt.com first, then try again or paste the response manually.")
                            return@evaluateJavascript
                        }
                        val credential = Credential.ChatGPTPlusCredential(
                            accessToken = sessionJson.accessToken ?: "",
                            planName = sessionJson.plan?.title ?: sessionJson.plan?.id ?: "Plus",
                            renewalDate = sessionJson.plan?.renewalDate ?: "",
                            billingPeriod = when (sessionJson.plan?.interval?.lowercase()) {
                                "year" -> "Yearly"
                                else -> "Monthly"
                            },
                            manualSessionResponse = rawText
                        )
                        prefsManager.saveCredential(AiService.CHATGPT_PLUS, credential)
                        setResult(Activity.RESULT_OK)
                        finish()
                    } catch (_: Exception) {
                        onInvalidResponse("Could not read the ChatGPT session response. Paste the JSON response manually.")
                    }
                }
                AiService.CODEX -> {
                    saveCodexResponse(rawText)?.let { message ->
                        onInvalidResponse(message)
                        return@evaluateJavascript
                    }
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                else -> {}
            }
        }
    }

    private fun saveCodexManualResponse(response: String): String? = saveCodexResponse(response)

    private fun saveCodexResponse(response: String): String? {
        parseCodexSessionAccessToken(response)?.let { accessToken ->
            val credential = Credential.CodexCredential(
                accessToken = accessToken,
                refreshToken = ""
            )
            prefsManager.saveCredential(AiService.CODEX, credential)
            return null
        }

        val usageResponse = CodexUsageResponseValidator.parse(response)
        if (usageResponse == null || !CodexUsageResponseValidator.hasUsageData(usageResponse)) {
            return invalidCodexResponseMessage(response)
        }

        val credential = Credential.CodexCredential(
            accessToken = "",
            refreshToken = "",
            manualResponse = response
        )
        prefsManager.saveCredential(AiService.CODEX, credential)
        return null
    }

    private fun invalidCodexResponseMessage(response: String): String {
        return if (CodexUsageResponseValidator.validationMessage(response).startsWith("Unauthorized")) {
            "ChatGPT returned Unauthorized. Open ${CodexUrls.SESSION_API} while signed in, then paste the session JSON."
        } else {
            "Paste a valid ChatGPT session JSON with accessToken, or a saved Codex usage JSON response."
        }
    }

    private fun parseCodexSessionAccessToken(response: String): String? {
        return runCatching {
            json.decodeFromString<JsonSessionResponse>(response).accessToken?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun validateCodexManualInput(response: String): String {
        if (parseCodexSessionAccessToken(response) != null) {
            return CodexUsageResponseValidator.VALID_RESPONSE_MESSAGE
        }
        return CodexUsageResponseValidator.validationMessage(response)
    }

    private fun decodeJavascriptString(result: String?): String? {
        if (result.isNullOrBlank() || result == "null") return null
        return runCatching { json.decodeFromString<String>(result) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun attemptExtractData(view: WebView, service: AiService) {
        when (service) {
            AiService.MIMO -> extractMiMoData(view)
            AiService.DEEPSEEK -> extractDeepSeekData()
            else -> {}
        }
    }

    private fun extractMiMoData(view: WebView) {
        val cookies = CookieManager.getInstance().getCookie("https://platform.xiaomimimo.com") ?: ""
        if (cookies.isNotBlank()) {
            val credential = Credential.MiMoCredential(directCookie = cookies)
            prefsManager.saveCredential(AiService.MIMO, credential)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun extractDeepSeekData() {
        val cookies = CookieManager.getInstance().getCookie("https://platform.deepseek.com") ?: ""
        if (cookies.isNotBlank() && hasLikelyAuthCookie(cookies)) {
            val credential = Credential.DeepSeekCredential(
                accessToken = "",
                sessionCookie = cookies
            )
            prefsManager.saveCredential(AiService.DEEPSEEK, credential)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun hasLikelyAuthCookie(cookies: String): Boolean {
        return cookies.split(";")
            .map { it.trim().lowercase() }
            .any { cookie ->
                cookie.contains("token") ||
                    cookie.contains("session") ||
                    cookie.contains("auth")
            }
    }

    private fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { startActivity(intent) }
    }
}
