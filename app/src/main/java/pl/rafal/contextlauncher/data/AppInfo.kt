package pl.rafal.contextlauncher.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.net.Uri
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
    // Skrót aplikacji (np. "Nowy czat" z komunikatora). Wtedy to nie ekran startowy, tylko akcja wewnątrz aplikacji;
    // reszta launchera traktuje go jak ikonę (karta, foldery, przeciąganie), różni się tylko uruchamianie.
    val shortcutId: String? = null,
) {
    // Właściwość wyliczana, jak "public string PackageName => Component.PackageName;"
    val packageName: String get() = component.packageName

    // Klucz do list w Compose. Ta sama aplikacja może wystąpić w dwóch profilach, stąd profil w kluczu.
    val key: String get() =
        if (shortcutId != null) "$SHORTCUT_KEY${component.packageName}/${Uri.encode(shortcutId)}#$userSerial"
        else "${component.flattenToString()}#$userSerial"

    val isShortcut: Boolean get() = shortcutId != null

    // Klucz całej aplikacji (bez aktywności) — po nim blokujemy i ukrywamy w trybach.
    val appKey: String get() = appKey(component.packageName, userSerial)

    fun matches(item: CardItemEntity): Boolean =
        if (shortcutId != null) {
            item.type == CardItemEntity.TYPE_SHORTCUT && item.packageName == component.packageName &&
                item.className == shortcutId && item.userSerial == userSerial
        } else {
            item.type == CardItemEntity.TYPE_APP && matches(item.packageName, item.className, item.userSerial)
        }

    // Wiersz karty dla tej ikony (aplikacji albo skrótu) w podanym miejscu.
    fun cardItem(modeId: Long, rect: GridRect, page: Int = 0): CardItemEntity = CardItemEntity(
        modeId = modeId,
        type = if (shortcutId != null) CardItemEntity.TYPE_SHORTCUT else CardItemEntity.TYPE_APP,
        packageName = component.packageName,
        className = shortcutId ?: component.className,
        userSerial = userSerial,
        x = rect.x, y = rect.y, w = rect.w, h = rect.h,
        page = page,
    )

    // Przeciążenie metody, jak w C#: ta sama nazwa, inne parametry.
    fun matches(packageName: String, className: String, userSerial: Long): Boolean =
        packageName == component.packageName &&
            className == component.className &&
            userSerial == this.userSerial
}

fun appKey(packageName: String, userSerial: Long) = "$packageName#$userSerial"

const val SHORTCUT_KEY = "s:" // przedrostek klucza skrótu (klucze aplikacji zaczynają się od nazwy pakietu)

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
    HANDY("OnHand", "Najważniejsze przypięte rzeczy trybu", 8, 3),
    FOLDER("Folder z szuflady", "Folder z zakładki Foldery (z podfolderami), wspólny dla wszystkich trybów", 4, 4),
    STICKER("Naklejka", "Obrazek bez tła, np. naklejka albo zdjęcie", 3, 3),
    CLOCK("Zegar", "Duża godzina i data", 4, 4),
    WEATHER("Pogoda", "Temperatura i prognoza na kilka dni (Open-Meteo)", 4, 4),
    MODE_DIAL("Tryby", "Mały: ikona i nazwa trybu (zamiast nagłówka). Duży: tarcza wszystkich trybów", 8, 6),
    GLANCE("W skrócie", "Godzina, data, najbliższe wydarzenie, pogoda i budzik w jednym", 8, 3),
    TODAY("Dziś", "Wydarzenia na dziś i jutro z kalendarza", 4, 4),
    COUNTDOWN("Odliczanie", "Ile dni zostało do ważnej daty", 4, 3),
    CHECKLIST("Lista", "Zadania albo zakupy do odhaczania", 4, 4),
    QUICK_TOGGLES("Szybkie przełączniki", "Latarka, Nie przeszkadzać, dźwięk, obrót, internet, Bluetooth", 8, 2),
    CONTACTS("Ulubione kontakty", "Kontakty z gwiazdką: telefon i SMS jednym dotknięciem", 8, 2),
    // Powstaje też z upuszczenia ikony na ikonę (zawartość w config, patrz CardFolder.kt).
    CARD_FOLDER("Pusty folder", "Folder tylko dla tego trybu — aplikacje dodasz po otwarciu (albo upuść ikonę na ikonę)", 2, 2),
    // Nie ma go na liście widżetów: powstaje z upuszczenia widżetu na widżet w edycji (patrz CardStacks.kt).
    STACK("Stos widżetów", "Kilka widżetów w jednym miejscu — przesuwasz palcem w górę i w dół", 4, 4),
}

// Własny widżet na karcie. Ustawienia czytamy z JSON-a (org.json jest wbudowany w Androida).
data class CardCustomWidget(override val item: CardItemEntity, val kind: CustomWidgetKind) : CardElement {
    val config: JSONObject get() = runCatching { JSONObject(item.config ?: "{}") }.getOrDefault(JSONObject())
}
