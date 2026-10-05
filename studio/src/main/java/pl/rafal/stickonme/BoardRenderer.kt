package pl.rafal.stickonme

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Rysowanie tablicy zwykłym Canvasem Androida — ten sam kod dla podglądu na ekranie i dla eksportu do PNG,
// więc to, co widać, jest dokładnie tym, co się zapisze (jak jeden "silnik" renderujący w grze).
//
// Dwa rodzaje renderera (1.3.0):
// - podgląd (domyślny): obrazy warstw pomniejszone do PREVIEW_SIDE, pamięć podręczna z limitem zależnym od telefonu,
//   na wątku UI NIGDY nie dekoduje — rysuje tylko to, co już jest w pamięci (blocking = false), a brakujące obrazy
//   wczytuje w tle preload(). Teksty składane raz (cache StaticLayout).
// - eksport (BoardRenderer(FULL_SIDE, fullCacheBytes())): tworzony na czas zapisu w tle, obrazy w pełnej jakości,
//   potem do wyrzucenia.
class BoardRenderer(
    private val maxSide: Int = PREVIEW_SIDE,
    cacheBytes: Int = previewCacheBytes(),
) {
    companion object {
        const val PREVIEW_SIDE = 1024   // podgląd na ekranie telefonu — z zapasem nawet przy dużym powiększeniu warstwy
        const val FULL_SIDE = 4096      // eksport: praktycznie oryginał (zdjęcia w assets mają do 1800 px)

        // Ok. 1/6 pamięci aplikacji, ale nie więcej niż 64 MB (na słabszych telefonach mniej).
        fun previewCacheBytes(): Int = minOf(64L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 6).toInt()
        // Eksport: większy zapas — jeden obraz może mieć kilkadziesiąt MB.
        fun fullCacheBytes(): Int = minOf(256L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 4).toInt()

        // Renderer do eksportu w pełnej jakości (wołać w tle; po użyciu wystarczy go porzucić).
        fun forExport() = BoardRenderer(FULL_SIDE, fullCacheBytes())

        // Rozmiar LOGICZNY obrazu warstwy (w nim liczona jest Layer.scale): dłuższy bok najwyżej 1400 px — tak jak
        // do wersji 1.2, więc stare tablice wyglądają tak samo. Bitmapa w pamięci może być mniejsza (podgląd) albo
        // większa (eksport) — przy rysowaniu skalujemy ją do rozmiaru logicznego. Jak "jednostki" vs piksele w XAML.
        const val LOGICAL_SIDE = 1400
        fun logicalScale(width: Int, height: Int): Float = minOf(1f, LOGICAL_SIDE.toFloat() / maxOf(width, height).coerceAtLeast(1))
    }

    // Obraz warstwy w pamięci + jego rozmiar logiczny.
    class Img(val bitmap: Bitmap, val width: Float, val height: Float) {
        val k get() = width / bitmap.width // ile jednostek logicznych na piksel bitmapy
    }

    // Obrazy warstw — LruCache liczony w bajtach. Dostęp z wątku UI (podgląd) i z tła (miniatura, wczytywanie),
    // więc pod lockiem (synchronized ≈ lock w C#) — ale samo dekodowanie zawsze POZA lockiem, żeby UI nie czekał na tło.
    private val lock = Any()
    private val bitmaps = object : android.util.LruCache<String, Img>(cacheBytes) {
        override fun sizeOf(key: String, value: Img) = value.bitmap.byteCount
    }
    private val missing = HashSet<String>() // pliki, których nie ma — nie próbujemy ich co klatkę

    // Tylko z pamięci, bez dekodowania (wątek UI). null = jeszcze niewczytany albo brak pliku.
    fun peek(path: String?): Img? {
        if (path == null) return null
        return synchronized(lock) { bitmaps.get(path) }
    }

    private fun isMissing(path: String?): Boolean = path != null && synchronized(lock) { path in missing }

    // Z pamięci albo dekodowanie — BLOKUJE, więc tylko w tle (eksport, miniatura, preload).
    fun bitmap(path: String?): Img? {
        if (path == null) return null
        synchronized(lock) {
            val cached = bitmaps.get(path)
            if (cached != null) return cached
            if (path in missing) return null
        }
        val bmp = decode(path)
        synchronized(lock) {
            if (bmp == null) missing += path else bitmaps.put(path, bmp)
        }
        return bmp
    }

    // Obraz z pliku pomniejszony od razu przy dekodowaniu (ImageDecoder z docelowym rozmiarem — nie wczytuje pełnej
    // bitmapy). Alokacja programowa: podgląd "wypala" warstwy do zwykłej bitmapy, a to nie działa z bitmapami sprzętowymi.
    private fun decode(path: String): Img? = runCatching {
        var logicalW = 0f
        var logicalH = 0f
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val logical = logicalScale(w, h)
            logicalW = w * logical
            logicalH = h * logical
            val scale = minOf(1f, maxSide.toFloat() / maxOf(w, h).coerceAtLeast(1))
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        Img(bmp, logicalW.coerceAtLeast(1f), logicalH.coerceAtLeast(1f))
    }.getOrNull()

    // Co trzeba mieć w pamięci przed rysowaniem: obraz i (jeśli jest) ramka. Tło = frame NONE.
    data class Need(val path: String, val frame: String = Layer.FRAME_NONE, val frameColor: Long = 0)

    // Wczytanie w tle brakujących obrazów i policzenie ramek (kontur / cień to kosztowne liczenie — nie na wątku UI).
    // check() = punkt przerwania (ensureActive korutyny), jak CancellationToken w C#.
    fun preload(needs: Collection<Need>, check: () -> Unit = {}) {
        for (need in needs) {
            check()
            val img = bitmap(need.path) ?: continue
            if (need.frame != Layer.FRAME_NONE) framedCached(img.bitmap, need.frame, need.frameColor)
        }
    }

    // Podgląd poprosił o obraz, którego nie ma w pamięci (np. wypadł z LruCache przy wielu zdjęciach).
    // Ekran tablicy sprawdza to po rysowaniu i wczytuje brakujące w tle jeszcze raz.
    @Volatile var previewMiss = false

    // Zwolnienie pamięci podręcznych (zamknięcie tablicy, onTrimMemory). Bez recycle(): bitmapę może jeszcze rysować
    // animacja wyjścia ekranu — wystarczy puścić referencje, resztę zrobi GC (od Androida 8 piksele są w stercie Javy).
    fun clear() {
        synchronized(lock) {
            bitmaps.evictAll()
            missing.clear()
            framedCache.evictAll()
        }
        textCache.evictAll()
    }

    // Obraz warstwy: w podglądzie tylko z pamięci, w eksporcie z dekodowaniem.
    private fun image(path: String?, blocking: Boolean): Img? =
        if (blocking) bitmap(path)
        else peek(path).also { if (it == null && path != null && !isMissing(path)) previewMiss = true }

    // --- Tekst ---

    private fun textPaint(layer: Layer): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        val style = when {
            layer.bold && layer.italic -> Typeface.BOLD_ITALIC
            layer.bold -> Typeface.BOLD
            layer.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        typeface = Typeface.create(layer.font, style)
        textSize = layer.size
        color = layer.color.toInt()
    }

    private fun textLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .build()

    // Złożony tekst warstwy: właściwe litery + (opcjonalnie) grube litery obrysu pod spodem.
    // Farby mają już ustawiony cień, więc rysowanie niczego w nich nie zmienia — blok można rysować wiele razy.
    private class TextBlock(val fill: StaticLayout, val stroke: StaticLayout?)

    // Klucz = tylko to, co zmienia wygląd liter (bez położenia, skali i obrotu) — przesuwanie tekstu nie składa go od nowa.
    private data class TextKey(
        val text: String, val font: String, val bold: Boolean, val italic: Boolean, val color: Long, val size: Float,
        val outlineColor: Long, val outlineWidth: Float, val shadow: Boolean,
    )

    // Pamięć podręczna bloków tekstu — tylko dla podglądu (wątek UI). Rysowanie w tle (eksport, miniatura) składa tekst
    // od nowa, żeby dwa wątki nie rysowały jednego StaticLayout naraz.
    private val textCache = android.util.LruCache<TextKey, TextBlock>(64)

    private fun buildTextBlock(layer: Layer): TextBlock {
        val paint = textPaint(layer)
        val text = layer.text.ifEmpty { " " }
        // Szerokość = najdłuższa linia (bez zawijania — tekst łamiesz Enterem).
        val width = text.split('\n').maxOf { Layout.getDesiredWidth(it, paint) }.toInt().coerceAtLeast(1) + 2
        val shadowRadius = layer.size * 0.08f
        val shadowDy = layer.size * 0.05f
        val stroke = if (layer.outlineWidth > 0f) {
            // Obrys: grube litery w kolorze obrysu (z cieniem, jeśli włączony), właściwy tekst bez cienia na wierzchu.
            val strokePaint = TextPaint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = layer.outlineWidth * 2f
                strokeJoin = Paint.Join.ROUND
                color = layer.outlineColor.toInt()
                if (layer.shadow) setShadowLayer(shadowRadius, 0f, shadowDy, 0x99000000.toInt())
            }
            textLayout(text, strokePaint, width)
        } else {
            if (layer.shadow) paint.setShadowLayer(shadowRadius, 0f, shadowDy, 0x99000000.toInt())
            null
        }
        return TextBlock(textLayout(text, paint, width), stroke)
    }

    private fun textBlock(layer: Layer, blocking: Boolean): TextBlock {
        if (blocking) return buildTextBlock(layer)
        val key = TextKey(
            layer.text, layer.font, layer.bold, layer.italic, layer.color, layer.size,
            layer.outlineColor, layer.outlineWidth, layer.shadow,
        )
        val cached = textCache.get(key)
        if (cached != null) return cached
        val made = buildTextBlock(layer)
        textCache.put(key, made)
        return made
    }

    // --- Rozmiar warstwy (bez skali i obrotu) — do trafiania palcem i ramki zaznaczenia ---
    // blocking = false: podgląd na wątku UI (obraz tylko z pamięci; niewczytany = rozmiar zastępczy 400×400).

    fun contentSize(layer: Layer, blocking: Boolean = true): Pair<Float, Float> = when (layer.kind) {
        Layer.KIND_TEXT -> {
            val layout = textBlock(layer, blocking).fill
            val pad = layer.size * 0.25f
            (layout.width + pad * 2) to (layout.height + pad * 2)
        }
        else -> {
            val img = image(layer.path, blocking)
            val w = img?.width ?: 400f
            val h = img?.height ?: 400f
            val (padX, top, bottom) = framePadding(layer.frame, w, h)
            (w + padX * 2) to (h + top + bottom)
        }
    }

    // Marginesy ramki: (boki, góra, dół) w px obrazu.
    private fun framePadding(frame: String, w: Float, h: Float): Triple<Float, Float, Float> {
        val m = max(w, h)
        return when (frame) {
            Layer.FRAME_INSTAX -> Triple(m * 0.06f, m * 0.06f, m * 0.22f)
            Layer.FRAME_POLAROID -> Triple(m * 0.07f, m * 0.07f, m * 0.28f)
            Layer.FRAME_OUTLINE -> Triple(m * 0.035f, m * 0.035f, m * 0.035f)
            Layer.FRAME_SHADOW -> Triple(m * 0.05f, m * 0.04f, m * 0.07f)
            Layer.FRAME_STAMP -> Triple(m * 0.07f, m * 0.07f, m * 0.07f)
            Layer.FRAME_FILM -> Triple(m * 0.03f, m * 0.13f, m * 0.13f)
            else -> Triple(0f, 0f, 0f)
        }
    }

    // --- Rysowanie ---

    fun drawBackground(canvas: Canvas, board: Board, blocking: Boolean = true) {
        val rect = RectF(0f, 0f, board.width.toFloat(), board.height.toFloat())
        when (board.bgType) {
            Board.BG_COLOR -> canvas.drawRect(rect, Paint().apply { color = board.bgColor.toInt() })
            Board.BG_GRADIENT -> canvas.drawRect(rect, Paint().apply {
                shader = LinearGradient(0f, 0f, rect.width(), rect.height(), board.bgColor.toInt(), board.bgColor2.toInt(), Shader.TileMode.CLAMP)
            })
            Board.BG_IMAGE -> {
                val bmp = image(board.bgImage, blocking)?.bitmap // tło wypełnia tablicę, rozmiar logiczny bez znaczenia
                if (bmp == null) { // brak pliku albo (w podglądzie) jeszcze się wczytuje — na razie kolor tablicy
                    canvas.drawRect(rect, Paint().apply { color = board.bgColor.toInt() })
                } else {
                    // Wypełnij tablicę (jak "centerCrop"): skala większa z dwóch, wyśrodkowane.
                    val s = max(rect.width() / bmp.width, rect.height() / bmp.height)
                    val m = Matrix().apply {
                        postScale(s, s)
                        postTranslate((rect.width() - bmp.width * s) / 2f, (rect.height() - bmp.height * s) / 2f)
                    }
                    canvas.save()
                    canvas.clipRect(rect)
                    canvas.drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG))
                    canvas.restore()
                }
            }
            else -> Unit // przezroczyste
        }
    }

    // Warstwa w układzie tablicy: przesunięcie do (x, y), obrót, skala, a treść wyśrodkowana w (0, 0).
    // alpha < 255 = przygaszona (część poza tablicą). Warstwa pośrednia tylko w granicach warstwy (z zapasem na ramkę
    // i cień), a nie całego płótna — dużo mniejszy bufor poza ekranem.
    fun drawLayer(canvas: Canvas, layer: Layer, alpha: Int = 255, blocking: Boolean = true) {
        canvas.save()
        canvas.translate(layer.x, layer.y)
        canvas.rotate(layer.rotation)
        canvas.scale(layer.scale, layer.scale)
        if (alpha < 255) {
            val (w, h) = contentSize(layer, blocking)
            val m = max(w, h) * 0.25f
            canvas.saveLayerAlpha(RectF(-w / 2f - m, -h / 2f - m, w / 2f + m, h / 2f + m), alpha)
        }
        when (layer.kind) {
            Layer.KIND_TEXT -> drawText(canvas, layer, blocking)
            else -> drawImage(canvas, layer, blocking)
        }
        if (alpha < 255) canvas.restore()
        canvas.restore()
    }

    private fun drawText(canvas: Canvas, layer: Layer, blocking: Boolean) {
        val block = textBlock(layer, blocking)
        val layout = block.fill
        val w = layout.width.toFloat()
        val h = layout.height.toFloat()
        canvas.save()
        canvas.translate(-w / 2f, -h / 2f)
        layer.highlight?.let { hl ->
            // Zakreślacz: zaokrąglony prostokąt pod każdą linią.
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hl.toInt() }
            val padX = layer.size * 0.18f
            for (line in 0 until layout.lineCount) {
                val left = layout.getLineLeft(line) - padX
                val right = layout.getLineRight(line) + padX
                val top = layout.getLineTop(line).toFloat() + layer.size * 0.05f
                val bottom = layout.getLineBottom(line).toFloat() - layer.size * 0.02f
                canvas.drawRoundRect(RectF(left, top, right, bottom), layer.size * 0.2f, layer.size * 0.2f, p)
            }
        }
        block.stroke?.draw(canvas) // obrys pod literami (cień, jeśli włączony, jest już w farbie)
        layout.draw(canvas)
        if (layer.underline > 0f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = layer.color.toInt() }
            for (line in 0 until layout.lineCount) {
                val y = layout.getLineBaseline(line) + layer.size * 0.12f
                canvas.drawRoundRect(
                    RectF(layout.getLineLeft(line), y, layout.getLineRight(line), y + layer.underline),
                    layer.underline / 2f, layer.underline / 2f, p,
                )
            }
        }
        canvas.restore()
    }

    private fun drawImage(canvas: Canvas, layer: Layer, blocking: Boolean) {
        val img = image(layer.path, blocking)
        if (img == null) {
            // Brak pliku (usunięty z biblioteki): szary prostokąt zamiast pustego miejsca.
            // W podglądzie obraz, który jeszcze wczytuje się w tle, przez chwilę po prostu nie jest rysowany.
            if (blocking || isMissing(layer.path)) canvas.drawRect(RectF(-200f, -200f, 200f, 200f), Paint().apply { color = 0x55808080 })
            return
        }
        val bmp = img.bitmap
        val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // Piksele bitmapy → jednostki logiczne (ramka ma marginesy proporcjonalne do obrazu, więc skaluje się razem z nim).
        canvas.save()
        canvas.scale(img.k, img.k)
        if (layer.frame == Layer.FRAME_NONE) {
            canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, filter)
        } else {
            // Obraz z ramką liczony raz i trzymany w pamięci (kontur to 16 kopii sylwetki — co klatkę byłoby za drogo).
            val framed = framedCached(bmp, layer.frame, layer.frameColor)
            canvas.drawBitmap(framed.bitmap, framed.offsetX, framed.offsetY, filter)
        }
        canvas.restore()
    }

    // Obraz w ramce: bitmapa z zapasem na cień + przesunięcie jej lewego górnego rogu względem środka warstwy.
    private class Framed(val bitmap: Bitmap, val offsetX: Float, val offsetY: Float)
    private data class FrameKey(val source: Bitmap, val frame: String, val color: Long) // Bitmap porównywana po referencji
    private val framedCache = object : android.util.LruCache<FrameKey, Framed>(minOf(32L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8).toInt()) {
        // Klucz trzyma też obraz źródłowy (może już wypaść z cache obrazów) — liczymy go, żeby limit był uczciwy.
        override fun sizeOf(key: FrameKey, value: Framed) = value.bitmap.byteCount + key.source.byteCount
    }

    private fun framedCached(bmp: Bitmap, frame: String, color: Long): Framed {
        val key = FrameKey(bmp, frame, color)
        val cached = synchronized(lock) { framedCache.get(key) }
        if (cached != null) return cached
        val made = makeFramed(bmp, frame, color) // liczone poza lockiem
        synchronized(lock) { framedCache.put(key, made) }
        return made
    }

    // Gotowa naklejka z ramką (edytor wycinania): przycięta do widocznej treści, bez pamięci podręcznej.
    fun frameSticker(bmp: Bitmap, frame: String, color: Long): Bitmap {
        if (frame == Layer.FRAME_NONE) return bmp
        val framed = makeFramed(bmp, frame, color).bitmap
        val bounds = visibleBounds(framed) ?: return framed
        val trimmed = Bitmap.createBitmap(framed, bounds.left, bounds.top, bounds.width(), bounds.height())
        if (trimmed !== framed) framed.recycle() // createBitmap zwraca ten sam obiekt, gdy wycinek = całość
        return trimmed
    }

    private fun makeFramed(bmp: Bitmap, frame: String, color: Long): Framed {
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val m = max(w, h)
        val (padX, top, bottom) = framePadding(frame, w, h)
        val totalW = w + padX * 2
        val totalH = h + top + bottom
        val margin = m * (if (frame == Layer.FRAME_TAPE) 0.16f else 0.07f) // miejsce na cień karty / rozmycie / wystającą taśmę
        val out = Bitmap.createBitmap(
            (totalW + margin * 2).toInt().coerceAtLeast(1),
            (totalH + margin * 2).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(out)
        canvas.translate(margin, margin)
        val imgX = padX
        val imgY = top
        val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val card = RectF(0f, 0f, totalW, totalH)
        when (frame) {
            Layer.FRAME_INSTAX, Layer.FRAME_POLAROID -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toInt()
                    setShadowLayer(m * 0.02f, 0f, m * 0.012f, 0x66000000)
                }
                canvas.drawRoundRect(card, m * 0.012f, m * 0.012f, paint)
                canvas.drawBitmap(bmp, imgX, imgY, filter)
            }
            Layer.FRAME_STAMP -> {
                // Kartka znaczka, potem "wygryzione" półkola na brzegach (CLEAR = gumka), na końcu obraz.
                canvas.drawRect(card, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() })
                val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
                val r = padX * 0.34f
                val step = r * 2.7f
                var x = step / 2f
                while (x < totalW) {
                    canvas.drawCircle(x, 0f, r, hole)
                    canvas.drawCircle(x, totalH, r, hole)
                    x += step
                }
                var y = step / 2f
                while (y < totalH) {
                    canvas.drawCircle(0f, y, r, hole)
                    canvas.drawCircle(totalW, y, r, hole)
                    y += step
                }
                canvas.drawBitmap(bmp, imgX, imgY, filter)
            }
            Layer.FRAME_FILM -> {
                // Klisza: ciemny pasek, zdjęcie w środku, prostokątne otwory perforacji u góry i u dołu.
                canvas.drawRect(card, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF16171A.toInt() })
                canvas.drawBitmap(bmp, imgX, imgY, filter)
                val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
                val holeW = top * 0.34f
                val holeH = top * 0.42f
                val step = holeW * 2.1f
                var x = step / 2f
                while (x + holeW < totalW) {
                    canvas.drawRoundRect(RectF(x, (top - holeH) / 2f, x + holeW, (top + holeH) / 2f), holeW * 0.2f, holeW * 0.2f, hole)
                    val by = totalH - bottom / 2f
                    canvas.drawRoundRect(RectF(x, by - holeH / 2f, x + holeW, by + holeH / 2f), holeW * 0.2f, holeW * 0.2f, hole)
                    x += step
                }
            }
            Layer.FRAME_OUTLINE -> {
                // Kontur: sylwetka obrazka w kolorze ramki, narysowana w 16 kierunkach wokół (jak "obrys" w programach).
                val silhouette = Paint(filter).apply { colorFilter = PorterDuffColorFilter(color.toInt(), PorterDuff.Mode.SRC_IN) }
                val r = padX * 0.85f
                for (i in 0 until 16) {
                    val a = Math.PI * 2 * i / 16
                    canvas.drawBitmap(bmp, imgX + (cos(a) * r).toFloat(), imgY + (sin(a) * r).toFloat(), silhouette)
                }
                canvas.drawBitmap(bmp, imgX, imgY, filter)
            }
            Layer.FRAME_SHADOW -> {
                val alpha = bmp.extractAlpha()
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = 0x88000000.toInt()
                    maskFilter = BlurMaskFilter(m * 0.025f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawBitmap(alpha, imgX + m * 0.01f, imgY + m * 0.025f, shadow)
                alpha.recycle() // pomocnicza sylwetka — już narysowana, nikt jej nie trzyma
                canvas.drawBitmap(bmp, imgX, imgY, filter)
            }
            Layer.FRAME_TAPE -> {
                canvas.drawBitmap(bmp, imgX, imgY, filter)
                // Dwa kawałki taśmy na górnych rogach, półprzezroczyste.
                val tape = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ((color and 0x00FFFFFFL) or 0xB0000000L).toInt() }
                val tw = m * 0.28f
                val th = m * 0.08f
                for ((cx, angle) in listOf(imgX + w * 0.1f to -35f, imgX + w * 0.9f to 35f)) {
                    canvas.save()
                    canvas.translate(cx, imgY + th * 0.2f)
                    canvas.rotate(angle)
                    canvas.drawRect(RectF(-tw / 2, -th / 2, tw / 2, th / 2), tape)
                    canvas.restore()
                }
            }
            else -> canvas.drawBitmap(bmp, imgX, imgY, filter)
        }
        return Framed(out, -totalW / 2f - margin, -totalH / 2f - margin)
    }

    // Cała tablica: tło i warstwy przycięte do jej obszaru (to, co wystaje poza krawędź, nie trafia do pliku).
    // Zawsze z dekodowaniem (blocking) — wołane w tle: eksport, tapeta, miniatura tablicy.
    fun render(board: Board, limit: Int = max(board.width, board.height)): Bitmap {
        val s = minOf(1f, limit.toFloat() / max(board.width, board.height))
        val out = Bitmap.createBitmap((board.width * s).toInt().coerceAtLeast(1), (board.height * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.scale(s, s)
        canvas.clipRect(0f, 0f, board.width.toFloat(), board.height.toFloat())
        drawBackground(canvas, board)
        board.layers.forEach { drawLayer(canvas, it) }
        return out
    }

    // Naklejka z tablicy: bez tła, przycięta do widocznej treści (np. kilka naklejek sklejonych w jedną).
    fun renderSticker(board: Board): Bitmap? {
        val full = render(board.copy(bgType = Board.BG_TRANSPARENT))
        val bounds = visibleBounds(full)
        if (bounds == null) {
            full.recycle()
            return null
        }
        val trimmed = Bitmap.createBitmap(full, bounds.left, bounds.top, bounds.width(), bounds.height())
        if (trimmed !== full) full.recycle() // pełna tablica już niepotrzebna (A4 to ok. 35 MB)
        return trimmed
    }

    private fun visibleBounds(bitmap: Bitmap): android.graphics.Rect? {
        val w = bitmap.width
        val row = IntArray(w)
        var top = -1
        var bottom = -1
        var left = w
        var right = -1
        for (y in 0 until bitmap.height) {
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

    // Która warstwa jest pod punktem (współrzędne tablicy)? Od góry stosu — jak kliknięcie w programie graficznym.
    // Wątek UI → rozmiar bez dekodowania.
    fun hit(board: Board, x: Float, y: Float): Layer? = board.layers.lastOrNull { layer ->
        val (w, h) = contentSize(layer, blocking = false)
        // Punkt w układzie warstwy: odwrotność przesunięcia, obrotu i skali.
        val dx = x - layer.x
        val dy = y - layer.y
        val rad = Math.toRadians(-layer.rotation.toDouble())
        val lx = ((dx * cos(rad) - dy * sin(rad)) / layer.scale).toFloat()
        val ly = ((dx * sin(rad) + dy * cos(rad)) / layer.scale).toFloat()
        val slack = 24f / layer.scale // trochę zapasu dla palca
        lx >= -w / 2 - slack && lx <= w / 2 + slack && ly >= -h / 2 - slack && ly <= h / 2 + slack
    }
}
