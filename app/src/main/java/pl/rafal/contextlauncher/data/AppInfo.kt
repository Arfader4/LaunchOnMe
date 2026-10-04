package pl.rafal.contextlauncher.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.UserHandle
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
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
enum class CustomWidgetKind(@StringRes private val titleRes: Int, @StringRes private val descriptionRes: Int, val w: Int, val h: Int) {
    DUAL_CLOCK(R.string.data_widget_dual_clock_title, R.string.data_widget_dual_clock_desc, 4, 3),
    MODE_NOTE(R.string.data_widget_mode_note_title, R.string.data_widget_mode_note_desc, 4, 3),
    HANDY(R.string.data_widget_handy_title, R.string.data_widget_handy_desc, 8, 3),
    FOLDER(R.string.data_widget_folder_title, R.string.data_widget_folder_desc, 4, 4),
    STICKER(R.string.data_widget_sticker_title, R.string.data_widget_sticker_desc, 3, 3),
    CLOCK(R.string.data_widget_clock_title, R.string.data_widget_clock_desc, 4, 4),
    WEATHER(R.string.data_widget_weather_title, R.string.data_widget_weather_desc, 4, 4),
    MODE_DIAL(R.string.data_widget_mode_dial_title, R.string.data_widget_mode_dial_desc, 8, 6),
    GLANCE(R.string.data_widget_glance_title, R.string.data_widget_glance_desc, 8, 3),
    TODAY(R.string.data_widget_today_title, R.string.data_widget_today_desc, 4, 4),
    COUNTDOWN(R.string.data_widget_countdown_title, R.string.data_widget_countdown_desc, 4, 3),
    CHECKLIST(R.string.data_widget_checklist_title, R.string.data_widget_checklist_desc, 4, 4),
    QUICK_TOGGLES(R.string.data_widget_quick_toggles_title, R.string.data_widget_quick_toggles_desc, 8, 2),
    CONTACTS(R.string.data_widget_contacts_title, R.string.data_widget_contacts_desc, 8, 2),
    // Powstaje też z upuszczenia ikony na ikonę (zawartość w config, patrz CardFolder.kt).
    CARD_FOLDER(R.string.data_widget_card_folder_title, R.string.data_widget_card_folder_desc, 2, 2),
    // Nie ma go na liście widżetów: powstaje z upuszczenia widżetu na widżet w edycji (patrz CardStacks.kt).
    STACK(R.string.data_widget_stack_title, R.string.data_widget_stack_desc, 4, 4);

    val title: String get() = AppText.get(titleRes)
    val description: String get() = AppText.get(descriptionRes)
}

// Własny widżet na karcie. Ustawienia czytamy z JSON-a (org.json jest wbudowany w Androida).
data class CardCustomWidget(override val item: CardItemEntity, val kind: CustomWidgetKind) : CardElement {
    val config: JSONObject get() = runCatching { JSONObject(item.config ?: "{}") }.getOrDefault(JSONObject())
}
