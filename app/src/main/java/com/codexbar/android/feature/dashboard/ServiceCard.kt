package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.DashboardThemeStyle
import com.codexbar.android.core.domain.model.ProviderStatus
import com.codexbar.android.ui.components.ServiceBrandIcon
import com.codexbar.android.ui.theme.DashboardThemePalette
import com.codexbar.android.ui.theme.dashboardThemePalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServiceCard(
    cardData: ServiceCardData,
    onClick: () -> Unit,
    onOpenRecharge: (String) -> Unit,
    themeStyle: DashboardThemeStyle,
    modifier: Modifier = Modifier
) {
    var showActions by remember { mutableStateOf(false) }
    val palette = dashboardThemePalette(themeStyle)
    val iconSize = if (palette.compact) 28.dp else 32.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showActions = true }
            ),
        shape = RoundedCornerShape(palette.cardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = palette.cardElevation),
        colors = CardDefaults.cardColors(
            containerColor = palette.cardContainerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(palette.cardPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ServiceBrandIcon(
                    service = cardData.service,
                    size = iconSize
                )
                Spacer(modifier = Modifier.width(palette.contentSpacing))
                Text(
                    text = cardData.service.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.cardContentColor,
                    modifier = Modifier.weight(1f)
                )
                cardData.tier?.let { tier ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(palette.chipRadius),
                        color = palette.secondaryTextColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = tier,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = palette.secondaryTextColor
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusChip(status = cardData.status, palette = palette)
            }

            if (cardData.error != null) {
                Spacer(modifier = Modifier.height(palette.contentSpacing))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = palette.errorColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatError(cardData.error),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.errorColor
                    )
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(palette.contentSpacing))

            val primaryWindow = cardData.windows.firstOrNull()
            primaryWindow?.let { window ->
                QuotaGaugeBar(
                    utilization = window.utilization,
                    label = window.label,
                    showPercentage = true,
                    remainingDays = window.remainingDays,
                    periodDays = window.periodDays,
                    resetsAt = window.resetsAt,
                    resetsAtLabel = window.resetsAtLabel,
                    themePalette = palette
                )
            }

            val secondaryWindows = cardData.windows.drop(1)

            if (secondaryWindows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(palette.windowSpacing))
                Column(
                    verticalArrangement = Arrangement.spacedBy(if (palette.compact) 3.dp else 4.dp)
                ) {
                    secondaryWindows.forEach { window ->
                        QuotaGaugeBar(
                            utilization = window.utilization,
                            label = window.label,
                            showPercentage = true,
                            remainingDays = window.remainingDays,
                            periodDays = window.periodDays,
                            resetsAt = window.resetsAt,
                            resetsAtLabel = window.resetsAtLabel,
                            themePalette = palette
                        )
                    }
                }
            }

            cardData.extraUsage?.let { extra ->
                Spacer(modifier = Modifier.height(palette.windowSpacing))
                val hasInitial = extra.monthlyLimit > 0
                Text(
                    text = if (hasInitial) {
                        val balance = (extra.monthlyLimit - extra.usedCredits).coerceAtLeast(0.0)
                        "Used: ${formatMoney(extra.currency, extra.usedCredits)}  |  Balance: ${formatMoney(extra.currency, balance)}"
                    } else {
                        "Balance: ${formatMoney(extra.currency, extra.usedCredits)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryTextColor
                )
                if (hasInitial) {
                    Spacer(modifier = Modifier.height(if (palette.compact) 3.dp else 4.dp))
                    QuotaGaugeBar(
                        utilization = extra.utilization,
                        label = "Balance",
                        themePalette = palette
                    )
                }
            }

            cardData.lastUpdated?.let { time ->
                Spacer(modifier = Modifier.height(palette.windowSpacing))
                Text(
                    text = "Updated: ${formatInstant(time)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryTextColor.copy(alpha = 0.72f)
                )
            }
        }

        DropdownMenu(
            expanded = showActions,
            onDismissRequest = { showActions = false }
        ) {
            cardData.service.rechargeUrl?.let { rechargeUrl ->
                DropdownMenuItem(
                    text = { Text("Open recharge") },
                    leadingIcon = {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                    },
                    onClick = {
                        showActions = false
                        onOpenRecharge(rechargeUrl)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: ProviderStatus, palette: DashboardThemePalette) {
    val (color, label) = when (status) {
        ProviderStatus.OK -> Pair(palette.successColor, "OK")
        ProviderStatus.WARNING -> Pair(palette.warningColor, "Warning")
        ProviderStatus.ERROR -> Pair(palette.errorColor, "Error")
        ProviderStatus.NEEDS_LOGIN -> Pair(palette.needsLoginColor, "Needs Login")
        ProviderStatus.UNKNOWN -> Pair(palette.unknownColor, "Unknown")
    }
    Surface(
        shape = RoundedCornerShape(palette.chipRadius),
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
        is AppError.NetworkError -> "Network error"
        is AppError.AuthError -> if (error.isTerminal) "Re-authentication required" else "Auth error"
        is AppError.RateLimited -> "Rate limited"
        is AppError.ParseError -> error.message ?: "Response parse error"
        is AppError.CredentialNotFound -> "Not configured"
        is AppError.ServiceUnavailable -> "Service unavailable"
        is AppError.NeedsLogin -> error.message
    }
}

private fun formatInstant(instant: Instant): String {
    return DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

private fun formatMoney(currency: String, amount: Double): String {
    val symbol = when (currency.uppercase()) {
        "CNY", "RMB", "¥" -> "¥"
        "USD", "$" -> "$"
        else -> "$currency "
    }
    return "$symbol${String.format("%.0f", amount)}"
}
