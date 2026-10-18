package pl.rafal.stickonme

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// --- Tablica naklejek: płótno z warstwami (naklejki, zdjęcia, teksty, emoji) ---
// Z tablicy powstaje kolaż, tapeta albo nowa naklejka "sklejona" z kilku (tło przezroczyste).
// Wszystkie współrzędne w pikselach tablicy (np. 1080×2340 dla tapety), środek warstwy = (x, y).

data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,                 // KIND_IMAGE albo KIND_TEXT
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,         // stopnie
    // Obraz (naklejka z biblioteki albo zdjęcie)
    val path: String? = null,
    val frame: String = FRAME_NONE,
    val frameColor: Long = 0xFFFFFFFF,
    // Tekst / emoji
    val text: String = "",
    val font: String = "sans-serif",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val color: Long = 0xFFFFFFFF,
    val size: Float = 110f,           // wielkość liter w px tablicy (przy skali 1)
    val outlineColor: Long = 0xFF000000,
    val outlineWidth: Float = 0f,     // grubość obrysu liter w px (0 = bez)
    val underline: Float = 0f,        // grubość podkreślenia w px (0 = bez)
    val shadow: Boolean = false,
    val highlight: Long? = null,      // kolor "zakreślacza" pod tekstem (null = bez)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind).put("x", x.toDouble()).put("y", y.toDouble())
        .put("scale", scale.toDouble()).put("rotation", rotation.toDouble())
        .putOpt("path", path).put("frame", frame).put("frameColor", frameColor)
        .put("text", text).put("font", font).put("bold", bold).put("italic", italic)
        .put("color", color).put("size", size.toDouble())
        .put("outlineColor", outlineColor).put("outlineWidth", outlineWidth.toDouble())
        .put("underline", underline.toDouble()).put("shadow", shadow)
        .putOpt("highlight", highlight)

    companion object {
        const val KIND_IMAGE = "image"
        const val KIND_TEXT = "text"

        const val FRAME_NONE = "none"
        const val FRAME_OUTLINE = "outline"   // biały (albo kolorowy) kontur jak na prawdziwej naklejce
        const val FRAME_SHADOW = "shadow"
        const val FRAME_INSTAX = "instax"
        const val FRAME_POLAROID = "polaroid"
        const val FRAME_TAPE = "tape"         // przyklejone taśmą
        const val FRAME_STAMP = "stamp"       // ząbkowany brzeg jak znaczek pocztowy
        const val FRAME_FILM = "film"         // czarny pasek kliszy z perforacją

        // Ramki do wyboru (tablica i edytor naklejki) — kolejność = kolejność czipów.
        // Getter: etykiety czytane przy każdym użyciu (zmiana języka bez restartu).
        val FRAMES: List<Pair<String, String>> get() = listOf(
            FRAME_NONE to StudioText.get(R.string.som_frame_none), FRAME_OUTLINE to StudioText.get(R.string.som_frame_outline),
            FRAME_SHADOW to StudioText.get(R.string.som_frame_shadow), FRAME_STAMP to StudioText.get(R.string.som_frame_stamp),
            FRAME_INSTAX to "Instax", FRAME_POLAROID to "Polaroid",
            FRAME_FILM to StudioText.get(R.string.som_frame_film), FRAME_TAPE to StudioText.get(R.string.som_frame_tape),
        )
        // Ramki, w których kolor ma znaczenie (cień i klisza mają własny).
        fun hasColor(frame: String) = frame != FRAME_NONE && frame != FRAME_SHADOW && frame != FRAME_FILM

        fun of(o: JSONObject) = Layer(
            id = o.optString("id", UUID.randomUUID().toString()),
            kind = o.optString("kind", KIND_IMAGE),
            x = o.optDouble("x", 0.0).toFloat(),
            y = o.optDouble("y", 0.0).toFloat(),
            scale = o.optDouble("scale", 1.0).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            path = if (o.isNull("path")) null else o.optString("path"),
            frame = o.optString("frame", FRAME_NONE),
            frameColor = o.optLong("frameColor", 0xFFFFFFFF),
            text = o.optString("text", ""),
            font = o.optString("font", "sans-serif"),
            bold = o.optBoolean("bold", false),
            italic = o.optBoolean("italic", false),
            color = o.optLong("color", 0xFFFFFFFF),
            size = o.optDouble("size", 110.0).toFloat(),
            outlineColor = o.optLong("outlineColor", 0xFF000000),
            outlineWidth = o.optDouble("outlineWidth", 0.0).toFloat(),
            underline = o.optDouble("underline", 0.0).toFloat(),
            shadow = o.optBoolean("shadow", false),
            highlight = if (o.has("highlight") && !o.isNull("highlight")) o.getLong("highlight") else null,
        )
    }
}

data class Board(
    val id: String = UUID.randomUUID().toString(),
    val name: String = StudioText.get(R.string.som_board_default_name),
    val width: Int,
    val height: Int,
    val bgType: String = BG_TRANSPARENT,
    val bgColor: Long = 0xFF101214,
    val bgColor2: Long = 0xFF3A2A5A,  // drugi kolor gradientu
    val bgImage: String? = null,
    val layers: List<Layer> = emptyList(),
) {
    fun toJson(): String = JSONObject()
        .put("id", id).put("name", name).put("width", width).put("height", height)
        .put("bgType", bgType).put("bgColor", bgColor).put("bgColor2", bgColor2).putOpt("bgImage", bgImage)
        .put("layers", JSONArray(layers.map { it.toJson() }))
        .toString()

    fun updateLayer(id: String, change: (Layer) -> Layer) = copy(layers = layers.map { if (it.id == id) change(it) else it })

    companion object {
        const val BG_TRANSPARENT = "transparent"
        const val BG_COLOR = "color"
        const val BG_GRADIENT = "gradient"
        const val BG_IMAGE = "image"

        fun of(json: String): Board? = runCatching {
            val o = JSONObject(json)
            val layers = o.optJSONArray("layers")
            Board(
                id = o.getString("id"),
                name = o.optString("name", "Tablica"),
                width = o.getInt("width").coerceIn(64, 6000),
                height = o.getInt("height").coerceIn(64, 6000),
                bgType = o.optString("bgType", BG_TRANSPARENT),
                bgColor = o.optLong("bgColor", 0xFF101214),
                bgColor2 = o.optLong("bgColor2", 0xFF3A2A5A),
                bgImage = if (o.isNull("bgImage")) null else o.optString("bgImage").ifBlank { null },
                layers = layers?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Layer::of) } }.orEmpty(),
            )
        }.getOrNull()
    }
}

// Gotowe rozmiary tablicy (dłuższy bok ok. 1080–2400 px — ostre na ekranie telefonu).
data class BoardPreset(val label: String, val width: Int, val height: Int, val transparent: Boolean = false)

fun boardPresets(context: Context): List<BoardPreset> {
    val metrics = context.resources.displayMetrics
    val screenW = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(720)
    // Wysokość ekranu z paskami (displayMetrics bywa bez paska nawigacji) — dla tapety bierzemy proporcje 9:19,5 jako minimum.
    val screenH = maxOf(metrics.widthPixels, metrics.heightPixels, (screenW * 19.5f / 9f).toInt())
    return listOf(
        BoardPreset(context.getString(R.string.som_board_preset_sticker), 1024, 1024, transparent = true),
        BoardPreset(context.getString(R.string.som_board_preset_wallpaper), screenW, screenH),
        BoardPreset(context.getString(R.string.som_board_preset_square), 1080, 1080),
        BoardPreset(context.getString(R.string.som_board_preset_portrait), 1080, 1350),
        BoardPreset(context.getString(R.string.som_board_preset_story), 1080, 1920),
        BoardPreset(context.getString(R.string.som_board_preset_a4), 2480, 3508),
    )
}

// Tablice zapisane jako JSON (do dalszej edycji) + miniatura PNG, zdjęcia w assets/.
object BoardStore {
    private fun folder(context: Context) = File(context.filesDir, "stickers/boards").apply { mkdirs() }
    fun assets(context: Context) = File(folder(context), "assets").apply { mkdirs() }

    fun list(context: Context): List<Board> =
        folder(context).listFiles { f -> f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { runCatching { Board.of(it.readText()) }.getOrNull() }
            .orEmpty()

    fun thumbnailFile(context: Context, id: String) = File(folder(context), "$id.png")

    suspend fun save(context: Context, board: Board, thumbnail: Bitmap?) = withContext(Dispatchers.IO) {
        File(folder(context), "${board.id}.json").writeText(board.toJson())
        thumbnail?.let { t -> thumbnailFile(context, board.id).outputStream().use { t.compress(Bitmap.CompressFormat.PNG, 90, it) } }
    }

    // Razem z tablicą znikają jej pliki w assets/ (zdjęcia, tło, kopie naklejek) — ale tylko te, których nie używa inna tablica.
    fun delete(context: Context, board: Board) {
        runCatching {
            File(folder(context), "${board.id}.json").delete()
            StickerLibrary.dropThumbs(context, thumbnailFile(context, board.id)) // kopie miniatury w cache
            thumbnailFile(context, board.id).delete()
            val assetsDir = assets(context).canonicalPath + File.separator
            val usedElsewhere = list(context).filter { it.id != board.id }
                .flatMap { b -> b.layers.mapNotNull { it.path } + listOfNotNull(b.bgImage) }.toSet()
            (board.layers.mapNotNull { it.path } + listOfNotNull(board.bgImage))
                .filter { it !in usedElsewhere }
                .map(::File)
                .filter { it.canonicalPath.startsWith(assetsDir) }
                .forEach { it.delete() }
        }
    }

    // Naklejka z biblioteki skopiowana do assets/ (null = nie udało się, wtedy zostaje ścieżka biblioteki).
    suspend fun copyAsset(context: Context, source: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(assets(context), "${UUID.randomUUID()}.${source.extension.ifBlank { "png" }}")
            source.copyTo(file)
            file.absolutePath
        }.getOrNull()
    }

    // Zdjęcie z galerii jako warstwa albo tło: kopia w assets/ (oryginał może zniknąć z telefonu).
    suspend fun importPhoto(context: Context, photo: Bitmap): String = withContext(Dispatchers.IO) {
        val file = File(assets(context), "${UUID.randomUUID()}.jpg")
        file.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        file.absolutePath
    }
}
