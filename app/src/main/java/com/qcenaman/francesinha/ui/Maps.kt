package com.qcenaman.francesinha.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qcenaman.francesinha.data.LastFix
import com.qcenaman.francesinha.data.Ors
import com.qcenaman.francesinha.data.Prefs
import com.qcenaman.francesinha.data.Store
import com.qcenaman.francesinha.data.TrackStore
import com.qcenaman.francesinha.logic.Fmt
import com.qcenaman.francesinha.logic.GeoJson
import com.qcenaman.francesinha.logic.I18n
import com.qcenaman.francesinha.logic.LatLon
import com.qcenaman.francesinha.logic.MapData
import com.qcenaman.francesinha.logic.OsmAnd
import com.qcenaman.francesinha.logic.Place
import com.qcenaman.francesinha.logic.Stats
import com.qcenaman.francesinha.logic.TripMap
import com.qcenaman.francesinha.logic.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private val LINES = listOf(
    Triple("planned", "#8E8E93", 5f),
    Triple("driven", "#B5541B", 6f),
)
private val DOTS = listOf(
    Triple("stops", "#E3A500", 7f),
    Triple("extras", "#3E6838", 6f),
    Triple("start", "#1E7F2E", 9f),
    Triple("end", "#C62828", 9f),
    Triple("me", "#1565C0", 8f),
)

private fun MapData.json(id: String): String = when (id) {
    "planned" -> GeoJson.line(planned)
    "driven" -> GeoJson.line(driven)
    "stops" -> GeoJson.points(stops)
    "extras" -> GeoJson.points(extras)
    "start" -> GeoJson.points(listOfNotNull(start))
    "end" -> GeoJson.points(listOfNotNull(end))
    else -> GeoJson.points(listOfNotNull(me))
}

/** OpenStreetMap map (MapLibre + OpenFreeMap) with the trip's lines and markers. */
@Composable
fun TruckMap(data: MapData, modifier: Modifier = Modifier, interactive: Boolean = true) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var fitted by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            runCatching {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    AndroidView(
        factory = {
            mapView.getMapAsync { m ->
                m.uiSettings.setAllGesturesEnabled(interactive)
                m.setStyle(Style.Builder().fromUri(STYLE_URL)) { st ->
                    LINES.forEach { (id, color, width) ->
                        st.addSource(GeoJsonSource(id, GeoJson.line(emptyList())))
                        st.addLayer(LineLayer(id, id).withProperties(PropertyFactory.lineColor(color), PropertyFactory.lineWidth(width)))
                    }
                    DOTS.forEach { (id, color, r) ->
                        st.addSource(GeoJsonSource(id, GeoJson.points(emptyList())))
                        st.addLayer(
                            CircleLayer(id, id).withProperties(
                                PropertyFactory.circleColor(color),
                                PropertyFactory.circleRadius(r),
                                PropertyFactory.circleStrokeColor("#FFFFFF"),
                                PropertyFactory.circleStrokeWidth(2f),
                            ),
                        )
                    }
                    map = m
                    style = st
                }
            }
            mapView
        },
        modifier = modifier,
    )

    LaunchedEffect(data, style) {
        val st = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        (LINES.map { it.first } + DOTS.map { it.first }).forEach { id ->
            st.getSourceAs<GeoJsonSource>(id)?.setGeoJson(data.json(id))
        }
        if (!fitted) {
            val pts = data.all.distinct()
            runCatching {
                when {
                    pts.size >= 2 -> {
                        val b = LatLngBounds.Builder()
                        pts.forEach { b.include(LatLng(it.lat, it.lon)) }
                        m.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 80))
                        fitted = true
                    }
                    pts.size == 1 -> {
                        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pts[0].lat, pts[0].lon), 13.0))
                        fitted = true
                    }
                    else -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.6, -8.0), 5.5))
                }
            }
        }
    }
}

/** Full-screen map of a trip; refreshes while the trip is running. */
@Composable
fun TripMapScreen(modifier: Modifier, id: Long) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(MapData()) }
    var s by remember { mutableStateOf(Store.get(id)) }
    LaunchedEffect(id) {
        while (true) {
            val cur = Store.get(id)
            s = cur
            if (cur != null) {
                val track = withContext(Dispatchers.IO) { TrackStore.load(id) }
                data = TripMap.forSession(cur, track, if (cur.end == null) LastFix.fresh() else null)
            }
            if (cur == null || cur.end != null) break
            delay(10_000)
        }
    }
    val now = rememberNow(10_000)
    Column(modifier.fillMaxSize()) {
        val cur = s
        if (cur != null) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    (cur.title.ifBlank { Fmt.date(cur.start) }) + if (cur.destName.isNotBlank()) " → " + cur.destName else "",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr("Driving %s · %s", Fmt.duration(Stats.drivingMs(cur, now)), Fmt.km(cur.km())),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(tr("Orange: driven · grey: planned · yellow: stops · green: extras"), style = MaterialTheme.typography.bodySmall)
                val dest = cur.destLat?.let { a -> cur.destLon?.let { LatLon(a, it) } }
                if (dest != null) {
                    Button(onClick = { openNavigation(ctx, dest, cur.destName) }, Modifier.fillMaxWidth()) {
                        Text(tr("Navigate in OsmAnd"))
                    }
                }
            }
        }
        TruckMap(data, Modifier.fillMaxWidth().weight(1f))
    }
}

/** Opens OsmAnd with the truck profile, or any map app, or OsmAnd's store page. */
fun openNavigation(ctx: Context, dest: LatLon, name: String) {
    val from = LastFix.fresh()
    val tries = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse(OsmAnd.navigateUri(dest, name, from))),
        Intent(Intent.ACTION_VIEW, Uri.parse(OsmAnd.geoUri(dest, name))),
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=net.osmand")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.osmand")),
    )
    for (i in tries) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
}

fun orsErrorText(reason: String?): String = when (reason) {
    "key" -> tr("The route key was not accepted. Check it in Settings.")
    "quota" -> tr("Too many route requests today. Try again later.")
    "network" -> tr("No internet connection, or the route service is down.")
    null -> tr("Something went wrong.")
    else -> reason
}

/** Text field that searches a place (openrouteservice) and lets you pick one. */
@Composable
fun PlaceField(label: String, value: String, allowMyLocation: Boolean, onPicked: (Place) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(value) { mutableStateOf(value) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text(label) },
                modifier = Modifier.weight(1f), singleLine = true,
            )
            Box {
                if (busy) {
                    CircularProgressIndicator(Modifier.padding(8.dp))
                } else {
                    TextButton(onClick = {
                        val key = Prefs.orsKey(ctx)
                        if (key.isBlank()) {
                            error = tr("Add your free route key in Settings first.")
                            return@TextButton
                        }
                        if (text.isBlank()) return@TextButton
                        busy = true
                        error = null
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                runCatching { Ors.search(key, text, I18n.lang.code) }
                            }
                            busy = false
                            r.onSuccess {
                                results = it
                                if (it.isEmpty()) error = tr("Nothing found.")
                            }.onFailure { e -> error = orsErrorText((e as? Ors.OrsError)?.reason) }
                        }
                    }) { Text(tr("Search")) }
                }
                DropdownMenu(expanded = results.isNotEmpty(), onDismissRequest = { results = emptyList() }) {
                    results.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = {
                            results = emptyList()
                            text = p.name
                            onPicked(p)
                        })
                    }
                }
            }
        }
        if (allowMyLocation) {
            TextButton(onClick = {
                val here = LastFix.fresh() ?: lastKnown(ctx)
                if (here == null) {
                    error = tr("No GPS position yet.")
                } else {
                    text = tr("My location")
                    onPicked(Place(tr("My location"), here.lat, here.lon))
                }
            }) { Text(tr("Use my location")) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Suppress("MissingPermission")
private fun lastKnown(ctx: Context): LatLon? = runCatching {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    val l = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
        ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
    l?.let { LatLon(it.latitude, it.longitude) }
}.getOrNull()

@Composable
fun MapButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
}
