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
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R
import androidx.annotation.StringRes

// Jaki gest pokazuje animowana dłoń nad kartą samouczka.
private enum class TutorialGesture { NONE, SWIPE_UP, SWIPE_DOWN, SWIPE_SIDE, HOLD, DROP }

private data class TutorialStep(@StringRes val title: Int, @StringRes val text: Int, val gesture: TutorialGesture)

private val TutorialSteps = listOf(
    TutorialStep(R.string.tut_welcome_title, R.string.tut_welcome_text, TutorialGesture.NONE),
    TutorialStep(R.string.tut_apps_title, R.string.tut_apps_text, TutorialGesture.SWIPE_UP),
    TutorialStep(R.string.tut_notifications_title, R.string.tut_notifications_text, TutorialGesture.SWIPE_DOWN),
    TutorialStep(R.string.tut_modes_title, R.string.tut_modes_text, TutorialGesture.NONE),
    TutorialStep(R.string.tut_edit_title, R.string.tut_edit_text, TutorialGesture.HOLD),
    TutorialStep(R.string.tut_folders_title, R.string.tut_folders_text, TutorialGesture.DROP),
    TutorialStep(R.string.tut_pages_title, R.string.tut_pages_text, TutorialGesture.SWIPE_SIDE),
    TutorialStep(R.string.tut_done_title, R.string.tut_done_text, TutorialGesture.NONE),
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
            Text(stringResource(current.title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(current.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    TextButton(onClick = onFinish) { Text(stringResource(R.string.common_skip)) }
                    Button(onClick = { step++ }) { Text(stringResource(R.string.common_next)) }
                } else {
                    Button(onClick = onFinish) { Text(stringResource(R.string.tut_lets_go)) }
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
                TutorialGesture.SWIPE_UP -> stringResource(R.string.tut_gesture_up)
                TutorialGesture.SWIPE_DOWN -> stringResource(R.string.tut_gesture_down)
                TutorialGesture.SWIPE_SIDE -> stringResource(R.string.tut_gesture_side)
                TutorialGesture.HOLD -> stringResource(R.string.tut_gesture_hold)
                TutorialGesture.DROP -> stringResource(R.string.tut_gesture_drop)
                TutorialGesture.NONE -> ""
            },
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
