package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.AppInfo

// Szuflada z dwiema zakładkami: wszystkie aplikacje (z wyszukiwarką) i foldery.
// Ekran nie zna ViewModelu: dostaje dane i funkcje zwrotne, więc łatwo go testować i podglądać.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    query: String,
    onQueryChange: (String) -> Unit,              // (String) -> Unit ≈ Action<string>
    onAppClick: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,       // ≈ Func<AppInfo, List<MenuAction>>
    foldersContent: @Composable () -> Unit,       // zakładka "Foldery" (slot)
    frequent: List<AppInfo>,                      // najczęściej uruchamiane w aktywnym trybie
    frequentLabel: String,
    autoFocusSearch: Boolean,                     // true = od razu kursor i klawiatura w wyszukiwarce
    hiddenApps: List<AppInfo> = emptyList(),      // ukryte w aktywnym trybie (na końcu listy, po rozwinięciu)
    dragOut: ExternalDrag? = null,                // przeciąganie ikon na kartę
    modifier: Modifier = Modifier,
) {
    var showFolders by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding() // lista kończy się nad klawiaturą
            .padding(horizontal = 16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            FilterChip(selected = !showFolders, onClick = { showFolders = false }, label = { Text("Wszystkie") })
            FilterChip(selected = showFolders, onClick = { showFolders = true }, label = { Text("Foldery") })
        }

        if (showFolders) {
            foldersContent()
        } else {
            AllAppsTab(apps, query, onQueryChange, onAppClick, menuFor, frequent, frequentLabel, autoFocusSearch, hiddenApps, dragOut)
        }
    }
}

@Composable
private fun AllAppsTab(
    apps: List<AppInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,
    frequent: List<AppInfo>,
    frequentLabel: String,
    autoFocusSearch: Boolean,
    hiddenApps: List<AppInfo>,
    dragOut: ExternalDrag?,
) {
    val focusRequester = remember { FocusRequester() }
    var showHidden by rememberSaveable { mutableStateOf(false) }

    // Wykonuje się raz, gdy zakładka się pojawia: kursor w wyszukiwarce tylko po "Szukaj aplikacji".
    LaunchedEffect(Unit) { if (autoFocusSearch) focusRequester.requestFocus() }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Szukaj aplikacji") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            // Enter na klawiaturze uruchamia pierwszą znalezioną aplikację.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { apps.firstOrNull()?.let(onAppClick) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .focusRequester(focusRequester),
        )

        // Lazy = rysuje tylko to, co widać (jak wirtualizacja w ListView/ItemsControl).
        LazyVerticalGrid(
            columns = GridCells.Fixed(LauncherViewModel.COLUMNS),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Bez wpisanego tekstu: najpierw rząd "Często w trybie…", potem wszystkie.
            if (query.isBlank() && frequent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(frequentLabel) }
                items(frequent, key = { "fav:" + it.key }) { app ->
                    AppTile(app = app, onClick = { onAppClick(app) }, menu = menuFor(app), dragOut = dragOut)
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Wszystkie aplikacje") }
            }
            items(apps, key = { it.key }) { app ->
                AppTile(app = app, onClick = { onAppClick(app) }, menu = menuFor(app), dragOut = dragOut)
            }
            // Ukryte: tylko na liście (nie w wyszukiwaniu), zwinięte za jednym dotknięciem.
            if (query.isBlank() && hiddenApps.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = { showHidden = !showHidden }) {
                        Text(if (showHidden) "Schowaj ukryte" else "Pokaż ukryte w tym trybie (${hiddenApps.size})")
                    }
                }
                if (showHidden) {
                    items(hiddenApps, key = { "hidden:" + it.key }) { app ->
                        AppTile(app = app, onClick = { onAppClick(app) }, menu = menuFor(app), dragOut = dragOut)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}
