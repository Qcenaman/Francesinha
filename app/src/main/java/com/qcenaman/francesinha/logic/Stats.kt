package com.qcenaman.francesinha.logic

import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session

data class Totals(
    val sessions: Int = 0,
    val totalMs: Long = 0,
    val drivingMs: Long = 0,
    val pauseMs: Long = 0,
    val km: Double = 0.0,
    val pauseByType: Map<PauseType, Long> = emptyMap(),
    val meals: Int = 0,
    val sleepovers: Int = 0,
    val mealEuros: Double = 0.0,
    val sleepoverEuros: Double = 0.0,
    val paidMs: Long = 0,
    val hourPay: Double = 0.0,
) {
    /** Everything earned: hours + meals + sleepovers. */
    val euros: Double get() = hourPay + mealEuros + sleepoverEuros
}

object Stats {
    fun totalMs(s: Session, now: Long): Long = ((s.end ?: now) - s.start).coerceAtLeast(0)

    fun pauseMs(s: Session, now: Long, type: PauseType? = null): Long {
        val sEnd = s.end ?: now
        return s.pauses.filter { type == null || it.type == type }.sumOf { p ->
            val st = maxOf(p.start, s.start)
            val en = minOf(p.end ?: now, sEnd)
            (en - st).coerceAtLeast(0)
        }
    }

    /** Paid working time: on-duty time minus unpaid breaks/rest (unless the job pays breaks). */
    fun paidMs(s: Session, now: Long): Long {
        if (s.paidBreaks) return totalMs(s, now)
        val unpaid = PauseType.entries.filter { !it.paid }.sumOf { pauseMs(s, now, it) }
        return (totalMs(s, now) - unpaid).coerceAtLeast(0)
    }

    fun hourPay(s: Session, now: Long): Double = (s.hourlyRate ?: 0.0) * paidMs(s, now) / 3_600_000.0

    fun extrasEuros(s: Session): Double = s.extras.sumOf { it.euros }

    fun totalPay(s: Session, now: Long): Double = hourPay(s, now) + extrasEuros(s)

    fun drivingMs(s: Session, now: Long): Long = (totalMs(s, now) - pauseMs(s, now)).coerceAtLeast(0)

    /** Driving time since the last pause of at least [resetMs] (or since start). 0 while paused. */
    fun continuousDrivingMs(s: Session, now: Long, resetMs: Long = 15 * 60_000L): Long {
        if (s.openPause != null) return 0
        val lastReset = s.pauses
            .filter { it.end != null && it.end - it.start >= resetMs }
            .maxOfOrNull { it.end!! } ?: s.start
        val shortPauses = s.pauses.filter { it.end != null && it.start >= lastReset }
            .sumOf { (it.end!! - it.start).coerceAtLeast(0) }
        return ((s.end ?: now) - lastReset - shortPauses).coerceAtLeast(0)
    }

    fun totals(list: List<Session>, now: Long): Totals {
        val byType = PauseType.entries.associateWith { t -> list.sumOf { pauseMs(it, now, t) } }
            .filterValues { it > 0 }
        val extras = list.flatMap { it.extras }
        return Totals(
            sessions = list.size,
            totalMs = list.sumOf { totalMs(it, now) },
            drivingMs = list.sumOf { drivingMs(it, now) },
            pauseMs = list.sumOf { pauseMs(it, now) },
            km = list.sumOf { it.km() },
            pauseByType = byType,
            meals = extras.count { it.type.isMeal },
            sleepovers = extras.count { !it.type.isMeal },
            mealEuros = extras.filter { it.type.isMeal }.sumOf { it.euros },
            sleepoverEuros = extras.filter { !it.type.isMeal }.sumOf { it.euros },
            paidMs = list.sumOf { paidMs(it, now) },
            hourPay = list.sumOf { hourPay(it, now) },
        )
    }

    fun inRange(list: List<Session>, from: Long, to: Long): List<Session> =
        list.filter { it.start in from until to }
}
