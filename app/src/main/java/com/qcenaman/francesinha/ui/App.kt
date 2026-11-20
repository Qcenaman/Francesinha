@file:OptIn(ExperimentalMaterial3Api::class)

package com.qcenaman.francesinha.ui

import com.qcenaman.francesinha.logic.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** Compose-visible language so the UI redraws when it changes. */
object UiLang {
    var lang by mutableStateOf(com.qcenaman.francesinha.logic.Lang.PT)
}

sealed class Screen {
    data object Home : Screen()
    data object Planner : Screen()
    data object Summary : Screen()
    data object Settings : Screen()
    data class Edit(val id: Long) : Screen()
    data class PlanEdit(val id: Long) : Screen()
}

@Composable
fun rememberNow(periodMs: Long = 5_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return now
}

@Composable
fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen is Screen.PlanEdit) Screen.Planner else Screen.Home
    }
    val title = when (val s = screen) {
        Screen.Home -> "Francesinha"
        Screen.Planner -> tr("Planner")
        is Screen.PlanEdit -> if (s.id == 0L) tr("New plan") else tr("Edit plan")
        Screen.Summary -> tr("Summary")
        Screen.Settings -> tr("Settings")
        is Screen.Edit -> if (s.id == 0L) tr("New trip") else tr("Edit trip")
    }
    key(UiLang.lang) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            if (screen !is Screen.Edit && screen !is Screen.PlanEdit) {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.Home, onClick = { screen = Screen.Home },
                        icon = { Icon(Icons.Filled.Home, null) }, label = { Text(tr("Trips")) },
                    )
                    NavigationBarItem(
                        selected = screen == Screen.Planner, onClick = { screen = Screen.Planner },
                        icon = { Icon(Icons.Filled.Edit, null) }, label = { Text(tr("Planner")) },
                    )
                    NavigationBarItem(
                        selected = screen == Screen.Summary, onClick = { screen = Screen.Summary },
                        icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text(tr("Summary")) },
                    )
                    NavigationBarItem(
                        selected = screen == Screen.Settings, onClick = { screen = Screen.Settings },
                        icon = { Icon(Icons.Filled.Settings, null) }, label = { Text(tr("Settings")) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (val s = screen) {
            Screen.Home -> HomeScreen(m, onEdit = { screen = Screen.Edit(it) })
            Screen.Planner -> PlannerScreen(m, onEdit = { screen = Screen.PlanEdit(it) })
            is Screen.PlanEdit -> PlanEditScreen(m, s.id, onDone = { screen = Screen.Planner })
            Screen.Summary -> SummaryScreen(m)
            Screen.Settings -> SettingsScreen(m)
            is Screen.Edit -> EditScreen(m, s.id, onDone = { screen = Screen.Home })
        }
    }
    }
}
