package com.qcenaman.francesinha.model

/** [paid]: counts as paid working time (breaks and rest don't, unless the job pays breaks). */
enum class PauseType(val label: String, val paid: Boolean) {
    BREAK("Break", false),
    REST("Rest", false),
    WORK("Other work", true),
    LOADING("Loading / Unloading", true),
    FUEL("Fuel", true),
    WAITING("Waiting", true);

    companion object {
        fun from(name: String?): PauseType = entries.firstOrNull { it.name == name } ?: BREAK
    }
}

data class Pause(
    val id: Long,
    val type: PauseType,
    val start: Long,
    val end: Long?,
    val note: String = "",
    val auto: Boolean = false,
)

enum class ExtraType(val label: String, val isMeal: Boolean) {
    BREAKFAST("Breakfast", true),
    LUNCH("Lunch", true),
    DINNER("Dinner", true),
    SNACK("Snack", true),
    SLEEPOVER("Sleepover in truck", false);

    companion object {
        fun from(name: String?): ExtraType = entries.firstOrNull { it.name == name } ?: LUNCH
    }
}

/** A meal or a night slept in the truck, with what it is worth in pay (EUR). */
data class Extra(
    val id: Long,
    val type: ExtraType,
    val time: Long,
    val euros: Double = 0.0,
    val note: String = "",
)

/** One trip / work session. Driving time = total time minus pauses. */
data class Session(
    val id: Long,
    val start: Long,
    val end: Long? = null,
    val gpsMeters: Double = 0.0,
    val odoStart: Double? = null,
    val odoEnd: Double? = null,
    val manualKm: Double? = null,
    val title: String = "",
    val notes: String = "",
    val auto: Boolean = false,
    val hourlyRate: Double? = null,
    val paidBreaks: Boolean = false,
    val pauses: List<Pause> = emptyList(),
    val extras: List<Extra> = emptyList(),
    val startLat: Double? = null,
    val startLon: Double? = null,
    val endLat: Double? = null,
    val endLon: Double? = null,
) {
    val isOpen: Boolean get() = end == null
    val openPause: Pause? get() = pauses.firstOrNull { it.end == null }

    fun odoKm(): Double? =
        if (odoStart != null && odoEnd != null && odoEnd >= odoStart) odoEnd - odoStart else null

    /** Manual km wins, then odometer, then GPS. */
    fun km(): Double = manualKm ?: odoKm() ?: (gpsMeters / 1000.0)
}
