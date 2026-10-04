package pl.rafal.contextlauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import pl.rafal.contextlauncher.data.AppInfo

// Co pasek wyszukiwania pokazuje w trybie czuwania. sealed interface ≈ zamknięta hierarchia klas
// (jak abstract record z kilkoma podtypami w C#) — kompilator pilnuje, że obsłużymy każdy rodzaj.
sealed interface SmartItem {
    val key: String

    // Propozycja / informacja o trybie: zostaje na pasku (nie przewija się), dopóki nie odpowiesz ✓ / ✕.
    data class Prompt(
        override val key: String,
        val color: Long,
        val title: String,
        val subtitle: String,
        val acceptLabel: String,
        val onAccept: () -> Unit,
        val rejectLabel: String,
        val onReject: () -> Unit,
    ) : SmartItem

    // Ostatnio używane aplikacje (np. z ostatniej godziny) — dotknięcie ikony je otwiera.
    data class Recent(val apps: List<AppInfo>) : SmartItem {
        override val key get() = "recent:" + apps.joinToString { it.key }
    }

    // Nieprzeczytane powiadomienie aplikacji — po kolei, każda aplikacja osobno.
    data class Notification(val app: AppInfo) : SmartItem {
        override val key get() = "notify:" + app.key
    }

    // Krótka informacja z symbolem (wydarzenie, budzik, bateria); onClick = co otworzyć (null = nic).
    data class Info(override val key: String, val symbol: String, val text: String, val onClick: (() -> Unit)?) : SmartItem
}

// Pasek "Szukaj" w trybie czuwania: zamiast samego napisu po kolei pokazuje to, co teraz przydatne.
// Dotknięcie lupy (albo pustego paska) = wyszukiwarka z klawiaturą; dotknięcie treści = jej akcja.
// Przewijanie działa tylko, gdy launcher jest widoczny (repeatOnLifecycle) — w tle nie zużywa baterii.
@Composable
fun SmartSearchBar(
    items: List<SmartItem>,
    pinned: SmartItem.Prompt?,
    onSearch: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var index by remember { mutableIntStateOf(0) }
    val lifecycle = (LocalContext.current as? LifecycleOwner)?.lifecycle
    val count = items.size
    LaunchedEffect(count, lifecycle) {
        if (count > 1) {
            if (lifecycle != null) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        delay(5_000)
                        index = (index + 1) % count
                    }
                }
            } else {
                // Kontekst bez cyklu życia (np. opakowany) — przewijamy bez wstrzymywania w tle.
                while (true) {
                    delay(5_000)
                    index = (index + 1) % count
                }
            }
        }
    }
    val current: SmartItem? = pinned ?: items.getOrNull(index % count.coerceAtLeast(1))
    val border = pinned?.let { Color(it.color).copy(alpha = 0.7f) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(28.dp)) else Modifier)
            .padding(start = 6.dp, end = 6.dp),
    ) {
        // Lupa: zawsze otwiera wyszukiwanie (klawiatura dopiero tutaj). Przy przypiętej propozycji chowamy ją,
        // żeby tytuł zmieścił się obok ✓ / ✕ (szuflada i tak jest pod przyciskiem obok).
        if (pinned == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSearch)
                    .semantics { contentDescription = "Szukaj aplikacji" },
            ) { Text("⌕", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
        AnimatedContent(
            targetState = current,
            contentKey = { it?.key ?: "empty" },
            transitionSpec = {
                (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(260))) togetherWith
                    (slideOutVertically(tween(260)) { -it / 2 } + fadeOut(tween(200)))
            },
            label = "pasek czuwania",
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { item ->
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                when (item) {
                    null -> Text(
                        "Szukaj aplikacji",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onSearch)
                            .padding(top = 16.dp),
                    )
                    is SmartItem.Prompt -> PromptContent(item)
                    is SmartItem.Recent -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🕘", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            item.apps.forEach { app ->
                                Image(
                                    app.icon,
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onLaunch(app) }
                                        .padding(2.dp),
                                )
                            }
                        }
                    }
                    is SmartItem.Notification -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onLaunch(item.app) },
                    ) {
                        Box {
                            Image(item.app.icon, contentDescription = null, modifier = Modifier.size(30.dp))
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Nowe powiadomienie", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                item.app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    is SmartItem.Info -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .then(if (item.onClick != null) Modifier.clickable(onClick = item.onClick) else Modifier.clickable(onClick = onSearch)),
                    ) {
                        Text(item.symbol, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// Propozycja trybu (dawniej osobny pasek nad dolnym rzędem, zasłaniający menu): tytuł, powód, ✓ / ✕.
@Composable
private fun PromptContent(item: SmartItem.Prompt) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
        ModeDot(item.color)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RoundAction("✓", Color(0xFF4FC27E), item.acceptLabel, item.onAccept)
        Spacer(Modifier.width(2.dp))
        RoundAction("✕", MaterialTheme.colorScheme.error, item.rejectLabel, item.onReject)
    }
}
