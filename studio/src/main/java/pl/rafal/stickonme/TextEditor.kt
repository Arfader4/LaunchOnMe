package pl.rafal.stickonme

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Edytor tekstu na tablicy w stylu Instagrama: pełny ekran nad przygaszoną tablicą, tekst piszesz od razu
// i od razu widzisz jego wygląd. Pionowy suwak = rozmiar, przycisk „A” = styl (zwykły / tło pod tekstem / obrys),
// czcionki i kolory nad klawiaturą. ✓ Gotowe oddaje warstwę, ✕ (albo Wstecz) rezygnuje.
// base = „normalna” wielkość liter dla tej tablicy (px tablicy) — suwak chodzi od 0,3× do 3× base.

private enum class TextLook { PLAIN, BACKGROUND, OUTLINE }

private fun lookOf(layer: Layer) = when {
    layer.highlight != null -> TextLook.BACKGROUND
    layer.outlineWidth > 0f -> TextLook.OUTLINE
    else -> TextLook.PLAIN
}

// Czarny albo biały — co lepiej widać na danym kolorze (jak automatyczny kolor tekstu na przycisku).
private fun contrastOn(color: Long): Long = if (Color(color).luminance() > 0.5f) 0xFF111111L else 0xFFFFFFFFL

// Czcionka systemowa po nazwie rodziny (te same nazwy co w BoardRenderer: "serif", "cursive", "sans-serif-black"…).
private fun familyOf(name: String): FontFamily = FontFamily(Typeface.create(name, Typeface.NORMAL))

@Composable
fun TextEditorOverlay(initial: Layer, base: Float, onDone: (Layer) -> Unit, onCancel: () -> Unit) {
    var layer by remember { mutableStateOf(initial) }
    var look by remember { mutableStateOf(lookOf(initial)) }
    // Wybrany kolor z palety: w trybie „tło” to kolor tła pod tekstem, w pozostałych — kolor liter.
    var picked by remember { mutableStateOf(initial.highlight?.let { it or 0xFF000000L } ?: initial.color) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { } // po pierwszym ułożeniu ekranu — wcześniej prośba o fokus przepada
        runCatching { focus.requestFocus() } // klawiatura od razu (jak w Instagramie)
    }
    BackHandler { onCancel() }

    // Styl liter wynika z trybu i wybranego koloru — liczony przy każdej zmianie (jak właściwość obliczana).
    fun styled(l: Layer, mode: TextLook, color: Long): Layer = when (mode) {
        TextLook.PLAIN -> l.copy(color = color, highlight = null, outlineWidth = 0f)
        TextLook.BACKGROUND -> l.copy(color = contrastOn(color), highlight = color, outlineWidth = 0f)
        TextLook.OUTLINE -> l.copy(color = color, highlight = null, outlineColor = contrastOn(color), outlineWidth = l.size * 0.06f)
    }
    // Zmiana trybu „A” albo koloru — przelicza kolory liter, tła i obrysu.
    fun update(l: Layer = layer, mode: TextLook = look, color: Long = picked) {
        look = mode
        picked = color
        layer = styled(l, mode, color)
    }
    // Pozostałe zmiany (tekst, B / I / U, cień, wyrównanie, czcionka) — bez ruszania kolorów, więc starszy tekst
    // z własnymi kolorami nie zmienia wyglądu przy pierwszym naciśnięciu klawisza.
    fun edit(l: Layer) {
        layer = l
    }

    val colors = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Podgląd: wielkość na ekranie proporcjonalna do wielkości na tablicy (base ≈ 30 sp).
    val previewSp = (30f * layer.size / base).coerceIn(10f, 90f)
    val align = when (layer.align) {
        Layer.ALIGN_LEFT -> TextAlign.Start
        Layer.ALIGN_RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }
    val family = remember(layer.font) { familyOf(layer.font) }
    val style = TextStyle(
        color = Color(layer.color),
        fontSize = previewSp.sp,
        fontFamily = family,
        fontWeight = if (layer.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (layer.italic) FontStyle.Italic else FontStyle.Normal,
        textAlign = align,
        textDecoration = if (layer.underline > 0f) TextDecoration.Underline else null,
        shadow = if (layer.shadow) Shadow(Color(0x99000000), Offset(0f, previewSp * 0.15f), previewSp * 0.25f) else null,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE60B0C0E)) // przygaszona tablica pod spodem
            .clickable(interactionSource = null, indication = null) {} // dotknięcia nie przechodzą do tablicy
            .imePadding(),
    ) {
        // Górny pasek: ✕ | wyrównanie | styl „A” | B | I | cień | ✓ Gotowe
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            StudioIconButton(StudioIcons.Close, stringResource(R.string.som_cancel), outlined = true, onClick = onCancel)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            ) {
                // Wyrównanie: dotknięcie przełącza do środka → do lewej → do prawej.
                ToggleCircle(
                    label = when (layer.align) { Layer.ALIGN_LEFT -> "⇤"; Layer.ALIGN_RIGHT -> "⇥"; else -> "≡" },
                    description = stringResource(R.string.som_text_align_cd),
                    on = false,
                ) {
                    val next = when (layer.align) { Layer.ALIGN_CENTER -> Layer.ALIGN_LEFT; Layer.ALIGN_LEFT -> Layer.ALIGN_RIGHT; else -> Layer.ALIGN_CENTER }
                    edit(layer.copy(align = next))
                }
                ToggleCircle(
                    label = "A",
                    description = stringResource(R.string.som_text_look_cd),
                    on = look != TextLook.PLAIN,
                    filled = look == TextLook.BACKGROUND,
                    outlined = look == TextLook.OUTLINE,
                ) {
                    update(mode = when (look) { TextLook.PLAIN -> TextLook.BACKGROUND; TextLook.BACKGROUND -> TextLook.OUTLINE; TextLook.OUTLINE -> TextLook.PLAIN })
                }
                ToggleCircle("B", stringResource(R.string.som_text_bold_cd), on = layer.bold, bold = true) { edit(layer.copy(bold = !layer.bold)) }
                ToggleCircle("I", stringResource(R.string.som_text_italic_cd), on = layer.italic, italic = true) { edit(layer.copy(italic = !layer.italic)) }
                ToggleCircle("U", stringResource(R.string.som_text_underline_cd), on = layer.underline > 0f, underline = true) {
                    edit(layer.copy(underline = if (layer.underline > 0f) 0f else layer.size * 0.06f))
                }
                ToggleCircle("◐", stringResource(R.string.som_text_shadow_cd), on = layer.shadow) { edit(layer.copy(shadow = !layer.shadow)) }
            }
            StudioPrimaryButton(stringResource(R.string.som_done)) { onDone(layer) }
        }

        // Środek: pionowy suwak rozmiaru po lewej + tekst na żywo.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VerticalSizeSlider(
                value = layer.size,
                range = base * 0.3f..base * 3f,
                onChange = { size ->
                    // Obrys i podkreślenie rosną razem z literami.
                    val l = layer.copy(
                        size = size,
                        underline = if (layer.underline > 0f) size * 0.06f else 0f,
                    )
                    edit(if (look == TextLook.OUTLINE) l.copy(outlineWidth = size * 0.06f) else l)
                },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).height(240.dp),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(start = 64.dp, end = 24.dp),
            ) {
                val hl = layer.highlight
                val pill = if (hl != null) Modifier.clip(RoundedCornerShape((previewSp * 0.4f).dp)).background(Color(hl)).padding(horizontal = 14.dp, vertical = 6.dp) else Modifier
                Box(pill) {
                    // Obrys w podglądzie: te same litery grubą kreską pod spodem (jak w BoardRenderer).
                    if (layer.outlineWidth > 0f) {
                        Text(
                            layer.text.ifEmpty { " " },
                            style = style.copy(
                                color = Color(layer.outlineColor),
                                drawStyle = Stroke(width = with(density) { previewSp.sp.toPx() } * 0.12f, join = androidx.compose.ui.graphics.StrokeJoin.Round), // jak w BoardRenderer: 2 × 6% wielkości liter
                                shadow = null,
                            ),
                        )
                    }
                    BasicTextField(
                        value = layer.text,
                        onValueChange = { edit(layer.copy(text = it)) },
                        textStyle = style,
                        cursorBrush = SolidColor(Color(layer.color)), // kursor w kolorze liter — widać go także na kolorowym tle
                        // Szerokość = najdłuższa linia (jak na tablicy) — tło pod tekstem obejmuje tylko tekst.
                        modifier = Modifier.focusRequester(focus).width(IntrinsicSize.Max),
                        decorationBox = { inner ->
                            Box {
                                if (layer.text.isEmpty()) {
                                    Text(stringResource(R.string.som_text_placeholder), style = style.copy(color = Color(layer.color).copy(alpha = 0.5f), shadow = null))
                                }
                                inner()
                            }
                        },
                    )
                }
            }
        }

        // Czcionki (każda nazwa napisana swoją czcionką) i kolory — nad klawiaturą.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            BoardFonts.forEach { (name, label) ->
                val on = layer.font == name
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) colors.onSurface else colors.surface)
                        .border(1.dp, if (on) colors.onSurface else colors.outline, RoundedCornerShape(20.dp))
                        .clickable(role = Role.RadioButton) { edit(layer.copy(font = name)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(label),
                        fontFamily = remember(name) { familyOf(name) },
                        fontSize = 15.sp,
                        color = if (on) colors.surface else colors.onSurface,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        ) {
            BoardPalette.forEach { c ->
                val on = (c or 0xFF000000L) == (picked or 0xFF000000L)
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (on) 3.dp else 2.dp, if (on) colors.secondary else Color(0xCCFFFFFF), CircleShape)
                        .clickable(role = Role.RadioButton) { update(color = c or 0xFF000000L) },
                )
            }
        }
    }
}

// Okrągły przełącznik stylu w górnym pasku edytora tekstu.
@Composable
private fun ToggleCircle(
    label: String,
    description: String,
    on: Boolean,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    filled: Boolean = false,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (filled) colors.onSurface else if (on) colors.primaryContainer else colors.surface)
            .border(if (on) 2.dp else 1.dp, if (on) colors.primary else colors.outlineVariant, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(
            label,
            color = if (filled) colors.surface else colors.onSurface,
            fontSize = 18.sp,
            fontWeight = if (bold || label == "A") FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (underline) TextDecoration.Underline else null,
            style = if (outlined) TextStyle(drawStyle = Stroke(width = 3f)) else TextStyle.Default,
        )
    }
}

// Pionowy suwak (góra = większy tekst), jak w Instagramie. Przeciąganie albo dotknięcie w wybranym miejscu.
@Composable
private fun VerticalSizeSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier) {
    val current by rememberUpdatedState(onChange)
    val desc = stringResource(R.string.som_text_size_cd)
    val track = MaterialTheme.colorScheme.outline
    val thumb = MaterialTheme.colorScheme.onSurface
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Canvas(
        modifier
            .width(40.dp)
            .semantics { contentDescription = desc }
            .pointerInput(range) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val r = 11.dp.toPx() // promień kółka — to samo co przy rysowaniu, żeby kółko było pod palcem
                    fun set(y: Float) {
                        val f = 1f - ((y - r) / (size.height - 2 * r).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        current(range.start + f * (range.endInclusive - range.start))
                    }
                    set(down.position.y)
                    down.consume()
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            pressed = false
                        } else {
                            set(change.position.y)
                            change.consume()
                        }
                    }
                }
            },
    ) {
        val x = size.width / 2f
        val r = 11.dp.toPx()
        // Klin: u góry szerszy (większy tekst), na dole wąski.
        drawLine(track, Offset(x, r), Offset(x, size.height - r), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        val y = r + (1f - fraction) * (size.height - 2 * r)
        drawCircle(Color(0xFF0E0F12), radius = r + 2.dp.toPx(), center = Offset(x, y))
        drawCircle(thumb, radius = r, center = Offset(x, y))
    }
}
