package pl.rafal.stickonme

import android.content.Context
import androidx.annotation.StringRes

// --- Szablony (SOM 1.9.0 kolaże, SOM 1.10.0 tapety) ---
// Szablon = gotowa tablica: tło, puste miejsca na zdjęcia (Layer.slot) z ramką, filtrami i kształtem, napisy
// i przypinki. Dotknięcie miejsca → „Wstaw zdjęcie” → edytor zdjęcia z proporcjami miejsca. Położenia w ułamkach
// tablicy, więc szablon tapety pasuje do każdego ekranu (jak układ w procentach w CSS).
object Templates {
    class Template(val id: String, @StringRes val labelRes: Int, val wallpaper: Boolean)

    val all = listOf(
        Template("cork", R.string.som_tpl_cork, false),
        Template("album", R.string.som_tpl_album, false),
        Template("gallery", R.string.som_tpl_gallery, false),
        Template("vintage", R.string.som_tpl_vintage, false),
        Template("fridge", R.string.som_tpl_fridge, false),
        Template("line", R.string.som_tpl_line, false),
        Template("film", R.string.som_tpl_film, false),
        Template("w_frame", R.string.som_tpl_w_frame, true),
        Template("w_polaroid", R.string.som_tpl_w_polaroid, true),
        Template("w_stack", R.string.som_tpl_w_stack, true),
        Template("w_gallery", R.string.som_tpl_w_gallery, true),
        Template("w_heart", R.string.som_tpl_w_heart, true),
    )
    val collages get() = all.filter { !it.wallpaper }
    val wallpapers get() = all.filter { it.wallpaper }

    // Budowa tablicy z szablonu (w tle: przypinki są rysowane i zapisywane do assets).
    suspend fun build(context: Context, t: Template): Board {
        val (w, h) = if (t.wallpaper) boardPresets(context).let { p -> p[1].width to p[1].height } // [1] = tapeta (rozmiar ekranu)
        else if (t.id == "line") 1080 to 1080 else 1080 to 1350
        val b = Builder(context, w, h)
        val text = { res: Int -> context.getString(res) }
        val board = when (t.id) {
            "cork" -> {
                b.slot(0.3f, 0.3f, 0.42f, 1f, -6f, Layer.FRAME_POLAROID)
                b.slot(0.7f, 0.42f, 0.42f, 1f, 5f, Layer.FRAME_POLAROID)
                b.slot(0.38f, 0.7f, 0.46f, 1f, 3f, Layer.FRAME_POLAROID)
                b.pin("pushpin", 0.3f, 0.13f, 0.08f, 0f, 0xFFE53935)
                b.pin("pushpin", 0.7f, 0.25f, 0.08f, 0f, 0xFF4C8DF6)
                b.pin("pushpin", 0.38f, 0.5f, 0.08f, 0f, 0xFFFFB020)
                b.text(0.74f, 0.83f, text(R.string.som_tpl_text_memories), 0.08f, 0xFF3A2410, "cursive")
                b.board(Board.BG_GRADIENT, 0xFFC4955E, 0xFFA87A45)
            }
            "album" -> {
                listOf(0.28f to 0.27f, 0.72f to 0.27f, 0.28f to 0.66f, 0.72f to 0.66f).forEachIndexed { i, (x, y) ->
                    b.slot(x, y, 0.36f, if (i % 2 == 0) 3f / 4f else 1f, if (i % 3 == 0) -2f else 2f, Layer.FRAME_ALBUM, 0xFF2A2E34)
                }
                b.text(0.5f, 0.9f, text(R.string.som_tpl_text_family), 0.07f, 0xFFF5F1E8, "serif", italic = true)
                b.board(Board.BG_COLOR, 0xFF2A2E34)
            }
            "gallery" -> {
                b.slot(0.5f, 0.33f, 0.56f, 4f / 5f, 0f, Layer.FRAME_GOLD)
                b.slot(0.27f, 0.76f, 0.32f, 1f, 0f, Layer.FRAME_GALLERY, 0xFF16171A)
                b.slot(0.73f, 0.76f, 0.32f, 1f, 0f, Layer.FRAME_BAROQUE)
                b.board(Board.BG_GRADIENT, 0xFFEDE7DC, 0xFFD9D0C1)
            }
            "vintage" -> {
                b.slot(0.36f, 0.3f, 0.5f, 4f / 3f, -4f, Layer.FRAME_TAPE4, 0xFFF3E7B0, "sepia,paper")
                b.slot(0.66f, 0.62f, 0.44f, 3f / 4f, 5f, Layer.FRAME_STAMP, 0xFFF5F1E8, "vintage")
                b.slot(0.3f, 0.75f, 0.3f, 1f, -7f, Layer.FRAME_INSTAX, 0xFFF5F1E8, "bw,print")
                b.text(0.7f, 0.12f, text(R.string.som_tpl_text_long_ago), 0.07f, 0xFF5A3A1E, "serif", italic = true)
                b.board(Board.BG_GRADIENT, 0xFFEFE3C6, 0xFFD8C49A)
            }
            "fridge" -> {
                b.slot(0.32f, 0.28f, 0.44f, 1f, -3f, Layer.FRAME_INSTAX)
                b.slot(0.7f, 0.5f, 0.4f, 3f / 4f, 4f, Layer.FRAME_INSTAX)
                b.slot(0.35f, 0.74f, 0.44f, 4f / 3f, 2f, Layer.FRAME_INSTAX)
                b.pin("magnet", 0.32f, 0.1f, 0.09f, 0f, 0xFFF0508C)
                b.pin("magnet", 0.7f, 0.31f, 0.09f, 0f, 0xFF2FD9B4)
                b.pin("magnet", 0.35f, 0.61f, 0.09f, 0f, 0xFFFFB020)
                b.board(Board.BG_GRADIENT, 0xFFF6F8F9, 0xFFD7DEE2)
            }
            "line" -> {
                listOf(0.2f to -5f, 0.5f to 3f, 0.8f to -3f).forEach { (x, r) ->
                    b.slot(x, 0.52f, 0.27f, 1f, r, Layer.FRAME_POLAROID)
                    b.pin("clothespin", x, 0.27f, 0.36f, 0f, 0xFFC89A62)
                }
                b.text(0.5f, 0.88f, text(R.string.som_tpl_text_summer), 0.08f, 0xFFFFFFFF, "cursive")
                b.board(Board.BG_GRADIENT, 0xFF6FC3DF, 0xFF4C8DF6)
            }
            "film" -> {
                listOf(0.2f, 0.5f, 0.8f).forEach { y -> b.slot(0.5f, y, 0.78f, 16f / 9f, 0f, Layer.FRAME_FILM, filters = "bw") }
                b.board(Board.BG_COLOR, 0xFF101214)
            }
            "w_frame" -> {
                b.slot(0.5f, 0.52f, 0.7f, 3f / 4f, 0f, Layer.FRAME_GOLD)
                b.board(Board.BG_GRADIENT, 0xFF101214, 0xFF3A2A5A)
            }
            "w_polaroid" -> {
                b.slot(0.5f, 0.55f, 0.72f, 1f, -4f, Layer.FRAME_POLAROID)
                b.pin("pushpin", 0.5f, 0.355f, 0.11f, 0f, 0xFFE53935)
                b.text(0.5f, 0.86f, text(R.string.som_tpl_text_moments), 0.08f, 0xFF3A2410, "cursive")
                b.board(Board.BG_GRADIENT, 0xFFC4955E, 0xFFA87A45)
            }
            "w_stack" -> {
                b.slot(0.36f, 0.36f, 0.52f, 1f, -6f, Layer.FRAME_INSTAX)
                b.slot(0.64f, 0.56f, 0.52f, 1f, 5f, Layer.FRAME_INSTAX)
                b.slot(0.42f, 0.77f, 0.52f, 1f, -2f, Layer.FRAME_INSTAX)
                b.pin("washi", 0.36f, 0.25f, 0.3f, -8f, 0xFFF0508C)
                b.pin("washi", 0.64f, 0.45f, 0.3f, 6f, 0xFF7FD6AE)
                b.pin("washi", 0.42f, 0.66f, 0.3f, -3f, 0xFFFFE066)
                b.board(Board.BG_GRADIENT, 0xFF2FD9B4, 0xFF4C8DF6)
            }
            "w_gallery" -> {
                b.slot(0.5f, 0.5f, 0.74f, 4f / 5f, 0f, Layer.FRAME_GALLERY, 0xFF2A2E34)
                b.board(Board.BG_COLOR, 0xFF101214)
            }
            else -> { // w_heart
                b.slot(0.5f, 0.52f, 0.78f, 1f, 0f, Layer.FRAME_OUTLINE, 0xFFFFFFFF, "watercolor", PhotoEdit(aspect = 1f, shape = "heart"))
                b.board(Board.BG_GRADIENT, 0xFFF0508C, 0xFFFFB020)
            }
        }
        return board.copy(name = context.getString(t.labelRes))
    }

    // Pomocnik do składania tablicy (jak builder w C#): wszystkie położenia w ułamkach tablicy.
    private class Builder(val context: Context, val w: Int, val h: Int) {
        val layers = mutableListOf<Layer>()

        fun slot(cx: Float, cy: Float, width: Float, aspect: Float, rotation: Float, frame: String = Layer.FRAME_NONE,
                 frameColor: Long = 0xFFFFFFFF, filters: String = "", edit: PhotoEdit? = null) {
            layers += Layer(
                kind = Layer.KIND_IMAGE, x = cx * w, y = cy * h, scale = width * w / BoardRenderer.SLOT_SIDE, rotation = rotation,
                frame = frame, frameColor = frameColor, filters = filters, slot = aspect,
                photoEdit = (edit ?: PhotoEdit(aspect = aspect)).toJson(),
            )
        }

        fun text(cx: Float, cy: Float, value: String, size: Float, color: Long, font: String, italic: Boolean = false) {
            layers += Layer(kind = Layer.KIND_TEXT, x = cx * w, y = cy * h, text = value, size = size * w, color = color, font = font, italic = italic, shadow = true)
        }

        suspend fun pin(id: String, cx: Float, cy: Float, width: Float, rotation: Float, color: Long) {
            val pin = Pins.of(id)
            val image = Pins.render(id, color)
            val path = BoardStore.importImage(context, image, png = true)
            image.recycle()
            val logical = pin.w * BoardRenderer.logicalScale(pin.w, pin.h)
            layers += Layer(kind = Layer.KIND_IMAGE, path = path, x = cx * w, y = cy * h, scale = width * w / logical, rotation = rotation)
        }

        fun board(bgType: String, color: Long, color2: Long = color) =
            Board(width = w, height = h, bgType = bgType, bgColor = color, bgColor2 = color2, layers = layers.toList())
    }
}
