package com.qcenaman.francesinha

import com.qcenaman.francesinha.data.Codec
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.logic.TaxCalc
import com.qcenaman.francesinha.model.CustomPreset
import com.qcenaman.francesinha.model.Extra
import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.Pause
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session
import com.qcenaman.francesinha.model.Tax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RulesTest {
    private val h = 3_600_000L

    private fun at(y: Int, m: Int, d: Int, hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, hour, 0, 0)
    }.timeInMillis

    private val tenHours = Session(id = 1, start = 0, end = 10 * h, hourlyRate = 10.0)

    @Test
    fun customFixedAndPercentItems() {
        val s = tenHours.copy(
            extras = listOf(
                Extra(1, ExtraType.CUSTOM, 0, name = "Bonus A", percent = 10.0),
                Extra(2, ExtraType.CUSTOM, 0, name = "Fee", percent = 5.0, deduct = true),
                Extra(3, ExtraType.CUSTOM, 0, 7.0, name = "Fixed"),
                Extra(4, ExtraType.CUSTOM, 0, 3.0, name = "Fixed off", deduct = true),
            ),
        )
        assertEquals(100.0 + 10.0 - 5.0 + 7.0 - 3.0, Stats.totalPay(s, 0), 0.001)
        assertEquals(9.0, Stats.adjustEuros(s, 0), 0.001)
    }

    @Test
    fun expensesOnlyCountWhenRefunded() {
        val s = tenHours.copy(
            extras = listOf(
                Extra(1, ExtraType.EXPENSE, 0, 20.0, name = "Toll", refunded = true),
                Extra(2, ExtraType.EXPENSE, 0, 15.0, name = "Parking"),
            ),
        )
        assertEquals(120.0, Stats.totalPay(s, 0), 0.001)
        assertEquals(35.0, Stats.expenseEuros(s), 0.001)
        assertEquals(20.0, Stats.refundedEuros(s), 0.001)
        assertEquals(100.0, Stats.taxable(s, 0, true), 0.001)
    }

    @Test
    fun taxesAndNet() {
        val s = tenHours.copy(extras = listOf(Extra(1, ExtraType.LUNCH, 0, 9.0)))
        val taxes = listOf(Tax("IRS", 10.0), Tax("SS", 11.0))
        val taxable = Stats.taxable(s, 0, mealsExempt = true)
        assertEquals(100.0, taxable, 0.001)
        assertEquals(21.0, TaxCalc.amount(taxable, taxes), 0.001)
        assertEquals(109.0 - 21.0, TaxCalc.net(Stats.totalPay(s, 0), taxable, taxes), 0.001)
        assertEquals(109.0, Stats.taxable(s, 0, mealsExempt = false), 0.001)
    }

    @Test
    fun nightSupplement() {
        // Thursday 2026-11-19 20:00 -> Friday 02:00, night from 22 to 7 = 4h
        val s = Session(id = 1, start = at(2026, 11, 19, 20), end = at(2026, 11, 20, 2), hourlyRate = 10.0, nightPct = 25.0)
        assertEquals(4 * h, Stats.nightMs(s, 0))
        assertEquals(10.0, Stats.supplementPay(s, 0), 0.001)
        // unpaid break in the night is not counted
        val withBreak = s.copy(pauses = listOf(Pause(9, PauseType.BREAK, at(2026, 11, 19, 23), at(2026, 11, 20, 0))))
        assertEquals(3 * h, Stats.nightMs(withBreak, 0))
    }

    @Test
    fun weekendAndHolidaySupplements() {
        // Saturday 2026-11-21 08:00-12:00
        val sat = Session(id = 1, start = at(2026, 11, 21, 8), end = at(2026, 11, 21, 12), hourlyRate = 10.0, weekendPct = 50.0)
        assertEquals(4 * h, Stats.weekendMs(sat, 0))
        assertEquals(20.0, Stats.supplementPay(sat, 0), 0.001)
        val hol = Session(id = 2, start = at(2026, 12, 8, 8), end = at(2026, 12, 8, 12), hourlyRate = 10.0, holidayPct = 100.0, holiday = true)
        assertEquals(40.0, Stats.supplementPay(hol, 0), 0.001)
        assertEquals(80.0, Stats.totalPay(hol, 0), 0.001)
    }


    @Test
    fun presetsTaxesAndNewFieldsRoundTrip() {
        val presets = listOf(CustomPreset("Bónus", 0.0, 5.0, false), CustomPreset("Quota", 12.5, null, true))
        assertEquals(presets, Codec.decodePresets(Codec.encodePresets(presets)))
        val taxes = listOf(Tax("IRS", 12.0), Tax("Segurança Social", 11.0))
        assertEquals(taxes, Codec.decodeTaxes(Codec.encodeTaxes(taxes)))
        val s = tenHours.copy(
            nightPct = 25.0, weekendPct = 50.0, holidayPct = 100.0, holiday = true,
            nightFromHour = 21, nightToHour = 6, photos = listOf("r1.jpg"),
            extras = listOf(Extra(1, ExtraType.CUSTOM, 0, 0.0, "n", "Bónus", 5.0, true, false), Extra(2, ExtraType.EXPENSE, 0, 4.5, name = "Toll", refunded = true)),
        )
        assertEquals(listOf(s), Codec.decode(Codec.encode(listOf(s))))
    }
}

