package com.qcenaman.francesinha.logic

import com.qcenaman.francesinha.model.Session

object Csv {
    private fun esc(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == ';' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun export(sessions: List<Session>, now: Long): String {
        val sb = StringBuilder()
        sb.append("date,start,end,title,driving_h,pauses_h,total_h,km,gps_km,odo_start,odo_end,paid_h,eur_per_hour,hours_pay,meals,sleepovers,meals_sleepovers_eur,total_pay_eur,pauses,extras,notes\n")
        for (s in sessions.sortedBy { it.start }) {
            val pauses = s.pauses.joinToString(" | ") { p ->
                "${p.type.label} ${Fmt.time(p.start)}-${p.end?.let { Fmt.time(it) } ?: "now"}" +
                    if (p.note.isNotBlank()) " (${p.note})" else ""
            }
            val row = listOf(
                Fmt.isoDate(s.start),
                Fmt.time(s.start),
                s.end?.let { Fmt.time(it) } ?: "",
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
                String.format(java.util.Locale.US, "%.2f", Stats.hourPay(s, now)),
                s.extras.count { it.type.isMeal }.toString(),
                s.extras.count { !it.type.isMeal }.toString(),
                String.format(java.util.Locale.US, "%.2f", s.extras.sumOf { it.euros }),
                String.format(java.util.Locale.US, "%.2f", Stats.totalPay(s, now)),
                pauses,
                s.extras.joinToString(" | ") { e ->
                    "${e.type.label} ${Fmt.time(e.time)} ${String.format(java.util.Locale.US, "%.2f", e.euros)} EUR" +
                        if (e.note.isNotBlank()) " (${e.note})" else ""
                },
                s.notes,
            )
            sb.append(row.joinToString(",") { esc(it) }).append('\n')
        }
        return sb.toString()
    }
}
