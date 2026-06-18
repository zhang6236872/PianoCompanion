package com.pianocompanion.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Consistent shape system across the app
val PianoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // chips, badges
    small = RoundedCornerShape(8.dp),        // text fields, buttons
    medium = RoundedCornerShape(12.dp),      // cards
    large = RoundedCornerShape(16.dp),       // bottom sheets, dialogs
    extraLarge = RoundedCornerShape(28.dp)   // FABs
)

// Reusable dimension constants
object Dimens {
    val spacingXs = 4.dp
    val spacingSm = 8.dp
    val spacingMd = 12.dp
    val spacingLg = 16.dp
    val spacingXl = 24.dp
    val spacingXxl = 32.dp

    val cardCorner = 12.dp
    val iconSm = 20.dp
    val iconMd = 28.dp
    val iconLg = 36.dp
    val iconXl = 48.dp

    val buttonHeight = 48.dp
    val fabSize = 72.dp
}
