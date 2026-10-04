package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.R

// Kolory tła do wyboru dla pojedynczego widżetu (plus "A" = ze schematu i "+" = dowolny).
private val WidgetBgColors = listOf(
    0xFF000000, 0xFF1A1C20, 0xFF23262B, 0xFF10151A, 0xFF0B1220, 0xFF1D3128, 0xFF2A161C,
    0xFFFFFFFF, 0xFFF5F1E8, 0xFFFFE8A3, 0xFFCDEBFF, 0xFFD6F5E8, 0xFFFFD6DC,
)

// Wygląd jednego widżetu: kolor tła i przezroczystość (niezależnie od ustawień globalnych).
// Zmiany zapisują się od razu — widać je na karcie za oknem. onOpenContent = ustawienia treści widżetu (jeśli ma).
@Composable
fun WidgetLookDialog(
    title: String,
    background: Long?,        // null = kolor ze schematu
    opacity: Int?,            // null = jak w Ustawieniach
    globalOpacity: Int,
    onChange: (background: Long?, opacity: Int?) -> Unit,
    onOpenContent: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onOpenContent != null) {
                    TextButton(onClick = onOpenContent) { Text(stringResource(R.string.wlook_widget_settings)) }
                }
                Text(stringResource(R.string.wlook_bg_color), style = MaterialTheme.typography.labelLarge)
                ColorSwatches(WidgetBgColors, background, { onChange(it, opacity) }, allowNone = true)
                Text(stringResource(R.string.wlook_bg_transparency), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = opacity == null,
                        onClick = { onChange(background, null) },
                        label = { Text(stringResource(R.string.wlook_as_in_settings, globalOpacity)) },
                    )
                }
                // Suwak trzyma wartość lokalnie, a zapis idzie dopiero po puszczeniu palca (bez zapisu przy każdym pikselu).
                var local by remember(opacity, globalOpacity) { mutableFloatStateOf((opacity ?: globalOpacity).toFloat()) }
                Text(stringResource(R.string.wlook_opacity_value, (local / 5).toInt() * 5), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = local,
                    onValueChange = { local = it },
                    onValueChangeFinished = { onChange(background, (local / 5).toInt() * 5) }, // co 5%
                    valueRange = 0f..100f,
                    modifier = Modifier,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
    )
}
