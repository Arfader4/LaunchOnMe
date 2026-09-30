package pl.rafal.contextlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Jaki gest pokazuje animowana dłoń nad kartą samouczka.
private enum class TutorialGesture { NONE, SWIPE_UP, SWIPE_DOWN, SWIPE_SIDE, HOLD, DROP }

private data class TutorialStep(val title: String, val text: String, val gesture: TutorialGesture)

private val TutorialSteps = listOf(
    TutorialStep(
        "Witaj w LaunchOnMe",
        "Każdy tryb ma własną kartę: aplikacje i widżety na jedną sytuację — pracę, dom, podróż. " +
            "Pokażę Ci w minutę najważniejsze gesty.",
        TutorialGesture.NONE,
    ),
    TutorialStep(
        "Wszystkie aplikacje",
        "Przesuń palcem w górę albo dotknij pola „Szukaj”. W szufladzie przesunięcie w bok przełącza na Foldery.",
        TutorialGesture.SWIPE_UP,
    ),
    TutorialStep(
        "Powiadomienia",
        "Przesuń w dół: lewa połowa ekranu otwiera powiadomienia, prawa — szybkie ustawienia.",
        TutorialGesture.SWIPE_DOWN,
    ),
    TutorialStep(
        "Tryby",
        "Klawisz ON (w rogu dolnego paska): dotknięcie = lista trybów i Ustawienia, przytrzymanie = łuk z trybami — " +
            "przesuń palec po łuku i puść na wybranym. Na widżecie „Tryby” kręcisz tarczą. " +
            "Tryb może też podpowiadać się sam — o stałych porach, po Wi-Fi, Bluetooth albo w danym miejscu.",
        TutorialGesture.NONE,
    ),
    TutorialStep(
        "Edycja układu",
        "Przytrzymaj puste miejsce albo dowolny element. Potem przeciągasz elementy, zmieniasz rozmiar uchwytem w rogu, " +
            "a ⚙ otwiera ustawienia widżetu. ✓ zapisuje, ✕ cofa zmiany.",
        TutorialGesture.HOLD,
    ),
    TutorialStep(
        "Foldery i stosy",
        "W edycji upuść ikonę na ikonę — powstanie folder. Widżet na widżet — stos widżetów; zmieniasz je przesunięciem w pionie albo uchwytem z kropkami z prawej. " +
            "Upuszczenie na pasku u góry usuwa element z karty.",
        TutorialGesture.DROP,
    ),
    TutorialStep(
        "Strony karty",
        "Przesuń w bok, aby zmienić stronę. W edycji przytrzymaj przeciągany element przy lewej lub prawej krawędzi — strona się przewinie, a element pojedzie z Tobą. " +
            "Przycisk Home wraca na pierwszą.",
        TutorialGesture.SWIPE_SIDE,
    ),
    TutorialStep(
        "Gotowe!",
        "Samouczek obejrzysz ponownie w Ustawieniach. Miłego układania 🙂",
        TutorialGesture.NONE,
    ),
)

// Samouczek pierwszego uruchomienia: przyciemniona karta, animowana dłoń i krótkie opisy gestów.
// Nakładka łapie dotyk (nic pod spodem się nie uruchomi przypadkiem); "Pomiń" kończy od razu.
@Composable
fun TutorialOverlay(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    BackHandler { if (step > 0) step-- else onFinish() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { } // blokada dotyku
            .systemBarsPadding()
            .padding(20.dp),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "tutorial",
            modifier = Modifier.align(Alignment.Center),
        ) { index ->
            GestureHint(TutorialSteps[index].gesture)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val current = TutorialSteps[step]
            Text(current.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(current.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Kropki postępu.
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                    TutorialSteps.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == step) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        )
                    }
                }
                if (step < TutorialSteps.lastIndex) {
                    TextButton(onClick = onFinish) { Text("Pomiń") }
                    Button(onClick = { step++ }) { Text("Dalej") }
                } else {
                    Button(onClick = onFinish) { Text("Zaczynamy") }
                }
            }
        }
    }
}

// Animowana "dłoń" pokazująca gest (emoji przesuwane w pętli; bez grafik, więc lekko i w każdym motywie).
@Composable
private fun GestureHint(gesture: TutorialGesture) {
    if (gesture == TutorialGesture.NONE) Text("✨", fontSize = 64.sp, textAlign = TextAlign.Center)
    else AnimatedGesture(gesture)
}

@Composable
private fun AnimatedGesture(gesture: TutorialGesture) {
    val transition = rememberInfiniteTransition(label = "gesture")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    // Ruch tylko w pierwszych 70% cyklu, potem chwila przerwy (łatwiej zauważyć początek gestu).
    val p = (t / 0.7f).coerceAtMost(1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Text(
                if (gesture == TutorialGesture.DROP) "🧩" else "👆",
                fontSize = 56.sp,
                modifier = Modifier.graphicsLayer {
                    val d = 70.dp.toPx()
                    when (gesture) {
                        TutorialGesture.SWIPE_UP -> translationY = d - 2 * d * p
                        TutorialGesture.SWIPE_DOWN -> translationY = -d + 2 * d * p
                        TutorialGesture.SWIPE_SIDE -> translationX = d - 2 * d * p
                        TutorialGesture.DROP -> {
                            translationX = -d + d * p
                            translationY = -d + d * p
                        }
                        TutorialGesture.HOLD -> {
                            val s = if (p < 1f) 1f - 0.15f * p else 0.85f // "wciśnięcie" palca
                            scaleX = s
                            scaleY = s
                        }
                        TutorialGesture.NONE -> Unit
                    }
                    alpha = if (t > 0.9f) (1f - t) * 10f else 1f
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when (gesture) {
                TutorialGesture.SWIPE_UP -> "w górę"
                TutorialGesture.SWIPE_DOWN -> "w dół"
                TutorialGesture.SWIPE_SIDE -> "w bok"
                TutorialGesture.HOLD -> "przytrzymaj"
                TutorialGesture.DROP -> "przeciągnij i upuść"
                TutorialGesture.NONE -> ""
            },
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
