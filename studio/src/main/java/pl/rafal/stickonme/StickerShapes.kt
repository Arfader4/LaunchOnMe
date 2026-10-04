package pl.rafal.stickonme

import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.StringRes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Kształty naklejki (wycięcie w formie serca, gwiazdy…) — ścieżki android.graphics.Path, jak Geometry w WPF.
// square = kształt ma sens tylko w kwadracie, więc naklejka jest najpierw kadrowana do kwadratu (środek).
// Nazwy zgodne z dawnym oknem naklejki w launcherze (StickerShape.name.lowercase()), żeby stare naklejki dało się przenieść.
object StickerShapes {
    const val NONE = "none"

    // Etykieta jako getter z @StringRes (czytana przy użyciu, więc idzie za językiem systemu).
    data class Shape(val id: String, @StringRes private val labelRes: Int, val square: Boolean) {
        val label: String get() = StudioText.get(labelRes)
    }

    val all = listOf(
        Shape(NONE, R.string.som_shape_none, false),
        Shape("rounded", R.string.som_shape_rounded, false),
        Shape("circle", R.string.som_shape_circle, true),
        Shape("oval", R.string.som_shape_oval, false),
        Shape("heart", R.string.som_shape_heart, true),
        Shape("star", R.string.som_shape_star, true),
        Shape("hexagon", R.string.som_shape_hexagon, true),
        Shape("flower", R.string.som_shape_flower, true),
        Shape("arch", R.string.som_shape_arch, false),
    )

    fun of(id: String?): Shape = all.firstOrNull { it.id == id } ?: all.first()

    fun path(id: String, w: Float, h: Float): Path? = when (id) {
        "rounded" -> Path().apply { val r = min(w, h) * 0.18f; addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW) }
        "circle", "oval" -> Path().apply { addOval(RectF(0f, 0f, w, h), Path.Direction.CW) }
        "heart" -> Path().apply {
            moveTo(0.5f * w, 0.92f * h)
            cubicTo(0.08f * w, 0.66f * h, -0.02f * w, 0.34f * h, 0.2f * w, 0.16f * h)
            cubicTo(0.35f * w, 0.04f * h, 0.5f * w, 0.14f * h, 0.5f * w, 0.28f * h)
            cubicTo(0.5f * w, 0.14f * h, 0.65f * w, 0.04f * h, 0.8f * w, 0.16f * h)
            cubicTo(1.02f * w, 0.34f * h, 0.92f * w, 0.66f * h, 0.5f * w, 0.92f * h)
            close()
        }
        "star" -> polygon(w, h, 10) { i -> if (i % 2 == 0) 1f else 0.46f }
        "hexagon" -> polygon(w, h, 6) { 1f }
        "flower" -> polygon(w, h, 120) { i -> 0.88f + 0.12f * cos(i * 2 * PI * 8 / 120).toFloat() } // 8 płatków
        "arch" -> Path().apply {
            val d = min(w, 2 * h) // na szerokim obrazku łuk spłaszczony, żeby nie wyjść poza dół
            moveTo(0f, h)
            lineTo(0f, d / 2)
            arcTo(RectF(0f, 0f, w, d), 180f, 180f, false) // półkole na górze
            lineTo(w, h)
            close()
        }
        else -> null
    }

    // Wielokąt foremny (albo "falujący") wokół środka; radius(i) = promień i-tego wierzchołka jako ułamek połowy boku.
    private fun polygon(w: Float, h: Float, points: Int, radius: (Int) -> Float) = Path().apply {
        val cx = w / 2
        val cy = h / 2
        val r = min(cx, cy)
        for (i in 0 until points) {
            val angle = -PI / 2 + i * 2 * PI / points // od góry, zgodnie z zegarem
            val x = cx + r * radius(i) * cos(angle).toFloat()
            val y = cy + r * radius(i) * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
