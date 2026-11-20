package com.qcenaman.francesinha.service

import com.qcenaman.francesinha.logic.tr
import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.qcenaman.francesinha.R
import com.qcenaman.francesinha.data.Config
import com.qcenaman.francesinha.data.Prefs
import com.qcenaman.francesinha.data.Store
import com.qcenaman.francesinha.logic.DetectorEvent
import com.qcenaman.francesinha.logic.Fix
import com.qcenaman.francesinha.logic.Fmt
import com.qcenaman.francesinha.logic.Phase
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.logic.TripDetector
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.ui.MainActivity

class TrackingService : Service(), LocationListener {

    companion object {
        private const val CH_TRACK = "tracking"
        private const val CH_ALERT = "alerts"
        private const val NOTIF_ID = 1
        private const val ALERT_ID = 2
        const val EXTRA_MANUAL_END = "manualEnd"

        fun hasLocation(ctx: Context) = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        fun shouldRun(ctx: Context): Boolean =
            hasLocation(ctx) && (Prefs.load(ctx).autoDetect || Store.openSession() != null)

        /** Start, resync or stop the service to match settings and stored state. */
        fun refresh(ctx: Context, manualEnd: Boolean = false) {
            Store.init(ctx)
            val i = Intent(ctx, TrackingService::class.java).putExtra(EXTRA_MANUAL_END, manualEnd)
            if (shouldRun(ctx)) {
                try {
                    ContextCompat.startForegroundService(ctx, i)
                } catch (_: Exception) {
                }
            } else {
                ctx.stopService(i)
            }
        }
    }

    private val detector = TripDetector()
    private lateinit var lm: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private var cfg = Config()
    private var pendingMeters = 0.0
    private var suppressAutoStartUntil = 0L
    private var lastAlertKey: Long? = null
    private var lastFixAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            handle(detector.onTick(System.currentTimeMillis()))
            flushDistance()
            updateNotification()
            checkAlert()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        com.qcenaman.francesinha.logic.I18n.lang = Prefs.lang(this)
        Store.init(this)
        com.qcenaman.francesinha.data.TrackStore.init(this)
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!shouldRun(this)) {
            stopNow()
            return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra(EXTRA_MANUAL_END, false) == true) {
            suppressAutoStartUntil = System.currentTimeMillis() + 10 * 60_000L
        }
        cfg = Prefs.load(this)
        detector.config = cfg.detector()
        syncDetector()
        startListening()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun goForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        true
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (listening || !hasLocation(this)) return
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000L, 0f, this, Looper.getMainLooper())
            listening = true
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun stopNow() {
        flushDistance()
        handler.removeCallbacks(tick)
        if (listening) {
            lm.removeUpdates(this)
            listening = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        flushDistance()
        handler.removeCallbacks(tick)
        if (listening) lm.removeUpdates(this)
        listening = false
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        lastFixAt = System.currentTimeMillis()
        val acc = if (location.hasAccuracy()) location.accuracy else 0f
        if (acc <= 50f) {
            com.qcenaman.francesinha.data.LastFix.set(location.latitude, location.longitude, lastFixAt)
            recordTrack(location.latitude, location.longitude, lastFixAt)
        }
        val fix = Fix(
            time = lastFixAt,
            lat = location.latitude,
            lon = location.longitude,
            speedMps = if (location.hasSpeed()) location.speed else null,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
        )
        handle(detector.onFix(fix))
    }

    private var lastTrack: com.qcenaman.francesinha.logic.LatLon? = null
    private var lastTrackSession = 0L

    /** Saves a track point every ~40 m while a trip is running. */
    private fun recordTrack(lat: Double, lon: Double, t: Long) {
        val s = Store.openSession() ?: return
        val prev = lastTrack
        if (s.id != lastTrackSession || prev == null ||
            com.qcenaman.francesinha.logic.Geo.distanceMeters(prev.lat, prev.lon, lat, lon) >= 40
        ) {
            com.qcenaman.francesinha.data.TrackStore.append(s.id, t, lat, lon)
            lastTrack = com.qcenaman.francesinha.logic.LatLon(lat, lon)
            lastTrackSession = s.id
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) { updateNotification() }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun syncDetector() {
        val s = Store.openSession()
        if (s == null) {
            detector.sync(Phase.IDLE, null)
        } else {
            val p = s.openPause
            if (p == null) detector.sync(Phase.DRIVING, null) else detector.sync(Phase.STOPPED, p.start, p.auto)
        }
    }

    private fun flushDistance() {
        if (pendingMeters > 0) {
            Store.addDistance(pendingMeters)
            pendingMeters = 0.0
        }
    }

    private fun handle(events: List<DetectorEvent>) {
        if (events.isEmpty()) return
        val auto = cfg.autoDetect
        var changed = false
        for (e in events) {
            when (e) {
                is DetectorEvent.Distance -> {
                    if (Store.openSession() != null) pendingMeters += e.meters
                    if (pendingMeters >= 250) flushDistance()
                }
                is DetectorEvent.SessionStarted -> if (auto && Store.openSession() == null &&
                    System.currentTimeMillis() >= suppressAutoStartUntil
                ) {
                    pendingMeters = 0.0
                    Store.startSession(e.time, true, e.lat, e.lon) { Prefs.newSessionDefaults(this, it) }
                    changed = true
                }
                is DetectorEvent.PauseStarted -> if (auto) {
                    flushDistance()
                    Store.startPause(e.time, PauseType.BREAK, auto = true)
                    changed = true
                }
                is DetectorEvent.PauseEnded -> if (auto) {
                    Store.endPause(e.time)
                    changed = true
                }
                is DetectorEvent.SessionEnded -> if (auto) {
                    flushDistance()
                    Store.endSession(e.time, e.lat, e.lon, trimAutoPause = true)
                    changed = true
                }
            }
        }
        syncDetector()
        if (changed) {
            updateNotification()
            if (!shouldRun(this)) stopNow()
        }
        checkAlert()
    }

    private var lastLimitKey: String? = null

    private fun checkLimits() {
        if (!Prefs.limitAlerts(this) || Store.openSession() == null) return
        val now = System.currentTimeMillis()
        val st = com.qcenaman.francesinha.logic.Limits.status(Store.sessions.value, now)
        val msg = when {
            st.todayMs >= com.qcenaman.francesinha.logic.Limits.DAILY_EXTENDED_MS -> tr("You've reached 10h of driving today.")
            st.todayMs >= com.qcenaman.francesinha.logic.Limits.DAILY_MS -> tr("You've reached 9h of driving today.")
            st.weekMs >= com.qcenaman.francesinha.logic.Limits.WEEKLY_MS -> tr("You've reached 56h of driving this week.")
            st.fortnightMs >= com.qcenaman.francesinha.logic.Limits.FORTNIGHT_MS -> tr("You've reached 90h of driving in two weeks.")
            else -> null
        } ?: return
        val key = Fmt.isoDate(now) + msg
        if (key == lastLimitKey) return
        lastLimitKey = key
        notify(
            ALERT_ID + 1,
            NotificationCompat.Builder(this, CH_ALERT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(tr("Driving limits (EU)"))
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun checkAlert() {
        checkLimits()
        if (!cfg.alertsOn) return
        val s = Store.openSession() ?: return
        val now = System.currentTimeMillis()
        val cont = Stats.continuousDrivingMs(s, now)
        if (cont < cfg.driveAlertMin * 60_000L) return
        val key = s.id * 31 + (s.pauses.maxOfOrNull { it.end ?: 0L } ?: 0L)
        if (key == lastAlertKey) return
        lastAlertKey = key
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Time for a break"))
            .setContentText(tr("You've been driving %s without a proper break.", Fmt.duration(cont)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notify(ALERT_ID, n)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun statusText(): Pair<String, String> {
        val s = Store.openSession()
        val now = System.currentTimeMillis()
        val gpsOff = try { !lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
        if (gpsOff) return tr("GPS is off") to tr("Turn on Location so trips can be recorded")
        if (s == null) return tr("Waiting for driving") to tr("A trip starts automatically when you drive")
        val km = s.km() + pendingMeters / 1000.0
        val p = s.openPause
        return if (p != null) {
            tr("%s since %s", tr(p.type.label), Fmt.time(p.start)) to tr("Driving %s · %s", Fmt.duration(Stats.drivingMs(s, now)), Fmt.km(km))
        } else {
            tr("Driving · %s", Fmt.duration(Stats.drivingMs(s, now))) to tr("%s · started %s", Fmt.km(km), Fmt.time(s.start))
        }
    }

    private fun buildNotification(): Notification {
        val (title, text) = statusText()
        return NotificationCompat.Builder(this, CH_TRACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun updateNotification() = notify(NOTIF_ID, buildNotification())

    private fun notify(id: Int, n: Notification) {
        try {
            getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: Exception) {
        }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_TRACK, tr("Trip tracking"), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, tr("Driving time alerts"), NotificationManager.IMPORTANCE_HIGH),
        )
    }
}
