package pl.rafal.onthemes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Wspólne kawałki ekranów OnThemes: karta motywu, miniatura, wybór Luxury, wiersz trybu i okna dialogowe.

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Karta motywu: miniatura ekranu w jego kolorach + nazwa + krótka paleta (po odcieniu).
// selected = motyw globalny (✓). Dotknięcie otwiera ekran motywu.
@Composable
internal fun ThemeCard(spec: ThemeSpec, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val selectedLabel = stringResource(R.string.ot_selected)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
            .semantics { if (selected) contentDescription = selectedLabel },
    ) {
        MiniPreview(spec.look(dark))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    spec.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                }
            }
            PaletteDots(spec.palette.take(8), 14)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PaletteDots(colors: List<Long>, sizeDp: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEach { c ->
            Box(
                Modifier
                    .size(sizeDp.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
            )
        }
    }
}

// Miniatura launchera w kolorach motywu: tło, karta z dwiema "liniami tekstu", akcent i trzy znaczki z palety.
@Composable
internal fun MiniPreview(look: PreviewLook) {
    val r = look.roles
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .size(width = 104.dp, height = 74.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(r.background))
            .border(1.dp, Color(r.outline), RoundedCornerShape(12.dp))
            .padding(7.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(Color(r.surface))
                .padding(5.dp),
        ) {
            Box(Modifier.size(width = 46.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(r.onBackground)))
            Box(Modifier.size(width = 30.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(Color(r.onSurfaceVariant)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            look.palette.take(3).forEach { c -> ThemedBadge(look, c, null, 15.dp, RoundedCornerShape(5.dp)) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 22.dp, height = 10.dp).clip(RoundedCornerShape(5.dp)).background(Color(r.primary)))
        }
    }
}

// Luxury: wybór metalu i bazy (każde połączenie to osobny motyw) + przełącznik ruchomego refleksu.
// onChange dostaje nowy wariant — ekran sam decyduje, czy to podgląd, czy od razu motyw globalny.
@Composable
internal fun LuxuryPicker(store: ThemeStore, current: ThemeSpec, onChange: (ThemeSpec) -> Unit) {
    val metal = current.metal ?: Metal.GOLD
    val base = current.luxuryBase ?: LuxuryBase.BOTTLE
    val sheen by store.luxurySheen.collectAsState()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.ot_luxury_metal), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Metal.entries.forEach { m ->
                FilterChip(
                    selected = m == metal,
                    onClick = { onChange(Luxury.spec(m, base)) },
                    label = { Text(m.label) },
                    leadingIcon = { Box(Modifier.size(16.dp).clip(CircleShape).background(m.brush())) },
                )
            }
        }
        Text(stringResource(R.string.ot_luxury_base), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            LuxuryBase.entries.forEach { b ->
                FilterChip(
                    selected = b == base,
                    onClick = { onChange(Luxury.spec(metal, b)) },
                    label = { Text(b.label) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(b.surfaceVariant))
                                .border(2.dp, Color(b.jewel), CircleShape),
                        )
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ot_luxury_sheen), style = MaterialTheme.typography.bodyLarge)
                Hint(stringResource(R.string.ot_luxury_sheen_sub))
            }
            Spacer(Modifier.width(12.dp))
            // Podgląd refleksu: mały metalowy kafelek, który błyszczy, gdy przełącznik jest włączony.
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(metal.brush())
                    .then(if (sheen) Modifier.metalSheen() else Modifier),
            )
            Spacer(Modifier.width(12.dp))
            Switch(checked = sheen, onCheckedChange = { store.setLuxurySheen(it) })
        }
    }
}

// Wiersz trybu: znaczek w stylu motywu, którego tryb używa, nazwa i ten motyw. Dotknięcie = zmiana motywu trybu.
@Composable
internal fun ModeRow(mode: HostMode, global: ThemeSpec, dark: Boolean, onClick: () -> Unit) {
    val own = Themes.find(mode.themeId)
    val spec = own ?: global
    val badgeDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val look = spec.look(dark).copy(dark = badgeDark)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        ThemedBadge(look, mode.color, mode.iconRes, 34.dp, RoundedCornerShape(11.dp))
        Spacer(Modifier.width(12.dp))
        Text(mode.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (own != null) own.label else stringResource(R.string.ot_mode_global),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PaletteDots(spec.swatches(dark).map { it.toArgb().toLong() and 0xFFFFFFFFL }, 9)
        }
    }
}

// Wybór motywu dla jednego trybu: "Motyw globalny" albo dowolny z katalogu (Luxury w wariancie,
// który tryb już ma, albo w wariancie motywu globalnego).
@Composable
internal fun ModeThemeDialog(mode: HostMode, global: ThemeSpec, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val own = Themes.find(mode.themeId)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.ot_mode_theme_title, mode.name)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                ChoiceRow(stringResource(R.string.ot_mode_global), own == null) { onPick(null) }
                Themes.all.forEach { base ->
                    val variant = when {
                        own != null && own.catalogId == base.catalogId -> own
                        global.catalogId == base.catalogId -> global
                        else -> base
                    }
                    ChoiceRow(variant.label, own != null && own.catalogId == base.catalogId, variant.palette.take(5)) { onPick(variant.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ot_cancel)) } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, dots: List<Long> = emptyList(), onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (dots.isNotEmpty()) PaletteDots(dots, 10)
    }
}

// "Ustaw dla trybów…": zaznaczone tryby dostają ten motyw, odznaczone (które go miały) wracają do globalnego.
@Composable
internal fun ApplyToModesDialog(spec: ThemeSpec, modes: List<HostMode>, onConfirm: (Map<Long, String?>) -> Unit, onDismiss: () -> Unit) {
    val initial = remember(modes, spec.id) { modes.filter { Themes.normalizeId(it.themeId) == spec.id }.map { it.id }.toSet() }
    var checked by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.ot_apply_modes_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Hint(stringResource(R.string.ot_apply_modes_hint, spec.label))
                if (modes.isEmpty()) Hint(stringResource(R.string.ot_modes_empty))
                modes.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checked = if (m.id in checked) checked - m.id else checked + m.id },
                    ) {
                        Checkbox(checked = m.id in checked, onCheckedChange = { on -> checked = if (on) checked + m.id else checked - m.id })
                        Text(m.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Tylko zmiany: nowo zaznaczone → ten motyw, odznaczone → globalny (null).
                val changes = HashMap<Long, String?>()
                (checked - initial).forEach { changes[it] = spec.id }
                (initial - checked).forEach { changes[it] = null }
                onConfirm(changes)
            }) { Text(stringResource(R.string.ot_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ot_cancel)) } },
    )
}

// Dowolny kolor: barwa, nasycenie i jasność (HSV) suwakami + HEX. Jak ColorDialog z WinForms, tylko prościej.
@Composable
internal fun HsvColorDialog(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val start = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hex by remember { mutableStateOf(toHex(argb)) }
    fun fromSliders() {
        hex = toHex(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.ot_color_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Spacer(Modifier.width(16.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { v ->
                            hex = v.uppercase().take(7)
                            parseHex(hex)?.let { c ->
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(c, hsv)
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                            }
                        },
                        singleLine = true,
                        label = { Text("HEX") },
                    )
                }
                // Tęczowy pasek nad suwakiem barwy — widać, gdzie jest jaki kolor.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })),
                )
                Slider(value = hue, onValueChange = { hue = it; fromSliders() }, valueRange = 0f..360f)
                Text(stringResource(R.string.ot_color_saturation), style = MaterialTheme.typography.labelMedium)
                Slider(value = sat, onValueChange = { sat = it; fromSliders() })
                Text(stringResource(R.string.ot_color_brightness), style = MaterialTheme.typography.labelMedium)
                Slider(value = value, onValueChange = { value = it; fromSliders() })
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(argb.toLong() and 0xFFFFFFFFL) }) { Text(stringResource(R.string.ot_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ot_cancel)) } },
    )
}

private fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val t = text.removePrefix("#")
    if (t.length != 6) return null
    return t.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
}
