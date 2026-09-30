package pl.rafal.contextlauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

// Proporcje ekranu telefonu (szerokość / wysokość w pionie) — ramka kadru ma dokładnie taki kształt.
private fun screenAspect(context: Context): Float {
    val wm = context.getSystemService(WindowManager::class.java)
    val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        @Suppress("DEPRECATION") // na Androidzie 10 nie ma jeszcze WindowMetrics
        DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    }
    return minOf(w, h).toFloat() / maxOf(w, h).coerceAtLeast(1)
}

// Stan kadrowania: powiększenie i położenie obrazu w ramce (w pikselach ramki).
// Osobna klasa, żeby przycisk "Zapisz" mógł odczytać aktualny kadr (jak ViewModel małego okna).
private class CropState(val iw: Float, val ih: Float, val fw: Float, val fh: Float, initial: RectF?) {
    private val base = maxOf(fw / iw, fh / ih) // najmniejsza skala, przy której obraz zakrywa całą ramkę
    var zoom by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero) // lewy górny róg obrazu względem ramki

    val scale get() = base * zoom
    val dw get() = iw * scale
    val dh get() = ih * scale

    init {
        if (initial != null) {
            zoom = (fw / (initial.width() * iw) / base).coerceIn(1f, MAX_ZOOM)
            offset = clamp(Offset(-initial.left * dw, -initial.top * dh))
        } else reset()
    }

    fun reset() {
        zoom = 1f
        offset = Offset((fw - dw) / 2f, (fh - dh) / 2f) // wycentrowany, wypełnia ramkę
    }

    // Obraz nie może odsłonić pustego tła: przesunięcie w granicach [ramka − obraz, 0].
    // minOf(…, 0f): przy powiększeniu 1× obraz bywa o ułamek piksela węższy niż ramka (zaokrąglenia float),
    // a coerceIn z odwróconym zakresem rzuca wyjątek.
    private fun clamp(o: Offset) = Offset(o.x.coerceIn(minOf(fw - dw, 0f), 0f), o.y.coerceIn(minOf(fh - dh, 0f), 0f))

    // Gest dwoma palcami: punkt obrazu pod środkiem palców zostaje pod palcami (jak w Zdjęciach).
    fun transform(centroid: Offset, pan: Offset, zoomChange: Float) {
        val before = scale
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val imagePoint = (centroid - offset) / before
        offset = clamp(centroid - imagePoint * scale + pan)
    }

    // Widoczny fragment jako ułamki obrazu (0..1) — niezależne od rozdzielczości podglądu.
    fun crop() = RectF(-offset.x / dw, -offset.y / dh, (fw - offset.x) / dw, (fh - offset.y) / dh)

    companion object {
        const val MAX_ZOOM = 6f
    }
}

// Okno "Dopasuj tapetę": ramka w proporcjach ekranu, obraz można powiększać (rozsuwając palce) i przesuwać.
// Jak kadrowanie w systemowym wyborze tapety. Zapisany kadr ustawiamy jako tapetę przy włączeniu trybu.
@Composable
fun WallpaperCropDialog(
    load: suspend () -> Bitmap?,
    initial: RectF?,
    onSave: (RectF) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val aspect = remember { screenAspect(context) }
    // produceState ≈ async ładowanie do właściwości: najpierw null (kółko ładowania), potem obraz.
    val bitmap by produceState<Bitmap?>(null) { value = load() }
    var failed by remember { mutableStateOf(false) }
    // Brak obrazka po wczytaniu (plik usunięty, zły format) — komunikat zamiast wiecznego kółka ładowania.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4000)
        if (bitmap == null) failed = true
    }
    // Zwykła tablica, nie stan: przycisk "Zapisz" czyta kadr w chwili kliknięcia, a ramka nie musi się przerysowywać.
    val holder = remember { arrayOfNulls<CropState>(1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0C0E))
                .systemBarsPadding()
                .padding(16.dp),
        ) {
            Text("Dopasuj tapetę", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                "Rozsuń palce, aby powiększyć, i przesuń obraz. Ramka to ekran telefonu.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val bmp = bitmap
                if (bmp == null && failed) {
                    Text("Nie udało się wczytać obrazu tapety.", color = Color.White)
                } else if (bmp == null) {
                    CircularProgressIndicator()
                } else {
                    // Ramka jak największa w dostępnym miejscu, w proporcjach ekranu.
                    val maxW = constraints.maxWidth.toFloat()
                    val maxH = constraints.maxHeight.toFloat()
                    val fw = if (maxW / maxH > aspect) maxH * aspect else maxW
                    val fh = fw / aspect
                    val crop = remember(bmp, fw, fh) {
                        CropState(bmp.width.toFloat(), bmp.height.toFloat(), fw, fh, holder[0]?.crop() ?: initial).also { holder[0] = it }
                    }
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    val density = LocalDensity.current
                    Canvas(
                        Modifier
                            .size(with(density) { fw.toDp() }, with(density) { fh.toDp() })
                            .clipToBounds()
                            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                            .pointerInput(crop) {
                                detectTransformGestures { centroid, pan, zoom, _ -> crop.transform(centroid, pan, zoom) }
                            },
                    ) {
                        drawImage(
                            image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(image.width, image.height),
                            dstOffset = IntOffset(crop.offset.x.roundToInt(), crop.offset.y.roundToInt()),
                            dstSize = IntSize(crop.dw.roundToInt(), crop.dh.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss) { Text("Anuluj", color = Color.White) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { holder[0]?.reset() }, enabled = bitmap != null) { Text("Wypełnij ekran", color = Color.White) }
                Button(onClick = { holder[0]?.let { onSave(it.crop()) } }, enabled = bitmap != null) { Text("Zapisz") }
            }
        }
    }
}
