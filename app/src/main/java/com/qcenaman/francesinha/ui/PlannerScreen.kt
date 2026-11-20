package com.qcenaman.francesinha.ui

import com.qcenaman.francesinha.logic.tr
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.qcenaman.francesinha.data.PlanStore
import com.qcenaman.francesinha.data.Prefs
import com.qcenaman.francesinha.data.Store
import com.qcenaman.francesinha.logic.Fmt
import com.qcenaman.francesinha.logic.PlanCalc
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.model.ExtraType
import com.qcenaman.francesinha.model.PauseType
import com.qcenaman.francesinha.model.Plan
import com.qcenaman.francesinha.model.PlanStep
import com.qcenaman.francesinha.model.StepKind

@Composable
fun PlannerScreen(modifier: Modifier, onEdit: (Long) -> Unit) {
    val plans by PlanStore.plans.collectAsState()
    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(tr("Plan a trip"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        tr("Build an upcoming trip from driving stretches, breaks, meals and sleepovers to see the expected pay."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onEdit(0L) }, Modifier.fillMaxWidth().height(52.dp)) { Text(tr("+ New plan")) }
                }
            }
        }
        if (plans.isEmpty()) item { Text(tr("No plans yet."), style = MaterialTheme.typography.bodyMedium) }
        items(plans, key = { it.id }) { p ->
            val s = PlanCalc.toSession(p)
            val end = s.end ?: s.start
            Card(Modifier.fillMaxWidth().clickable { onEdit(p.id) }) {
                Column(Modifier.padding(14.dp)) {
                    Row {
                        Text(p.name.ifBlank { tr("Plan") }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(Fmt.eur(Stats.totalPay(s, end)), fontWeight = FontWeight.Bold)
                    }
                    Text("${Fmt.dateTime(p.start)} → ${Fmt.dateTime(end)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        tr("Driving %s · %s · paid %s", Fmt.duration(Stats.drivingMs(s, end)), Fmt.km(s.km()), Fmt.duration(Stats.paidMs(s, end))),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun PlanEditScreen(modifier: Modifier, id: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val initial = remember(id) {
        PlanStore.get(id) ?: run {
            val d = Prefs.newSessionDefaults(ctx, com.qcenaman.francesinha.model.Session(id = 0, start = 0))
            val c = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 8); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }
            PlanCalc.applyPay(
                Plan(id = 0, start = c.timeInMillis, steps = listOf(PlanStep(Store.newId(), StepKind.DRIVE, minutes = 240, km = 300.0))),
                d,
            )
        }
    }
    var p by remember(id) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    val s = PlanCalc.toSession(p)
    val end = s.end ?: s.start
    val starts = PlanCalc.stepStarts(p)
    fun savedPlan() = if (p.id == 0L) p.copy(id = Store.newId()).also { p = it } else p
    fun addStep(st: PlanStep) { p = p.copy(steps = p.steps + st) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = p.name, onValueChange = { p = p.copy(name = it) },
            label = { Text(tr("Plan name / route")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        ClientField(p.client) { p = p.copy(client = it) }
        OutlinedButton(onClick = { pickDateTime(ctx, p.start) { p = p.copy(start = it) } }, Modifier.fillMaxWidth()) {
            Text(tr("Starts: %s", Fmt.dateTime(p.start)))
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Ends: %s", Fmt.dateTime(end)), fontWeight = FontWeight.Bold)
                StatRow(
                    tr("Driving") to Fmt.duration(Stats.drivingMs(s, end)),
                    tr("Stops") to Fmt.duration(Stats.pauseMs(s, end)),
                    tr("Distance") to Fmt.km(s.km()),
                )
                Text(tr("Expected pay: %s", Fmt.eur(Stats.totalPay(s, end))), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Steps"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        p.steps.forEachIndexed { i, st ->
            StepEditor(
                st = st, at = starts.getOrElse(i) { p.start }, value = s.extras.firstOrNull { it.id == st.id }?.let { Stats.extraValue(it, s, end) } ?: 0.0,
                onChange = { ns -> p = p.copy(steps = p.steps.map { if (it.id == st.id) ns else it }) },
                onUp = { p = p.copy(steps = PlanCalc.move(p.steps, i, -1)) },
                onDown = { p = p.copy(steps = PlanCalc.move(p.steps, i, 1)) },
                onDelete = { p = p.copy(steps = p.steps.filterNot { it.id == st.id }) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { addStep(PlanStep(Store.newId(), StepKind.DRIVE, minutes = 120, km = 150.0)) }, Modifier.weight(1f)) {
                Text(tr("+ Drive"))
            }
            FilledTonalButton(onClick = { addStep(PlanStep(Store.newId(), StepKind.PAUSE, minutes = 45, pauseType = PauseType.BREAK)) }, Modifier.weight(1f)) {
                Text(tr("+ Break"))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                addStep(PlanStep(Store.newId(), StepKind.PAUSE, minutes = 11 * 60, pauseType = PauseType.REST))
                addStep(PlanStep(Store.newId(), StepKind.EXTRA, extraType = ExtraType.SLEEPOVER, euros = Prefs.euros(ctx, ExtraType.SLEEPOVER)))
            }, Modifier.weight(1f)) { Text(tr("+ Sleepover")) }
            Box(Modifier.weight(1f)) {
                FilledTonalButton(onClick = { addMenu = true }, Modifier.fillMaxWidth()) { Text(tr("+ Other ▾")) }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    extraChoices(ctx, ExtraType.entries.filter { it != ExtraType.SLEEPOVER }).forEach { c ->
                        DropdownMenuItem(text = { Text(c.label) }, onClick = {
                            addMenu = false
                            val e = c.make(0)
                            addStep(
                                PlanStep(
                                    Store.newId(), StepKind.EXTRA, extraType = e.type, euros = e.euros,
                                    name = e.name, percent = e.percent, deduct = e.deduct, refunded = e.refunded,
                                ),
                            )
                        })
                    }
                    PauseType.entries.filter { it != PauseType.BREAK }.forEach { t ->
                        DropdownMenuItem(text = { Text(tr("Stop: %s", tr(t.label))) }, onClick = {
                            addMenu = false
                            addStep(PlanStep(Store.newId(), StepKind.PAUSE, minutes = 60, pauseType = t))
                        })
                    }
                }
            }
        }

        HorizontalDivider()
        PayEditor(s, end) { ns -> p = PlanCalc.applyPay(p, ns) }

        OutlinedTextField(
            value = p.notes, onValueChange = { p = p.copy(notes = it) },
            label = { Text(tr("Notes")) }, modifier = Modifier.fillMaxWidth(), minLines = 2,
        )
        msg?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }

        Button(onClick = {
            PlanStore.upsert(savedPlan())
            onDone()
        }, Modifier.fillMaxWidth().height(52.dp)) { Text(tr("Save plan")) }
        OutlinedButton(onClick = {
            val saved = savedPlan()
            PlanStore.upsert(saved)
            Store.upsert(PlanCalc.toTrip(saved, Store.newId()))
            msg = tr("Saved as a trip in Trips. Edit it there after the trip and add the invoice to compare.")
        }, Modifier.fillMaxWidth()) { Text(tr("Save as a real trip")) }
        OutlinedButton(onClick = {
            val copy = p.copy(id = Store.newId(), name = p.name + " (2)", steps = p.steps.map { it.copy(id = Store.newId()) })
            PlanStore.upsert(copy)
            p = copy
            msg = tr("Copy made.")
        }, Modifier.fillMaxWidth()) { Text(tr("Duplicate plan")) }
        if (p.id != 0L) {
            OutlinedButton(
                onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(tr("Delete plan")) }
        }
        OutlinedButton(onClick = onDone, Modifier.fillMaxWidth()) { Text(tr("Back")) }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Delete this plan?")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    PlanStore.delete(p.id)
                    onDone()
                }) { Text(tr("Delete")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Keep")) } },
        )
    }
}

@Composable
private fun StepEditor(
    st: PlanStep, at: Long, value: Double,
    onChange: (PlanStep) -> Unit, onUp: () -> Unit, onDown: () -> Unit, onDelete: () -> Unit,
) {
    if (st.kind == StepKind.EXTRA) {
        val e = com.qcenaman.francesinha.model.Extra(st.id, st.extraType, at, st.euros, st.note, st.name, st.percent, st.deduct, st.refunded)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.time(at), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onUp) { Text("▲") }
                TextButton(onClick = onDown) { Text("▼") }
            }
            ExtraEditor(
                e = e, value = value, showTime = false,
                onChange = { n ->
                    onChange(st.copy(extraType = n.type, euros = n.euros, note = n.note, name = n.name, percent = n.percent, deduct = n.deduct, refunded = n.refunded))
                },
                onDelete = onDelete,
            )
        }
        return
    }
    var menu by remember { mutableStateOf(false) }
    var hours by remember(st.id) { mutableStateOf(PlanCalc.hoursText(st.minutes)) }
    var km by remember(st.id) { mutableStateOf(if (st.km == 0.0) "" else Fmt.num(st.km)) }
    var eur by remember(st.id) { mutableStateOf(if (st.euros == 0.0) "" else money(st.euros)) }
    val color = when (st.kind) {
        StepKind.DRIVE -> MaterialTheme.colorScheme.surfaceVariant
        StepKind.PAUSE -> MaterialTheme.colorScheme.secondaryContainer
        StepKind.EXTRA -> MaterialTheme.colorScheme.primaryContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = when (st.kind) {
                    StepKind.DRIVE -> tr("Drive")
                    StepKind.PAUSE -> tr(st.pauseType.label) + " ▾"
                    StepKind.EXTRA -> tr(st.extraType.label) + " ▾"
                }
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { if (st.kind != StepKind.DRIVE) menu = true }) {
                        Text(Fmt.time(at) + "  " + title, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (st.kind == StepKind.PAUSE) {
                            PauseType.entries.forEach { t ->
                                DropdownMenuItem(text = { Text(tr(t.label)) }, onClick = { menu = false; onChange(st.copy(pauseType = t)) })
                            }
                        } else {
                            ExtraType.entries.forEach { t ->
                                DropdownMenuItem(text = { Text(tr(t.label)) }, onClick = { menu = false; onChange(st.copy(extraType = t)) })
                            }
                        }
                    }
                }
                TextButton(onClick = onUp) { Text("▲") }
                TextButton(onClick = onDown) { Text("▼") }
                TextButton(onClick = onDelete) { Text("✕", color = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (st.kind != StepKind.EXTRA) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = {
                            hours = it
                            PlanCalc.parseHours(it)?.let { m -> onChange(st.copy(minutes = m.coerceAtLeast(0))) }
                        },
                        label = { Text(tr("Hours (e.g. 4:30)")) }, modifier = Modifier.weight(1f), singleLine = true,
                    )
                }
                if (st.kind == StepKind.DRIVE) {
                    OutlinedTextField(
                        value = km,
                        onValueChange = {
                            km = it
                            onChange(st.copy(km = parseNum(it) ?: 0.0))
                        },
                        label = { Text("km") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (st.kind == StepKind.EXTRA) {
                    OutlinedTextField(
                        value = eur,
                        onValueChange = {
                            eur = it
                            onChange(st.copy(euros = parseNum(it) ?: 0.0))
                        },
                        label = { Text(if (st.extraType.sign < 0) tr("€ deducted") else tr("€ pay")) },
                        modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        }
    }
}
