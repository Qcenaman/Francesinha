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
