package app.still.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = Color(0xFF203628),
    primaryContainer = Color(0xFF30483B),
    onPrimaryContainer = Color(0xFFE2EDDA),
    secondary = Color(0xFFBBCBAA),
    secondaryContainer = Color(0xFF40513A),
    onSecondaryContainer = Color(0xFFE2EDDA),
    background = Color(0xFF141C18),
    onBackground = Color(0xFFE7EDE2),
    surface = Color(0xFF1E2922),
    onSurface = Color(0xFFE7EDE2),
    surfaceVariant = Color(0xFF2B362E),
    onSurfaceVariant = Color(0xFFAFBBAE),
    outline = Color(0xFF8C998B),
    outlineVariant = Color(0xFF3A483D),
)

@Composable
fun StillTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
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
