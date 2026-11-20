package com.qcenaman.francesinha.data

import android.content.Context
import com.qcenaman.francesinha.logic.LatLon
import java.io.File

/** Last GPS position seen by the tracking service (used to place stops and extras on the map). */
object LastFix {
    @Volatile var lat: Double? = null
    @Volatile var lon: Double? = null
    @Volatile var time: Long = 0

    fun set(la: Double, lo: Double, t: Long) {
        lat = la; lon = lo; time = t
    }

    /** Only trust a position from the last 10 minutes. */
    fun fresh(now: Long = System.currentTimeMillis()): LatLon? {
        val a = lat ?: return null
        val b = lon ?: return null
        return if (now - time < 10 * 60_000L) LatLon(a, b) else null
    }
}

/** Driven GPS track of each trip, one small text file per trip ("time,lat,lon" lines). */
object TrackStore {
    private var dir: File? = null

    @Synchronized
    fun init(ctx: Context) {
        if (dir == null) dir = File(ctx.applicationContext.filesDir, "tracks").apply { mkdirs() }
    }

    fun folder(ctx: Context): File { init(ctx); return dir!! }
    private fun file(id: Long): File? = dir?.let { File(it, "$id.csv") }

    @Synchronized
    fun append(id: Long, t: Long, lat: Double, lon: Double) {
        val f = file(id) ?: return
        runCatching { f.appendText(String.format(java.util.Locale.US, "%d,%.6f,%.6f\n", t, lat, lon)) }
    }

    fun load(id: Long): List<LatLon> {
        val f = file(id) ?: return emptyList()
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { line ->
                val p = line.split(',')
                if (p.size < 3) null else {
                    val la = p[1].toDoubleOrNull()
                    val lo = p[2].toDoubleOrNull()
                    if (la == null || lo == null) null else LatLon(la, lo)
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun merge(keep: Long, gone: Long) {
        val a = file(keep) ?: return
        val b = file(gone) ?: return
        if (b.exists()) {
            runCatching { a.appendText(b.readText()); b.delete() }
        }
    }

    @Synchronized
    fun delete(id: Long) {
        file(id)?.delete()
    }
}
