package app.still.data

import java.time.LocalDate

enum class WeightUnit(val symbol: String) {
    KG("kg"),
    LB("lb");

    fun fromKg(value: Double): Double = if (this == KG) value else value / KG_PER_LB

    fun toKg(value: Double): Double = if (this == KG) value else value * KG_PER_LB

    private companion object {
        const val KG_PER_LB = 0.45359237
    }
}

data class Entry(
    val id: Long = 0,
    val date: LocalDate,
    val weightKg: Double,
    val bodyFat: Double? = null,
    val waistCm: Double? = null,
    val note: String = "",
)

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

data class Preferences(
    val unit: WeightUnit = WeightUnit.KG,
    val goalKg: Double? = null,
    val heightCm: Double? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
