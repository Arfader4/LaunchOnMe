package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.db.FolderEntity

// Czy pokazywać podpisy pod ikonami. CompositionLocal to wartość "rozgłaszana" w dół drzewa UI
// (jak dziedziczona właściwość w WPF), więc nie trzeba jej przekazywać przez każdy komponent.
val LocalShowAppLabels = staticCompositionLocalOf { true }

// Skala podpisów (Ustawienia → Zaawansowane → Rozmiar tekstu): 0.85 / 1.0 / 1.15.
val LocalLabelScale = staticCompositionLocalOf { 1f }

// Czy pokazywać nazwy folderów na widżetach folderów (osobno od podpisów aplikacji).
val LocalShowFolderLabels = staticCompositionLocalOf { true }

// Aplikacje zablokowane w aktywnym trybie (klucze AppInfo.appKey). compositionLocalOf (nie static),
// bo zbiór zmienia się często, a wtedy przerysowują się tylko ikony, które go czytają.
val LocalBlockedApps = compositionLocalOf { emptySet<String>() }

// Pakiety z nieprzeczytanym powiadomieniem (kropka w rogu ikony).
val LocalNotifiedApps = compositionLocalOf { emptySet<String>() }

// Wyciąganie ikony z szuflady na kartę. Pozycje są we współrzędnych całego ekranu (root),
// bo ikona i karta leżą w różnych częściach drzewa UI.
// Co jest przeciągane z szuflady: aplikacja albo cały folder (sealed ≈ zamknięta hierarchia klas w C#).
sealed interface DragItem {
    data class App(val app: AppInfo) : DragItem
    data class Folder(val folder: FolderEntity, val preview: List<AppInfo>) : DragItem
}

class ExternalDrag(
    val onStart: (DragItem, Offset) -> Unit,
    val onMove: (Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

// Pozycja menu po przytrzymaniu ikony: tekst + co zrobić.
data class MenuAction(val label: String, val onClick: () -> Unit)

// Jedna ikona aplikacji, używana i na karcie trybu, i w szufladzie.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppInfo,
    onClick: () -> Unit,
    menu: List<MenuAction>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true, // w trybie edycji układu ikona nie reaguje na dotyk, tylko się ją przeciąga
    dragOut: ExternalDrag? = null, // przytrzymaj i przeciągnij: ikona wyjeżdża z szuflady na kartę
    longPressMenu: Boolean = true, // false na karcie: tam przytrzymanie otwiera edycję układu
) {
    // remember + mutableStateOf = lokalny stan komponentu (jak pole prywatne kontrolki).
    // "by" pozwala pisać menuOpen zamiast menuOpen.value.
    var menuOpen by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) } // lewy górny róg ikony na ekranie
    val currentDrag by rememberUpdatedState(dragOut)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    // Wciśnięcie: ikona lekko się zapada i sprężynuje z powrotem (stan "pressed" z tego samego źródła co ripple).
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.9f else 1f, animationSpec = Motion.press(), label = "wciśnięcie ikony",
    )
    val iconBounds = remember { IntArray(4) } // położenie ikony na ekranie → stąd "wyrośnie" aplikacja
    val launch = {
        if (iconBounds[2] > 0) LaunchOrigin.icon(iconBounds[0], iconBounds[1], iconBounds[2], iconBounds[3])
        onClick()
    }

    // BoxWithConstraints: ikona dopasowuje się do kratki. Na karcie (1×1 albo 2×2) wysokość jest ograniczona,
    // w szufladzie nie (lista) — wtedy bierzemy pełny rozmiar 52 dp.
    BoxWithConstraints(modifier) {
        val metrics = iconMetrics(maxWidth, maxHeight)
        val iconSize = metrics.icon
        val showLabel = LocalShowAppLabels.current
        val bounded = maxHeight != Dp.Infinity
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Na karcie ikona z podpisem jest wyśrodkowana w komórce — tak samo jak folder, więc leżą w jednej linii.
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (bounded) Modifier.fillMaxHeight() else Modifier)
                .clip(RoundedCornerShape(12.dp))
                // then(...) dokleja modyfikator warunkowo.
                .then(
                    if (enabled) Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = launch,
                        onLongClick = if (longPressMenu) ({ menuOpen = true }) else null,
                    )
                    else Modifier,
                )
                .onGloballyPositioned { origin = it.positionInRoot() }
                // Przytrzymanie + ruch palcem: menu znika i zaczyna się przeciąganie.
                // Sam przytrzymanie bez ruchu = zwykłe menu (obsługuje je combinedClickable wyżej).
                .then(
                    if (enabled && dragOut != null) Modifier.pointerInput(app.key) {
                        var total = Offset.Zero
                        var started = false
                        val threshold = with(density) { 16.dp.toPx() }
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                total = Offset.Zero
                                started = false
                            },
                            onDrag = { change, amount ->
                                total += amount
                                if (!started && total.getDistance() > threshold) {
                                    started = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuOpen = false
                                    currentDrag?.onStart(DragItem.App(app), origin + change.position)
                                }
                                if (started) {
                                    change.consume() // lista pod spodem ma się nie przewijać
                                    currentDrag?.onMove(origin + change.position)
                                }
                            },
                            onDragEnd = { if (started) currentDrag?.onEnd() },
                            onDragCancel = { if (started) currentDrag?.onCancel() },
                        )
                    } else Modifier,
                )
                .padding(vertical = metrics.pad),
        ) {
            val blocked = app.appKey in LocalBlockedApps.current
            Box {
                Image(
                    bitmap = app.icon,
                    contentDescription = null, // nazwa jest tuż pod ikoną, czytnik ekranu przeczyta ją stamtąd
                    // Zablokowana: przygaszona ikona z kłódką — widać, że jest, ale "nie teraz".
                    modifier = Modifier
                        .size(iconSize)
                        .onGloballyPositioned { // przed skalą wciśnięcia: pełny rozmiar ikony
                            val p = it.positionOnScreen()
                            iconBounds[0] = p.x.toInt()
                            iconBounds[1] = p.y.toInt()
                            iconBounds[2] = it.size.width
                            iconBounds[3] = it.size.height
                        }
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .alpha(if (blocked) 0.4f else 1f),
                )
                if (app.packageName in LocalNotifiedApps.current) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .semantics { contentDescription = "ma powiadomienia" },
                    )
                }
                if (blocked) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .semantics { contentDescription = "zablokowana w tym trybie" },
                    ) { Text("🔒", style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (showLabel) IconLabel(app.label, metrics)
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menu.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        menuOpen = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

// Wspólne wymiary "ikony z podpisem": aplikacja i folder na karcie liczą je tak samo,
// dzięki czemu stoją w jednej linii (jak kontrolki ze wspólnym stylem w WPF).
data class IconMetrics(val icon: Dp, val fontSize: Float, val gap: Dp, val labelDp: Dp, val pad: Dp)

@Composable
fun iconMetrics(maxWidth: Dp, maxHeight: Dp, withLabel: Boolean = LocalShowAppLabels.current): IconMetrics {
    val compact = maxWidth < 64.dp // mała kratka 1×1: mniejszy tekst i odstępy
    val fontSize = (if (compact) 10.5f else 12f) * LocalLabelScale.current
    val gap = if (compact) 2.dp else 6.dp
    val pad = if (compact) 2.dp else 6.dp
    // Wysokość podpisu (jedna linia) + odstęp od ikony. Liczona zawsze, gdy podpisy aplikacji są włączone —
    // folder bez nazwy rezerwuje to samo miejsce, żeby jego znaczek nie "podskakiwał" względem ikon obok.
    val labelDp = if (withLabel) with(LocalDensity.current) { (fontSize * 1.25f).sp.toDp() } + gap else 0.dp
    val byHeight = if (maxHeight == Dp.Infinity) 52.dp else maxHeight - labelDp - pad * 2
    val icon = minOf(52.dp, maxWidth * 0.8f, byHeight).coerceAtLeast(16.dp)
    return IconMetrics(icon, fontSize, gap, labelDp, pad)
}

// Podpis pod ikoną — ten sam dla aplikacji i folderu.
@Composable
fun IconLabel(text: String, metrics: IconMetrics) {
    Spacer(Modifier.height(metrics.gap))
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = metrics.fontSize.sp, lineHeight = (metrics.fontSize * 1.25f).sp),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
