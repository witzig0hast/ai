package de.hastnetwork.jarvis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import de.hastnetwork.jarvis.data.local.DarkModeOverride

private val LightColors = lightColorScheme(
    primary = BluePrimaryLight,
    onPrimary = OnBluePrimaryLight,
    primaryContainer = BluePrimaryContainerLight,
    onPrimaryContainer = OnBluePrimaryContainerLight,
    secondary = NeutralSecondaryLight,
    onSecondary = OnNeutralSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
    onError = OnErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = OnBluePrimaryDark,
    primaryContainer = BluePrimaryContainerDark,
    onPrimaryContainer = OnBluePrimaryContainerDark,
    secondary = NeutralSecondaryDark,
    onSecondary = OnNeutralSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
    onError = OnErrorDark,
)

/**
 * Resolves whether dark mode should be active from the Settings override
 * ([DarkModeOverride.SYSTEM] falls back to [isSystemInDarkTheme]).
 */
@Composable
fun rememberIsDarkTheme(override: DarkModeOverride): Boolean = when (override) {
    DarkModeOverride.SYSTEM -> isSystemInDarkTheme()
    DarkModeOverride.LIGHT -> false
    DarkModeOverride.DARK -> true
}

@Composable
fun JarvisTheme(
    darkModeOverride: DarkModeOverride = DarkModeOverride.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = rememberIsDarkTheme(darkModeOverride)
    val colorScheme = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        content = content,
    )
}
