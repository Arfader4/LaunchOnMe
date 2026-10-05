package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R

// Wybór dowolnego koloru: barwa (koło kolorów rozwinięte w pasek), nasycenie, jasność + kod HEX.
// HSV to ten sam model co w Paint/Photoshopie: łatwiej "dojść" do koloru niż suwakami R, G, B.
@Composable
fun ColorPickerDialog(
    initial: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    badgePreview: Boolean = false, // kolor trybu: obok podgląd, jak wyjdzie na znaczku w tym motywie
) {
    val start = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV((initial ?: 0xFF4DF5CD).toInt(), it) }
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hex by remember { mutableStateOf(toHex(argb)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.look_custom_color)) },
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
                    if (badgePreview) {
                        Spacer(Modifier.width(8.dp))
                        val dark = isThemeDark()
                        val style = pl.rafal.onthemes.LocalThemeSpec.current.badge
                        val c = argb.toLong() and 0xFFFFFFFFL
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(LocalIconShape.current.shape(48.dp))
                                .background(modeBadgeColor(c, dark, style)),
                        ) { Text("✓", color = modeBadgeSymbol(dark, c, style, pl.rafal.onthemes.LocalThemeSpec.current.metal), style = MaterialTheme.typography.titleMedium) }
                    }
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
                        label = { Text("HEX") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.width(140.dp),
                    )
                }
                Text(stringResource(R.string.look_hue), style = MaterialTheme.typography.labelLarge)
                // Tęczowy pasek nad suwakiem barwy — widać, dokąd przesunąć.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })),
                )
                Slider(value = hue, onValueChange = { hue = it; hex = toHex(android.graphics.Color.HSVToColor(floatArrayOf(it, sat, value))) }, valueRange = 0f..360f)
                Text(stringResource(R.string.look_saturation), style = MaterialTheme.typography.labelLarge)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value)))),
                )
                Slider(value = sat, onValueChange = { sat = it; hex = toHex(android.graphics.Color.HSVToColor(floatArrayOf(hue, it, value))) })
                Text(stringResource(R.string.look_value), style = MaterialTheme.typography.labelLarge)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, sat, 1f)))),
                )
                Slider(value = value, onValueChange = { value = it; hex = toHex(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, it))) })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(argb.toLong() and 0xFFFFFFFFL) }) { Text(stringResource(R.string.look_pick)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// #RRGGBB (bez przezroczystości — kolory w launcherze są zawsze pełne).
private fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val clean = text.removePrefix("#")
    if (clean.length != 6) return null
    return clean.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
}

// Kolor jako Long (ARGB) — tak zapisujemy kolory w bazie i ustawieniach.
fun Color.toLongArgb(): Long = toArgb().toLong() and 0xFFFFFFFFL
