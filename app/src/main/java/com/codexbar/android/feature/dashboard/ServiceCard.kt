package com.codexbar.android.feature.dashboard

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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.ProviderStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ServiceCard(
    cardData: ServiceCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatError(cardData.error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(12.dp))

            val primaryWindow = cardData.windows.firstOrNull()
            primaryWindow?.let { window ->
                QuotaGaugeBar(
                    utilization = window.utilization,
                    label = window.label,
                    showPercentage = true,
                    remainingDays = window.remainingDays,
                    periodDays = window.periodDays,
                    resetsAt = window.resetsAt,
                    resetsAtLabel = window.resetsAtLabel
                )
            }

            val secondaryWindows = cardData.windows.drop(1)

            if (secondaryWindows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    secondaryWindows.forEach { window ->
                        QuotaGaugeBar(
                            utilization = window.utilization,
                            label = window.label,
                            showPercentage = true,
                            remainingDays = window.remainingDays,
                            periodDays = window.periodDays,
                            resetsAt = window.resetsAt,
                            resetsAtLabel = window.resetsAtLabel
                        )
                    }
                }
            }

            cardData.extraUsage?.let { extra ->
                Spacer(modifier = Modifier.height(8.dp))
                val hasInitial = extra.monthlyLimit > 0
                Text(
                    text = if (hasInitial) {
                        "Balance: ${extra.currency} ${String.format("%.2f", extra.usedCredits)} / ${String.format("%.2f", extra.monthlyLimit)}"
                    } else {
                        "Balance: ${extra.currency} ${String.format("%.2f", extra.usedCredits)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasInitial) {
                    Spacer(modifier = Modifier.height(4.dp))
                    QuotaGaugeBar(
                        utilization = extra.utilization,
                        label = "Used"
                    )
                }
            }

            cardData.lastUpdated?.let { time ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Updated: ${formatInstant(time)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
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
