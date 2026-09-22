package app.still.ui

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.still.R
import app.still.data.ThemeMode

val Forest = Color(0xFF31594A)
val Lime = Color(0xFFDDEDAD)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EEDB),
    onPrimaryContainer = Color(0xFF243F33),
    secondary = Color(0xFF667B57),
    secondaryContainer = Lime,
    onSecondaryContainer = Color(0xFF283B20),
    background = Color(0xFFF7F8F2),
    onBackground = Color(0xFF202D26),
    surface = Color(0xFFFEFFF9),
    onSurface = Color(0xFF202D26),
    surfaceVariant = Color(0xFFEBEEE5),
    onSurfaceVariant = Color(0xFF68746A),
    outline = Color(0xFF7A857A),
    outlineVariant = Color(0xFFDEE4D9),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8CAA94),
    onPrimary = Color(0xFF102418),
    primaryContainer = Color(0xFF293F32),
    onPrimaryContainer = Color(0xFFB9CABD),
    secondary = Color(0xFF9BAC93),
    onSecondary = Color(0xFF1B2B1D),
    secondaryContainer = Color(0xFF344333),
    onSecondaryContainer = Color(0xFFC0CDB8),
    tertiary = Color(0xFF91AAA5),
    onTertiary = Color(0xFF182C28),
    tertiaryContainer = Color(0xFF2B403B),
    onTertiaryContainer = Color(0xFFBDCFC9),
    background = Color(0xFF111813),
    onBackground = Color(0xFFC7D0C8),
    surface = Color(0xFF19221B),
    onSurface = Color(0xFFC7D0C8),
    surfaceVariant = Color(0xFF2B362E),
    onSurfaceVariant = Color(0xFFA3ADA5),
    surfaceDim = Color(0xFF101613),
    surfaceBright = Color(0xFF29332D),
    surfaceContainerLowest = Color(0xFF0D130F),
    surfaceContainerLow = Color(0xFF171F19),
    surfaceContainer = Color(0xFF1B241E),
    surfaceContainerHigh = Color(0xFF222D25),
    surfaceContainerHighest = Color(0xFF2B362E),
    surfaceTint = Color(0xFF8CAA94),
    inverseSurface = Color(0xFFB8C2B9),
    inverseOnSurface = Color(0xFF1E2B22),
    inversePrimary = Color(0xFF31594A),
    error = Color(0xFFD89891),
    onError = Color(0xFF3E1413),
    errorContainer = Color(0xFF49302D),
    onErrorContainer = Color(0xFFE0B6B0),
    outline = Color(0xFF819083),
    outlineVariant = Color(0xFF38443B),
)

internal data class HeroColors(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val badgeForeground: Color = accent,
)

private val LightHero = HeroColors(Forest, Color.White, Color(0xFFDFE8D9), Lime, Color(0xFF243A28))
internal val DarkHero = HeroColors(
    Color(0xFF22372B), Color(0xFFCFD7CE), Color(0xFFACBDB0),
    Color(0xFF87A891), Color(0xFF102418), Color(0xFFACBDB0),
)
internal val LocalHeroColors = staticCompositionLocalOf { LightHero }
internal val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = mode.isDark(isSystemInDarkTheme())

@Composable
internal fun rememberDialogContext(): Context {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    return remember(context, dark) {
        ContextThemeWrapper(context, if (dark) R.style.Theme_Still_Dialog_Dark else R.style.Theme_Still_Dialog_Light)
    }
}

@Composable
fun StillTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalHeroColors provides if (dark) DarkHero else LightHero) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = Typography(
                displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 64.sp, letterSpacing = (-3).sp),
                headlineLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
                headlineMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.7).sp),
                titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 21.sp, letterSpacing = (-0.4).sp),
                titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
            ),
            content = content,
        )
    }
}
