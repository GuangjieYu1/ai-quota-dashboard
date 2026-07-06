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
            AiService.CODEX_FEELOL to "https://feea.lol/subscriptions",
            AiService.DEEPSEEK to AiService.DEEPSEEK.homeUrl,
            AiService.MIMO to "https://platform.xiaomimimo.com/auth/login"
        )

        private val API_URLS = mapOf(
            AiService.CHATGPT_PLUS to CodexUrls.SESSION_API,
            AiService.CODEX to CodexUrls.SESSION_API,
            AiService.CODEX_FEELOL to "https://feea.lol/api/v1/subscriptions?timezone=Asia%2FShanghai"
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
        var showPasteForm by remember { mutableStateOf(service == AiService.CODEX || service == AiService.CODEX_FEELOL) }
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
                        if (apiUrl(service) != null || service == AiService.MIMO || service == AiService.DEEPSEEK) {
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
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.113 Mobile Safari/537.36"
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        statusText = "Loading..."
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        isLoading = false
                                        val apiTarget = apiUrl(service)
                                        when {
                                            apiTarget?.let { url.startsWith(it.substringBefore("?")) } == true -> {
                                                statusText = "Extracting data..."
                                                extractJsonFromPage(view, service) { message ->
                                                    statusText = message
                                                    showPasteForm = true
                                                }
                                            }
                                            service == AiService.MIMO ||
                                                service == AiService.DEEPSEEK ||
                                                service == AiService.CODEX_FEELOL -> {
                                                attemptExtractData(view, service)
                                            }
                                            url.contains("chatgpt.com") && !url.contains("auth/") && apiTarget != null -> {
                                                statusText = "Opening API..."
                                                view.loadUrl(apiTarget)
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
                }

                if (showPasteForm || statusText.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (statusText.isNotBlank()) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = pasteTitle(service),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = pasteInstructions(service),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            apiUrl(service)?.let { target ->
                                OutlinedButton(
                                    onClick = { openExternalUrl(target) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open API")
                                }
                            }
                            Button(
                                onClick = { openExternalUrl(loginUrl(service)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Cloud, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Login")
                            }
                        }
                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = { manualInput = it },
                            label = { Text("Response / Token / Cookie") },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            minLines = 4,
                            maxLines = 10
                        )
                        Button(
                            onClick = {
                                val error = saveManualResponse(service, manualInput)
                                if (error != null) statusText = error
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

    private fun pasteTitle(service: AiService): String {
        return when (service) {
            AiService.CODEX -> "Paste ChatGPT session JSON"
            AiService.CHATGPT_PLUS -> "Paste ChatGPT session JSON"
            AiService.CODEX_FEELOL -> "Paste feelol response or Bearer token"
            AiService.DEEPSEEK -> "Paste DeepSeek cookie"
            AiService.MIMO -> "Paste MiMo cookie"
            else -> "Paste response"
        }
    }

    private fun pasteInstructions(service: AiService): String {
        return when (service) {
            AiService.CODEX -> "Open ${CodexUrls.SESSION_API} while signed in to chatgpt.com, copy the full JSON response, then paste it here."
            AiService.CHATGPT_PLUS -> "Open ${CodexUrls.SESSION_API} while signed in to chatgpt.com, copy the full JSON response, then paste it here."
            AiService.CODEX_FEELOL -> "Open https://feea.lol/api/v1/subscriptions?timezone=Asia%2FShanghai while signed in, copy JSON; or paste Authorization: Bearer token."
            AiService.DEEPSEEK -> "Paste the cookie captured from platform.deepseek.com after login."
            AiService.MIMO -> "Paste the cookie captured from platform.xiaomimimo.com after login."
            else -> "Paste the response below."
        }
    }

    private fun saveManualResponse(service: AiService, response: String): String? {
        return try {
            when (service) {
                AiService.CODEX -> saveCodexResponse(response)?.let { return it }
                AiService.CHATGPT_PLUS -> saveChatGptPlusResponse(response)
                AiService.CODEX_FEELOL -> saveFeelolResponse(response)
                AiService.MIMO -> {
                    prefsManager.saveCredential(AiService.MIMO, Credential.MiMoCredential(directCookie = response))
                }
                AiService.DEEPSEEK -> {
                    prefsManager.saveCredential(
                        AiService.DEEPSEEK,
                        Credential.DeepSeekCredential(accessToken = "", sessionCookie = response)
                    )
                }
                else -> return null
            }
            setResult(Activity.RESULT_OK)
            finish()
            null
        } catch (e: Exception) {
            "Invalid response: ${e.message}"
        }
    }

    private fun extractJsonFromPage(
        view: WebView,
        service: AiService,
        onInvalidResponse: (String) -> Unit
    ) {
        view.evaluateJavascript("document.body ? document.body.innerText.trim() : ''") { result ->
            val rawText = decodeJavascriptString(result) ?: return@evaluateJavascript
            val error = saveManualResponse(service, rawText)
            if (error != null) {
                onInvalidResponse(error)
            }
        }
    }

    private fun saveChatGptPlusResponse(response: String) {
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

    private fun saveCodexResponse(response: String): String? {
        parseCodexSessionAccessToken(response)?.let { accessToken ->
            prefsManager.saveCredential(
                AiService.CODEX,
                Credential.CodexCredential(accessToken = accessToken, refreshToken = "")
            )
            return null
        }

        val usageResponse = CodexUsageResponseValidator.parse(response)
        if (usageResponse == null || !CodexUsageResponseValidator.hasUsageData(usageResponse)) {
            return if (response.contains("unauthorized", ignoreCase = true)) {
                "ChatGPT returned Unauthorized. Paste ${CodexUrls.SESSION_API} session JSON instead."
            } else {
                "Paste a valid ChatGPT session JSON or saved Codex usage JSON response."
            }
        }

        prefsManager.saveCredential(
            AiService.CODEX,
            Credential.CodexCredential(accessToken = "", refreshToken = "", manualResponse = response)
        )
        return null
    }

    private fun saveFeelolResponse(response: String) {
        val token = extractBearerToken(response)
        val credential = if (token != null) {
            Credential.CodexFeelolCredential(accessToken = token)
        } else {
            Credential.CodexFeelolCredential(manualResponse = response)
        }
        prefsManager.saveCredential(AiService.CODEX_FEELOL, credential)
    }

    private fun parseCodexSessionAccessToken(response: String): String? {
        return runCatching {
            json.decodeFromString<JsonSessionResponse>(response).accessToken?.takeIf { it.isNotBlank() }
        }.getOrNull()
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
            AiService.MIMO -> extractMiMoData()
            AiService.DEEPSEEK -> extractDeepSeekData()
            AiService.CODEX_FEELOL -> extractFeelolData(view)
            else -> Unit
        }
    }

    private fun extractMiMoData() {
        val cookies = CookieManager.getInstance().getCookie("https://platform.xiaomimimo.com") ?: ""
        if (cookies.isNotBlank()) {
            prefsManager.saveCredential(AiService.MIMO, Credential.MiMoCredential(directCookie = cookies))
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun extractDeepSeekData() {
        CookieManager.getInstance().flush()
        val cookies = listOf(
            CookieManager.getInstance().getCookie("https://platform.deepseek.com") ?: "",
            CookieManager.getInstance().getCookie("https://api.deepseek.com") ?: ""
        ).filter { it.isNotBlank() }.distinct().joinToString("; ")
        if (cookies.isNotBlank()) {
            prefsManager.saveCredential(
                AiService.DEEPSEEK,
                Credential.DeepSeekCredential(accessToken = "", sessionCookie = cookies)
            )
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun extractFeelolData(view: WebView) {
        val script = """
            (function() {
              const parts = [];
              for (let i = 0; i < localStorage.length; i++) {
                const k = localStorage.key(i);
                parts.push(k + '=' + localStorage.getItem(k));
              }
              for (let i = 0; i < sessionStorage.length; i++) {
                const k = sessionStorage.key(i);
                parts.push(k + '=' + sessionStorage.getItem(k));
              }
              if (document.body) parts.push(document.body.innerText || '');
              return parts.join('\n');
            })();
        """.trimIndent()
        view.evaluateJavascript(script) { result ->
            val text = decodeJavascriptString(result).orEmpty()
            val token = extractBearerToken(text)
            if (token != null) {
                prefsManager.saveCredential(
                    AiService.CODEX_FEELOL,
                    Credential.CodexFeelolCredential(accessToken = token)
                )
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    private fun extractBearerToken(text: String): String? {
        val bearer = Regex("Bearer\\s+([A-Za-z0-9._~+/-]+=*)").find(text)?.groupValues?.getOrNull(1)
        if (!bearer.isNullOrBlank()) return bearer
        val jwt = Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").find(text)?.value
        return jwt?.takeIf { it.isNotBlank() }
    }

    private fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { startActivity(intent) }
    }
}
