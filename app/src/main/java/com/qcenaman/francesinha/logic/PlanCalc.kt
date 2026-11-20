package com.qcenaman.francesinha.logic

import com.qcenaman.francesinha.model.Extra
import com.qcenaman.francesinha.model.Pause
import com.qcenaman.francesinha.model.Plan
import com.qcenaman.francesinha.model.PlanStep
import com.qcenaman.francesinha.model.Session
import com.qcenaman.francesinha.model.StepKind

object PlanCalc {
    /** Start time of each step on the plan's timeline. */
    fun stepStarts(plan: Plan): List<Long> {
        var t = plan.start
        return plan.steps.map { st ->
            val at = t
            if (st.kind != StepKind.EXTRA) t += st.minutes * 60_000L
            at
        }
    }

    /** Lays the plan out as a trip so the same maths (hours, overtime, pay) applies. */
    fun toSession(plan: Plan): Session {
        var t = plan.start
        var km = 0.0
        val pauses = mutableListOf<Pause>()
        val extras = mutableListOf<Extra>()
        for (st in plan.steps) {
            val len = st.minutes.coerceAtLeast(0) * 60_000L
            when (st.kind) {
                StepKind.DRIVE -> {
                    km += st.km
                    t += len
                }
                StepKind.PAUSE -> {
                    pauses += Pause(st.id, st.pauseType, t, t + len, st.note)
                    t += len
                }
                StepKind.EXTRA -> extras += Extra(st.id, st.extraType, t, st.euros, st.note, st.name, st.percent, st.deduct, st.refunded)
            }
        }
        return Session(
            id = plan.id, start = plan.start, end = t, manualKm = km,
            title = plan.name, notes = plan.notes, pauses = pauses, extras = extras,
            hourlyRate = plan.hourlyRate, paidBreaks = plan.paidBreaks,
            overtimeAfterMin = plan.overtimeAfterMin, overtimeRate = plan.overtimeRate,
            perKmRate = plan.perKmRate, nightPct = plan.nightPct, weekendPct = plan.weekendPct,
            holidayPct = plan.holidayPct, holiday = plan.holiday,
            nightFromHour = plan.nightFromHour, nightToHour = plan.nightToHour, client = plan.client,
            destName = plan.toName, destLat = plan.toLat, destLon = plan.toLon, route = plan.route,
        )
    }

    fun expectedPay(plan: Plan): Double = toSession(plan).let { Stats.totalPay(it, it.end ?: it.start) }

    /** A real trip record from the plan, remembering the planned pay to compare later. */
    fun toTrip(plan: Plan, newId: Long): Session =
        toSession(plan).copy(id = newId, plannedEuros = expectedPay(plan))

    fun applyPay(plan: Plan, s: Session): Plan = plan.copy(
        hourlyRate = s.hourlyRate, paidBreaks = s.paidBreaks,
        overtimeAfterMin = s.overtimeAfterMin, overtimeRate = s.overtimeRate,
        perKmRate = s.perKmRate, nightPct = s.nightPct, weekendPct = s.weekendPct,
        holidayPct = s.holidayPct, holiday = s.holiday,
        nightFromHour = s.nightFromHour, nightToHour = s.nightToHour, client = s.client,
    )

    fun move(steps: List<PlanStep>, index: Int, by: Int): List<PlanStep> {
        val to = index + by
        if (index !in steps.indices || to !in steps.indices) return steps
        val m = steps.toMutableList()
        val x = m.removeAt(index)
        m.add(to, x)
        return m
    }

    /** "4.5", "4,5" or "4:30" -> minutes. */
    fun parseHours(s: String): Int? {
        val v = s.trim()
        if (v.contains(':')) {
            val parts = v.split(':')
            val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return h * 60 + m
        }
        return v.replace(',', '.').toDoubleOrNull()?.let { (it * 60).toInt() }
    }

    fun hoursText(minutes: Int): String = String.format(java.util.Locale.US, "%d:%02d", minutes / 60, minutes % 60)
}
