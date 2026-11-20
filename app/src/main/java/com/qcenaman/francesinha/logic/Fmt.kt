package com.qcenaman.francesinha.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun duration(ms: Long): String {
        val m = (ms / 60_000).coerceAtLeast(0)
        return String.format(Locale.US, "%dh %02dm", m / 60, m % 60)
    }

    fun hours(ms: Long): String = String.format(Locale.US, "%.2f", ms / 3_600_000.0)
    fun km(km: Double): String = String.format(Locale.US, "%.1f km", km)
    fun eur(v: Double): String = if (v < 0) String.format(Locale.US, "−€%.2f", -v) else String.format(Locale.US, "€%.2f", v)
    fun eurDiff(v: Double): String = if (v >= 0) "+" + eur(v) else eur(v)
    fun num(v: Double): String = String.format(Locale.US, "%.1f", v)
    fun time(ms: Long): String = SimpleDateFormat("HH:mm", I18n.lang.locale).format(Date(ms))
    fun date(ms: Long): String = SimpleDateFormat("EEE dd MMM yyyy", I18n.lang.locale).format(Date(ms))
    fun isoDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
    fun dateTime(ms: Long): String = SimpleDateFormat("EEE dd MMM, HH:mm", I18n.lang.locale).format(Date(ms))
}
