package com.codexbar.android.core.domain.model

enum class DashboardThemeStyle(
    val displayName: String,
    val description: String
) {
    SYSTEM(
        displayName = "System",
        description = "Follow Android colors"
    ),
    FOCUS(
        displayName = "Focus",
        description = "Light cards with green and blue accents"
    ),
    CONTRAST(
        displayName = "Contrast",
        description = "Dark neutral dashboard with warm alerts"
    ),
    BLOOM(
        displayName = "Bloom",
        description = "Bright coral, blue, and green palette"
    ),
    MIDNIGHT(
        displayName = "Midnight",
        description = "Dark, low-distraction dashboard for night checks"
    ),
    PAPER(
        displayName = "Paper",
        description = "High-readability report style with crisp neutrals"
    )
}
