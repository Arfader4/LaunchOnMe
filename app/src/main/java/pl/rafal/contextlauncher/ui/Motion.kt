package pl.rafal.contextlauncher.ui

import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.IntOffset
import java.lang.ref.WeakReference

// Jedno miejsce na "charakter ruchu" całej aplikacji (jak zasoby Storyboard/Duration w XAML):
// zmiana tutaj zmienia wszystkie przejścia naraz. Systemowe "Usuń animacje" Compose respektuje sam
// (skala czasu animacji = 0 → animacje kończą się od razu).
object Motion {
    // Strony karty: lekko sprężyste, bez widocznego odbicia.
    fun <T> page() = spring<T>(dampingRatio = 0.86f, stiffness = 420f)
    // Wejście paneli (szuflada, ustawienia): miękka sprężyna.
    fun <T> panel() = spring<T>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    val panelOffset = spring<IntOffset>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    // Wciśnięcie ikony i powrót.
    fun <T> press() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    // Zmiana trybu: karta "oddycha" (pomniejszenie i powrót).
    fun <T> mode() = spring<T>(dampingRatio = 0.78f, stiffness = 300f)
    // Szybkie znikanie (zamykanie folderu itp.).
    fun <T> fastOut() = tween<T>(durationMillis = 140)

    // Odstęp między kolejnymi ikonami przy pojawianiu się "falą" (i ile ikon maksymalnie czeka).
    const val STAGGER_MS = 14L
    const val STAGGER_MAX = 40
}

// Pojawianie się "falą": element nr index startuje trochę po poprzednim (przesunięcie w górę + rozjaśnienie).
// Tylko tuż po otwarciu listy (startedAt) — elementy, które wjeżdżają później przy przewijaniu, są od razu widoczne.
@Composable
fun Modifier.staggerIn(index: Int, startedAt: Long): Modifier {
    val animate = index < Motion.STAGGER_MAX && System.currentTimeMillis() - startedAt < 450
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * Motion.STAGGER_MS)
            progress.animateTo(1f, tween(260))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 18.dp.toPx()
    }
}

// Skąd "wyrasta" otwierana aplikacja. Ostatnie dotknięcie ekranu (zapisuje MainActivity) albo dokładne
// położenie ikony (zapisuje AppTile tuż przed uruchomieniem). Współrzędne ekranu w pikselach.
object LaunchOrigin {
    private var view: WeakReference<View>? = null
    private var touch: Offset? = null
    private var touchAt = 0L
    private var iconRect: Rect? = null
    private var iconAt = 0L

    fun attach(root: View) {
        view = WeakReference(root)
    }

    fun touched(x: Float, y: Float) {
        touch = Offset(x, y)
        touchAt = System.currentTimeMillis()
    }

    // Tylko świeże dotknięcie: uruchomienie bez dotyku (reguła, klawiatura) nie "wyrasta" ze starego miejsca.
    fun lastTouch(): Offset? = touch?.takeIf { System.currentTimeMillis() - touchAt < 1500 }

    fun icon(left: Int, top: Int, width: Int, height: Int) {
        iconRect = Rect(left, top, left + width, top + height)
        iconAt = System.currentTimeMillis()
    }

    // Prostokąt źródła: ikona (jeśli zapisana przed chwilą) albo kwadrat wokół miejsca dotknięcia.
    fun sourceBounds(): Rect? {
        val icon = iconRect?.takeIf { System.currentTimeMillis() - iconAt < 1500 }
        if (icon != null) return icon
        val t = lastTouch() ?: return null
        val half = 64
        return Rect(t.x.toInt() - half, t.y.toInt() - half, t.x.toInt() + half, t.y.toInt() + half)
    }

    // Systemowa animacja "wyrastania" okna aplikacji z tego prostokąta (jak w Pixel Launcherze).
    fun options(): Bundle? {
        val root = view?.get() ?: return null
        val r = sourceBounds() ?: return null
        val location = IntArray(2)
        root.getLocationOnScreen(location) // współrzędne w makeClipReveal są względem widoku
        return runCatching {
            ActivityOptions.makeClipRevealAnimation(root, r.left - location[0], r.top - location[1], r.width(), r.height()).toBundle()
        }.getOrNull()
    }
}
