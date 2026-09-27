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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.TapAction
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.contextlauncher.system.QuickToggle
import pl.rafal.contextlauncher.ui.ModeBadge

// Kroki wyboru akcji: najpierw rodzaj, potem szczegół (aplikacja, przełącznik, tryb, link).
private enum class Step { KIND, APP, TOGGLE, MODE, LINK }

// Okno "Po dotknięciu naklejki…". Kontakt wybiera systemowa lista kontaktów (onPickPhone),
// bo tylko ona daje dostęp do jednego numeru bez uprawnień do całej książki.
@Composable
fun TapActionDialog(
    current: TapAction?,
    apps: List<AppInfo>,
    modes: List<ModeEntity>,
    onPickPhone: (sms: Boolean) -> Unit,
    onDone: (TapAction?) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(Step.KIND) }
    var filter by remember { mutableStateOf("") }
    var link by remember { mutableStateOf((current as? TapAction.OpenLink)?.url.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                when (step) {
                    Step.KIND -> "Po dotknięciu naklejki"
                    Step.APP -> "Którą aplikację otworzyć?"
                    Step.TOGGLE -> "Co przełączać?"
                    Step.MODE -> "Który tryb włączyć?"
                    Step.LINK -> "Adres strony"
                },
            )
        },
        text = {
            when (step) {
                Step.KIND -> Column {
                    current?.let {
                        Text("Teraz: ${it.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.heightIn(min = 8.dp))
                    }
                    Option("✨  Nic — tylko ozdoba") { onDone(null) }
                    Option("📱  Otwórz aplikację…") { step = Step.APP }
                    Option("📞  Zadzwoń do…") { onPickPhone(false) }
                    Option("💬  SMS do…") { onPickPhone(true) }
                    Option("🔦  Przełącz funkcję…") { step = Step.TOGGLE }
                    Option("⭐  Włącz tryb…") { step = Step.MODE }
                    Option("🔗  Otwórz link…") { step = Step.LINK }
                }
                Step.APP -> Column {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = { Text("Szukaj") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val shown = apps.filter { filter.isBlank() || it.label.contains(filter.trim(), ignoreCase = true) }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it.key }) { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onDone(TapAction.OpenApp(app.component.packageName, app.component.className, app.userSerial, app.label))
                                    }
                                    .padding(vertical = 6.dp),
                            ) {
                                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(app.label)
                            }
                        }
                    }
                }
                Step.TOGGLE -> Column {
                    QuickToggle.entries.forEach { toggle -> Option(toggle.label) { onDone(TapAction.Toggle(toggle)) } }
                }
                Step.MODE -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(modes, key = { it.id }) { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDone(TapAction.SwitchMode(mode.id, mode.name)) }
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
                    placeholder = { Text("np. intercity.pl") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (step == Step.LINK) {
                TextButton(onClick = {
                    val url = link.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
                    onDone(TapAction.OpenLink(url))
                }, enabled = link.isNotBlank()) { Text("Zapisz") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step == Step.KIND) onDismiss() else step = Step.KIND }) {
                Text(if (step == Step.KIND) "Anuluj" else "Wstecz")
            }
        },
    )
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
