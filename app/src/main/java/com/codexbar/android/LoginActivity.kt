package com.codexbar.android

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.codexbar.android.core.security.EncryptedPrefsManager
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
            AiService.CODEX to "https://chatgpt.com/auth/login",
            AiService.CHATGPT_PLUS to "https://chatgpt.com/auth/login",
            AiService.MIMO to "https://platform.xiaomimimo.com/auth/login"
        )

        private val API_URLS = mapOf(
            AiService.CHATGPT_PLUS to "https://chatgpt.com/api/auth/session",
            AiService.CODEX to "https://chatgpt.com/backend-api/wham/usage"
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
                LoginScreen(
                    service = service,
                    initialUrl = loginUrl(service),
                    onCancel = { finish() }
                )
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
                            Icon(
                                Icons.Default.Cloud,
                                contentDescription = null,
                                tint = Color(service.brandColor),
                                modifier = Modifier.size(24.dp)
                            )
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
                                                extractJsonFromPage(view, service)
                                            }
                                            service == AiService.MIMO -> {
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
                                "1. Open https://chatgpt.com/backend-api/wham/usage in your browser (logged in)\n" +
                                "2. Copy the entire JSON response\n" +
                                "3. Paste it below"
                            }
                            AiService.CHATGPT_PLUS -> {
                                "1. Open https://chatgpt.com/api/auth/session in your browser (logged in)\n" +
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
                    val credential = Credential.CodexCredential(
                        accessToken = "", refreshToken = "", manualResponse = response
                    )
                    prefsManager.saveCredential(AiService.CODEX, credential)
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
                else -> return null
            }
            setResult(Activity.RESULT_OK)
            finish()
            null
        } catch (e: Exception) {
            "Invalid JSON: ${e.message}"
        }
    }

    private fun extractJsonFromPage(view: WebView, service: AiService) {
        view.evaluateJavascript("document.body?.innerText?.trim() ?: ''") { result ->
            val rawText = result?.trim('"')?.trim() ?: return@evaluateJavascript
            if (rawText.length < 10) return@evaluateJavascript

            when (service) {
                AiService.CHATGPT_PLUS -> {
                    try {
                        val sessionJson = json.decodeFromString<JsonSessionResponse>(rawText)
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
                    } catch (_: Exception) {}
                }
                AiService.CODEX -> {
                    val credential = Credential.CodexCredential(
                        accessToken = "", refreshToken = "", manualResponse = rawText
                    )
                    prefsManager.saveCredential(AiService.CODEX, credential)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                else -> {}
            }
        }
    }

    private fun attemptExtractData(view: WebView, service: AiService) {
        when (service) {
            AiService.MIMO -> extractMiMoData(view)
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
}

@kotlinx.serialization.Serializable
data class JsonSessionResponse(
    val accessToken: String? = null,
    val user: JsonUser? = null,
    val plan: JsonPlan? = null,
    val expires: String? = null
)

@kotlinx.serialization.Serializable
data class JsonUser(
    val name: String? = null,
    val email: String? = null,
    val image: String? = null
)

@kotlinx.serialization.Serializable
data class JsonPlan(
    val id: String? = null,
    val title: String? = null,
    val interval: String? = null,
    val renewalDate: String? = null
)
