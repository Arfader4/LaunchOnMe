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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.db.ModeEntity

// Katalog ikon trybów. Grafiki są w res/drawable, więc ten sam symbol pokażą launcher i kafelek szybkich ustawień.
enum class ModeIcon(val key: String, @DrawableRes val res: Int, val label: String) {
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
    ;

    companion object {
        fun of(key: String?): ModeIcon = entries.firstOrNull { it.key == key } ?: STAR
    }
}

// Znaczek trybu: symbol na kolorowym tle. Kolor symbolu dobieramy do jasności tła, żeby zawsze był czytelny.
@Composable
fun ModeBadge(icon: ModeIcon, color: Long, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val bg = Color(color)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(bg),
    ) {
        Icon(
            painter = painterResource(icon.res),
            contentDescription = null,
            tint = if (bg.luminance() > 0.5f) Color(0xFF1A1206) else Color.White,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

@Composable
fun ModeBadge(mode: ModeEntity, size: Dp = 28.dp, modifier: Modifier = Modifier) =
    ModeBadge(ModeIcon.of(mode.icon), mode.color, size, modifier)

// Siatka wyboru ikony; liczba kolumn dopasowuje się do szerokości (okno dialogowe jest węższe niż arkusz).
@Composable
fun IconPicker(selected: ModeIcon, color: Long, onSelect: (ModeIcon) -> Unit) {
    BoxWithConstraints {
        val columns = ((maxWidth + 6.dp) / 40.dp).toInt().coerceIn(4, 8) // 34 dp ikona + 6 dp odstępu
        IconGrid(columns, selected, color, onSelect)
    }
}

@Composable
private fun IconGrid(columns: Int, selected: ModeIcon, color: Long, onSelect: (ModeIcon) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeIcon.entries.chunked(columns).forEach { row ->
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
) {
    // horizontalScroll: kolorów jest więcej, niż mieści się w szerokości ekranu.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        if (allowNone) {
            Swatch(color = null, selected = selected == null, onClick = { onSelect(null) })
        }
        colors.forEach { c -> Swatch(color = c, selected = c == selected, onClick = { onSelect(c) }) }
    }
}

@Composable
private fun Swatch(color: Long?, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant)
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
        }
    }
}
