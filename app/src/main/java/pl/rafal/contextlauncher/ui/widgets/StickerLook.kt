package pl.rafal.contextlauncher.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Kształt, do którego przycinamy obrazek naklejki. square = kształt ma sens tylko w kwadracie (koło, serce…).
enum class StickerShape(val label: String, val square: Boolean) {
    NONE("Bez", false),
    ROUNDED("Zaokrąglony", false),
    CIRCLE("Koło", true),
    OVAL("Owal", false),
    HEART("Serce", true),
    STAR("Gwiazda", true),
    HEXAGON("Sześciokąt", true),
    FLOWER("Kwiatek", true),
    ARCH("Łuk", false),
    ;

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: NONE
    }
}

// Ramka / styl naklejki.
enum class StickerFrame(val label: String) {
    NONE("Bez"),
    OUTLINE("Biały kontur"), // jak wycięta naklejka winylowa — kontur idzie po kształcie obrazka
    SHADOW("Cień"),
    STAMP("Znaczek"),        // ząbkowany brzeg jak znaczek pocztowy
    INSTAX("Instax"),        // biała ramka z szerszym dołem, pionowa
    POLAROID("Polaroid"),    // kwadratowe zdjęcie w ramce z grubym dołem
    FILM("Klisza"),          // czarny pasek z perforacją
    TAPE("Taśma"),           // przyklejona taśmą washi w rogach
    ;

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: NONE
    }
}

// Kształt Compose dla danego rodzaju. GenericShape = ścieżka rysowana na rozmiarze elementu (jak Geometry w WPF).
fun StickerShape.toShape(): Shape? = when (this) {
    StickerShape.NONE -> null
    StickerShape.ROUNDED -> RoundedCornerShape(18) // 18% boku
    StickerShape.CIRCLE -> CircleShape
    StickerShape.OVAL -> GenericShape { size, _ -> addOval(Rect(Offset.Zero, size)) }
    StickerShape.HEART -> GenericShape { s, _ ->
        val w = s.width
        val h = s.height
        moveTo(0.5f * w, 0.92f * h)
        cubicTo(0.08f * w, 0.66f * h, -0.02f * w, 0.34f * h, 0.2f * w, 0.16f * h)
        cubicTo(0.35f * w, 0.04f * h, 0.5f * w, 0.14f * h, 0.5f * w, 0.28f * h)
        cubicTo(0.5f * w, 0.14f * h, 0.65f * w, 0.04f * h, 0.8f * w, 0.16f * h)
        cubicTo(1.02f * w, 0.34f * h, 0.92f * w, 0.66f * h, 0.5f * w, 0.92f * h)
        close()
    }
    StickerShape.STAR -> polygon(10) { i -> if (i % 2 == 0) 1f else 0.46f }
    StickerShape.HEXAGON -> polygon(6) { 1f }
    StickerShape.FLOWER -> polygon(120) { i -> 0.88f + 0.12f * cos(i * 2 * PI * 8 / 120).toFloat() } // 8 płatków
    StickerShape.ARCH -> GenericShape { s, _ ->
        val w = s.width
        val h = s.height
        moveTo(0f, h)
        lineTo(0f, w / 2)
        arcTo(Rect(0f, 0f, w, w), 180f, 180f, false) // półkole na górze
        lineTo(w, h)
        close()
    }
}

// Wielokąt foremny (albo "falujący") wokół środka; radius(i) = promień i-tego wierzchołka jako ułamek połowy boku.
private fun polygon(points: Int, radius: (Int) -> Float) = GenericShape { s, _ ->
    val cx = s.width / 2
    val cy = s.height / 2
    val r = minOf(cx, cy)
    for (i in 0 until points) {
        val angle = -PI / 2 + i * 2 * PI / points // od góry, zgodnie z zegarem
        val x = cx + r * radius(i) * cos(angle).toFloat()
        val y = cy + r * radius(i) * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

// Sam obrazek: przycięty do kształtu (wtedy wypełnia go, Crop) albo cały (Fit, np. wycięty obiekt z przezroczystością).
@Composable
private fun Picture(
    bitmap: ImageBitmap,
    shape: StickerShape,
    flipped: Boolean,
    modifier: Modifier = Modifier,
    tint: Color? = null,          // kolorowa "sylwetka" obrazka (do konturu i cienia)
    forceCrop: Boolean = false,   // ramki papierowe zawsze wypełniają okienko
) {
    val clip = shape.toShape()
    val crop = clip != null || forceCrop
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it) }, // tint = SrcIn: zostaje kształt, znika treść
        // aspectRatio PRZED modyfikatorem wywołującego: po fillMaxSize() zostałby zignorowany (koło zrobiłoby się owalem).
        modifier = (if (shape.square) Modifier.aspectRatio(1f) else Modifier)
            .then(modifier)
            .then(if (clip != null) Modifier.clip(clip) else Modifier)
            .graphicsLayer { scaleX = if (flipped) -1f else 1f },
    )
}

// Naklejka z kształtem i ramką. Obrót robi wywołujący (dotyczy całości, razem z ramką).
@Composable
internal fun StickerContent(bitmap: ImageBitmap, shape: StickerShape, frame: StickerFrame, flipped: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        when (frame) {
            StickerFrame.NONE -> Picture(bitmap, shape, flipped, Modifier.fillMaxSize())

            // Kontur: 12 białych "sylwetek" przesuniętych dookoła, a na nich obrazek — działa też dla wyciętych obiektów.
            StickerFrame.OUTLINE -> Box(Modifier.fillMaxSize().padding(side * 0.04f), contentAlignment = Alignment.Center) {
                val r = (side * 0.025f).coerceIn(2.dp, 6.dp)
                for (i in 0 until 12) {
                    val a = i * 2 * PI / 12
                    Picture(
                        bitmap, shape, flipped,
                        Modifier.fillMaxSize().offset(r * cos(a).toFloat(), r * sin(a).toFloat()),
                        tint = Color.White,
                    )
                }
                Picture(bitmap, shape, flipped, Modifier.fillMaxSize())
            }

            // Cień: rozmyta czarna sylwetka przesunięta w dół (rozmycie od Androida 12, starsze pokażą ostry cień).
            StickerFrame.SHADOW -> Box(Modifier.fillMaxSize().padding(side * 0.05f), contentAlignment = Alignment.Center) {
                Picture(
                    bitmap, shape, flipped,
                    Modifier.fillMaxSize().offset(3.dp, 5.dp).blur(6.dp, BlurredEdgeTreatment.Unbounded).graphicsLayer { alpha = 0.45f },
                    tint = Color.Black,
                )
                Picture(bitmap, shape, flipped, Modifier.fillMaxSize())
            }

            StickerFrame.STAMP -> Stamp(side) { Picture(bitmap, shape, flipped, Modifier.fillMaxSize(), forceCrop = true) }

            StickerFrame.INSTAX -> PaperFrame(ratio = 0.63f, side = side, bottom = 0.2f) {
                Picture(bitmap, shape, flipped, Modifier.fillMaxSize(), forceCrop = true)
            }

            StickerFrame.POLAROID -> PaperFrame(ratio = 0.84f, side = side, bottom = 0.22f) {
                Picture(bitmap, shape, flipped, Modifier.fillMaxWidth().aspectRatio(1f), forceCrop = true)
            }

            StickerFrame.FILM -> Film(side) { Picture(bitmap, shape, flipped, Modifier.fillMaxSize(), forceCrop = true) }

            // Taśma: obrazek + dwa półprzezroczyste paski na górnych rogach.
            StickerFrame.TAPE -> Box(Modifier.fillMaxSize().padding(side * 0.08f)) {
                Picture(bitmap, shape, flipped, Modifier.fillMaxSize().shadow(3.dp), forceCrop = true)
                val tape = Color(0xCCE9DDBF)
                Box(
                    Modifier.align(Alignment.TopStart).offset(-side * 0.06f, side * 0.02f)
                        .size(side * 0.34f, side * 0.1f).rotate(-38f).background(tape),
                )
                Box(
                    Modifier.align(Alignment.TopEnd).offset(side * 0.06f, side * 0.02f)
                        .size(side * 0.34f, side * 0.1f).rotate(38f).background(tape),
                )
            }
        }
    }
}

// Znaczek pocztowy: biała kartka z wyciętymi półkolami na brzegach (ząbki) i obrazkiem w środku.
@Composable
private fun Stamp(side: Dp, content: @Composable () -> Unit) {
    val hole = (side * 0.035f).coerceIn(3.dp, 7.dp)
    Box(
        Modifier
            .fillMaxSize()
            .padding(side * 0.03f)
            // Offscreen: wycinanie dziur (BlendMode.Clear) działa tylko na osobnej warstwie.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawRect(Color(0xFFF7F3EA))
                val r = hole.toPx()
                val step = r * 3.2f
                var x = step / 2
                while (x < size.width) {
                    drawCircle(Color.Black, r, Offset(x, 0f), blendMode = BlendMode.Clear)
                    drawCircle(Color.Black, r, Offset(x, size.height), blendMode = BlendMode.Clear)
                    x += step
                }
                var y = step / 2
                while (y < size.height) {
                    drawCircle(Color.Black, r, Offset(0f, y), blendMode = BlendMode.Clear)
                    drawCircle(Color.Black, r, Offset(size.width, y), blendMode = BlendMode.Clear)
                    y += step
                }
            }
            .padding(hole * 2.2f),
    ) { content() }
}

// Zdjęcie z natychmiastowego aparatu: biała ramka, szerszy dół. ratio = szerokość / wysokość całej odbitki.
@Composable
private fun PaperFrame(ratio: Float, side: Dp, bottom: Float, content: @Composable () -> Unit) {
    Box(
        Modifier
            .padding(side * 0.04f)
            .aspectRatio(ratio)
            .shadow(4.dp, RoundedCornerShape(3.dp))
            .background(Color(0xFFFBFAF7), RoundedCornerShape(3.dp)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            Column(Modifier.fillMaxSize().padding(start = w * 0.06f, end = w * 0.06f, top = w * 0.06f)) {
                Box(Modifier.fillMaxWidth().weight(1f, fill = true).clip(RoundedCornerShape(1.dp))) { content() }
                Spacer(Modifier.height(h * bottom))
            }
        }
    }
}

// Klisza: czarny pasek z otworami na górze i na dole.
@Composable
private fun Film(side: Dp, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(side * 0.02f)
            .background(Color(0xFF151515), RoundedCornerShape(4.dp)),
    ) {
        Sprockets(side)
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = side * 0.04f)) { content() }
        Sprockets(side)
    }
}

@Composable
private fun Sprockets(side: Dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(side * 0.12f)
            .padding(horizontal = side * 0.03f, vertical = side * 0.03f),
    ) {
        repeat(8) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = side * 0.015f)
                    .background(Color(0xFFDADADA), RoundedCornerShape(2.dp)),
            )
        }
    }
}
