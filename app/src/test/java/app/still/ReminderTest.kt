package app.still

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class ReminderTest {
    @Test
    fun usesTodayBeforeChosenTimeAndTomorrowAfter() {
        val settings = ReminderSettings(true, 8, 0)
        assertEquals(
            ZonedDateTime.parse("2026-01-15T08:00:00Z"),
            Reminders.nextTime(settings, ZonedDateTime.parse("2026-01-15T07:30:00Z")),
        )
        assertEquals(
            ZonedDateTime.parse("2026-01-16T08:00:00Z"),
            Reminders.nextTime(settings, ZonedDateTime.parse("2026-01-15T08:00:00Z")),
        )
    }

    @Test
    fun returnsToSelectedWallClockTimeAfterDaylightSavingGap() {
        val settings = ReminderSettings(true, 2, 30)
        assertEquals(
            ZonedDateTime.parse("2026-03-30T02:30:00+02:00[Europe/Berlin]"),
            Reminders.nextTime(settings, ZonedDateTime.parse("2026-03-29T04:00:00+02:00[Europe/Berlin]")),
        )
    }
}
