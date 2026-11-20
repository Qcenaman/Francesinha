package com.qcenaman.francesinha.logic

import com.qcenaman.francesinha.model.Extra
import com.qcenaman.francesinha.model.Pause
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session

object SessionOps {
    fun addExtra(s: Session, e: Extra): Session = s.copy(extras = (s.extras + e).sortedBy { it.time })

    fun close(s: Session, time: Long, lat: Double?, lon: Double?, trimAutoPause: Boolean): Session {
        val open = s.openPause
        return if (open != null && trimAutoPause && open.auto) {
            s.copy(
                end = maxOf(open.start, s.start),
                pauses = s.pauses.filter { it.id != open.id },
                endLat = lat, endLon = lon,
            )
        } else {
            s.copy(
                end = maxOf(time, s.start),
                pauses = s.pauses.map { if (it.end == null) it.copy(end = maxOf(time, it.start)) else it },
                endLat = lat, endLon = lon,
            )
        }
    }

    fun startPause(s: Session, p: Pause): Session =
        if (s.openPause != null) s else s.copy(pauses = s.pauses + p)

    fun endPause(s: Session, time: Long): Session =
        s.copy(pauses = s.pauses.map { if (it.end == null) it.copy(end = maxOf(time, it.start)) else it })

    fun addDistance(s: Session, meters: Double): Session = s.copy(gpsMeters = s.gpsMeters + meters)

    /** Joins two trips; the gap between them becomes a break. */
    fun merge(prev: Session, cur: Session, gapPauseId: Long): Session {
        val gapEnd = cur.start
        val gapStart = prev.end ?: prev.start
        val gap = if (gapEnd > gapStart) listOf(Pause(gapPauseId, PauseType.BREAK, gapStart, gapEnd)) else emptyList()
        val manual = if (prev.manualKm != null || cur.manualKm != null) prev.km() + cur.km() else null
        return prev.copy(
            end = cur.end,
            gpsMeters = prev.gpsMeters + cur.gpsMeters,
            odoStart = prev.odoStart ?: cur.odoStart,
            odoEnd = cur.odoEnd ?: prev.odoEnd,
            manualKm = manual,
            hourlyRate = prev.hourlyRate ?: cur.hourlyRate,
            paidBreaks = prev.paidBreaks || cur.paidBreaks,
            title = prev.title.ifBlank { cur.title },
            notes = listOf(prev.notes, cur.notes).filter { it.isNotBlank() }.joinToString("\n"),
            pauses = (prev.pauses + gap + cur.pauses).sortedBy { it.start },
            extras = (prev.extras + cur.extras).sortedBy { it.time },
            endLat = cur.endLat, endLon = cur.endLon,
        )
    }
}
