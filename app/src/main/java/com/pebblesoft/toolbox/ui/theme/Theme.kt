package com.pebblesoft.toolbox.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * The app's look.
 *
 * Deliberately calm and ordinary: a person may open this app while frightened,
 * or while someone is watching over their shoulder. Nothing here shouts, no
 * alarm reds in the resting state, no branding that hints at what the app
 * holds. Colour is spent only where meaning lives — a recording that is real
 * evidence, and a step that is still missing.
 *
 * Dynamic colour is NOT used: the palette must look the same on every phone, so
 * that a screenshot from one device means the same as from another, and so the
 * neutral disguise never picks up a loud system accent.
 */

private val Ink = Color(0xFF13181F)
private val Slate = Color(0xFF2C3A47)
private val Teal = Color(0xFF00695C)
private val TealLight = Color(0xFF4DB6AC)
private val Sand = Color(0xFFF7F5F1)
private val Paper = Color(0xFFFFFFFF)
private val Amber = Color(0xFF9A6700)
private val AmberLight = Color(0xFFE3B341)
private val Rust = Color(0xFF8E3B2F)
private val RustLight = Color(0xFFE49188)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE3EA),
    onSecondaryContainer = Ink,
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFDF0D5),
    onTertiaryContainer = Color(0xFF2E1F00),
    error = Rust,
    onError = Color.White,
    errorContainer = Color(0xFFFBE1DD),
    onErrorContainer = Color(0xFF3B0A05),
    background = Sand,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E6E1),
    onSurfaceVariant = Color(0xFF4A4741),
    outline = Color(0xFFBFBBB4),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00332C),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFB6C4D2),
    onSecondary = Color(0xFF1E2932),
    secondaryContainer = Color(0xFF35434F),
    onSecondaryContainer = Color(0xFFDCE3EA),
    tertiary = AmberLight,
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF564000),
    onTertiaryContainer = Color(0xFFFDF0D5),
    error = RustLight,
    onError = Color(0xFF551108),
    errorContainer = Color(0xFF73281D),
    onErrorContainer = Color(0xFFFBE1DD),
    background = Color(0xFF14171A),
    onBackground = Color(0xFFE4E2DD),
    surface = Color(0xFF1B1F23),
    onSurface = Color(0xFFE4E2DD),
    surfaceVariant = Color(0xFF33383D),
    onSurfaceVariant = Color(0xFFC6C2BB),
    outline = Color(0xFF6E736F),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
        // Body text is a notch larger than Material's default: this is read in a
        // hurry, sometimes through tears, sometimes by someone older.
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun ToolboxTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors
    val view = LocalContext.current

    SideEffect {
        (view as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
