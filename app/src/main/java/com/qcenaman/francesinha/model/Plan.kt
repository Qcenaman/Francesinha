package com.qcenaman.francesinha.model

enum class StepKind { DRIVE, PAUSE, EXTRA }

/** One piece of a planned trip, in order: a driving stretch, a stop, or a meal/sleepover/money item. */
data class PlanStep(
    val id: Long,
    val kind: StepKind,
    val minutes: Int = 0,
    val km: Double = 0.0,
    val pauseType: PauseType = PauseType.BREAK,
    val extraType: ExtraType = ExtraType.LUNCH,
    val euros: Double = 0.0,
    val note: String = "",
    val name: String = "",
    val percent: Double? = null,
    val deduct: Boolean = false,
    val refunded: Boolean = false,
)

/** A planned / simulated trip used to work out the expected pay before doing it. */
data class Plan(
    val id: Long,
    val name: String = "",
    val start: Long,
    val steps: List<PlanStep> = emptyList(),
    val hourlyRate: Double? = null,
    val paidBreaks: Boolean = false,
    val overtimeAfterMin: Int? = null,
    val overtimeRate: Double? = null,
    val notes: String = "",
    val perKmRate: Double? = null,
    val nightPct: Double? = null,
    val weekendPct: Double? = null,
    val holidayPct: Double? = null,
    val holiday: Boolean = false,
    val nightFromHour: Int = 22,
    val nightToHour: Int = 7,
    val client: String = "",
    val fromName: String = "",
    val fromLat: Double? = null,
    val fromLon: Double? = null,
    val toName: String = "",
    val toLat: Double? = null,
    val toLon: Double? = null,
    /** Truck route result: km, driving minutes, encoded line "lat,lon;…". */
    val routeKm: Double? = null,
    val routeMin: Int? = null,
    val route: String = "",
)
