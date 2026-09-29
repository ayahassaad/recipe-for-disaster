package com.recipefordisaster.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Deliberately the Material3 defaults (system font, default sizes) for now.
// Section 14 (Accessibility) requires text to scale correctly with the
// user's system font-size setting — using `sp` units (not `dp`) for all
// text, as below, is what makes that possible; a custom type ramp can be
// layered on later without revisiting this requirement.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
)
