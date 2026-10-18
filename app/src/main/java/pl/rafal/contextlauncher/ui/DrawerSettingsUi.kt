package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.DrawerSettings
import pl.rafal.contextlauncher.data.DrawerSort
import pl.rafal.contextlauncher.data.DrawerView

// 1.7.0: sekcja "Szuflada aplikacji" w arkuszu OnMode: przełącznik "własna szuflada" + (gdy włączony) jej ustawienia.
// Wyłączony = tryb idzie za ustawieniami ogólnymi z ekranu Ustawienia.
@Composable
fun ModeDrawerSettingsSection(
    override: DrawerSettings?,
    global: DrawerSettings,
    onOverrideChange: (DrawerSettings?) -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))
    Text(stringResource(R.string.dset_title), style = MaterialTheme.typography.titleSmall)
    DrawerSwitchRow(
        title = stringResource(R.string.dset_own),
        subtitle = if (override != null) stringResource(R.string.dset_own_on)
        else stringResource(R.string.dset_own_off, drawerViewLabel(global.view) + " · " + drawerSortLabel(global.sort)),
        checked = override != null,
        // Włączenie startuje od ustawień ogólnych — zmieniasz tylko to, co ma być inne.
        onToggle = { on -> onOverrideChange(if (on) global else null) },
    )
    if (override != null) {
        DrawerSettingsSection(settings = override, onChange = { onOverrideChange(it) })
    }
}

@Composable
fun drawerViewLabel(view: DrawerView): String = when (view) {
    DrawerView.TABS -> stringResource(R.string.dset_view_tabs)
    DrawerView.INTEGRATED -> stringResource(R.string.dset_view_integrated)
}

// Same ustawienia szuflady (widok, sortowanie, sekcje) — w arkuszu OnMode i na ekranie Ustawienia.
// Każda zmiana zapisuje się od razu.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerSettingsSection(
    settings: DrawerSettings,
    onChange: (DrawerSettings) -> Unit,
) {
    Text(stringResource(R.string.dset_view), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        FilterChip(
            selected = settings.view == DrawerView.TABS,
            onClick = { onChange(settings.copy(view = DrawerView.TABS)) },
            label = { Text(drawerViewLabel(DrawerView.TABS)) },
        )
        FilterChip(
            selected = settings.view == DrawerView.INTEGRATED,
            onClick = { onChange(settings.copy(view = DrawerView.INTEGRATED)) },
            label = { Text(drawerViewLabel(DrawerView.INTEGRATED)) },
        )
    }

    Text(stringResource(R.string.dset_sort), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        DrawerSort.entries.forEach { sort ->
            FilterChip(selected = settings.sort == sort, onClick = { onChange(settings.copy(sort = sort)) }, label = { Text(drawerSortLabel(sort)) })
        }
    }
    if (settings.sort == DrawerSort.MANUAL) {
        Text(
            stringResource(R.string.dset_manual_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // "Foldery na początku" ma sens tylko wtedy, gdy foldery stoją na tej samej liście co aplikacje.
    val integrated = settings.view == DrawerView.INTEGRATED
    DrawerSwitchRow(
        title = stringResource(R.string.dset_folders_first),
        subtitle = stringResource(if (integrated) R.string.dset_folders_first_desc else R.string.dset_folders_first_off),
        checked = settings.foldersFirst,
        enabled = integrated,
        onToggle = { onChange(settings.copy(foldersFirst = it)) },
    )
    DrawerSwitchRow(
        title = stringResource(R.string.dset_show_new),
        subtitle = stringResource(R.string.dset_show_new_desc),
        checked = settings.showNew,
        onToggle = { onChange(settings.copy(showNew = it)) },
    )
    DrawerSwitchRow(
        title = stringResource(R.string.dset_show_frequent),
        subtitle = stringResource(R.string.dset_show_frequent_desc),
        checked = settings.showFrequent,
        onToggle = { onChange(settings.copy(showFrequent = it)) },
    )
}

@Composable
private fun DrawerSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onToggle(!checked) }
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}
