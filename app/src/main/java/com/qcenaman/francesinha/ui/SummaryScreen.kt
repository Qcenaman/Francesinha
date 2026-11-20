package com.qcenaman.francesinha.ui

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
import java.io.File

@Composable
fun SummaryScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val sessions by Store.sessions.collectAsState()
    val now = rememberNow(30_000)
    var period by remember { mutableStateOf(Period.WEEK) }
    val (from, to) = period.range(now)
    val list = Stats.inRange(sessions, from, to)
    val t = Stats.totals(list, now)
    val days = list.groupBy { Fmt.isoDate(it.start) }.toSortedMap(compareByDescending { it })

    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { p ->
                    FilterChip(selected = p == period, onClick = { period = p }, label = { Text(p.label) })
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    StatRow(
                        "Driving" to Fmt.duration(t.drivingMs),
                        "On duty" to Fmt.duration(t.totalMs),
                        "Distance" to Fmt.km(t.km),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("${t.sessions} trips · stops ${Fmt.duration(t.pauseMs)}", style = MaterialTheme.typography.bodyMedium)
                    t.pauseByType.forEach { (type, ms) ->
                        Text("• ${type.label}: ${Fmt.duration(ms)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    StatRow(
                        "Paid hours" to Fmt.duration(t.paidMs),
                        "Hours pay" to Fmt.eur(t.hourPay),
                        "Total pay" to Fmt.eur(t.euros),
                    )
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        "Meals" to t.meals.toString(),
                        "Sleepovers" to t.sleepovers.toString(),
                        "Extras" to Fmt.eur(t.mealEuros + t.sleepoverEuros),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Meals ${Fmt.eur(t.mealEuros)} · sleepovers ${Fmt.eur(t.sleepoverEuros)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                        .putExtra(Intent.EXTRA_SUBJECT, "Driving log – ${period.label}")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share driving log"))
                } catch (_: Exception) {
                }
            }, Modifier.fillMaxWidth(), enabled = list.isNotEmpty()) { Text("Export / share as spreadsheet (CSV)") }
        }
        if (days.isNotEmpty()) item { Text("By day", style = MaterialTheme.typography.titleMedium) }
        items(days.entries.toList(), key = { it.key }) { (_, daySessions) ->
            val d = Stats.totals(daySessions, now)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(Fmt.date(daySessions.first().start), fontWeight = FontWeight.Bold)
                    Text(
                        "Driving ${Fmt.duration(d.drivingMs)} · on duty ${Fmt.duration(d.totalMs)} · ${Fmt.km(d.km)}" +
                            "\nPay ${Fmt.eur(d.euros)}" +
                            if (d.meals + d.sleepovers > 0) " · ${d.meals} meals · ${d.sleepovers} sleepovers" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
