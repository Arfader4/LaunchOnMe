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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// Warstwa na cały ekran, rysowana w GŁÓWNYM oknie launchera (jak "adorner layer" w WPF):
// komponent gdzieś głęboko w drzewie (np. klawisz ON) wstawia tu treść, a rysuje ją korzeń ekranu, nad kartą.
// Okno jest edge-to-edge, więc warstwa sięga też pod paski systemu — w przeciwieństwie do okna Popup,
// które na Androidzie ≤14 kończy się na paskach. Do tego bez tworzenia osobnego okna = taniej.
@Stable
class ScreenOverlay {
    class Layer(val content: @Composable () -> Unit)

    val layers = mutableStateListOf<Layer>()

    // 1.6.1: rozmycie tła pod warstwą. Każde przygaszenie zgłasza swój postęp 0..1 (lambda, czytana dopiero
    // przy rysowaniu — bez przeliczania układu), a karta pod spodem rozmywa się wg największego z nich.
    val blurSources = mutableStateListOf<() -> Float>()
    fun backdrop(): Float = blurSources.maxOfOrNull { it() } ?: 0f
}

// Promień rozmycia przy pełnym przygaszeniu.
private val BackdropBlurRadius = 16.dp

// Na kontener z treścią ekranu (pod warstwą): rozmycie, gdy coś jest przygaszone.
// RenderEffect (rozmycie GPU) jest od Androida 12 — na starszych zostaje samo przygaszenie.
// Tapety systemu (za przezroczystym oknem) to nie rozmywa — tylko to, co rysuje launcher.
fun Modifier.screenBackdrop(overlay: ScreenOverlay): Modifier = this.graphicsLayer {
    val p = overlay.backdrop()
    renderEffect = if (p > 0.01f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val r = BackdropBlurRadius.toPx() * p
        android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
    } else {
        null
    }
}

// Zgłasza rozmycie tła na czas, gdy ten composable jest w drzewie (progress: 0 = brak, 1 = pełne).
@Composable
fun BackdropBlur(progress: () -> Float) {
    val overlay = LocalScreenOverlay.current
    val current by rememberUpdatedState(progress)
    if (overlay != null) {
        DisposableEffect(overlay) {
            val source: () -> Float = { current() }
            overlay.blurSources.add(source)
            onDispose { overlay.blurSources.remove(source) }
        }
    }
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
fun ScreenDim(visible: Boolean, alpha: Float = 0.6f) {
    val shown = animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 220 else 160), label = "przygaszenie")
    // derivedStateOf: przeliczenie tylko, gdy zmieni się "widać / nie widać", a nie w każdej klatce animacji.
    val stillShown by remember { derivedStateOf { shown.value > 0f } }
    if (visible || stillShown) BackdropBlur { shown.value }
    OnScreen(visible || stillShown) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = shown.value } // odczyt tylko w warstwie graficznej — bez przeliczania układu
                .background(Color.Black.copy(alpha = alpha)),
        )
    }
}
