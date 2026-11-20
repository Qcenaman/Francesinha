package com.qcenaman.francesinha.ui

import com.qcenaman.francesinha.logic.TaxCalc
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import com.qcenaman.francesinha.logic.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qcenaman.francesinha.data.Store
import com.qcenaman.francesinha.logic.Fmt
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.data.Prefs
import com.qcenaman.francesinha.data.Receipts
import com.qcenaman.francesinha.model.Extra
import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.Pause
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Session
import com.qcenaman.francesinha.service.TrackingService

@Composable
fun EditScreen(modifier: Modifier, id: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val initial = remember(id) {
        Store.get(id) ?: run {
            val now = System.currentTimeMillis()
            Prefs.newSessionDefaults(ctx, Session(id = 0L, start = now - 3_600_000L, end = now))
        }
    }
    var s by remember(id) { mutableStateOf(initial) }
    var odoStart by remember(id) { mutableStateOf(initial.odoStart?.let { Fmt.num(it) } ?: "") }
    var odoEnd by remember(id) { mutableStateOf(initial.odoEnd?.let { Fmt.num(it) } ?: "") }
    var manualKm by remember(id) { mutableStateOf(initial.manualKm?.let { Fmt.num(it) } ?: "") }
    var invoice by remember(id) { mutableStateOf(initial.invoiceEuros?.let { money(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val now = rememberNow()
    val isNew = id == 0L
    var showMap by remember { mutableStateOf(false) }

    if (showMap) {
        androidx.activity.compose.BackHandler { showMap = false }
        val track = remember(id) { com.qcenaman.francesinha.data.TrackStore.load(id) }
        Column(modifier.fillMaxWidth()) {
            TextButton(onClick = { showMap = false }) { Text(tr("Close map")) }
            TruckMap(com.qcenaman.francesinha.logic.TripMap.forSession(s, track), Modifier.fillMaxWidth().weight(1f))
        }
        return
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = s.title, onValueChange = { s = s.copy(title = it) },
            label = { Text(tr("Route / load (optional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        ClientField(s.client) { s = s.copy(client = it) }
        PlaceField(tr("Destination"), s.destName, allowMyLocation = false) { pl ->
            s = s.copy(destName = pl.name, destLat = pl.lat, destLon = pl.lon)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isNew) OutlinedButton(onClick = { showMap = true }, Modifier.weight(1f)) { Text(tr("Map")) }
            val dest = s.destLat?.let { a -> s.destLon?.let { com.qcenaman.francesinha.logic.LatLon(a, it) } }
            if (dest != null) {
                OutlinedButton(onClick = { openNavigation(ctx, dest, s.destName) }, Modifier.weight(1f)) { Text(tr("Navigate")) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDateTime(ctx, s.start) { s = s.copy(start = it) } }, Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Start"), style = MaterialTheme.typography.labelSmall)
                    Text(Fmt.dateTime(s.start))
                }
            }
            OutlinedButton(onClick = { pickDateTime(ctx, s.end ?: now) { s = s.copy(end = it) } }, Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("End"), style = MaterialTheme.typography.labelSmall)
                    Text(s.end?.let { Fmt.dateTime(it) } ?: tr("Still running"))
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp)) {
                StatRow(
                    tr("Driving") to Fmt.duration(Stats.drivingMs(s, now)),
                    tr("Stops") to Fmt.duration(Stats.pauseMs(s, now)),
                    tr("Total") to Fmt.duration(Stats.totalMs(s, now)),
                )
                Spacer(Modifier.height(6.dp))
                Text(tr("GPS distance: %s", Fmt.km(s.gpsMeters / 1000.0)), style = MaterialTheme.typography.bodySmall)
            }
        }

        PayEditor(s, now) { s = it }

        Text(tr("Invoice"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = invoice,
            onValueChange = {
                invoice = it
                s = s.copy(invoiceEuros = parseNum(it))
            },
            label = { Text(tr("Final invoice amount (€)")) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        ComparisonCard(
            planned = s.plannedEuros, expected = Stats.totalPay(s, now), invoice = s.invoiceEuros,
        )

        Text(tr("Kilometres"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = odoStart, onValueChange = { odoStart = it }, label = { Text(tr("Odometer start")) },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = odoEnd, onValueChange = { odoEnd = it }, label = { Text(tr("Odometer end")) },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        OutlinedTextField(
            value = manualKm, onValueChange = { manualKm = it },
            label = { Text(tr("Km override (leave empty to use odometer / GPS)")) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Breaks and stops"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val sEnd = s.end ?: now
                val start = maxOf(s.start, sEnd - 30 * 60_000L)
                val end = minOf(start + 30 * 60_000L, sEnd)
                s = s.copy(pauses = (s.pauses + Pause(Store.newId(), PauseType.BREAK, start, end)).sortedBy { it.start })
            }) { Text(tr("+ Add break")) }
        }
        if (s.pauses.isEmpty()) Text(tr("No breaks."), style = MaterialTheme.typography.bodySmall)
        s.pauses.forEach { p ->
            PauseEditor(
                p = p, now = now,
                onChange = { np -> s = s.copy(pauses = s.pauses.map { if (it.id == p.id) np else it }.sortedBy { it.start }) },
                onDelete = { s = s.copy(pauses = s.pauses.filterNot { it.id == p.id }) },
            )
        }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Meals, sleepovers & extras (%s)", Fmt.eur(Stats.extrasEuros(s, now))),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            AddExtraMenu(tr("+ Add ▾"), extraChoices(ctx)) { c ->
                val time = minOf(maxOf(now, s.start), s.end ?: now)
                s = s.copy(extras = (s.extras + c.make(time)).sortedBy { it.time })
            }
        }
        if (s.extras.isEmpty()) {
            Text(tr("Meals, sleepovers, prizes, supplements, discounts, custom items and expenses go here."), style = MaterialTheme.typography.bodySmall)
        }
        s.extras.forEach { e ->
            ExtraEditor(
                e = e, value = Stats.extraValue(e, s, now),
                onChange = { ne -> s = s.copy(extras = s.extras.map { if (it.id == e.id) ne else it }.sortedBy { it.time }) },
                onDelete = { s = s.copy(extras = s.extras.filterNot { it.id == e.id }) },
            )
        }
        if (Stats.expenseEuros(s) > 0) {
            Text(
                tr("Expenses %s (refunded %s)", Fmt.eur(Stats.expenseEuros(s)), Fmt.eur(Stats.refundedEuros(s))),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()
        PhotosSection(s.photos) { s = s.copy(photos = it) }

        HorizontalDivider()
        OutlinedTextField(
            value = s.notes, onValueChange = { s = s.copy(notes = it) },
            label = { Text(tr("Notes")) }, modifier = Modifier.fillMaxWidth(), minLines = 3,
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = {
            val sEnd = s.end
            val otherOpen = Store.openSession()?.let { it.id != s.id } ?: false
            val bad = when {
                sEnd != null && sEnd < s.start -> tr("End must be after start.")
                sEnd == null && (isNew || otherOpen) -> tr("Set an end time (another trip is running).")
                s.pauses.any { (it.end ?: now) < it.start } -> tr("A break ends before it starts.")
                s.pauses.any { it.start < s.start || (sEnd != null && (it.end ?: sEnd) > sEnd) } ->
                    tr("Breaks must be inside the trip times.")
                s.pauses.count { it.end == null } > 1 -> tr("Only one break can be running.")
                sEnd != null && s.pauses.any { it.end == null } -> tr("Set an end time for every break.")
                else -> null
            }
            if (bad != null) {
                error = bad
                return@Button
            }
            val saved = s.copy(
                id = if (isNew) Store.newId() else s.id,
                odoStart = parseNum(odoStart),
                odoEnd = parseNum(odoEnd),
                manualKm = parseNum(manualKm),
                invoiceEuros = parseNum(invoice),
            )
            Store.upsert(saved)
            if (saved.client.isNotBlank()) Prefs.setLastClient(ctx, saved.client)
            TrackingService.refresh(ctx)
            onDone()
        }, Modifier.fillMaxWidth().height(52.dp)) { Text(tr("Save")) }

        OutlinedButton(onClick = onDone, Modifier.fillMaxWidth()) { Text(tr("Cancel")) }

        if (!isNew) {
            if (s.end != null) {
                OutlinedButton(onClick = {
                    if (Store.mergeWithPrevious(s.id)) onDone() else error = tr("No earlier trip to join.")
                }, Modifier.fillMaxWidth()) { Text(tr("Join with previous trip")) }
            }
            OutlinedButton(
                onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(tr("Delete trip")) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete this trip?")) },
            text = { Text(tr("This can't be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Store.get(id)?.photos?.forEach { Receipts.delete(ctx, it) }
                    com.qcenaman.francesinha.data.TrackStore.delete(id)
                    Store.delete(id)
                    TrackingService.refresh(ctx)
                    onDone()
                }) { Text(tr("Delete")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Keep")) } },
        )
    }
}

@Composable
private fun PauseEditor(p: Pause, now: Long, onChange: (Pause) -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { menu = true }) { Text(tr(p.type.label) + " ▾") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        PauseType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(tr(t.label)) }, onClick = {
                                menu = false
                                onChange(p.copy(type = t))
                            })
                        }
                    }
                }
                Text(Fmt.duration((p.end ?: now) - p.start), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onDelete) { Text(tr("Remove"), color = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDateTime(ctx, p.start) { onChange(p.copy(start = it)) } }, Modifier.weight(1f)) {
                    Text(tr("From %s", Fmt.time(p.start)))
                }
                OutlinedButton(onClick = { pickDateTime(ctx, p.end ?: now) { onChange(p.copy(end = it)) } }, Modifier.weight(1f)) {
                    Text(tr("To %s", p.end?.let { Fmt.time(it) } ?: tr("now")))
                }
            }
            OutlinedTextField(
                value = p.note, onValueChange = { onChange(p.copy(note = it)) },
                label = { Text(tr("Note")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
    }
}

fun money(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

/** Hourly rate, overtime, km, night/weekend/holiday and paid-breaks settings, with the resulting pay. */
@Composable
fun PayEditor(s: Session, now: Long, onChange: (Session) -> Unit) {
    val ctx = LocalContext.current
    var rate by remember(s.id) { mutableStateOf(s.hourlyRate?.let { money(it) } ?: "") }
    var otAfter by remember(s.id) { mutableStateOf(s.overtimeAfterMin?.let { Fmt.num(it / 60.0) } ?: "") }
    var otRate by remember(s.id) { mutableStateOf(s.overtimeRate?.let { money(it) } ?: "") }
    var perKm by remember(s.id) { mutableStateOf(s.perKmRate?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "") }
    var night by remember(s.id) { mutableStateOf(s.nightPct?.let { Fmt.num(it) } ?: "") }
    var weekend by remember(s.id) { mutableStateOf(s.weekendPct?.let { Fmt.num(it) } ?: "") }
    var holiday by remember(s.id) { mutableStateOf(s.holidayPct?.let { Fmt.num(it) } ?: "") }
    var more by remember { mutableStateOf(s.perKmRate != null || s.nightPct != null || s.weekendPct != null || s.holiday) }

    @Composable
    fun NumField(value: String, label: String, modifier: Modifier, set: (String) -> Unit) = OutlinedTextField(
        value = value, onValueChange = set, label = { Text(label) }, modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )

    Text(tr("Pay"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    NumField(rate, tr("Pay per hour for this job (€)"), Modifier.fillMaxWidth()) {
        rate = it
        onChange(s.copy(hourlyRate = parseNum(it)))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumField(otAfter, tr("Overtime after (h)"), Modifier.weight(1f)) {
            otAfter = it
            onChange(s.copy(overtimeAfterMin = parseNum(it)?.takeIf { h -> h > 0 }?.let { h -> (h * 60).toInt() }))
        }
        NumField(otRate, tr("Overtime €/h"), Modifier.weight(1f)) {
            otRate = it
            onChange(s.copy(overtimeRate = parseNum(it)))
        }
    }
    SwitchRow(tr("Breaks and rest are paid"), s.paidBreaks) { onChange(s.copy(paidBreaks = it)) }
    SwitchRow(tr("More pay rules (km, night, weekend, holiday)"), more) { more = it }
    if (more) {
        NumField(perKm, tr("Pay per km (€)"), Modifier.fillMaxWidth()) {
            perKm = it
            onChange(s.copy(perKmRate = parseNum(it)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(night, tr("Night +%% (%d–%d h)", s.nightFromHour, s.nightToHour), Modifier.weight(1f)) {
                night = it
                onChange(s.copy(nightPct = parseNum(it)))
            }
            NumField(weekend, tr("Weekend +%"), Modifier.weight(1f)) {
                weekend = it
                onChange(s.copy(weekendPct = parseNum(it)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumField(holiday, tr("Holiday +%"), Modifier.weight(1f)) {
                holiday = it
                onChange(s.copy(holidayPct = parseNum(it)))
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Holiday"), modifier = Modifier.weight(1f))
                Switch(checked = s.holiday, onCheckedChange = { onChange(s.copy(holiday = it)) })
            }
        }
    }
    val taxes = Prefs.taxes(ctx)
    val gross = Stats.totalPay(s, now)
    val taxable = Stats.taxable(s, now, Prefs.mealsTaxFree(ctx))
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatRow(
                tr("Paid hours") to Fmt.duration(Stats.paidMs(s, now)),
                tr("Hours pay") to Fmt.eur(Stats.hourPay(s, now)),
                tr("Overtime") to Fmt.eur(Stats.overtimePay(s, now)),
            )
            if (more) {
                StatRow(
                    tr("Km pay") to Fmt.eur(Stats.kmPay(s)),
                    tr("Night/weekend") to Fmt.eur(Stats.supplementPay(s, now)),
                    tr("Night hours") to Fmt.duration(Stats.nightMs(s, now)),
                )
            }
            StatRow(
                tr("Meals") to Fmt.eur(Stats.mealEuros(s)),
                tr("Sleepovers") to Fmt.eur(Stats.sleepoverEuros(s)),
                tr("Extras") to Fmt.eur(Stats.adjustEuros(s, now) + Stats.refundedEuros(s)),
            )
            Text(
                tr("Expected total: %s", Fmt.eur(gross)) +
                    if (Stats.overtimeMs(s, now) > 0) "  " + tr("(incl. %s overtime)", Fmt.duration(Stats.overtimeMs(s, now))) else "",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
            if (TaxCalc.percent(taxes) > 0) {
                Text(
                    tr("After tax ≈ %s (taxes %s)", Fmt.eur(TaxCalc.net(gross, taxable, taxes)), Fmt.eur(TaxCalc.amount(taxable, taxes))),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Client / employer with quick picks from earlier trips. */
@Composable
fun ClientField(value: String, onChange: (String) -> Unit) {
    val known = Store.sessions.value.map { it.client }.filter { it.isNotBlank() }.distinct().take(8)
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(tr("Client / employer")) },
            modifier = Modifier.weight(1f), singleLine = true,
        )
        if (known.isNotEmpty()) {
            Box {
                TextButton(onClick = { open = true }) { Text("▾") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    known.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { open = false; onChange(c) }) }
                }
            }
        }
    }
}

/** Receipt / invoice photos: take one, pick one, view, remove. */
@Composable
fun PhotosSection(photos: List<String>, onChange: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val take = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { ok ->
        val n = pending
        if (ok && n != null) onChange(photos + n) else n?.let { Receipts.delete(ctx, it) }
        pending = null
    }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { Receipts.copyIn(ctx, it) }?.let { onChange(photos + it) } }

    Text(tr("Receipts and invoices"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val f = Receipts.newFile(ctx)
            pending = f.name
            try {
                take.launch(Receipts.uri(ctx, f))
            } catch (_: Exception) {
                pending = null
            }
        }, Modifier.weight(1f)) { Text(tr("Take photo")) }
        OutlinedButton(onClick = {
            try {
                pick.launch("image/*")
            } catch (_: Exception) {
            }
        }, Modifier.weight(1f)) { Text(tr("Choose photo")) }
    }
    if (photos.isNotEmpty()) {
        Row(
            Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            photos.forEach { name ->
                val bmp = remember(name) { Receipts.thumbnail(ctx, name) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.size(96.dp).clickable {
                                try {
                                    ctx.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW)
                                            .setDataAndType(Receipts.uri(ctx, Receipts.file(ctx, name)), "image/*")
                                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    )
                                } catch (_: Exception) {
                                }
                            },
                        )
                    }
                    TextButton(onClick = {
                        Receipts.delete(ctx, name)
                        onChange(photos - name)
                    }) { Text(tr("Remove"), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/** Planned vs expected (calculated) vs invoiced amounts. */
@Composable
fun ComparisonCard(planned: Double?, expected: Double, invoice: Double?) {
    if (planned == null && invoice == null) return
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            planned?.let { Text(tr("Planned: %s", Fmt.eur(it))) }
            Text(tr("Expected (from this trip): %s", Fmt.eur(expected)))
            if (invoice != null) {
                Text(tr("Invoice: %s", Fmt.eur(invoice)))
                val d = invoice - expected
                Text(
                    when {
                        d > 0.005 -> tr("Invoice is %s more than expected", Fmt.eur(d))
                        d < -0.005 -> tr("Invoice is %s LESS than expected", Fmt.eur(-d))
                        else -> tr("Invoice matches what was expected")
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (d < -0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
                planned?.let { Text(tr("vs plan: %s", Fmt.eurDiff(invoice - it)), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
