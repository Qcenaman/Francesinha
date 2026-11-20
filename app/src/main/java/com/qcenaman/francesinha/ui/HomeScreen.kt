package com.qcenaman.francesinha.ui

import com.qcenaman.francesinha.logic.tr
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qcenaman.francesinha.data.Prefs
import com.qcenaman.francesinha.data.Store
import com.qcenaman.francesinha.logic.Fmt
import com.qcenaman.francesinha.logic.Limits
import com.qcenaman.francesinha.logic.Period
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session
import com.qcenaman.francesinha.service.TrackingService

private fun granted(ctx: Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun HomeScreen(modifier: Modifier, onEdit: (Long) -> Unit, onMap: (Long) -> Unit = {}) {
    val ctx = LocalContext.current
    val sessions by Store.sessions.collectAsState()
    val now = rememberNow()
    val open = sessions.firstOrNull { it.end == null }
    val (from, to) = Period.TODAY.range(now)
    val today = Stats.totals(Stats.inRange(sessions, from, to), now)
    val (wFrom, wTo) = Period.WEEK.range(now)
    val week = Stats.totals(Stats.inRange(sessions, wFrom, wTo), now)
    var auto by remember { mutableStateOf(Prefs.load(ctx).autoDetect) }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PermissionsCard() }
        item { StatusCard(open, now, onMap) }
        item { LimitsCard(sessions, now) }
        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Automatic trips"), fontWeight = FontWeight.Bold)
                        Text(
                            if (auto) tr("Trips and breaks are detected by GPS") else tr("Off: start and stop trips by hand"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = auto, onCheckedChange = {
                        auto = it
                        Prefs.save(ctx, Prefs.load(ctx).copy(autoDetect = it))
                        TrackingService.refresh(ctx)
                    })
                }
            }
        }
        item { ExtrasQuickCard(hasTrips = sessions.isNotEmpty(), open = open != null) }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Today"), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        tr("Driving") to Fmt.duration(today.drivingMs),
                        tr("Breaks") to Fmt.duration(today.pauseMs),
                        tr("Distance") to Fmt.km(today.km),
                    )
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        tr("Meals") to today.meals.toString(),
                        tr("Sleepovers") to today.sleepovers.toString(),
                        tr("Pay") to Fmt.eur(today.euros),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(tr("This week"), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        tr("Driving") to Fmt.duration(week.drivingMs),
                        tr("On duty") to Fmt.duration(week.totalMs),
                        tr("Distance") to Fmt.km(week.km),
                    )
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        tr("Meals") to week.meals.toString(),
                        tr("Sleepovers") to week.sleepovers.toString(),
                        tr("Pay") to Fmt.eur(week.euros),
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Trips"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = { onEdit(0L) }) { Text(tr("+ Add trip by hand")) }
            }
        }
        if (sessions.isEmpty()) {
            item {
                Text(
                    tr("No trips yet. Start driving with automatic trips on, or tap Start trip."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(sessions, key = { it.id }) { s -> SessionRow(s, now) { onEdit(s.id) } }
    }
}

@Composable
private fun StatusCard(open: Session?, now: Long, onMap: (Long) -> Unit) {
    val ctx = LocalContext.current
    var typeMenu by remember { mutableStateOf(false) }
    val pause = open?.openPause
    val color = when {
        open == null -> MaterialTheme.colorScheme.surfaceVariant
        pause != null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            when {
                open == null -> {
                    Text(tr("Not on a trip"), style = MaterialTheme.typography.headlineSmall)
                    Text(tr("Driving will start a trip automatically, or start one now."))
                }
                pause != null -> {
                    Text(tr("%s since %s", tr(pause.type.label), Fmt.time(pause.start)), style = MaterialTheme.typography.headlineSmall)
                    Text(tr("%s so far", Fmt.duration(now - pause.start)))
                }
                else -> {
                    Text(tr("Driving"), style = MaterialTheme.typography.headlineSmall)
                    Text(tr("Since last break: %s", Fmt.duration(Stats.continuousDrivingMs(open, now))))
                }
            }
            if (open != null) {
                Spacer(Modifier.height(8.dp))
                StatRow(
                    tr("Started") to Fmt.time(open.start),
                    tr("Driving") to Fmt.duration(Stats.drivingMs(open, now)),
                    tr("Distance") to Fmt.km(open.km()),
                )
                if (open.hourlyRate != null || open.extras.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("Earned so far: %s", Fmt.eur(Stats.totalPay(open, now))) +
                            (open.hourlyRate?.let { " (${Fmt.eur(it)}/h)" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMap(open.id) }, Modifier.weight(1f)) { Text(tr("Map")) }
                    val dest = open.destLat?.let { a -> open.destLon?.let { com.qcenaman.francesinha.logic.LatLon(a, it) } }
                    if (dest != null) {
                        OutlinedButton(onClick = { openNavigation(ctx, dest, open.destName) }, Modifier.weight(1f)) { Text(tr("Navigate")) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (open == null) {
                    Button(
                        onClick = {
                            Store.startSession(System.currentTimeMillis(), auto = false) { Prefs.newSessionDefaults(ctx, it) }
                            TrackingService.refresh(ctx)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(tr("Start trip")) }
                } else {
                    if (pause != null) {
                        Button(
                            onClick = {
                                Store.endPause(System.currentTimeMillis())
                                TrackingService.refresh(ctx)
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text(tr("End: %s", tr(pause.type.label))) }
                    } else {
                        Box(Modifier.weight(1f)) {
                            Button(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                Text(tr("Start break ▾"))
                            }
                            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                                PauseType.entries.forEach { t ->
                                    DropdownMenuItem(text = { Text(tr(t.label)) }, onClick = {
                                        typeMenu = false
                                        Store.startPause(System.currentTimeMillis(), t, auto = false)
                                        TrackingService.refresh(ctx)
                                    })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = {
                            Store.endSession(System.currentTimeMillis())
                            TrackingService.refresh(ctx, manualEnd = true)
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(tr("End trip")) }
                }
            }
        }
    }
}

@Composable
private fun ExtrasQuickCard(hasTrips: Boolean, open: Boolean) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var adjMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ExtraType?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun addItem(e: com.qcenaman.francesinha.model.Extra) {
        val here = com.qcenaman.francesinha.data.LastFix.fresh()
        msg = if (Store.addExtraItem(if (here != null && e.lat == null) e.copy(lat = here.lat, lon = here.lon) else e)) {
            val v = if (e.percent != null) Fmt.num(e.percent) + "%" else Fmt.eur(if (e.type == ExtraType.CUSTOM && e.deduct) -e.euros else e.type.sign * e.euros)
            tr(if (open) "%s (%s) added to this trip" else "%s (%s) added to the last trip", e.name.ifBlank { tr(e.type.label) }, v)
        } else {
            tr("Start or add a trip first")
        }
    }
    fun add(t: ExtraType) = addItem(com.qcenaman.francesinha.model.Extra(Store.newId(), t, System.currentTimeMillis(), Prefs.euros(ctx, t)))
    dialog?.let { t ->
        QuickExtraDialog(t, onDismiss = { dialog = null }) { e ->
            dialog = null
            addItem(e)
        }
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(tr("Meals, sleepovers & extras"), fontWeight = FontWeight.Bold)
            Text(
                if (open) tr("Adds to the current trip") else if (hasTrips) tr("Adds to the last trip") else tr("No trip yet"),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Button(onClick = { menu = true }, Modifier.fillMaxWidth().height(52.dp), enabled = hasTrips) {
                        Text(tr("+ Meal ▾"))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ExtraType.entries.filter { it.isMeal }.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(tr(t.label) + "  " + Fmt.eur(Prefs.euros(ctx, t))) },
                                onClick = { menu = false; add(t) },
                            )
                        }
                    }
                }
                Button(
                    onClick = { add(ExtraType.SLEEPOVER) },
                    modifier = Modifier.weight(1f).height(52.dp), enabled = hasTrips,
                ) { Text(tr("+ Sleepover")) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { adjMenu = true }, Modifier.fillMaxWidth(), enabled = hasTrips) {
                        Text(tr("+ Extras ▾"))
                    }
                    DropdownMenu(expanded = adjMenu, onDismissRequest = { adjMenu = false }) {
                        extraChoices(ctx, ExtraType.entries.filter { it.isAdjustment && it != ExtraType.CUSTOM }).forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = {
                                adjMenu = false
                                addItem(c.make(System.currentTimeMillis()))
                            })
                        }
                        extraChoices(ctx, listOf(ExtraType.CUSTOM)).drop(1).forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = {
                                adjMenu = false
                                addItem(c.make(System.currentTimeMillis()))
                            })
                        }
                        DropdownMenuItem(text = { Text(tr("Custom item…")) }, onClick = {
                            adjMenu = false
                            dialog = ExtraType.CUSTOM
                        })
                    }
                }
                OutlinedButton(onClick = { dialog = ExtraType.EXPENSE }, Modifier.weight(1f), enabled = hasTrips) {
                    Text(tr("+ Expense"))
                }
            }
            msg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SessionRow(s: Session, now: Long, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.date(s.start), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    Fmt.time(s.start) + " – " + (s.end?.let { Fmt.time(it) } ?: tr("now")),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (s.title.isNotBlank()) Text(s.title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            val breaks = s.pauses.size
            Text(
                tr("Driving %s · %s · ", Fmt.duration(Stats.drivingMs(s, now)), Fmt.km(s.km())) +
                    tr(if (breaks == 1) "%d stop (%s)" else "%d stops (%s)", breaks, Fmt.duration(Stats.pauseMs(s, now))) +
                    if (s.auto) tr(" · auto") else "",
                style = MaterialTheme.typography.bodySmall,
            )
            if (s.hourlyRate != null || s.extras.isNotEmpty()) {
                Text(
                    tr("Expected %s", Fmt.eur(Stats.totalPay(s, now))) +
                        (s.hourlyRate?.let { " · " + tr("%s at %s/h", Fmt.duration(Stats.paidMs(s, now)), Fmt.eur(it)) } ?: "") +
                        (if (Stats.overtimeMs(s, now) > 0) " · " + tr("overtime %s", Fmt.duration(Stats.overtimeMs(s, now))) else ""),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                )
            }
            Stats.invoiceDiff(s, now)?.let { d ->
                Text(
                    tr("Invoice %s (%s)", Fmt.eur(s.invoiceEuros ?: 0.0), Fmt.eurDiff(d)),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                    color = if (d < -0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
            }
            if (s.extras.isNotEmpty()) {
                val meals = s.extras.count { it.type.isMeal }
                val nights = s.extras.count { it.type.isSleepover }
                Text(
                    tr("%d meals · %d sleepovers · extras %s", meals, nights, Fmt.eur(Stats.extrasEuros(s))),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PermissionsCard() {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val after = { _: Any? ->
        refresh++
        TrackingService.refresh(ctx)
    }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { after(it) }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { after(it) }

    @Suppress("UNUSED_VARIABLE") val tick = refresh
    val fine = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    val bg = Build.VERSION.SDK_INT < 29 || granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    val notif = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val battery = pm.isIgnoringBatteryOptimizations(ctx.packageName)
    if (fine && bg && notif && battery) return

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Setup needed"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(tr("So trips are recorded even with the screen off:"), style = MaterialTheme.typography.bodySmall)
            if (!fine) {
                Button(onClick = {
                    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                    multi.launch(perms.toTypedArray())
                }, Modifier.fillMaxWidth()) { Text(tr("1. Allow location")) }
            } else if (!bg) {
                Button(onClick = { single.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }, Modifier.fillMaxWidth()) {
                    Text(tr("2. Location: choose \"Allow all the time\""))
                }
            }
            if (fine && !notif && Build.VERSION.SDK_INT >= 33) {
                Button(onClick = { single.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.fillMaxWidth()) {
                    Text(tr("Allow notifications"))
                }
            }
            if (!battery) {
                OutlinedButton(onClick = {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                        )
                    } catch (_: Exception) {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (_: Exception) {
                        }
                    }
                }, Modifier.fillMaxWidth()) { Text(tr("Don't let the battery saver stop it")) }
            }
        }
    }
}

@Composable
private fun LimitsCard(sessions: List<Session>, now: Long) {
    val st = Limits.status(sessions, now)
    if (st.todayMs == 0L && st.weekMs == 0L) return
    val warn = st.dailyOver || st.weeklyOver || st.fortnightOver || st.extendedTooOften
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Driving limits (EU)"), fontWeight = FontWeight.Bold)
            Text(tr("Today: %s of 9h (10h twice a week)", Fmt.duration(st.todayMs)), style = MaterialTheme.typography.bodyMedium)
            Text(tr("This week: %s of 56h", Fmt.duration(st.weekMs)), style = MaterialTheme.typography.bodyMedium)
            Text(tr("Two weeks: %s of 90h", Fmt.duration(st.fortnightMs)), style = MaterialTheme.typography.bodyMedium)
            if (st.extendedTooOften) Text(tr("More than 2 days over 9h this week."), color = MaterialTheme.colorScheme.error)
            Text(tr("Guide only — the tachograph is what counts."), style = MaterialTheme.typography.bodySmall)
        }
    }
}
