package com.codexbar.android.feature.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.DashboardThemeStyle
import com.codexbar.android.ui.theme.dashboardThemePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    refreshTrigger: Int = 0,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val dashboardTheme by viewModel.dashboardTheme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = dashboardThemePalette(dashboardTheme)

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.refresh()
    }

    Scaffold(
        containerColor = palette.backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("AI Quota", color = palette.primaryContentColor) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = palette.primaryContentColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.topBarColor
                )
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(palette.backgroundColor)
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = palette.accentColor)
                    }
                }

                is DashboardUiState.Success -> {
                    if (state.cards.isEmpty()) {
                        EmptyState(
                            onNavigateToSettings = onNavigateToSettings,
                            contentColor = palette.primaryContentColor
                        )
                    } else {
                        CardList(
                            cards = state.cards,
                            errorBanner = null,
                            errorColor = palette.errorColor,
                            themeStyle = dashboardTheme,
                            onOpenRecharge = { url -> openExternalUrl(context, url) }
                        )
                    }
                }

                is DashboardUiState.PartialSuccess -> {
                    val errorServices = state.errors.keys.joinToString(", ") { it.displayName }
                    CardList(
                        cards = state.cards,
                        errorBanner = "Failed to load: $errorServices",
                        errorColor = palette.errorColor,
                        themeStyle = dashboardTheme,
                        onOpenRecharge = { url -> openExternalUrl(context, url) }
                    )
                }

                is DashboardUiState.Error -> {
                    val errorMessage = when (val e = state.error) {
                        is AppError.CredentialNotFound -> "No credentials for ${e.service.displayName}"
                        is AppError.NeedsLogin -> e.message
                        is AppError.AuthError -> e.message.ifBlank { "Auth error for ${e.service.displayName}" }
                        is AppError.NetworkError -> "Network error: ${e.message}"
                        else -> "Failed to load quota data"
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = palette.errorColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.primaryContentColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onNavigateToSettings) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Text("Go to Settings")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardList(
    cards: List<ServiceCardData>,
    errorBanner: String?,
    errorColor: Color,
    themeStyle: DashboardThemeStyle,
    onOpenRecharge: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (errorBanner != null) {
            item {
                Text(
                    text = errorBanner,
                    style = MaterialTheme.typography.bodySmall,
                    color = errorColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        items(cards, key = { it.service.name }) { card ->
            ServiceCard(
                cardData = card,
                themeStyle = themeStyle,
                onClick = { /* Bottom sheet detail — future enhancement */ },
                onOpenRecharge = onOpenRecharge
            )
        }
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
}

@Composable
private fun EmptyState(
    onNavigateToSettings: () -> Unit = {},
    contentColor: Color
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No services configured",
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Go to Settings to add your API credentials",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.72f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Text("Settings")
            }
        }
    }
}
