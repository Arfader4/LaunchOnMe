package pl.rafal.onthemes

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Kolory systemu (Material You / One UI "Paleta kolorów") — Android 12+ liczy je z tapety.
// Motyw "Systemowy" tylko je czyta: zmienić ich nie da się (Good Lock / Theme Park nie mają API),
// można jedynie zmienić tapetę albo paletę w Ustawieniach telefonu.
object SystemColors {
    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private var light by mutableStateOf<Roles?>(null)
    private var dark by mutableStateOf<Roles?>(null)

    // Krótka paleta: kolory główny, drugorzędny i trzeciorzędny z obu wersji (po odcieniu, bez powtórzeń).
    var palette by mutableStateOf<List<Long>>(emptyList())
        private set

    fun roles(dark: Boolean): Roles? = if (dark) this.dark else light

    // Wołane przy starcie i po powrocie do launchera / OnThemes (użytkownik mógł zmienić tapetę albo paletę).
    fun refresh(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) read(context) // jawny warunek — lint widzi sprawdzenie API
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun read(context: Context) {
        val l = dynamicLightColorScheme(context)
        val d = dynamicDarkColorScheme(context)
        light = toRoles(l)
        dark = toRoles(d)
        palette = sortByHue(
            listOf(l.primary, l.secondary, l.tertiary, d.primary, d.secondary, d.tertiary).map { it.long() }.distinct(),
        )
    }

    private fun toRoles(s: ColorScheme) = Roles(
        background = s.background.long(),
        surface = s.surfaceContainer.long(),
        surfaceVariant = s.surfaceContainerHigh.long(),
        outline = s.outlineVariant.long(),
        onBackground = s.onSurface.long(),
        onSurfaceVariant = s.onSurfaceVariant.long(),
        primary = s.primary.long(),
        onPrimary = s.onPrimary.long(),
    )

    private fun Color.long(): Long = toArgb().toLong() and 0xFFFFFFFFL
}
