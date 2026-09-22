package app.still.data

import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {
    @Test
    fun newPreferencesFollowSystem() {
        assertEquals(ThemeMode.SYSTEM, Preferences().themeMode)
        assertFalse(ThemeMode.SYSTEM.isDark(false))
        assertTrue(ThemeMode.SYSTEM.isDark(true))
    }

    @Test
    fun explicitSelectionsIgnoreSystemChanges() {
        for (systemDark in listOf(false, true)) {
            assertFalse(ThemeMode.LIGHT.isDark(systemDark))
            assertTrue(ThemeMode.DARK.isDark(systemDark))
        }
    }
}
