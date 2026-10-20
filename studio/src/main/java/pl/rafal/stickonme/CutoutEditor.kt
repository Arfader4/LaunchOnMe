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
    // Kadr (jak w zwykłym edytorze zdjęć): prostokąt w ułamkach zdjęcia 0..1, NIEdestrukcyjny — maska się nie zmienia,
    // a kadr można przesuwać i zmieniać do końca edycji. Kształt (serce, koło…) wypełnia cały kadr.
    // aspect = zablokowane proporcje kadru w PIKSELACH (szer./wys.), null = dowolne.
    var crop by mutableStateOf(CropBox.FULL)
    var aspect by mutableStateOf<Float?>(null)
    val photoAspect: Float get() = photo.width.toFloat() / photo.height

    // Kształt z wyboru: kształty „kwadratowe” (koło, serce, gwiazda…) od razu ustawiają kadr 1:1 — inaczej by się rozciągały.
    fun chooseShape(id: String) {
        shape = id
        if (StickerShapes.of(id).square) changeAspect(1f)
    }

    // Nowe proporcje: największy prostokąt o tych proporcjach mieszczący się w obecnym kadrze, na jego środku.
    fun changeAspect(value: Float?) {
        aspect = value
        if (value == null) return
        val c = crop
        val n = value / photoAspect // proporcje w ułamkach zdjęcia
        var w = c.width
        var h = c.height
        if (w / h > n) w = h * n else h = w / n
        val cx = (c.left + c.right) / 2f
        val cy = (c.top + c.bottom) / 2f
        crop = CropBox(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f).clamped()
    }

    fun resetCrop() {
        crop = CropBox.FULL
        aspect = null
        shape = StickerShapes.NONE
    }

    // --- Kroki edycji (jak w edytorach w telefonie): wejście w narzędzie → zmiany → ✓ zatwierdź albo ✕ anuluj. ---
    // Ustawienia wyniku w jednym obiekcie — do przywrócenia po ✕ (jak memento).
    private data class Settings(
        val edgeSmooth: Float, val edgeShrink: Float, val shape: String, val frame: String, val frameColor: Long,
        val rotation: Float, val outputSide: Int, val crop: CropBox, val aspect: Float?,
    )

    private var stepMask: Bitmap? = null
    private var stepVersion = 0
    private var stepSettings: Settings? = null

    fun beginStep() {
        releaseUndo() // Cofnij / Ponów działa w obrębie jednego narzędzia
        stepMask?.recycle()
        stepMask = mask.copy(Bitmap.Config.ALPHA_8, false)
        stepVersion = version
        stepSettings = Settings(edgeSmooth, edgeShrink, shape, frame, frameColor, rotation, outputSide, crop, aspect)
    }

    fun cancelStep() {
        val saved = stepMask
        stepMask = null
        if (saved != null) {
            if (version != stepVersion) restore(saved) else saved.recycle()
        }
        stepSettings?.let { st ->
            edgeSmooth = st.edgeSmooth
            edgeShrink = st.edgeShrink
            shape = st.shape
            frame = st.frame
            frameColor = st.frameColor
            rotation = st.rotation
            outputSide = st.outputSide
            crop = st.crop
            aspect = st.aspect
        }
        stepSettings = null
        releaseUndo()
    }

    fun commitStep() {
        stepMask?.recycle()
        stepMask = null
        stepSettings = null
        releaseUndo()
    }

    private val undo = ArrayDeque<Bitmap>()
    private var undoCount by mutableIntStateOf(0) // stan Compose: przycisk "Cofnij" wie, kiedy się włączyć
    val canUndo get() = undoCount > 0
    // „Ponów”: kroki zdjęte przez Cofnij. Każda nowa zmiana maski czyści tę listę (jak Ctrl+Y w edytorach).
    private val redo = ArrayDeque<Bitmap>()
    private var redoCount by mutableIntStateOf(0)
    val canRedo get() = redoCount > 0

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
        clearRedo() // nowa zmiana = stara „przyszłość” przepada
    }

    fun undoLast() {
        val previous = undo.removeLastOrNull() ?: return
        undoCount = undo.size
        redo.addLast(mask.copy(Bitmap.Config.ALPHA_8, false))
        redoCount = redo.size
        restore(previous)
    }

    fun redoLast() {
        val next = redo.removeLastOrNull() ?: return
        redoCount = redo.size
        undo.addLast(mask.copy(Bitmap.Config.ALPHA_8, false))
        undoCount = undo.size
        restore(next)
    }

    private fun restore(state: Bitmap) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(state, 0f, 0f, null)
        state.recycle()
        version++
    }

    private fun clearRedo() {
        redo.forEach { it.recycle() }
        redo.clear()
        redoCount = 0
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
        // Próg pewności: poniżej 0,3 = tło (0), powyżej 0,7 = obiekt (255), pomiędzy — miękkie przejście.
        // Bez progu tło miało kilka % krycia: niewidoczne, ale naklejka zostawała prostokątem całego zdjęcia.
        val pixels = IntArray(width * height) { i -> (((confidence[i] - 0.3f) / 0.4f).coerceIn(0f, 1f) * 255).roundToInt() shl 24 }
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
        clearRedo()
    }

    // Zamknięcie edytora: wszystkie kopie robocze do zwolnienia.
    fun release() {
        releaseUndo()
        stepMask?.recycle()
        stepMask = null
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

    // Gotowa naklejka = cutout() (zdjęcie × maska) → finish() (kadr, kształt, przycięcie, ramka, skala, obrót).
    // check() = punkt przerwania (podgląd w tle). workSide: podgląd liczony na kopii o dłuższym boku najwyżej
    // workSide px; null = pełna jakość (zapis). null = nic nie zostało.
    fun render(rotate: Boolean = true, workSide: Int? = null, check: () -> Unit = {}): Bitmap? {
        val cut = cutout(workSide, check)
        try {
            return finish(cut, rotate, check)
        } finally {
            cut.recycle() // finish zawsze tworzy nową bitmapę
        }
    }

    // Zdjęcie × maska (z wykończoną krawędzią) w PEŁNYM kadrze zdjęcia — bez przycinania, bo kadr liczy się
    // w ułamkach całego zdjęcia (ekran kadrowania pokazuje właśnie ten obraz).
    // Wygładzenie i zwężenie krawędzi skalują się razem z kopią roboczą, więc podgląd wygląda tak samo jak zapis.
    fun cutout(workSide: Int? = null, check: () -> Unit = {}): Bitmap {
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
            return cut
        } finally {
            if (src !== photo) src.recycle()
            if (msk !== mask) msk.recycle()
        }
    }

    // Kadr → kształt → przycięcie do widocznych pikseli → ramka → skala do outputSide → obrót.
    // Wejścia "cut" NIE zwalnia (podgląd trzyma je dalej); zawsze zwraca NOWĄ bitmapę albo null (nic nie zostało).
    fun finish(cut: Bitmap, rotate: Boolean = true, check: () -> Unit = {}): Bitmap? {
        var b = cut
        fun next(new: Bitmap) { // poprzedni krok do zwolnienia — chyba że to wejście albo ten sam obiekt
            if (new !== b && b !== cut) b.recycle()
            b = new
        }
        val c = crop
        if (c != CropBox.FULL) {
            val x = (c.left * cut.width).roundToInt().coerceIn(0, cut.width - 1)
            val y = (c.top * cut.height).roundToInt().coerceIn(0, cut.height - 1)
            val w = (c.width * cut.width).roundToInt().coerceIn(1, cut.width - x)
            val h = (c.height * cut.height).roundToInt().coerceIn(1, cut.height - y)
            next(Bitmap.createBitmap(cut, x, y, w, h))
        }
        val kind = StickerShapes.of(shape)
        if (kind.id != StickerShapes.NONE) next(applyShape(b, kind))
        check()
        val bounds = visibleBounds(b)
        if (bounds == null) {
            if (b !== cut) b.recycle()
            return null
        }
        if (bounds.width() != b.width || bounds.height() != b.height) {
            next(Bitmap.createBitmap(b, bounds.left, bounds.top, bounds.width(), bounds.height()))
        }
        if (frame != Layer.FRAME_NONE) next(BoardRenderer().frameSticker(b, frame, frameColor))
        check()
        val scale = outputSide.toFloat() / maxOf(b.width, b.height)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (rotate) postRotate(rotation)
        }
        // createBitmap z macierzą sam powiększa płótno o obrócone rogi (tło przezroczyste).
        next(Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true))
        // Gdyby createBitmap oddał wejście (skala 1, bez obrotu) — kopia, żeby wynik był zawsze nowy.
        return if (b === cut) cut.copy(Bitmap.Config.ARGB_8888, false) else b
    }

    private fun scaled(source: Bitmap, factor: Float): Bitmap = Bitmap.createScaledBitmap(
        source,
        (source.width * factor).roundToInt().coerceAtLeast(1),
        (source.height * factor).roundToInt().coerceAtLeast(1),
        true,
    )

    // Kształt wypełnia cały (wykadrowany) obraz: maska ze ścieżki kształtu w trybie DST_IN. Wejścia nie zwalnia.
    private fun applyShape(source: Bitmap, kind: StickerShapes.Shape): Bitmap {
        val path = StickerShapes.path(kind.id, source.width.toFloat(), source.height.toFloat()) ?: return source
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val shapeMask = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ALPHA_8)
        Canvas(shapeMask).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
        Canvas(out).drawBitmap(shapeMask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        shapeMask.recycle()
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

// Kadr w ułamkach zdjęcia (0..1), niezmienny (jak struct/record w C#) — nowa wartość = nowy obiekt, więc Compose widzi zmianę.
data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    // Przesunięty z powrotem do środka zdjęcia, gdyby wystawał (rozmiar bez zmian, o ile się mieści).
    fun clamped(): CropBox {
        val w = width.coerceIn(MIN, 1f)
        val h = height.coerceIn(MIN, 1f)
        val l = left.coerceIn(0f, 1f - w)
        val t = top.coerceIn(0f, 1f - h)
        return CropBox(l, t, l + w, t + h)
    }

    companion object {
        val FULL = CropBox(0f, 0f, 1f, 1f)
        const val MIN = 0.05f // najmniejszy kadr: 5% boku zdjęcia
    }
}
