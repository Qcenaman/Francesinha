package com.qcenaman.francesinha

import com.qcenaman.francesinha.data.Codec
import com.qcenaman.francesinha.logic.Csv
import com.qcenaman.francesinha.logic.SessionOps
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.model.Extra
import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.Pause
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private val h = 3_600_000L

    private val trip = Session(
        id = 1, start = 0, end = 10 * h, gpsMeters = 500_000.0,
        hourlyRate = 10.0,
        pauses = listOf(
            Pause(2, PauseType.BREAK, 4 * h, 5 * h),          // unpaid 1h
            Pause(3, PauseType.LOADING, 6 * h, 6 * h + h / 2), // paid 30m
        ),
        extras = listOf(
            Extra(4, ExtraType.LUNCH, 4 * h, 8.5),
            Extra(5, ExtraType.SLEEPOVER, 10 * h, 25.0),
        ),
    )

    @Test
    fun drivingIsTotalMinusPauses() {
        assertEquals(10 * h, Stats.totalMs(trip, 0))
        assertEquals(h + h / 2, Stats.pauseMs(trip, 0))
        assertEquals(8 * h + h / 2, Stats.drivingMs(trip, 0))
    }

    @Test
    fun paidHoursSkipUnpaidBreaks() {
        assertEquals(9 * h, Stats.paidMs(trip, 0))
        assertEquals(90.0, Stats.hourPay(trip, 0), 0.001)
        assertEquals(123.5, Stats.totalPay(trip, 0), 0.001)
        assertEquals(10 * h, Stats.paidMs(trip.copy(paidBreaks = true), 0))
    }

    @Test
    fun totalsAddUp() {
        val t = Stats.totals(listOf(trip, trip.copy(id = 9, hourlyRate = 20.0, extras = emptyList())), 0)
        assertEquals(1, t.meals)
        assertEquals(1, t.sleepovers)
        assertEquals(90.0 + 180.0, t.hourPay, 0.001)
        assertEquals(270.0 + 33.5, t.euros, 0.001)
        assertEquals(1000.0, t.km, 0.001)
    }

    @Test
    fun kmPrefersManualThenOdometerThenGps() {
        assertEquals(500.0, trip.km(), 0.001)
        assertEquals(420.0, trip.copy(odoStart = 1000.0, odoEnd = 1420.0).km(), 0.001)
        assertEquals(7.0, trip.copy(odoStart = 1000.0, odoEnd = 1420.0, manualKm = 7.0).km(), 0.001)
    }

    @Test
    fun continuousDrivingResetsAfterLongBreak() {
        val open = trip.copy(end = null, pauses = listOf(Pause(2, PauseType.BREAK, 4 * h, 5 * h)))
        assertEquals(2 * h, Stats.continuousDrivingMs(open, 7 * h))
    }

    @Test
    fun closeTrimsTrailingAutoBreak() {
        val open = Session(id = 1, start = 0, pauses = listOf(Pause(2, PauseType.BREAK, 5 * h, null, auto = true)))
        val c = SessionOps.close(open, 5 * h, null, null, trimAutoPause = true)
        assertEquals(5 * h, c.end)
        assertTrue(c.pauses.isEmpty())
    }

    @Test
    fun mergeTurnsGapIntoBreak() {
        val a = Session(id = 1, start = 0, end = 2 * h, gpsMeters = 1000.0)
        val b = Session(id = 2, start = 3 * h, end = 5 * h, gpsMeters = 2000.0, extras = listOf(Extra(7, ExtraType.DINNER, 4 * h, 9.0)))
        val m = SessionOps.merge(a, b, 99)
        assertEquals(5 * h, m.end)
        assertEquals(3000.0, m.gpsMeters, 0.001)
        assertEquals(1, m.pauses.size)
        assertEquals(h, Stats.pauseMs(m, 0))
        assertEquals(1, m.extras.size)
    }

    @Test
    fun jsonRoundTrip() {
        val list = listOf(trip.copy(odoStart = 12.5, notes = "Porto → Madrid"), Session(id = 2, start = 5))
        assertEquals(list, Codec.decode(Codec.encode(list)))
    }

    @Test
    fun csvHasOneRowPerTrip() {
        val csv = Csv.export(listOf(trip, trip.copy(id = 2, start = h)), 0)
        assertEquals(3, csv.trim().lines().size)
        assertTrue(csv.lines()[0].contains("total_pay_eur"))
    }
}
