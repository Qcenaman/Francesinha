package com.qcenaman.francesinha.ui

import com.qcenaman.francesinha.logic.tr
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.qcenaman.francesinha.data.Prefs
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.qcenaman.francesinha.data.Store
import com.qcenaman.francesinha.logic.Csv
import com.qcenaman.francesinha.logic.Fmt
import com.qcenaman.francesinha.logic.Period
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.logic.TaxCalc
import java.io.File

@Composable
fun SummaryScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val sessions by Store.sessions.collectAsState()
    val now = rememberNow(30_000)
    var period by remember { mutableStateOf(Period.WEEK) }
    val (from, to) = period.range(now)
    val list = Stats.inRange(sessions, from, to)
    val mealsFree = Prefs.mealsTaxFree(ctx)
    val taxes = Prefs.taxes(ctx)
    val t = Stats.totals(list, now, mealsFree)
    val days = list.groupBy { Fmt.isoDate(it.start) }.toSortedMap(compareByDescending { it })

    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { p ->
                    FilterChip(selected = p == period, onClick = { period = p }, label = { Text(tr(p.label)) })
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    StatRow(
                        tr("Driving") to Fmt.duration(t.drivingMs),
                        tr("On duty") to Fmt.duration(t.totalMs),
                        tr("Distance") to Fmt.km(t.km),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(tr("%d trips · stops %s", t.sessions, Fmt.duration(t.pauseMs)), style = MaterialTheme.typography.bodyMedium)
                    t.pauseByType.forEach { (type, ms) ->
                        Text("• " + tr(type.label) + ": " + Fmt.duration(ms), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    StatRow(
                        tr("Paid hours") to Fmt.duration(t.paidMs),
                        tr("Hours pay") to Fmt.eur(t.hourPay),
                        tr("Overtime") to Fmt.eur(t.overtimePay),
                    )
                    if (t.overtimeMs > 0) {
                        Text(tr("Overtime hours: %s", Fmt.duration(t.overtimeMs)), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        tr("Meals") to t.meals.toString(),
                        tr("Sleepovers") to t.sleepovers.toString(),
                        tr("Extras") to Fmt.eur(t.adjustEuros),
                    )
                    if (t.supplementPay > 0) {
                        Spacer(Modifier.height(8.dp))
                        StatRow(
                            tr("Night/weekend") to Fmt.eur(t.supplementPay),
                            tr("Night hours") to Fmt.duration(t.nightMs),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("Meals %s · sleepovers %s", Fmt.eur(t.mealEuros), Fmt.eur(t.sleepoverEuros)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (t.expenseEuros > 0) {
                        Text(
                            tr("Expenses %s (refunded %s)", Fmt.eur(t.expenseEuros), Fmt.eur(t.refundedEuros)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(tr("Expected total: %s", Fmt.eur(t.euros)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (TaxCalc.percent(taxes) > 0) {
                        Text(
                            tr("After tax ≈ %s (taxes %s)", Fmt.eur(TaxCalc.net(t.euros, t.taxable, taxes)), Fmt.eur(TaxCalc.amount(t.taxable, taxes))),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (t.invoicedTrips > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr("Invoiced (%d trips): %s vs expected %s", t.invoicedTrips, Fmt.eur(t.invoiced), Fmt.eur(t.invoicedExpected)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            tr("Difference: %s", Fmt.eurDiff(t.invoiceDiff)),
                            fontWeight = FontWeight.Bold,
                            color = if (t.invoiceDiff < -0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
        item {
            Button(onClick = {
                try {
                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                    val f = File(dir, "francesinha-${period.name.lowercase()}-${Fmt.isoDate(now)}.csv")
                    f.writeText(Csv.export(list, now))
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/csv")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, tr("Driving log – %s", tr(period.label)))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, tr("Share driving log")))
                } catch (_: Exception) {
                }
            }, Modifier.fillMaxWidth(), enabled = list.isNotEmpty()) { Text(tr("Export / share as spreadsheet (CSV)")) }
        }
        if (period == Period.MONTH || period == Period.LAST_MONTH) {
            item(key = "month-" + Fmt.isoDate(from).take(7)) { MonthPayCard(Fmt.isoDate(from).take(7), t.euros, t.taxable) }
        }
        if (days.isNotEmpty()) item { Text(tr("By day"), style = MaterialTheme.typography.titleMedium) }
        items(days.entries.toList(), key = { it.key }) { (_, daySessions) ->
            val d = Stats.totals(daySessions, now)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(Fmt.date(daySessions.first().start), fontWeight = FontWeight.Bold)
                    Text(
                        tr("Driving %s · on duty %s · %s", Fmt.duration(d.drivingMs), Fmt.duration(d.totalMs), Fmt.km(d.km)) +
                            "\n" + tr("Pay %s", Fmt.eur(d.euros)) +
                            if (d.meals + d.sleepovers > 0) " · " + tr("%d meals · %d sleepovers", d.meals, d.sleepovers) else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthPayCard(month: String, earned: Double, earnedTaxable: Double) {
    val ctx = LocalContext.current
    val base = Prefs.baseSalary(ctx) ?: 0.0
    val taxes = Prefs.taxes(ctx)
    var slip by remember(month) { mutableStateOf(Prefs.payslip(ctx, month)?.let { money(it) } ?: "") }
    val gross = base + earned
    val tax = TaxCalc.amount(base + earnedTaxable, taxes)
    val net = gross - tax
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Month pay"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(tr("Base salary %s + trips %s", Fmt.eur(base), Fmt.eur(earned)))
            Text(tr("Gross: %s", Fmt.eur(gross)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            taxes.filter { it.percent > 0 }.forEach { tx ->
                Text("− ${tx.name} ${Fmt.num(tx.percent)}%: ${Fmt.eur((base + earnedTaxable).coerceAtLeast(0.0) * tx.percent / 100)}", style = MaterialTheme.typography.bodySmall)
            }
            Text(tr("Net: %s", Fmt.eur(net)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = slip,
                onValueChange = {
                    slip = it
                    Prefs.setPayslip(ctx, month, parseNum(it))
                },
                label = { Text(tr("Payslip / invoice received for %s (€)", month)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            parseNum(slip)?.let { got ->
                val d = got - net
                Text(
                    when {
                        d > 0.005 -> tr("Received %s more than expected", Fmt.eur(d))
                        d < -0.005 -> tr("Received %s LESS than expected", Fmt.eur(-d))
                        else -> tr("Matches what was expected")
                    } + " " + tr("(vs net)"),
                    fontWeight = FontWeight.Bold,
                    color = if (d < -0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
                Text(tr("vs gross: %s", Fmt.eurDiff(got - gross)), style = MaterialTheme.typography.bodySmall)
            }
            if (base == 0.0) Text(tr("Set the base salary in Settings."), style = MaterialTheme.typography.bodySmall)
        }
    }
}
