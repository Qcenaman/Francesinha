package com.qcenaman.francesinha.data

import com.qcenaman.francesinha.model.Extra
import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.Pause
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session
import org.json.JSONArray
import org.json.JSONObject

object Codec {
    fun encode(list: List<Session>): String {
        val arr = JSONArray()
        list.forEach { arr.put(sessionToJson(it)) }
        return JSONObject().put("version", 1).put("sessions", arr).toString()
    }

    fun decode(text: String): List<Session> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("sessions") ?: JSONArray()
        return (0 until arr.length()).map { sessionFromJson(arr.getJSONObject(it)) }
    }

    private fun JSONObject.opt(k: String, v: Any?): JSONObject = if (v != null) put(k, v) else this
    private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.doubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null

    private fun pauseToJson(p: Pause): JSONObject = JSONObject()
        .put("id", p.id).put("type", p.type.name).put("start", p.start)
        .opt("end", p.end).put("note", p.note).put("auto", p.auto)

    private fun pauseFromJson(o: JSONObject) = Pause(
        id = o.getLong("id"),
        type = PauseType.from(o.optString("type")),
        start = o.getLong("start"),
        end = o.longOrNull("end"),
        note = o.optString("note", ""),
        auto = o.optBoolean("auto", false),
    )

    private fun extraToJson(e: Extra): JSONObject = JSONObject()
        .put("id", e.id).put("type", e.type.name).put("time", e.time)
        .put("euros", e.euros).put("note", e.note)

    private fun extraFromJson(o: JSONObject) = Extra(
        id = o.getLong("id"),
        type = ExtraType.from(o.optString("type")),
        time = o.getLong("time"),
        euros = o.optDouble("euros", 0.0),
        note = o.optString("note", ""),
    )

    private fun sessionToJson(s: Session): JSONObject {
        val pauses = JSONArray()
        s.pauses.forEach { pauses.put(pauseToJson(it)) }
        val extras = JSONArray()
        s.extras.forEach { extras.put(extraToJson(it)) }
        return JSONObject()
            .put("id", s.id).put("start", s.start).opt("end", s.end)
            .put("gpsMeters", s.gpsMeters)
            .opt("odoStart", s.odoStart).opt("odoEnd", s.odoEnd).opt("manualKm", s.manualKm)
            .put("title", s.title).put("notes", s.notes).put("auto", s.auto)
            .opt("hourlyRate", s.hourlyRate).put("paidBreaks", s.paidBreaks)
            .opt("startLat", s.startLat).opt("startLon", s.startLon)
            .opt("endLat", s.endLat).opt("endLon", s.endLon)
            .put("pauses", pauses)
            .put("extras", extras)
    }

    private fun sessionFromJson(o: JSONObject): Session {
        val arr = o.optJSONArray("pauses") ?: JSONArray()
        val ex = o.optJSONArray("extras") ?: JSONArray()
        return Session(
            id = o.getLong("id"),
            start = o.getLong("start"),
            end = o.longOrNull("end"),
            gpsMeters = o.optDouble("gpsMeters", 0.0),
            odoStart = o.doubleOrNull("odoStart"),
            odoEnd = o.doubleOrNull("odoEnd"),
            manualKm = o.doubleOrNull("manualKm"),
            title = o.optString("title", ""),
            notes = o.optString("notes", ""),
            auto = o.optBoolean("auto", false),
            hourlyRate = o.doubleOrNull("hourlyRate"),
            paidBreaks = o.optBoolean("paidBreaks", false),
            pauses = (0 until arr.length()).map { pauseFromJson(arr.getJSONObject(it)) },
            extras = (0 until ex.length()).map { extraFromJson(ex.getJSONObject(it)) },
            startLat = o.doubleOrNull("startLat"),
            startLon = o.doubleOrNull("startLon"),
            endLat = o.doubleOrNull("endLat"),
            endLon = o.doubleOrNull("endLon"),
        )
    }
}
