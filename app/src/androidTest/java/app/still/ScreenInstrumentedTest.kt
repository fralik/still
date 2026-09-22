package app.still

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import app.still.data.Entry
import app.still.data.Preferences
import app.still.data.WeightUnit
import app.still.data.FullBackup
import app.still.ui.RestoreBackupDialog
import app.still.ui.EntryEditor
import app.still.ui.HistoryScreen
import app.still.ui.HomeScreen
import app.still.ui.PreferencesEditor
import app.still.ui.SettingsScreen
import app.still.ui.StillTheme
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
                    HomeScreen(TrackerState(loading = false), { clicked = true }, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Hello, you.").assertIsDisplayed()
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
                HomeScreen(TrackerState(entries, loading = false), {}, {}, {}, {})
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
        compose.onNodeWithText("A note to yourself").performScrollTo().performTextInput("After a walk")
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
    fun optionalGoalCanBeRemoved() {
        var saved: Preferences? = null
        compose.setContent {
            StillTheme {
                PreferencesEditor(Preferences(goalKg = 70.0, heightCm = 175.0), false, {}, { saved = it })
            }
        }
        compose.onNodeWithText("Goal weight (kg)").performTextClearance()
        closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Save preferences").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertNull(saved!!.goalKg)
            assertEquals(175.0, saved!!.heightCm!!, 0.0)
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
    fun changingDisplayUnitsKeepsGoalInKilograms() {
        val preferences = Preferences(goalKg = 70.0)
        var updated: Preferences? = null
        compose.setContent {
            StillTheme {
                SettingsScreen(TrackerState(preferences = preferences, loading = false), { updated = it }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Pounds / lb").performClick()
        compose.runOnIdle {
            assertEquals(WeightUnit.LB, updated!!.unit)
            assertEquals(70.0, updated!!.goalKg!!, 0.0)
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
        compose.onNodeWithText("Create full backup").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(requested) }
    }

    @Test
    fun restoreRequiresExplicitReplacementAndCanBeCancelled() {
        var restored = false
        var cancelled = false
        compose.setContent {
            StillTheme {
                RestoreBackupDialog(
                    FullBackup(emptyList(), Preferences(), ReminderSettings(true, 8, 15)),
                    12, false, false, { cancelled = true }, { restored = true },
                )
            }
        }
        compose.onNodeWithText("This replaces all 12 current check-ins", substring = true).assertExists()
        compose.onNodeWithText("Reminders will be paused", substring = true).assertExists()
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
                    FullBackup(emptyList(), Preferences(), ReminderSettings()),
                    0, true, true, {}, {},
                )
            }
        }
        compose.onNodeWithText("Restoring...").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }
}
