package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.ProviderStatus
import com.codexbar.android.ui.theme.CodexBarTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockDashboardScreen(
    onNavigateToSettings: () -> Unit
) {
    var providers by remember { mutableStateOf(MockQuotaData.providers()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Quota Dashboard") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        MockCardList(
            providers = providers,
            onRefresh = { providerId ->
                providers = providers.map { card ->
                    if (card.service.name == providerId) card.copy(isLoading = true) else card
                }
                scope.launch {
                    delay(500)
                    providers = providers.map { card ->
                        if (card.service.name == providerId) {
                            card.copy(
                                isLoading = false,
                                status = randomStatus(),
                                lastUpdated = Instant.now(),
                                error = if (Random.nextFloat() < 0.2f)
                                    AppError.NetworkError("Simulated error")
                                else null
                            )
                        } else card
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
private fun MockCardList(
    providers: List<ServiceCardData>,
    onRefresh: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 360.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(providers, key = { it.service.name }) { card ->
            MockServiceCard(
                cardData = card,
                onRefresh = { onRefresh(card.service.name) }
            )
        }
    }
}

@Composable
private fun MockServiceCard(
    cardData: ServiceCardData,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = cardData.service.displayName,
                    modifier = Modifier.size(32.dp),
                    tint = Color(cardData.service.brandColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = cardData.service.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                cardData.tier?.let { tier ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = tier,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusChip(status = cardData.status)
            }

            if (cardData.error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = formatError(cardData.error!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                cardData.windows.forEach { window ->
                    Spacer(modifier = Modifier.height(12.dp))
                    QuotaGaugeBar(
                        utilization = window.utilization,
                        label = window.label,
                        showPercentage = true,
                        resetsAt = window.resetsAt
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cardData.lastUpdated?.let {
                    Text(
                        text = "Updated: Just now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(onClick = onRefresh) {
                    if (cardData.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ProviderStatus) {
    val (color, label) = when (status) {
        ProviderStatus.OK -> Pair(Color(0xFF4CAF50), "OK")
        ProviderStatus.WARNING -> Pair(Color(0xFFFFC107), "Warning")
        ProviderStatus.ERROR -> Pair(MaterialTheme.colorScheme.error, "Error")
        ProviderStatus.NEEDS_LOGIN -> Pair(Color(0xFFFF9800), "Needs Login")
        ProviderStatus.UNKNOWN -> Pair(Color.Gray, "Unknown")
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = color
        )
    }
}

private fun formatError(error: AppError): String {
    return when (error) {
        is AppError.NetworkError -> error.message
        is AppError.AuthError -> error.message.ifBlank { "Auth error" }
        is AppError.RateLimited -> "Rate limited"
        is AppError.ParseError -> error.message ?: "Parse error"
        is AppError.CredentialNotFound -> "Not configured"
        is AppError.ServiceUnavailable -> "Service unavailable"
        is AppError.NeedsLogin -> error.message
    }
}

private fun randomStatus(): ProviderStatus {
    return when (Random.nextInt(10)) {
        0, 1 -> ProviderStatus.ERROR
        2, 3 -> ProviderStatus.WARNING
        4 -> ProviderStatus.NEEDS_LOGIN
        else -> ProviderStatus.OK
    }
}

// ── Previews ──

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Phone")
@Composable
private fun DashboardPhonePreview() {
    CodexBarTheme(dynamicColor = false) {
        MockDashboardScreen(onNavigateToSettings = {})
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 1280, name = "Tablet")
@Composable
private fun DashboardTabletPreview() {
    CodexBarTheme(dynamicColor = false) {
        MockDashboardScreen(onNavigateToSettings = {})
    }
}
