package com.codexbar.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.DashboardThemeStyle

@Immutable
data class DashboardThemePalette(
    val backgroundColor: Color,
    val topBarColor: Color,
    val primaryContentColor: Color,
    val cardContainerColor: Color,
    val cardContentColor: Color,
    val secondaryTextColor: Color,
    val accentColor: Color,
    val successColor: Color,
    val warningColor: Color,
    val errorColor: Color,
    val needsLoginColor: Color,
    val unknownColor: Color,
    val cardElevation: Dp,
    val cardRadius: Dp,
    val cardPadding: Dp,
    val contentSpacing: Dp,
    val windowSpacing: Dp,
    val chipRadius: Dp,
    val listHorizontalPadding: Dp,
    val listVerticalPadding: Dp,
    val listItemSpacing: Dp,
    val compact: Boolean
)

@Composable
fun dashboardThemePalette(style: DashboardThemeStyle): DashboardThemePalette {
    val scheme = MaterialTheme.colorScheme
    return when (style) {
        DashboardThemeStyle.SYSTEM -> DashboardThemePalette(
            backgroundColor = scheme.background,
            topBarColor = scheme.surface,
            primaryContentColor = scheme.onSurface,
            cardContainerColor = scheme.surface,
            cardContentColor = scheme.onSurface,
            secondaryTextColor = scheme.onSurfaceVariant,
            accentColor = scheme.primary,
            successColor = Color(0xFF4CAF50),
            warningColor = Color(0xFFFFC107),
            errorColor = scheme.error,
            needsLoginColor = Color(0xFFFF9800),
            unknownColor = Color.Gray,
            cardElevation = 2.dp,
            cardRadius = 8.dp,
            cardPadding = 16.dp,
            contentSpacing = 12.dp,
            windowSpacing = 8.dp,
            chipRadius = 8.dp,
            listHorizontalPadding = 16.dp,
            listVerticalPadding = 16.dp,
            listItemSpacing = 12.dp,
            compact = false
        )

        DashboardThemeStyle.FOCUS -> DashboardThemePalette(
            backgroundColor = Color(0xFFEFF5F2),
            topBarColor = Color(0xFFF7FAF9),
            primaryContentColor = Color(0xFF1C2522),
            cardContainerColor = Color(0xFFF7FAF9),
            cardContentColor = Color(0xFF1C2522),
            secondaryTextColor = Color(0xFF52635D),
            accentColor = Color(0xFF1B7F69),
            successColor = Color(0xFF1B7F3A),
            warningColor = Color(0xFF9A6700),
            errorColor = Color(0xFFB42318),
            needsLoginColor = Color(0xFFB54708),
            unknownColor = Color(0xFF6B7280),
            cardElevation = 1.dp,
            cardRadius = 6.dp,
            cardPadding = 14.dp,
            contentSpacing = 10.dp,
            windowSpacing = 6.dp,
            chipRadius = 6.dp,
            listHorizontalPadding = 14.dp,
            listVerticalPadding = 14.dp,
            listItemSpacing = 10.dp,
            compact = true
        )

        DashboardThemeStyle.CONTRAST -> DashboardThemePalette(
            backgroundColor = Color(0xFF151719),
            topBarColor = Color(0xFF202326),
            primaryContentColor = Color(0xFFF4F2EE),
            cardContainerColor = Color(0xFF202326),
            cardContentColor = Color(0xFFF4F2EE),
            secondaryTextColor = Color(0xFFC6C0B8),
            accentColor = Color(0xFF7DD3FC),
            successColor = Color(0xFF6EE7B7),
            warningColor = Color(0xFFFBBF24),
            errorColor = Color(0xFFF87171),
            needsLoginColor = Color(0xFFF59E0B),
            unknownColor = Color(0xFF9CA3AF),
            cardElevation = 3.dp,
            cardRadius = 4.dp,
            cardPadding = 18.dp,
            contentSpacing = 14.dp,
            windowSpacing = 8.dp,
            chipRadius = 4.dp,
            listHorizontalPadding = 18.dp,
            listVerticalPadding = 18.dp,
            listItemSpacing = 14.dp,
            compact = false
        )

        DashboardThemeStyle.BLOOM -> DashboardThemePalette(
            backgroundColor = Color(0xFFFFF1EB),
            topBarColor = Color(0xFFFFFBF8),
            primaryContentColor = Color(0xFF2C2523),
            cardContainerColor = Color(0xFFFFFBF8),
            cardContentColor = Color(0xFF2C2523),
            secondaryTextColor = Color(0xFF725F5A),
            accentColor = Color(0xFFE25555),
            successColor = Color(0xFF2E7D59),
            warningColor = Color(0xFFB7791F),
            errorColor = Color(0xFFC62828),
            needsLoginColor = Color(0xFFDA6B2D),
            unknownColor = Color(0xFF7C6F6A),
            cardElevation = 1.dp,
            cardRadius = 14.dp,
            cardPadding = 18.dp,
            contentSpacing = 14.dp,
            windowSpacing = 10.dp,
            chipRadius = 12.dp,
            listHorizontalPadding = 18.dp,
            listVerticalPadding = 18.dp,
            listItemSpacing = 16.dp,
            compact = false
        )

        DashboardThemeStyle.MIDNIGHT -> DashboardThemePalette(
            backgroundColor = Color(0xFF0E1116),
            topBarColor = Color(0xFF151A21),
            primaryContentColor = Color(0xFFECF3F7),
            cardContainerColor = Color(0xFF1B222B),
            cardContentColor = Color(0xFFECF3F7),
            secondaryTextColor = Color(0xFFAEB7C2),
            accentColor = Color(0xFF4DD0E1),
            successColor = Color(0xFF42D392),
            warningColor = Color(0xFFF5C542),
            errorColor = Color(0xFFFF6B6B),
            needsLoginColor = Color(0xFFF2A65A),
            unknownColor = Color(0xFF7A8491),
            cardElevation = 2.dp,
            cardRadius = 10.dp,
            cardPadding = 16.dp,
            contentSpacing = 12.dp,
            windowSpacing = 8.dp,
            chipRadius = 10.dp,
            listHorizontalPadding = 16.dp,
            listVerticalPadding = 16.dp,
            listItemSpacing = 12.dp,
            compact = false
        )

        DashboardThemeStyle.PAPER -> DashboardThemePalette(
            backgroundColor = Color(0xFFF6F8FA),
            topBarColor = Color.White,
            primaryContentColor = Color(0xFF1F2328),
            cardContainerColor = Color.White,
            cardContentColor = Color(0xFF1F2328),
            secondaryTextColor = Color(0xFF59636E),
            accentColor = Color(0xFF0969DA),
            successColor = Color(0xFF1A7F64),
            warningColor = Color(0xFF9A6700),
            errorColor = Color(0xFFD1242F),
            needsLoginColor = Color(0xFF8250DF),
            unknownColor = Color(0xFF6E7781),
            cardElevation = 0.dp,
            cardRadius = 2.dp,
            cardPadding = 14.dp,
            contentSpacing = 10.dp,
            windowSpacing = 6.dp,
            chipRadius = 2.dp,
            listHorizontalPadding = 12.dp,
            listVerticalPadding = 12.dp,
            listItemSpacing = 8.dp,
            compact = true
        )
    }
}
