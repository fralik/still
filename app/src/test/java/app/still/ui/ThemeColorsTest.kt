package app.still.ui

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ThemeColorsTest {
    @Test
    fun darkAccentsAreDimmerThanOriginalLime() {
        assertTrue(DarkColors.primary.luminance() < Lime.luminance() * .6f)
        assertTrue(DarkHero.accent.luminance() < Lime.luminance() * .6f)
        assertTrue(DarkColors.surfaceContainerHigh.luminance() < .05f)
    }

    @Test
    fun mutedPaletteRetainsReadableTextContrast() {
        for ((foreground, background) in listOf(
            DarkColors.onSurface to DarkColors.surface,
            DarkColors.onSurfaceVariant to DarkColors.surface,
            DarkColors.onSurfaceVariant to DarkColors.surfaceVariant,
            DarkColors.onPrimary to DarkColors.primary,
            DarkColors.onPrimaryContainer to DarkColors.primaryContainer,
            DarkColors.onSecondaryContainer to DarkColors.secondaryContainer,
            DarkHero.foreground to DarkHero.background,
            DarkHero.muted to DarkHero.background,
            DarkHero.onAccent to DarkHero.accent,
            DarkHero.badgeForeground to DarkHero.foreground.copy(alpha = .12f).compositeOver(DarkHero.background),
        )) {
            val ratio = (max(foreground.luminance(), background.luminance()) + .05f) /
                (min(foreground.luminance(), background.luminance()) + .05f)
            assertTrue("Text contrast $ratio for $foreground on $background", ratio >= 4.5f)
        }
    }
}
