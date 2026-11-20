package com.qcenaman.francesinha

import com.qcenaman.francesinha.data.Codec
import com.qcenaman.francesinha.logic.Limits
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.logic.TaxCalc
import com.qcenaman.francesinha.model.CustomPreset
import com.qcenaman.francesinha.model.Extra
import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.Pause
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session
import com.qcenaman.francesinha.model.Tax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RulesTest {
    private val h = 3_600_000L

    private fun at(y: Int, m: Int, d: Int, hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, hour, 0, 0)
    }.timeInMillis

    private val tenHours = Session(id = 1, start = 0, end = 10 * h, hourlyRate = 10.0)

    @Test
    fun customFixedAndPercentItems() {
        val s = tenHours.copy(
            extras = listOf(
                Extra(1, ExtraType.CUSTOM, 0, name = "Bonus A", percent = 10.0),
                Extra(2, ExtraType.CUSTOM, 0, name = "Fee", percent = 5.0, deduct = true),
                Extra(3, ExtraType.CUSTOM, 0, 7.0, name = "Fixed"),
                Extra(4, ExtraType.CUSTOM, 0, 3.0, name = "Fixed off", deduct = true),
            ),
        )
        assertEquals(100.0 + 10.0 - 5.0 + 7.0 - 3.0, Stats.totalPay(s, 0), 0.001)
        assertEquals(9.0, Stats.adjustEuros(s, 0), 0.001)
    }

    @Test
    fun expensesOnlyCountWhenRefunded() {
        val s = tenHours.copy(
            extras = listOf(
                Extra(1, ExtraType.EXPENSE, 0, 20.0, name = "Toll", refunded = true),
                Extra(2, ExtraType.EXPENSE, 0, 15.0, name = "Parking"),
            ),
        )
        assertEquals(120.0, Stats.totalPay(s, 0), 0.001)
        assertEquals(35.0, Stats.expenseEuros(s), 0.001)
        assertEquals(20.0, Stats.refundedEuros(s), 0.001)
        assertEquals(100.0, Stats.taxable(s, 0, true), 0.001)
    }

    @Test
    fun taxesAndNet() {
        val s = tenHours.copy(extras = listOf(Extra(1, ExtraType.LUNCH, 0, 9.0)))
        val taxes = listOf(Tax("IRS", 10.0), Tax("SS", 11.0))
        val taxable = Stats.taxable(s, 0, mealsExempt = true)
        assertEquals(100.0, taxable, 0.001)
        assertEquals(21.0, TaxCalc.amount(taxable, taxes), 0.001)
        assertEquals(109.0 - 21.0, TaxCalc.net(Stats.totalPay(s, 0), taxable, taxes), 0.001)
        assertEquals(109.0, Stats.taxable(s, 0, mealsExempt = false), 0.001)
    }

    @Test
    fun perKmPay() {
        val s = tenHours.copy(manualKm = 500.0, perKmRate = 0.05)
        assertEquals(25.0, Stats.kmPay(s), 0.001)
        assertEquals(125.0, Stats.totalPay(s, 0), 0.001)
    }

    @Test
    fun nightSupplement() {
        // Thursday 2026-11-19 20:00 -> Friday 02:00, night from 22 to 7 = 4h
        val s = Session(id = 1, start = at(2026, 11, 19, 20), end = at(2026, 11, 20, 2), hourlyRate = 10.0, nightPct = 25.0)
        assertEquals(4 * h, Stats.nightMs(s, 0))
        assertEquals(10.0, Stats.supplementPay(s, 0), 0.001)
        // unpaid break in the night is not counted
        val withBreak = s.copy(pauses = listOf(Pause(9, PauseType.BREAK, at(2026, 11, 19, 23), at(2026, 11, 20, 0))))
        assertEquals(3 * h, Stats.nightMs(withBreak, 0))
    }

    @Test
    fun weekendAndHolidaySupplements() {
        // Saturday 2026-11-21 08:00-12:00
        val sat = Session(id = 1, start = at(2026, 11, 21, 8), end = at(2026, 11, 21, 12), hourlyRate = 10.0, weekendPct = 50.0)
        assertEquals(4 * h, Stats.weekendMs(sat, 0))
        assertEquals(20.0, Stats.supplementPay(sat, 0), 0.001)
        val hol = Session(id = 2, start = at(2026, 12, 8, 8), end = at(2026, 12, 8, 12), hourlyRate = 10.0, holidayPct = 100.0, holiday = true)
        assertEquals(40.0, Stats.supplementPay(hol, 0), 0.001)
        assertEquals(80.0, Stats.totalPay(hol, 0), 0.001)
    }

    @Test
    fun drivingLimits() {
        val start = at(2026, 11, 19, 6)
        val s = Session(id = 1, start = start, end = start + 10 * h, pauses = listOf(Pause(2, PauseType.BREAK, start + 4 * h, start + 5 * h)))
        val st = Limits.status(listOf(s), start + 11 * h)
        assertEquals(9 * h, st.todayMs)
        assertFalse(st.dailyOver)
        val long = s.copy(end = start + 11 * h)
        assertTrue(Limits.status(listOf(long), start + 12 * h).dailyOver)
    }

    @Test
    fun presetsTaxesAndNewFieldsRoundTrip() {
        val presets = listOf(CustomPreset("Bónus", 0.0, 5.0, false), CustomPreset("Quota", 12.5, null, true))
        assertEquals(presets, Codec.decodePresets(Codec.encodePresets(presets)))
        val taxes = listOf(Tax("IRS", 12.0), Tax("Segurança Social", 11.0))
        assertEquals(taxes, Codec.decodeTaxes(Codec.encodeTaxes(taxes)))
        val s = tenHours.copy(
            perKmRate = 0.1, nightPct = 25.0, weekendPct = 50.0, holidayPct = 100.0, holiday = true,
            nightFromHour = 21, nightToHour = 6, client = "Transportes Silva", photos = listOf("r1.jpg"),
            extras = listOf(Extra(1, ExtraType.CUSTOM, 0, 0.0, "n", "Bónus", 5.0, true, false), Extra(2, ExtraType.EXPENSE, 0, 4.5, name = "Toll", refunded = true)),
        )
        assertEquals(listOf(s), Codec.decode(Codec.encode(listOf(s))))
    }
}

class MapRouteTest {
    @Test
    fun routePlanFollowsEuRules() {
        var id = 0L
        // 14h of driving, 1200 km
        val steps = com.qcenaman.francesinha.logic.RoutePlan.steps(1200.0, 14 * 60, { ++id }, 30.0)
        val kinds = steps.map { it.kind.name + (if (it.kind.name == "PAUSE") ":" + it.pauseType.name else "") }
        org.junit.Assert.assertEquals(
            listOf("DRIVE", "PAUSE:BREAK", "DRIVE", "PAUSE:REST", "EXTRA", "DRIVE", "PAUSE:BREAK", "DRIVE"),
            kinds,
        )
        org.junit.Assert.assertEquals(14 * 60, steps.filter { it.kind.name == "DRIVE" }.sumOf { it.minutes })
        org.junit.Assert.assertEquals(1200.0, steps.sumOf { it.km }, 0.5)
        org.junit.Assert.assertEquals(30.0, steps.first { it.kind.name == "EXTRA" }.euros, 0.001)
        org.junit.Assert.assertTrue(com.qcenaman.francesinha.logic.RoutePlan.steps(0.0, 0, { ++id }).isEmpty())
    }

    @Test
    fun routeLineEncodeThin() {
        val pts = (0 until 1000).map { com.qcenaman.francesinha.logic.LatLon(41.0 + it * 0.001, -8.6) }
        val thin = com.qcenaman.francesinha.logic.RouteLine.thin(pts, 100)
        org.junit.Assert.assertEquals(100, thin.size)
        org.junit.Assert.assertEquals(pts.first(), thin.first())
        org.junit.Assert.assertEquals(pts.last(), thin.last())
        val back = com.qcenaman.francesinha.logic.RouteLine.decode(com.qcenaman.francesinha.logic.RouteLine.encode(thin))
        org.junit.Assert.assertEquals(100, back.size)
        org.junit.Assert.assertEquals(thin.last().lat, back.last().lat, 1e-5)
    }

    @Test
    fun orsJson() {
        val req = org.json.JSONObject(
            com.qcenaman.francesinha.logic.OrsJson.routeRequest(
                com.qcenaman.francesinha.logic.LatLon(41.15, -8.61), com.qcenaman.francesinha.logic.LatLon(40.41, -3.70),
                com.qcenaman.francesinha.logic.TruckSize(height = 4.1), "pt",
            ),
        )
        org.junit.Assert.assertEquals(-8.61, req.getJSONArray("coordinates").getJSONArray(0).getDouble(0), 1e-9)
        org.junit.Assert.assertEquals(4.1, req.getJSONObject("options").getJSONObject("profile_params").getJSONObject("restrictions").getDouble("height"), 1e-9)
        val reply = """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"summary":{"distance":562.3,"duration":27000}},"geometry":{"type":"LineString","coordinates":[[-8.61,41.15],[-3.70,40.41]]}}]}"""
        val r = com.qcenaman.francesinha.logic.OrsJson.parseRoute(reply)
        org.junit.Assert.assertEquals(562.3, r.km, 1e-9)
        org.junit.Assert.assertEquals(450, r.minutes)
        org.junit.Assert.assertEquals(41.15, r.points[0].lat, 1e-9)
        val places = com.qcenaman.francesinha.logic.OrsJson.parsePlaces(
            """{"features":[{"geometry":{"coordinates":[-8.61,41.15]},"properties":{"label":"Porto, Portugal"}}]}""",
        )
        org.junit.Assert.assertEquals("Porto, Portugal", places[0].name)
        org.junit.Assert.assertEquals("key", com.qcenaman.francesinha.logic.OrsJson.errorMessage(403, ""))
    }

    @Test
    fun osmAndUriAndGeoJson() {
        val u = com.qcenaman.francesinha.logic.OsmAnd.navigateUri(com.qcenaman.francesinha.logic.LatLon(40.4, -3.7), "Madrid")
        org.junit.Assert.assertTrue(u.startsWith("osmand.api://navigate?"))
        org.junit.Assert.assertTrue(u.contains("dest_lat=40.400000") && u.contains("profile=truck"))
        val g = org.json.JSONObject(com.qcenaman.francesinha.logic.GeoJson.line(listOf(com.qcenaman.francesinha.logic.LatLon(1.0, 2.0))))
        org.junit.Assert.assertEquals(0, g.getJSONArray("features").length())
    }

    @Test
    fun newMapFieldsRoundTrip() {
        val s = com.qcenaman.francesinha.model.Session(
            id = 1, start = 0, end = 5, destName = "Madrid", destLat = 40.4, destLon = -3.7, route = "41.1,-8.6;40.4,-3.7",
            pauses = listOf(com.qcenaman.francesinha.model.Pause(2, com.qcenaman.francesinha.model.PauseType.BREAK, 1, 2, lat = 41.0, lon = -8.0)),
            extras = listOf(com.qcenaman.francesinha.model.Extra(3, com.qcenaman.francesinha.model.ExtraType.LUNCH, 1, 9.0, lat = 41.0, lon = -8.0)),
        )
        org.junit.Assert.assertEquals(listOf(s), com.qcenaman.francesinha.data.Codec.decode(com.qcenaman.francesinha.data.Codec.encode(listOf(s))))
        val p = com.qcenaman.francesinha.model.Plan(
            id = 9, start = 0, fromName = "Porto", fromLat = 41.1, fromLon = -8.6, toName = "Madrid", toLat = 40.4, toLon = -3.7,
            routeKm = 562.3, routeMin = 450, route = "41.1,-8.6;40.4,-3.7",
        )
        org.junit.Assert.assertEquals(listOf(p), com.qcenaman.francesinha.data.Codec.decodePlans(com.qcenaman.francesinha.data.Codec.encodePlans(listOf(p))))
        val trip = com.qcenaman.francesinha.logic.PlanCalc.toTrip(p, 10)
        org.junit.Assert.assertEquals("Madrid", trip.destName)
        org.junit.Assert.assertEquals(p.route, trip.route)
    }
}
