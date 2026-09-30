package pl.rafal.stickonme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

// Stan edytora wycinania: zdjęcie + maska (co zostaje w naklejce) + ustawienia wyniku.
// Maska to bitmapa ALPHA_8 w rozmiarze zdjęcia: 255 = zostaje, 0 = przezroczyste. Pędzel "Dodaj" maluje, "Usuń" wymazuje.
// fromSticker = poprawiamy gotową naklejkę: maska startuje z jej przezroczystości (bez automatycznego wycinania).
class CutoutEditor(source: Bitmap, val fromSticker: Boolean = false) {
    // Przy poprawce naklejki przezroczystość trzymamy TYLKO w masce, a zdjęcie robimy nieprzezroczyste —
    // inaczej zapis mnożyłby alfę dwa razy (miękki brzeg 50% → 25%) i pędzel "Dodaj" nie mógłby jej przywrócić.
    val photo: Bitmap = if (!fromSticker) source else source.copy(Bitmap.Config.ARGB_8888, true).apply {
        val px = IntArray(width * height)
        getPixels(px, 0, width, 0, 0, width, height) // getPixels zwraca kolory bez premultiplikacji
        for (i in px.indices) if ((px[i] ushr 24) != 0) px[i] = px[i] or 0xFF000000.toInt()
        setPixels(px, 0, width, 0, 0, width, height)
    }
    val mask: Bitmap =
        if (fromSticker) source.extractAlpha().copy(Bitmap.Config.ALPHA_8, true)
        else Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ALPHA_8).apply { eraseColor(Color.BLACK) }
    var version by mutableIntStateOf(0)   // zmiana = maska się zmieniła (Compose przerysowuje podgląd)
        private set
    var rotation by mutableFloatStateOf(0f) // obrót wyniku w stopniach
    var outputSide by mutableIntStateOf(768) // dłuższy bok wyniku w px (rozmiar naklejki)
    var hasAutoMask by mutableStateOf(false)
    // Wykończenie krawędzi (w pikselach zdjęcia): wygładzenie = miękkie przejście, zwężenie = zjedzenie resztek tła przy brzegu.
    var edgeSmooth by mutableFloatStateOf(0f)
    var edgeShrink by mutableFloatStateOf(0f)
    // Kształt i ramka "wypalane" w naklejkę przy zapisie (StickerShapes / Layer.FRAMES).
    var shape by mutableStateOf(StickerShapes.NONE)
    var frame by mutableStateOf(Layer.FRAME_NONE)
    var frameColor by mutableStateOf(0xFFFFFFFFL)

    private val undo = ArrayDeque<Bitmap>()
    private var undoCount by mutableIntStateOf(0) // stan Compose: przycisk "Cofnij" wie, kiedy się włączyć
    val canUndo get() = undoCount > 0

    private val canvas = Canvas(mask)
    private val add = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK // w ALPHA_8 liczy się tylko przezroczystość: czarny nieprzezroczysty = 255
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val erase = Paint(add).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    // Kopia maski przed zmianą (cofanie do 12 kroków wstecz).
    fun snapshot() {
        undo.addLast(mask.copy(Bitmap.Config.ALPHA_8, false))
        while (undo.size > 12) undo.removeFirst()
        undoCount = undo.size
    }

    fun undoLast() {
        val previous = undo.removeLastOrNull() ?: return
        undoCount = undo.size
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(previous, 0f, 0f, null)
        version++
    }

    // Pociągnięcie pędzla w pikselach zdjęcia (width = średnica pędzla w pikselach zdjęcia).
    fun stroke(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, adding: Boolean) {
        val paint = if (adding) add else erase
        paint.strokeWidth = width
        canvas.drawLine(x0, y0, x1, y1, paint)
        version++
    }

    // Maska z modelu (pewność 0..1 dla każdego piksela) → wypełnienie maski.
    fun applyConfidence(confidence: FloatArray, width: Int, height: Int) {
        snapshot()
        val small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { i -> (confidence[i].coerceIn(0f, 1f) * 255).roundToInt() shl 24 }
        small.setPixels(pixels, 0, width, 0, 0, width, height)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(small, null, android.graphics.Rect(0, 0, mask.width, mask.height), Paint(Paint.FILTER_BITMAP_FLAG))
        hasAutoMask = true
        version++
    }

    // Całe zdjęcie (bez wycinania) albo wyczyszczenie maski.
    fun fill(all: Boolean) {
        snapshot()
        canvas.drawColor(if (all) Color.BLACK else Color.TRANSPARENT, if (all) PorterDuff.Mode.SRC else PorterDuff.Mode.CLEAR)
        version++
    }

    // Kadr: wszystko poza prostokątem (albo owalem) znika z maski. Współrzędne w pikselach zdjęcia.
    fun cropTo(left: Float, top: Float, right: Float, bottom: Float, oval: Boolean) {
        snapshot()
        canvas.save()
        if (oval) {
            canvas.clipOutPath(android.graphics.Path().apply { addOval(left, top, right, bottom, android.graphics.Path.Direction.CW) })
        } else {
            canvas.clipOutRect(left, top, right, bottom)
        }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.restore()
        version++
    }

    // Maska z wykończoną krawędzią albo null, gdy suwaki są na zero (wtedy liczy się zwykła maska).
    // Rozmycie pudełkowe o promieniu R zamienia ostry brzeg w liniową rampę szerokości 2R —
    // z jasności piksela odczytujemy więc jego odległość od brzegu i rysujemy nowy brzeg, przesunięty do środka o "zwężenie".
    fun finishedMask(check: () -> Unit = {}): Bitmap? {
        val smooth = edgeSmooth.coerceAtLeast(0f)
        val shrink = edgeShrink.coerceAtLeast(0f)
        if (smooth < 0.5f && shrink < 0.5f) return null
        val radius = kotlin.math.ceil(smooth / 2f + shrink).toInt().coerceAtLeast(1)
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)
        val alpha = IntArray(w * h) { pixels[it] ushr 24 }
        val blurred = boxBlur(boxBlur(alpha, w, h, radius, horizontal = true, check), w, h, radius, horizontal = false, check)
        val span = 2f * radius
        for (i in blurred.indices) {
            val distance = blurred[i] / 255f * span - radius // >0 = wewnątrz obiektu
            val a = if (smooth < 0.5f) {
                if (distance >= shrink) 1f else 0f
            } else {
                ((distance - shrink) / smooth + 0.5f).coerceIn(0f, 1f)
            }
            pixels[i] = (a * 255f).roundToInt() shl 24
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).apply { setPixels(pixels, 0, w, 0, 0, w, h) }
    }

    // Średnia z okna 2r+1 w jednym kierunku (suma krocząca — O(n) niezależnie od promienia).
    // check() = punkt przerwania (np. ensureActive() korutyny, jak CancellationToken.ThrowIfCancellationRequested).
    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean, check: () -> Unit): IntArray {
        val out = IntArray(src.size)
        val window = 2 * r + 1
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val step = if (horizontal) 1 else w          // odległość między sąsiednimi pikselami w tablicy
        for (line in 0 until lines) {
            check()
            val first = if (horizontal) line * w else line  // indeks pierwszego piksela tej linii
            var sum = 0
            for (i in -r..r) sum += src[first + i.coerceIn(0, length - 1) * step]
            for (i in 0 until length) {
                out[first + i * step] = sum / window
                sum += src[first + (i + r + 1).coerceAtMost(length - 1) * step] - src[first + (i - r).coerceAtLeast(0) * step]
            }
        }
        return out
    }

    // Gotowa naklejka: zdjęcie × maska, przycięte do widocznej części, obrócone i przeskalowane.
    // Kolejność: wycięcie → przycięcie → kształt → ramka → skala i obrót. check() = punkt przerwania (podgląd w tle).
    fun render(rotate: Boolean = true, check: () -> Unit = {}): Bitmap? {
        val cut = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
        Canvas(cut).apply {
            drawBitmap(photo, 0f, 0f, null)
            drawBitmap(finishedMask(check) ?: mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        }
        check()
        val bounds = visibleBounds(cut) ?: return null
        var trimmed = Bitmap.createBitmap(cut, bounds.left, bounds.top, bounds.width(), bounds.height())
        check()
        val kind = StickerShapes.of(shape)
        if (kind.id != StickerShapes.NONE) trimmed = applyShape(trimmed, kind)
        if (frame != Layer.FRAME_NONE) trimmed = BoardRenderer().frameSticker(trimmed, frame, frameColor)
        check()
        val scale = outputSide.toFloat() / maxOf(trimmed.width, trimmed.height)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (rotate) postRotate(rotation)
        }
        // createBitmap z macierzą sam powiększa płótno o obrócone rogi (tło przezroczyste).
        return Bitmap.createBitmap(trimmed, 0, 0, trimmed.width, trimmed.height, matrix, true)
    }

    // Kształt: kwadratowe (koło, serce…) najpierw kadrujemy do kwadratu ze środka, potem zostawiamy tylko wnętrze ścieżki.
    private fun applyShape(source: Bitmap, kind: StickerShapes.Shape): Bitmap {
        val side = minOf(source.width, source.height)
        val base = if (kind.square) {
            Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        } else {
            source
        }
        val path = StickerShapes.path(kind.id, base.width.toFloat(), base.height.toFloat()) ?: return base
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
            drawBitmap(base, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
        }
        return out
    }

    // Prostokąt z widocznymi pikselami (alfa > 8), liczony na siatce co kilka pikseli — szybko także dla 1600 px.
    private fun visibleBounds(bitmap: Bitmap): android.graphics.Rect? {
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        var top = -1
        var bottom = -1
        var left = w
        var right = -1
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var any = false
            for (x in 0 until w) {
                if ((row[x] ushr 24) > 8) {
                    any = true
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
            if (any) {
                if (top < 0) top = y
                bottom = y
            }
        }
        if (top < 0 || right < left) return null
        return android.graphics.Rect(left, top, right + 1, bottom + 1)
    }
}

// Maska pewności z ML Kit (na telefonie, zdjęcie nigdzie nie wychodzi). Zwraca (wartości, szerokość, wysokość).
suspend fun autoMask(photo: Bitmap): Triple<FloatArray, Int, Int>? = withContext(Dispatchers.Default) {
    suspendCancellableCoroutine { cont ->
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build(),
        )
        segmenter.process(InputImage.fromBitmap(photo, 0))
            .addOnSuccessListener { result ->
                val buffer = result.foregroundConfidenceMask
                segmenter.close()
                if (buffer == null) {
                    cont.resume(null)
                } else {
                    buffer.rewind()
                    val values = FloatArray(buffer.remaining())
                    buffer.get(values)
                    // Maska ma rozmiar zdjęcia wejściowego.
                    cont.resume(Triple(values, photo.width, photo.height).takeIf { values.size == photo.width * photo.height })
                }
            }
            .addOnFailureListener { e ->
                segmenter.close()
                cont.resumeWithException(e)
            }
        cont.invokeOnCancellation { runCatching { segmenter.close() } }
    }
}
