package com.qcenaman.francesinha.data

import android.content.Context
import com.qcenaman.francesinha.logic.DetectorConfig
import com.qcenaman.francesinha.model.ExtraType

data class Config(
    val autoDetect: Boolean = true,
    val startSpeedKmh: Int = 12,
    val breakAfterMin: Int = 5,
    val endAfterMin: Int = 120,
    val alertsOn: Boolean = true,
    val driveAlertMin: Int = 270,
) {
    fun detector() = DetectorConfig(
        startSpeedKmh = startSpeedKmh.toDouble(),
        breakAfterMs = breakAfterMin * 60_000L,
        endAfterMs = endAfterMin * 60_000L,
    )
}

object Prefs {
    private const val NAME = "settings"

    fun load(ctx: Context): Config {
        val p = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val d = Config()
        return Config(
            autoDetect = p.getBoolean("autoDetect", d.autoDetect),
            startSpeedKmh = p.getInt("startSpeedKmh", d.startSpeedKmh),
            breakAfterMin = p.getInt("breakAfterMin", d.breakAfterMin),
            endAfterMin = p.getInt("endAfterMin", d.endAfterMin),
            alertsOn = p.getBoolean("alertsOn", d.alertsOn),
            driveAlertMin = p.getInt("driveAlertMin", d.driveAlertMin),
        )
    }

    /** Default pay per hour for new trips (null = not set). */
    fun rate(ctx: Context): Double? {
        val v = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("rate", 0f).toDouble()
        return if (v > 0) v else null
    }

    fun setRate(ctx: Context, v: Double?) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putFloat("rate", (v ?: 0.0).toFloat()).apply()
    }

    /** Monthly base salary; everything earned on trips is added on top. */
    fun baseSalary(ctx: Context): Double? {
        val v = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("baseSalary", 0f).toDouble()
        return if (v > 0) v else null
    }

    fun setBaseSalary(ctx: Context, v: Double?) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putFloat("baseSalary", (v ?: 0.0).toFloat()).apply()
    }

    /** Final payslip amount for a month (key yyyy-MM), to compare with what was expected. */
    fun payslip(ctx: Context, month: String): Double? {
        val p = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return if (p.contains("payslip_$month")) p.getFloat("payslip_$month", 0f).toDouble() else null
    }

    fun setPayslip(ctx: Context, month: String, v: Double?) {
        val e = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
        if (v == null) e.remove("payslip_$month") else e.putFloat("payslip_$month", v.toFloat())
        e.apply()
    }

    /** Default overtime: after this many hours (0 = off) and at this rate (0 = same as normal). */
    fun overtimeAfterMin(ctx: Context): Int? {
        val v = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("otAfterMin", 0)
        return if (v > 0) v else null
    }

    fun setOvertimeAfterMin(ctx: Context, v: Int?) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putInt("otAfterMin", v ?: 0).apply()
    }

    fun overtimeRate(ctx: Context): Double? {
        val v = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("otRate", 0f).toDouble()
        return if (v > 0) v else null
    }

    fun setOvertimeRate(ctx: Context, v: Double?) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putFloat("otRate", (v ?: 0.0).toFloat()).apply()
    }

    fun orsKey(ctx: Context): String = sp(ctx).getString("orsKey", "")?.trim() ?: ""
    fun setOrsKey(ctx: Context, v: String) = sp(ctx).edit().putString("orsKey", v.trim()).apply()

    fun truck(ctx: Context): com.qcenaman.francesinha.logic.TruckSize {
        val d = com.qcenaman.francesinha.logic.TruckSize()
        return com.qcenaman.francesinha.logic.TruckSize(
            height = num(ctx, "truckHeight") ?: d.height,
            width = num(ctx, "truckWidth") ?: d.width,
            length = num(ctx, "truckLength") ?: d.length,
            weight = num(ctx, "truckWeight") ?: d.weight,
            axleLoad = num(ctx, "truckAxle") ?: d.axleLoad,
            hazmat = sp(ctx).getBoolean("truckHazmat", false),
        )
    }

    fun setHazmat(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("truckHazmat", v).apply()

    /** A new trip with the default pay settings filled in. */
    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Optional money setting: 0 / missing means "not set". */
    fun num(ctx: Context, key: String): Double? = sp(ctx).getFloat(key, 0f).toDouble().takeIf { it > 0 }

    fun setNum(ctx: Context, key: String, v: Double?) {
        sp(ctx).edit().putFloat(key, (v ?: 0.0).toFloat()).apply()
    }

    fun nightFromHour(ctx: Context): Int = sp(ctx).getInt("nightFrom", 22)
    fun nightToHour(ctx: Context): Int = sp(ctx).getInt("nightTo", 7)
    fun setNightHours(ctx: Context, from: Int, to: Int) {
        sp(ctx).edit().putInt("nightFrom", from.coerceIn(0, 23)).putInt("nightTo", to.coerceIn(0, 23)).apply()
    }

    /** Meals and sleepovers left out of tax (Portuguese allowances usually are). */
    fun mealsTaxFree(ctx: Context): Boolean = sp(ctx).getBoolean("mealsTaxFree", true)
    fun setMealsTaxFree(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("mealsTaxFree", v).apply()

    val defaultTaxes = listOf(
        com.qcenaman.francesinha.model.Tax("IRS", 0.0),
        com.qcenaman.francesinha.model.Tax("Segurança Social", 11.0),
    )

    fun taxes(ctx: Context): List<com.qcenaman.francesinha.model.Tax> =
        sp(ctx).getString("taxes", null)?.let { runCatching { Codec.decodeTaxes(it) }.getOrNull() } ?: defaultTaxes

    fun setTaxes(ctx: Context, list: List<com.qcenaman.francesinha.model.Tax>) =
        sp(ctx).edit().putString("taxes", Codec.encodeTaxes(list)).apply()

    fun presets(ctx: Context): List<com.qcenaman.francesinha.model.CustomPreset> =
        sp(ctx).getString("presets", null)?.let { runCatching { Codec.decodePresets(it) }.getOrNull() } ?: emptyList()

    fun setPresets(ctx: Context, list: List<com.qcenaman.francesinha.model.CustomPreset>) =
        sp(ctx).edit().putString("presets", Codec.encodePresets(list)).apply()

    fun limitAlerts(ctx: Context): Boolean = sp(ctx).getBoolean("limitAlerts", true)
    fun setLimitAlerts(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("limitAlerts", v).apply()

    fun lastClient(ctx: Context): String = sp(ctx).getString("lastClient", "") ?: ""
    fun setLastClient(ctx: Context, v: String) = sp(ctx).edit().putString("lastClient", v).apply()

    /** A new trip with the default pay settings filled in. */
    fun newSessionDefaults(ctx: Context, s: com.qcenaman.francesinha.model.Session) = s.copy(
        hourlyRate = s.hourlyRate ?: rate(ctx),
        overtimeAfterMin = s.overtimeAfterMin ?: overtimeAfterMin(ctx),
        overtimeRate = s.overtimeRate ?: overtimeRate(ctx),
        perKmRate = s.perKmRate ?: num(ctx, "perKm"),
        nightPct = s.nightPct ?: num(ctx, "nightPct"),
        weekendPct = s.weekendPct ?: num(ctx, "weekendPct"),
        holidayPct = s.holidayPct ?: num(ctx, "holidayPct"),
        nightFromHour = nightFromHour(ctx),
        nightToHour = nightToHour(ctx),
        client = s.client.ifBlank { lastClient(ctx) },
    )

    /** Every setting, for backups. */
    fun exportAll(ctx: Context): org.json.JSONObject {
        val o = org.json.JSONObject()
        for ((k, v) in sp(ctx).all) {
            val e = when (v) {
                is Boolean -> org.json.JSONObject().put("t", "b").put("v", v)
                is Int -> org.json.JSONObject().put("t", "i").put("v", v)
                is Long -> org.json.JSONObject().put("t", "l").put("v", v)
                is Float -> org.json.JSONObject().put("t", "f").put("v", v.toDouble())
                is String -> org.json.JSONObject().put("t", "s").put("v", v)
                else -> null
            }
            if (e != null) o.put(k, e)
        }
        return o
    }

    fun importAll(ctx: Context, o: org.json.JSONObject) {
        val ed = sp(ctx).edit().clear()
        for (k in o.keys()) {
            val e = o.getJSONObject(k)
            when (e.getString("t")) {
                "b" -> ed.putBoolean(k, e.getBoolean("v"))
                "i" -> ed.putInt(k, e.getInt("v"))
                "l" -> ed.putLong(k, e.getLong("v"))
                "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                "s" -> ed.putString(k, e.getString("v"))
            }
        }
        ed.commit()
    }

    fun lang(ctx: Context): com.qcenaman.francesinha.logic.Lang =
        com.qcenaman.francesinha.logic.Lang.from(ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("lang", null))

    fun setLang(ctx: Context, l: com.qcenaman.francesinha.logic.Lang) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString("lang", l.code).apply()
        com.qcenaman.francesinha.logic.I18n.lang = l
    }

    fun euros(ctx: Context, t: ExtraType): Double =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat("eur_" + t.name, 0f).toDouble()

    fun setEuros(ctx: Context, t: ExtraType, v: Double) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putFloat("eur_" + t.name, v.toFloat()).apply()
    }

    fun save(ctx: Context, c: Config) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("autoDetect", c.autoDetect)
            .putInt("startSpeedKmh", c.startSpeedKmh)
            .putInt("breakAfterMin", c.breakAfterMin)
            .putInt("endAfterMin", c.endAfterMin)
            .putBoolean("alertsOn", c.alertsOn)
            .putInt("driveAlertMin", c.driveAlertMin)
            .apply()
    }
}
