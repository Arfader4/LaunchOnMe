package pl.rafal.onthemes

import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Kolory systemu "pośrednio": Good Lock / Theme Park nie mają API, ale Android 12+ (i One UI "Paleta kolorów")
// liczy kolory systemu z TAPETY. Rysujemy więc tapetę z kolorów motywu i ustawiamy ją w systemie —
// potem w Ustawieniach telefonu wystarczy wybrać paletę, która z niej wyszła.
object PaletteWallpaper {
    enum class Style(@StringRes private val labelRes: Int) {
        GLOW(R.string.ot_wall_glow),         // tło motywu + rozmyte "światła" w kolorach palety (najlepiej działa z paletą One UI)
        GRADIENT(R.string.ot_wall_gradient), // spokojny gradient: tło → powierzchnia → akcent
        BANDS(R.string.ot_wall_bands),       // ukośne pasy kolorów palety
        ;

        val label: String get() = OnThemesText.get(labelRes)
    }

    // Rysuje tapetę w zadanym rozmiarze (podgląd: mały, ustawienie: rozmiar ekranu). Zwykły Canvas Androida (jak GDI+).
    fun render(look: PreviewLook, style: Style, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = look.roles
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(r.background.toInt())
        // Kolory palety bez szarości (te nie dają systemowi koloru), z akcentem na początku.
        val colors = (listOf(r.primary) + look.palette).distinct().take(4)
        when (style) {
            Style.GLOW -> {
                val spots = listOf(0.22f to 0.22f, 0.82f to 0.42f, 0.30f to 0.78f, 0.75f to 0.90f)
                colors.forEachIndexed { i, c ->
                    val (fx, fy) = spots[i % spots.size]
                    val radius = w * (0.75f - i * 0.08f)
                    paint.shader = RadialGradient(fx * w, fy * h, radius, withAlpha(c, 0.62f), withAlpha(c, 0f), Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, paint)
                }
            }
            Style.GRADIENT -> {
                paint.shader = LinearGradient(
                    0f, 0f, w * 0.3f, h,
                    intArrayOf(r.background.toInt(), r.surface.toInt(), mix(r.primary, r.background, 0.55f).toInt()),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            Style.BANDS -> {
                val band = h / (colors.size + 1)
                colors.forEachIndexed { i, c ->
                    paint.shader = null
                    paint.color = withAlpha(c, 0.55f)
                    val y = band * (i + 0.7f)
                    // ukośny pas: równoległobok przez całą szerokość
                    val path = android.graphics.Path().apply {
                        moveTo(0f, y)
                        lineTo(w, y - w * 0.35f)
                        lineTo(w, y - w * 0.35f + band * 0.55f)
                        lineTo(0f, y + band * 0.55f)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
        return bmp
    }

    // Ustawia tapetę systemu (ekran główny, opcjonalnie też blokady). Na wątku tła — trwa ok. pół sekundy.
    // Uprawnienie SET_WALLPAPER ma już launcher (ten sam APK). true = udało się.
    suspend fun apply(context: Context, look: PreviewLook, style: Style, lockToo: Boolean): Boolean = withContext(Dispatchers.Default) {
        runCatching {
            val manager = WallpaperManager.getInstance(context)
            val metrics = context.resources.displayMetrics
            val width = maxOf(metrics.widthPixels, 720)
            val height = maxOf(metrics.heightPixels, 1280)
            val bmp = render(look, style, width, height)
            val which = WallpaperManager.FLAG_SYSTEM or (if (lockToo) WallpaperManager.FLAG_LOCK else 0)
            manager.setBitmap(bmp, null, true, which)
        }.isSuccess
    }

    // Kolory motywu jako tekst do schowka — do przepisania w Theme Park (Good Lock) albo gdziekolwiek indziej.
    fun copyHex(context: Context, name: String, look: PreviewLook) {
        val r = look.roles
        fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)
        val text = buildString {
            appendLine(name)
            appendLine("${OnThemesText.get(R.string.ot_editor_background)}: ${hex(r.background)}")
            appendLine("${OnThemesText.get(R.string.ot_editor_surfaces)}: ${hex(r.surface)}")
            appendLine("${OnThemesText.get(R.string.ot_editor_accent)}: ${hex(r.primary)}")
            appendLine("${OnThemesText.get(R.string.ot_editor_text)}: ${hex(r.onBackground)}")
            append("${OnThemesText.get(R.string.ot_palette)}: ${look.palette.joinToString(" ") { hex(it) }}")
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(name, text))
    }

    private fun withAlpha(c: Long, alpha: Float): Int = ((alpha * 255).toInt() shl 24) or (c.toInt() and 0xFFFFFF)
}
