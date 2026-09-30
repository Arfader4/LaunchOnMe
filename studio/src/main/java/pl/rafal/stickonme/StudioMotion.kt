package pl.rafal.stickonme

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

// Ruch w StickOnMe (odpowiednik ui/Motion.kt launchera — moduły są osobne, więc tu własna, mała wersja).

// Pojawianie się "falą" w siatce: element nr index startuje trochę po poprzednim.
// Tylko tuż po wejściu na ekran (startedAt) — przy przewijaniu elementy są od razu widoczne.
@Composable
fun Modifier.studioStaggerIn(index: Int, startedAt: Long): Modifier {
    val animate = index < 30 && System.currentTimeMillis() - startedAt < 450
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * 18L)
            progress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        val s = 0.85f + 0.15f * progress.value
        scaleX = s
        scaleY = s
    }
}

// Cofanie z podglądem (Android 14+): w trakcie gestu ekran lekko się zmniejsza i odsuwa, puszczenie = onBack,
// rezygnacja = powrót do pełnego rozmiaru. Na starszych Androidach działa jak zwykły "Wstecz".
// Zwraca State (nie Float): postęp czytany tylko w warstwie graficznej, więc ekran nie przelicza się co klatkę gestu.
@Composable
fun predictiveBackProgress(enabled: Boolean = true, onBack: () -> Unit): State<Float> {
    val progress = remember { mutableFloatStateOf(0f) }
    val currentOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { progress.floatValue = it.progress }
            currentOnBack()
            // Ekran został (np. zapis się nie udał) → wraca do pełnego rozmiaru; gdy zniknął, to i tak bez znaczenia.
            delay(600)
            progress.floatValue = 0f
        } catch (e: CancellationException) {
            progress.floatValue = 0f
            throw e
        }
    }
    return progress
}

fun Modifier.backPreview(progress: State<Float>): Modifier = graphicsLayer {
    val p = progress.value
    if (p > 0f) {
        val s = 1f - 0.08f * p
        scaleX = s
        scaleY = s
        translationX = p * 28.dp.toPx()
        shape = RoundedCornerShape(28.dp)
        clip = true
    }
}

// "Odklejenie" zapisanej naklejki: róg się podnosi (obrót wokół prawego dolnego rogu), naklejka unosi się,
// maleje i znika w stronę biblioteki — nawiązanie do logo z odklejanym rogiem.
@Composable
fun PeelAway(image: ImageBitmap, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(image) {
        t.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        currentOnDone()
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight) * 0.6f
        val heightPx = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .size(side)
                .graphicsLayer {
                    val p = t.value
                    transformOrigin = TransformOrigin(1f, 1f)
                    cameraDistance = 12f * density
                    rotationX = 50f * p
                    rotationZ = -14f * p
                    translationY = -heightPx * 0.35f * p
                    val s = 1f - 0.7f * p
                    scaleX = s
                    scaleY = s
                    alpha = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f
                },
        ) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}
