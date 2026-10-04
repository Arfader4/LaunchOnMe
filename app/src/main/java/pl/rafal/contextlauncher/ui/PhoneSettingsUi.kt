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
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R
import androidx.compose.ui.res.pluralStringResource

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
        Text(stringResource(R.string.phone_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.phone_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Brakujące zgody pokazujemy tylko wtedy, gdy któreś ustawienie ich potrzebuje.
        val needsDnd = settings.dnd != null || settings.ringer == RingerSetting.SILENT
        val needsWrite = settings.brightness != null || settings.autoRotate != null || settings.screenTimeoutSec != null
        if (needsDnd && !hasDndAccess) {
            AccessHint(stringResource(R.string.phone_dnd_access_hint), onGrantDnd)
        }
        if (needsWrite && !canWriteSettings) {
            AccessHint(stringResource(R.string.phone_write_access_hint), onGrantWrite)
        }

        SectionLabel2(stringResource(R.string.phone_section_auto))
        TriState(stringResource(R.string.phone_dnd), settings.dnd) { onChange(settings.copy(dnd = it)) }
        ChoiceRow(
            label = stringResource(R.string.phone_ringer),
            options = listOf(null) + RingerSetting.entries,
            selected = settings.ringer,
            labelOf = { it?.label ?: "—" },
            onSelect = { onChange(settings.copy(ringer = it)) },
        )
        PercentSetting(stringResource(R.string.phone_media), settings.mediaVolume) { onChange(settings.copy(mediaVolume = it)) }
        PercentSetting(stringResource(R.string.phone_ring), settings.ringVolume) { onChange(settings.copy(ringVolume = it)) }
        PercentSetting(stringResource(R.string.phone_alarm), settings.alarmVolume) { onChange(settings.copy(alarmVolume = it)) }
        PercentSetting(stringResource(R.string.phone_brightness), settings.brightness) { onChange(settings.copy(brightness = it)) }
        TriState(stringResource(R.string.phone_autorotate), settings.autoRotate) { onChange(settings.copy(autoRotate = it)) }
        ChoiceRow(
            label = stringResource(R.string.phone_screen_timeout),
            options = listOf(null, 30, 60, 120, 300, 600),
            selected = settings.screenTimeoutSec,
            labelOf = { sec -> when { sec == null -> "—"; sec < 60 -> "$sec s"; else -> "${sec / 60} min" } },
            onSelect = { onChange(settings.copy(screenTimeoutSec = it)) },
        )

        SectionLabel2(stringResource(R.string.phone_section_reminder))
        TriState("Wi-Fi", settings.wifi) { onChange(settings.copy(wifi = it)) }
        TriState("Bluetooth", settings.bluetooth) { onChange(settings.copy(bluetooth = it)) }
        TriState(stringResource(R.string.phone_mobile_data), settings.mobileData) { onChange(settings.copy(mobileData = it)) }
        TriState("NFC", settings.nfc) { onChange(settings.copy(nfc = it)) }
        TriState(stringResource(R.string.phone_battery_saver), settings.batterySaver) { onChange(settings.copy(batterySaver = it)) }
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
        TextButton(onClick = onGrant) { Text(stringResource(R.string.phone_grant)) }
    }
}

// Trzy stany: nie zmieniaj / włącz / wyłącz.
@Composable
private fun TriState(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    val onLabel = stringResource(R.string.phone_on)
    val offLabel = stringResource(R.string.phone_off)
    ChoiceRow(
        label = label,
        options = listOf(null, true, false),
        selected = value,
        labelOf = { when (it) { null -> "—"; true -> onLabel; false -> offLabel } },
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
    val hideCd = stringResource(R.string.phone_hide_reminder_cd)
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
            Text(stringResource(R.string.phone_manual_title, modeName), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
                .semantics { contentDescription = hideCd },
        ) { Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium) }
    }
}

// --- Okna nowych reguł ---

@Composable
fun BluetoothRuleDialog(devices: List<PairedDevice>, onPick: (PairedDevice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.phone_bt_title)) },
        text = {
            if (devices.isEmpty()) {
                Text(stringResource(R.string.phone_bt_empty))
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun WifiRuleDialog(currentSsid: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var ssid by remember { mutableStateOf(currentSsid.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.phone_wifi_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (currentSsid != null) stringResource(R.string.phone_wifi_current) else stringResource(R.string.phone_wifi_enter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = ssid, onValueChange = { ssid = it }, label = { Text(stringResource(R.string.phone_wifi_name)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = ssid.isNotBlank(), onClick = { onConfirm(ssid) }) { Text(stringResource(R.string.common_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
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
        title = { Text(stringResource(R.string.phone_place_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.phone_place_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.phone_place_name)) }, singleLine = true)
                Text(stringResource(R.string.phone_radius), style = MaterialTheme.typography.labelLarge)
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
        confirmButton = { TextButton(enabled = label.isNotBlank(), onClick = { onConfirm(label, radius) }) { Text(stringResource(R.string.phone_save_here)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// --- Reguły: ładowanie i bateria ---

@Composable
fun ChargingRuleDialog(onConfirm: (charging: Boolean) -> Unit, onDismiss: () -> Unit) {
    var charging by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.phone_charging_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.phone_charging_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = charging, onClick = { charging = true }, label = { Text(stringResource(R.string.phone_while_charging)) })
                    FilterChip(selected = !charging, onClick = { charging = false }, label = { Text(stringResource(R.string.phone_not_charging)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(charging) }) { Text(stringResource(R.string.common_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun BatteryRuleDialog(onConfirm: (belowPercent: Int) -> Unit, onDismiss: () -> Unit) {
    var percent by remember { mutableFloatStateOf(20f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.phone_battery_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.phone_battery_below, percent.roundToInt()), style = MaterialTheme.typography.titleMedium)
                // steps = liczba "ząbków" między końcami: 5, 10, …, 50.
                Slider(value = percent, onValueChange = { percent = it }, valueRange = 5f..50f, steps = 8)
                Text(
                    stringResource(R.string.phone_battery_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(percent.roundToInt()) }) { Text(stringResource(R.string.common_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// --- Tryb na czas ---

@Composable
fun TimedModeDialog(modeName: String, onPick: (durationMs: Long) -> Unit, onDismiss: () -> Unit) {
    val now = java.time.LocalDateTime.now()
    val untilMidnight = java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
    val options = listOf(
        pluralStringResource(R.plurals.phone_minutes, 30, 30) to 30 * 60_000L,
        pluralStringResource(R.plurals.phone_hours, 1, 1) to 60 * 60_000L,
        pluralStringResource(R.plurals.phone_hours, 2, 2) to 2 * 60 * 60_000L,
        pluralStringResource(R.plurals.phone_hours, 3, 3) to 3 * 60 * 60_000L,
        stringResource(R.string.phone_until_end_of_day) to untilMidnight,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.phone_timed_title, modeName)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.phone_timed_text),
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
