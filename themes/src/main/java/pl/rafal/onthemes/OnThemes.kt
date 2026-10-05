package pl.rafal.onthemes

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable

// Punkt wejścia modułu dla launchera (jak OnHand / StickOnMe): inicjalizacja, intencje, ikona.
object OnThemes {
    // Pełna nazwa aktywności — launcher po niej rozpoznaje ikonę OnThemes na liście aplikacji.
    const val ACTIVITY_CLASS = "pl.rafal.onthemes.OnThemesActivity"

    var host: OnThemesHost? = null
        private set

    // Wołane z klasy aplikacji launchera: teksty, gospodarz i od razu wczytany stan motywu.
    fun init(context: Context, host: OnThemesHost?) {
        OnThemesText.init(context)
        this.host = host
        ThemeStore.get(context)
    }

    // Kolory systemu (motyw "Systemowy") — odśwież po powrocie na ekran; tapeta / paleta mogła się zmienić.
    fun refreshSystemColors(context: Context) = SystemColors.refresh(context)

    fun openIntent(context: Context): Intent =
        Intent(context, OnThemesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // Ikona OnThemes w wersji jasnej albo ciemnej niezależnie od trybu systemu: zasoby czytamy przez kontekst
    // z podmienioną konfiguracją (night yes/no), więc Android sam wybierze drawable albo drawable-night.
    // Launcher używa tego, gdy jego motyw ma wymuszoną jasność (Jasny/Ciemny zamiast Auto).
    fun appIcon(context: Context, dark: Boolean): Drawable? = runCatching {
        val config = Configuration(context.resources.configuration)
        val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        context.createConfigurationContext(config).getDrawable(R.mipmap.ic_onthemes)
    }.getOrNull()
}
