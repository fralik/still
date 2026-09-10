package app.still.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.still.TrackerState
import app.still.data.Entry
import app.still.data.Preferences
import java.time.LocalDate

@Preview(name = "Fresh journal", showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun EmptyJournalPreview() {
    StillTheme {
        Surface { HomeScreen(TrackerState(loading = false), {}, {}, {}, {}) }
    }
}

@Preview(name = "Recorded history", showBackground = true, widthDp = 412, heightDp = 860)
@Preview(name = "Large type", showBackground = true, widthDp = 412, heightDp = 860, fontScale = 1.5f)
@Composable
private fun RecordedJournalPreview() {
    val entries = List(20) { day ->
        Entry(
            id = (day + 1).toLong(),
            date = LocalDate.now().minusDays(day.toLong() * 2),
            weightKg = 72.4 + day * .08 + (day % 3) * .12,
            note = if (day == 0) "A quiet morning" else "",
        )
    }
    StillTheme {
        Surface {
            HomeScreen(TrackerState(entries, Preferences(goalKg = 70.0), loading = false), {}, {}, {}, {})
        }
    }
}

@Preview(name = "Dark journal", showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun DarkJournalPreview() {
    StillTheme(dark = true) {
        Surface { HomeScreen(TrackerState(loading = false), {}, {}, {}, {}) }
    }
}
