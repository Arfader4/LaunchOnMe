package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.AppInfo

// Czy pokazywać podpisy pod ikonami. CompositionLocal to wartość "rozgłaszana" w dół drzewa UI
// (jak dziedziczona właściwość w WPF), więc nie trzeba jej przekazywać przez każdy komponent.
val LocalShowAppLabels = staticCompositionLocalOf { true }

// Aplikacje zablokowane w aktywnym trybie (klucze AppInfo.appKey). compositionLocalOf (nie static),
// bo zbiór zmienia się często, a wtedy przerysowują się tylko ikony, które go czytają.
val LocalBlockedApps = compositionLocalOf { emptySet<String>() }

// Pakiety z nieprzeczytanym powiadomieniem (kropka w rogu ikony).
val LocalNotifiedApps = compositionLocalOf { emptySet<String>() }

// Wyciąganie ikony z szuflady na kartę. Pozycje są we współrzędnych całego ekranu (root),
// bo ikona i karta leżą w różnych częściach drzewa UI.
class ExternalDrag(
    val onStart: (AppInfo, Offset) -> Unit,
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
) {
    // remember + mutableStateOf = lokalny stan komponentu (jak pole prywatne kontrolki).
    // "by" pozwala pisać menuOpen zamiast menuOpen.value.
    var menuOpen by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) } // lewy górny róg ikony na ekranie
    val currentDrag by rememberUpdatedState(dragOut)
    val density = LocalDensity.current

    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                // then(...) dokleja modyfikator warunkowo.
                .then(
                    if (enabled) Modifier.combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
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
                                    menuOpen = false
                                    currentDrag?.onStart(app, origin + change.position)
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
                .padding(vertical = 6.dp),
        ) {
            val blocked = app.appKey in LocalBlockedApps.current
            Box {
                Image(
                    bitmap = app.icon,
                    contentDescription = null, // nazwa jest tuż pod ikoną, czytnik ekranu przeczyta ją stamtąd
                    // Zablokowana: przygaszona ikona z kłódką — widać, że jest, ale "nie teraz".
                    modifier = Modifier
                        .size(52.dp)
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
            if (LocalShowAppLabels.current) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            }
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
