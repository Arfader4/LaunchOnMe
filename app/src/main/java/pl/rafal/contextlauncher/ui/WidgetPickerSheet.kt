package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.CustomWidgetKind
import pl.rafal.contextlauncher.data.widgets.WidgetProvider

// Widżety pogrupowane po aplikacji (jak w Pixel Launcherze): najpierw grupa "LaunchOnMe" z widżetami launchera,
// potem aplikacje alfabetycznie. Grupy są zwinięte — dotknięcie nagłówka je rozwija; przy wyszukiwaniu rozwijają się same.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    providers: List<WidgetProvider>,
    onPick: (WidgetProvider) -> Unit,
    onPickCustom: (CustomWidgetKind) -> Unit,
    onDismiss: () -> Unit,
    appIcon: (packageName: String) -> ImageBitmap? = { null },
) {
    var filter by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(emptySet<String>()) } // klucze rozwiniętych grup
    val searching = filter.isNotBlank()

    // Filtrujemy po nazwie widżetu i nazwie aplikacji ("zeg" znajdzie wszystkie widżety Zegara).
    val shown = providers.filter {
        !searching ||
            it.label.contains(filter.trim(), ignoreCase = true) ||
            it.appLabel.contains(filter.trim(), ignoreCase = true)
    }
    val kinds = CustomWidgetKind.entries.filter {
        it != CustomWidgetKind.STACK && // stos powstaje z upuszczenia widżetu na widżet, nie z listy
        (!searching || it.title.contains(filter.trim(), ignoreCase = true) || it.description.contains(filter.trim(), ignoreCase = true))
    }
    // groupBy ≈ GroupBy w LINQ; klucz = pakiet + profil (ta sama aplikacja w profilu służbowym to osobna grupa).
    val groups = shown
        .groupBy { it.info.provider.packageName + "#" + it.info.profile.hashCode() }
        .toList()
        .sortedBy { (_, list) -> list.first().appLabel.lowercase() }

    fun toggle(key: String) {
        expanded = if (key in expanded) expanded - key else expanded + key
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            stringResource(R.string.picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text(stringResource(R.string.picker_search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (kinds.isNotEmpty()) {
                val open = searching || "launcher" in expanded
                item(key = "h:launcher") {
                    GroupHeader(
                        icon = { OwnAppIcon() },
                        title = "LaunchOnMe",
                        count = kinds.size,
                        open = open,
                        onClick = { toggle("launcher") },
                    )
                }
                if (open) items(kinds, key = { "k:" + it.name }) { kind ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPickCustom(kind) }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(kind.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            kind.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (providers.isEmpty()) {
                item(key = "loading") {
                    Text(stringResource(R.string.picker_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                }
            }
            groups.forEach { (groupKey, list) ->
                val open = searching || groupKey in expanded
                val first = list.first()
                item(key = "h:$groupKey") {
                    GroupHeader(
                        icon = {
                            appIcon(first.info.provider.packageName)?.let {
                                Image(it, contentDescription = null, modifier = Modifier.size(36.dp))
                            } ?: LauncherGlyph(first.appLabel.take(1))
                        },
                        title = first.appLabel,
                        count = list.size,
                        open = open,
                        onClick = { toggle(groupKey) },
                    )
                }
                // "#" między nazwą a profilem: bez niego ".W1"+profil 0 i ".W"+profil 10 dawałyby ten sam klucz.
                if (open) items(list, key = { "p:" + it.info.provider.flattenToString() + "#" + it.info.profile.hashCode() }) { provider ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(provider) }
                            .heightIn(min = 72.dp)
                            .padding(12.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp, 64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            provider.preview?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.padding(4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(provider.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// Nagłówek grupy: ikona aplikacji, nazwa, liczba widżetów i strzałka rozwinięcia.
@Composable
private fun GroupHeader(icon: @Composable () -> Unit, title: String, count: Int, open: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.primary)
    }
}

// Ikona grupy "LaunchOnMe" w wyborze widżetów.
@Composable
private fun OwnAppIcon() {
    // Ikona LaunchOnMe z systemu (ta sama co w szufladzie) — ikona adaptacyjna rysowana do bitmapy raz.
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName).toBitmap(144, 144).asImageBitmap()
        }.getOrNull()
    }
    if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp)) else LauncherGlyph()
}

// Znaczek grupy bez ikony: litera na kolorowym kółku.
@Composable
private fun LauncherGlyph(letter: String = "L") {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Text(letter, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}
