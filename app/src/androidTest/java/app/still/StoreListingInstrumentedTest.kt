package app.still

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import app.still.data.Entry
import app.still.data.Preferences
import app.still.ui.HistoryScreen
import app.still.ui.HomeScreen
import app.still.ui.StillTheme
import app.still.ui.TrendsScreen
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class StoreListingInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun captureSampleScreensAndIcon() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureStoreListing") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.vadimfrolov.still.debug")
        val output = File(checkNotNull(context.getExternalFilesDir(null)), "store-listing")
        check(output.mkdirs() || output.isDirectory)
        val today = LocalDate.now()
        val entries = List(45) { day ->
            Entry(
                id = (day + 1).toLong(),
                date = today.minusDays(day.toLong()),
                weightKg = 72.4 + day * .035 + (day % 4) * .06,
                bodyFat = 21.5,
                waistCm = 81.2,
                note = if (day % 7 == 0) "Morning check-in" else "",
            )
        }
        val state = TrackerState(entries, Preferences(goalKg = 70.0), loading = false)
        var screen by mutableIntStateOf(0)
        compose.setContent {
            StillTheme(dark = screen == 3) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Surface(Modifier.fillMaxSize().testTag("store-screen"), color = MaterialTheme.colorScheme.background) {
                        when (screen) {
                            1 -> TrendsScreen(state) {}
                            2 -> HistoryScreen(state, {}, {})
                            else -> HomeScreen(state, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        repeat(4) { index ->
            compose.runOnIdle { screen = index }
            compose.waitForIdle()
            save(compose.onNodeWithTag("store-screen").captureToImage().asAndroidBitmap(), File(output, "${index + 1}.png"))
        }
        val icon = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        checkNotNull(context.getDrawable(R.drawable.ic_launcher)).apply {
            setBounds(0, 0, icon.width, icon.height)
            draw(Canvas(icon))
        }
        save(icon, File(output, "icon.png"))
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
