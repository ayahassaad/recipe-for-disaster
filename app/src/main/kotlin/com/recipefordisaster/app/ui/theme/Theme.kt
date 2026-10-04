package com.recipefordisaster.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = DisasterRed,
    onPrimary = PlateCream,
    secondary = StoveOrange,
    // Tonal buttons and selected chips use the secondary container; without
    // this they fall back to Material's default lavender.
    secondaryContainer = ApricotContainer,
    onSecondaryContainer = KitchenCharcoal,
    tertiary = SinkTeal,
    background = PlateCream,
    surface = PlateCream,
    surfaceContainer = CardCreamLight,
    surfaceContainerHigh = CardCreamLight,
    onBackground = KitchenCharcoal,
    onSurface = KitchenCharcoal,
)

private val DarkColors = darkColorScheme(
    primary = StoveOrange,
    onPrimary = CharcoalDeep,
    secondary = DisasterRed,
    secondaryContainer = EmberContainer,
    onSecondaryContainer = PlateCream,
    tertiary = SinkTeal,
    background = KitchenCharcoal,
    surface = KitchenCharcoal,
    surfaceContainer = CardCharcoalDark,
    surfaceContainerHigh = CardCharcoalDark,
    onBackground = PlateCream,
    onSurface = PlateCream,
)

/**
 * Deliberately NOT using dynamic color (Material You). The game has its own
 * absurd-but-serious identity — letting it be overridden by whatever wallpaper
 * colors a phone happens to have would undercut that, and dynamic color also
 * makes contrast harder to guarantee for accessibility (Phase 7). Both color
 * schemes here are placeholders pending real contrast verification.
 */
@Composable
fun RecipeForDisasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
