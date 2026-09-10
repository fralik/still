package app.still.data

import java.time.LocalDate

object Metrics {
    fun validate(entry: Entry) {
        require(entry.weightKg.isFinite() && entry.weightKg > 0 && entry.weightKg <= 650) {
            "Weight must be finite, greater than 0 and at most 650 kg."
        }
        require(!entry.date.isAfter(LocalDate.now())) { "Date must not be in the future." }
        entry.bodyFat?.let {
            require(it.isFinite() && it > 0 && it <= 100) {
                "Body fat must be finite, greater than 0 and at most 100 percent."
            }
        }
        entry.waistCm?.let {
            require(it.isFinite() && it > 0 && it <= 400) {
                "Waist must be finite, greater than 0 and at most 400 cm."
            }
        }
        require(entry.note.length <= 2000) { "Note must be at most 2000 characters." }
    }

    fun average(entries: List<Entry>): Double? =
        if (entries.isEmpty()) null else entries.map { it.weightKg }.average()

    fun bmi(weightKg: Double, heightCm: Double): Double {
        require(weightKg.isFinite() && weightKg > 0) { "Weight must be finite and positive." }
        require(heightCm.isFinite() && heightCm > 0) { "Height must be finite and positive." }
        val heightMetres = heightCm / 100
        return weightKg / (heightMetres * heightMetres)
    }

    fun progress(startKg: Double, currentKg: Double, goalKg: Double): Double {
        require(listOf(startKg, currentKg, goalKg).all { it.isFinite() && it > 0 }) {
            "Progress weights must be finite and positive."
        }
        // A maintenance target has no distance to travel: only being at the target is complete.
        if (startKg == goalKg) return if (currentKg == goalKg) 1.0 else 0.0
        return ((currentKg - startKg) / (goalKg - startKg)).coerceIn(0.0, 1.0)
    }

    internal fun validatePreferences(value: Preferences) {
        value.goalKg?.let {
            require(it.isFinite() && it > 0 && it <= 650) {
                "Goal must be finite, greater than 0 and at most 650 kg."
            }
        }
        value.heightCm?.let {
            require(it.isFinite() && it > 0 && it <= 300) {
                "Height must be finite, greater than 0 and at most 300 cm."
            }
        }
    }
}
