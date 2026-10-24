package pl.rafal.stickonme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.StringRes
import kotlin.math.roundToInt

// --- Przypinki (SOM 1.8.0): pinezka, zszywka, spinacz, taśmy, gwóźdź, magnes, klips, klamerka, zagięty róg ---
// Rysowane w kodzie do przezroczystego PNG i dodawane na tablicę jak zwykła naklejka (warstwa obrazu) — więc działa
// przesuwanie, obrót, skala, kopiowanie i eksport bez żadnej nowej logiki. Rozmiary w „jednostkach” płótna (szer. × wys.).
object Pins {
    class Pin(val id: String, @StringRes val labelRes: Int, val w: Int, val h: Int, val defaultColor: Long, val boardShare: Float)

    val all = listOf(
        Pin("pushpin", R.string.som_pin_pushpin, 400, 400, 0xFFE53935, 0.09f),
        Pin("nail", R.string.som_pin_nail, 260, 260, 0xFF9EA3A8, 0.05f),
        Pin("staple", R.string.som_pin_staple, 440, 120, 0xFFB4B8BC, 0.1f),
        Pin("paperclip", R.string.som_pin_paperclip, 220, 520, 0xFFB4B8BC, 0.07f),
        Pin("tape", R.string.som_pin_tape, 600, 190, 0xFFF3E7B0, 0.24f),
        Pin("tape_x", R.string.som_pin_tape_x, 560, 560, 0xFFF3E7B0, 0.22f),
        Pin("washi", R.string.som_pin_washi, 600, 170, 0xFFF0508C, 0.24f),
        Pin("magnet", R.string.som_pin_magnet, 360, 360, 0xFF4C8DF6, 0.09f),
        Pin("binder", R.string.som_pin_binder, 420, 460, 0xFF16171A, 0.12f),
        Pin("clothespin", R.string.som_pin_clothespin, 640, 320, 0xFFC89A62, 0.26f),
        Pin("corner", R.string.som_pin_corner, 400, 400, 0xFFF5F1E8, 0.12f),
    )

    fun of(id: String): Pin = all.firstOrNull { it.id == id } ?: all.first()

    // Gotowy obraz przypinki (scale < 1 = miniatura do okna wyboru).
    fun render(id: String, color: Long, scale: Float = 1f): Bitmap {
        val pin = of(id)
        val out = Bitmap.createBitmap((pin.w * scale).roundToInt().coerceAtLeast(1), (pin.h * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.scale(scale, scale)
        val c = color.toInt()
        val w = pin.w.toFloat()
        val h = pin.h.toFloat()
        when (pin.id) {
            "pushpin" -> pushpin(canvas, c)
            "nail" -> nail(canvas)
            "staple" -> staple(canvas, w, h, c)
            "paperclip" -> paperclip(canvas, c)
            "tape" -> FrameArt.tape(canvas, w / 2, h / 2, -3f, w * 0.92f, h * 0.74f, color)
            "tape_x" -> {
                FrameArt.tape(canvas, w / 2, h / 2, 38f, w * 0.95f, h * 0.24f, color)
                FrameArt.tape(canvas, w / 2, h / 2, -38f, w * 0.95f, h * 0.24f, color)
            }
            "washi" -> washi(canvas, w, h, c)
            "magnet" -> magnet(canvas, c)
            "binder" -> binder(canvas, c)
            "clothespin" -> clothespin(canvas, w, h, c)
            else -> corner(canvas, w, h, c)
        }
        return out
    }

    // Miękki cień pod przedmiotem (rozmyty kształt w czerni o danej przezroczystości).
    private fun shadow(radius: Float, alpha: Int = 0x66) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = alpha shl 24
        maskFilter = android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

    // Kulka z odblaskiem (jak „gradient radialny” w Inkscape): jasny punkt u góry z lewej, ciemny brzeg.
    private fun glossy(cx: Float, cy: Float, r: Float, c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.35f,
            intArrayOf(FrameArt.shade(c, 1.9f) or 0xFF000000.toInt(), c, FrameArt.shade(c, 0.45f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    private fun metal(x0: Float, y0: Float, x1: Float, y1: Float, c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(x0, y0, x1, y1, intArrayOf(FrameArt.shade(c, 0.6f), FrameArt.shade(c, 1.35f), c, FrameArt.shade(c, 0.55f)), null, Shader.TileMode.CLAMP)
    }

    private fun pushpin(canvas: Canvas, c: Int) {
        // Cień igły i główki na papierze, potem główka z kołnierzem.
        canvas.drawOval(RectF(150f, 170f, 370f, 340f), shadow(26f, 0x55))
        canvas.drawLine(220f, 220f, 300f, 300f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A8F94.toInt(); strokeWidth = 10f; strokeCap = Paint.Cap.ROUND })
        canvas.drawCircle(190f, 190f, 125f, glossy(190f, 190f, 125f, c))
        canvas.drawCircle(190f, 190f, 78f, glossy(185f, 185f, 78f, FrameArt.shade(c, 1.1f)))
        canvas.drawCircle(190f, 190f, 78f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = 0x33000000 })
    }

    private fun nail(canvas: Canvas) {
        canvas.drawCircle(140f, 145f, 82f, shadow(18f))
        canvas.drawCircle(125f, 125f, 80f, glossy(125f, 125f, 80f, 0xFF9EA3A8.toInt()))
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = 0x33000000 }
        canvas.drawCircle(125f, 125f, 55f, ring)
        canvas.drawCircle(125f, 125f, 30f, ring)
    }

    private fun staple(canvas: Canvas, w: Float, h: Float, c: Int) {
        val bar = RectF(40f, h / 2 - 13f, w - 40f, h / 2 + 13f)
        canvas.drawRoundRect(RectF(bar.left + 6f, bar.top + 10f, bar.right + 6f, bar.bottom + 10f), 13f, 13f, shadow(10f, 0x55))
        canvas.drawRoundRect(bar, 13f, 13f, metal(0f, bar.top, 0f, bar.bottom, c))
        // Dziurki po nóżkach zszywki.
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88000000.toInt() }
        canvas.drawCircle(bar.left + 4f, h / 2, 9f, hole)
        canvas.drawCircle(bar.right - 4f, h / 2, 9f, hole)
    }

    private fun paperclip(canvas: Canvas, c: Int) {
        // Klasyczny spinacz: trzy proste odcinki połączone półokręgami (ścieżka jak w SVG: linie + łuki).
        val path = Path().apply {
            moveTo(130f, 150f)
            lineTo(130f, 400f)
            arcTo(RectF(70f, 370f, 130f, 430f), 0f, 180f)
            lineTo(70f, 110f)
            arcTo(RectF(70f, 60f, 170f, 160f), 180f, 180f)
            lineTo(170f, 440f)
            arcTo(RectF(50f, 380f, 170f, 500f), 0f, 180f)
            lineTo(50f, 170f)
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 16f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.save()
        canvas.translate(8f, 10f)
        canvas.drawPath(path, Paint(stroke).apply { color = 0x55000000; maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL) })
        canvas.restore()
        canvas.drawPath(path, Paint(stroke).apply { shader = LinearGradient(40f, 0f, 180f, 0f, intArrayOf(FrameArt.shade(c, 0.6f), FrameArt.shade(c, 1.4f), FrameArt.shade(c, 0.7f)), null, Shader.TileMode.MIRROR) })
    }

    private fun washi(canvas: Canvas, w: Float, h: Float, c: Int) {
        FrameArt.tape(canvas, w / 2, h / 2, 2f, w * 0.92f, h * 0.74f, c.toLong() and 0xFFFFFFFFL)
        // Wzór: białe kropki w rzędach, obcięte do kształtu taśmy (kropki tylko na taśmie).
        canvas.save()
        canvas.rotate(2f, w / 2, h / 2)
        canvas.clipRect(RectF(w * 0.04f, h * 0.13f, w * 0.96f, h * 0.87f))
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAAFFFFFF.toInt() }
        var row = 0
        var y = h * 0.22f
        while (y < h * 0.9f) {
            var x = w * 0.06f + if (row % 2 == 1) 20f else 0f
            while (x < w) {
                canvas.drawCircle(x, y, 8f, dot)
                x += 40f
            }
            y += 30f
            row++
        }
        canvas.restore()
    }

    private fun magnet(canvas: Canvas, c: Int) {
        canvas.drawCircle(195f, 200f, 135f, shadow(22f))
        // Bok krążka (cylinder) i wierzch z odblaskiem.
        canvas.drawCircle(180f, 192f, 130f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FrameArt.shade(c, 0.5f) })
        canvas.drawCircle(180f, 175f, 130f, glossy(180f, 175f, 130f, c))
    }

    private fun binder(canvas: Canvas, c: Int) {
        // Klips biurowy z góry: czarny korpus i dwa srebrne uchwyty z drutu złożone do góry.
        val body = Path().apply { moveTo(70f, 230f); lineTo(350f, 230f); lineTo(390f, 420f); lineTo(30f, 420f); close() }
        canvas.save()
        canvas.translate(8f, 12f)
        canvas.drawPath(body, shadow(14f))
        canvas.restore()
        canvas.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 230f, 0f, 420f, intArrayOf(FrameArt.shade(c, 1.8f) or 0xFF000000.toInt(), c, FrameArt.shade(c, 0.6f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP) })
        val wire = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12f
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(0f, 0f, 420f, 0f, intArrayOf(0xFF6E7378.toInt(), 0xFFF2F4F6.toInt(), 0xFF8A8F94.toInt()), null, Shader.TileMode.MIRROR)
        }
        for (x in listOf(120f, 300f)) {
            canvas.drawPath(Path().apply { moveTo(x - 40f, 250f); lineTo(x - 30f, 60f); quadTo(x, 30f, x + 30f, 60f); lineTo(x + 40f, 250f) }, wire)
        }
    }

    private fun clothespin(canvas: Canvas, w: Float, h: Float, c: Int) {
        // Sznurek przez całą szerokość (lekko zwisa) i drewniana klamerka ze sprężyną.
        canvas.drawPath(Path().apply { moveTo(0f, 70f); quadTo(w / 2, 110f, w, 70f) }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            color = 0xFFB8A27A.toInt()
        })
        val wood = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(w / 2 - 40f, 0f, w / 2 + 40f, 0f, intArrayOf(FrameArt.shade(c, 0.75f), FrameArt.shade(c, 1.15f), FrameArt.shade(c, 0.8f)), null, Shader.TileMode.CLAMP) }
        val pin = RectF(w / 2 - 36f, 30f, w / 2 + 36f, h - 20f)
        canvas.drawRoundRect(RectF(pin.left + 8f, pin.top + 10f, pin.right + 8f, pin.bottom + 10f), 18f, 18f, shadow(12f))
        canvas.drawRoundRect(pin, 18f, 18f, wood)
        canvas.drawLine(w / 2, pin.top + 6f, w / 2, pin.bottom - 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; strokeWidth = 4f })
        // Sprężyna.
        val spring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f; color = 0xFFB4B8BC.toInt() }
        canvas.drawRoundRect(RectF(w / 2 - 44f, 120f, w / 2 + 44f, 150f), 10f, 10f, spring)
    }

    private fun corner(canvas: Canvas, w: Float, h: Float, c: Int) {
        // Zagięty róg kartki: odgięty trójkąt z cieniem — kładziesz go na róg zdjęcia.
        val flap = Path().apply { moveTo(w, h * 0.25f); lineTo(w * 0.25f, h); lineTo(w * 0.42f, h * 0.42f); close() }
        canvas.save()
        canvas.translate(-10f, -10f)
        canvas.drawPath(flap, shadow(18f, 0x55))
        canvas.restore()
        canvas.drawPath(flap, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(w * 0.42f, h * 0.42f, w * 0.62f, h * 0.62f, intArrayOf(FrameArt.shade(c, 0.78f), c, FrameArt.shade(c, 1.05f) or 0xFF000000.toInt()), null, Shader.TileMode.CLAMP)
        })
    }
}
