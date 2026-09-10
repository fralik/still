package app.still.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MetricsTest {
    private val entry = Entry(date = LocalDate.of(2020, 1, 2), weightKg = 80.0)

    @Test
    fun unitsUseExactPoundConversion() {
        assertEquals("kg", WeightUnit.KG.symbol)
        assertEquals("lb", WeightUnit.LB.symbol)
        assertEquals(0.45359237, WeightUnit.LB.toKg(1.0), 0.0)
        assertEquals(1.0, WeightUnit.LB.fromKg(0.45359237), 0.0)
        assertEquals(80.0, WeightUnit.KG.toKg(80.0), 0.0)
        assertEquals(80.0, WeightUnit.KG.fromKg(80.0), 0.0)
        assertEquals(80.0, WeightUnit.LB.toKg(WeightUnit.LB.fromKg(80.0)), 1e-12)
    }

    @Test
    fun averageAndBmi() {
        assertNull(Metrics.average(emptyList()))
        assertEquals(75.0, Metrics.average(listOf(entry, entry.copy(weightKg = 70.0)))!!, 0.0)
        assertEquals(25.0, Metrics.bmi(81.0, 180.0), 1e-12)
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { Metrics.bmi(invalid, 180.0) }
            assertThrows(IllegalArgumentException::class.java) { Metrics.bmi(80.0, invalid) }
        }
    }

    @Test
    fun progressSupportsLossGainMaintenanceAndOvershoot() {
        assertEquals(0.5, Metrics.progress(100.0, 90.0, 80.0), 0.0)
        assertEquals(0.5, Metrics.progress(60.0, 70.0, 80.0), 0.0)
        assertEquals(0.0, Metrics.progress(100.0, 110.0, 80.0), 0.0)
        assertEquals(0.0, Metrics.progress(60.0, 50.0, 80.0), 0.0)
        assertEquals(1.0, Metrics.progress(100.0, 70.0, 80.0), 0.0)
        assertEquals(1.0, Metrics.progress(60.0, 90.0, 80.0), 0.0)
        assertEquals(1.0, Metrics.progress(80.0, 80.0, 80.0), 0.0)
        assertEquals(0.0, Metrics.progress(80.0, 81.0, 80.0), 0.0)
        assertThrows(IllegalArgumentException::class.java) { Metrics.progress(80.0, Double.NaN, 70.0) }
    }

    @Test
    fun entryValidationAcceptsInclusiveUpperBoundsAndToday() {
        Metrics.validate(
            entry.copy(
                date = LocalDate.now(), weightKg = 650.0, bodyFat = 100.0,
                waistCm = 400.0, note = "a".repeat(2000),
            ),
        )
        Metrics.validate(entry)
    }

    @Test
    fun entryValidationRejectsNonfiniteNonpositiveAndExcessiveValues() {
        val invalid = listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        for (value in invalid) {
            assertThrows(IllegalArgumentException::class.java) { Metrics.validate(entry.copy(weightKg = value)) }
            assertThrows(IllegalArgumentException::class.java) { Metrics.validate(entry.copy(bodyFat = value)) }
            assertThrows(IllegalArgumentException::class.java) { Metrics.validate(entry.copy(waistCm = value)) }
        }
        assertThrows(IllegalArgumentException::class.java) { Metrics.validate(entry.copy(weightKg = 650.1)) }
        assertThrows(IllegalArgumentException::class.java) { Metrics.validate(entry.copy(bodyFat = 100.1)) }
        assertThrows(IllegalArgumentException::class.java) { Metrics.validate(entry.copy(waistCm = 400.1)) }
        assertThrows(IllegalArgumentException::class.java) { Metrics.validate(entry.copy(note = "a".repeat(2001))) }
        assertThrows(IllegalArgumentException::class.java) {
            Metrics.validate(entry.copy(date = LocalDate.now().plusDays(1)))
        }
    }

    @Test
    fun preferencesValidation() {
        Metrics.validatePreferences(Preferences())
        Metrics.validatePreferences(Preferences(goalKg = 650.0, heightCm = 300.0))
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                Metrics.validatePreferences(Preferences(goalKg = value))
            }
            assertThrows(IllegalArgumentException::class.java) {
                Metrics.validatePreferences(Preferences(heightCm = value))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            Metrics.validatePreferences(Preferences(goalKg = 650.1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Metrics.validatePreferences(Preferences(heightCm = 300.1))
        }
    }
}
