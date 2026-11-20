package com.qcenaman.francesinha.ui

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
    fun set(n: Config) {
        c = n
        Prefs.save(ctx, n)
        TrackingService.refresh(ctx)
    }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToggleRow("Automatic trips", "Start, pause and end trips from GPS", c.autoDetect) { set(c.copy(autoDetect = it)) }
        Stepper("Trip starts above", "${c.startSpeedKmh} km/h", { set(c.copy(startSpeedKmh = (c.startSpeedKmh - 1).coerceAtLeast(5))) }) {
            set(c.copy(startSpeedKmh = (c.startSpeedKmh + 1).coerceAtMost(40)))
        }
        Stepper("Stopped this long = break", "${c.breakAfterMin} min", { set(c.copy(breakAfterMin = (c.breakAfterMin - 1).coerceAtLeast(1))) }) {
            set(c.copy(breakAfterMin = (c.breakAfterMin + 1).coerceAtMost(60)))
        }
        Stepper("Stopped this long = trip ends", Fmt.duration(c.endAfterMin * 60_000L), {
            set(c.copy(endAfterMin = (c.endAfterMin - 15).coerceAtLeast(15)))
        }) { set(c.copy(endAfterMin = (c.endAfterMin + 15).coerceAtMost(12 * 60))) }
        ToggleRow("Break reminder", "Alert after long driving without a proper break (15 min+)", c.alertsOn) {
            set(c.copy(alertsOn = it))
        }
        Stepper("Remind after driving", Fmt.duration(c.driveAlertMin * 60_000L), {
            set(c.copy(driveAlertMin = (c.driveAlertMin - 15).coerceAtLeast(30)))
        }) { set(c.copy(driveAlertMin = (c.driveAlertMin + 15).coerceAtMost(10 * 60))) }
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Default pay per hour (€)", fontWeight = FontWeight.Bold)
                Text("Filled in on each new trip; change it on a trip when a job pays differently.", style = MaterialTheme.typography.bodySmall)
                var r by remember { mutableStateOf(Prefs.rate(ctx)?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "") }
                OutlinedTextField(
                    value = r,
                    onValueChange = {
                        r = it
                        Prefs.setRate(ctx, parseNum(it))
                    },
                    label = { Text("€ per hour") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pay per meal / sleepover (€)", fontWeight = FontWeight.Bold)
                Text("Used when you add one; each can still be changed on the trip.", style = MaterialTheme.typography.bodySmall)
                ExtraType.entries.forEach { t ->
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
                        label = { Text(t.label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        }
        Text(
            "Tip: if trips stop recording with the screen off, allow the app in the phone's battery settings " +
                "(no restrictions / auto-start).",
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
