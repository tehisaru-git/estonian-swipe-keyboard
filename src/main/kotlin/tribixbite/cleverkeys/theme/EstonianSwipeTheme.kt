package tribixbite.cleverkeys.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Original Estonian Swipe app-shell palette; does not alter the IME theme preference. */
private val Light = lightColorScheme(
    primary = Color(0xFF395C84), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E7F6), onPrimaryContainer = Color(0xFF18344F),
    secondary = Color(0xFF426D68), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEEEA), onSecondaryContainer = Color(0xFF23453F),
    background = Color(0xFFF6F8FB), onBackground = Color(0xFF1F2C39),
    surface = Color.White, onSurface = Color(0xFF1F2C39),
    surfaceVariant = Color(0xFFEEF2F6), onSurfaceVariant = Color(0xFF536375),
    outline = Color(0xFF7C8C9C), outlineVariant = Color(0xFFDCE3EA)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB1C9E8), onPrimary = Color(0xFF17334E),
    primaryContainer = Color(0xFF304B68), onPrimaryContainer = Color(0xFFDCE8F6),
    secondary = Color(0xFFAFDCD3), onSecondary = Color(0xFF183E37),
    secondaryContainer = Color(0xFF304D49), onSecondaryContainer = Color(0xFFDCF5EF),
    background = Color(0xFF151D28), onBackground = Color(0xFFE5EAF1),
    surface = Color(0xFF202B3A), onSurface = Color(0xFFE5EAF1),
    surfaceVariant = Color(0xFF2B394A), onSurfaceVariant = Color(0xFFC0CBD8),
    outline = Color(0xFFA4B0BE), outlineVariant = Color(0xFF3B4C5C)
)

@Composable
fun EstonianSwipeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) Dark else Light
    CompositionLocalProvider(LocalKeyboardColorScheme provides keyboardColorSchemeFromMaterial(
        scheme.primary, scheme.secondary, dark
    )) {
        MaterialTheme(colorScheme = scheme, typography = KeyboardTypography,
            shapes = KeyboardShapes, content = content)
    }
}
