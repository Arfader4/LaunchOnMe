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
import androidx.compose.ui.res.stringResource
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
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.db.ModeEntity
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf

private val ModeRowHeight = 52.dp // wysokość wiersza trybu na liście (też krok przy przeciąganiu)

// Superelipsa |x|⁴ + |y|⁴ = 1 ("squircle" jak ikony iOS / One UI): boki lekko wypukłe, rogi bardzo miękkie.
private val OnKeyShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val steps = 72
    for (i in 0..steps) {
        val t = 2.0 * Math.PI * i / steps
        val c = Math.cos(t)
        val s = Math.sin(t)
        // Superelipsa w postaci parametrycznej: x = sgn(cos)·|cos|^(2/n), n = 4.
        val x = cx + cx * (Math.signum(c) * Math.pow(Math.abs(c), 0.5)).toFloat()
        val y = cy + cy * (Math.signum(s) * Math.pow(Math.abs(s), 0.5)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

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
    val switchDescription = stringResource(R.string.mode_switch_cd, active?.name.orEmpty())
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
            // Klawisz ON: organiczny "squircle" (superelipsa — miękkie boki zamiast kwadratu z rogami),
            // przy przytrzymaniu sprężyście rośnie, a po zmianie trybu rozchodzi się od niego fala w kolorze trybu.
            val grow by androidx.compose.animation.core.animateFloatAsState(
                if (arc.open || expanded) 1.1f else 1f, Motion.press(), label = "klawisz ON",
            )
            val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
            var lastActiveId by remember { mutableStateOf(active?.id) }
            LaunchedEffect(active?.id) {
                val previous = lastActiveId
                lastActiveId = active?.id
                if (previous != null && previous != active?.id) {
                    pulse.snapTo(0f)
                    pulse.animateTo(1f, androidx.compose.animation.core.tween(700))
                }
            }
            val ringColor = active?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .drawBehind {
                        // Fala po zmianie trybu: okrąg rośnie i gaśnie (rysowany za klawiszem, poza jego kształtem).
                        val p = pulse.value
                        if (p < 1f) {
                            drawCircle(
                                ringColor.copy(alpha = (1f - p) * 0.6f),
                                radius = size.minDimension / 2f * (1f + 0.45f * p),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx() * (1f - p) + 1f),
                            )
                        }
                    }
                    .graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                    }
                    .clip(OnKeyShape)
                    .then(arcGesture)
                    .semantics {
                        contentDescription = switchDescription
                        role = androidx.compose.ui.semantics.Role.Button
                        onClick { expanded = true; true }
                    },
            ) {
                if (active != null) ModeBadge(active, size = 56.dp, shape = OnKeyShape)
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
                        contentDescription = switchDescription
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
    // Bez wspólnego "pudełka": tryby to osobne owalne klawisze unoszące się nad kartą (każdy ma własne tło i cień),
    // pojawiające się falą od klawisza ON. Pod nimi mniejsze przyciski: nowy tryb, OnHand, Ustawienia…
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        val openedAt = remember { System.currentTimeMillis() } // start "fali" (menu wchodzi do kompozycji przy otwarciu)
        header?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
                val index = order.indexOf(mode)
                val pillColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                val ring = Color(mode.color)
                val timedDescription = stringResource(R.string.mode_timed_cd, mode.name)
                val manageDescription = stringResource(R.string.mode_manage_cd, mode.name)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .staggerIn(index, openedAt)
                        .padding(horizontal = 8.dp, vertical = 3.dp) // odstęp między klawiszami
                        .graphicsLayer {
                            translationY = if (dragged) dragOffset else 0f
                            val s = if (dragged) 1.03f else 1f
                            scaleX = s
                            scaleY = s
                            shadowElevation = (if (dragged) 10.dp else 3.dp).toPx()
                            shape = RoundedCornerShape(50)
                            clip = true
                        }
                        .background(pillColor)
                        .then(if (isActive) Modifier.border(1.5.dp, ring.copy(alpha = 0.8f), RoundedCornerShape(50)) else Modifier)
                        .widthIn(min = 220.dp)
                        .height(ModeRowHeight - 6.dp)
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
                                .semantics { contentDescription = timedDescription },
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
                            .semantics { contentDescription = manageDescription },
                    ) { Text("⋮", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
        if (onReorder != null && modes.size > 1) {
            Text(
                stringResource(R.string.mode_reorder_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        // Mniejsze przyciski pod trybami: nowy tryb i akcje karty (OnHand, Ustawienia, przypomnienia…).
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .staggerIn(order.size, openedAt)
                .widthIn(max = 320.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            SmallKey(stringResource(R.string.mode_new_button)) {
                onDismiss()
                onNewMode()
            }
            extraActions.forEach { action ->
                SmallKey(action.label) {
                    onDismiss()
                    action.onClick()
                }
            }
        }
    }
}

// Mały owalny przycisk pod listą trybów (z cieniem, bo menu nie ma wspólnego tła).
@Composable
private fun SmallKey(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .shadow(3.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}
