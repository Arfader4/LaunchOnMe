package pl.rafal.contextlauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.rafal.contextlauncher.data.matchApps

class AppMatchingTest {

    // Para (pakiet, nazwa) udaje zainstalowaną aplikację.
    private val installed = listOf(
        "com.google.android.gm" to "Gmail",
        "com.google.android.calendar" to "Kalendarz",
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.maps" to "Mapy",
    )

    private fun match(hints: List<String>, limit: Int = 8) =
        matchApps(installed, hints, { it.first }, { it.second }, limit).map { it.second }

    @Test
    fun `kolejność wyniku odpowiada kolejności podpowiedzi`() {
        assertEquals(listOf("Mapy", "Gmail"), match(listOf("maps", "com.google.android.gm")))
    }

    @Test
    fun `dopasowanie działa też po nazwie aplikacji`() {
        assertEquals(listOf("Kalendarz"), match(listOf("kalendarz")))
    }

    @Test
    fun `aplikacja pasująca do dwóch podpowiedzi występuje raz`() {
        assertEquals(listOf("Spotify"), match(listOf("spotify", "music")))
    }

    @Test
    fun `limit obcina wynik`() {
        assertEquals(2, match(listOf("com.google"), limit = 2).size)
    }
}
