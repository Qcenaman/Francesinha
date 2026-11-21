package com.qcenaman.francesinha.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qcenaman.francesinha.data.Prefs
import com.qcenaman.francesinha.data.Store
import com.qcenaman.francesinha.logic.Fmt
import com.qcenaman.francesinha.logic.Period
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.logic.TaxCalc
import com.qcenaman.francesinha.logic.tr
import com.qcenaman.francesinha.model.Session

@Composable
fun HomeScreen(modifier: Modifier, onEdit: (Long) -> Unit) {
    val ctx = LocalContext.current
    val sessions by Store.sessions.collectAsState()
    val now = rememberNow(30_000)
    val mealsFree = Prefs.mealsTaxFree(ctx)
    val taxes = Prefs.taxes(ctx)
    val (mFrom, mTo) = Period.MONTH.range(now)
    val month = Stats.totals(Stats.inRange(sessions, mFrom, mTo), now, mealsFree)
    val (wFrom, wTo) = Period.WEEK.range(now)
    val week = Stats.totals(Stats.inRange(sessions, wFrom, wTo), now, mealsFree)
    val base = Prefs.baseSalary(ctx) ?: 0.0

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(tr("Owed this month"), fontWeight = FontWeight.Bold)
                    val gross = base + month.euros
                    Text(Fmt.eur(gross), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (base > 0) Text(tr("Base salary %s + trips %s", Fmt.eur(base), Fmt.eur(month.euros)), style = MaterialTheme.typography.bodySmall)
                    if (TaxCalc.percent(taxes) > 0) {
                        Text(
                            tr("Net: %s", Fmt.eur(TaxCalc.net(gross, base + month.taxable, taxes))),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        tr("Trips") to month.sessions.toString(),
                        tr("Paid hours") to Fmt.duration(month.paidMs),
                        tr("Distance") to Fmt.km(month.km),
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(tr("This week"), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        tr("Paid hours") to Fmt.duration(week.paidMs),
                        tr("Distance") to Fmt.km(week.km),
                        tr("Pay") to Fmt.eur(week.euros),
                    )
                }
            }
        }
        item {
            Button(onClick = { onEdit(0L) }, Modifier.fillMaxWidth().height(56.dp)) { Text(tr("+ New trip")) }
        }
        if (sessions.isEmpty()) {
            item {
                Text(
                    tr("No trips yet. Tap + New trip and add the times, breaks, meals and sleepovers."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(sessions, key = { it.id }) { s -> SessionRow(s, now) { onEdit(s.id) } }
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
                    tr(if (breaks == 1) "%d stop (%s)" else "%d stops (%s)", breaks, Fmt.duration(Stats.pauseMs(s, now))),
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
