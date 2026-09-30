package pl.rafal.contextlauncher.ui.widgets

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

// Klasa rozmiaru widżetu — każdy widżet decyduje, co pokazać w danej klasie.
// Komórka siatki ma ok. 45–50 dp, więc: 1×1 = TINY, 2×2 = SMALL, niski pasek = STRIP, reszta = LARGE.
enum class WidgetSize { TINY, SMALL, STRIP, LARGE }

fun widgetSizeOf(w: Dp, h: Dp): WidgetSize = when {
    w < 70.dp && h < 70.dp -> WidgetSize.TINY
    w < 125.dp && h < 125.dp -> WidgetSize.SMALL
    h < 90.dp -> WidgetSize.STRIP
    else -> WidgetSize.LARGE
}

// Mierzy dostępne miejsce i podaje klasę rozmiaru + wymiary (jak ActualWidth/ActualHeight w WPF).
@Composable
internal fun AdaptiveWidget(content: @Composable (size: WidgetSize, w: Dp, h: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        content(widgetSizeOf(w, h), w, h)
    }
}

// Tekst w jednej linii, który ZMNIEJSZA się, zamiast zawijać do pionu (auto-size z Compose Foundation).
@Composable
internal fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    maxSize: TextUnit = 96.sp,
    minSize: TextUnit = 7.sp,
    color: Color = LocalContentColor.current,
    fontWeight: FontWeight? = FontWeight.SemiBold,
    textAlign: TextAlign = TextAlign.Start,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontWeight = fontWeight, textAlign = textAlign),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}

// Mała wersja widżetu: ikona na środku, pod nią drobny podpis, w rogu licznik.
// pulse = delikatne "mruganie", gdy w środku coś czeka (np. zapisana notatka).
@Composable
internal fun CompactTile(
    icon: Int,
    onClick: (() -> Unit)?,
    label: String? = null,
    badge: String? = null,
    pulse: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    WidgetSurface(onClick = onClick) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight)
            val alpha = if (pulse) {
                val transition = rememberInfiniteTransition(label = "puls")
                val a by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "puls",
                )
                a
            } else 1f
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier
                        .size(side * (if (label != null) 0.45f else 0.6f))
                        .alpha(alpha),
                )
                if (label != null) {
                    FitText(
                        label,
                        modifier = Modifier.fillMaxWidth(),
                        maxSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (badge != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(side * 0.3f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    FitText(
                        badge,
                        modifier = Modifier.padding(2.dp),
                        maxSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
