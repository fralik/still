package app.still.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvCodecTest {
    private val header = "date,weight_kg,body_fat_percent,waist_cm,note"
    private val legacyHeader = "value|type|date|metric|id|comment"
    private val entry = Entry(date = LocalDate.of(2020, 1, 2), weightKg = 80.25)

    @Test
    fun canonicalRoundTripPreservesAllMeasurementsAndNotes() {
        val entries = listOf(
            entry.copy(bodyFat = 20.5, waistCm = 91.25, note = "Morning"),
            entry.copy(date = entry.date.minusDays(1)),
        )
        val encoded = CsvCodec.encode(entries)
        assertTrue(encoded.startsWith("$header\r\n"))
        assertEquals(entries, CsvCodec.decode(encoded))
        assertEquals(emptyList<Entry>(), CsvCodec.decode(CsvCodec.encode(emptyList())))
    }

    @Test
    fun quotesCommasPipesUnicodeAndEmbeddedNewlinesRoundTrip() {
        for (note in listOf(
            "A, B", "said \"hello\"", "one\ntwo", "one\r\ntwo", "one\rtwo",
            "\"", "\"\"", ",", "  spaced  ", "été ☀ | with pipes", "end\n",
        )) {
            val value = entry.copy(note = note)
            assertEquals(listOf(value), CsvCodec.decode(CsvCodec.encode(listOf(value))))
        }
        assertTrue(CsvCodec.encode(listOf(entry.copy(note = "\"hello\""))).contains("\"\"hello\"\""))
    }

    @Test
    fun supportsLfCrlfBomAndNoFinalNewline() {
        for (newline in listOf("\n", "\r\n")) {
            val text = "$header${newline}2020-01-02,80.25,,,"
            assertEquals(listOf(entry), CsvCodec.decode(text))
            assertEquals(listOf(entry), CsvCodec.decode("\uFEFF$text$newline"))
        }
    }

    @Test
    fun rejectsEmptyUnknownAndNoncanonicalHeaders() {
        for (text in listOf("", "\uFEFF", " ", "date,weight", "$header,extra", "\"date\",weight_kg,body_fat_percent,waist_cm,note")) {
            errorAt(text, 1)
        }
    }

    @Test
    fun rejectsMalformedFieldsDatesAndInvalidMeasurements() {
        for (row in listOf(
            "2020-01-02,80,,,extra,field",
            "2020-01-02,80,,",
            "2020-01-02,NaN,,,",
            "2020-01-02,Infinity,,,",
            "2020-01-02,0,,,",
            "2020-01-02,651,,,",
            "2020-01-02,80,101,,",
            "2020-01-02,80,,401,",
            "2020-01-02,80,-1,,",
            "2020-01-02,80,,NaN,",
            "2020-02-30,80,,,",
            "2020-1-2,80,,,",
            "${LocalDate.now().plusDays(1)},80,,,",
            "2020-01-02, 80,,,",
            "2020-01-02,80,,,${"a".repeat(2001)}",
            "2020-01-02,80,,,abc\"def",
            "2020-01-02,80,,,\"note\"garbage",
            "2020-01-02,80,,,\"unterminated",
            "",
        )) {
            errorAt("$header\n$row\n", 2)
        }
    }

    @Test
    fun reportsPhysicalLineAfterMultilineNote() {
        errorAt("$header\n2020-01-02,80,,,\"first\nsecond\"\n2020-01-03,bad,,,", 4)
    }

    @Test
    fun legacyGroupsMeasurementsAndUsesHistoricalImperialConversions() {
        val decoded = CsvCodec.decode(
            "\uFEFF$legacyHeader|\r\n" +
                "20|BODYFAT|2020-01-02 01:02:03|false|1|fat note|\r\n" +
                "39|WAIST|2020-01-02 11:00:00|false|2|waist note|\r\n" +
                "176|WEIGHT|2020-01-02 09:00:00|false|3|first weight|\r\n" +
                "198|WEIGHT|2020-01-02 01:00:00|false|4|last|weight|\r\n" +
                "180|HEIGHT|2020-01-03 12:00:00|true|5|ignored|\r\n",
        )
        assertEquals(1, decoded.size)
        assertEquals(90.0, decoded.single().weightKg, 1e-12)
        assertEquals(100.0, decoded.single().waistCm!!, 1e-12)
        assertEquals(20.0, decoded.single().bodyFat!!, 0.0)
        assertEquals("last|weight\nfat note\nwaist note", decoded.single().note)
        assertEquals(entry.date, decoded.single().date)
    }

    @Test
    fun legacyMetricRowsAndEmptyNotesAndHeightOnly() {
        assertEquals(
            listOf(entry),
            CsvCodec.decode("$legacyHeader\n80.25|WEIGHT|2020-01-02 12:00:00|true|1||"),
        )
        assertEquals(
            listOf(entry),
            CsvCodec.decode("$legacyHeader\n80.25|WEIGHT|2020-01-02|true|1|"),
        )
        assertEquals(
            emptyList<Entry>(),
            CsvCodec.decode("$legacyHeader\n180|HEIGHT|2020-01-02 12:00:00|true|1||"),
        )
    }

    @Test
    fun legacyRejectsMalformedUnknownOrOrphanedMeasurements() {
        for (row in listOf(
            "80|UNKNOWN|2020-01-02 12:00:00|true|1||",
            "NaN|WEIGHT|2020-01-02 12:00:00|true|1||",
            "0|WEIGHT|2020-01-02 12:00:00|true|1||",
            "80|WEIGHT|2020-01-02 25:00:00|true|1||",
            "80|WEIGHT|2020-02-30 12:00:00|true|1||",
            "80|WEIGHT|${LocalDate.now().plusDays(1)} 12:00:00|true|1||",
            "80|WEIGHT|2020-01-02 12:00:00|maybe|1||",
            "80|WEIGHT|2020-01-02 12:00:00|true|bad||",
            "20|BODYFAT|2020-01-02 12:00:00|true|1||",
            "80|WAIST|2020-01-02 12:00:00|true|1||",
            "80|WEIGHT|2020-01-02",
        )) {
            errorAt("$legacyHeader\n$row", 2)
        }
    }

    @Test
    fun validatesEvenLegacyRowsThatWouldBeOverwritten() {
        errorAt(
            "$legacyHeader\n" +
                "700|WEIGHT|2020-01-02 12:00:00|true|1||\n" +
                "80|WEIGHT|2020-01-02 12:00:00|true|2||",
            2,
        )
    }

    @Test
    fun exportRejectsInvalidEntries() {
        assertThrows(IllegalArgumentException::class.java) {
            CsvCodec.encode(listOf(entry.copy(weightKg = Double.NaN)))
        }
    }

    private fun errorAt(text: String, line: Int) {
        val error = assertThrows(IllegalArgumentException::class.java) { CsvCodec.decode(text) }
        assertTrue("Expected line $line, got: ${error.message}", error.message!!.startsWith("Line $line:"))
    }
}
