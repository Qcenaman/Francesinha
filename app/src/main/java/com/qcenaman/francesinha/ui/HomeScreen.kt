package com.qcenaman.francesinha.ui

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
import com.qcenaman.francesinha.logic.Period
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session
import com.qcenaman.francesinha.service.TrackingService

private fun granted(ctx: Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun HomeScreen(modifier: Modifier, onEdit: (Long) -> Unit) {
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
        item { StatusCard(open, now) }
        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic trips", fontWeight = FontWeight.Bold)
                        Text(
                            if (auto) "Trips and breaks are detected by GPS" else "Off: start and stop trips by hand",
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
                    Text("Today", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        "Driving" to Fmt.duration(today.drivingMs),
                        "Breaks" to Fmt.duration(today.pauseMs),
                        "Distance" to Fmt.km(today.km),
                    )
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        "Meals" to today.meals.toString(),
                        "Sleepovers" to today.sleepovers.toString(),
                        "Pay" to Fmt.eur(today.euros),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("This week", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        "Driving" to Fmt.duration(week.drivingMs),
                        "On duty" to Fmt.duration(week.totalMs),
                        "Distance" to Fmt.km(week.km),
                    )
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        "Meals" to week.meals.toString(),
                        "Sleepovers" to week.sleepovers.toString(),
                        "Pay" to Fmt.eur(week.euros),
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Trips", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = { onEdit(0L) }) { Text("+ Add trip by hand") }
            }
        }
        if (sessions.isEmpty()) {
            item {
                Text(
                    "No trips yet. Start driving with automatic trips on, or tap Start trip.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(sessions, key = { it.id }) { s -> SessionRow(s, now) { onEdit(s.id) } }
    }
}

@Composable
private fun StatusCard(open: Session?, now: Long) {
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
                    Text("Not on a trip", style = MaterialTheme.typography.headlineSmall)
                    Text("Driving will start a trip automatically, or start one now.")
                }
                pause != null -> {
                    Text("${pause.type.label} since ${Fmt.time(pause.start)}", style = MaterialTheme.typography.headlineSmall)
                    Text("${Fmt.duration(now - pause.start)} so far")
                }
                else -> {
                    Text("Driving", style = MaterialTheme.typography.headlineSmall)
                    Text("Since last break: ${Fmt.duration(Stats.continuousDrivingMs(open, now))}")
                }
            }
            if (open != null) {
                Spacer(Modifier.height(8.dp))
                StatRow(
                    "Started" to Fmt.time(open.start),
                    "Driving" to Fmt.duration(Stats.drivingMs(open, now)),
                    "Distance" to Fmt.km(open.km()),
                )
                if (open.hourlyRate != null || open.extras.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Earned so far: ${Fmt.eur(Stats.totalPay(open, now))}" +
                            (open.hourlyRate?.let { " (${Fmt.eur(it)}/h)" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (open == null) {
                    Button(
                        onClick = {
                            Store.startSession(System.currentTimeMillis(), auto = false, rate = Prefs.rate(ctx))
                            TrackingService.refresh(ctx)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Start trip") }
                } else {
                    if (pause != null) {
                        Button(
                            onClick = {
                                Store.endPause(System.currentTimeMillis())
                                TrackingService.refresh(ctx)
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text("End ${pause.type.label.lowercase()}") }
                    } else {
                        Box(Modifier.weight(1f)) {
                            Button(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                Text("Start break ▾")
                            }
                            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                                PauseType.entries.forEach { t ->
                                    DropdownMenuItem(text = { Text(t.label) }, onClick = {
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
                    ) { Text("End trip") }
                }
            }
        }
    }
}

@Composable
private fun ExtrasQuickCard(hasTrips: Boolean, open: Boolean) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun add(t: ExtraType) {
        val eur = Prefs.euros(ctx, t)
        msg = if (Store.addExtra(t, System.currentTimeMillis(), eur)) {
            "${t.label} (${Fmt.eur(eur)}) added to ${if (open) "this trip" else "the last trip"}"
        } else {
            "Start or add a trip first"
        }
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Meals & sleepovers", fontWeight = FontWeight.Bold)
            Text(
                if (open) "Adds to the current trip" else if (hasTrips) "Adds to the last trip" else "No trip yet",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Button(onClick = { menu = true }, Modifier.fillMaxWidth().height(52.dp), enabled = hasTrips) {
                        Text("+ Meal ▾")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ExtraType.entries.filter { it.isMeal }.forEach { t ->
                            DropdownMenuItem(
                                text = { Text("${t.label}  ${Fmt.eur(Prefs.euros(ctx, t))}") },
                                onClick = { menu = false; add(t) },
                            )
                        }
                    }
                }
                Button(
                    onClick = { add(ExtraType.SLEEPOVER) },
                    modifier = Modifier.weight(1f).height(52.dp), enabled = hasTrips,
                ) { Text("+ Sleepover") }
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
                    "${Fmt.time(s.start)} – ${s.end?.let { Fmt.time(it) } ?: "now"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (s.title.isNotBlank()) Text(s.title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            val breaks = s.pauses.size
            Text(
                "Driving ${Fmt.duration(Stats.drivingMs(s, now))} · ${Fmt.km(s.km())} · " +
                    "$breaks ${if (breaks == 1) "stop" else "stops"} (${Fmt.duration(Stats.pauseMs(s, now))})" +
                    if (s.auto) " · auto" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            if (s.hourlyRate != null || s.extras.isNotEmpty()) {
                Text(
                    "Pay ${Fmt.eur(Stats.totalPay(s, now))}" +
                        (s.hourlyRate?.let { " · ${Fmt.duration(Stats.paidMs(s, now))} at ${Fmt.eur(it)}/h" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                )
            }
            if (s.extras.isNotEmpty()) {
                val meals = s.extras.count { it.type.isMeal }
                val nights = s.extras.count { !it.type.isMeal }
                Text(
                    "$meals ${if (meals == 1) "meal" else "meals"} · $nights ${if (nights == 1) "sleepover" else "sleepovers"} · " +
                        Fmt.eur(s.extras.sumOf { it.euros }),
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
            Text("Setup needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("So trips are recorded even with the screen off:", style = MaterialTheme.typography.bodySmall)
            if (!fine) {
                Button(onClick = {
                    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                    multi.launch(perms.toTypedArray())
                }, Modifier.fillMaxWidth()) { Text("1. Allow location") }
            } else if (!bg) {
                Button(onClick = { single.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }, Modifier.fillMaxWidth()) {
                    Text("2. Location: choose \"Allow all the time\"")
                }
            }
            if (fine && !notif && Build.VERSION.SDK_INT >= 33) {
                Button(onClick = { single.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.fillMaxWidth()) {
                    Text("Allow notifications")
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
                }, Modifier.fillMaxWidth()) { Text("Don't let the battery saver stop it") }
            }
        }
    }
}
