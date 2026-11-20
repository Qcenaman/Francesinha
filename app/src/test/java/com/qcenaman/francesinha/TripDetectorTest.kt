package com.qcenaman.francesinha

import com.qcenaman.francesinha.logic.DetectorConfig
import com.qcenaman.francesinha.logic.DetectorEvent
import com.qcenaman.francesinha.logic.Fix
import com.qcenaman.francesinha.logic.Phase
import com.qcenaman.francesinha.logic.TripDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDetectorTest {
    private val cfg = DetectorConfig(startConfirmMs = 20_000, breakAfterMs = 5 * 60_000L, endAfterMs = 60 * 60_000L)

    // ~0.0003 deg lat ≈ 33 m per 5 s ≈ 24 km/h
    private fun drive(d: TripDetector, t0: Long, steps: Int, lat0: Double): Pair<List<DetectorEvent>, Double> {
        val out = mutableListOf<DetectorEvent>()
        var lat = lat0
        for (i in 0 until steps) {
            lat += 0.0003
            out += d.onFix(Fix(t0 + i * 5_000L, lat, 8.6, speedMps = 6.7f, accuracy = 8f))
        }
        return out to lat
    }

    private fun park(d: TripDetector, t0: Long, minutes: Int, lat: Double): List<DetectorEvent> {
        val out = mutableListOf<DetectorEvent>()
        for (i in 0 until minutes * 12) out += d.onFix(Fix(t0 + i * 5_000L, lat, 8.6, speedMps = 0f, accuracy = 8f))
        return out
    }

    @Test
    fun startsTripAfterConfirmTime() {
        val d = TripDetector(cfg)
        val (ev, _) = drive(d, 0, 10, 41.0)
        val start = ev.filterIsInstance<DetectorEvent.SessionStarted>()
        assertEquals(1, start.size)
        assertEquals(0L, start[0].time)
        assertEquals(Phase.DRIVING, d.phase)
        assertTrue(ev.filterIsInstance<DetectorEvent.Distance>().sumOf { it.meters } > 200)
    }

    @Test
    fun shortStopIsNotABreak() {
        val d = TripDetector(cfg)
        val (_, lat) = drive(d, 0, 10, 41.0)
        val ev = park(d, 60_000, 2, lat)
        assertTrue(ev.none { it is DetectorEvent.PauseStarted })
        assertEquals(Phase.DRIVING, d.phase)
    }

    @Test
    fun longStopBecomesBreakThenTripEnds() {
        val d = TripDetector(cfg)
        val (_, lat) = drive(d, 0, 10, 41.0)
        val ev = park(d, 60_000, 6, lat)
        val p = ev.filterIsInstance<DetectorEvent.PauseStarted>()
        assertEquals(1, p.size)
        assertEquals(60_000L, p[0].time)
        assertEquals(Phase.STOPPED, d.phase)
        val end = d.onTick(60_000 + 61 * 60_000L)
        val e = end.filterIsInstance<DetectorEvent.SessionEnded>()
        assertEquals(1, e.size)
        assertEquals(60_000L, e[0].time)
        assertEquals(Phase.IDLE, d.phase)
    }

    @Test
    fun drivingAgainEndsBreak() {
        val d = TripDetector(cfg)
        val (_, lat) = drive(d, 0, 10, 41.0)
        park(d, 60_000, 6, lat)
        val (ev, _) = drive(d, 60_000 + 7 * 60_000L, 3, lat)
        assertEquals(1, ev.filterIsInstance<DetectorEvent.PauseEnded>().size)
        assertEquals(Phase.DRIVING, d.phase)
    }

    @Test
    fun ignoresInaccurateFixesAndJumps() {
        val d = TripDetector(cfg)
        assertTrue(d.onFix(Fix(0, 41.0, 8.6, 10f, accuracy = 500f)).isEmpty())
        d.onFix(Fix(1_000, 41.0, 8.6, 0f, 5f))
        assertTrue(d.onFix(Fix(2_000, 42.0, 8.6, null, 5f)).isEmpty()) // 111 km in 1 s
        assertEquals(Phase.IDLE, d.phase)
    }

    @Test
    fun manualPauseIsNotAutoEnded() {
        val d = TripDetector(cfg)
        d.sync(Phase.STOPPED, 0L, allowAutoEnd = false)
        assertTrue(d.onTick(10 * 60 * 60_000L).none { it is DetectorEvent.SessionEnded })
    }
}
