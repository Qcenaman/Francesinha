package com.qcenaman.francesinha.ui

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
            Session(id = 0L, start = now - 3_600_000L, end = now, hourlyRate = Prefs.rate(ctx))
        }
    }
    var s by remember(id) { mutableStateOf(initial) }
    var odoStart by remember(id) { mutableStateOf(initial.odoStart?.let { Fmt.num(it) } ?: "") }
    var odoEnd by remember(id) { mutableStateOf(initial.odoEnd?.let { Fmt.num(it) } ?: "") }
    var manualKm by remember(id) { mutableStateOf(initial.manualKm?.let { Fmt.num(it) } ?: "") }
    var rate by remember(id) {
        mutableStateOf(initial.hourlyRate?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val now = rememberNow()
    val isNew = id == 0L

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = s.title, onValueChange = { s = s.copy(title = it) },
            label = { Text("Route / load (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDateTime(ctx, s.start) { s = s.copy(start = it) } }, Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Start", style = MaterialTheme.typography.labelSmall)
                    Text(Fmt.dateTime(s.start))
                }
            }
            OutlinedButton(onClick = { pickDateTime(ctx, s.end ?: now) { s = s.copy(end = it) } }, Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("End", style = MaterialTheme.typography.labelSmall)
                    Text(s.end?.let { Fmt.dateTime(it) } ?: "Still running")
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp)) {
                StatRow(
                    "Driving" to Fmt.duration(Stats.drivingMs(s, now)),
                    "Stops" to Fmt.duration(Stats.pauseMs(s, now)),
                    "Total" to Fmt.duration(Stats.totalMs(s, now)),
                )
                Spacer(Modifier.height(6.dp))
                Text("GPS distance: ${Fmt.km(s.gpsMeters / 1000.0)}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Pay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = rate,
            onValueChange = {
                rate = it
                s = s.copy(hourlyRate = parseNum(it))
            },
            label = { Text("Pay per hour for this job (€)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Breaks and rest are paid", modifier = Modifier.weight(1f))
            Switch(checked = s.paidBreaks, onCheckedChange = { s = s.copy(paidBreaks = it) })
        }
        Card {
            Column(Modifier.padding(12.dp)) {
                StatRow(
                    "Paid hours" to Fmt.duration(Stats.paidMs(s, now)),
                    "Hours pay" to Fmt.eur(Stats.hourPay(s, now)),
                    "Total pay" to Fmt.eur(Stats.totalPay(s, now)),
                )
            }
        }

        Text("Kilometres", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = odoStart, onValueChange = { odoStart = it }, label = { Text("Odometer start") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = odoEnd, onValueChange = { odoEnd = it }, label = { Text("Odometer end") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        OutlinedTextField(
            value = manualKm, onValueChange = { manualKm = it },
            label = { Text("Km override (leave empty to use odometer / GPS)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Breaks and stops", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val sEnd = s.end ?: now
                val start = maxOf(s.start, sEnd - 30 * 60_000L)
                val end = minOf(start + 30 * 60_000L, sEnd)
                s = s.copy(pauses = (s.pauses + Pause(Store.newId(), PauseType.BREAK, start, end)).sortedBy { it.start })
            }) { Text("+ Add break") }
        }
        if (s.pauses.isEmpty()) Text("No breaks.", style = MaterialTheme.typography.bodySmall)
        s.pauses.forEach { p ->
            PauseEditor(
                p = p, now = now,
                onChange = { np -> s = s.copy(pauses = s.pauses.map { if (it.id == p.id) np else it }.sortedBy { it.start }) },
                onDelete = { s = s.copy(pauses = s.pauses.filterNot { it.id == p.id }) },
            )
        }

        HorizontalDivider()
        var mealMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Meals & sleepovers (${Fmt.eur(s.extras.sumOf { it.euros })})",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            Box {
                TextButton(onClick = { mealMenu = true }) { Text("+ Add ▾") }
                DropdownMenu(expanded = mealMenu, onDismissRequest = { mealMenu = false }) {
                    ExtraType.entries.forEach { t ->
                        DropdownMenuItem(text = { Text(t.label) }, onClick = {
                            mealMenu = false
                            val time = minOf(maxOf(now, s.start), s.end ?: now)
                            val e = Extra(Store.newId(), t, time, Prefs.euros(ctx, t))
                            s = s.copy(extras = (s.extras + e).sortedBy { it.time })
                        })
                    }
                }
            }
        }
        if (s.extras.isEmpty()) Text("None.", style = MaterialTheme.typography.bodySmall)
        s.extras.forEach { e ->
            ExtraEditor(
                e = e,
                onChange = { ne -> s = s.copy(extras = s.extras.map { if (it.id == e.id) ne else it }.sortedBy { it.time }) },
                onDelete = { s = s.copy(extras = s.extras.filterNot { it.id == e.id }) },
            )
        }

        HorizontalDivider()
        OutlinedTextField(
            value = s.notes, onValueChange = { s = s.copy(notes = it) },
            label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3,
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = {
            val sEnd = s.end
            val otherOpen = Store.openSession()?.let { it.id != s.id } ?: false
            val bad = when {
                sEnd != null && sEnd < s.start -> "End must be after start."
                sEnd == null && (isNew || otherOpen) -> "Set an end time (another trip is running)."
                s.pauses.any { (it.end ?: now) < it.start } -> "A break ends before it starts."
                s.pauses.any { it.start < s.start || (sEnd != null && (it.end ?: sEnd) > sEnd) } ->
                    "Breaks must be inside the trip times."
                s.pauses.count { it.end == null } > 1 -> "Only one break can be running."
                sEnd != null && s.pauses.any { it.end == null } -> "Set an end time for every break."
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
                hourlyRate = parseNum(rate),
            )
            Store.upsert(saved)
            TrackingService.refresh(ctx)
            onDone()
        }, Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }

        OutlinedButton(onClick = onDone, Modifier.fillMaxWidth()) { Text("Cancel") }

        if (!isNew) {
            if (s.end != null) {
                OutlinedButton(onClick = {
                    if (Store.mergeWithPrevious(s.id)) onDone() else error = "No earlier trip to join."
                }, Modifier.fillMaxWidth()) { Text("Join with previous trip") }
            }
            OutlinedButton(
                onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete trip") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this trip?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Store.delete(id)
                    TrackingService.refresh(ctx)
                    onDone()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
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
                    TextButton(onClick = { menu = true }) { Text("${p.type.label} ▾") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        PauseType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = {
                                menu = false
                                onChange(p.copy(type = t))
                            })
                        }
                    }
                }
                Text(Fmt.duration((p.end ?: now) - p.start), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDateTime(ctx, p.start) { onChange(p.copy(start = it)) } }, Modifier.weight(1f)) {
                    Text("From ${Fmt.time(p.start)}")
                }
                OutlinedButton(onClick = { pickDateTime(ctx, p.end ?: now) { onChange(p.copy(end = it)) } }, Modifier.weight(1f)) {
                    Text("To ${p.end?.let { Fmt.time(it) } ?: "now"}")
                }
            }
            OutlinedTextField(
                value = p.note, onValueChange = { onChange(p.copy(note = it)) },
                label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
    }
}

@Composable
private fun ExtraEditor(e: Extra, onChange: (Extra) -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var eur by remember(e.id) { mutableStateOf(if (e.euros == 0.0) "" else String.format(java.util.Locale.US, "%.2f", e.euros)) }
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { menu = true }) { Text("${e.type.label} ▾") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ExtraType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = {
                                menu = false
                                onChange(e.copy(type = t))
                            })
                        }
                    }
                }
                TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickDateTime(ctx, e.time) { onChange(e.copy(time = it)) } }, Modifier.weight(1f)) {
                    Text(Fmt.dateTime(e.time))
                }
                OutlinedTextField(
                    value = eur,
                    onValueChange = {
                        eur = it
                        onChange(e.copy(euros = parseNum(it) ?: 0.0))
                    },
                    label = { Text("€ pay") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            OutlinedTextField(
                value = e.note, onValueChange = { onChange(e.copy(note = it)) },
                label = { Text("Note (place, receipt…)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
    }
}
