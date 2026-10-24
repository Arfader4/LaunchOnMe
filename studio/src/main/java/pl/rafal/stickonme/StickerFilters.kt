package pl.rafal.stickonme

import android.graphics.Bitmap
import androidx.annotation.StringRes
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// --- Filtry zdjęć i naklejek (SOM 1.5.0) ---
// Zestaw filtrów to tekst "oil,sepia,paper" (jak flagi [Flags] enum w C#, tylko czytelne w JSON tablicy).
// Kolejność nakładania jest stała (kolejność listy `all`), więc wynik nie zależy od kolejności dotykania.
// Filtry kolorów i faktur można łączyć dowolnie; styl malarski — jeden naraz (nakładany pierwszy).
// Liczymy na pikselach BEZ premultiplikacji (getPixels): alfa zostaje nietknięta, przezroczyste tło naklejki zostaje
// przezroczyste. Efekty zależą od położenia w UŁAMKACH obrazu (x / szerokość), a promienie od rozmiaru obrazu —
// dzięki temu podgląd (ok. 1024 px) i eksport (do 4096 px) wyglądają tak samo.
object StickerFilters {
    const val BW = "bw"
    const val SEPIA = "sepia"
    const val VINTAGE = "vintage"
    const val PRINT = "print"
    const val PAPER = "paper"
    const val GLASS = "glass"
    const val OIL = "oil"
    const val WATERCOLOR = "watercolor"
    const val PENCIL = "pencil"
    const val COMIC = "comic"

    class Filter(val id: String, @StringRes val labelRes: Int, val painting: Boolean = false)

    // Kolejność = kolejność nakładania: najpierw malowanie, potem kolor, na końcu faktury i odblask.
    val all = listOf(
        Filter(OIL, R.string.som_filter_oil, painting = true),
        Filter(WATERCOLOR, R.string.som_filter_watercolor, painting = true),
        Filter(PENCIL, R.string.som_filter_pencil, painting = true),
        Filter(COMIC, R.string.som_filter_comic, painting = true),
        Filter(BW, R.string.som_filter_bw),
        Filter(SEPIA, R.string.som_filter_sepia),
        Filter(VINTAGE, R.string.som_filter_vintage),
        Filter(PRINT, R.string.som_filter_print),
        Filter(PAPER, R.string.som_filter_paper),
        Filter(GLASS, R.string.som_filter_glass),
    )
    val looks = all.filter { !it.painting }
    val paintings = all.filter { it.painting }

    private fun isPainting(id: String) = all.any { it.id == id && it.painting }

    // Nieznane nazwy (np. z nowszej wersji aplikacji) są pomijane — jak TryParse.
    fun parse(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(',').map { it.trim() }.filter { id -> all.any { it.id == id } }

    fun has(value: String, id: String) = id in parse(value)

    // Włącz / wyłącz filtr. Nowy styl malarski zastępuje poprzedni.
    fun toggle(value: String, id: String): String {
        val current = parse(value).toMutableSet()
        if (id in current) {
            current -= id
        } else {
            if (isPainting(id)) current.removeAll { isPainting(it) }
            current += id
        }
        return all.map { it.id }.filter { it in current }.joinToString(",")
    }

    // Zwraca NOWĄ, mutowalną bitmapę ARGB_8888 albo samo źródło, gdy filtrów brak. Źródła nie zwalnia.
    // check() = punkt przerwania (ensureActive korutyny), jak CancellationToken w C#.
    fun apply(source: Bitmap, value: String, check: () -> Unit = {}): Bitmap {
        val list = parse(value)
        if (list.isEmpty()) return source
        val painting = list.firstOrNull { isPainting(it) }
        val out = if (painting != null) paint(source, painting, check) else source.copy(Bitmap.Config.ARGB_8888, true)
        val looks = list.filter { !isPainting(it) }.map { LOOK_CODES.getValue(it) }.toIntArray()
        if (looks.isNotEmpty()) applyLooks(out, looks, check)
        return out
    }

    // --- Filtry punktowe (kolor, faktura, odblask): każdy piksel osobno, wiersz po wierszu (mało pamięci także przy 12 Mpx) ---

    private const val C_BW = 0
    private const val C_SEPIA = 1
    private const val C_VINTAGE = 2
    private const val C_PRINT = 3
    private const val C_PAPER = 4
    private const val C_GLASS = 5
    private val LOOK_CODES = mapOf(BW to C_BW, SEPIA to C_SEPIA, VINTAGE to C_VINTAGE, PRINT to C_PRINT, PAPER to C_PAPER, GLASS to C_GLASS)

    private fun applyLooks(bitmap: Bitmap, codes: IntArray, check: () -> Unit) {
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        // Kropka rastra „wydruku” w pikselach — ok. 150 kropek na dłuższym boku.
        val cell = max(3f, max(w, h) / 150f)
        for (y in 0 until h) {
            if (y % 32 == 0) check()
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            val fy = (y + 0.5f) / h
            for (x in 0 until w) {
                val c = row[x]
                val a = c ushr 24
                if (a == 0) continue
                var r = ((c shr 16) and 255).toFloat()
                var g = ((c shr 8) and 255).toFloat()
                var b = (c and 255).toFloat()
                val fx = (x + 0.5f) / w
                // Odległość od środka: 0 w środku, 1 w rogu (do winiety).
                val dx = fx - 0.5f
                val dy = fy - 0.5f
                val d = (dx * dx + dy * dy) * 2f
                for (code in codes) {
                    when (code) {
                        C_BW -> {
                            val l = ((0.299f * r + 0.587f * g + 0.114f * b) - 128f) * 1.12f + 128f
                            r = l; g = l; b = l
                        }
                        C_SEPIA -> {
                            val nr = 0.393f * r + 0.769f * g + 0.189f * b
                            val ng = 0.349f * r + 0.686f * g + 0.168f * b
                            val nb = 0.272f * r + 0.534f * g + 0.131f * b
                            r = nr; g = ng; b = nb
                        }
                        C_VINTAGE -> {
                            // Mniej nasycenia, „podniesiona” czerń (wyblakła odbitka), ciepły odcień i winieta.
                            val l = 0.299f * r + 0.587f * g + 0.114f * b
                            r = l + (r - l) * 0.7f
                            g = l + (g - l) * 0.7f
                            b = l + (b - l) * 0.7f
                            r = r * 0.86f + 34f
                            g = g * 0.82f + 26f
                            b = b * 0.70f + 30f
                            val v = 1f - 0.45f * d * d
                            r *= v; g *= v; b *= v
                        }
                        C_PRINT -> {
                            // Raster półtonowy pod kątem 45° (jak w gazecie / druku), delikatnie zmieszany z obrazem.
                            val l = 0.299f * r + 0.587f * g + 0.114f * b
                            val u = (x + y) * 0.7071f / cell
                            val v = (x - y) * 0.7071f / cell
                            val du = u - floor(u) - 0.5f
                            val dv = v - floor(v) - 0.5f
                            val dist = sqrt(du * du + dv * dv)
                            val radius = sqrt(1f - l / 255f) * 0.62f
                            val ink = smoothstep(radius + 0.08f, radius - 0.08f, dist)
                            val k = 1.04f - 0.2f * ink
                            r = (r * 0.9f + 14f) * k
                            g = (g * 0.9f + 13f) * k
                            b = (b * 0.88f + 10f) * k
                        }
                        C_PAPER -> {
                            // Stary papier: sepia w połowie, kolor kartki, plamy (szum w dwóch skalach), ziarno i ciemniejsze brzegi.
                            val l = 0.299f * r + 0.587f * g + 0.114f * b
                            r = l + (r - l) * 0.35f
                            g = l + (g - l) * 0.35f
                            b = l + (b - l) * 0.35f
                            val blot = noise(fx * 6f, fy * 6f) * 0.6f + noise(fx * 23f + 7f, fy * 23f + 3f) * 0.4f
                            val grain = hash(x, y)
                            var k = (0.86f + 0.14f * blot) * (0.95f + 0.05f * grain) * (1f - 0.35f * d * d)
                            if (blot > 0.72f) k *= 1f - (blot - 0.72f) * 0.9f // plamy „po kawie”
                            r = (r * 0.85f + 233f * 0.15f) * k * (233f / 255f)
                            g = (g * 0.85f + 214f * 0.15f) * k * (214f / 255f)
                            b = (b * 0.85f + 170f * 0.15f) * k * (170f / 255f)
                        }
                        C_GLASS -> {
                            // Odblask jak na zdjęciu za szkłem: szeroki i wąski skośny pas + jaśniejszy lewy górny róg.
                            // Mieszanie „screen”: rozjaśnia, nigdy nie przyciemnia (jak tryb Ekran w programach graficznych).
                            val p = fx * 0.8f + fy * 0.6f
                            val t1 = (p - 0.42f) / 0.08f
                            val t2 = (p - 0.6f) / 0.025f
                            val corner = max(0f, 1f - sqrt(fx * fx + fy * fy) / 0.6f)
                            val glare = min(0.85f, exp(-t1 * t1) * 0.5f + exp(-t2 * t2) * 0.35f + corner * corner * 0.3f)
                            r = 255f - (255f - r) * (1f - glare)
                            g = 255f - (255f - g) * (1f - glare)
                            b = 255f - (255f - b) * (1f - glare) + 4f
                        }
                    }
                }
                row[x] = (a shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
            }
            bitmap.setPixels(row, 0, w, 0, y, w, 1)
        }
    }

    // --- Style malarskie: liczone na kopii ≤ 1024 px (pędzel i tak nie ma drobnych szczegółów), potem powiększane ---

    private const val PAINT_SIDE = 1024

    private fun paint(source: Bitmap, id: String, check: () -> Unit): Bitmap {
        val w = source.width
        val h = source.height
        val k = min(1f, PAINT_SIDE.toFloat() / max(w, h))
        val sw = (w * k).roundToInt().coerceAtLeast(1)
        val sh = (h * k).roundToInt().coerceAtLeast(1)
        val small = if (k < 1f) Bitmap.createScaledBitmap(source, sw, sh, true) else source
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (small !== source) small.recycle()
        fillTransparent(px)
        check()
        val side = max(sw, sh)
        val painted = when (id) {
            OIL -> oil(px, sw, sh, side, check)
            WATERCOLOR -> watercolor(px, sw, sh, side, check)
            PENCIL -> pencil(px, sw, sh, side, check)
            else -> comic(px, sw, sh, side, check)
        }
        // Wynik bez przezroczystości (alfa 255): przy powiększaniu kolory brzegów się nie zgubią (premultiplikacja
        // zeruje kolor pikseli z alfą 0). Właściwą alfę oryginału przepisujemy na końcu, w pełnym rozmiarze.
        val paintedSmall = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        paintedSmall.setPixels(painted, 0, sw, 0, 0, sw, sh)
        var big = if (sw != w || sh != h) Bitmap.createScaledBitmap(paintedSmall, w, h, true) else paintedSmall
        if (big !== paintedSmall) paintedSmall.recycle()
        if (!big.isMutable || big.config != Bitmap.Config.ARGB_8888) {
            val copy = big.copy(Bitmap.Config.ARGB_8888, true)
            big.recycle()
            big = copy
        }
        check()
        val src = IntArray(w)
        val dst = IntArray(w)
        for (y in 0 until h) {
            source.getPixels(src, 0, w, 0, y, w, 1)
            big.getPixels(dst, 0, w, 0, y, w, 1)
            for (x in 0 until w) dst[x] = (src[x] and 0xFF000000.toInt()) or (dst[x] and 0x00FFFFFF)
            big.setPixels(dst, 0, w, 0, y, w, 1)
        }
        return big
    }

    // Przezroczyste piksele naklejki mają kolor czarny (albo przypadkowy) — malowanie wciągnęłoby go w brzeg jako ciemną
    // obwódkę. Dlatego przed malowaniem wypełniamy je średnim kolorem widocznej części (alfa i tak wraca na końcu).
    private fun fillTransparent(px: IntArray) {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (c in px) {
            if ((c ushr 24) >= 128) {
                r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255; n++
            }
        }
        if (n == 0L || n == px.size.toLong()) return
        val mean = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        for (i in px.indices) if ((px[i] ushr 24) < 128) px[i] = mean
    }

    // Olej: filtr Kuwahary (z czterech kwadratów wokół piksela bierze średni kolor najgładszego) — płaskie plamy
    // farby z ostrymi krawędziami. Do tego trochę więcej nasycenia.
    private fun oil(px: IntArray, w: Int, h: Int, side: Int, check: () -> Unit): IntArray {
        val out = kuwahara(px, w, h, max(2, (side / 110f).roundToInt()), check)
        for (i in out.indices) out[i] = saturate(out[i], 1.18f)
        return out
    }

    // Akwarela: mniejsze plamy, rozjaśnione i lekko spłaszczone kolory, ciemniejszy pigment na granicach plam
    // i ziarno papieru akwarelowego.
    private fun watercolor(px: IntArray, w: Int, h: Int, side: Int, check: () -> Unit): IntArray {
        val out = kuwahara(px, w, h, max(2, (side / 200f).roundToInt()), check)
        val lum = IntArray(w * h) { luminance(out[it]) }
        val soft = boxBlur(lum, w, h, max(2, side / 160))
        check()
        val grainScale = side / 180f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = out[i]
                val edge = min(0.4f, abs(lum[i] - soft[i]) / 255f * 2.6f)
                val paper = 0.92f + 0.08f * noise(x / grainScale * 6f, y / grainScale * 6f)
                val k = (1f - edge) * paper
                var r = ((c shr 16) and 255).toFloat()
                var g = ((c shr 8) and 255).toFloat()
                var b = (c and 255).toFloat()
                r = (posterize(r, 28f) * 0.5f + r * 0.5f) * 0.82f + 44f
                g = (posterize(g, 28f) * 0.5f + g * 0.5f) * 0.82f + 44f
                b = (posterize(b, 28f) * 0.5f + b * 0.5f) * 0.82f + 44f
                out[i] = rgb(r * k, g * k, b * k)
            }
        }
        return out
    }

    // Ołówek: klasyczny szkic — jasność, rozmyty negatyw i mieszanie „color dodge” (jak w Photoshopie), potem
    // mocniejsza kreska i ziarno papieru.
    private fun pencil(px: IntArray, w: Int, h: Int, side: Int, check: () -> Unit): IntArray {
        val lum = IntArray(w * h) { luminance(px[it]) }
        val inverted = IntArray(w * h) { 255 - lum[it] }
        val blurred = boxBlur(inverted, w, h, max(2, side / 150))
        check()
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val dodge = min(255f, lum[i] * 255f / (256f - blurred[i]))
                val line = 255f - (255f - dodge) * 1.35f
                val v = line * (0.94f + 0.06f * hash(x, y))
                out[i] = rgb(v, v, v * 0.97f)
            }
        }
        return out
    }

    // Komiks: wygładzone plamy, kolory spłaszczone do kilku poziomów i mocniej nasycone, czarny kontur na krawędziach.
    private fun comic(px: IntArray, w: Int, h: Int, side: Int, check: () -> Unit): IntArray {
        val out = kuwahara(px, w, h, max(2, (side / 280f).roundToInt()), check)
        val lum = IntArray(w * h) { luminance(out[it]) }
        check()
        val res = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                // Sobel 3×3 na jasności (krawędzie przy brzegu obrazu — powielony piksel brzegowy).
                val x0 = max(0, x - 1)
                val x1 = min(w - 1, x + 1)
                val y0 = max(0, y - 1)
                val y1 = min(h - 1, y + 1)
                val gx = (lum[y0 * w + x1] + 2 * lum[y * w + x1] + lum[y1 * w + x1]) - (lum[y0 * w + x0] + 2 * lum[y * w + x0] + lum[y1 * w + x0])
                val gy = (lum[y1 * w + x0] + 2 * lum[y1 * w + x] + lum[y1 * w + x1]) - (lum[y0 * w + x0] + 2 * lum[y0 * w + x] + lum[y0 * w + x1])
                val mag = sqrt((gx * gx + gy * gy).toFloat())
                val ink = 1f - smoothstep(70f, 140f, mag)
                val c = saturate(out[i], 1.35f)
                val r = posterize(((c shr 16) and 255).toFloat(), 51f)
                val g = posterize(((c shr 8) and 255).toFloat(), 51f)
                val b = posterize((c and 255).toFloat(), 51f)
                res[i] = rgb(r * ink, g * ink, b * ink)
            }
        }
        return res
    }

    // Kuwahara przez tablice sum (summed-area table, jak „integral image” w OpenCV): średnia i wariancja dowolnego
    // prostokąta w stałym czasie, więc koszt nie rośnie z promieniem. Wynik bez przezroczystości (alfa 255).
    private fun kuwahara(px: IntArray, w: Int, h: Int, radius: Int, check: () -> Unit): IntArray {
        val sw = w + 1
        val sumR = IntArray(sw * (h + 1))
        val sumG = IntArray(sw * (h + 1))
        val sumB = IntArray(sw * (h + 1))
        val sumL = IntArray(sw * (h + 1))
        val sumL2 = LongArray(sw * (h + 1))
        for (y in 0 until h) {
            var rr = 0; var gg = 0; var bb = 0; var ll = 0; var l2 = 0L
            for (x in 0 until w) {
                val c = px[y * w + x]
                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255
                val l = (r * 77 + g * 150 + b * 29) shr 8
                rr += r; gg += g; bb += b; ll += l; l2 += (l * l).toLong()
                val i = (y + 1) * sw + x + 1
                val up = y * sw + x + 1
                sumR[i] = sumR[up] + rr
                sumG[i] = sumG[up] + gg
                sumB[i] = sumB[up] + bb
                sumL[i] = sumL[up] + ll
                sumL2[i] = sumL2[up] + l2
            }
        }
        check()
        val out = IntArray(w * h)
        // Kwadraty: lewy górny, prawy górny, lewy dolny, prawy dolny (każdy zawiera piksel środkowy).
        for (y in 0 until h) {
            if (y % 32 == 0) check()
            for (x in 0 until w) {
                var bestVar = Float.MAX_VALUE
                var best = 0
                for (q in 0 until 4) {
                    val left = if (q == 0 || q == 2) max(0, x - radius) else x
                    val right = if (q == 0 || q == 2) x else min(w - 1, x + radius)
                    val top = if (q < 2) max(0, y - radius) else y
                    val bottom = if (q < 2) y else min(h - 1, y + radius)
                    val a = top * sw + left
                    val bIdx = top * sw + right + 1
                    val cIdx = (bottom + 1) * sw + left
                    val dIdx = (bottom + 1) * sw + right + 1
                    val n = (right - left + 1) * (bottom - top + 1)
                    val sl = (sumL[dIdx] - sumL[bIdx] - sumL[cIdx] + sumL[a]).toFloat()
                    val sl2 = (sumL2[dIdx] - sumL2[bIdx] - sumL2[cIdx] + sumL2[a]).toFloat()
                    val mean = sl / n
                    val variance = sl2 / n - mean * mean
                    if (variance < bestVar) {
                        bestVar = variance
                        val r = (sumR[dIdx] - sumR[bIdx] - sumR[cIdx] + sumR[a]) / n
                        val g = (sumG[dIdx] - sumG[bIdx] - sumG[cIdx] + sumG[a]) / n
                        val b = (sumB[dIdx] - sumB[bIdx] - sumB[cIdx] + sumB[a]) / n
                        best = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                out[y * w + x] = best
            }
        }
        return out
    }

    // Rozmycie pudełkowe jednego kanału (poziomo, potem pionowo; suma bieżąca — koszt niezależny od promienia).
    private fun boxBlur(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val tmp = IntArray(w * h)
        for (y in 0 until h) {
            var sum = 0
            for (x in -radius..radius) sum += src[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[y * w + x] = sum / (2 * radius + 1)
                sum += src[y * w + (x + radius + 1).coerceAtMost(w - 1)] - src[y * w + (x - radius).coerceAtLeast(0)]
            }
        }
        val out = IntArray(w * h)
        for (x in 0 until w) {
            var sum = 0
            for (y in -radius..radius) sum += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / (2 * radius + 1)
                sum += tmp[(y + radius + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - radius).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    // --- Drobne narzędzia ---

    private fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, 255)
    private fun rgb(r: Float, g: Float, b: Float): Int = (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
    private fun luminance(c: Int): Int = ((((c shr 16) and 255) * 77) + (((c shr 8) and 255) * 150) + ((c and 255) * 29)) shr 8
    private fun posterize(v: Float, step: Float): Float = (v / step).roundToInt() * step

    private fun saturate(c: Int, amount: Float): Int {
        val r = ((c shr 16) and 255).toFloat()
        val g = ((c shr 8) and 255).toFloat()
        val b = (c and 255).toFloat()
        val l = 0.299f * r + 0.587f * g + 0.114f * b
        return (c and 0xFF000000.toInt()) or (clamp(l + (r - l) * amount) shl 16) or (clamp(l + (g - l) * amount) shl 8) or clamp(l + (b - l) * amount)
    }

    // Płynne przejście 0 → 1 między edge0 a edge1 (jak smoothstep w shaderach; edge0 > edge1 odwraca kierunek).
    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // Pseudolosowa wartość 0..1 dla punktu siatki — zawsze ta sama dla tych samych współrzędnych (powtarzalny efekt).
    private fun hash(x: Int, y: Int): Float {
        var n = x * 374761393 + y * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65535f
    }

    // Szum wartości: gładko interpolowane losowe wartości w węzłach siatki (miękkie plamy).
    private fun noise(x: Float, y: Float): Float {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val fx = x - ix
        val fy = y - iy
        val sx = fx * fx * (3f - 2f * fx)
        val sy = fy * fy * (3f - 2f * fy)
        val top = hash(ix, iy) + (hash(ix + 1, iy) - hash(ix, iy)) * sx
        val bottom = hash(ix, iy + 1) + (hash(ix + 1, iy + 1) - hash(ix, iy + 1)) * sx
        return top + (bottom - top) * sy
    }
}
