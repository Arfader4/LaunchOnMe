package pl.rafal.onthemes

import androidx.annotation.DrawableRes

// Tryb launchera widziany z OnThemes — tylko to, czego potrzeba do podglądu i wyboru motywu.
data class HostMode(
    val id: Long,
    val name: String,
    @DrawableRes val iconRes: Int, // symbol trybu (zasób z launchera — ten sam APK, więc id działa)
    val color: Long,
    val themeId: String?,          // własny motyw trybu; null = motyw globalny
    val accent: Long?,
)

// Interfejs gospodarza (launchera). Moduł nie zna bazy launchera — launcher rejestruje implementację
// w OnThemes.init (jak wstrzyknięcie zależności przez interfejs w .NET).
interface OnThemesHost {
    suspend fun modes(): List<HostMode>

    // themeId = null → tryb wraca do motywu globalnego.
    suspend fun setModeTheme(modeId: Long, themeId: String?)
}
