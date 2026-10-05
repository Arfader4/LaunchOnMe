package pl.rafal.contextlauncher.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.onthemes.AllColors
import pl.rafal.onthemes.BadgeStyle
import pl.rafal.onthemes.LocalThemeSpec
import pl.rafal.onthemes.badgeOutline

// Katalog ikon trybów. Grafiki są w res/drawable, więc ten sam symbol pokażą launcher i kafelek szybkich ustawień.
enum class ModeIcon(val key: String, @DrawableRes val res: Int, @StringRes private val labelRes: Int, val extra: Boolean = false) {
    WORK("work", R.drawable.ic_mode_work, R.string.mode_icon_work),
    HOME("home", R.drawable.ic_mode_home, R.string.mode_icon_home),
    TRAVEL("travel", R.drawable.ic_mode_travel, R.string.mode_icon_travel),
    STUDY("study", R.drawable.ic_mode_study, R.string.mode_icon_study),
    SPORT("sport", R.drawable.ic_mode_sport, R.string.mode_icon_sport),
    NIGHT("night", R.drawable.ic_mode_night, R.string.mode_icon_night),
    SUN("sun", R.drawable.ic_mode_sun, R.string.mode_icon_sun),
    HEART("heart", R.drawable.ic_mode_heart, R.string.mode_icon_heart),
    STAR("star", R.drawable.ic_mode_star, R.string.mode_icon_star),
    MUSIC("music", R.drawable.ic_mode_music, R.string.mode_icon_music),
    CAR("car", R.drawable.ic_mode_car, R.string.mode_icon_car),
    CART("cart", R.drawable.ic_mode_cart, R.string.mode_icon_cart),
    COFFEE("coffee", R.drawable.ic_mode_coffee, R.string.mode_icon_coffee),
    LEAF("leaf", R.drawable.ic_mode_leaf, R.string.mode_icon_leaf),
    GAME("game", R.drawable.ic_mode_game, R.string.mode_icon_game),
    CAMERA("camera", R.drawable.ic_mode_camera, R.string.mode_icon_camera),
    // Rozszerzony zestaw (konturowy, jak podstawowe — foldery mają osobne, wypełnione symbole).
    X_BED("bed", R.drawable.ic_mode_bed, R.string.mode_icon_bed, extra = true),
    X_CODE("code", R.drawable.ic_mode_code, R.string.mode_icon_code, extra = true),
    X_COCKTAIL("cocktail", R.drawable.ic_mode_cocktail, R.string.mode_icon_cocktail, extra = true),
    X_BEER("beer", R.drawable.ic_mode_beer, R.string.mode_icon_beer, extra = true),
    X_BIKE("bike", R.drawable.ic_mode_bike, R.string.mode_icon_bike, extra = true),
    X_WAVES("waves", R.drawable.ic_mode_waves, R.string.mode_icon_waves, extra = true),
    X_MOUNTAIN("mountain", R.drawable.ic_mode_mountain, R.string.mode_icon_mountain, extra = true),
    X_TENT("tent", R.drawable.ic_mode_tent, R.string.mode_icon_tent, extra = true),
    X_BEACH("beach", R.drawable.ic_mode_beach, R.string.mode_icon_beach, extra = true),
    X_SNOW("snow", R.drawable.ic_mode_snow, R.string.mode_icon_snow, extra = true),
    X_PAW("paw", R.drawable.ic_mode_paw, R.string.mode_icon_paw, extra = true),
    X_PILL("pill", R.drawable.ic_mode_pill, R.string.mode_icon_pill, extra = true),
    X_LOTUS("lotus", R.drawable.ic_mode_lotus, R.string.mode_icon_lotus, extra = true),
    X_CHAT("chat", R.drawable.ic_mode_chat, R.string.mode_icon_chat, extra = true),
    X_PHONE("phone", R.drawable.ic_mode_phone, R.string.mode_icon_phone, extra = true),
    X_LAPTOP("laptop", R.drawable.ic_mode_laptop, R.string.mode_icon_laptop, extra = true),
    X_BRUSH("brush", R.drawable.ic_mode_brush, R.string.mode_icon_brush, extra = true),
    X_HAMMER("hammer", R.drawable.ic_mode_hammer, R.string.mode_icon_hammer, extra = true),
    X_SHIELD("shield", R.drawable.ic_mode_shield, R.string.mode_icon_shield, extra = true),
    X_CHART("chart", R.drawable.ic_mode_chart, R.string.mode_icon_chart, extra = true),
    X_FAMILY("family", R.drawable.ic_mode_family, R.string.mode_icon_family, extra = true),
    X_BELL("bell", R.drawable.ic_mode_bell, R.string.mode_icon_bell, extra = true),
    X_RAIN("rain", R.drawable.ic_mode_rain, R.string.mode_icon_rain, extra = true),
    X_MOVIE("movie", R.drawable.ic_mode_movie, R.string.mode_icon_movie, extra = true),
    X_GIFT("gift", R.drawable.ic_mode_gift, R.string.mode_icon_gift, extra = true),
    X_CLOCK("clock", R.drawable.ic_mode_clock, R.string.mode_icon_clock, extra = true),
    X_CALENDAR("calendar", R.drawable.ic_mode_calendar, R.string.mode_icon_calendar, extra = true),
    X_GRAD("grad", R.drawable.ic_mode_grad, R.string.mode_icon_grad, extra = true),
    X_BUS("bus", R.drawable.ic_mode_bus, R.string.mode_icon_bus, extra = true),
    X_TRAIN("train", R.drawable.ic_mode_train, R.string.mode_icon_train, extra = true),
    X_BOLT("bolt", R.drawable.ic_mode_bolt, R.string.mode_icon_bolt, extra = true),
    X_GLOBE("globe", R.drawable.ic_mode_globe, R.string.mode_icon_globe, extra = true),
    X_FLAG("flag", R.drawable.ic_mode_flag, R.string.mode_icon_flag, extra = true),
    X_HEADPHONES("headphones", R.drawable.ic_mode_headphones, R.string.mode_icon_headphones, extra = true),
    X_READING("reading", R.drawable.ic_mode_reading, R.string.mode_icon_reading, extra = true),
    X_BABY("baby", R.drawable.ic_mode_baby, R.string.mode_icon_baby, extra = true),
    X_PIZZA("pizza", R.drawable.ic_mode_pizza, R.string.mode_icon_pizza, extra = true),
    ;

    val label: String get() = AppText.get(labelRes)

    companion object {
        fun of(key: String?): ModeIcon = entries.firstOrNull { it.key == key } ?: STAR
    }
}

// Kolor tła znaczka trybu dopasowany do motywu. Symbol ma jeden kolor na cały motyw: w ciemnym zawsze jasny,
// w jasnym zawsze ciemny — więc to tło się dostosowuje. Zachowujemy odcień i nasycenie (HSL), zmieniamy tylko jasność,
// aż kontrast z symbolem będzie dobry (luminancja jak w WCAG). Dotyczy też kolorów własnych z palety HSV.
// Samo liczenie mieszka w module OnThemes (Badges.kt), żeby podgląd w OnThemes rysował znaczki tak samo.
// Styl znaczka (BadgeStyle) zależy od motywu — patrz LocalThemeSpec.
fun modeBadgeColor(color: Long, dark: Boolean, style: BadgeStyle = BadgeStyle.TINTED): Color =
    pl.rafal.onthemes.badgeBackground(color, dark, style)

// Kolor symbolu na znaczku trybu (i na podglądzie koloru w wyborze). W High Contrast symbol ma kolor trybu.
fun modeBadgeSymbol(dark: Boolean, color: Long = 0xFF808080, style: BadgeStyle = BadgeStyle.TINTED): Color =
    pl.rafal.onthemes.badgeSymbol(dark, style, color)

// Kolor, który na znaczku "niesie" kolor trybu (tło, a w High Contrast — symbol). Do kółek wyboru i kropek.
fun modeSwatchColor(color: Long, dark: Boolean, style: BadgeStyle): Color = pl.rafal.onthemes.badgeSwatch(color, dark, style)

// Znaczek trybu: symbol na tle. Wygląd według stylu motywu (TINTED / INVERTED / MONO), symbol jasny/ciemny jak motyw.
@Composable
fun ModeBadge(icon: ModeIcon, color: Long, size: Dp = 28.dp, modifier: Modifier = Modifier, shape: Shape? = null) {
    val dark = isThemeDark()
    val style = LocalThemeSpec.current.badge
    val bg = remember(color, dark, style) { modeBadgeColor(color, dark, style) }
    val clipShape = shape ?: LocalIconShape.current.shape(size) // kształt z ustawień, chyba że wywołujący wymusza własny
    val outline = badgeOutline(dark, style) // tylko High Contrast: czarny znaczek na czarnym tle potrzebuje obwódki
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(clipShape)
            .background(bg)
            .border(1.dp, outline ?: Color.Transparent, clipShape),
    ) {
        Icon(
            painter = painterResource(icon.res),
            contentDescription = null,
            tint = modeBadgeSymbol(dark, color, style),
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
            if (more) stringResource(R.string.mode_icons_less) else stringResource(R.string.mode_icons_more, ModeIcon.entries.count { it.extra }),
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
    showOffList: Boolean = true, // wybrany kolor spoza listy pokaż na początku (false, gdy pokazuje go inny rząd)
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val customColorDescription = stringResource(R.string.mode_color_custom)
    // horizontalScroll: kolorów jest więcej, niż mieści się w szerokości ekranu.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        if (allowNone) {
            Swatch(color = null, selected = selected == null, onClick = { onSelect(null) })
        }
        // Wybrany kolor spoza listy (własny) pokazujemy na początku, żeby było widać, co jest ustawione.
        if (showOffList && selected != null && selected !in colors) {
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
                    .semantics { contentDescription = customColorDescription },
            ) {
                androidx.compose.material3.Text("+", color = Color.Black, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (pickerOpen) {
        ColorPickerDialog(
            initial = selected,
            badgePreview = modeBadge,
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
    val style = LocalThemeSpec.current.badge
    val fill = when {
        color == null -> MaterialTheme.colorScheme.surfaceVariant
        modeBadge -> remember(color, dark, style) { modeSwatchColor(color, dark, style) }
        else -> Color(color)
    }
    val swatchDescription = if (color == null) stringResource(R.string.mode_color_scheme) else stringResource(R.string.mode_color)
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
            .semantics { contentDescription = swatchDescription },
    ) {
        if (color == null) {
            // "A" = automatyczny kolor ze schematu
            androidx.compose.material3.Text("A", style = MaterialTheme.typography.labelSmall)
        } else if (modeBadge && selected) {
            // Wybrany kolor trybu: znaczek ✓ w kolorze symbolu — od razu widać, jak będzie wyglądać ikona.
            // W High Contrast kółko ma kolor symbolu, więc ✓ rysujemy kolorem tła znaczka (czarnym / białym).
            val check = if (style == BadgeStyle.INVERTED) modeBadgeColor(color, dark, style) else modeBadgeSymbol(dark, color, style)
            androidx.compose.material3.Text("✓", color = check, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// Wybór koloru w dwóch rzędach: krótka paleta bieżącego motywu ("Z motywu") i wszystkie kolory po odcieniu
// ("Wszystkie kolory", na końcu "+" = dowolny kolor). Kolor spoza obu list pokazuje się na początku drugiego rzędu.
@Composable
fun ThemeColorSwatches(
    selected: Long?,
    onSelect: (Long?) -> Unit,
    allowNone: Boolean = false,
    modeBadge: Boolean = false,
) {
    val palette = LocalThemeSpec.current.palette
    val rest = AllColors.filter { it !in palette }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.look_colors_theme),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorSwatches(
            colors = palette,
            selected = selected,
            onSelect = onSelect,
            allowNone = allowNone,
            allowCustom = false,
            modeBadge = modeBadge,
            showOffList = false,
        )
        Text(
            stringResource(R.string.look_colors_all),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorSwatches(
            colors = rest,
            selected = selected,
            onSelect = onSelect,
            modeBadge = modeBadge,
            showOffList = selected != null && selected !in palette,
        )
    }
}
