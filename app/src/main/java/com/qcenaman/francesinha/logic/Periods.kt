package com.qcenaman.francesinha.logic

import java.util.Calendar

enum class Period(val label: String) {
    TODAY("Today"), YESTERDAY("Yesterday"), WEEK("This week"), LAST_WEEK("Last week"),
    MONTH("This month"), LAST_MONTH("Last month"), ALL("All");

    fun range(now: Long): Pair<Long, Long> {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        fun startOfWeek(): Calendar = (c.clone() as Calendar).apply {
            val dow = get(Calendar.DAY_OF_WEEK)
            val back = (dow - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_YEAR, -back)
        }
        return when (this) {
            TODAY -> c.timeInMillis to (c.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            YESTERDAY -> (c.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis to c.timeInMillis
            WEEK -> startOfWeek().let { it.timeInMillis to (it.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis }
            LAST_WEEK -> startOfWeek().let { (it.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis to it.timeInMillis }
            MONTH -> (c.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.let {
                it.timeInMillis to (it.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
            }
            LAST_MONTH -> (c.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.let {
                (it.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.timeInMillis to it.timeInMillis
            }
            ALL -> 0L to Long.MAX_VALUE
        }
    }
}
