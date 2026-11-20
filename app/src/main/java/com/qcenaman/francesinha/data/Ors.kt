package com.qcenaman.francesinha.data

import com.qcenaman.francesinha.logic.LatLon
import com.qcenaman.francesinha.logic.OrsJson
import com.qcenaman.francesinha.logic.Place
import com.qcenaman.francesinha.logic.TruckRoute
import com.qcenaman.francesinha.logic.TruckSize
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** openrouteservice (HeiGIT) client: place search and truck (driving-hgv) routes. Call off the main thread. */
object Ors {
    private const val BASE = "https://api.heigit.org"

    class OrsError(val reason: String) : Exception(reason)

    private fun call(url: String, key: String, body: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("Authorization", key)
            c.setRequestProperty("Accept", "application/json, application/geo+json")
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw OrsError(OrsJson.errorMessage(code, text))
            return text
        } catch (e: OrsError) {
            throw e
        } catch (e: Exception) {
            throw OrsError("network")
        } finally {
            c.disconnect()
        }
    }

    fun search(key: String, text: String, lang: String): List<Place> {
        val q = URLEncoder.encode(text, "UTF-8")
        val k = URLEncoder.encode(key, "UTF-8")
        return OrsJson.parsePlaces(call("$BASE/pelias/v1/search?text=$q&size=6&lang=$lang&api_key=$k", key))
    }

    fun route(key: String, from: LatLon, to: LatLon, truck: TruckSize, lang: String): TruckRoute =
        OrsJson.parseRoute(
            call("$BASE/openrouteservice/v2/directions/driving-hgv/geojson", key, OrsJson.routeRequest(from, to, truck, lang)),
        )
}
