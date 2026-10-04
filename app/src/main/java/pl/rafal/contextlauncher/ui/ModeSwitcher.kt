package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.db.ModeEntity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf

private val ModeRowHeight = 52.dp // wysokość wiersza trybu na liście (też krok przy przeciąganiu)

// Rozwijany przycisk trybu na dole ekranu (w zasięgu kciuka).
// Dotknięcie rozwija listę trybów do góry; ⋮ przy trybie otwiera jego ustawienia.
// Od kiedy nie ma nagłówka, w tym samym menu są też akcje karty (Ustawienia, Dodaj widżet…).
@Composable
fun ModeSwitcherButton(
    active: ModeEntity?,
    modes: List<ModeEntity>,
    closeSignal: Int, // zmiana tej liczby (np. naciśnięcie Home) zamyka listę
    onSelect: (ModeEntity) -> Unit,
    onManage: (ModeEntity) -> Unit,
    onNewMode: () -> Unit,
    onTimed: ((ModeEntity) -> Unit)? = null, // "włącz na czas" (⏱ przy trybie)
    compact: Boolean = false, // sama ikona trybu wypełniająca przycisk
    extraActions: List<MenuAction> = emptyList(), // akcje pod listą trybów
    menuHeader: String? = null,                   // np. "Podróż · do 17:30"
    badge: Boolean = false,                       // kropka: coś czeka (np. ręczne przełączniki)
    onReorder: ((List<ModeEntity>) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(closeSignal) { expanded = false }
    // Klawisz ON: dotknięcie = lista trybów, przytrzymanie = łuk z trybami (wybór jednym ruchem palca).
    val arc = remember { ModeArcState() }
    val haptics by androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.platform.LocalHapticFeedback.current)
    val deadZonePx = with(androidx.compose.ui.platform.LocalDensity.current) { ModeArcDeadZone.toPx() }
    val currentModes by androidx.compose.runtime.rememberUpdatedState(modes)
    val currentOnSelect by androidx.compose.runtime.rememberUpdatedState(onSelect)
    val currentActive by androidx.compose.runtime.rememberUpdatedState(active) // gest żyje dłużej niż jedno przerysowanie
    val arcGesture = Modifier.modeArcGesture(
        state = arc,
        modes = { currentModes },
        onTap = { expanded = true },
        onPick = { mode -> if (mode.id != currentActive?.id) currentOnSelect(mode) },
        onOpen = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
        deadZonePx = deadZonePx,
    )

    Box(
        // Klawisz po prawej połowie ekranu → łuk rozwija się w lewo-w górę; po lewej (leworęczni) → w prawo-w górę.
        Modifier.onGloballyPositioned { coords ->
            val centerX = coords.positionInWindow().x + coords.size.width / 2f
            arc.opensLeft = centerX > coords.findRootCoordinates().size.width / 2f
        },
    ) {
        if (compact) {
            // Ikona trybu wypełnia cały kafelek, w kształcie z ustawień (koło / zaokrąglony / kwadrat).
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(LocalIconShape.current.shape(56.dp))
                    .then(arcGesture)
                    .semantics {
                        contentDescription = "Zmień tryb, aktywny: ${active?.name.orEmpty()}. Przytrzymaj, aby wybrać z łuku."
                        role = androidx.compose.ui.semantics.Role.Button
                        onClick { expanded = true; true }
                    },
            ) {
                if (active != null) ModeBadge(active, size = 56.dp)
            }
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .then(arcGesture)
                    .semantics {
                        contentDescription = "Zmień tryb, aktywny: ${active?.name.orEmpty()}. Przytrzymaj, aby wybrać z łuku."
                        role = androidx.compose.ui.semantics.Role.Button
                        onClick { expanded = true; true }
                    }
                    .padding(start = 10.dp, end = 16.dp),
            ) {
                if (active != null) ModeBadge(active, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    active?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("▴", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ModeArcOverlay(arc, modes, active?.id)
        ModeDropdown(
            expanded = expanded,
            onDismiss = { expanded = false },
            active = active,
            modes = modes,
            onSelect = onSelect,
            onManage = onManage,
            onNewMode = onNewMode,
            onTimed = onTimed,
            extraActions = extraActions,
            header = menuHeader,
            onReorder = onReorder,
        )
    }
}

// Lista trybów + akcje. Wydzielona, bo tej samej listy używa też widżet "Tryby" w małych rozmiarach.
@Composable
fun ModeDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    active: ModeEntity?,
    modes: List<ModeEntity>,
    onSelect: (ModeEntity) -> Unit,
    onManage: (ModeEntity) -> Unit,
    onNewMode: () -> Unit,
    onTimed: ((ModeEntity) -> Unit)? = null,
    extraActions: List<MenuAction> = emptyList(),
    header: String? = null,
    onReorder: ((List<ModeEntity>) -> Unit)? = null, // przytrzymanie + przeciągnięcie trybu na liście
) {
    // DropdownMenu sam otwiera się do góry, gdy pod przyciskiem brakuje miejsca.
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        header?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        // Kolejność na żywo w trakcie przeciągania (przytrzymaj tryb i przesuń palcem w górę / w dół).
        // Stały stan (gest żyje dłużej niż jedno przerysowanie); synchronizacja z bazą tylko poza przeciąganiem.
        var draggingId by remember { mutableStateOf<Long?>(null) }
        val orderState = remember { mutableStateOf(modes) }
        var order by orderState
        val latestModes by androidx.compose.runtime.rememberUpdatedState(modes)
        LaunchedEffect(modes) { if (draggingId == null) orderState.value = modes }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { ModeRowHeight.toPx() }
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        val currentOnReorder by androidx.compose.runtime.rememberUpdatedState(onReorder)
        val currentOnSelect by androidx.compose.runtime.rememberUpdatedState(onSelect)
        val currentOnDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
        for (mode in order) {
            key(mode.id) {
                val isActive = mode.id == active?.id
                val dragged = draggingId == mode.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragged) dragOffset else 0f
                            val s = if (dragged) 1.03f else 1f
                            scaleX = s
                            scaleY = s
                            shadowElevation = if (dragged) 8.dp.toPx() else 0f
                            shape = RoundedCornerShape(14.dp)
                            clip = dragged
                        }
                        .background(if (dragged) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                        .widthIn(min = 220.dp)
                        .height(ModeRowHeight)
                        .semantics {
                            role = androidx.compose.ui.semantics.Role.Button
                            onClick(label = mode.name) {
                                onDismiss()
                                onSelect(mode)
                                true
                            }
                        }
                        // Jeden gest: krótkie dotknięcie = włącz tryb, przytrzymanie + ruch = zmiana kolejności.
                        .pointerInput(mode.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var lifted = false
                                var consumedUp = false
                                var moved = false
                                val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) {
                                            lifted = true
                                            consumedUp = change?.isConsumed == true // ⏱ / ⋮ obsłużyły dotyk same
                                            break
                                        }
                                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                            moved = true // przewijanie listy — nie nasze
                                            break
                                        }
                                    }
                                } == null
                                if (!held) {
                                    if (lifted && !consumedUp && !moved) {
                                        currentOnDismiss()
                                        currentOnSelect(mode)
                                    }
                                } else if (currentOnReorder != null) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    draggingId = mode.id
                                    dragOffset = 0f
                                    try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        // × 1.03: wiersz jest powiększony, więc lokalny ruch palca jest o 3% mniejszy.
                                        dragOffset += (change.position.y - change.previousPosition.y) * 1.03f
                                        change.consume()
                                        // Przesunięcie o pół wiersza = zamiana z sąsiadem (wiersz "przeskakuje" pod palcem).
                                        val list = order
                                        val index = list.indexOfFirst { it.id == mode.id }
                                        if (dragOffset > rowPx / 2 && index < list.size - 1) {
                                            order = list.toMutableList().apply { add(index + 1, removeAt(index)) }
                                            dragOffset -= rowPx
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        } else if (dragOffset < -rowPx / 2 && index > 0) {
                                            order = list.toMutableList().apply { add(index - 1, removeAt(index)) }
                                            dragOffset += rowPx
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    } finally {
                                        draggingId = null
                                        dragOffset = 0f
                                    }
                                    if (order.map { it.id } != latestModes.map { it.id }) currentOnReorder?.invoke(order)
                                }
                            }
                        }
                        .padding(start = 12.dp),
                ) {
                    ModeBadge(mode, size = 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        mode.name,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onTimed != null && !isActive) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onDismiss()
                                    onTimed(mode)
                                }
                                .semantics { contentDescription = "Włącz ${mode.name} na czas" },
                        ) { Text("⏱", style = MaterialTheme.typography.titleMedium) }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                onDismiss()
                                onManage(mode)
                            }
                            .semantics { contentDescription = "Ustawienia trybu ${mode.name}" },
                    ) { Text("⋮", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
        if (onReorder != null && modes.size > 1) {
            Text(
                "Przytrzymaj tryb i przesuń, aby zmienić kolejność",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        DropdownMenuItem(
            text = { Text("+ Nowy tryb") },
            onClick = {
                onDismiss()
                onNewMode()
            },
        )
        if (extraActions.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            extraActions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                )
            }
        }
    }
}
