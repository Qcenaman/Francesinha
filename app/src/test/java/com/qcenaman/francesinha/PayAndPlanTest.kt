package com.qcenaman.francesinha

import com.qcenaman.francesinha.data.Codec
import com.qcenaman.francesinha.logic.I18n
import com.qcenaman.francesinha.logic.Lang
import com.qcenaman.francesinha.logic.PlanCalc
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.logic.Translations
import com.qcenaman.francesinha.model.Extra
import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Plan
import com.qcenaman.francesinha.model.PlanStep
import com.qcenaman.francesinha.model.Session
import com.qcenaman.francesinha.model.StepKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayAndPlanTest {
    private val h = 3_600_000L

    @After
    fun resetLang() {
        I18n.lang = Lang.PT
    }

    @Test
    fun overtimeIsPaidAtItsOwnRate() {
        val s = Session(id = 1, start = 0, end = 10 * h, hourlyRate = 10.0, overtimeAfterMin = 8 * 60, overtimeRate = 15.0)
        assertEquals(2 * h, Stats.overtimeMs(s, 0))
        assertEquals(80.0, Stats.hourPay(s, 0), 0.001)
        assertEquals(30.0, Stats.overtimePay(s, 0), 0.001)
        assertEquals(110.0, Stats.totalPay(s, 0), 0.001)
        // no overtime rate -> same as normal
        assertEquals(100.0, Stats.totalPay(s.copy(overtimeRate = null), 0), 0.001)
    }

    @Test
    fun prizesSupplementsAndDiscounts() {
        val s = Session(
            id = 1, start = 0, end = h,
            extras = listOf(
                Extra(1, ExtraType.BONUS, 0, 50.0),
                Extra(2, ExtraType.SUPPLEMENT, 0, 20.0),
                Extra(3, ExtraType.DISCOUNT, 0, 15.0),
                Extra(4, ExtraType.LUNCH, 0, 9.0),
            ),
        )
        assertEquals(55.0, Stats.adjustEuros(s), 0.001)
        assertEquals(64.0, Stats.totalPay(s, 0), 0.001)
        val t = Stats.totals(listOf(s), 0)
        assertEquals(1, t.meals)
        assertEquals(0, t.sleepovers)
        assertEquals(64.0, t.euros, 0.001)
    }

    @Test
    fun invoiceComparison() {
        val s = Session(id = 1, start = 0, end = 2 * h, hourlyRate = 10.0, invoiceEuros = 18.0)
        assertEquals(-2.0, Stats.invoiceDiff(s, 0)!!, 0.001)
        val t = Stats.totals(listOf(s, s.copy(id = 2, invoiceEuros = null)), 0)
        assertEquals(1, t.invoicedTrips)
        assertEquals(-2.0, t.invoiceDiff, 0.001)
    }

    private val plan = Plan(
        id = 7, name = "Porto - Madrid", start = 0, hourlyRate = 12.0,
        steps = listOf(
            PlanStep(1, StepKind.DRIVE, minutes = 270, km = 380.0),
            PlanStep(2, StepKind.PAUSE, minutes = 45, pauseType = PauseType.BREAK),
            PlanStep(3, StepKind.EXTRA, extraType = ExtraType.LUNCH, euros = 10.0),
            PlanStep(4, StepKind.DRIVE, minutes = 150, km = 220.0),
            PlanStep(5, StepKind.EXTRA, extraType = ExtraType.SLEEPOVER, euros = 30.0),
        ),
    )

    @Test
    fun planLaysOutTimelineAndPay() {
        val s = PlanCalc.toSession(plan)
        assertEquals((270 + 45 + 150) * 60_000L, s.end)
        assertEquals(600.0, s.km(), 0.001)
        assertEquals((270 + 150) * 60_000L, Stats.drivingMs(s, s.end!!))
        // 7h paid at 12 + 10 + 30
        assertEquals(7 * 12.0 + 40.0, PlanCalc.expectedPay(plan), 0.001)
        assertEquals(listOf(0L, 270 * 60_000L, 315 * 60_000L, 315 * 60_000L, 465 * 60_000L), PlanCalc.stepStarts(plan))
        val trip = PlanCalc.toTrip(plan, 99)
        assertEquals(99L, trip.id)
        assertEquals(PlanCalc.expectedPay(plan), trip.plannedEuros!!, 0.001)
    }

    @Test
    fun parseHours() {
        assertEquals(270, PlanCalc.parseHours("4:30"))
        assertEquals(270, PlanCalc.parseHours("4,5"))
        assertEquals(90, PlanCalc.parseHours("1.5"))
        assertEquals("4:30", PlanCalc.hoursText(270))
    }

    @Test
    fun plansRoundTrip() {
        assertEquals(listOf(plan), Codec.decodePlans(Codec.encodePlans(listOf(plan))))
    }

    @Test
    fun newFieldsRoundTrip() {
        val s = Session(id = 1, start = 0, end = h, overtimeAfterMin = 480, overtimeRate = 14.5, invoiceEuros = 99.0, plannedEuros = 101.0)
        assertEquals(listOf(s), Codec.decode(Codec.encode(listOf(s))))
    }

    @Test
    fun portugueseIsDefaultAndTranslationsMatchFormats() {
        assertEquals(Lang.PT, Lang.from(null))
        I18n.lang = Lang.PT
        assertEquals("Viagens", I18n.tr("Trips"))
        I18n.lang = Lang.FR
        assertEquals("Trajets", I18n.tr("Trips"))
        I18n.lang = Lang.EN
        assertEquals("Trips", I18n.tr("Trips"))
        val spec = Regex("%[sd]")
        for (map in listOf(Translations.pt, Translations.fr)) {
            assertEquals(Translations.pt.keys, map.keys)
            for ((k, v) in map) {
                assertEquals("format mismatch for '$k'", spec.findAll(k).map { it.value }.toList(), spec.findAll(v).map { it.value }.toList())
            }
        }
        for (t in ExtraType.entries) assertTrue(t.label in Translations.pt)
        for (t in PauseType.entries) assertTrue(t.label in Translations.pt)
    }
}
