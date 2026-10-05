package pl.rafal.contextlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

// Warstwa na cały ekran, rysowana w GŁÓWNYM oknie launchera (jak "adorner layer" w WPF):
// komponent gdzieś głęboko w drzewie (np. klawisz ON) wstawia tu treść, a rysuje ją korzeń ekranu, nad kartą.
// Okno jest edge-to-edge, więc warstwa sięga też pod paski systemu — w przeciwieństwie do okna Popup,
// które na Androidzie ≤14 kończy się na paskach. Do tego bez tworzenia osobnego okna = taniej.
@Stable
class ScreenOverlay {
    class Layer(val content: @Composable () -> Unit)

    val layers = mutableStateListOf<Layer>()
}

// Podawana w LauncherApp; null = brak warstwy (np. inny ekran) — wtedy OnScreen po prostu nic nie rysuje.
val LocalScreenOverlay = staticCompositionLocalOf<ScreenOverlay?> { null }

// Wstawiane jako OSTATNI element korzenia ekranu (żeby było nad wszystkim).
@Composable
fun ScreenOverlayLayer(overlay: ScreenOverlay) {
    // key(layer): każda warstwa ma własny stan (animacje), nawet gdy inna zniknie przed nią.
    for (layer in overlay.layers) key(layer) { layer.content() }
}

// Treść na całym ekranie, dopóki visible = true (i ten composable jest w drzewie).
@Composable
fun OnScreen(visible: Boolean, content: @Composable () -> Unit) {
    val overlay = LocalScreenOverlay.current
    val current by rememberUpdatedState(content)
    if (overlay != null && visible) {
        DisposableEffect(overlay) {
            val layer = ScreenOverlay.Layer { current() }
            overlay.layers.add(layer)
            onDispose { overlay.layers.remove(layer) }
        }
    }
}

// Przygaszenie całego ekranu (np. pod listą trybów): płynnie pojawia się i znika.
// Lista w osobnym oknie (DropdownMenu) leży nad nim, więc odcina się od karty i tapety.
@Composable
fun ScreenDim(visible: Boolean, alpha: Float = 0.5f) {
    val shown = animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 220 else 160), label = "przygaszenie")
    // derivedStateOf: przeliczenie tylko, gdy zmieni się "widać / nie widać", a nie w każdej klatce animacji.
    val stillShown by remember { derivedStateOf { shown.value > 0f } }
    OnScreen(visible || stillShown) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = shown.value } // odczyt tylko w warstwie graficznej — bez przeliczania układu
                .background(Color.Black.copy(alpha = alpha)),
        )
    }
}
