package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.CustomWidgetKind
import pl.rafal.contextlauncher.data.widgets.WidgetProvider

// Lista wszystkich widżetów zainstalowanych aplikacji.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    providers: List<WidgetProvider>,
    onPick: (WidgetProvider) -> Unit,
    onPickCustom: (CustomWidgetKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }

    // Filtrujemy po nazwie widżetu i nazwie aplikacji ("zeg" znajdzie wszystkie widżety Zegara).
    val shown = providers.filter {
        filter.isBlank() ||
            it.label.contains(filter.trim(), ignoreCase = true) ||
            it.appLabel.contains(filter.trim(), ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            "Dodaj widżet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Szukaj widżetu lub aplikacji") },
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
        // Jedna przewijana lista: najpierw widżety launchera, potem widżety aplikacji.
        // (Wcześniej widżety launchera stały w nieprzewijanej kolumnie i przy 14 rodzajach wypychały resztę poza ekran.)
        val kinds = CustomWidgetKind.entries.filter {
            filter.isBlank() || it.title.contains(filter.trim(), ignoreCase = true) || it.description.contains(filter.trim(), ignoreCase = true)
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (kinds.isNotEmpty()) {
                item(key = "h:launcher") { SectionHeader("Widżety launchera") }
                items(kinds, key = { "k:" + it.name }) { kind ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
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
            item(key = "h:apps") { SectionHeader("Widżety aplikacji") }
            if (providers.isEmpty()) {
                item(key = "loading") {
                    Text("Wczytywanie…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                }
            }
            // "#" między nazwą a profilem: bez niego ".W1"+profil 0 i ".W"+profil 10 dawałyby ten sam klucz.
            items(shown, key = { "p:" + it.info.provider.flattenToString() + "#" + it.info.profile.hashCode() }) { provider ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
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
                    Column {
                        Text(provider.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            provider.appLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}
