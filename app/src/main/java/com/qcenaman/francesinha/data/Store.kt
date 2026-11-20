package com.qcenaman.francesinha.data

import android.content.Context
import com.qcenaman.francesinha.logic.SessionOps
import com.qcenaman.francesinha.model.Extra
import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.Pause
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object Store {
    private var file: File? = null
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()
    private var lastId = 0L

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "sessions.json")
        file = f
        if (f.exists()) {
            runCatching { _sessions.value = Codec.decode(f.readText()).sortedByDescending { it.start } }
        }
    }

    @Synchronized
    fun newId(): Long {
        val t = System.currentTimeMillis()
        lastId = if (t > lastId) t else lastId + 1
        return lastId
    }

    fun openSession(): Session? = _sessions.value.firstOrNull { it.end == null }
    fun get(id: Long): Session? = _sessions.value.firstOrNull { it.id == id }

    @Synchronized
    fun update(block: (List<Session>) -> List<Session>) {
        val next = block(_sessions.value).sortedByDescending { it.start }
        _sessions.value = next
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(Codec.encode(next))
            if (!tmp.renameTo(f)) {
                f.writeText(Codec.encode(next))
                tmp.delete()
            }
        }
    }

    private fun updateOpen(block: (Session) -> Session) =
        update { list -> list.map { if (it.end == null) block(it) else it } }

    fun startSession(time: Long, auto: Boolean, lat: Double? = null, lon: Double? = null, rate: Double? = null) {
        if (openSession() != null) return
        val s = Session(id = newId(), start = time, auto = auto, startLat = lat, startLon = lon, hourlyRate = rate)
        update { it + s }
    }

    fun endSession(time: Long, lat: Double? = null, lon: Double? = null, trimAutoPause: Boolean = false) =
        updateOpen { SessionOps.close(it, time, lat, lon, trimAutoPause) }

    fun startPause(time: Long, type: PauseType, auto: Boolean) {
        val id = newId()
        updateOpen { SessionOps.startPause(it, Pause(id, type, time, null, "", auto)) }
    }

    /** Adds a meal / sleepover to the running trip, or else the most recent trip. */
    fun addExtra(type: ExtraType, time: Long, euros: Double): Boolean {
        val target = openSession() ?: _sessions.value.maxByOrNull { it.start } ?: return false
        val e = Extra(newId(), type, time, euros)
        update { list -> list.map { if (it.id == target.id) SessionOps.addExtra(it, e) else it } }
        return true
    }

    fun endPause(time: Long) = updateOpen { SessionOps.endPause(it, time) }

    fun addDistance(meters: Double) {
        if (meters <= 0 || openSession() == null) return
        updateOpen { SessionOps.addDistance(it, meters) }
    }

    /** Saves an edited session; keeps live GPS distance of a running trip. */
    fun upsert(s: Session) = update { list ->
        val cur = list.firstOrNull { it.id == s.id }
        val fixed = if (cur != null && cur.end == null && s.end == null) s.copy(gpsMeters = cur.gpsMeters) else s
        list.filterNot { it.id == s.id } + fixed
    }

    fun delete(id: Long) = update { list -> list.filterNot { it.id == id } }

    fun mergeWithPrevious(id: Long): Boolean {
        var ok = false
        val gapId = newId()
        update { list ->
            val cur = list.firstOrNull { it.id == id } ?: return@update list
            val prev = list.filter { it.id != id && it.end != null && it.start < cur.start }
                .maxByOrNull { it.start } ?: return@update list
            ok = true
            list.filter { it.id != prev.id && it.id != cur.id } + SessionOps.merge(prev, cur, gapId)
        }
        return ok
    }
}
