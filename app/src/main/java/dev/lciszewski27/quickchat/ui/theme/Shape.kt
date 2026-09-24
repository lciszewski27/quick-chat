package dev.lciszewski27.quickchat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * App-wide Material 3 shape scale with expressive contrast.
 * Mirrors the reference app: compact controls stay tight while
 * cards, sheets, and dialogs get generous rounding.
 */
val QuickShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(36.dp)
)
