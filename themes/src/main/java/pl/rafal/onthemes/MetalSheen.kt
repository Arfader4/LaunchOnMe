package pl.rafal.onthemes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Refleks światła na metalu (Luxury): ukośny jasny pas przejeżdża po elemencie mniej więcej co 4 s,
// przez resztę czasu jest poza krawędzią (pauza). Rysowany na wierzchu treści, w granicach przycięcia (clip).
// Jak animacja w WPF z RepeatBehavior="Forever" — tyle że liczona co klatkę w Compose.
@Composable
fun Modifier.metalSheen(): Modifier {
    val transition = rememberInfiniteTransition(label = "połysk metalu")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 4200, easing = LinearEasing)),
        label = "połysk metalu",
    )
    return this.drawWithContent {
        drawContent()
        val w = size.width
        val x = (-0.6f + progress * 2.8f) * w // pas widoczny w ok. pierwszej połowie cyklu, potem pauza
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                start = Offset(x, 0f),
                end = Offset(x + w * 0.5f, size.height),
            ),
        )
    }
}
