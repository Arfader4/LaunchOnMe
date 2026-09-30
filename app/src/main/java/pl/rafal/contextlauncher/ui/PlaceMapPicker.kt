package pl.rafal.contextlauncher.ui

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import pl.rafal.contextlauncher.R
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

// Wybór miejsca dla trybu: przesuwasz mapę tak, żeby pinezka na środku wskazywała miejsce (jak w Uberze czy Bolcie),
// albo wyszukujesz adres. Nie trzeba być w tym miejscu, żeby je zapisać.
// Mapa: OpenStreetMap przez bibliotekę osmdroid (bez klucza API); wyszukiwanie: wbudowany Geocoder Androida.
@Composable
fun PlaceMapDialog(
    start: Pair<Double, Double>?,      // gdzie otworzyć mapę (ostatnia znana lokalizacja), null = Warszawa
    onConfirm: (label: String, lat: Double, lon: Double, radiusMeters: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var radius by remember { mutableIntStateOf(300) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Address>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf(GeoPoint(start?.first ?: 52.2297, start?.second ?: 21.0122)) }

    // MapView to zwykły widok Androida — tworzymy go raz i wkładamy do Compose przez AndroidView.
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName // wymóg serwerów OSM: przedstaw się
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(if (start != null) 16.0 else 12.0)
            controller.setCenter(center)
        }
    }
    val circle = remember { Polygon(map) }
    val accent = MaterialTheme.colorScheme.primary.toArgb()

    fun redrawCircle() {
        circle.setPoints(Polygon.pointsAsCircle(center, radius.toDouble()))
        circle.fillPaint.color = (accent and 0x00FFFFFF) or 0x33000000 // kolor akcentu, 20% krycia
        circle.outlinePaint.color = accent
        circle.outlinePaint.strokeWidth = 4f
        map.invalidate()
    }

    fun syncCenter() {
        center = GeoPoint(map.mapCenter.latitude, map.mapCenter.longitude)
        redrawCircle()
    }

    DisposableEffect(map) {
        map.overlays.add(circle)
        // Przesuwanie / przybliżanie mapy → nowy środek (tam, gdzie pinezka).
        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                syncCenter()
                return false
            }
            // Przybliżanie dwoma palcami też potrafi przesunąć środek.
            override fun onZoom(event: ZoomEvent?): Boolean {
                syncCenter()
                return false
            }
        })
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    LaunchedEffect(radius) { redrawCircle() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Miejsce trybu", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nazwa, np. Biuro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Szukaj adresu") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = query.isNotBlank() && !searching, onClick = {
                        searching = true
                        scope.launch {
                            results = geocode(context, query.trim())
                            searching = false
                            if (results.isEmpty()) android.widget.Toast.makeText(context, "Nie znaleziono adresu", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(if (searching) "…" else "Szukaj") }
                }
                if (results.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 160.dp)) {
                        items(results) { address ->
                            Text(
                                address.getAddressLine(0) ?: "${address.latitude}, ${address.longitude}",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        center = GeoPoint(address.latitude, address.longitude)
                                        map.controller.setZoom(17.0)
                                        map.controller.setCenter(center)
                                        if (label.isBlank()) label = address.featureName ?: address.thoroughfare ?: ""
                                        results = emptyList()
                                        redrawCircle()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }
                // Mapa z pinezką na środku (przesuń mapę pod pinezkę).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
                    Icon(
                        painter = painterResource(R.drawable.ic_folder_pin),
                        contentDescription = "Wybrane miejsce",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center).offset(y = (-18).dp).size(36.dp),
                    )
                    if (start != null) {
                        TextButton(
                            onClick = {
                                center = GeoPoint(start.first, start.second)
                                map.controller.animateTo(center)
                                redrawCircle()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(20.dp)),
                        ) { Text("Tu jestem") }
                    }
                    // Atrybucja wymagana przez licencję danych OpenStreetMap.
                    Text(
                        "© OpenStreetMap",
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color(0xFF333333),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(androidx.compose.ui.graphics.Color(0xB3FFFFFF))
                            .padding(horizontal = 4.dp),
                    )
                }
                Text("Promień", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(50, 100, 300, 500, 1000, 3000).forEach { r ->
                        FilterChip(
                            selected = r == radius,
                            onClick = { radius = r },
                            label = { Text(if (r < 1000) "$r m" else "${r / 1000} km") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Anuluj") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = label.isNotBlank(),
                        onClick = { onConfirm(label.trim(), center.latitude, center.longitude, radius) },
                    ) { Text("Zapisz miejsce") }
                }
            }
        }
    }
}

// Adres → współrzędne. Od Androida 13 Geocoder ma wersję z odpowiedzią przez callback (bez blokowania wątku).
private suspend fun geocode(context: Context, query: String): List<Address> = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext emptyList()
    val geocoder = Geocoder(context, Locale.forLanguageTag("pl-PL"))
    runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine<List<Address>> { cont ->
                geocoder.getFromLocationName(query, 6, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                    override fun onError(errorMessage: String?) = cont.resume(emptyList())
                })
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, 6).orEmpty()
        }
    }.getOrDefault(emptyList())
}
