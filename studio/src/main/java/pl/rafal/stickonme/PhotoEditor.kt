package pl.rafal.stickonme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// --- Edytor zdjęcia przed dodaniem na tablicę (SOM 1.6.0) ---
// Jak edytor w telefonie: stałe okno kadru o wybranych proporcjach (i kształcie), a pod nim zdjęcie, które przesuwasz,
// powiększasz i obracasz palcami. Do tego korekta (jasność, kontrast, nasycenie, ciepło), filtry i ramka.
// Kadr i korekta są „wypalane” w nowy plik; filtry i ramka zostają ustawieniami warstwy (można je zmienić później).
// Ustawienia kadru zapisujemy w warstwie (Layer.photoEdit), a oryginał zostaje w assets — ponowna edycja zaczyna
// od tego samego miejsca (jak warstwa „smart object” w Photoshopie).

data class PhotoEdit(
    val aspect: Float? = null,          // szer./wys. okna; null = proporcje zdjęcia
    val shape: String = StickerShapes.NONE,
    val zoom: Float = 1f,               // względem „wypełnienia” okna (1 = zdjęcie dokładnie zakrywa okno)
    val panX: Float = 0f,               // przesunięcie środka zdjęcia w ułamkach szerokości / wysokości okna
    val panY: Float = 0f,
    val rotation: Float = 0f,           // stopnie
    val brightness: Int = 0,            // poziomy −2..2
    val contrast: Int = 0,
    val saturation: Int = 0,
    val warmth: Int = 0,
) {
    fun toJson(): String = JSONObject()
        .putOpt("aspect", aspect?.toDouble()).put("shape", shape)
        .put("zoom", zoom.toDouble()).put("panX", panX.toDouble()).put("panY", panY.toDouble()).put("rotation", rotation.toDouble())
        .put("brightness", brightness).put("contrast", contrast).put("saturation", saturation).put("warmth", warmth)
        .toString()

    companion object {
        fun of(json: String): PhotoEdit = runCatching {
            val o = JSONObject(json)
            PhotoEdit(
                aspect = if (o.has("aspect") && !o.isNull("aspect")) o.getDouble("aspect").toFloat() else null,
                shape = o.optString("shape", StickerShapes.NONE),
                zoom = o.optDouble("zoom", 1.0).toFloat(),
                panX = o.optDouble("panX", 0.0).toFloat(),
                panY = o.optDouble("panY", 0.0).toFloat(),
                rotation = o.optDouble("rotation", 0.0).toFloat(),
                brightness = o.optInt("brightness", 0),
                contrast = o.optInt("contrast", 0),
                saturation = o.optInt("saturation", 0),
                warmth = o.optInt("warmth", 0),
            )
        }.getOrDefault(PhotoEdit())
    }
}

// Wynik edytora: gotowy obraz + ustawienia warstwy.
class PhotoResult(val bitmap: Bitmap, val opaque: Boolean, val edit: PhotoEdit, val frame: String, val frameColor: Long, val filters: String)

// Liczenie kadru — wspólne dla podglądu i zapisu (te same wzory, inny rozmiar okna).
object PhotoRender {
    const val OUTPUT_SIDE = 1600

    fun windowAspect(edit: PhotoEdit, photo: Bitmap): Float = edit.aspect ?: (photo.width.toFloat() / photo.height)

    fun outputSize(edit: PhotoEdit, photo: Bitmap, maxSide: Int = OUTPUT_SIDE): Pair<Int, Int> {
        val a = windowAspect(edit, photo)
        return if (a >= 1f) maxSide to (maxSide / a).roundToInt().coerceAtLeast(1)
        else (maxSide * a).roundToInt().coerceAtLeast(1) to maxSide
    }

    // Macierz: piksele zdjęcia → okno (w × h). Najpierw środek zdjęcia w (0,0), skala, obrót, potem środek okna + przesunięcie.
    fun matrix(edit: PhotoEdit, photo: Bitmap, w: Float, h: Float): Matrix {
        val s = max(w / photo.width, h / photo.height) * edit.zoom
        return Matrix().apply {
            postTranslate(-photo.width / 2f, -photo.height / 2f)
            postScale(s, s)
            postRotate(edit.rotation)
            postTranslate(w / 2f + edit.panX * w, h / 2f + edit.panY * h)
        }
    }

    // Korekta jako macierz kolorów 4×5 (jak ColorMatrix w WPF-owych efektach): nasycenie, kontrast z jasnością, ciepło.
    fun colorFilter(edit: PhotoEdit): ColorMatrixColorFilter? {
        if (edit.brightness == 0 && edit.contrast == 0 && edit.saturation == 0 && edit.warmth == 0) return null
        val m = ColorMatrix()
        m.setSaturation(1f + edit.saturation * 0.25f)
        val c = 1f + edit.contrast * 0.15f
        val t = 128f * (1f - c) + edit.brightness * 18f
        m.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        val w = edit.warmth * 10f
        m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, w, 0f, 1f, 0f, 0f, w * 0.3f, 0f, 0f, 1f, 0f, -w, 0f, 0f, 0f, 1f, 0f)))
        return ColorMatrixColorFilter(m)
    }

    // Gotowy obraz okna (kadr + korekta + kształt). Poza zdjęciem — przezroczystość.
    fun render(edit: PhotoEdit, photo: Bitmap, maxSide: Int = OUTPUT_SIDE): Bitmap {
        val (w, h) = outputSize(edit, photo, maxSide)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = colorFilter(edit) }
        canvas.drawBitmap(photo, matrix(edit, photo, w.toFloat(), h.toFloat()), paint)
        val path = StickerShapes.path(edit.shape, w.toFloat(), h.toFloat())
        if (path != null) {
            val shapeMask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(shapeMask).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK })
            canvas.drawBitmap(shapeMask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            shapeMask.recycle()
        }
        return out
    }

    // Czy zdjęcie zakrywa całe okno bez kształtu — wtedy wystarczy JPEG (mniejszy plik), inaczej PNG z przezroczystością.
    fun covers(edit: PhotoEdit, photo: Bitmap): Boolean {
        if (edit.shape != StickerShapes.NONE) return false
        val (w, h) = outputSize(edit, photo)
        val inverse = Matrix()
        if (!matrix(edit, photo, w.toFloat(), h.toFloat()).invert(inverse)) return false
        val pts = floatArrayOf(0f, 0f, w.toFloat(), 0f, 0f, h.toFloat(), w.toFloat(), h.toFloat())
        inverse.mapPoints(pts)
        for (i in 0 until 4) {
            val x = pts[2 * i]
            val y = pts[2 * i + 1]
            if (x < -0.5f || y < -0.5f || x > photo.width + 0.5f || y > photo.height + 0.5f) return false
        }
        return true
    }
}

private enum class PhotoTab(@StringRes val labelRes: Int) {
    CROP(R.string.som_rail_crop), ADJUST(R.string.som_photo_adjust), FILTER(R.string.som_rail_filter), FRAME(R.string.som_rail_frame);

    val icon get() = when (this) {
        CROP -> StudioIcons.Crop
        ADJUST -> StudioIcons.Adjust
        FILTER -> StudioIcons.Filter
        FRAME -> StudioIcons.Frame
    }
}

private val PHOTO_ASPECTS: List<Pair<Float?, String>> = listOf(
    null to "", 1f to "1:1", 4f / 5f to "4:5", 3f / 4f to "3:4", 4f / 3f to "4:3", 16f / 9f to "16:9", 9f / 16f to "9:16",
)

// Pełnoekranowy edytor zdjęcia (nad tablicą, jak edytor tekstu). photo = oryginał (do 1800 px).
@Composable
fun PhotoEditorOverlay(
    photo: Bitmap,
    initialEdit: PhotoEdit,
    initialFrame: String,
    initialFrameColor: Long,
    initialFilters: String,
    onDone: (PhotoResult) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(initialEdit) }
    var frame by remember { mutableStateOf(initialFrame) }
    var frameColor by remember { mutableStateOf(initialFrameColor) }
    var filters by remember { mutableStateOf(initialFilters) }
    var tab by remember { mutableStateOf(PhotoTab.CROP) }
    var busy by remember { mutableStateOf(false) }
    // Mniejsza kopia do podglądu (płynne przesuwanie palcem); wzory kadru są względne, więc wynik ten sam.
    val display = remember(photo) {
        val k = min(1f, 1280f / max(photo.width, photo.height))
        if (k < 1f) Bitmap.createScaledBitmap(photo, (photo.width * k).roundToInt().coerceAtLeast(1), (photo.height * k).roundToInt().coerceAtLeast(1), true) else photo
    }
    // Filtry i ramka: podgląd gotowej warstwy liczony w tle (styl malarski trwa chwilę), palcem nic się tu nie przesuwa.
    val live = tab == PhotoTab.CROP || tab == PhotoTab.ADJUST
    var finished by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(live, edit, filters, frame, frameColor) {
        if (!live) {
            kotlinx.coroutines.delay(60)
            finished = withContext(Dispatchers.Default) {
                runCatching {
                    var b = PhotoRender.render(edit, display, 900)
                    ensureActive()
                    val f = StickerFilters.apply(b, filters) { ensureActive() }
                    if (f !== b) b.recycle()
                    b = f
                    val framed = BoardRenderer().frameSticker(b, frame, frameColor)
                    if (framed !== b) b.recycle()
                    framed.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    BackHandler { if (!busy) onCancel() }
    val colors = MaterialTheme.colorScheme
    val pad = with(LocalDensity.current) { 24.dp.toPx() }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            StudioIconButton(StudioIcons.Close, stringResource(R.string.som_cancel), enabled = !busy, outlined = true) { onCancel() }
            Text(stringResource(R.string.som_photo_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StudioPrimaryButton(stringResource(R.string.som_done), enabled = !busy) {
                busy = true
                val snapshot = edit
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        runCatching { PhotoRender.render(snapshot, photo) to PhotoRender.covers(snapshot, photo) }.getOrNull()
                    }
                    busy = false
                    if (result != null) onDone(PhotoResult(result.first, result.second, snapshot, frame, frameColor, filters))
                }
            }
        }

        // Płótno: zdjęcie pod oknem kadru (poza oknem przygaszone) albo gotowy podgląd z filtrami i ramką.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (live) {
                androidx.compose.foundation.Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                val (ww, wh) = windowSize(size.width.toFloat(), size.height.toFloat(), pad, PhotoRender.windowAspect(edit, display))
                                edit = edit.copy(
                                    panX = (edit.panX + pan.x / ww).coerceIn(-2f, 2f),
                                    panY = (edit.panY + pan.y / wh).coerceIn(-2f, 2f),
                                    zoom = (edit.zoom * zoom).coerceIn(0.2f, 8f),
                                    rotation = edit.rotation + rotation,
                                )
                            }
                        },
                ) {
                    val (ww, wh) = windowSize(size.width, size.height, pad, PhotoRender.windowAspect(edit, display))
                    val left = (size.width - ww) / 2f
                    val top = (size.height - wh) / 2f
                    drawIntoCanvas { c ->
                        val nc = c.nativeCanvas
                        nc.save()
                        nc.translate(left, top)
                        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PhotoRender.colorFilter(edit) }
                        nc.drawBitmap(display, PhotoRender.matrix(edit, display, ww, wh), paint)
                        nc.restore()
                        // Przygaszenie poza oknem (4 prostokąty) i obrys okna / kształtu.
                        val dim = Paint().apply { color = 0xB0000000.toInt() }
                        nc.drawRect(0f, 0f, size.width, top, dim)
                        nc.drawRect(0f, top + wh, size.width, size.height, dim)
                        nc.drawRect(0f, top, left, top + wh, dim)
                        nc.drawRect(left + ww, top, size.width, top + wh, dim)
                        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 2.5f
                            color = android.graphics.Color.WHITE
                        }
                        nc.save()
                        nc.translate(left, top)
                        val shape = StickerShapes.path(edit.shape, ww, wh)
                        if (shape != null) {
                            nc.drawRect(0f, 0f, ww, wh, Paint(line).apply { alpha = 90 })
                            nc.drawPath(shape, line)
                        } else {
                            nc.drawRect(0f, 0f, ww, wh, line)
                        }
                        nc.restore()
                    }
                }
            } else {
                val img = finished
                if (img != null) {
                    Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(24.dp))
                }
            }
        }

        // Opcje zakładki (pigułki, bez suwaków) + pasek zakładek.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 230.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tab) {
                PhotoTab.CROP -> {
                    StudioLabel(stringResource(R.string.som_crop_aspect))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        PHOTO_ASPECTS.forEach { (value, label) ->
                            StudioChip(if (value == null) stringResource(R.string.som_photo_original) else label, edit.aspect == value) { edit = edit.copy(aspect = value) }
                        }
                    }
                    StudioLabel(stringResource(R.string.som_editor_shape))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        StickerShapes.all.forEach { s ->
                            StudioChip(s.label, edit.shape == s.id) { edit = edit.copy(shape = s.id, aspect = if (s.square) 1f else edit.aspect) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        StudioChip("↺ 90°", false) { edit = edit.copy(rotation = edit.rotation - 90f) }
                        StudioChip("−5°", false) { edit = edit.copy(rotation = edit.rotation - 5f) }
                        StudioChip(stringResource(R.string.som_rotate_straight), false) { edit = edit.copy(rotation = 0f) }
                        StudioChip("+5°", false) { edit = edit.copy(rotation = edit.rotation + 5f) }
                        StudioChip("↻ 90°", false) { edit = edit.copy(rotation = edit.rotation + 90f) }
                        StudioChip(stringResource(R.string.som_photo_reset), false) { edit = edit.copy(zoom = 1f, panX = 0f, panY = 0f, rotation = 0f) }
                    }
                    Text(stringResource(R.string.som_photo_crop_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                PhotoTab.ADJUST -> {
                    AdjustRow(stringResource(R.string.som_photo_brightness), edit.brightness) { edit = edit.copy(brightness = it) }
                    AdjustRow(stringResource(R.string.som_photo_contrast), edit.contrast) { edit = edit.copy(contrast = it) }
                    AdjustRow(stringResource(R.string.som_photo_saturation), edit.saturation) { edit = edit.copy(saturation = it) }
                    AdjustRow(stringResource(R.string.som_photo_warmth), edit.warmth) { edit = edit.copy(warmth = it) }
                }
                PhotoTab.FILTER -> FilterPicker(filters) { filters = it }
                PhotoTab.FRAME -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Layer.FRAMES.forEach { (key, label) -> StudioChip(label, frame == key) { frame = key } }
                    }
                    if (Layer.hasColor(frame)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            FrameColors.forEach { c ->
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .border(if (frameColor == c) 3.dp else 1.dp, if (frameColor == c) colors.secondary else colors.outline, CircleShape)
                                        .clickable { frameColor = c },
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
        ) {
            PhotoTab.entries.forEach { t -> StudioRailItem(t.icon, stringResource(t.labelRes), selected = tab == t) { if (!busy) tab = t } }
        }
    }
}

// Okno kadru o proporcjach `aspect` wpisane w płótno z marginesem.
private fun windowSize(w: Float, h: Float, pad: Float, aspect: Float): Pair<Float, Float> {
    val aw = (w - 2 * pad).coerceAtLeast(1f)
    val ah = (h - 2 * pad).coerceAtLeast(1f)
    return if (aw / ah > aspect) ah * aspect to ah else aw to aw / aspect
}

// Poziom korekty: pięć pigułek zamiast suwaka.
@Composable
private fun AdjustRow(label: String, value: Int, onChange: (Int) -> Unit) {
    StudioLabel(label)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        listOf(-2 to "−−", -1 to "−", 0 to "0", 1 to "+", 2 to "++").forEach { (v, text) ->
            StudioChip(text, value == v) { onChange(v) }
        }
    }
}
