package space.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import space.app.AppTheme
import space.app.Settings

// Nothing-style: black, white and greys, one red accent.
private val Red = Color(0xFFD71921)
private val dark = darkColorScheme(
    primary = Red, onPrimary = Color.White, primaryContainer = Color(0xFF2A2A2A), onPrimaryContainer = Color.White,
    secondary = Color(0xFFBDBDBD), secondaryContainer = Color(0xFF1E1E1E), onSecondaryContainer = Color.White,
    background = Color.Black, onBackground = Color.White, surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF161616), onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceContainer = Color(0xFF121212), surfaceContainerLow = Color(0xFF0E0E0E), surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222), outline = Color(0xFF3A3A3A), error = Red,
)
private val light = lightColorScheme(
    primary = Red, onPrimary = Color.White, primaryContainer = Color(0xFFEDEDED), onPrimaryContainer = Color.Black,
    secondary = Color(0xFF424242), secondaryContainer = Color(0xFFF2F2F2), onSecondaryContainer = Color.Black,
    background = Color.White, onBackground = Color.Black, surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5), onSurfaceVariant = Color(0xFF616161),
    surfaceContainer = Color(0xFFF7F7F7), surfaceContainerLow = Color(0xFFFAFAFA), surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFE8E8E8), outline = Color(0xFFCCCCCC), error = Red,
)

/** Headings in a monospaced face, the nearest system font to Nothing's dot-matrix look. */
private val typography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontFamily = FontFamily.Monospace),
        titleLarge = titleLarge.copy(fontFamily = FontFamily.Monospace),
        titleMedium = titleMedium.copy(fontFamily = FontFamily.Monospace),
        titleSmall = titleSmall.copy(fontFamily = FontFamily.Monospace),
        labelSmall = labelSmall.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
fun SpaceTheme(content: @Composable () -> Unit) {
    val darkMode = when (Settings(LocalContext.current).theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    MaterialTheme(colorScheme = if (darkMode) dark else light, typography = typography, content = content)
}
