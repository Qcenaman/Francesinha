package com.qcenaman.francesinha.logic

data class Fix(
    val time: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Float? = null,
    val accuracy: Float? = null,
)

data class DetectorConfig(
    val startSpeedKmh: Double = 12.0,
    val startConfirmMs: Long = 20_000,
    val stopSpeedKmh: Double = 5.0,
    val breakAfterMs: Long = 5 * 60_000L,
    val endAfterMs: Long = 120 * 60_000L,
    val maxAccuracyM: Float = 50f,
    val maxSpeedKmh: Double = 200.0,
)

enum class Phase { IDLE, DRIVING, STOPPED }

sealed class DetectorEvent {
    data class SessionStarted(val time: Long, val lat: Double, val lon: Double) : DetectorEvent()
    data class PauseStarted(val time: Long) : DetectorEvent()
    data class PauseEnded(val time: Long) : DetectorEvent()
    data class Distance(val meters: Double) : DetectorEvent()
    data class SessionEnded(val time: Long, val lat: Double?, val lon: Double?) : DetectorEvent()
}

/**
 * Pure state machine: GPS fixes in, trip events out.
 * IDLE -> DRIVING when moving above startSpeed for startConfirm.
 * DRIVING -> STOPPED (auto break) when slow for breakAfter.
 * STOPPED -> DRIVING when moving again; STOPPED -> IDLE (trip ends) after endAfter.
 */
class TripDetector(var config: DetectorConfig = DetectorConfig()) {
    var phase: Phase = Phase.IDLE
        private set

    private var last: Fix? = null
    private var movingSince: Long? = null
    private var startFix: Fix? = null
    private var pendingMeters = 0.0
    private var stoppedSince: Long? = null
    private var canAutoEnd = true

    /** Align with the stored state (manual actions, restarts). */
    fun sync(external: Phase, since: Long?, allowAutoEnd: Boolean = true) {
        canAutoEnd = allowAutoEnd
        if (external == phase) {
            if (external == Phase.STOPPED && since != null) stoppedSince = since
            return
        }
        phase = external
        movingSince = null
        pendingMeters = 0.0
        stoppedSince = if (external == Phase.STOPPED) since else null
    }

    fun onFix(fix: Fix): List<DetectorEvent> {
        val acc = fix.accuracy
        if (acc != null && acc > config.maxAccuracyM) return onTick(fix.time)
        val prev = last
        var meters = 0.0
        var derivedKmh: Double? = null
        if (prev != null) {
            val dtMs = fix.time - prev.time
            if (dtMs <= 0) return emptyList()
            meters = Geo.distanceMeters(prev.lat, prev.lon, fix.lat, fix.lon)
            val kmh = meters / (dtMs / 1000.0) * 3.6
            if (kmh > config.maxSpeedKmh) return emptyList() // GPS jump, drop this fix
            derivedKmh = kmh
        }
        last = fix
        val kmh = fix.speedMps?.let { it * 3.6 } ?: derivedKmh ?: 0.0
        val out = mutableListOf<DetectorEvent>()

        when (phase) {
            Phase.IDLE -> {
                if (kmh >= config.startSpeedKmh) {
                    val since = movingSince
                    if (since == null) {
                        movingSince = fix.time
                        startFix = prev ?: fix
                        pendingMeters = meters
                    } else {
                        pendingMeters += meters
                    }
                    val ms = movingSince!!
                    if (fix.time - ms >= config.startConfirmMs) {
                        val sf = startFix ?: fix
                        out += DetectorEvent.SessionStarted(ms, sf.lat, sf.lon)
                        if (pendingMeters > 0) out += DetectorEvent.Distance(pendingMeters)
                        phase = Phase.DRIVING
                        movingSince = null
                        pendingMeters = 0.0
                        stoppedSince = null
                    }
                } else {
                    movingSince = null
                    pendingMeters = 0.0
                }
            }

            Phase.DRIVING -> {
                if (kmh >= config.stopSpeedKmh) {
                    if (meters > 0) out += DetectorEvent.Distance(meters)
                    stoppedSince = null
                } else {
                    if (stoppedSince == null) stoppedSince = fix.time
                    out += checkStopped(fix.time)
                }
            }

            Phase.STOPPED -> {
                if (kmh >= config.startSpeedKmh) {
                    out += DetectorEvent.PauseEnded(fix.time)
                    if (meters > 0) out += DetectorEvent.Distance(meters)
                    phase = Phase.DRIVING
                    stoppedSince = null
                } else {
                    if (stoppedSince == null) stoppedSince = fix.time
                    out += checkStopped(fix.time)
                }
            }
        }
        return out
    }

    /** Called periodically so trips can end even without new fixes. */
    fun onTick(now: Long): List<DetectorEvent> =
        if (phase == Phase.IDLE || stoppedSince == null) emptyList() else checkStopped(now)

    private fun checkStopped(now: Long): List<DetectorEvent> {
        val since = stoppedSince ?: return emptyList()
        val out = mutableListOf<DetectorEvent>()
        if (phase == Phase.DRIVING && now - since >= config.breakAfterMs) {
            out += DetectorEvent.PauseStarted(since)
            phase = Phase.STOPPED
        }
        if (phase == Phase.STOPPED && canAutoEnd && now - since >= config.endAfterMs) {
            out += DetectorEvent.SessionEnded(since, last?.lat, last?.lon)
            phase = Phase.IDLE
            stoppedSince = null
            movingSince = null
        }
        return out
    }
}
