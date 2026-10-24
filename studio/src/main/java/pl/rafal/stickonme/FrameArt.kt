package pl.rafal.stickonme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

// --- Ramki z SOM 1.7.0 (album, cztery taśmy, drewno, ramy obrazu) i wspólne kawałki rysunku (taśma) ---
// Wszystko rysowane w kodzie, bez plików graficznych (jak ścieżki w XAML) — rozmiary w ułamkach dłuższego boku obrazu (m),
// więc ramka wygląda tak samo w podglądzie i w eksporcie. BoardRenderer woła tu marginesy i rysowanie nowych ramek.
object FrameArt {
    // Marginesy (boki, góra, dół) w px obrazu albo null, gdy to nie jest ramka z tego pliku.
    fun padding(frame: String, m: Float): Triple<Float, Float, Float>? = when (frame) {
        Layer.FRAME_ALBUM -> Triple(m * 0.09f, m * 0.09f, m * 0.09f)
        Layer.FRAME_TAPE4 -> Triple(0f, 0f, 0f)
        Layer.FRAME_WOOD -> Triple(m * 0.09f, m * 0.09f, m * 0.09f)
        Layer.FRAME_GOLD, Layer.FRAME_SILVER -> Triple(m * 0.1f, m * 0.1f, m * 0.1f)
        Layer.FRAME_BAROQUE -> Triple(m * 0.16f, m * 0.16f, m * 0.16f)
        Layer.FRAME_GALLERY -> Triple(m * 0.15f, m * 0.15f, m * 0.15f)
        else -> null
    }

    // Zapas wokół karty (cień, wystające taśmy).
    fun margin(frame: String): Float? = when (frame) {
        Layer.FRAME_TAPE4 -> 0.12f
        Layer.FRAME_ALBUM, Layer.FRAME_WOOD, Layer.FRAME_GOLD, Layer.FRAME_SILVER, Layer.FRAME_BAROQUE, Layer.FRAME_GALLERY -> 0.07f
        else -> null
    }

    // Rysuje ramkę razem z obrazem. false = to nie jest ramka z tego pliku (BoardRenderer rysuje ją sam).
    fun draw(canvas: Canvas, frame: String, bmp: Bitmap, imgX: Float, imgY: Float, totalW: Float, totalH: Float, m: Float, color: Long, filter: Paint): Boolean {
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val outer = RectF(0f, 0f, totalW, totalH)
        val inner = RectF(imgX, imgY, imgX + w, imgY + h)
        when (frame) {
            Layer.FRAME_ALBUM -> album(canvas, bmp, outer, inner, m, color, filter)
            Layer.FRAME_TAPE4 -> {
                canvas.drawBitmap(bmp, imgX, imgY, filter)
                val tw = m * 0.24f
                val th = m * 0.075f
                listOf(
                    Triple(inner.left, inner.top, -45f), Triple(inner.right, inner.top, 45f),
                    Triple(inner.left, inner.bottom, 45f), Triple(inner.right, inner.bottom, -45f),
                ).forEach { (x, y, a) -> tape(canvas, x, y, a, tw, th, color) }
            }
            Layer.FRAME_WOOD -> {
                shadowCard(canvas, outer, m)
                molding(canvas, outer, inner, intArrayOf(0xFF5A3A1E.toInt(), 0xFF8A5A30.toInt(), 0xFF9C6A3A.toInt(), 0xFF7A4E28.toInt(), 0xFF4A2E16.toInt()))
                woodGrain(canvas, outer, inner, m)
                canvas.drawBitmap(bmp, imgX, imgY, filter)
                innerLip(canvas, inner, m, 0x66000000)
            }
            Layer.FRAME_GOLD, Layer.FRAME_SILVER -> {
                shadowCard(canvas, outer, m)
                molding(canvas, outer, inner, if (frame == Layer.FRAME_GOLD) GOLD else SILVER)
                canvas.drawBitmap(bmp, imgX, imgY, filter)
                innerLip(canvas, inner, m, 0x88000000.toInt())
            }
            Layer.FRAME_BAROQUE -> {
                shadowCard(canvas, outer, m)
                molding(canvas, outer, inner, GOLD_DEEP)
                baroqueOrnaments(canvas, outer, inner)
                canvas.drawBitmap(bmp, imgX, imgY, filter)
                innerLip(canvas, inner, m, 0x99000000.toInt())
            }
            Layer.FRAME_GALLERY -> {
                // Cienka rama w kolorze ramki + biały passe-partout ze ściętą krawędzią wokół zdjęcia.
                shadowCard(canvas, outer, m)
                val rim = m * 0.035f
                val c = color.toInt()
                molding(canvas, outer, RectF(rim, rim, outer.right - rim, outer.bottom - rim), intArrayOf(shade(c, 0.7f), shade(c, 1.15f), c, shade(c, 0.8f)))
                canvas.drawRect(RectF(rim, rim, outer.right - rim, outer.bottom - rim), Paint().apply { this.color = 0xFFF6F3EC.toInt() })
                val bevel = m * 0.006f
                canvas.drawRect(RectF(inner.left - bevel, inner.top - bevel, inner.right + bevel, inner.bottom + bevel), Paint().apply { this.color = 0xFFFFFFFF.toInt() })
                canvas.drawRect(RectF(inner.left - bevel, inner.top - bevel, inner.right + bevel, inner.top), Paint().apply { this.color = 0x22000000 })
                canvas.drawBitmap(bmp, imgX, imgY, filter)
            }
            else -> return false
        }
        return true
    }

    private val GOLD = intArrayOf(0xFF7A5512.toInt(), 0xFFE9C766.toInt(), 0xFFB08327.toInt(), 0xFFFFF0B0.toInt(), 0xFFC89A3A.toInt(), 0xFF6B4A0E.toInt())
    private val GOLD_DEEP = intArrayOf(0xFF5E3F0A.toInt(), 0xFFD8B04C.toInt(), 0xFF9C7020.toInt(), 0xFFFFE9A0.toInt(), 0xFFB98A2E.toInt(), 0xFF8A6418.toInt(), 0xFF4E3408.toInt())
    private val SILVER = intArrayOf(0xFF5E6266.toInt(), 0xFFDADDE0.toInt(), 0xFF9EA3A8.toInt(), 0xFFFFFFFF.toInt(), 0xFFB4B8BC.toInt(), 0xFF4E5256.toInt())

    // Album: kartka w kolorze ramki, zdjęcie z lekkim cieniem i cztery papierowe narożniki.
    private fun album(canvas: Canvas, bmp: Bitmap, outer: RectF, inner: RectF, m: Float, color: Long, filter: Paint) {
        val page = color.toInt()
        canvas.drawRect(outer, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = page
            setShadowLayer(m * 0.02f, 0f, m * 0.01f, 0x55000000)
        })
        canvas.drawRect(inner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            setShadowLayer(m * 0.008f, 0f, m * 0.004f, 0x66000000)
        })
        canvas.drawBitmap(bmp, inner.left, inner.top, filter)
        // Narożniki: czarne na jasnej kartce, kremowe na ciemnej.
        val corner = if (luminance(page) > 0.45f) 0xFF1E1E1E.toInt() else 0xFFEDE3CC.toInt()
        val c = m * 0.1f
        val o = c * 0.18f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = corner
            setShadowLayer(m * 0.006f, 0f, m * 0.003f, 0x55000000)
        }
        val corners = listOf(
            Triple(inner.left, inner.top, 0f), Triple(inner.right, inner.top, 90f),
            Triple(inner.right, inner.bottom, 180f), Triple(inner.left, inner.bottom, 270f),
        )
        for ((x, y, a) in corners) {
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(a)
            canvas.drawPath(Path().apply { moveTo(-o, -o); lineTo(c, -o); lineTo(-o, c); close() }, paint)
            canvas.restore()
        }
    }

    // Cień pod całą ramą (rama wisi na ścianie / leży na stole).
    private fun shadowCard(canvas: Canvas, outer: RectF, m: Float) {
        canvas.drawRect(outer, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            setShadowLayer(m * 0.025f, 0f, m * 0.015f, 0x77000000)
        })
    }

    // Listwa ramy: cztery trapezy (ścięte rogi jak przy prawdziwej ramie), w każdym gradient w poprzek profilu
    // od zewnętrznej krawędzi do zdjęcia — daje wrażenie wypukłej, złoconej / drewnianej listwy.
    private fun molding(canvas: Canvas, outer: RectF, inner: RectF, stops: IntArray) {
        val sides = listOf(
            Path().apply { moveTo(outer.left, outer.top); lineTo(outer.right, outer.top); lineTo(inner.right, inner.top); lineTo(inner.left, inner.top); close() } to
                floatArrayOf(0f, outer.top, 0f, inner.top),
            Path().apply { moveTo(outer.left, outer.bottom); lineTo(outer.right, outer.bottom); lineTo(inner.right, inner.bottom); lineTo(inner.left, inner.bottom); close() } to
                floatArrayOf(0f, outer.bottom, 0f, inner.bottom),
            Path().apply { moveTo(outer.left, outer.top); lineTo(inner.left, inner.top); lineTo(inner.left, inner.bottom); lineTo(outer.left, outer.bottom); close() } to
                floatArrayOf(outer.left, 0f, inner.left, 0f),
            Path().apply { moveTo(outer.right, outer.top); lineTo(inner.right, inner.top); lineTo(inner.right, inner.bottom); lineTo(outer.right, outer.bottom); close() } to
                floatArrayOf(outer.right, 0f, inner.right, 0f),
        )
        // Światło z lewej góry: górna i lewa listwa jaśniejsze, dolna i prawa ciemniejsze.
        val light = floatArrayOf(1.08f, 0.82f, 1.0f, 0.9f)
        sides.forEachIndexed { i, (path, g) ->
            val colors = IntArray(stops.size) { shade(stops[it], light[i]) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(g[0], g[1], g[2], g[3], colors, null, Shader.TileMode.CLAMP) }
            canvas.drawPath(path, paint)
        }
        // Linie styku listew w rogach (ścięcie 45°).
        val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40000000
            style = Paint.Style.STROKE
            strokeWidth = max(1f, (inner.left - outer.left) * 0.02f)
        }
        canvas.drawLine(outer.left, outer.top, inner.left, inner.top, seam)
        canvas.drawLine(outer.right, outer.top, inner.right, inner.top, seam)
        canvas.drawLine(outer.left, outer.bottom, inner.left, inner.bottom, seam)
        canvas.drawLine(outer.right, outer.bottom, inner.right, inner.bottom, seam)
    }

    // Słoje drewna: cienkie, lekko falujące linie wzdłuż każdej listwy (powtarzalny „los” — ten sam wzór co klatkę).
    private fun woodGrain(canvas: Canvas, outer: RectF, inner: RectF, m: Float) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, m * 0.0025f)
        }
        val band = inner.left - outer.left
        val count = 9
        for (side in 0 until 4) {
            canvas.save()
            val clip = Path()
            when (side) {
                0 -> clip.apply { moveTo(outer.left, outer.top); lineTo(outer.right, outer.top); lineTo(inner.right, inner.top); lineTo(inner.left, inner.top); close() }
                1 -> clip.apply { moveTo(outer.left, outer.bottom); lineTo(outer.right, outer.bottom); lineTo(inner.right, inner.bottom); lineTo(inner.left, inner.bottom); close() }
                2 -> clip.apply { moveTo(outer.left, outer.top); lineTo(inner.left, inner.top); lineTo(inner.left, inner.bottom); lineTo(outer.left, outer.bottom); close() }
                else -> clip.apply { moveTo(outer.right, outer.top); lineTo(inner.right, inner.top); lineTo(inner.right, inner.bottom); lineTo(outer.right, outer.bottom); close() }
            }
            canvas.clipPath(clip)
            val horizontal = side < 2
            val length = if (horizontal) outer.width() else outer.height()
            for (k in 0 until count) {
                val t = (k + 0.5f) / count
                val offset = band * t
                line.color = if (k % 3 == 0) 0x40301806 else 0x2A1E0E02
                val p = Path()
                val steps = 24
                for (s in 0..steps) {
                    val along = length * s / steps
                    val wave = band * 0.06f * kotlin.math.sin(along / m * 9f + k * 1.7f).toFloat()
                    val across = offset + wave
                    val x: Float
                    val y: Float
                    when (side) {
                        0 -> { x = along; y = outer.top + across }
                        1 -> { x = along; y = outer.bottom - across }
                        2 -> { x = outer.left + across; y = along }
                        else -> { x = outer.right - across; y = along }
                    }
                    if (s == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                canvas.drawPath(p, line)
            }
            canvas.restore()
        }
    }

    // Barok: perełki wzdłuż listwy i rozety w rogach (złote kulki z odblaskiem — RadialGradient).
    private fun baroqueOrnaments(canvas: Canvas, outer: RectF, inner: RectF) {
        val band = inner.left - outer.left
        val r = band * 0.11f
        val step = r * 2.6f
        val mid = band * 0.5f
        fun bead(x: Float, y: Float, radius: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(x - radius * 0.35f, y - radius * 0.35f, radius * 1.3f,
                    intArrayOf(0xFFFFF4C0.toInt(), 0xFFD9AE48.toInt(), 0xFF6E4C0C.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                setShadowLayer(radius * 0.4f, radius * 0.15f, radius * 0.25f, 0x66000000)
            }
            canvas.drawCircle(x, y, radius, p)
        }
        // Perełki na środku listwy, z dala od rogów (tam rozety).
        var x = outer.left + band * 1.4f
        while (x < outer.right - band * 1.4f) {
            bead(x, outer.top + mid, r)
            bead(x, outer.bottom - mid, r)
            x += step
        }
        var y = outer.top + band * 1.4f
        while (y < outer.bottom - band * 1.4f) {
            bead(outer.left + mid, y, r)
            bead(outer.right - mid, y, r)
            y += step
        }
        // Rozety: duża kula w środku i sześć płatków dookoła.
        val corners = listOf(outer.left + mid to outer.top + mid, outer.right - mid to outer.top + mid, outer.left + mid to outer.bottom - mid, outer.right - mid to outer.bottom - mid)
        for ((cx, cy) in corners) {
            for (i in 0 until 6) {
                val a = Math.PI * 2 * i / 6
                bead(cx + (kotlin.math.cos(a) * band * 0.24f).toFloat(), cy + (kotlin.math.sin(a) * band * 0.24f).toFloat(), band * 0.12f)
            }
            bead(cx, cy, band * 0.17f)
        }
        // Wewnętrzny wałek przy zdjęciu.
        val lip = band * 0.08f
        canvas.drawRect(RectF(inner.left - lip, inner.top - lip, inner.right + lip, inner.bottom + lip), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = lip
            shader = LinearGradient(inner.left, inner.top, inner.right, inner.bottom, intArrayOf(0xFFFFE9A0.toInt(), 0xFF8A6418.toInt()), null, Shader.TileMode.CLAMP)
        })
    }

    // Cień listwy padający na zdjęcie (wewnętrzna krawędź).
    private fun innerLip(canvas: Canvas, inner: RectF, m: Float, color: Int) {
        val t = m * 0.012f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, inner.top, 0f, inner.top + t, color, 0, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(RectF(inner.left, inner.top, inner.right, inner.top + t), paint)
        paint.shader = LinearGradient(inner.left, 0f, inner.left + t, 0f, color, 0, Shader.TileMode.CLAMP)
        canvas.drawRect(RectF(inner.left, inner.top, inner.left + t, inner.bottom), paint)
    }

    // Kawałek taśmy klejącej: półprzezroczysty, z postrzępionymi końcami. (cx, cy) = środek, kąt w stopniach.
    fun tape(canvas: Canvas, cx: Float, cy: Float, angle: Float, tw: Float, th: Float, color: Long) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle)
        val teeth = 7
        val path = Path().apply {
            moveTo(-tw / 2, -th / 2)
            lineTo(tw / 2, -th / 2)
            for (i in 1..teeth) lineTo(tw / 2 + if (i % 2 == 1) th * 0.08f else 0f, -th / 2 + th * i / teeth)
            lineTo(-tw / 2, th / 2)
            for (i in teeth - 1 downTo 0) lineTo(-tw / 2 - if (i % 2 == 1) th * 0.08f else 0f, -th / 2 + th * i / teeth)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = ((color and 0x00FFFFFFL) or 0xB8000000L).toInt()
            setShadowLayer(th * 0.08f, 0f, th * 0.05f, 0x33000000)
        })
        // Delikatny połysk wzdłuż taśmy.
        canvas.drawRect(RectF(-tw / 2, -th / 2, tw / 2, -th / 2 + th * 0.28f), Paint().apply { this.color = 0x26FFFFFF })
        canvas.restore()
    }

    // Jaśniej / ciemniej (k > 1 rozjaśnia), alfa bez zmian.
    fun shade(c: Int, k: Float): Int {
        val r = min(255f, ((c shr 16) and 255) * k).toInt()
        val g = min(255f, ((c shr 8) and 255) * k).toInt()
        val b = min(255f, (c and 255) * k).toInt()
        return (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    private fun luminance(c: Int): Float = (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f
}
