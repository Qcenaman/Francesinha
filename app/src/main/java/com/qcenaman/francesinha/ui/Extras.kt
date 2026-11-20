package com.qcenaman.francesinha.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.qcenaman.francesinha.data.Prefs
import com.qcenaman.francesinha.data.Store
import com.qcenaman.francesinha.logic.Fmt
import com.qcenaman.francesinha.logic.tr
import com.qcenaman.francesinha.model.Extra
import com.qcenaman.francesinha.model.ExtraType

/** Something that can be added to a trip or plan, ready-made. */
data class ExtraChoice(val label: String, val make: (Long) -> Extra)

fun extraChoices(ctx: Context, types: List<ExtraType> = ExtraType.entries): List<ExtraChoice> {
    val out = mutableListOf<ExtraChoice>()
    for (t in types) {
        out += ExtraChoice(tr(t.label)) { time -> Extra(Store.newId(), t, time, Prefs.euros(ctx, t)) }
    }
    if (ExtraType.CUSTOM in types) {
        for (p in Prefs.presets(ctx)) {
            val v = if (p.percent != null) Fmt.num(p.percent) + "%" else Fmt.eur(p.euros)
            out += ExtraChoice("★ ${p.name} (${if (p.deduct) "−" else "+"}$v)") { time ->
                Extra(Store.newId(), ExtraType.CUSTOM, time, p.euros, name = p.name, percent = p.percent, deduct = p.deduct)
            }
        }
    }
    return out
}

@Composable
fun AddExtraMenu(label: String, choices: List<ExtraChoice>, modifier: Modifier = Modifier, enabled: Boolean = true, onPick: (ExtraChoice) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }, enabled = enabled) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { c ->
                DropdownMenuItem(text = { Text(c.label) }, onClick = {
                    open = false
                    onPick(c)
                })
            }
        }
    }
}

/** Fields for one meal / sleepover / prize / custom item / expense. */
@Composable
fun ExtraEditor(
    e: Extra,
    value: Double,
    onChange: (Extra) -> Unit,
    onDelete: () -> Unit,
    showTime: Boolean = true,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val isPct = e.type == ExtraType.CUSTOM && e.percent != null
    var eur by remember(e.id, isPct) {
        mutableStateOf(
            if (isPct) Fmt.num(e.percent ?: 0.0) else if (e.euros == 0.0) "" else money(e.euros),
        )
    }
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { menu = true }) { Text(tr(e.type.label) + " ▾") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ExtraType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(tr(t.label)) }, onClick = {
                                menu = false
                                onChange(e.copy(type = t, percent = if (t == ExtraType.CUSTOM) e.percent else null))
                            })
                        }
                    }
                }
                Text(Fmt.eur(value), fontWeight = FontWeight.Bold)
                TextButton(onClick = onDelete) { Text(tr("Remove"), color = MaterialTheme.colorScheme.error) }
            }
            if (e.type == ExtraType.CUSTOM || e.type == ExtraType.EXPENSE) {
                OutlinedTextField(
                    value = e.name, onValueChange = { onChange(e.copy(name = it)) },
                    label = { Text(tr("Name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showTime) {
                    OutlinedButton(onClick = { pickDateTime(ctx, e.time) { onChange(e.copy(time = it)) } }, Modifier.weight(1f)) {
                        Text(Fmt.dateTime(e.time))
                    }
                }
                OutlinedTextField(
                    value = eur,
                    onValueChange = {
                        eur = it
                        val n = parseNum(it) ?: 0.0
                        onChange(if (isPct) e.copy(percent = n) else e.copy(euros = n))
                    },
                    label = {
                        Text(
                            when {
                                isPct -> tr("% of work pay")
                                e.type.sign < 0 || (e.type == ExtraType.CUSTOM && e.deduct) -> tr("€ deducted")
                                e.type == ExtraType.EXPENSE -> tr("€ spent")
                                else -> tr("€ pay")
                            },
                        )
                    },
                    modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            if (e.type == ExtraType.CUSTOM) {
                SwitchRow(tr("Percentage instead of a fixed amount"), isPct) { on ->
                    onChange(e.copy(percent = if (on) (parseNum(eur) ?: 0.0) else null, euros = if (on) e.euros else (parseNum(eur) ?: 0.0)))
                }
                SwitchRow(tr("Deduct (instead of add)"), e.deduct) { onChange(e.copy(deduct = it)) }
            }
            if (e.type == ExtraType.EXPENSE) {
                SwitchRow(tr("Refunded by the employer"), e.refunded) { onChange(e.copy(refunded = it)) }
            }
            OutlinedTextField(
                value = e.note, onValueChange = { onChange(e.copy(note = it)) },
                label = { Text(tr("Note (place, receipt…)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
    }
}

@Composable
fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

/** Quick entry of an expense or a custom item from the main screen. */
@Composable
fun QuickExtraDialog(type: ExtraType, onDismiss: () -> Unit, onSave: (Extra) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var pct by remember { mutableStateOf(false) }
    var deduct by remember { mutableStateOf(false) }
    var refunded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(type.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(if (type == ExtraType.EXPENSE) tr("What (toll, parking…)") else tr("Name")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text(if (pct) tr("% of work pay") else tr("Amount (€)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (type == ExtraType.CUSTOM) {
                    SwitchRow(tr("Percentage instead of a fixed amount"), pct) { pct = it }
                    SwitchRow(tr("Deduct (instead of add)"), deduct) { deduct = it }
                }
                if (type == ExtraType.EXPENSE) SwitchRow(tr("Refunded by the employer"), refunded) { refunded = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = parseNum(amount) ?: 0.0
                onSave(
                    Extra(
                        Store.newId(), type, System.currentTimeMillis(),
                        euros = if (pct) 0.0 else n, name = name.trim(),
                        percent = if (pct) n else null, deduct = deduct, refunded = refunded,
                    ),
                )
            }) { Text(tr("Add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
