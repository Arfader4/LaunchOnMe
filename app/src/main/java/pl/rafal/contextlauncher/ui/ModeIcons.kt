package pl.rafal.contextlauncher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.db.ModeEntity

// Katalog ikon trybów. Grafiki są w res/drawable, więc ten sam symbol pokażą launcher i kafelek szybkich ustawień.
enum class ModeIcon(val key: String, @DrawableRes val res: Int, val label: String, val extra: Boolean = false) {
    WORK("work", R.drawable.ic_mode_work, "Teczka"),
    HOME("home", R.drawable.ic_mode_home, "Dom"),
    TRAVEL("travel", R.drawable.ic_mode_travel, "Podróż"),
    STUDY("study", R.drawable.ic_mode_study, "Książka"),
    SPORT("sport", R.drawable.ic_mode_sport, "Trening"),
    NIGHT("night", R.drawable.ic_mode_night, "Księżyc"),
    SUN("sun", R.drawable.ic_mode_sun, "Słońce"),
    HEART("heart", R.drawable.ic_mode_heart, "Serce"),
    STAR("star", R.drawable.ic_mode_star, "Gwiazda"),
    MUSIC("music", R.drawable.ic_mode_music, "Muzyka"),
    CAR("car", R.drawable.ic_mode_car, "Auto"),
    CART("cart", R.drawable.ic_mode_cart, "Zakupy"),
    COFFEE("coffee", R.drawable.ic_mode_coffee, "Kawa"),
    LEAF("leaf", R.drawable.ic_mode_leaf, "Natura"),
    GAME("game", R.drawable.ic_mode_game, "Gry"),
    CAMERA("camera", R.drawable.ic_mode_camera, "Aparat"),
    // Rozszerzony zestaw (konturowy, jak podstawowe — foldery mają osobne, wypełnione symbole).
    X_BED("bed", R.drawable.ic_mode_bed, "Sen", extra = true),
    X_CODE("code", R.drawable.ic_mode_code, "Programowanie", extra = true),
    X_COCKTAIL("cocktail", R.drawable.ic_mode_cocktail, "Impreza", extra = true),
    X_BEER("beer", R.drawable.ic_mode_beer, "Pub", extra = true),
    X_BIKE("bike", R.drawable.ic_mode_bike, "Rower", extra = true),
    X_WAVES("waves", R.drawable.ic_mode_waves, "Woda", extra = true),
    X_MOUNTAIN("mountain", R.drawable.ic_mode_mountain, "Góry", extra = true),
    X_TENT("tent", R.drawable.ic_mode_tent, "Biwak", extra = true),
    X_BEACH("beach", R.drawable.ic_mode_beach, "Plaża", extra = true),
    X_SNOW("snow", R.drawable.ic_mode_snow, "Zima", extra = true),
    X_PAW("paw", R.drawable.ic_mode_paw, "Zwierzęta", extra = true),
    X_PILL("pill", R.drawable.ic_mode_pill, "Zdrowie", extra = true),
    X_LOTUS("lotus", R.drawable.ic_mode_lotus, "Relaks", extra = true),
    X_CHAT("chat", R.drawable.ic_mode_chat, "Rozmowy", extra = true),
    X_PHONE("phone", R.drawable.ic_mode_phone, "Telefon", extra = true),
    X_LAPTOP("laptop", R.drawable.ic_mode_laptop, "Komputer", extra = true),
    X_BRUSH("brush", R.drawable.ic_mode_brush, "Sztuka", extra = true),
    X_HAMMER("hammer", R.drawable.ic_mode_hammer, "Majsterkowanie", extra = true),
    X_SHIELD("shield", R.drawable.ic_mode_shield, "Skupienie", extra = true),
    X_CHART("chart", R.drawable.ic_mode_chart, "Finanse", extra = true),
    X_FAMILY("family", R.drawable.ic_mode_family, "Rodzina", extra = true),
    X_BELL("bell", R.drawable.ic_mode_bell, "Przypomnienia", extra = true),
    X_RAIN("rain", R.drawable.ic_mode_rain, "Deszcz", extra = true),
    X_MOVIE("movie", R.drawable.ic_mode_movie, "Kino", extra = true),
    X_GIFT("gift", R.drawable.ic_mode_gift, "Święta", extra = true),
    X_CLOCK("clock", R.drawable.ic_mode_clock, "Rutyna", extra = true),
    X_CALENDAR("calendar", R.drawable.ic_mode_calendar, "Plan", extra = true),
    X_GRAD("grad", R.drawable.ic_mode_grad, "Uczelnia", extra = true),
    X_BUS("bus", R.drawable.ic_mode_bus, "Komunikacja", extra = true),
    X_TRAIN("train", R.drawable.ic_mode_train, "Pociąg", extra = true),
    X_BOLT("bolt", R.drawable.ic_mode_bolt, "Energia", extra = true),
    X_GLOBE("globe", R.drawable.ic_mode_globe, "Świat", extra = true),
    X_FLAG("flag", R.drawable.ic_mode_flag, "Cele", extra = true),
    X_HEADPHONES("headphones", R.drawable.ic_mode_headphones, "Słuchawki", extra = true),
    X_READING("reading", R.drawable.ic_mode_reading, "Czytanie", extra = true),
    X_BABY("baby", R.drawable.ic_mode_baby, "Dziecko", extra = true),
    X_PIZZA("pizza", R.drawable.ic_mode_pizza, "Jedzenie", extra = true),
    ;

    companion object {
        fun of(key: String?): ModeIcon = entries.firstOrNull { it.key == key } ?: STAR
    }
}

// Kolor tła znaczka trybu dopasowany do motywu. Symbol ma jeden kolor na cały motyw: w ciemnym zawsze jasny,
// w jasnym zawsze ciemny — więc to tło się dostosowuje. Zachowujemy odcień i nasycenie (HSL), zmieniamy tylko jasność,
// aż kontrast z symbolem będzie dobry (luminancja jak w WCAG). Dotyczy też kolorów własnych z palety HSV.
fun modeBadgeColor(color: Long, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toInt(), hsl) // Long ARGB → Int, jak (int)kolor w C#
    fun lum() = Color(ColorUtils.HSLToColor(hsl)).luminance()
    if (dark) {
        // Jasny symbol: tło o luminancji ≤ 0.14 (kontrast ok. 5:1 z symbolem 0xFFF4F5F7).
        while (lum() > 0.14f && hsl[2] > 0.05f) hsl[2] -= 0.02f
    } else {
        // Ciemny symbol: tło jasne (pastel), luminancja ≥ ok. 0.5.
        while (lum() < 0.5f && hsl[2] < 0.97f) hsl[2] += 0.02f
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

// Kolor symbolu na znaczku trybu (i na podglądzie koloru w wyborze).
fun modeBadgeSymbol(dark: Boolean): Color = if (dark) Color(0xFFF4F5F7) else Color(0xFF17181C)

// Znaczek trybu: symbol na kolorowym tle. Tło dopasowane do motywu (modeBadgeColor), symbol jasny/ciemny jak motyw.
@Composable
fun ModeBadge(icon: ModeIcon, color: Long, size: Dp = 28.dp, modifier: Modifier = Modifier, shape: Shape? = null) {
    val dark = isThemeDark()
    val bg = remember(color, dark) { modeBadgeColor(color, dark) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape ?: LocalIconShape.current.shape(size)) // kształt z ustawień, chyba że wywołujący wymusza własny
            .background(bg),
    ) {
        Icon(
            painter = painterResource(icon.res),
            contentDescription = null,
            tint = modeBadgeSymbol(dark),
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

@Composable
fun ModeBadge(mode: ModeEntity, size: Dp = 28.dp, modifier: Modifier = Modifier, shape: Shape? = null) =
    ModeBadge(ModeIcon.of(mode.icon), mode.color, size, modifier, shape)

// Siatka wyboru ikony; liczba kolumn dopasowuje się do szerokości (okno dialogowe jest węższe niż arkusz).
// Podstawowe ikony od razu, rozszerzony zestaw po dotknięciu "Więcej ikon" (otwiera się sam, gdy wybrana jest któraś z nich).
@Composable
fun IconPicker(selected: ModeIcon, color: Long, onSelect: (ModeIcon) -> Unit) {
    var more by remember { mutableStateOf(selected.extra) }
    Column {
        BoxWithConstraints {
            val columns = ((maxWidth + 6.dp) / 40.dp).toInt().coerceIn(4, 8) // 34 dp ikona + 6 dp odstępu
            IconGrid(columns, ModeIcon.entries.filter { !it.extra }, selected, color, onSelect)
        }
        Text(
            if (more) "Mniej ikon ▴" else "Więcej ikon (${ModeIcon.entries.count { it.extra }}) ▾",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { more = !more }
                .padding(vertical = 8.dp),
        )
        if (more) {
            BoxWithConstraints {
                val columns = ((maxWidth + 6.dp) / 40.dp).toInt().coerceIn(4, 8)
                IconGrid(columns, ModeIcon.entries.filter { it.extra }, selected, color, onSelect)
            }
        }
    }
}

@Composable
private fun IconGrid(columns: Int, icons: List<ModeIcon>, selected: ModeIcon, color: Long, onSelect: (ModeIcon) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        icons.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { icon ->
                    val isSelected = icon == selected
                    ModeBadge(
                        icon = icon,
                        color = if (isSelected) color else 0xFF5C6068,
                        size = 34.dp,
                        modifier = Modifier
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = RoundedCornerShape(11.dp),
                            )
                            .clickable { onSelect(icon) }
                            .semantics { contentDescription = icon.label },
                    )
                }
            }
        }
    }
}

// Rząd kolorowych kółek do wyboru (kolor trybu albo kolor główny). null = opcja "ze schematu".
@Composable
fun ColorSwatches(
    colors: List<Long>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    allowNone: Boolean = false,
    allowCustom: Boolean = true, // "+" na końcu: dowolny kolor z palety (HSV / HEX)
    modeBadge: Boolean = false,  // kolory trybu: kółka pokazują kolor tak, jak wyjdzie na znaczku w tym motywie
) {
    var pickerOpen by remember { mutableStateOf(false) }
    // horizontalScroll: kolorów jest więcej, niż mieści się w szerokości ekranu.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        if (allowNone) {
            Swatch(color = null, selected = selected == null, onClick = { onSelect(null) })
        }
        // Wybrany kolor spoza listy (własny) pokazujemy na początku, żeby było widać, co jest ustawione.
        if (selected != null && selected !in colors) {
            Swatch(color = selected, selected = true, onClick = { pickerOpen = true }, modeBadge = modeBadge)
        }
        colors.forEach { c -> Swatch(color = c, selected = c == selected, onClick = { onSelect(c) }, modeBadge = modeBadge) }
        if (allowCustom) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    // Tęczowe kółko = "dowolny kolor".
                    .background(androidx.compose.ui.graphics.Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.8f, 1f) }))
                    .clickable { pickerOpen = true }
                    .semantics { contentDescription = "Własny kolor" },
            ) {
                androidx.compose.material3.Text("+", color = Color.Black, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (pickerOpen) {
        ColorPickerDialog(
            initial = selected,
            onPick = {
                onSelect(it)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun Swatch(color: Long?, selected: Boolean, onClick: () -> Unit, modeBadge: Boolean = false) {
    val dark = isThemeDark()
    val fill = when {
        color == null -> MaterialTheme.colorScheme.surfaceVariant
        modeBadge -> remember(color, dark) { modeBadgeColor(color, dark) }
        else -> Color(color)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (color == null) "Kolor ze schematu" else "Kolor" },
    ) {
        if (color == null) {
            // "A" = automatyczny kolor ze schematu
            androidx.compose.material3.Text("A", style = MaterialTheme.typography.labelSmall)
        } else if (modeBadge && selected) {
            // Wybrany kolor trybu: znaczek ✓ w kolorze symbolu — od razu widać, jak będzie wyglądać ikona.
            androidx.compose.material3.Text("✓", color = modeBadgeSymbol(dark), style = MaterialTheme.typography.labelLarge)
        }
    }
}
