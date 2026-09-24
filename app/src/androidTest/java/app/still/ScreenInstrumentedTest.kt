package app.still

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import app.still.data.Entry
import app.still.data.Preferences
import app.still.data.WeightUnit
import app.still.data.FullBackup
import app.still.data.ThemeMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import app.still.ui.RestoreBackupDialog
import app.still.ui.EntryEditor
import app.still.ui.HistoryScreen
import app.still.ui.HomeScreen
import app.still.ui.PreferencesEditor
import app.still.ui.SettingsScreen
import app.still.ui.StillTheme
import app.still.ui.TrendsScreen
import app.still.ui.number
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyHomeHasAnActionAndNoInventedWeight() {
        var clicked = false
        compose.setContent {
            StillTheme {
                Surface {
                    HomeScreen(TrackerState(loading = false), { clicked = true }, {}, {})
                }
            }
        }
        compose.onNodeWithText("Overview").assertIsDisplayed()
        compose.onNodeWithText("No weight recorded").assertIsDisplayed()
        compose.onNodeWithText("Less pressure. More perspective.").assertDoesNotExist()
        compose.onNodeWithText("Log your weight").performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun homeShowsActualLatestWeight() {
        val entries = listOf(
            Entry(1, LocalDate.now(), 72.4),
            Entry(2, LocalDate.now().minusDays(4), 73.0),
        )
        compose.setContent {
            StillTheme {
                HomeScreen(TrackerState(entries, loading = false), {}, {}, {})
            }
        }
        compose.onNodeWithText(number(72.4)).assertIsDisplayed()
        compose.onNodeWithText("Update today's check-in").assertIsDisplayed()
    }

    @Test
    fun entryFormRejectsInvalidWeightThenSavesOptionalFields() {
        var saved: Entry? = null
        compose.setContent {
            StillTheme {
                EntryEditor(null, null, WeightUnit.KG, false, {}, { saved = it }, {})
            }
        }
        compose.onNodeWithText("Weight (kg)").performTextInput("-1")
        closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Save check-in").performScrollTo().performClick()
        compose.onNodeWithText("Enter a positive number for weight.").assertExists()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("Weight (kg)").performScrollTo().performTextReplacement("72,4")
        compose.onNodeWithText("Body fat (%)").performTextInput("21.5")
        compose.onNodeWithText("Waist (cm)").performTextInput("81.2")
        compose.onNodeWithText("Note").performScrollTo().performTextInput("After a walk")
        closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Save check-in").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(72.4, saved!!.weightKg, .000001)
            assertEquals(21.5, saved!!.bodyFat!!, .000001)
            assertEquals(81.2, saved!!.waistCm!!, .000001)
            assertEquals("After a walk", saved!!.note)
        }
    }

    @Test
    fun editingInPoundsDoesNotRoundExistingMetricWeight() {
        val original = Entry(4, LocalDate.now(), 72.456789, bodyFat = 21.123456, waistCm = 81.123456, note = "Original")
        var saved: Entry? = null
        compose.setContent {
            StillTheme {
                EntryEditor(original, null, WeightUnit.LB, false, {}, { saved = it }, {})
            }
        }
        compose.onNodeWithText("Save check-in").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original, saved) }
    }

    @Test
    fun deletionNeedsConfirmation() {
        val entry = Entry(5, LocalDate.now(), 72.0)
        var deleted = false
        compose.setContent {
            StillTheme {
                EntryEditor(entry, null, WeightUnit.KG, false, {}, {}, { deleted = true })
            }
        }
        compose.onNodeWithText("Delete entry").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("Keep entry").performClick()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("Delete entry").performScrollTo().performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }

    @Test
    fun optionalHeightCanBeRemovedWithoutChangingOtherPreferences() {
        var saved: Preferences? = null
        compose.setContent {
            StillTheme {
                PreferencesEditor(Preferences(WeightUnit.LB, 175.0, ThemeMode.DARK), false, {}, { saved = it })
            }
        }
        compose.onNodeWithText("Goal", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Height (cm)").performTextClearance()
        closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Save preferences").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(Preferences(WeightUnit.LB, null, ThemeMode.DARK), saved)
        }
    }

    @Test
    fun historyOpensSelectedEntry() {
        val entry = Entry(7, LocalDate.now(), 72.0, note = "Test note")
        var selected: Entry? = null
        compose.setContent {
            StillTheme {
                HistoryScreen(TrackerState(listOf(entry), loading = false), { selected = it }, {})
            }
        }
        compose.onNodeWithText("Test note").performClick()
        compose.runOnIdle { assertEquals(entry, selected) }
    }

    @Test
    fun changingDisplayUnitsKeepsHeightInCentimeters() {
        val preferences = Preferences(heightCm = 175.0)
        var updated: Preferences? = null
        compose.setContent {
            StillTheme {
                SettingsScreen(TrackerState(preferences = preferences, loading = false), { updated = it }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Pounds / lb").performClick()
        compose.runOnIdle {
            assertEquals(WeightUnit.LB, updated!!.unit)
            assertEquals(175.0, updated!!.heightCm!!, 0.0)
        }
    }

    @Test
    fun fullBackupIsAvailableForAnEmptyJournal() {
        var requested = false
        compose.setContent {
            StillTheme {
                SettingsScreen(
                    TrackerState(loading = false), {}, {}, {}, {},
                    onBackup = { requested = true },
                )
            }
        }
        compose.onNodeWithText("Back up everything").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(requested) }
    }

    @Test
    fun csvActionsAreSecondaryAndStillWorkWhenExpanded() {
        var imported = false
        var exported = false
        compose.setContent {
            StillTheme {
                SettingsScreen(
                    TrackerState(listOf(Entry(1, LocalDate.now(), 72.0)), loading = false),
                    {}, {}, { imported = true }, { exported = true },
                )
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Data tools"))
        compose.onNodeWithText("Import measurements (CSV)").assertDoesNotExist()
        compose.onNodeWithText("Export measurements (CSV)").assertDoesNotExist()
        compose.onNodeWithText("Data tools").performClick()
        compose.onNodeWithText("Import measurements (CSV)").performScrollTo().performClick()
        compose.onNodeWithText("Export measurements (CSV)").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(imported)
            assertTrue(exported)
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Data tools"))
        compose.onNodeWithText("Data tools").performClick()
        compose.onNodeWithText("Import measurements (CSV)").assertDoesNotExist()
        compose.onNodeWithText("Export measurements (CSV)").assertDoesNotExist()
    }

    @Test
    fun emptyJournalAllowsCsvImportButNotExport() {
        compose.setContent {
            StillTheme { SettingsScreen(TrackerState(loading = false), {}, {}, {}, {}) }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Data tools"))
        compose.onNodeWithText("Data tools").performClick()
        compose.onNodeWithText("Import measurements (CSV)").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Export measurements (CSV)").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun settingsUseDirectLabelsAndKeepBackupWarnings() {
        compose.setContent {
            StillTheme { SettingsScreen(TrackerState(loading = false), {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Preferences").assertIsDisplayed()
        compose.onNodeWithText("Theme").assertExists()
        compose.onNodeWithText("System").assertIsSelected()
        compose.onNodeWithText("Made for you.").assertDoesNotExist()
        compose.onNodeWithText("Height").assertExists()
        compose.onNodeWithText("Daily reminder").assertDoesNotExist()
        compose.onNodeWithText("Goal & height").assertDoesNotExist()
        compose.onNodeWithText("A gentle nudge").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Backup files are not encrypted.", substring = true))
        compose.onNodeWithText("Backup files are not encrypted.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Restoring replaces the current journal.", substring = true).assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("About"))
        compose.onNodeWithText("About").assertIsDisplayed()
        compose.onNodeWithText("Private by design.").assertDoesNotExist()
        compose.onNodeWithText("Still has no internet permission", substring = true).assertDoesNotExist()
    }

    @Test
    fun entryWithoutNotesDoesNotGetFillerText() {
        compose.setContent {
            StillTheme {
                HistoryScreen(TrackerState(listOf(Entry(1, LocalDate.now(), 72.0)), loading = false), {}, {})
            }
        }
        compose.onNodeWithText("History").assertIsDisplayed()
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("A moment for yourself").assertDoesNotExist()
        compose.onNodeWithText("ONE DAY AT A TIME").assertDoesNotExist()
    }

    @Test
    fun themeSelectorSupportsSystemAndBothExplicitOverrides() {
        val original = Preferences(WeightUnit.LB, 175.0)
        var preferences by mutableStateOf(original)
        compose.setContent {
            StillTheme {
                SettingsScreen(TrackerState(preferences = preferences, loading = false), { preferences = it }, {}, {}, {})
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Theme"))
        compose.onNodeWithText("System").assertIsSelected()
        for (mode in listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM)) {
            compose.onNodeWithText(mode.label).performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(original.copy(themeMode = mode), preferences) }
        }
    }

    @Test
    fun restoreRequiresExplicitReplacementAndCanBeCancelled() {
        var restored = false
        var cancelled = false
        compose.setContent {
            StillTheme {
                RestoreBackupDialog(
                    FullBackup(emptyList(), Preferences()),
                    12, false, { cancelled = true }, { restored = true },
                )
            }
        }
        compose.onNodeWithText("This replaces all 12 current check-ins", substring = true).assertExists()
        compose.onNodeWithText("Reminder", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Goal:", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertFalse(restored) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertTrue(cancelled)
            assertFalse(restored)
        }
        compose.onNodeWithText("Replace journal").performClick()
        compose.runOnIdle { assertTrue(restored) }
    }

    @Test
    fun restoreButtonsAreDisabledWhileWriting() {
        compose.setContent {
            StillTheme {
                RestoreBackupDialog(
                    FullBackup(emptyList(), Preferences()),
                    0, true, {}, {},
                )
            }
        }
        compose.onNodeWithText("Restoring...").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun homeFlowsDirectlyFromChartToRecentEntriesWithoutGoalCard() {
        compose.setContent {
            StillTheme { HomeScreen(TrackerState(listOf(Entry(1, LocalDate.now(), 72.0)), loading = false), {}, {}, {}) }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Recent check-ins"))
        compose.onNodeWithText("Recent check-ins").assertIsDisplayed()
        compose.onNodeWithText("Goal weight").assertDoesNotExist()
        compose.onNodeWithText("Set goal").assertDoesNotExist()
    }

    @Test
    fun trendsRetainsBmiWithoutGoalCard() {
        compose.setContent {
            StillTheme {
                TrendsScreen(TrackerState(listOf(Entry(1, LocalDate.now(), 81.0)), Preferences(heightCm = 180.0), loading = false))
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Body mass index (BMI)"))
        compose.onNodeWithText("Body mass index (BMI)").assertIsDisplayed()
        compose.onNodeWithText(number(25.0)).assertExists()
        compose.onNodeWithText("Goal weight").assertDoesNotExist()
    }

    @Test
    fun heightEditorRejectsInvalidValuesAndKeepsUnchangedPrecision() {
        val original = Preferences(heightCm = 175.123456)
        var saved: Preferences? = null
        compose.setContent { StillTheme { PreferencesEditor(original, false, {}, { saved = it }) } }
        compose.onNodeWithText("Save preferences").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(original, saved); saved = null }
        compose.onNodeWithText("Height (cm)").performTextReplacement("301")
        closeSoftKeyboard()
        compose.onNodeWithText("Save preferences").performScrollTo().performClick()
        compose.onNodeWithText("Height must be at most 300 cm.").assertExists()
        compose.runOnIdle { assertNull(saved) }
    }
}
