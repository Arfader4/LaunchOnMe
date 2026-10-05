package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R
import pl.rafal.onthemes.CustomColors

// Gotowe punkty wyjścia — potem każdy kolor można zmienić (także na dowolny z palety).
private val ThemeStarters = listOf(
    R.string.look_starter_mint to CustomColors(0xFF10151A, 0xFF1A2229, 0xFF4DF5CD, 0xFFE8EEF0),
    R.string.look_starter_navy to CustomColors(0xFF0B1220, 0xFF141E33, 0xFF6EA8FE, 0xFFE6ECF7),
    R.string.look_starter_burgundy to CustomColors(0xFF1A0E12, 0xFF2A161C, 0xFFE0736B, 0xFFF3E6E8),
    R.string.look_starter_paper to CustomColors(0xFFF5F1E8, 0xFFFFFFFF, 0xFF8B5A2B, 0xFF2A2118),
    R.string.look_starter_snow to CustomColors(0xFFF7F9FC, 0xFFFFFFFF, 0xFF2563EB, 0xFF111827),
)

private val BackgroundPresets = listOf(0xFF000000, 0xFF0F1012, 0xFF10151A, 0xFF0B1220, 0xFF1A0E12, 0xFF14120F, 0xFFF5F1E8, 0xFFF7F9FC, 0xFFFFFFFF)
private val SurfacePresets = listOf(0xFF0D0D0D, 0xFF1A1C20, 0xFF1A2229, 0xFF141E33, 0xFF2A161C, 0xFF24201A, 0xFFFFFFFF, 0xFFEFEAE0, 0xFFE8EDF5)
private val TextPresets = listOf(0xFFFFFFFF, 0xFFECEAE4, 0xFFE8EEF0, 0xFFD0D0D0, 0xFF111111, 0xFF2A2118, 0xFF1F2937)

// Kreator własnego schematu: 4 kolory (tło, powierzchnie, akcent, tekst) + podgląd na żywo.
// Reszta ról (ramki, drugorzędny tekst…) wylicza się sama w CustomColors.roles().
@Composable
fun ThemeCreatorDialog(initial: CustomColors, onSave: (CustomColors) -> Unit, onDismiss: () -> Unit) {
    var colors by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.look_custom_scheme)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreview(colors)
                Text(stringResource(R.string.look_starters), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
                    ThemeStarters.forEach { (name, c) ->
                        TextButton(onClick = { colors = c }) { Text(stringResource(name)) }
                    }
                }
                Text(stringResource(R.string.look_background), style = MaterialTheme.typography.labelLarge)
                ColorSwatches(BackgroundPresets, colors.background, { it?.let { c -> colors = colors.copy(background = c) } })
                Text(stringResource(R.string.look_surfaces), style = MaterialTheme.typography.labelLarge)
                ColorSwatches(SurfacePresets, colors.surface, { it?.let { c -> colors = colors.copy(surface = c) } })
                Text(stringResource(R.string.look_accent), style = MaterialTheme.typography.labelLarge)
                ColorSwatches(ModeColors, colors.accent, { it?.let { c -> colors = colors.copy(accent = c) } })
                Text(stringResource(R.string.look_text), style = MaterialTheme.typography.labelLarge)
                ColorSwatches(TextPresets, colors.text, { it?.let { c -> colors = colors.copy(text = c) } })
                val bgLight = Color(colors.background).luminance() > 0.5f
                val textLight = Color(colors.text).luminance() > 0.5f
                if (bgLight == textLight) {
                    Text(
                        stringResource(R.string.look_low_contrast),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(colors) }) { Text(stringResource(R.string.look_save_and_use)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// Miniatura ekranu w wybranych kolorach: tło, karta z tekstem, przycisk w kolorze akcentu.
@Composable
private fun ThemePreview(c: CustomColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(c.background))
            .padding(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(c.surface))
                .padding(12.dp),
        ) {
            Text(stringResource(R.string.look_preview_mode), color = Color(c.text), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.look_preview_event), color = Color(c.text).copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(c.accent))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.look_preview_button),
                        color = if (Color(c.accent).luminance() > 0.5f) Color(0xFF111111) else Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c.accent).copy(alpha = 0.35f)))
            }
        }
    }
}
