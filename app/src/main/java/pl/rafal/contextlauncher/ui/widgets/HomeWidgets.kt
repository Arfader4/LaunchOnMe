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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import androidx.compose.foundation.layout.aspectRatio
import pl.rafal.contextlauncher.ui.LocalIconShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
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

private val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
// Język systemu (getter: po zmianie języka telefonu daty od razu w nowym języku).
private val LongDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) // "niedziela, 27 września"
private val ShortDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())   // "niedz., 27 wrz"
private val DayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")                 // "28.09"

// --- Zegar ---

@Composable
fun ClockWidget(onClick: (() -> Unit)?) {
    val now = rememberCurrentMinute()
    val time = now.atZone(ZoneId.systemDefault())
    AdaptiveWidget { size, _, _ ->
        WidgetSurface(onClick = onClick) {
            when (size) {
                // 1×1: sama godzina, tak duża, jak się zmieści.
                WidgetSize.TINY -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FitText(time.format(HourMinute), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                // 2×2: godzina + data DD.MM.
                WidgetSize.SMALL -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    FitText(time.format(HourMinute), Modifier.fillMaxWidth().weight(1f), textAlign = TextAlign.Center)
                    FitText(
                        time.format(DayMonth),
                        Modifier.fillMaxWidth(),
                        maxSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Niski pasek: jedna linia jak na pasku stanu — tekst maleje, zamiast się zawijać.
                WidgetSize.STRIP -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    FitText(time.format(HourMinute), Modifier.weight(0.45f), maxSize = 40.sp)
                    Spacer(Modifier.width(8.dp))
                    FitText(
                        time.format(ShortDate).replaceFirstChar { it.uppercase() },
                        Modifier.weight(0.55f),
                        maxSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WidgetSize.LARGE -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    FitText(time.format(HourMinute), Modifier.fillMaxWidth().weight(1f), maxSize = 110.sp)
                    FitText(
                        time.format(LongDate).replaceFirstChar { it.uppercase() },
                        Modifier.fillMaxWidth(),
                        maxSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// --- Pogoda ---

@Composable
fun WeatherWidget(weather: Weather?, hasPermission: Boolean, onClick: (() -> Unit)?) {
  AdaptiveWidget { size, _, _ ->
    // 1×1 i 2×2: ikona pogody i temperatura, obie skalują się do kratki.
    if (weather != null && hasPermission && (size == WidgetSize.TINY || size == WidgetSize.SMALL)) {
        WidgetSurface(onClick = onClick) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(
                    painter = painterResource(WeatherCodes.icon(weather.code, weather.isDay)),
                    contentDescription = WeatherCodes.describe(weather.code),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                )
                FitText("${weather.temperature}°", Modifier.fillMaxWidth(), maxSize = 28.sp, textAlign = TextAlign.Center)
            }
        }
        return@AdaptiveWidget
    }
    WidgetSurface(onClick = onClick) {
        when {
            !hasPermission -> {
                Text(stringResource(R.string.w_weather_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.w_weather_grant),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            weather == null -> Text(stringResource(R.string.w_weather_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                // Mały: ikona + temperatura (i opis, jeśli zmieści się w poziomie).
                if (maxHeight < 90.dp) {
                    val icon = (maxHeight - 8.dp).coerceIn(18.dp, 40.dp)
                    val wideStrip = maxWidth > 150.dp // odczyt tutaj: wewnątrz Row zewnętrzny zakres jest niedostępny
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(WeatherCodes.icon(weather.code, weather.isDay)),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(icon),
                        )
                        Spacer(Modifier.width(6.dp))
                        FitText("${weather.temperature}°", Modifier.weight(if (wideStrip) 0.35f else 1f), maxSize = (icon.value * 0.8f).sp)
                        if (wideStrip) {
                            Spacer(Modifier.width(8.dp))
                            FitText(
                                WeatherCodes.describe(weather.code),
                                Modifier.weight(0.65f),
                                maxSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    return@BoxWithConstraints
                }
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
                                        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
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
}

// --- Tarcza trybów ---

// Tryby rozłożone na okręgu wokół przycisku "+". Pierścień można obracać palcem (przy wielu trybach).
// Widżet "Tryby" skaluje się jak nagłówek: od samej ikony (1×1), przez pasek z nazwą i "+",
// po pełną tarczę z kołem. Dzięki temu zastępuje dawny nagłówek karty.
@Composable
fun ModeDialWidget(
    modes: List<ModeEntity>,
    activeId: Long?,
    enabled: Boolean,
    onSelect: (ModeEntity) -> Unit,
    onNewMode: () -> Unit,
    subtitle: String? = null,  // np. "do 17:30" (tryb na czas)
    badge: Boolean = false,    // czerwona kropka: coś czeka w menu
    menu: @Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit = { _, _ -> },
) {
    var menuOpen by remember { mutableStateOf(false) }
    val active = modes.firstOrNull { it.id == activeId }
    // Teksty dla czytnika ekranu czytamy tutaj: semantics { } nie jest funkcją @Composable.
    val createModeLabel = stringResource(R.string.w_mode_create)
    val createModeActiveLabel = stringResource(R.string.w_mode_create_active, active?.name.orEmpty())

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wymiary czytamy tutaj: wewnątrz zagnieżdżonych Box/Row nie są dostępne (DSL marker zasłania zewnętrzny zakres).
        val w = maxWidth
        val h = maxHeight
        // Progi: do 2 kratek szerokości sam "+", od 3 kratek pasek z ikoną i nazwą, pełna tarcza od ok. 4×4.
        val tiny = w < 125.dp
        val strip = !tiny && min(w, h) < 180.dp
        when {
            // 1×1 / 2×2: przycisk "+" (nowy tryb) z kropką w kolorze aktualnego trybu.
            // Od 2×2 pod przyciskiem jest podpis "Nowy tryb" — sam "+" nie mówił, co robi.
            tiny -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            ) {
                val labeled = w >= 70.dp && h >= 70.dp
                val labelH = if (labeled) 16.dp else 0.dp
                val side = (min(w, h - labelH) - 8.dp).coerceAtMost(64.dp)
                Box {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(side)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(enabled = enabled, onClick = onNewMode)
                            .semantics { contentDescription = createModeActiveLabel },
                    ) {
                        FitText("+", Modifier.fillMaxSize(0.6f), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
                    }
                    // Kropka w kolorze aktywnego trybu — jej dotknięcie otwiera listę trybów.
                    active?.let {
                        val changeModeLabel = stringResource(R.string.w_mode_change, it.name)
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(side * 0.34f)
                                .clip(CircleShape)
                                .clickable(enabled = enabled) { menuOpen = true }
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(pl.rafal.contextlauncher.ui.modeBadgeColor(it.color, pl.rafal.contextlauncher.ui.isThemeDark()))
                                .semantics { contentDescription = changeModeLabel },
                        )
                    }
                    if (badge) BadgeDot(Modifier.align(Alignment.BottomEnd))
                    menu(menuOpen) { menuOpen = false }
                }
                if (labeled) {
                    FitText(
                        stringResource(R.string.w_mode_new),
                        Modifier.fillMaxWidth().height(labelH),
                        maxSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Pasek: ikona + nazwa (+ "do 17:30"), a gdy jest miejsce — przycisk "+".
            strip -> WidgetSurface(onClick = null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = enabled) { menuOpen = true },
                        ) {
                            Box {
                                active?.let { ModeBadge(it, size = (h - 20.dp).coerceIn(24.dp, 44.dp)) }
                                if (badge) BadgeDot(Modifier.align(Alignment.TopEnd))
                            }
                            Spacer(Modifier.width(10.dp))
                            // Nazwa zajmuje resztę miejsca i maleje, gdy się nie mieści (bez zawijania do pionu).
                            Column(Modifier.weight(1f)) {
                                FitText(active?.name.orEmpty(), Modifier.fillMaxWidth(), maxSize = 18.sp)
                                subtitle?.let {
                                    FitText(it, Modifier.fillMaxWidth(), maxSize = 11.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        menu(menuOpen) { menuOpen = false }
                    }
                    Spacer(Modifier.width(6.dp))
                    run {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size((h - 20.dp).coerceIn(28.dp, 44.dp))
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(enabled = enabled, onClick = onNewMode)
                                .semantics { contentDescription = createModeLabel },
                        ) { Text("+", color = MaterialTheme.colorScheme.onPrimary, fontSize = 22.sp, fontWeight = FontWeight.Light) }
                    }
                }
            }
            else -> FullModeDial(modes, activeId, enabled, onSelect, onNewMode)
        }
    }
}

@Composable
private fun BadgeDot(modifier: Modifier) {
    Box(
        modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
    )
}

@Composable
private fun FullModeDial(
    modes: List<ModeEntity>,
    activeId: Long?,
    enabled: Boolean,
    onSelect: (ModeEntity) -> Unit,
    onNewMode: () -> Unit,
) {
    val createModeLabel = stringResource(R.string.w_mode_create) // semantics { } nie jest @Composable
    // Przy jednym trybie tarcza nie ma czego pokazywać — zamiast pustego koła podpowiadamy, co dalej.
    if (modes.size < 2) {
        WidgetSurface(onClick = null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = enabled, onClick = onNewMode)
                        .semantics { contentDescription = createModeLabel },
                ) {
                    Text("+", color = MaterialTheme.colorScheme.onPrimary, fontSize = 30.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.w_mode_add_another), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.w_mode_dial_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    // --- "Fidget spinner" ---
    // Pierścień z trybami kręci się palcem z bezwładnością: puszczony rozpędzony kręci się dalej, zwalnia,
    // "klika" na najbliższy tryb pod znacznikiem ▲ u góry i ten tryb się włącza. Przy każdym trybie
    // mijającym znacznik telefon lekko drga (jak zapadka). Dotknięcie trybu obraca go pod znacznik i włącza.
    val n = modes.size
    val step = 360f / n
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Start od razu na aktywnym trybie (bez obracania się przy każdym pokazaniu karty). W stopniach, bez limitu.
    val rotation = remember { Animatable(-(modes.indexOfFirst { it.id == activeId }.coerceAtLeast(0)) * step) }
    var interacting by remember { mutableStateOf(false) } // palec albo rozpęd — wtedy nie ruszamy tarczy z zewnątrz
    val gesture = remember { intArrayOf(0) } // numer ostatniego gestu: stary rozpęd nie "puszcza" nowego chwytu
    val currentModes by rememberUpdatedState(modes)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentActiveId by rememberUpdatedState(activeId)

    // Który tryb jest teraz pod znacznikiem. Tryb i leży na kącie rotation + i·step (0 = góra).
    fun topIndex(rot: Float): Int = Math.floorMod(Math.round(-rot / step), n)
    // Najbliższy obrót (w którąkolwiek stronę), przy którym tryb [index] stoi pod znacznikiem.
    fun rotationFor(index: Int, from: Float): Float {
        val base = -index * step
        val turns = Math.round((from - base) / 360f)
        return base + turns * 360f
    }

    // Ustawienie tarczy na aktywny tryb (start, zmiana trybu z innego miejsca) — tylko gdy nikt nie kręci.
    LaunchedEffect(activeId, modes.map { it.id }) {
        val index = currentModes.indexOfFirst { it.id == activeId }
        if (index < 0 || interacting) return@LaunchedEffect
        rotation.animateTo(rotationFor(index, rotation.value), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    // Zapadka: drgnięcie, gdy kolejny tryb mija znacznik (tylko przy kręceniu, nie przy samoczynnym ustawianiu).
    LaunchedEffect(n) {
        var last = topIndex(rotation.value)
        snapshotFlow { topIndex(rotation.value) }.collect { index ->
            if (index != last && interacting) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            last = index
        }
    }

    // Rozpęd → wyhamowanie → "kliknięcie" na tryb (lekkie sprężynowanie) → włączenie trybu.
    suspend fun settle(velocity: Float, id: Int) {
        try {
            if (kotlin.math.abs(velocity) > 30f) {
                rotation.animateDecay(velocity, exponentialDecay(frictionMultiplier = 0.45f, absVelocityThreshold = 15f)) // mniejsze tarcie = dłużej się kręci
            }
            val index = topIndex(rotation.value)
            rotation.animateTo(rotationFor(index, rotation.value), spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            val mode = currentModes.getOrNull(index)
            if (mode != null && mode.id != currentActiveId) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnSelect(mode)
            }
        } finally {
            if (gesture[0] == id) interacting = false // przerwany nowym chwytem → nowy gest sam zdecyduje
        }
    }

    WidgetSurface(onClick = null) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!enabled) Modifier
                    else Modifier.pointerInput(n) {
                        // Prędkość kątowa z ostatnich ruchów palca (jak VelocityTracker w Androidzie, ale w stopniach).
                        val tracker = VelocityTracker1D(isDataDifferential = false)
                        var angle = 0f
                        detectDragGestures(
                            onDragStart = {
                                interacting = true
                                gesture[0]++
                                tracker.resetTracking()
                                angle = rotation.value
                                scope.launch { rotation.stop() } // złapanie kręcącej się tarczy ją zatrzymuje
                            },
                            onDragEnd = {
                                val velocity = tracker.calculateVelocity().coerceIn(-2500f, 2500f)
                                val id = gesture[0]
                                scope.launch { settle(velocity, id) }
                            },
                            onDragCancel = {
                                val id = gesture[0]
                                scope.launch { settle(0f, id) }
                            },
                        ) { change, drag ->
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
                            angle += delta
                            tracker.addDataPoint(change.uptimeMillis, angle)
                            val target = angle
                            scope.launch { rotation.snapTo(target) }
                        }
                    },
                ),
        ) {
            val side = min(maxWidth, maxHeight)
            val badge = (side * 0.2f).coerceIn(36.dp, 56.dp)
            val radius = side / 2 - badge / 2 - 8.dp
            val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            val rot = rotation.value

            // Tor pierścienia (jak łożysko spinnera) — widać, że to coś do kręcenia.
            Box(
                Modifier
                    .size(radius * 2)
                    .drawBehind {
                        drawCircle(track, radius = size.minDimension / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    },
            )

            modes.forEachIndexed { index, mode ->
                // Kąt trybu: równe odstępy, 0° = góra (stąd -90° w układzie ekranu), plus obrót tarczy.
                val deg = rot + index * step
                val angle = Math.toRadians((deg - 90f).toDouble())
                val isActive = mode.id == activeId
                // Im bliżej znacznika, tym większy znaczek (płynnie, także w trakcie kręcenia).
                val fromTop = kotlin.math.abs(((deg % 360f) + 540f) % 360f - 180f) // 0 = na górze
                val closeness = (1f - fromTop / step).coerceIn(0f, 1f)
                val modeLabel = if (isActive) stringResource(R.string.w_mode_item_active, mode.name) else stringResource(R.string.w_mode_item, mode.name)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(radius * cos(angle).toFloat(), radius * sin(angle).toFloat())
                        .graphicsLayer {
                            scaleX = 1f + 0.22f * closeness
                            scaleY = 1f + 0.22f * closeness
                        }
                        .size(badge + 8.dp)
                        .clip(CircleShape)
                        .then(
                            if (isActive) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier,
                        )
                        .clickable(enabled = enabled) {
                            // Dotknięcie: obrót tego trybu pod znacznik, potem włączenie.
                            interacting = true
                            val id = ++gesture[0]
                            scope.launch {
                                try {
                                    rotation.animateTo(rotationFor(index, rotation.value), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
                                    currentOnSelect(mode)
                                } finally {
                                    if (gesture[0] == id) interacting = false
                                }
                            }
                        }
                        .semantics { contentDescription = modeLabel },
                ) {
                    // Na tarczy zawsze koła — kwadrat w okrągłej ramce wyglądał na "ucięty".
                    ModeBadge(mode, size = badge, shape = CircleShape)
                }
            }

            // Znacznik ▲ pod górnym trybem: "to się włączy".
            Text(
                "▲",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.offset(y = -(radius - badge / 2 - 12.dp)),
            )

            // Środek (piasta spinnera): tworzenie nowego trybu + nazwa trybu pod znacznikiem.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(badge * 1.1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = enabled, onClick = onNewMode)
                        .semantics { contentDescription = createModeLabel },
                ) {
                    Text("+", color = MaterialTheme.colorScheme.onPrimary, fontSize = 28.sp, fontWeight = FontWeight.Light)
                }
                if (side > 200.dp) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        modes.getOrNull(topIndex(rot))?.name.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
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
    alarmApp: String?,
    weather: Weather?,
    hasWeatherPermission: Boolean,
    callbacks: GlanceCallbacks?, // null = tryb edycji układu (bez reakcji na dotyk)
) {
    val now = rememberCurrentMinute()
    val zone = ZoneId.systemDefault()
    val time = now.atZone(zone)
    // Opisy stref dotyku (dla czytnika ekranu) — Modifier.tap nie jest @Composable, więc teksty czytamy tutaj.
    val openClock = stringResource(R.string.w_glance_open_clock)
    val openWeather = stringResource(R.string.w_glance_open_weather)
    val openCalendar = stringResource(R.string.w_glance_open_calendar)
    val openEvent = stringResource(R.string.w_glance_open_event)
    val alarmLabel = stringResource(R.string.w_glance_alarm)
    val grantCalendar = stringResource(R.string.w_glance_grant_calendar)

    WidgetSurface(onClick = null) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gSize = widgetSizeOf(maxWidth + 16.dp, maxHeight + 8.dp) // + marginesy WidgetSurface = rozmiar kratki
            // 1×1: sam zegar.
            if (gSize == WidgetSize.TINY) {
                Box(Modifier.fillMaxSize().tap(openClock, callbacks?.onClock), contentAlignment = Alignment.Center) {
                    FitText(time.format(HourMinute), Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                return@BoxWithConstraints
            }
            // 2×2: mała pogoda, zegar, data DD.MM i same ikonki budzika / kalendarza, gdy jest coś ustawione.
            if (gSize == WidgetSize.SMALL) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    weather?.let { wth ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(0.3f).tap(openWeather, callbacks?.onWeather)) {
                            Icon(
                                painter = painterResource(WeatherCodes.icon(wth.code, wth.isDay)),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                            )
                            FitText("${wth.temperature}°", maxSize = 12.sp, fontWeight = FontWeight.Normal)
                        }
                    }
                    FitText(
                        time.format(HourMinute),
                        Modifier.fillMaxWidth().weight(0.45f).tap(openClock, callbacks?.onClock),
                        textAlign = TextAlign.Center,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(0.25f)) {
                        FitText(
                            time.format(DayMonth),
                            Modifier.tap(openCalendar, callbacks?.onDate),
                            maxSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (alarmAt != null) {
                            Icon(
                                painterResource(R.drawable.ic_w_alarm),
                                contentDescription = stringResource(R.string.w_glance_alarm_desc, describeAlarm(alarmAt, now)),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp).fillMaxHeight().aspectRatio(1f).tap(alarmLabel, callbacks?.onAlarm),
                            )
                        }
                        if (event != null) {
                            Icon(
                                painterResource(R.drawable.ic_w_calendar),
                                contentDescription = stringResource(R.string.w_glance_event_desc, event.title),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp).fillMaxHeight().aspectRatio(1f)
                                    .tap(openEvent, callbacks?.let { cb -> { cb.onEvent(event) } }),
                            )
                        }
                    }
                }
                return@BoxWithConstraints
            }
            // Pasek jednej linii: godzina · data · pogoda · budzik — tekst maleje, zamiast się zawijać.
            if (gSize == WidgetSize.STRIP) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                    FitText(time.format(HourMinute), Modifier.weight(0.3f).tap(openClock, callbacks?.onClock), maxSize = 32.sp)
                    Spacer(Modifier.width(8.dp))
                    FitText(
                        time.format(ShortDate).replaceFirstChar { it.uppercase() },
                        Modifier.weight(0.34f).tap(openCalendar, callbacks?.onDate),
                        maxSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    )
                    weather?.let {
                        FitText("${it.temperature}°", Modifier.weight(0.14f).tap(openWeather, callbacks?.onWeather), maxSize = 16.sp)
                    }
                    if (alarmAt != null) {
                        FitText(
                            "⏰" + describeAlarm(alarmAt, now),
                            Modifier.weight(0.22f).tap(alarmLabel, callbacks?.onAlarm),
                            maxSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@BoxWithConstraints
            }
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
                        modifier = Modifier.tap(openClock, callbacks?.onClock),
                    )
                    Column {
                        Text(
                            time.format(LongDate).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.tap(openCalendar, callbacks?.onDate),
                        )
                        when {
                            !hasCalendar -> GlanceLine(
                                stringResource(R.string.w_glance_show_events),
                                Modifier.tap(grantCalendar, callbacks?.onGrantCalendar),
                            )
                            event != null -> GlanceLine(
                                describeEvent(event, now),
                                Modifier.tap(openEvent, callbacks?.let { cb -> { cb.onEvent(event) } }),
                                highlight = event.begin - now.toEpochMilli() in 0..SOON_MS || event.begin <= now.toEpochMilli(),
                            )
                            else -> GlanceLine(stringResource(R.string.w_glance_no_events), Modifier.tap(openCalendar, callbacks?.onDate))
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
                        modifier = Modifier.tap(openWeather, callbacks?.onWeather),
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
                            // Jak przy kalendarzu: od razu widać, co zrobić, zamiast samego słowa "Pogoda".
                            Text(
                                if (hasWeatherPermission) stringResource(R.string.w_glance_weather_loading) else stringResource(R.string.w_glance_weather_grant),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                    if (alarmAt != null) {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.tap(alarmLabel, callbacks?.onAlarm)) {
                            Text(
                                "⏰ " + describeAlarm(alarmAt, now),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Alarm ustawiony nie przez Zegar (np. przypomnienie z Kalendarza) — podpisujemy, skąd jest.
                            alarmApp?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
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
    val title = event.title.ifBlank { AppText.get(R.string.w_event_untitled) }
    if (event.allDay) return AppText.get(R.string.w_event_all_day, title)
    val begin = Instant.ofEpochMilli(event.begin).atZone(zone)
    return when {
        event.begin <= nowMs -> AppText.get(R.string.w_event_now, title)
        event.begin - nowMs <= SOON_MS -> AppText.get(R.string.w_event_in_min, ((event.begin - nowMs) / 60_000).coerceAtLeast(1), title)
        begin.toLocalDate() == now.atZone(zone).toLocalDate() -> "${begin.format(HourMinute)} $title"
        else -> AppText.get(R.string.w_event_tomorrow, begin.format(HourMinute), title)
    }
}

// Budzik w ciągu doby: sama godzina; dalej: dzień tygodnia + godzina ("pn 06:30").
private fun describeAlarm(at: Long, now: Instant): String {
    val alarm = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return if (at - now.toEpochMilli() < 24 * 3_600_000L) alarm.format(HourMinute)
    else alarm.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + alarm.format(HourMinute)
}
