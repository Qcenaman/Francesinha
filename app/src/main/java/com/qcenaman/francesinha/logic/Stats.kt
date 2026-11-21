package com.qcenaman.francesinha.logic

import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session
import com.qcenaman.francesinha.model.Tax
import java.util.Calendar

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
    val overtimeMs: Long = 0,
    val hourPay: Double = 0.0,
    val overtimePay: Double = 0.0,
    val adjustEuros: Double = 0.0,
    val supplementPay: Double = 0.0,
    val nightMs: Long = 0,
    val weekendMs: Long = 0,
    val expenseEuros: Double = 0.0,
    val refundedEuros: Double = 0.0,
    val taxable: Double = 0.0,
    /** Expected pay of the trips that have an invoice amount, and the invoiced total. */
    val invoicedExpected: Double = 0.0,
    val invoiced: Double = 0.0,
    val invoicedTrips: Int = 0,
) {
    /** Everything earned (gross): hours, overtime, supplements, km, meals, sleepovers, extras, refunds. */
    val euros: Double get() = hourPay + overtimePay + supplementPay + mealEuros + sleepoverEuros + adjustEuros + refundedEuros
    val invoiceDiff: Double get() = invoiced - invoicedExpected
}

object TaxCalc {
    fun percent(taxes: List<Tax>): Double = taxes.sumOf { it.percent }
    fun amount(taxable: Double, taxes: List<Tax>): Double = taxable.coerceAtLeast(0.0) * percent(taxes) / 100.0
    fun net(gross: Double, taxable: Double, taxes: List<Tax>): Double = gross - amount(taxable, taxes)
}

object Stats {
    private const val H = 3_600_000.0
    private const val MIN = 60_000L

    fun totalMs(s: Session, now: Long): Long = ((s.end ?: now) - s.start).coerceAtLeast(0)

    fun pauseMs(s: Session, now: Long, type: PauseType? = null): Long {
        val sEnd = s.end ?: now
        return s.pauses.filter { type == null || it.type == type }.sumOf { p ->
            val st = maxOf(p.start, s.start)
            val en = minOf(p.end ?: now, sEnd)
            (en - st).coerceAtLeast(0)
        }
    }

    /** The trip's time minus the given pauses, as [start, end) intervals. */
    private fun intervals(s: Session, now: Long, cut: (PauseType) -> Boolean): List<Pair<Long, Long>> {
        val sEnd = s.end ?: now
        var parts = listOf(s.start to sEnd)
        for (p in s.pauses.filter { cut(it.type) }) {
            val ps = p.start
            val pe = p.end ?: now
            parts = parts.flatMap { (a, b) ->
                if (pe <= a || ps >= b) listOf(a to b)
                else listOfNotNull(if (ps > a) a to ps else null, if (pe < b) pe to b else null)
            }
        }
        return parts.filter { it.second > it.first }
    }

    fun drivingIntervals(s: Session, now: Long) = intervals(s, now) { true }
    fun paidIntervals(s: Session, now: Long) = intervals(s, now) { !s.paidBreaks && !it.paid }

    /** Driving time that falls between [from] and [to]. */
    fun drivingBetween(s: Session, from: Long, to: Long, now: Long): Long =
        drivingIntervals(s, now).sumOf { (a, b) -> (minOf(b, to) - maxOf(a, from)).coerceAtLeast(0) }

    /** Paid working time: on-duty time minus unpaid breaks/rest (unless the job pays breaks). */
    fun paidMs(s: Session, now: Long): Long {
        if (s.paidBreaks) return totalMs(s, now)
        val unpaid = PauseType.entries.filter { !it.paid }.sumOf { pauseMs(s, now, it) }
        return (totalMs(s, now) - unpaid).coerceAtLeast(0)
    }

    fun overtimeMs(s: Session, now: Long): Long {
        val after = s.overtimeAfterMin ?: return 0
        return (paidMs(s, now) - after * MIN).coerceAtLeast(0)
    }

    fun normalMs(s: Session, now: Long): Long = paidMs(s, now) - overtimeMs(s, now)

    /** Pay for normal (non-overtime) hours. */
    fun hourPay(s: Session, now: Long): Double = (s.hourlyRate ?: 0.0) * normalMs(s, now) / H

    fun overtimePay(s: Session, now: Long): Double =
        (s.overtimeRate ?: s.hourlyRate ?: 0.0) * overtimeMs(s, now) / H

    private fun paidMinutesWhere(s: Session, now: Long, test: (Calendar) -> Boolean): Long {
        val c = Calendar.getInstance()
        var n = 0L
        for ((a, b) in paidIntervals(s, now)) {
            var t = a
            while (t < b) {
                c.timeInMillis = t
                if (test(c)) n += minOf(MIN, b - t)
                t += MIN
            }
        }
        return n
    }

    fun nightMs(s: Session, now: Long): Long {
        if ((s.nightPct ?: 0.0) == 0.0) return 0
        val from = s.nightFromHour
        val to = s.nightToHour
        return paidMinutesWhere(s, now) { c ->
            val h = c.get(Calendar.HOUR_OF_DAY)
            if (from <= to) h in from until to else h >= from || h < to
        }
    }

    fun weekendMs(s: Session, now: Long): Long {
        if ((s.weekendPct ?: 0.0) == 0.0) return 0
        return paidMinutesWhere(s, now) { c ->
            val d = c.get(Calendar.DAY_OF_WEEK)
            d == Calendar.SATURDAY || d == Calendar.SUNDAY
        }
    }

    /** Night, weekend and holiday extras (a % of the hourly rate for that time). */
    fun supplementPay(s: Session, now: Long): Double {
        val rate = s.hourlyRate ?: return 0.0
        val night = nightMs(s, now) * (s.nightPct ?: 0.0)
        val weekend = weekendMs(s, now) * (s.weekendPct ?: 0.0)
        val holiday = if (s.holiday) paidMs(s, now) * (s.holidayPct ?: 0.0) else 0.0
        return rate * (night + weekend + holiday) / 100.0 / H
    }

    /** Pay for the work itself; % custom items are worked out from this. */
    fun workPay(s: Session, now: Long): Double =
        hourPay(s, now) + overtimePay(s, now) + supplementPay(s, now)

    fun extraValue(e: com.qcenaman.francesinha.model.Extra, s: Session, now: Long): Double {
        val pct = e.percent
        return if (e.type == ExtraType.CUSTOM && pct != null) {
            val v = workPay(s, now) * pct / 100.0
            if (e.deduct) -v else v
        } else {
            e.signedEuros
        }
    }

    fun extrasEuros(s: Session, now: Long = s.end ?: System.currentTimeMillis()): Double =
        s.extras.sumOf { extraValue(it, s, now) }

    fun mealEuros(s: Session): Double = s.extras.filter { it.type.isMeal }.sumOf { it.euros }
    fun sleepoverEuros(s: Session): Double = s.extras.filter { it.type.isSleepover }.sumOf { it.euros }
    fun adjustEuros(s: Session, now: Long = s.end ?: System.currentTimeMillis()): Double =
        s.extras.filter { it.type.isAdjustment }.sumOf { extraValue(it, s, now) }

    fun expenseEuros(s: Session): Double = s.extras.filter { it.type.isExpense }.sumOf { it.euros }
    fun refundedEuros(s: Session): Double = s.extras.filter { it.type.isExpense && it.refunded }.sumOf { it.euros }

    /** Expected (gross) pay for the trip. */
    fun totalPay(s: Session, now: Long): Double = workPay(s, now) + extrasEuros(s, now)

    /** Part of the pay that taxes apply to (refunds and, if exempt, meals/sleepovers left out). */
    fun taxable(s: Session, now: Long, mealsExempt: Boolean): Double =
        totalPay(s, now) - refundedEuros(s) - if (mealsExempt) mealEuros(s) + sleepoverEuros(s) else 0.0

    /** Invoice minus expected (null if no invoice entered). */
    fun invoiceDiff(s: Session, now: Long): Double? = s.invoiceEuros?.let { it - totalPay(s, now) }

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

    fun totals(list: List<Session>, now: Long, mealsExempt: Boolean = true): Totals {
        val byType = PauseType.entries.associateWith { t -> list.sumOf { pauseMs(it, now, t) } }
            .filterValues { it > 0 }
        val invoicedList = list.filter { it.invoiceEuros != null }
        return Totals(
            sessions = list.size,
            totalMs = list.sumOf { totalMs(it, now) },
            drivingMs = list.sumOf { drivingMs(it, now) },
            pauseMs = list.sumOf { pauseMs(it, now) },
            km = list.sumOf { it.km() },
            pauseByType = byType,
            meals = list.sumOf { s -> s.extras.count { it.type.isMeal } },
            sleepovers = list.sumOf { s -> s.extras.count { it.type.isSleepover } },
            mealEuros = list.sumOf { mealEuros(it) },
            sleepoverEuros = list.sumOf { sleepoverEuros(it) },
            paidMs = list.sumOf { paidMs(it, now) },
            overtimeMs = list.sumOf { overtimeMs(it, now) },
            hourPay = list.sumOf { hourPay(it, now) },
            overtimePay = list.sumOf { overtimePay(it, now) },
            adjustEuros = list.sumOf { adjustEuros(it, now) },
            supplementPay = list.sumOf { supplementPay(it, now) },
            nightMs = list.sumOf { nightMs(it, now) },
            weekendMs = list.sumOf { weekendMs(it, now) },
            expenseEuros = list.sumOf { expenseEuros(it) },
            refundedEuros = list.sumOf { refundedEuros(it) },
            taxable = list.sumOf { taxable(it, now, mealsExempt) },
            invoicedExpected = invoicedList.sumOf { totalPay(it, now) },
            invoiced = invoicedList.sumOf { it.invoiceEuros ?: 0.0 },
            invoicedTrips = invoicedList.size,
        )
    }

    fun inRange(list: List<Session>, from: Long, to: Long): List<Session> =
        list.filter { it.start in from until to }
}
