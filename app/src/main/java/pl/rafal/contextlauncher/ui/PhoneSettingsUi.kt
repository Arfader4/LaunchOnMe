package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.ModePhoneSettings
import pl.rafal.contextlauncher.data.RingerSetting
import pl.rafal.contextlauncher.system.ManualTask
import pl.rafal.contextlauncher.system.PairedDevice
import kotlin.math.roundToInt

// --- Sekcja "Ustawienia telefonu" w ustawieniach trybu ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSettingsSection(
    settings: ModePhoneSettings,
    hasDndAccess: Boolean,
    canWriteSettings: Boolean,
    onChange: (ModePhoneSettings) -> Unit,
    onGrantDnd: () -> Unit,
    onGrantWrite: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ustawienia telefonu w tym trybie", style = MaterialTheme.typography.titleSmall)
        Text(
            "Zmieniane przy włączeniu trybu. „—” = nie zmieniaj.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Brakujące zgody pokazujemy tylko wtedy, gdy któreś ustawienie ich potrzebuje.
        val needsDnd = settings.dnd != null || settings.ringer == RingerSetting.SILENT
        val needsWrite = settings.brightness != null || settings.autoRotate != null || settings.screenTimeoutSec != null
        if (needsDnd && !hasDndAccess) {
            AccessHint("Nie przeszkadzać i cisza wymagają zgody „Dostęp do trybu Nie przeszkadzać”.", onGrantDnd)
        }
        if (needsWrite && !canWriteSettings) {
            AccessHint("Jasność, wygaszanie i obracanie wymagają zgody „Modyfikowanie ustawień systemu”.", onGrantWrite)
        }

        SectionLabel2("Automatycznie")
        TriState("Nie przeszkadzać", settings.dnd) { onChange(settings.copy(dnd = it)) }
        ChoiceRow(
            label = "Tryb dźwięku",
            options = listOf(null) + RingerSetting.entries,
            selected = settings.ringer,
            labelOf = { it?.label ?: "—" },
            onSelect = { onChange(settings.copy(ringer = it)) },
        )
        PercentSetting("Multimedia", settings.mediaVolume) { onChange(settings.copy(mediaVolume = it)) }
        PercentSetting("Dzwonek", settings.ringVolume) { onChange(settings.copy(ringVolume = it)) }
        PercentSetting("Alarm", settings.alarmVolume) { onChange(settings.copy(alarmVolume = it)) }
        PercentSetting("Jasność ekranu", settings.brightness) { onChange(settings.copy(brightness = it)) }
        TriState("Autoobracanie", settings.autoRotate) { onChange(settings.copy(autoRotate = it)) }
        ChoiceRow(
            label = "Wygaszanie ekranu",
            options = listOf(null, 30, 60, 120, 300, 600),
            selected = settings.screenTimeoutSec,
            labelOf = { sec -> when { sec == null -> "—"; sec < 60 -> "$sec s"; else -> "${sec / 60} min" } },
            onSelect = { onChange(settings.copy(screenTimeoutSec = it)) },
        )

        SectionLabel2("Przypomnienie z przyciskiem (Android nie pozwala przełączać ich aplikacjom)")
        TriState("Wi-Fi", settings.wifi) { onChange(settings.copy(wifi = it)) }
        TriState("Bluetooth", settings.bluetooth) { onChange(settings.copy(bluetooth = it)) }
        TriState("Dane komórkowe", settings.mobileData) { onChange(settings.copy(mobileData = it)) }
        TriState("NFC", settings.nfc) { onChange(settings.copy(nfc = it)) }
        TriState("Oszczędzanie energii", settings.batterySaver) { onChange(settings.copy(batterySaver = it)) }
    }
}

@Composable
private fun SectionLabel2(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun AccessHint(text: String, onGrant: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onGrant) { Text("Nadaj") }
    }
}

// Trzy stany: nie zmieniaj / włącz / wyłącz.
@Composable
private fun TriState(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    ChoiceRow(
        label = label,
        options = listOf(null, true, false),
        selected = value,
        labelOf = { when (it) { null -> "—"; true -> "Wł."; false -> "Wył." } },
        onSelect = onChange,
    )
}

// Generyczny wiersz wyboru: etykieta + chipy z opcjami (T jak w C#: dowolny typ opcji).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(labelOf(option)) })
            }
        }
    }
}

// Procent (głośność, jasność): przełącznik "zmieniaj" + suwak. Zapis dopiero po puszczeniu suwaka.
@Composable
private fun PercentSetting(label: String, value: Int?, onChange: (Int?) -> Unit) {
    var local by remember(value) { mutableFloatStateOf((value ?: 50).toFloat()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (value != null) {
                Text("${local.roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            Switch(checked = value != null, onCheckedChange = { on -> onChange(if (on) local.roundToInt() else null) })
        }
        if (value != null) {
            Slider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { onChange(local.roundToInt()) },
                valueRange = 0f..100f,
            )
        }
    }
}

// --- Przypomnienie na karcie: co przełączyć ręcznie ---

@Composable
fun ManualTasksCard(
    modeName: String,
    tasks: List<ManualTask>,
    onTask: (ManualTask) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tryb $modeName prosi o:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                tasks.forEach { task -> AssistChip(onClick = { onTask(task) }, label = { Text(task.label) }) }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "Ukryj przypomnienie" },
        ) { Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium) }
    }
}

// --- Okna nowych reguł ---

@Composable
fun BluetoothRuleDialog(devices: List<PairedDevice>, onPick: (PairedDevice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Urządzenie Bluetooth") },
        text = {
            if (devices.isEmpty()) {
                Text("Brak sparowanych urządzeń albo brak zgody na Bluetooth. Sparuj urządzenie w ustawieniach telefonu.")
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(devices, key = { it.address }) { device ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(device) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Text(device.name, style = MaterialTheme.typography.bodyLarge)
                            Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
fun WifiRuleDialog(currentSsid: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var ssid by remember { mutableStateOf(currentSsid.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Sieć Wi-Fi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (currentSsid != null) "Wpisaliśmy sieć, z którą telefon jest teraz połączony." else "Wpisz nazwę sieci (SSID).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = ssid, onValueChange = { ssid = it }, label = { Text("Nazwa sieci") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = ssid.isNotBlank(), onClick = { onConfirm(ssid) }) { Text("Dodaj") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceRuleDialog(onConfirm: (label: String, radiusMeters: Int) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var radius by remember { mutableIntStateOf(300) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Miejsce") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Zapiszemy miejsce, w którym jesteś teraz. Tryb będzie podpowiadany, gdy wrócisz w jego pobliże.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Nazwa, np. Biuro") }, singleLine = true)
                Text("Promień", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100, 300, 1000).forEach { r ->
                        FilterChip(
                            selected = r == radius,
                            onClick = { radius = r },
                            label = { Text(if (r < 1000) "$r m" else "${r / 1000} km") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = label.isNotBlank(), onClick = { onConfirm(label, radius) }) { Text("Zapisz tutaj") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// --- Reguły: ładowanie i bateria ---

@Composable
fun ChargingRuleDialog(onConfirm: (charging: Boolean) -> Unit, onDismiss: () -> Unit) {
    var charging by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Ładowanie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Np. „Noc” podczas ładowania przy łóżku albo „Auto” po podłączeniu ładowarki samochodowej.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = charging, onClick = { charging = true }, label = { Text("Podczas ładowania") })
                    FilterChip(selected = !charging, onClick = { charging = false }, label = { Text("Bez ładowarki") })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(charging) }) { Text("Dodaj") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
fun BatteryRuleDialog(onConfirm: (belowPercent: Int) -> Unit, onDismiss: () -> Unit) {
    var percent by remember { mutableFloatStateOf(20f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Niska bateria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Poniżej ${percent.roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                // steps = liczba "ząbków" między końcami: 5, 10, …, 50.
                Slider(value = percent, onValueChange = { percent = it }, valueRange = 5f..50f, steps = 8)
                Text(
                    "Ta reguła wygrywa z innymi — dobry moment na tryb z oszczędzaniem baterii w ustawieniach telefonu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(percent.roundToInt()) }) { Text("Dodaj") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// --- Tryb na czas ---

@Composable
fun TimedModeDialog(modeName: String, onPick: (durationMs: Long) -> Unit, onDismiss: () -> Unit) {
    val now = java.time.LocalDateTime.now()
    val untilMidnight = java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
    val options = listOf(
        "30 minut" to 30 * 60_000L,
        "1 godzina" to 60 * 60_000L,
        "2 godziny" to 2 * 60 * 60_000L,
        "3 godziny" to 3 * 60 * 60_000L,
        "Do końca dnia" to untilMidnight,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("$modeName na czas") },
        text = {
            Column {
                Text(
                    "Po tym czasie launcher sam wróci do obecnego trybu. Automat w tym czasie nie przełącza.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                options.forEach { (label, ms) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(ms) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
