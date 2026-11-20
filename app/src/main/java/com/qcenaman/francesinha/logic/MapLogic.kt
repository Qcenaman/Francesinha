package com.qcenaman.francesinha.logic

import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.PlanStep
import com.qcenaman.francesinha.model.Session
import com.qcenaman.francesinha.model.StepKind
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LatLon(val lat: Double, val lon: Double)

/** Everything a map shows: lines and points. */
data class MapData(
    val driven: List<LatLon> = emptyList(),
    val planned: List<LatLon> = emptyList(),
    val stops: List<LatLon> = emptyList(),
    val extras: List<LatLon> = emptyList(),
    val start: LatLon? = null,
    val end: LatLon? = null,
    val me: LatLon? = null,
) {
    val all: List<LatLon> get() = driven + planned + stops + extras + listOfNotNull(start, end, me)
}

data class TruckSize(
    val height: Double = 4.0,
    val width: Double = 2.55,
    val length: Double = 16.5,
    val weight: Double = 40.0,
    val axleLoad: Double = 11.5,
    val hazmat: Boolean = false,
)

data class Place(val name: String, val lat: Double, val lon: Double)
data class TruckRoute(val km: Double, val minutes: Int, val points: List<LatLon>)

object RouteLine {
    fun encode(points: List<LatLon>): String =
        points.joinToString(";") { String.format(Locale.US, "%.5f,%.5f", it.lat, it.lon) }

    fun decode(s: String): List<LatLon> = if (s.isBlank()) emptyList() else s.split(';').mapNotNull {
        val p = it.split(',')
        val a = p.getOrNull(0)?.toDoubleOrNull()
        val b = p.getOrNull(1)?.toDoubleOrNull()
        if (a == null || b == null) null else LatLon(a, b)
    }

    /** Keeps at most [max] points, always the first and last. */
    fun thin(points: List<LatLon>, max: Int = 400): List<LatLon> {
        if (points.size <= max) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return (0 until max).map { points[Math.round(it * step).toInt().coerceAtMost(points.size - 1)] }
    }
}

object GeoJson {
    private fun coords(p: LatLon) = JSONArray().put(p.lon).put(p.lat)

    fun line(points: List<LatLon>): String {
        val geom = JSONObject().put("type", "LineString").put("coordinates", JSONArray().also { a -> points.forEach { a.put(coords(it)) } })
        return feature(if (points.size >= 2) geom else null)
    }

    fun points(points: List<LatLon>): String {
        val geom = JSONObject().put("type", "MultiPoint").put("coordinates", JSONArray().also { a -> points.forEach { a.put(coords(it)) } })
        return feature(if (points.isNotEmpty()) geom else null)
    }

    private fun feature(geom: JSONObject?): String {
        val fc = JSONObject().put("type", "FeatureCollection")
        val arr = JSONArray()
        if (geom != null) arr.put(JSONObject().put("type", "Feature").put("properties", JSONObject()).put("geometry", geom))
        return fc.put("features", arr).toString()
    }
}

object TripMap {
    /** Map content for a trip: driven track, planned route, stops and extras that have a position. */
    fun forSession(s: Session, track: List<LatLon>, me: LatLon? = null): MapData {
        val planned = RouteLine.decode(s.route)
        val start = track.firstOrNull() ?: s.startLat?.let { a -> s.startLon?.let { LatLon(a, it) } }
        val end = s.destLat?.let { a -> s.destLon?.let { LatLon(a, it) } }
            ?: s.endLat?.let { a -> s.endLon?.let { LatLon(a, it) } }
        return MapData(
            driven = track,
            planned = planned,
            stops = s.pauses.mapNotNull { p -> p.lat?.let { a -> p.lon?.let { LatLon(a, it) } } },
            extras = s.extras.mapNotNull { e -> e.lat?.let { a -> e.lon?.let { LatLon(a, it) } } },
            start = start,
            end = end,
            me = me,
        )
    }
}

object RoutePlan {
    /**
     * Turns a truck route into plan steps following the EU rules:
     * a 45 min break after each 4h30 of driving, an 11 h rest (sleepover) after 9 h of driving in a day.
     */
    fun steps(
        km: Double,
        minutes: Int,
        newId: () -> Long,
        sleepoverEuros: Double = 0.0,
        blockMin: Int = 270,
        dayMin: Int = 540,
        breakMin: Int = 45,
        restMin: Int = 660,
    ): List<PlanStep> {
        if (minutes <= 0) return emptyList()
        val out = mutableListOf<PlanStep>()
        var left = minutes
        var day = 0
        var kmLeft = km
        while (left > 0) {
            val chunk = minOf(blockMin, left, dayMin - day)
            val chunkKm = if (left == chunk) kmLeft else km * chunk / minutes
            out += PlanStep(newId(), StepKind.DRIVE, minutes = chunk, km = Math.round(chunkKm * 10) / 10.0)
            kmLeft -= chunkKm
            left -= chunk
            day += chunk
            if (left > 0) {
                if (day >= dayMin) {
                    out += PlanStep(newId(), StepKind.PAUSE, minutes = restMin, pauseType = PauseType.REST)
                    out += PlanStep(newId(), StepKind.EXTRA, extraType = ExtraType.SLEEPOVER, euros = sleepoverEuros)
                    day = 0
                } else {
                    out += PlanStep(newId(), StepKind.PAUSE, minutes = breakMin, pauseType = PauseType.BREAK)
                }
            }
        }
        return out
    }
}

/** openrouteservice requests and replies (network-free parts). */
object OrsJson {
    fun routeRequest(from: LatLon, to: LatLon, truck: TruckSize, lang: String): String {
        val restrictions = JSONObject()
            .put("height", truck.height).put("width", truck.width).put("length", truck.length)
            .put("weight", truck.weight).put("axleload", truck.axleLoad).put("hazmat", truck.hazmat)
        return JSONObject()
            .put("coordinates", JSONArray().put(JSONArray().put(from.lon).put(from.lat)).put(JSONArray().put(to.lon).put(to.lat)))
            .put("units", "km")
            .put("language", lang)
            .put("instructions", false)
            .put("options", JSONObject().put("vehicle_type", "hgv").put("profile_params", JSONObject().put("restrictions", restrictions)))
            .toString()
    }

    fun parseRoute(text: String): TruckRoute {
        val f = JSONObject(text).getJSONArray("features").getJSONObject(0)
        val sum = f.getJSONObject("properties").getJSONObject("summary")
        val c = f.getJSONObject("geometry").getJSONArray("coordinates")
        val pts = (0 until c.length()).map { i -> c.getJSONArray(i).let { LatLon(it.getDouble(1), it.getDouble(0)) } }
        return TruckRoute(sum.optDouble("distance", 0.0), Math.round(sum.optDouble("duration", 0.0) / 60.0).toInt(), pts)
    }

    fun parsePlaces(text: String): List<Place> {
        val arr = JSONObject(text).optJSONArray("features") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val f = arr.getJSONObject(i)
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val label = f.optJSONObject("properties")?.optString("label").orEmpty()
            Place(label, c.getDouble(1), c.getDouble(0))
        }
    }

    fun errorMessage(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
            ?: runCatching { JSONObject(body).optString("error") }.getOrNull()
        return when (code) {
            401, 403 -> "key"
            429 -> "quota"
            else -> msg?.takeIf { it.isNotBlank() } ?: "HTTP $code"
        }
    }
}

/** Opens a destination in OsmAnd (truck profile), or any map app. */
object OsmAnd {
    fun navigateUri(dest: LatLon, name: String, from: LatLon? = null): String {
        val sb = StringBuilder("osmand.api://navigate?")
        if (from != null) sb.append(String.format(Locale.US, "start_lat=%.6f&start_lon=%.6f&", from.lat, from.lon))
        sb.append(String.format(Locale.US, "dest_lat=%.6f&dest_lon=%.6f", dest.lat, dest.lon))
        sb.append("&dest_name=").append(java.net.URLEncoder.encode(name.ifBlank { "Destino" }, "UTF-8"))
        sb.append("&profile=truck&force=true")
        return sb.toString()
    }

    fun geoUri(dest: LatLon, name: String): String =
        String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)", dest.lat, dest.lon, dest.lat, dest.lon, java.net.URLEncoder.encode(name.ifBlank { "Destino" }, "UTF-8").replace("+", "%20"))
}
