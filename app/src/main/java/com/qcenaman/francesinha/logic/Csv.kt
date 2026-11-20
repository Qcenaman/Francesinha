package com.qcenaman.francesinha.logic

import com.qcenaman.francesinha.model.Session

object Csv {
    private fun money(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

    private fun esc(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == ';' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun export(sessions: List<Session>, now: Long): String {
        val sb = StringBuilder()
        sb.append("date,start,end,client,title,driving_h,pauses_h,total_h,km,gps_km,odo_start,odo_end,paid_h,eur_per_hour,hours_pay,overtime_h,overtime_pay,km_pay,night_weekend_holiday_pay,meals,sleepovers,meals_eur,sleepovers_eur,prizes_supplements_discounts_custom_eur,expenses_eur,refunded_eur,expected_pay_eur,planned_eur,invoice_eur,invoice_diff_eur,pauses,extras,notes\n")
        for (s in sessions.sortedBy { it.start }) {
            val pauses = s.pauses.joinToString(" | ") { p ->
                "${p.type.label} ${Fmt.time(p.start)}-${p.end?.let { Fmt.time(it) } ?: "now"}" +
                    if (p.note.isNotBlank()) " (${p.note})" else ""
            }
            val row = listOf(
                Fmt.isoDate(s.start),
                Fmt.time(s.start),
                s.end?.let { Fmt.time(it) } ?: "",
                s.client,
                s.title,
                Fmt.hours(Stats.drivingMs(s, now)),
                Fmt.hours(Stats.pauseMs(s, now)),
                Fmt.hours(Stats.totalMs(s, now)),
                Fmt.num(s.km()),
                Fmt.num(s.gpsMeters / 1000.0),
                s.odoStart?.let { Fmt.num(it) } ?: "",
                s.odoEnd?.let { Fmt.num(it) } ?: "",
                Fmt.hours(Stats.paidMs(s, now)),
                s.hourlyRate?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "",
                money(Stats.hourPay(s, now)),
                Fmt.hours(Stats.overtimeMs(s, now)),
                money(Stats.overtimePay(s, now)),
                money(Stats.kmPay(s)),
                money(Stats.supplementPay(s, now)),
                s.extras.count { it.type.isMeal }.toString(),
                s.extras.count { it.type.isSleepover }.toString(),
                money(Stats.mealEuros(s)),
                money(Stats.sleepoverEuros(s)),
                money(Stats.adjustEuros(s, now)),
                money(Stats.expenseEuros(s)),
                money(Stats.refundedEuros(s)),
                money(Stats.totalPay(s, now)),
                s.plannedEuros?.let { money(it) } ?: "",
                s.invoiceEuros?.let { money(it) } ?: "",
                Stats.invoiceDiff(s, now)?.let { money(it) } ?: "",
                pauses,
                s.extras.joinToString(" | ") { e ->
                    "${e.displayName} ${Fmt.time(e.time)} ${money(Stats.extraValue(e, s, now))} EUR" +
                        if (e.note.isNotBlank()) " (${e.note})" else ""
                },
                s.notes,
            )
            sb.append(row.joinToString(",") { esc(it) }).append('\n')
        }
        return sb.toString()
    }
}
