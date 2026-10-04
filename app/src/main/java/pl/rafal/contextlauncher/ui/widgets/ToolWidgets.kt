package pl.rafal.contextlauncher.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pl.rafal.contextlauncher.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.rafal.contextlauncher.data.CheckItem
import pl.rafal.contextlauncher.data.Checklist
import pl.rafal.contextlauncher.data.Countdown
import pl.rafal.contextlauncher.data.FavoriteContact
import pl.rafal.contextlauncher.data.GlanceEvent
import pl.rafal.contextlauncher.data.day
import pl.rafal.contextlauncher.system.QuickStates
import pl.rafal.contextlauncher.system.QuickToggle
import pl.rafal.contextlauncher.system.RingerState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
// Język systemu (getter: po zmianie języka telefonu daty od razu w nowym języku).
private val DayHeader: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())
private val FullDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())

// ============================== Dziś ==============================

class TodayCallbacks(
    val onOpenDay: () -> Unit,
    val onOpenEvent: (GlanceEvent) -> Unit,
    val onNewEvent: () -> Unit,
    val onGrant: () -> Unit,
)

// Plan na dziś i jutro. Nagłówek → kalendarz, wydarzenie → to wydarzenie, "+" → nowe wydarzenie.
@Composable
fun TodayWidget(agenda: List<GlanceEvent>, hasCalendar: Boolean, callbacks: TodayCallbacks?) {
    val now = rememberCurrentMinute()
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()

  AdaptiveWidget { size, _, _ ->
    // Mały: ikona kalendarza z liczbą dzisiejszych wydarzeń; 2×2 dodaje godzinę najbliższego.
    if (size == WidgetSize.TINY || size == WidgetSize.SMALL) {
        val todays = agenda.filter { it.day(zone) == today }
        val next = agenda.firstOrNull { !it.allDay && it.end > now.toEpochMilli() }
        CompactTile(
            icon = R.drawable.ic_w_calendar,
            onClick = callbacks?.let { cb -> if (!hasCalendar) cb.onGrant else if (next != null) ({ cb.onOpenEvent(next) }) else cb.onOpenDay },
            label = if (size == WidgetSize.SMALL) next?.let { Instant.ofEpochMilli(it.begin).atZone(zone).format(Hm) + " " + it.title } ?: today.format(DayHeader) else null,
            badge = todays.size.takeIf { it > 0 }?.toString(),
        )
        return@AdaptiveWidget
    }
    WidgetSurface(onClick = null) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        // Niski pasek: tylko najbliższe wydarzenie (albo krótka podpowiedź).
        if (maxHeight < 64.dp) {
            val next = agenda.firstOrNull { !it.allDay && it.end > now.toEpochMilli() } ?: agenda.firstOrNull()
            when {
                !hasCalendar -> HintText(stringResource(R.string.cal_grant_short), callbacks?.onGrant)
                next == null -> HintText(stringResource(R.string.cal_empty_short), callbacks?.onOpenDay)
                else -> AgendaRow(next, now, onClick = callbacks?.let { cb -> { cb.onOpenEvent(next) } })
            }
            return@BoxWithConstraints
        }
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                today.format(DayHeader).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(if (callbacks != null) Modifier.clickable(onClick = callbacks.onOpenDay) else Modifier),
            )
            if (callbacks != null && hasCalendar) SmallRoundButton("+", stringResource(R.string.cal_new_event), callbacks.onNewEvent)
        }
        Spacer(Modifier.height(6.dp))
        when {
            !hasCalendar -> HintText(stringResource(R.string.cal_grant), callbacks?.onGrant)
            agenda.isEmpty() -> HintText(stringResource(R.string.cal_empty), callbacks?.onOpenDay)
            // W edycji układu lista nie przewija się — inaczej "zjadałaby" gest przesuwania widżetu.
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), userScrollEnabled = callbacks != null) {
                var lastDay: LocalDate? = null
                agenda.forEach { event ->
                    val day = event.day(zone)
                    if (day != lastDay && day != today) {
                        item(key = "h$day") {
                            Text(
                                if (day == today.plusDays(1)) stringResource(R.string.cal_tomorrow) else day.format(DayHeader).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    lastDay = day
                    item(key = "e${event.id}-${event.begin}") {
                        AgendaRow(event, now, onClick = callbacks?.let { cb -> { cb.onOpenEvent(event) } })
                    }
                }
            }
        }
      }
      }
    }
  }
}

@Composable
private fun AgendaRow(event: GlanceEvent, now: Instant, onClick: (() -> Unit)?) {
    val zone = ZoneId.systemDefault()
    val ongoing = !event.allDay && event.begin <= now.toEpochMilli()
    val untitled = stringResource(R.string.w_event_untitled)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 3.dp),
    ) {
        // Kolorowa kreska jak w aplikacji Kalendarz; trwające wydarzenie w kolorze trybu.
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (ongoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(event.title.ifBlank { untitled }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    event.allDay -> stringResource(R.string.cal_all_day)
                    ongoing -> stringResource(R.string.cal_now_until, Instant.ofEpochMilli(event.end).atZone(zone).format(Hm))
                    else -> Instant.ofEpochMilli(event.begin).atZone(zone).format(Hm) + "–" + Instant.ofEpochMilli(event.end).atZone(zone).format(Hm)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============================== Odliczanie ==============================

@Composable
fun CountdownWidget(countdown: Countdown, onClick: (() -> Unit)?) {
    val now = rememberCurrentMinute()
    val countdownLabel = stringResource(R.string.w_countdown_title)
    val todayLabel = stringResource(R.string.w_countdown_today)
    WidgetSurface(onClick = onClick) {
        val date = countdown.date
        if (date == null) {
            Text(countdownLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            HintText(if (countdown.title.isNotBlank()) stringResource(R.string.w_countdown_set_date_titled, countdown.title) else stringResource(R.string.w_countdown_set_date), null)
            return@WidgetSurface
        }
        // ChronoUnit.DAYS.between ≈ (date - today).TotalDays w C#, ale na pełnych dniach kalendarzowych.
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, date)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 1×1: sama liczba dni.
            if (widgetSizeOf(maxWidth + 16.dp, maxHeight + 8.dp) == WidgetSize.TINY) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FitText(
                        if (days == 0L) "!" else "${kotlin.math.abs(days)}",
                        Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                return@BoxWithConstraints
            }
            // 2×2 i niski pasek: liczba dni obok tytułu.
            if (maxHeight < 90.dp || maxWidth < 110.dp) {
                val size = (maxHeight.value * 0.55f).coerceIn(16f, 40f).sp
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                    FitText(if (days == 0L) todayLabel else "${kotlin.math.abs(days)}", Modifier.weight(0.45f), maxSize = size, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(0.55f)) {
                        FitText(countdown.title.ifBlank { countdownLabel }, Modifier.fillMaxWidth(), maxSize = 13.sp)
                        if (days != 0L) {
                            FitText(
                                daysWord(kotlin.math.abs(days), ago = days < 0),
                                Modifier.fillMaxWidth(),
                                maxSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                return@BoxWithConstraints
            }
            val big = (maxHeight.value / 2.4f).coerceIn(28f, 64f).sp
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                Text(
                    countdown.title.ifBlank { countdownLabel },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        when {
                            days == 0L -> todayLabel
                            days > 0 -> "$days"
                            else -> "${-days}"
                        },
                        fontSize = big,
                        lineHeight = big,
                        fontWeight = FontWeight.SemiBold,
                        color = if (days == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (days != 0L) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            daysWord(kotlin.math.abs(days), ago = days < 0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Text(date.format(FullDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Odmiana z zasobów <plurals> (po polsku: 1 dzień, 2–4 dni, 5 dni…); ago = "… temu".
@Composable
private fun daysWord(n: Long, ago: Boolean): String =
    pluralStringResource(
        if (ago) R.plurals.w_countdown_days_ago else R.plurals.w_countdown_days,
        n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownDialog(initial: Countdown, onSave: (Countdown) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    // DatePicker liczy w milisekundach UTC od północy — stąd konwersje przez ZoneOffset.UTC.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = (initial.date ?: LocalDate.now().plusDays(7)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val date = pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                onSave(Countdown(title.trim(), date))
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.w_countdown_dialog_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        DatePicker(state = pickerState, title = null, headline = null, showModeToggle = false)
    }
}

// ============================== Lista ==============================

class ChecklistCallbacks(
    val onToggle: (index: Int) -> Unit,
    val onAdd: () -> Unit,
    val onRename: () -> Unit,
    val onClearDone: () -> Unit,
)

// Lista do odhaczania. Dotknięcie pozycji = zrobione / niezrobione; zrobione spadają na koniec.
@Composable
fun ChecklistWidget(list: Checklist, callbacks: ChecklistCallbacks?) {
  AdaptiveWidget { size, _, _ ->
    // Mały: ikona listy z liczbą pozycji do zrobienia; 2×2 dodaje tytuł.
    if (size == WidgetSize.TINY || size == WidgetSize.SMALL) {
        val done = list.items.count { it.done }
        CompactTile(
            icon = R.drawable.ic_w_check,
            onClick = callbacks?.onAdd,
            label = if (size == WidgetSize.SMALL) list.title else null,
            badge = if (list.items.isEmpty()) null else "${list.items.size - done}",
        )
        return@AdaptiveWidget
    }
    WidgetSurface(onClick = null) {
        val doneCount = list.items.count { it.done }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                list.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(if (callbacks != null) Modifier.clickable(onClick = callbacks.onRename) else Modifier),
            )
            if (list.items.isNotEmpty()) {
                Text("$doneCount/${list.items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
            }
            if (callbacks != null) {
                if (doneCount > 0) SmallRoundButton("🧹", stringResource(R.string.w_checklist_clear_done), callbacks.onClearDone)
                SmallRoundButton("+", stringResource(R.string.w_checklist_add_item), callbacks.onAdd)
            }
        }
        if (list.items.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            HintText(stringResource(R.string.w_checklist_empty), callbacks?.onAdd)
            return@WidgetSurface
        }
        // Indeksy z oryginalnej listy, żeby po posortowaniu odhaczyć właściwą pozycję.
        val ordered = list.items.withIndex().sortedBy { it.value.done }
        LazyColumn(userScrollEnabled = callbacks != null) {
            items(ordered, key = { it.index }) { (index, item) ->
                CheckRow(item, onClick = callbacks?.let { cb -> { cb.onToggle(index) } })
            }
        }
    }
  }
}

@Composable
private fun CheckRow(item: CheckItem, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 32.dp),
    ) {
        Checkbox(
            checked = item.done,
            onCheckedChange = onClick?.let { { _: Boolean -> it() } },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(32.dp),
        )
        Text(
            item.text,
            style = MaterialTheme.typography.bodySmall,
            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Jedno pole na kilka pozycji naraz: każda linijka to osobna pozycja (wygodne przy zakupach).
@Composable
fun ChecklistAddDialog(onAdd: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.w_checklist_add_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.w_checklist_add_placeholder)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text.lines().map { it.trim().removePrefix("•").trim() }.filter { it.isNotEmpty() }) }) { Text(stringResource(R.string.common_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// ============================== Szybkie przełączniki ==============================

@Composable
fun QuickTogglesWidget(states: QuickStates, onToggle: ((QuickToggle) -> Unit)?) {
    WidgetSurface(onClick = null) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Skalowanie: tyle przycisków, ile zmieści szerokość (w kolejności ważności), a podpisy
            // tylko wtedy, gdy jest na nie wysokość. Przycisk rośnie razem z widżetem.
            val w = maxWidth
            val h = maxHeight
            val showLabels = h >= 72.dp
            val button = (if (showLabels) h - 22.dp else h).coerceIn(28.dp, 52.dp)
            val fit = (w / (button + 10.dp)).toInt().coerceAtLeast(1)
            val toggles = QuickToggle.entries.filter { it != QuickToggle.TORCH || states.torchAvailable }.take(fit)
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                toggles.forEach { toggle ->
                    val (icon, on, label) = when (toggle) {
                        QuickToggle.TORCH -> Triple(R.drawable.ic_qs_torch, states.torch, toggle.label)
                        QuickToggle.DND -> Triple(R.drawable.ic_qs_dnd, states.dnd, toggle.label)
                        QuickToggle.RINGER -> when (states.ringer) {
                            RingerState.NORMAL -> Triple(R.drawable.ic_qs_bell, true, stringResource(R.string.w_toggle_ring))
                            RingerState.VIBRATE -> Triple(R.drawable.ic_qs_vibrate, false, stringResource(R.string.w_toggle_vibrate))
                            RingerState.SILENT -> Triple(R.drawable.ic_qs_silent, false, stringResource(R.string.w_toggle_silent))
                        }
                        QuickToggle.ROTATION -> Triple(R.drawable.ic_qs_rotate, states.rotation, toggle.label)
                        QuickToggle.INTERNET -> Triple(R.drawable.ic_qs_wifi, states.wifi, if (states.wifi) "Wi-Fi" else toggle.label)
                        QuickToggle.BLUETOOTH -> Triple(R.drawable.ic_qs_bluetooth, states.bluetooth, toggle.label)
                    }
                    ToggleButton(icon, label, on, button, showLabels, onClick = onToggle?.let { { it(toggle) } })
                }
            }
        }
    }
}

@Composable
private fun ToggleButton(icon: Int, label: String, on: Boolean, size: Dp, showLabel: Boolean, onClick: (() -> Unit)?) {
    // Opis dla czytnika ekranu czytamy tutaj: semantics { } nie jest funkcją @Composable.
    val stateDescription = if (on) stringResource(R.string.w_toggle_state_on, label) else stringResource(R.string.w_toggle_state_off, label)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, onClick = onClick) else Modifier)
            .padding(2.dp)
            .semantics { contentDescription = stateDescription },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // Minimalistyczne ikony konturowe (jak w panelu szybkich ustawień), kolor z motywu.
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(size * 0.5f),
            )
        }
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================== Ulubione kontakty ==============================

class ContactCallbacks(
    val onDial: (String) -> Unit,
    val onSms: (String) -> Unit,
    val onOpen: (FavoriteContact) -> Unit,
    val onOpenContacts: () -> Unit,
    val onGrant: () -> Unit,
)

@Composable
fun ContactsWidget(contacts: List<FavoriteContact>, hasPermission: Boolean, callbacks: ContactCallbacks?) {
    WidgetSurface(onClick = null) {
        when {
            !hasPermission -> HintText(stringResource(R.string.contacts_grant), callbacks?.onGrant)
            contacts.isEmpty() -> HintText(stringResource(R.string.contacts_empty), callbacks?.onOpenContacts)
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                // Niski widżet: same zdjęcia (bez imion), wysoki: większe zdjęcia z imionami.
                val h = maxHeight
                val showNames = h >= 70.dp
                val avatar = (if (showNames) h - 20.dp else h - 4.dp).coerceIn(28.dp, 64.dp)
                LazyRow(
                    userScrollEnabled = callbacks != null,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(contacts, key = { it.id }) { contact -> ContactBubble(contact, callbacks, avatar, showNames) }
                }
            }
        }
    }
}

// Dotknięcie kontaktu rozwija małe menu: zadzwoń / SMS / karta kontaktu.
@Composable
private fun ContactBubble(contact: FavoriteContact, callbacks: ContactCallbacks?, avatar: Dp = 44.dp, showName: Boolean = true) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(avatar + 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(if (callbacks != null) Modifier.clickable { menu = true } else Modifier),
        ) {
            Avatar(contact, avatar.value.toInt())
            if (showName) Text(
                contact.name.substringBefore(' '),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (callbacks != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                contact.number?.let { number ->
                    DropdownMenuItem(text = { Text(stringResource(R.string.contacts_call)) }, onClick = { menu = false; callbacks.onDial(number) })
                    DropdownMenuItem(text = { Text("💬  SMS") }, onClick = { menu = false; callbacks.onSms(number) })
                }
                DropdownMenuItem(text = { Text("👤  ${contact.name}") }, onClick = { menu = false; callbacks.onOpen(contact) })
            }
        }
    }
}

@Composable
private fun Avatar(contact: FavoriteContact, sizeDp: Int) {
    val photo = contact.photo
    if (photo != null) {
        Image(bitmap = photo, contentDescription = null, modifier = Modifier.size(sizeDp.dp).clip(CircleShape))
    } else {
        // Kolor z nazwy: ten sam kontakt zawsze ma ten sam kolor (hashCode jak GetHashCode w C#).
        val palette = listOf(0xFF8FB2FF, 0xFF7FD6AE, 0xFFF0A844, 0xFFFF9A7F, 0xFFB9A5FF, 0xFF6FC7D9)
        val color = Color(palette[Math.floorMod(contact.name.hashCode(), palette.size)])
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(color),
        ) {
            val initials = contact.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
            Text(initials, color = Color(0xFF16181B), fontWeight = FontWeight.SemiBold)
        }
    }
}

// ============================== Wspólne ==============================

@Composable
private fun HintText(text: String, onClick: (() -> Unit)?) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun SmallRoundButton(symbol: String, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}
