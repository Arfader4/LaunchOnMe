package pl.rafal.contextlauncher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import pl.rafal.contextlauncher.data.GlanceEvent
import pl.rafal.contextlauncher.data.Weather
import pl.rafal.contextlauncher.data.WeatherCodes
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.contextlauncher.ui.ModeBadge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val Polish = Locale("pl")
private val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val LongDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Polish) // "niedziela, 27 września"

// --- Zegar ---

@Composable
fun ClockWidget(onClick: (() -> Unit)?) {
    val now = rememberCurrentMinute()
    WidgetSurface(onClick = onClick) {
        // Wielkość cyfr zależy od szerokości widżetu: po powiększeniu zegar rośnie razem z nim.
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            val timeSize = (maxWidth.value / 3.2f).coerceIn(34f, 96f).sp
            Column {
                val time = now.atZone(ZoneId.systemDefault())
                Text(time.format(HourMinute), fontSize = timeSize, fontWeight = FontWeight.SemiBold, lineHeight = timeSize)
                Text(
                    time.format(LongDate).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- Pogoda ---

@Composable
fun WeatherWidget(weather: Weather?, hasPermission: Boolean, onClick: (() -> Unit)?) {
    WidgetSurface(onClick = onClick) {
        when {
            !hasPermission -> {
                Text("Pogoda", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Dotknij, aby udostępnić przybliżoną lokalizację.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            weather == null -> Text("Wczytywanie pogody…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > 240.dp
                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(WeatherCodes.icon(weather.code, weather.isDay)),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${weather.temperature}°", fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text(WeatherCodes.describe(weather.code), style = MaterialTheme.typography.bodyMedium)
                        weather.place?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Szeroki widżet: prognoza na kolejne dni (pomijamy dzisiaj, drop(1) ≈ Skip(1)).
                    if (wide && weather.daily.size > 1) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            weather.daily.drop(1).take(3).forEach { day ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Polish),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Icon(
                                        painter = painterResource(WeatherCodes.icon(day.code)),
                                        contentDescription = WeatherCodes.describe(day.code),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text("${day.max}° / ${day.min}°", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Tarcza trybów ---

// Tryby rozłożone na okręgu wokół przycisku "+". Pierścień można obracać palcem (przy wielu trybach).
@Composable
fun ModeDialWidget(
    modes: List<ModeEntity>,
    activeId: Long?,
    enabled: Boolean,
    onSelect: (ModeEntity) -> Unit,
    onNewMode: () -> Unit,
) {
    var rotation by remember { mutableFloatStateOf(0f) } // w stopniach

    WidgetSurface(onClick = null) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!enabled || modes.size < 2) Modifier
                    else Modifier.pointerInput(modes.size) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            // Obrót = różnica kątów palca względem środka (atan2 ≈ Math.Atan2 w C#).
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val now = change.position - center
                            val before = now - drag
                            var delta = Math.toDegrees(
                                (atan2(now.y, now.x) - atan2(before.y, before.x)).toDouble(),
                            ).toFloat()
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            rotation += delta
                        }
                    },
                ),
        ) {
            val side = min(maxWidth, maxHeight)
            val badge = (side * 0.2f).coerceIn(36.dp, 56.dp)
            val radius = side / 2 - badge / 2 - 6.dp

            modes.forEachIndexed { index, mode ->
                // Kąt kolejnego trybu: równe odstępy, pierwszy na górze (-90°), plus obrót palcem.
                val angle = Math.toRadians((rotation - 90f + index * 360f / modes.size).toDouble())
                val isActive = mode.id == activeId
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(radius * cos(angle).toFloat(), radius * sin(angle).toFloat())
                        .size(badge + 8.dp)
                        .clip(CircleShape)
                        .then(
                            if (isActive) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier,
                        )
                        .clickable(enabled = enabled) { onSelect(mode) }
                        .semantics { contentDescription = "Tryb ${mode.name}${if (isActive) ", aktywny" else ""}" },
                ) {
                    ModeBadge(mode, size = badge)
                }
            }

            // Środek: tworzenie nowego trybu.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(badge * 1.25f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = enabled, onClick = onNewMode)
                        .semantics { contentDescription = "Stwórz tryb" },
                ) {
                    Text("+", color = MaterialTheme.colorScheme.onPrimary, fontSize = 30.sp, fontWeight = FontWeight.Light)
                }
                if (side > 200.dp) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        modes.firstOrNull { it.id == activeId }?.name.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}


// --- W skrócie (jak "At a Glance" z Pixela) ---

// Co otwiera dotknięcie poszczególnych części widżetu. Klasa zamiast pięciu osobnych parametrów.
class GlanceCallbacks(
    val onClock: () -> Unit,
    val onDate: () -> Unit,
    val onEvent: (GlanceEvent) -> Unit,
    val onGrantCalendar: () -> Unit,
    val onWeather: () -> Unit,
    val onAlarm: () -> Unit,
)

// Jeden widżet, kilka "stref dotyku": godzina → Zegar, data → Kalendarz, wydarzenie → to wydarzenie,
// pogoda → aplikacja pogodowa, budzik → lista budzików.
@Composable
fun GlanceWidget(
    event: GlanceEvent?,
    hasCalendar: Boolean,
    alarmAt: Long?,
    weather: Weather?,
    callbacks: GlanceCallbacks?, // null = tryb edycji układu (bez reakcji na dotyk)
) {
    val now = rememberCurrentMinute()
    val zone = ZoneId.systemDefault()
    val time = now.atZone(zone)

    WidgetSurface(onClick = null) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val timeSize = (maxHeight.value / 2.6f).coerceIn(30f, 72f).sp
            Row(Modifier.fillMaxSize()) {
                // Lewa kolumna: godzina, data, wydarzenie.
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    Text(
                        time.format(HourMinute),
                        fontSize = timeSize,
                        lineHeight = timeSize,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.tap("Otwórz zegar", callbacks?.onClock),
                    )
                    Column {
                        Text(
                            time.format(LongDate).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.tap("Otwórz kalendarz", callbacks?.onDate),
                        )
                        when {
                            !hasCalendar -> GlanceLine(
                                "Dotknij, aby pokazać wydarzenia",
                                Modifier.tap("Zezwól na kalendarz", callbacks?.onGrantCalendar),
                            )
                            event != null -> GlanceLine(
                                describeEvent(event, now),
                                Modifier.tap("Otwórz wydarzenie", callbacks?.let { cb -> { cb.onEvent(event) } }),
                                highlight = event.begin - now.toEpochMilli() in 0..SOON_MS || event.begin <= now.toEpochMilli(),
                            )
                            else -> GlanceLine("Brak wydarzeń do jutra", Modifier.tap("Otwórz kalendarz", callbacks?.onDate))
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Prawa kolumna: pogoda u góry, budzik na dole.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.tap("Otwórz pogodę", callbacks?.onWeather),
                    ) {
                        if (weather != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(WeatherCodes.icon(weather.code, weather.isDay)),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("${weather.temperature}°", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                WeatherCodes.describe(weather.code),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("Pogoda", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (alarmAt != null) {
                        Text(
                            "⏰ " + describeAlarm(alarmAt, now),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.tap("Budziki", callbacks?.onAlarm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceLine(text: String, modifier: Modifier, highlight: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

// Strefa dotyku z opisem dla czytnika ekranu; bez akcji (edycja) — zwykły tekst.
// Funkcja rozszerzająca ≈ extension method w C#.
private fun Modifier.tap(label: String, action: (() -> Unit)?): Modifier =
    if (action == null) this
    else this
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
        .clickable(onClickLabel = label, onClick = action)
        .padding(vertical = 2.dp)

private const val SOON_MS = 60 * 60_000L

// "Teraz: Spotkanie", "za 25 min: Spotkanie", "14:00 Spotkanie", "jutro 9:00 Spotkanie", "Cały dzień: Urodziny".
private fun describeEvent(event: GlanceEvent, now: Instant): String {
    val nowMs = now.toEpochMilli()
    val zone = ZoneId.systemDefault()
    val title = event.title.ifBlank { "(bez tytułu)" }
    if (event.allDay) return "Cały dzień: $title"
    val begin = Instant.ofEpochMilli(event.begin).atZone(zone)
    return when {
        event.begin <= nowMs -> "Teraz: $title"
        event.begin - nowMs <= SOON_MS -> "Za ${((event.begin - nowMs) / 60_000).coerceAtLeast(1)} min: $title"
        begin.toLocalDate() == now.atZone(zone).toLocalDate() -> "${begin.format(HourMinute)} $title"
        else -> "Jutro ${begin.format(HourMinute)} $title"
    }
}

// Budzik w ciągu doby: sama godzina; dalej: dzień tygodnia + godzina ("pn 06:30").
private fun describeAlarm(at: Long, now: Instant): String {
    val alarm = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return if (at - now.toEpochMilli() < 24 * 3_600_000L) alarm.format(HourMinute)
    else alarm.dayOfWeek.getDisplayName(TextStyle.SHORT, Polish) + " " + alarm.format(HourMinute)
}
