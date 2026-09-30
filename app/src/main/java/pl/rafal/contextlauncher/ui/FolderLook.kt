package pl.rafal.contextlauncher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.db.FolderEntity

// Symbole folderów. Celowo WYPEŁNIONE kształty i inne motywy niż ikony trybów (te są konturami),
// żeby na karcie od razu było widać, co jest trybem, a co folderem.
enum class FolderIcon(val key: String, @DrawableRes val res: Int, val label: String, val extra: Boolean = false) {
    FOLDER("folder", R.drawable.ic_folder_folder, "Folder"),
    CHAT("chat", R.drawable.ic_folder_chat, "Komunikatory"),
    PEOPLE("people", R.drawable.ic_folder_people, "Ludzie"),
    PHOTO("photo", R.drawable.ic_folder_photo, "Zdjęcia"),
    VIDEO("video", R.drawable.ic_folder_video, "Wideo"),
    HEADSET("headset", R.drawable.ic_folder_headset, "Audio"),
    DOC("doc", R.drawable.ic_folder_doc, "Dokumenty"),
    BOOKMARK("bookmark", R.drawable.ic_folder_bookmark, "Zakładka"),
    CARD("card", R.drawable.ic_folder_card, "Finanse"),
    TOOLS("tools", R.drawable.ic_folder_tools, "Narzędzia"),
    CODE("code", R.drawable.ic_folder_code, "Kod"),
    CLOUD("cloud", R.drawable.ic_folder_cloud, "Chmura"),
    PIN("pin", R.drawable.ic_folder_pin, "Miejsca"),
    SHIELD("shield", R.drawable.ic_folder_shield, "Bezpieczeństwo"),
    BOLT("bolt", R.drawable.ic_folder_bolt, "Energia"),
    GIFT("gift", R.drawable.ic_folder_gift, "Prezent"),
    // Rozszerzony zestaw — widoczny po rozwinięciu "Więcej symboli", żeby okno nie było przeładowane.
    // Najpierw pieniądze i zakupy, potem reszta.
    X_DOLLAR("dollar", R.drawable.ic_folder_dollar, "Dolar", extra = true),
    X_EURO("euro", R.drawable.ic_folder_euro, "Euro", extra = true),
    X_COINS("coins", R.drawable.ic_folder_coins, "Monety", extra = true),
    X_BANKNOTE("banknote", R.drawable.ic_folder_banknote, "Gotówka", extra = true),
    X_PIGGY("piggy", R.drawable.ic_folder_piggy, "Oszczędności", extra = true),
    X_RECEIPT("receipt", R.drawable.ic_folder_receipt, "Rachunki", extra = true),
    X_TAG("tag", R.drawable.ic_folder_tag, "Promocje", extra = true),
    X_TREND("trend", R.drawable.ic_folder_trend, "Inwestycje", extra = true),
    X_STORE("store", R.drawable.ic_folder_store, "Sklep", extra = true),
    X_FUEL("fuel", R.drawable.ic_folder_fuel, "Paliwo", extra = true),
    X_CLAPPER("clapper", R.drawable.ic_folder_clapper, "Filmy", extra = true),
    X_WIFI("wifi", R.drawable.ic_folder_wifi, "Sieć", extra = true),
    X_HOME("home", R.drawable.ic_folder_home, "Dom", extra = true),
    X_WORK("work", R.drawable.ic_folder_work, "Praca", extra = true),
    X_SCHOOL("school", R.drawable.ic_folder_school, "Nauka", extra = true),
    X_BOOK("book", R.drawable.ic_folder_book, "Książki", extra = true),
    X_MUSIC("music", R.drawable.ic_folder_music, "Muzyka", extra = true),
    X_MIC("mic", R.drawable.ic_folder_mic, "Podcasty", extra = true),
    X_TV("tv", R.drawable.ic_folder_tv, "Telewizja", extra = true),
    X_CAMERA("camera", R.drawable.ic_folder_camera, "Aparat", extra = true),
    X_GAMEPAD("gamepad", R.drawable.ic_folder_gamepad, "Gry", extra = true),
    X_FITNESS("fitness", R.drawable.ic_folder_fitness, "Trening", extra = true),
    X_HEART("heart", R.drawable.ic_folder_heart, "Serce", extra = true),
    X_HEALTH("health", R.drawable.ic_folder_health, "Zdrowie", extra = true),
    X_FOOD("food", R.drawable.ic_folder_food, "Jedzenie", extra = true),
    X_CAFE("cafe", R.drawable.ic_folder_cafe, "Kawa", extra = true),
    X_CART("cart", R.drawable.ic_folder_cart, "Zakupy", extra = true),
    X_BAG("bag", R.drawable.ic_folder_bag, "Sklepy", extra = true),
    X_WALLET("wallet", R.drawable.ic_folder_wallet, "Portfel", extra = true),
    X_BANK("bank", R.drawable.ic_folder_bank, "Bank", extra = true),
    X_CHART("chart", R.drawable.ic_folder_chart, "Statystyki", extra = true),
    X_FLIGHT("flight", R.drawable.ic_folder_flight, "Podróże", extra = true),
    X_CAR("car", R.drawable.ic_folder_car, "Auto", extra = true),
    X_BUS("bus", R.drawable.ic_folder_bus, "Komunikacja", extra = true),
    X_MAIL("mail", R.drawable.ic_folder_mail, "Poczta", extra = true),
    X_PHONE("phone", R.drawable.ic_folder_phone, "Telefon", extra = true),
    X_EVENT("event", R.drawable.ic_folder_event, "Kalendarz", extra = true),
    X_BELL("bell", R.drawable.ic_folder_bell, "Powiadomienia", extra = true),
    X_BULB("bulb", R.drawable.ic_folder_bulb, "Pomysły", extra = true),
    X_EDIT("edit", R.drawable.ic_folder_edit, "Notatki", extra = true),
    X_SEARCH("search", R.drawable.ic_folder_search, "Szukanie", extra = true),
    X_DOWNLOAD("download", R.drawable.ic_folder_download, "Pobrane", extra = true),
    X_KEY("key", R.drawable.ic_folder_key, "Hasła", extra = true),
    X_LOCK("lock", R.drawable.ic_folder_lock, "Prywatne", extra = true),
    X_APPS("apps", R.drawable.ic_folder_apps, "Aplikacje", extra = true),
    X_SMILE("smile", R.drawable.ic_folder_smile, "Rozrywka", extra = true),
    X_MOON("moon", R.drawable.ic_folder_moon, "Noc", extra = true),
    X_ECO("eco", R.drawable.ic_folder_eco, "Natura", extra = true),
    X_FLAG("flag", R.drawable.ic_folder_flag, "Cele", extra = true),
    X_STAR("star", R.drawable.ic_folder_star, "Ulubione", extra = true),
    X_CIRCLE("circle", R.drawable.ic_folder_circle, "Koło", extra = true),
    X_SQUARE("square", R.drawable.ic_folder_square, "Kwadrat", extra = true),
    X_TRIANGLE("triangle", R.drawable.ic_folder_triangle, "Trójkąt", extra = true),
    X_HEXAGON("hexagon", R.drawable.ic_folder_hexagon, "Sześciokąt", extra = true),
    X_DIAMOND("diamond", R.drawable.ic_folder_diamond, "Romb", extra = true),
    ;

    companion object {
        // null = brak symbolu, czyli miniatura z ikon aplikacji (domyślny wygląd).
        fun of(key: String?): FolderIcon? = entries.firstOrNull { it.key == key }
    }
}

// Klucz wyglądu folderu (kolumna icon) może oznaczać trzy rzeczy:
//   "chat", "home"…   → symbol z FolderIcon,
//   "t:A", "t:7"      → litera lub cyfra,
//   "a:<klucz apki>"  → ikona zainstalowanej aplikacji (np. folder "Instagram" z prawdziwą ikoną Instagrama).
const val TEXT_ICON = "t:"
const val APP_ICON = "a:"

// Skąd FolderBadge weźmie ikonę aplikacji po kluczu — podaje ją ekran główny (CompositionLocal ≈ wstrzykiwanie zależności).
val LocalAppLookup = staticCompositionLocalOf<(String) -> AppInfo?> { { null } }

// Kolory tła folderu — przygaszone, żeby nie "krzyczały" na ciemnej tapecie.
val FolderColors: List<Long> = listOf(
    0xFF3A6EA5, 0xFF2F8F83, 0xFF4F8A3C, 0xFF9A7B2F, 0xFFB5652E, 0xFFA8444B,
    0xFF8E4A8F, 0xFF5B53A6, 0xFF4A5563, 0xFF2B2F36, 0xFFD9D4C7,
    0xFF1F8A70, 0xFF7C3AED, 0xFFDB2777, 0xFFCA8A04, 0xFF0E7490, 0xFF65A30D, 0xFF111111,
)

// Wygląd folderu w jednym miejscu: kolorowe tło w kształcie ikon z Ustawień + symbol / litera / ikona aplikacji / miniatura.
@Composable
fun FolderBadge(folder: FolderEntity?, preview: List<AppInfo>, size: Dp, modifier: Modifier = Modifier, grid: Int = 2) =
    FolderBadge(folder?.icon, folder?.color, preview, size, modifier, grid)

@Composable
fun FolderBadge(iconKey: String?, color: Long?, preview: List<AppInfo>, size: Dp, modifier: Modifier = Modifier, grid: Int = 2) {
    val bg = color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant
    val fg = if (bg.luminance() > 0.5f) Color(0xFF1A1206) else Color.White
    val symbol = FolderIcon.of(iconKey)
    val appIcon = iconKey?.takeIf { it.startsWith(APP_ICON) }?.let { LocalAppLookup.current(it.removePrefix(APP_ICON)) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(LocalIconShape.current.shape(size))
            .background(bg),
    ) {
        when {
            iconKey != null && iconKey.startsWith(TEXT_ICON) -> {
                val text = iconKey.removePrefix(TEXT_ICON)
                // Im dłuższy napis, tym mniejsza czcionka: 1 znak = połowa znaczka, 3 znaki = ok. 30%.
                val scale = when (text.length) { 1 -> 0.5f; 2 -> 0.38f; else -> 0.29f }
                Text(
                    text,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * scale).sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            appIcon != null -> Image(appIcon.icon, contentDescription = null, modifier = Modifier.size(size * 0.7f))
            symbol != null || preview.isEmpty() -> Icon(
                // Pusty folder bez symbolu też dostaje symbol "folder", żeby nie był pustym kwadratem.
                painter = painterResource((symbol ?: FolderIcon.FOLDER).res),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(size * 0.56f),
            )
            else -> {
                // 2×2: cztery większe ikony, 3×3: dziewięć mniejszych (więcej widać bez otwierania).
                val cell = size * (if (grid == 3) 0.25f else 0.34f)
                val gap = size * (if (grid == 3) 0.035f else 0.05f)
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    preview.take(grid * grid).chunked(grid).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { app -> Image(app.icon, contentDescription = null, modifier = Modifier.size(cell)) }
                        }
                    }
                }
            }
        }
    }
}

// Gotowe napisy do szybkiego wyboru (własny napis wpiszesz obok, do 3 znaków).
private val TextPresets = listOf("zł", "$", "€", "£", "₿", "%", "24h", "VIP", "TV", "PDF", "AI", "GPS", "WWW", "SOS", "123", "ABC", "♥", "★", "♪", "✓")

// Okno "Wygląd folderu": podgląd, symbol (podstawowe od razu, reszta w rozwijanych sekcjach) i kolor.
@Composable
fun FolderLookDialog(
    folder: FolderEntity,
    preview: List<AppInfo>,
    onSave: (icon: String?, color: Long?) -> Unit,
    onDismiss: () -> Unit,
    allApps: List<AppInfo> = emptyList(), // do sekcji "Ikona aplikacji"
) {
    var icon by remember { mutableStateOf(folder.icon) }
    var color by remember { mutableStateOf(folder.color) }
    // Sekcja z aktualnym wyborem otwiera się sama, żeby było widać, co jest zaznaczone.
    var moreOpen by remember { mutableStateOf(FolderIcon.of(folder.icon)?.extra == true) }
    var lettersOpen by remember { mutableStateOf(folder.icon?.startsWith(TEXT_ICON) == true) }
    var customText by remember { mutableStateOf(folder.icon?.takeIf { it.startsWith(TEXT_ICON) }?.removePrefix(TEXT_ICON).orEmpty()) }
    var appPickOpen by remember { mutableStateOf(false) }
    val lookup = LocalAppLookup.current

    fun label(key: String?): String = when {
        key == null -> "Miniatura aplikacji"
        key.startsWith(TEXT_ICON) -> "Napis „${key.removePrefix(TEXT_ICON)}”"
        key.startsWith(APP_ICON) -> lookup(key.removePrefix(APP_ICON))?.label ?: "Ikona aplikacji"
        else -> FolderIcon.of(key)?.label ?: key
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Wygląd „${folder.name}”") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FolderBadge(icon, color, preview, 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(label(icon), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(16.dp))
                Text("Symbol", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                // null na początku = "bez symbolu" (miniatura z ikon aplikacji).
                BadgeGrid(listOf<String?>(null) + FolderIcon.entries.filter { !it.extra }.map { it.key }, icon, color, preview) { icon = it }

                SectionToggle("Więcej symboli (${FolderIcon.entries.count { it.extra }})", moreOpen) { moreOpen = !moreOpen }
                if (moreOpen) BadgeGrid(FolderIcon.entries.filter { it.extra }.map { it.key }, icon, color, preview) { icon = it }

                SectionToggle("Własny napis (do 3 znaków)", lettersOpen) { lettersOpen = !lettersOpen }
                if (lettersOpen) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { v ->
                            // take(3) liczy znaki UTF-16; emoji ze znakami złożonymi mogą się uciąć — to akceptowalne.
                            customText = v.replace("\n", "").take(3)
                            icon = if (customText.isBlank()) null else TEXT_ICON + customText
                        },
                        placeholder = { Text("np. zł, VIP, 24h") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    BadgeGrid(TextPresets.map { TEXT_ICON + it }, icon, color, preview) {
                        icon = it
                        customText = it?.removePrefix(TEXT_ICON).orEmpty()
                    }
                }

                if (allApps.isNotEmpty()) {
                    SectionToggle("Ikona aplikacji…", false) { appPickOpen = true }
                }

                Spacer(Modifier.height(16.dp))
                Text("Kolor tła", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ColorSwatches(colors = FolderColors, selected = color, onSelect = { color = it }, allowNone = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(icon, color) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )

    if (appPickOpen) {
        AppGridPickerDialog(
            allApps = allApps,
            title = "Ikona aplikacji dla folderu",
            single = true,
            onConfirm = { chosen ->
                chosen.firstOrNull()?.let { icon = APP_ICON + it.key }
                appPickOpen = false
            },
            onDismiss = { appPickOpen = false },
        )
    }
}

@Composable
private fun SectionToggle(label: String, open: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.primary)
    }
}

// Siatka znaczków do wyboru; liczba kolumn dopasowuje się do szerokości okna.
@Composable
private fun BadgeGrid(keys: List<String?>, selected: String?, color: Long?, preview: List<AppInfo>, onSelect: (String?) -> Unit) {
    BoxWithConstraints {
        val columns = ((maxWidth + 6.dp) / 44.dp).toInt().coerceIn(4, 8)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        val isSelected = key == selected
                        FolderBadge(
                            iconKey = key,
                            color = if (isSelected) color else 0xFF4A4E56,
                            preview = preview,
                            size = 38.dp,
                            modifier = Modifier
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { onSelect(key) }
                                .semantics { contentDescription = key ?: "Miniatura aplikacji" },
                        )
                    }
                }
            }
        }
    }
}

// Rozmiary widżetu folderu w komórkach siatki (8 kolumn). Opis mówi, co widać w danym rozmiarze.
private data class FolderSizeOption(val w: Int, val h: Int, val label: String, val hint: String)

private val FolderSizes = listOf(
    FolderSizeOption(1, 1, "Mini", "sam symbol"),
    FolderSizeOption(2, 2, "Jak ikona", "symbol i nazwa"),
    FolderSizeOption(4, 2, "Pasek", "symbol + rząd aplikacji"),
    FolderSizeOption(4, 4, "Średni", "siatka aplikacji i podfolderów"),
    FolderSizeOption(8, 4, "Szeroki", "więcej aplikacji w rzędzie"),
    FolderSizeOption(8, 6, "Duży", "cała zawartość na widoku"),
)

// Wybór rozmiaru folderu na karcie aktywnego trybu. current = obecny rozmiar, jeśli folder już tam leży.
@Composable
fun FolderSizeDialog(
    folderName: String,
    modeName: String,
    current: Pair<Int, Int>?,
    onPick: (w: Int, h: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (current == null) "„$folderName” na kartę $modeName" else "Rozmiar „$folderName” na karcie") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FolderSizes.forEach { option ->
                    val isCurrent = current == option.w to option.h
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .clickable { onPick(option.w, option.h) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        SizeGlyph(option.w, option.h)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${option.label}  ${option.w}×${option.h}", fontWeight = FontWeight.SemiBold)
                            Text(
                                option.hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isCurrent) Text("teraz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// Mały prostokąt w proporcji rozmiaru na tle "karty" 8 kolumn — od razu widać, ile miejsca zajmie.
@Composable
private fun SizeGlyph(w: Int, h: Int) {
    val unit = 5.dp
    Box(
        Modifier
            .size(unit * 8, unit * 6)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .size(unit * w, unit * h)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
