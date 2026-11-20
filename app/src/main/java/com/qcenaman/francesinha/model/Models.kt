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
    val lat: Double? = null,
    val lon: Double? = null,
)

enum class ExtraKind { MEAL, SLEEPOVER, ADJUSTMENT, EXPENSE }

/** Meals, sleepovers and money adjustments. Discounts count negative. */
enum class ExtraType(val label: String, val kind: ExtraKind, val sign: Int = 1) {
    BREAKFAST("Breakfast", ExtraKind.MEAL),
    LUNCH("Lunch", ExtraKind.MEAL),
    DINNER("Dinner", ExtraKind.MEAL),
    SNACK("Snack", ExtraKind.MEAL),
    SLEEPOVER("Sleepover in truck", ExtraKind.SLEEPOVER),
    BONUS("Prize / bonus", ExtraKind.ADJUSTMENT),
    SUPPLEMENT("Supplement", ExtraKind.ADJUSTMENT),
    DISCOUNT("Discount", ExtraKind.ADJUSTMENT, -1),
    /** Own name, fixed € or a % of the trip's pay, added or deducted. */
    CUSTOM("Custom", ExtraKind.ADJUSTMENT),
    /** Money he paid himself (tolls, parking…); only counts as pay if refunded. */
    EXPENSE("Expense", ExtraKind.EXPENSE);

    val isMeal: Boolean get() = kind == ExtraKind.MEAL
    val isSleepover: Boolean get() = kind == ExtraKind.SLEEPOVER
    val isAdjustment: Boolean get() = kind == ExtraKind.ADJUSTMENT
    val isExpense: Boolean get() = kind == ExtraKind.EXPENSE

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
    /** Name for custom items / expenses. */
    val name: String = "",
    /** If set (custom items), value is this % of the trip's pay instead of [euros]. */
    val percent: Double? = null,
    /** Custom item is deducted instead of added. */
    val deduct: Boolean = false,
    /** Expense is paid back by the employer (then it counts as money received). */
    val refunded: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val displayName: String get() = name.ifBlank { type.label }

    /** Fixed value as it counts toward pay (discounts/deductions negative; % items not included). */
    val signedEuros: Double
        get() = when {
            type == ExtraType.CUSTOM -> if (percent != null) 0.0 else (if (deduct) -euros else euros)
            type.isExpense -> if (refunded) euros else 0.0
            else -> type.sign * euros
        }
}

/** A ready-made custom item to pick quickly. */
data class CustomPreset(val name: String, val euros: Double = 0.0, val percent: Double? = null, val deduct: Boolean = false)

/** A tax / contribution taken from gross pay. */
data class Tax(val name: String, val percent: Double)

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
    /** Paid hours above this many minutes count as overtime (null = no overtime). */
    val overtimeAfterMin: Int? = null,
    /** Pay per overtime hour (null = same as hourlyRate). */
    val overtimeRate: Double? = null,
    /** Final amount on the invoice / payslip, to compare with what was expected. */
    val invoiceEuros: Double? = null,
    /** Expected pay from the plan this trip was made from. */
    val plannedEuros: Double? = null,
    /** Pay per km (null = not paid per km). */
    val perKmRate: Double? = null,
    /** Extra % on the hourly rate for paid time at night / on weekends / on a holiday. */
    val nightPct: Double? = null,
    val weekendPct: Double? = null,
    val holidayPct: Double? = null,
    val holiday: Boolean = false,
    /** Night window, hours of the day (wraps past midnight). */
    val nightFromHour: Int = 22,
    val nightToHour: Int = 7,
    /** Employer / client this trip was for. */
    val client: String = "",
    /** Receipt / invoice photo file names (in the app's receipts folder). */
    val photos: List<String> = emptyList(),
    /** Where the trip is going (from a plan, or set by hand) — used for OsmAnd navigation. */
    val destName: String = "",
    val destLat: Double? = null,
    val destLon: Double? = null,
    /** Planned truck route, encoded "lat,lon;lat,lon…". */
    val route: String = "",
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
