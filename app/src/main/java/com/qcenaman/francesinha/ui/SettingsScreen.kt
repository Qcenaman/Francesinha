package com.qcenaman.francesinha.ui

import com.qcenaman.francesinha.logic.TaxCalc
import com.qcenaman.francesinha.logic.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qcenaman.francesinha.data.Config
import com.qcenaman.francesinha.data.Prefs
import com.qcenaman.francesinha.logic.Fmt
import com.qcenaman.francesinha.service.TrackingService
import com.qcenaman.francesinha.model.ExtraType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun SettingsScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    var c by remember { mutableStateOf(Prefs.load(ctx)) }
    var limitAlerts by remember { mutableStateOf(Prefs.limitAlerts(ctx)) }
    fun set(n: Config) {
        c = n
        Prefs.save(ctx, n)
        TrackingService.refresh(ctx)
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(tr("Language"), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.qcenaman.francesinha.logic.Lang.entries.forEach { l ->
                        androidx.compose.material3.FilterChip(
                            selected = UiLang.lang == l,
                            onClick = {
                                Prefs.setLang(ctx, l)
                                UiLang.lang = l
                                TrackingService.refresh(ctx)
                            },
                            label = { Text(l.label.substringBefore(" (")) },
                        )
                    }
                }
            }
        }
        ToggleRow(tr("Automatic trips"), tr("Start, pause and end trips from GPS"), c.autoDetect) { set(c.copy(autoDetect = it)) }
        Stepper(tr("Trip starts above"), "${c.startSpeedKmh} km/h", { set(c.copy(startSpeedKmh = (c.startSpeedKmh - 1).coerceAtLeast(5))) }) {
            set(c.copy(startSpeedKmh = (c.startSpeedKmh + 1).coerceAtMost(40)))
        }
        Stepper(tr("Stopped this long = break"), "${c.breakAfterMin} min", { set(c.copy(breakAfterMin = (c.breakAfterMin - 1).coerceAtLeast(1))) }) {
            set(c.copy(breakAfterMin = (c.breakAfterMin + 1).coerceAtMost(60)))
        }
        Stepper(tr("Stopped this long = trip ends"), Fmt.duration(c.endAfterMin * 60_000L), {
            set(c.copy(endAfterMin = (c.endAfterMin - 15).coerceAtLeast(15)))
        }) { set(c.copy(endAfterMin = (c.endAfterMin + 15).coerceAtMost(12 * 60))) }
        ToggleRow(tr("Break reminder"), tr("Alert after long driving without a proper break (15 min+)"), c.alertsOn) {
            set(c.copy(alertsOn = it))
        }
        Stepper(tr("Remind after driving"), Fmt.duration(c.driveAlertMin * 60_000L), {
            set(c.copy(driveAlertMin = (c.driveAlertMin - 15).coerceAtLeast(30)))
        }) { set(c.copy(driveAlertMin = (c.driveAlertMin + 15).coerceAtMost(10 * 60))) }
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Base salary (€ per month)"), fontWeight = FontWeight.Bold)
                Text(tr("Hours, overtime, meals, sleepovers and extras are added on top of it."), style = MaterialTheme.typography.bodySmall)
                var base by remember {
                    mutableStateOf(Prefs.baseSalary(ctx)?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "")
                }
                OutlinedTextField(
                    value = base,
                    onValueChange = {
                        base = it
                        Prefs.setBaseSalary(ctx, parseNum(it))
                    },
                    label = { Text(tr("Base salary per month")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(tr("Default pay per hour (€)"), fontWeight = FontWeight.Bold)
                Text(tr("Filled in on each new trip; change it on a trip when a job pays differently."), style = MaterialTheme.typography.bodySmall)
                var r by remember { mutableStateOf(Prefs.rate(ctx)?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "") }
                OutlinedTextField(
                    value = r,
                    onValueChange = {
                        r = it
                        Prefs.setRate(ctx, parseNum(it))
                    },
                    label = { Text(tr("€ per hour")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                var otA by remember { mutableStateOf(Prefs.overtimeAfterMin(ctx)?.let { Fmt.num(it / 60.0) } ?: "") }
                var otR by remember {
                    mutableStateOf(Prefs.overtimeRate(ctx)?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "")
                }
                OutlinedTextField(
                    value = otA,
                    onValueChange = {
                        otA = it
                        Prefs.setOvertimeAfterMin(ctx, parseNum(it)?.takeIf { h -> h > 0 }?.let { h -> (h * 60).toInt() })
                    },
                    label = { Text(tr("Overtime after this many paid hours (empty = no overtime)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = otR,
                    onValueChange = {
                        otR = it
                        Prefs.setOvertimeRate(ctx, parseNum(it))
                    },
                    label = { Text(tr("Overtime € per hour (empty = same as normal)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Default values (€)"), fontWeight = FontWeight.Bold)
                Text(tr("Used when you add one; each can still be changed on the trip."), style = MaterialTheme.typography.bodySmall)
                ExtraType.entries.filter { it != ExtraType.CUSTOM && it != ExtraType.EXPENSE }.forEach { t ->
                    var v by remember(t) {
                        val cur = Prefs.euros(ctx, t)
                        mutableStateOf(if (cur == 0.0) "" else String.format(java.util.Locale.US, "%.2f", cur))
                    }
                    OutlinedTextField(
                        value = v,
                        onValueChange = {
                            v = it
                            Prefs.setEuros(ctx, t, parseNum(it) ?: 0.0)
                        },
                        label = { Text(tr(t.label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        }
        RouteCard()
        PayRulesCard()
        TaxesCard()
        PresetsCard()
        ToggleRow(tr("Driving limit alerts (EU)"), tr("Warn at 9h/10h a day, 56h a week, 90h in two weeks"), limitAlerts) {
            limitAlerts = it
            Prefs.setLimitAlerts(ctx, it)
        }
        BackupCard()
        Text(
            tr("Tip: if trips stop recording with the screen off, allow the app in the phone's battery settings ") +
                tr("(no restrictions / auto-start)."),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = value, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun Stepper(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onMinus) { Text("−") }
            Text(value, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
            FilledTonalButton(onClick = onPlus) { Text("+") }
        }
    }
}

@Composable
private fun MoneyField(label: String, initial: Double?, modifier: Modifier = Modifier.fillMaxWidth(), digits: Int = 2, onSet: (Double?) -> Unit) {
    var v by remember { mutableStateOf(initial?.let { String.format(java.util.Locale.US, "%.${digits}f", it) } ?: "") }
    OutlinedTextField(
        value = v,
        onValueChange = {
            v = it
            onSet(parseNum(it))
        },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun PayRulesCard() {
    val ctx = LocalContext.current
    var from by remember { mutableStateOf(Prefs.nightFromHour(ctx).toString()) }
    var to by remember { mutableStateOf(Prefs.nightToHour(ctx).toString()) }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("More pay rules (defaults for new trips)"), fontWeight = FontWeight.Bold)
            Text(tr("Leave empty if not used. Pay per hour stays the main rate."), style = MaterialTheme.typography.bodySmall)
            MoneyField(tr("Pay per km (€)"), Prefs.num(ctx, "perKm"), digits = 3) { Prefs.setNum(ctx, "perKm", it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(tr("Night +%"), Prefs.num(ctx, "nightPct"), Modifier.weight(1f), 1) { Prefs.setNum(ctx, "nightPct", it) }
                OutlinedTextField(
                    value = from, onValueChange = {
                        from = it
                        it.toIntOrNull()?.let { h -> Prefs.setNightHours(ctx, h, Prefs.nightToHour(ctx)) }
                    },
                    label = { Text(tr("From (h)")) }, singleLine = true, modifier = Modifier.weight(0.6f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = to, onValueChange = {
                        to = it
                        it.toIntOrNull()?.let { h -> Prefs.setNightHours(ctx, Prefs.nightFromHour(ctx), h) }
                    },
                    label = { Text(tr("To (h)")) }, singleLine = true, modifier = Modifier.weight(0.6f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(tr("Weekend +%"), Prefs.num(ctx, "weekendPct"), Modifier.weight(1f), 1) { Prefs.setNum(ctx, "weekendPct", it) }
                MoneyField(tr("Holiday +%"), Prefs.num(ctx, "holidayPct"), Modifier.weight(1f), 1) { Prefs.setNum(ctx, "holidayPct", it) }
            }
        }
    }
}

@Composable
private fun TaxesCard() {
    val ctx = LocalContext.current
    var taxes by remember { mutableStateOf(Prefs.taxes(ctx)) }
    var free by remember { mutableStateOf(Prefs.mealsTaxFree(ctx)) }
    var ver by remember { mutableStateOf(0) }
    fun save(n: List<com.qcenaman.francesinha.model.Tax>) {
        taxes = n
        Prefs.setTaxes(ctx, n)
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Taxes"), fontWeight = FontWeight.Bold)
            Text(tr("Taken from the gross pay to show the net amount."), style = MaterialTheme.typography.bodySmall)
            taxes.forEachIndexed { i, tx ->
                androidx.compose.runtime.key(ver, i) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        var name by remember { mutableStateOf(tx.name) }
                        var pct by remember { mutableStateOf(Fmt.num(tx.percent)) }
                        OutlinedTextField(
                            value = name, onValueChange = {
                                name = it
                                save(taxes.mapIndexed { j, x -> if (j == i) x.copy(name = it) else x })
                            },
                            label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.weight(1.4f),
                        )
                        OutlinedTextField(
                            value = pct, onValueChange = {
                                pct = it
                                save(taxes.mapIndexed { j, x -> if (j == i) x.copy(percent = parseNum(it) ?: 0.0) else x })
                            },
                            label = { Text("%") }, singleLine = true, modifier = Modifier.weight(0.8f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        androidx.compose.material3.TextButton(onClick = {
                            save(taxes.filterIndexed { j, _ -> j != i })
                            ver++
                        }) { Text("✕", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            FilledTonalButton(onClick = {
                save(taxes + com.qcenaman.francesinha.model.Tax(tr("Tax"), 0.0))
                ver++
            }) { Text(tr("+ Add tax")) }
            Text(tr("Total: %s%%", Fmt.num(TaxCalc.percent(taxes))), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Meals and sleepovers are tax-free"), modifier = Modifier.weight(1f))
                Switch(checked = free, onCheckedChange = {
                    free = it
                    Prefs.setMealsTaxFree(ctx, it)
                })
            }
        }
    }
}

@Composable
private fun PresetsCard() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(Prefs.presets(ctx)) }
    var ver by remember { mutableStateOf(0) }
    fun save(n: List<com.qcenaman.francesinha.model.CustomPreset>) {
        list = n
        Prefs.setPresets(ctx, n)
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Custom items"), fontWeight = FontWeight.Bold)
            Text(tr("Your own additions or deductions, fixed € or a % of the work pay. They appear in the add menus."), style = MaterialTheme.typography.bodySmall)
            list.forEachIndexed { i, p ->
                androidx.compose.runtime.key(ver, i) {
                    Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            var name by remember { mutableStateOf(p.name) }
                            var v by remember { mutableStateOf(if (p.percent != null) Fmt.num(p.percent) else money(p.euros)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = name, onValueChange = {
                                        name = it
                                        save(list.mapIndexed { j, x -> if (j == i) x.copy(name = it) else x })
                                    },
                                    label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.weight(1.4f),
                                )
                                OutlinedTextField(
                                    value = v, onValueChange = {
                                        v = it
                                        val n = parseNum(it) ?: 0.0
                                        save(list.mapIndexed { j, x -> if (j == i) (if (x.percent != null) x.copy(percent = n) else x.copy(euros = n)) else x })
                                    },
                                    label = { Text(if (p.percent != null) "%" else "€") }, singleLine = true, modifier = Modifier.weight(0.8f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                                androidx.compose.material3.TextButton(onClick = {
                                    save(list.filterIndexed { j, _ -> j != i })
                                    ver++
                                }) { Text("✕", color = MaterialTheme.colorScheme.error) }
                            }
                            SwitchRow(tr("Percentage instead of a fixed amount"), p.percent != null) { on ->
                                val n = parseNum(v) ?: 0.0
                                save(list.mapIndexed { j, x -> if (j == i) x.copy(percent = if (on) n else null, euros = if (on) 0.0 else n) else x })
                                ver++
                            }
                            SwitchRow(tr("Deduct (instead of add)"), p.deduct) { on ->
                                save(list.mapIndexed { j, x -> if (j == i) x.copy(deduct = on) else x })
                                ver++
                            }
                        }
                    }
                }
            }
            FilledTonalButton(onClick = {
                save(list + com.qcenaman.francesinha.model.CustomPreset(tr("Custom"), 0.0))
                ver++
            }) { Text(tr("+ Add custom item")) }
        }
    }
}

@Composable
private fun BackupCard() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> pending = uri }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Backup"), fontWeight = FontWeight.Bold)
            Text(
                tr("Save everything (trips, plans, settings, photos) in one file and keep it off the phone (email, Drive…)."),
                style = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(onClick = {
                try {
                    val f = com.qcenaman.francesinha.data.Backup.export(ctx)
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("application/zip")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(android.content.Intent.createChooser(send, tr("Save backup")))
                } catch (_: Exception) {
                    msg = tr("Backup failed.")
                }
            }, Modifier.fillMaxWidth()) { Text(tr("Make a backup")) }
            androidx.compose.material3.OutlinedButton(onClick = {
                try {
                    pick.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                } catch (_: Exception) {
                }
            }, Modifier.fillMaxWidth()) { Text(tr("Restore a backup")) }
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    pending?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(tr("Restore this backup?")) },
            text = { Text(tr("Everything on this phone will be replaced by the backup.")) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pending = null
                    val n = com.qcenaman.francesinha.data.Backup.restore(ctx, uri)
                    msg = if (n == null) tr("That file isn't a Francesinha backup.") else tr("Restored %d trips. Reopen the app to see every setting.", n)
                    TrackingService.refresh(ctx)
                }) { Text(tr("Restore")) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pending = null }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun RouteCard() {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(Prefs.orsKey(ctx)) }
    var hazmat by remember { mutableStateOf(Prefs.truck(ctx).hazmat) }
    val truck = Prefs.truck(ctx)
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Truck and route planning"), fontWeight = FontWeight.Bold)
            Text(
                tr("Routes avoid roads the truck can't use (height, weight, no-truck roads) where OpenStreetMap has that data. Always follow the road signs."),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(tr("Height (m)"), truck.height, Modifier.weight(1f), 2) { Prefs.setNum(ctx, "truckHeight", it) }
                MoneyField(tr("Width (m)"), truck.width, Modifier.weight(1f), 2) { Prefs.setNum(ctx, "truckWidth", it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(tr("Length (m)"), truck.length, Modifier.weight(1f), 2) { Prefs.setNum(ctx, "truckLength", it) }
                MoneyField(tr("Weight (t)"), truck.weight, Modifier.weight(1f), 1) { Prefs.setNum(ctx, "truckWeight", it) }
            }
            MoneyField(tr("Axle load (t)"), truck.axleLoad, Modifier.fillMaxWidth(), 1) { Prefs.setNum(ctx, "truckAxle", it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Dangerous goods (ADR)"), modifier = Modifier.weight(1f))
                Switch(checked = hazmat, onCheckedChange = {
                    hazmat = it
                    Prefs.setHazmat(ctx, it)
                })
            }
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    Prefs.setOrsKey(ctx, it)
                },
                label = { Text(tr("Free route key (openrouteservice)")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                tr("Get it free at account.heigit.org (sign up → API keys → Standard). Up to 2,000 routes a day."),
                style = MaterialTheme.typography.bodySmall,
            )
            androidx.compose.material3.TextButton(onClick = {
                try {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://account.heigit.org")))
                } catch (_: Exception) {
                }
            }) { Text(tr("Open account.heigit.org")) }
        }
    }
}
