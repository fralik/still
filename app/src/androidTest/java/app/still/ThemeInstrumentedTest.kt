package app.still

import android.content.res.Configuration
import android.util.TypedValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.still.data.ThemeMode
import app.still.ui.StillTheme
import app.still.ui.isDarkTheme
import app.still.ui.rememberDialogContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class ThemeInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun systemChangesUpdateComposeAndNativeDialogThemeWithoutChangingPreference() {
        var systemDark by mutableStateOf(false)
        var mode by mutableStateOf(ThemeMode.SYSTEM)
        var background = Color.Unspecified
        var nativeBackground = 0
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                val dark = isDarkTheme(mode)
                StillTheme(dark) {
                    val color = MaterialTheme.colorScheme.background
                    val dialogContext = rememberDialogContext()
                    SideEffect {
                        background = color
                        val value = TypedValue()
                        check(dialogContext.theme.resolveAttribute(android.R.attr.colorBackground, value, true))
                        nativeBackground = value.data
                    }
                    Text(if (dark) "Resolved dark" else "Resolved light")
                }
            }
        }
        compose.onNodeWithText("Resolved light").assertExists()
        val light = compose.runOnIdle { background }
        compose.runOnIdle { systemDark = true }
        compose.onNodeWithText("Resolved dark").assertExists()
        compose.runOnIdle {
            assertEquals(ThemeMode.SYSTEM, mode)
            assertNotEquals(light, background)
            assertEquals(0xFF1B241E.toInt(), nativeBackground)
            mode = ThemeMode.LIGHT
        }
        compose.onNodeWithText("Resolved light").assertExists()
        compose.runOnIdle {
            assertEquals(light, background)
            assertNotEquals(0xFF1B241E.toInt(), nativeBackground)
            systemDark = false
            mode = ThemeMode.DARK
        }
        compose.onNodeWithText("Resolved dark").assertExists()
        compose.runOnIdle {
            mode = ThemeMode.SYSTEM
        }
        compose.onNodeWithText("Resolved light").assertExists()
    }
}
