package pl.rafal.contextlauncher.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import pl.rafal.contextlauncher.data.db.CardItemEntity
import org.json.JSONObject
import pl.rafal.contextlauncher.layout.GridRect

// data class to odpowiednik record z C#: równość po wartościach, toString i copy() za darmo.
// val = właściwość tylko do odczytu (jak { get; init; }).
data class AppInfo(
    val label: String,
    val component: ComponentName, // pakiet + klasa aktywności: jednoznaczny adres ekranu startowego aplikacji
    val user: UserHandle,         // profil użytkownika: osobisty albo służbowy
    val userSerial: Long,         // ten sam profil jako liczba, którą da się zapisać w bazie
    val icon: ImageBitmap,
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED, // kategoria ze sklepu: gra, wideo, społecznościowa…
) {
    // Właściwość wyliczana, jak "public string PackageName => Component.PackageName;"
    val packageName: String get() = component.packageName

    // Klucz do list w Compose. Ta sama aplikacja może wystąpić w dwóch profilach, stąd profil w kluczu.
    val key: String get() = "${component.flattenToString()}#$userSerial"

    // Klucz całej aplikacji (bez aktywności) — po nim blokujemy i ukrywamy w trybach.
    val appKey: String get() = appKey(component.packageName, userSerial)

    fun matches(item: CardItemEntity): Boolean = matches(item.packageName, item.className, item.userSerial)

    // Przeciążenie metody, jak w C#: ta sama nazwa, inne parametry.
    fun matches(packageName: String, className: String, userSerial: Long): Boolean =
        packageName == component.packageName &&
            className == component.className &&
            userSerial == this.userSerial
}

fun appKey(packageName: String, userSerial: Long) = "$packageName#$userSerial"

// Funkcja rozszerzająca (extension method z C#): dokleja metodę do klasy encji bez jej zmieniania.
fun CardItemEntity.toRect(): GridRect = GridRect(x, y, w, h)

// Element karty: aplikacja albo widżet. sealed interface = zamknięta rodzina typów;
// kompilator wie, że innych nie ma, więc "when" nie potrzebuje gałęzi "else" (jak switch na typach w C#).
sealed interface CardElement {
    val item: CardItemEntity
    val rect: GridRect get() = item.toRect()
}

// Aplikacja przypięta do karty: wiersz z bazy (pozycja, rozmiar) + dane do narysowania.
data class CardApp(override val item: CardItemEntity, val app: AppInfo) : CardElement

// Widżet systemowy na karcie.
data class CardWidget(override val item: CardItemEntity, val appWidgetId: Int) : CardElement

// Rodzaje własnych widżetów. enum class = enum z C#, ale z polami i konstruktorem.
enum class CustomWidgetKind(val title: String, val description: String, val w: Int, val h: Int) {
    DUAL_CLOCK("Dwa zegary", "Czas tutaj i w drugiej strefie", 4, 3),
    MODE_NOTE("Notatka trybu", "Krótki tekst widoczny na karcie", 4, 3),
    HANDY("Pod ręką", "Najważniejsze przypięte rzeczy trybu", 8, 3),
    FOLDER("Folder", "Folder aplikacji z podfolderami", 4, 4),
    STICKER("Naklejka", "Obrazek bez tła, np. naklejka albo zdjęcie", 3, 3),
    CLOCK("Zegar", "Duża godzina i data", 4, 4),
    WEATHER("Pogoda", "Temperatura i prognoza na kilka dni (Open-Meteo)", 4, 4),
    MODE_DIAL("Tarcza trybów", "Wszystkie tryby wokół przycisku +", 8, 6),
    GLANCE("W skrócie", "Godzina, data, najbliższe wydarzenie, pogoda i budzik w jednym", 8, 3),
    TODAY("Dziś", "Wydarzenia na dziś i jutro z kalendarza", 4, 4),
    COUNTDOWN("Odliczanie", "Ile dni zostało do ważnej daty", 4, 3),
    CHECKLIST("Lista", "Zadania albo zakupy do odhaczania", 4, 4),
    QUICK_TOGGLES("Szybkie przełączniki", "Latarka, Nie przeszkadzać, dźwięk, obrót, internet, Bluetooth", 8, 2),
    CONTACTS("Ulubione kontakty", "Kontakty z gwiazdką: telefon i SMS jednym dotknięciem", 8, 2),
}

// Własny widżet na karcie. Ustawienia czytamy z JSON-a (org.json jest wbudowany w Androida).
data class CardCustomWidget(override val item: CardItemEntity, val kind: CustomWidgetKind) : CardElement {
    val config: JSONObject get() = runCatching { JSONObject(item.config ?: "{}") }.getOrDefault(JSONObject())
}
