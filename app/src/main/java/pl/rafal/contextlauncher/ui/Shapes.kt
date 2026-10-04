package pl.rafal.contextlauncher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R

// Kształt ikon trybów (Ustawienia → Zaawansowane). enum z metodą — jak enum z rozszerzeniem w C#.
enum class IconShape(@StringRes private val labelRes: Int) {
    ROUNDED(R.string.look_shape_rounded),
    CIRCLE(R.string.look_shape_circle),
    SQUARE(R.string.look_shape_square);

    val label: String get() = AppText.get(labelRes)

    fun shape(size: Dp): Shape = when (this) {
        ROUNDED -> RoundedCornerShape(size * 0.32f)
        CIRCLE -> CircleShape
        SQUARE -> RoundedCornerShape(size * 0.12f) // lekko złagodzony róg, ostry kwadrat wygląda na ekranie "cyfrowo"
    }

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ROUNDED
    }
}

// Rozgłaszane w dół drzewa UI (CompositionLocal ≈ dziedziczona właściwość w WPF).
val LocalIconShape = staticCompositionLocalOf { IconShape.ROUNDED }
val LocalWidgetCorner = staticCompositionLocalOf { 20.dp }
