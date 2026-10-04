package pl.rafal.contextlauncher.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.TapAction
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.contextlauncher.system.QuickToggle
import pl.rafal.contextlauncher.ui.ModeBadge
import pl.rafal.contextlauncher.ui.AppGridPickerDialog
import pl.rafal.contextlauncher.ui.ShortcutPickerDialog
import pl.rafal.contextlauncher.data.SystemAction

// Kroki okna: lista akcji, wybór rodzaju nowej akcji, szczegół (przełącznik, tryb, link, akcja systemowa).
// Aplikacja i skrót wybierają się w osobnych oknach (siatka ikon / lista skrótów pogrupowana po aplikacji).
private enum class Step { LIST, KIND, TOGGLE, MODE, LINK, SYSTEM }

// Okno "Po dotknięciu naklejki…": można ustawić KILKA akcji wykonywanych po kolei
// (np. włącz latarkę → otwórz aparat). Kontakt wybiera systemowa lista kontaktów (onPickPhone),
// bo tylko ona daje dostęp do jednego numeru bez uprawnień do całej książki.
@Composable
fun TapActionDialog(
    actions: List<TapAction>,
    apps: List<AppInfo>,
    shortcuts: List<AppInfo>,
    hasShortcutAccess: Boolean,
    modes: List<ModeEntity>,
    onPickPhone: (sms: Boolean) -> Unit,
    onChange: (List<TapAction>) -> Unit,  // zmiana wersji roboczej (zapis dopiero "Zapisz")
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(Step.LIST) }
    var link by remember { mutableStateOf("") }
    var appPickOpen by remember { mutableStateOf(false) }
    var shortcutPickOpen by remember { mutableStateOf(false) }

    fun add(action: TapAction) {
        onChange(actions + action)
        step = Step.LIST
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                when (step) {
                    Step.LIST -> stringResource(R.string.tap_title_list)
                    Step.KIND -> stringResource(R.string.tap_title_kind)
                    Step.TOGGLE -> stringResource(R.string.tap_title_toggle)
                    Step.MODE -> stringResource(R.string.tap_title_mode)
                    Step.LINK -> stringResource(R.string.tap_title_link)
                    Step.SYSTEM -> stringResource(R.string.tap_title_system)
                },
            )
        },
        text = {
            when (step) {
                Step.LIST -> Column {
                    if (actions.isEmpty()) {
                        Text(stringResource(R.string.tap_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    actions.forEachIndexed { index, action ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${index + 1}.  ${action.label}", modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                            TextButton(onClick = { onChange(actions.filterIndexed { i, _ -> i != index }) }) { Text("✕") }
                        }
                    }
                    Spacer(Modifier.heightIn(min = 8.dp))
                    TextButton(onClick = { step = Step.KIND }) { Text(stringResource(R.string.tap_add_action)) }
                    if (actions.size > 1) {
                        Text(
                            stringResource(R.string.tap_order_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Step.KIND -> Column {
                    Option(stringResource(R.string.tap_kind_app)) { appPickOpen = true }
                    Option(stringResource(R.string.tap_kind_shortcut)) { shortcutPickOpen = true }
                    Option(stringResource(R.string.tap_kind_dial)) {
                        step = Step.LIST
                        onPickPhone(false)
                    }
                    Option(stringResource(R.string.tap_kind_sms)) {
                        step = Step.LIST
                        onPickPhone(true)
                    }
                    Option(stringResource(R.string.tap_kind_toggle)) { step = Step.TOGGLE }
                    Option(stringResource(R.string.tap_kind_system)) { step = Step.SYSTEM }
                    Option(stringResource(R.string.tap_kind_mode)) { step = Step.MODE }
                    Option(stringResource(R.string.tap_kind_link)) { step = Step.LINK }
                }
                Step.TOGGLE -> Column {
                    QuickToggle.entries.forEach { toggle -> Option(toggle.label) { add(TapAction.Toggle(toggle)) } }
                }
                Step.SYSTEM -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(SystemAction.entries) { action -> Option(action.label) { add(TapAction.System(action)) } }
                }
                Step.MODE -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(modes, key = { it.id }) { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { add(TapAction.SwitchMode(mode.id, mode.name)) }
                                .padding(vertical = 8.dp),
                        ) {
                            ModeBadge(mode, size = 28.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(mode.name)
                        }
                    }
                }
                Step.LINK -> OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text(stringResource(R.string.tap_link_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            when (step) {
                Step.LIST -> TextButton(onClick = onSave) { Text(stringResource(R.string.common_save)) }
                Step.LINK -> TextButton(onClick = {
                    val url = link.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
                    link = ""
                    add(TapAction.OpenLink(url))
                }, enabled = link.isNotBlank()) { Text(stringResource(R.string.common_add)) }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step == Step.LIST) onDismiss() else step = if (step == Step.KIND) Step.LIST else Step.KIND }) {
                Text(if (step == Step.LIST) stringResource(R.string.common_cancel) else stringResource(R.string.common_back))
            }
        },
    )

    // Aplikacje w siatce (jak w szufladzie) — przy setkach aplikacji to dużo szybsze niż lista.
    if (appPickOpen) {
        AppGridPickerDialog(
            allApps = apps,
            title = stringResource(R.string.tap_pick_app_title),
            single = true,
            onConfirm = { chosen ->
                chosen.firstOrNull()?.let { app ->
                    add(TapAction.OpenApp(app.component.packageName, app.component.className, app.userSerial, app.label))
                }
                appPickOpen = false
            },
            onDismiss = { appPickOpen = false },
        )
    }

    // Skróty zostają listą: ich nazwy ("Nowa wiadomość", "Skanuj") są ważniejsze niż ikony, a grupa = aplikacja.
    if (shortcutPickOpen) {
        ShortcutPickerDialog(
            shortcuts = shortcuts,
            apps = apps,
            hasAccess = hasShortcutAccess,
            title = stringResource(R.string.tap_pick_shortcut_title),
            onPick = { sc ->
                sc.shortcutId?.let { add(TapAction.OpenShortcut(sc.packageName, it, sc.userSerial, sc.label)) }
                shortcutPickOpen = false
            },
            onDismiss = { shortcutPickOpen = false },
        )
    }
}

@Composable
private fun Option(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    )
}
