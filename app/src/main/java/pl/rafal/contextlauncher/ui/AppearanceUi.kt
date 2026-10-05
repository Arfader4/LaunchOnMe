package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R
import pl.rafal.onthemes.ThemeMode
import pl.rafal.onthemes.ThemeSpec
import pl.rafal.onthemes.Themes

// Czy aktualny motyw jest ciemny (po kolorze tła) — do podglądu schematów w tej samej wersji.
@Composable
fun isThemeDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

// Globalne ustawienia wyglądu: jasny/ciemny/auto i domyślny schemat dla trybów bez własnego.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    themeMode: ThemeMode,
    defaultPalette: ThemeSpec,
    onThemeMode: (ThemeMode) -> Unit,
    onDefaultPalette: (ThemeSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Text(stringResource(R.string.look_title), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.look_brightness), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(selected = mode == themeMode, onClick = { onThemeMode(mode) }, label = { Text(mode.label) })
                }
            }

            Text(stringResource(R.string.look_default_scheme), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.look_default_scheme_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PaletteRow(selected = defaultPalette, onSelect = { it?.let(onDefaultPalette) }, allowDefault = false)
        }
    }
}

// Rząd kart schematów: nazwa + trzy kolory. allowDefault dodaje kartę "Domyślny" (null).
@Composable
fun PaletteRow(selected: ThemeSpec?, onSelect: (ThemeSpec?) -> Unit, allowDefault: Boolean) {
    val dark = isThemeDark()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        if (allowDefault) {
            PaletteCard(label = stringResource(R.string.look_default), swatches = emptyList(), selected = selected == null, onClick = { onSelect(null) })
        }
        Themes.all.forEach { palette ->
            PaletteCard(
                label = palette.label,
                swatches = palette.swatches(dark),
                selected = palette == selected,
                onClick = { onSelect(palette) },
            )
        }
    }
}

@Composable
private fun PaletteCard(label: String, swatches: List<Color>, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (swatches.isEmpty()) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            swatches.forEach { color ->
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
