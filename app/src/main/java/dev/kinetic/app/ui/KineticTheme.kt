package dev.kinetic.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class StellarTheme(val label: String) { SYSTEM("Follow system"), LIGHT("Kinetic Light"), DARK("Kinetic Stellar Dark") }
data class Appearance(val theme: StellarTheme, val dynamic: Boolean, val change: (StellarTheme, Boolean) -> Unit)
val LocalAppearance = staticCompositionLocalOf { Appearance(StellarTheme.SYSTEM, false) { _, _ -> } }

internal fun usesDarkPalette(theme: StellarTheme, systemDark: Boolean): Boolean =
    theme == StellarTheme.DARK || theme == StellarTheme.SYSTEM && systemDark

private tailrec fun Context.activityWindow(): android.view.Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.activityWindow()
    else -> null
}

internal fun stellarColors(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFFBCC6FF), onPrimary = Color(0xFF17265D),
    primaryContainer = Color(0xFF2E3D73), onPrimaryContainer = Color(0xFFDFE4FF),
    secondary = Color(0xFFA9D5CE), onSecondary = Color(0xFF123A35),
    secondaryContainer = Color(0xFF284C47), onSecondaryContainer = Color(0xFFC5ECE5),
    tertiary = Color(0xFFE7C590), onTertiary = Color(0xFF412D09),
    background = Color(0xFF101218), onBackground = Color(0xFFE7E7EF),
    surface = Color(0xFF101218), onSurface = Color(0xFFE7E7EF),
    surfaceVariant = Color(0xFF303440), onSurfaceVariant = Color(0xFFC5C8D7),
    outline = Color(0xFF8F93A2),
) else lightColorScheme(
    primary = Color(0xFF40558E), onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE5FF), onPrimaryContainer = Color(0xFF17265D),
    secondary = Color(0xFF32635B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4EDE6), onSecondaryContainer = Color(0xFF143B34),
    tertiary = Color(0xFF75572C), onTertiary = Color.White,
    background = Color(0xFFFAF9FD), onBackground = Color(0xFF1A1C24),
    surface = Color(0xFFFAF9FD), onSurface = Color(0xFF1A1C24),
    surfaceVariant = Color(0xFFE5E5EE), onSurfaceVariant = Color(0xFF454956),
    outline = Color(0xFF727785),
)

@Composable
fun KineticTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("kinetic_appearance", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(runCatching {
        StellarTheme.valueOf(preferences.getString("theme", "SYSTEM")!!)
    }.getOrDefault(StellarTheme.SYSTEM)) }
    var dynamic by remember { mutableStateOf(preferences.getBoolean("dynamic", false)) }
    val dark = usesDarkPalette(theme, isSystemInDarkTheme())
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        context.activityWindow()?.let { window ->
            // The app's explicit theme may differ from the device theme used by enableEdgeToEdge.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else stellarColors(dark)
    CompositionLocalProvider(LocalAppearance provides Appearance(theme, dynamic) { next, useDynamic ->
        if (preferences.edit().putString("theme", next.name).putBoolean("dynamic", useDynamic).commit()) {
            theme = next; dynamic = useDynamic
        }
    }) { MaterialTheme(colorScheme = colors, content = content) }
}
