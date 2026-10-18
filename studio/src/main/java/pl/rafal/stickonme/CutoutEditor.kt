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
import kotlinx.coroutines.asExecutor
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
        while (undo.size > 12) undo.removeFirst().recycle() // najstarszy krok — nigdzie nie rysowany
        undoCount = undo.size
    }

    fun undoLast() {
        val previous = undo.removeLastOrNull() ?: return
        undoCount = undo.size
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(previous, 0f, 0f, null)
        previous.recycle()
        version++
    }

    // Pociągnięcie pędzla w pikselach zdjęcia (width = średnica pędzla w pikselach zdjęcia).
    fun stroke(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, adding: Boolean) {
        val paint = if (adding) add else erase
        paint.strokeWidth = width
        canvas.drawLine(x0, y0, x1, y1, paint)
        version++
    }

    // Maska z modelu (pewność 0..1 dla każdego piksela, rozmiar wejścia ML Kit) → gotowa maska w rozmiarze zdjęcia.
    // Ciężka część (pętla po pikselach, skalowanie) — W TLE (Dispatchers.Default). Nie rusza bieżącej maski.
    fun prepareMask(confidence: FloatArray, width: Int, height: Int): Bitmap {
        val small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height) { i -> (confidence[i].coerceIn(0f, 1f) * 255).roundToInt() shl 24 }
        small.setPixels(pixels, 0, width, 0, 0, width, height)
        val full = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ALPHA_8)
        Canvas(full).drawBitmap(small, null, android.graphics.Rect(0, 0, mask.width, mask.height), Paint(Paint.FILTER_BITMAP_FLAG))
        small.recycle()
        return full
    }

    // Podmiana maski na przygotowaną — na wątku UI (tam też maluje pędzel), to już tylko kopia pikseli.
    fun applyMask(prepared: Bitmap) {
        snapshot()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(prepared, 0f, 0f, null)
        prepared.recycle()
        hasAutoMask = true
        version++
    }

    // Zamknięcie edytora: kopie do "Cofnij" nigdzie nie są rysowane, więc można je od razu zwolnić (do 12 × kilka MB).
    fun releaseUndo() {
        undo.forEach { it.recycle() }
        undo.clear()
        undoCount = 0
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
    // source / factor: maska robocza i jej skala względem zdjęcia (podgląd liczy na mniejszej kopii).
    private fun finishedMask(source: Bitmap, factor: Float, check: () -> Unit = {}): Bitmap? {
        val smooth = edgeSmooth.coerceAtLeast(0f) * factor
        val shrink = edgeShrink.coerceAtLeast(0f) * factor
        if (edgeSmooth < 0.5f && edgeShrink < 0.5f) return null
        val radius = kotlin.math.ceil(smooth / 2f + shrink).toInt().coerceAtLeast(1)
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val alpha = IntArray(w * h) { pixels[it] ushr 24 }
        val blurred = boxBlur(boxBlur(alpha, w, h, radius, horizontal = true, check), w, h, radius, horizontal = false, check)
        val span = 2f * radius
        for (i in blurred.indices) {
            val distance = blurred[i] / 255f * span - radius // >0 = wewnątrz obiektu
            val a = if (smooth < 0.5f) { // (po przeskalowaniu wygładzenie poniżej pół piksela = ostry brzeg)
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
    // workSide: podgląd liczony na kopii o dłuższym boku najwyżej workSide px (szybciej, mniej pamięci); null = pełna
    // jakość (zapis). Wygładzenie i zwężenie krawędzi skalują się razem z kopią, więc podgląd wygląda tak samo.
    // Pośrednie bitmapy są zwalniane od razu (recycle) — przy kilku MB każda GC nie nadąża za suwakiem.
    fun render(rotate: Boolean = true, workSide: Int? = null, check: () -> Unit = {}): Bitmap? {
        val longest = maxOf(photo.width, photo.height)
        val factor = if (workSide != null && longest > workSide) workSide.toFloat() / longest else 1f
        val src = if (factor < 1f) scaled(photo, factor) else photo
        val msk = if (factor < 1f) scaled(mask, factor) else mask
        try {
            val cut = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val finished = finishedMask(msk, factor, check)
            Canvas(cut).apply {
                drawBitmap(src, 0f, 0f, null)
                drawBitmap(finished ?: msk, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            }
            finished?.recycle()
            check()
            val bounds = visibleBounds(cut)
            if (bounds == null) {
                cut.recycle()
                return null
            }
            var trimmed = Bitmap.createBitmap(cut, bounds.left, bounds.top, bounds.width(), bounds.height())
            if (trimmed !== cut) cut.recycle()
            check()
            val kind = StickerShapes.of(shape)
            if (kind.id != StickerShapes.NONE) trimmed = replaced(trimmed, applyShape(trimmed, kind))
            if (frame != Layer.FRAME_NONE) trimmed = replaced(trimmed, BoardRenderer().frameSticker(trimmed, frame, frameColor))
            check()
            val scale = outputSide.toFloat() / maxOf(trimmed.width, trimmed.height)
            val matrix = Matrix().apply {
                postScale(scale, scale)
                if (rotate) postRotate(rotation)
            }
            // createBitmap z macierzą sam powiększa płótno o obrócone rogi (tło przezroczyste).
            return replaced(trimmed, Bitmap.createBitmap(trimmed, 0, 0, trimmed.width, trimmed.height, matrix, true))
        } finally {
            if (src !== photo) src.recycle()
            if (msk !== mask) msk.recycle()
        }
    }

    // Następny krok obróbki gotowy → poprzedni wynik do zwolnienia (chyba że to ten sam obiekt).
    private fun replaced(old: Bitmap, new: Bitmap): Bitmap {
        if (new !== old) old.recycle()
        return new
    }

    private fun scaled(source: Bitmap, factor: Float): Bitmap = Bitmap.createScaledBitmap(
        source,
        (source.width * factor).roundToInt().coerceAtLeast(1),
        (source.height * factor).roundToInt().coerceAtLeast(1),
        true,
    )

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
        if (base !== source) base.recycle() // pomocniczy kwadrat (źródło zwalnia wywołujący)
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

// Maska pewności z ML Kit (na telefonie, zdjęcie nigdzie nie wychodzi). Zwraca (wartości, szerokość, wysokość)
// w rozmiarze WEJŚCIA modelu — maska i tak jest potem skalowana do zdjęcia (CutoutEditor.prepareMask).
// Model dostaje kopię najwyżej ML_SIDE px (szybciej, a wynik i tak jest gładko skalowany), a wynik odbieramy
// na wątku w tle (executor), nie na wątku UI — kopiowanie milionów liczb nie zacina ekranu.
private const val ML_SIDE = 1024

suspend fun autoMask(photo: Bitmap): Triple<FloatArray, Int, Int>? = withContext(Dispatchers.Default) {
    val longest = maxOf(photo.width, photo.height)
    val input = if (longest > ML_SIDE) {
        val f = ML_SIDE.toFloat() / longest
        Bitmap.createScaledBitmap(photo, (photo.width * f).roundToInt().coerceAtLeast(1), (photo.height * f).roundToInt().coerceAtLeast(1), true)
    } else {
        photo
    }
    val background = Dispatchers.Default.asExecutor()
    // Kopii "input" NIE zwalniamy ręcznie: po anulowaniu ML Kit może ją jeszcze czytać na swoim wątku.
    // To ok. 4 MB — zbierze ją GC, gdy model skończy.
    val iw = input.width
    val ih = input.height
    suspendCancellableCoroutine<Triple<FloatArray, Int, Int>?> { cont ->
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build(),
        )
        segmenter.process(InputImage.fromBitmap(input, 0))
            .addOnSuccessListener(background) { result ->
                val buffer = result.foregroundConfidenceMask
                if (buffer == null) {
                    segmenter.close()
                    cont.resume(null)
                } else {
                    buffer.rewind()
                    val values = FloatArray(buffer.remaining())
                    buffer.get(values)
                    segmenter.close() // dopiero po skopiowaniu wyniku (bufor może należeć do segmentera)
                    // Maska ma rozmiar obrazu wejściowego.
                    cont.resume(Triple(values, iw, ih).takeIf { values.size == iw * ih })
                }
            }
            .addOnFailureListener(background) { e ->
                segmenter.close()
                cont.resumeWithException(e)
            }
        cont.invokeOnCancellation { runCatching { segmenter.close() } }
    }
}
