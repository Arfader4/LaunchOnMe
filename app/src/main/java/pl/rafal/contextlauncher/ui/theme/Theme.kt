package pl.rafal.contextlauncher.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import pl.rafal.onthemes.ThemeMode
import pl.rafal.onthemes.ThemeSpec
import pl.rafal.onthemes.Themes

@Composable
fun ContextLauncherTheme(
    palette: ThemeSpec = Themes.NIGHT, // motywy mieszkają w module OnThemes
    accent: Long? = null,
    themeMode: ThemeMode = ThemeMode.AUTO,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark(isSystemInDarkTheme())
    val target = palette.colorScheme(dark, accent)
    val colors = animateColorScheme(target) // płynne przejście przy zmianie trybu albo motywu

    // Ikony paska statusu: ciemne na jasnym motywie, jasne na ciemnym.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                // Po faktycznej jasności tła (własny schemat może być jasny mimo trybu "Ciemny").
                val lightBars = target.background.luminance() > 0.5f
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    // LocalContentColor = domyślny kolor tekstu i ikon. MaterialTheme go nie ustawia (robi to dopiero Surface),
    // a my rysujemy na zwykłym Box — stąd czarne napisy na ciemnym tle. Ustawiamy go więc sami, dla całej aplikacji.
    MaterialTheme(colorScheme = colors, typography = Typography) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}

// Animujemy najważniejsze role; reszta zmienia się od razu (i tak jej prawie nie widać).
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    @Composable
    fun animate(color: Color): Color {
        val value by animateColorAsState(color, animationSpec = tween(durationMillis = 450), label = "kolor motywu")
        return value
    }
    return target.copy(
        primary = animate(target.primary),
        onPrimary = animate(target.onPrimary),
        background = animate(target.background),
        onBackground = animate(target.onBackground),
        surface = animate(target.surface),
        onSurface = animate(target.onSurface),
        surfaceVariant = animate(target.surfaceVariant),
        onSurfaceVariant = animate(target.onSurfaceVariant),
        outline = animate(target.outline),
    )
}
