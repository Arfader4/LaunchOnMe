package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.contextlauncher.suggest.Suggestion
import pl.rafal.contextlauncher.system.ManualTask

// Komunikaty w nagłówku karty (tam, gdzie zwykle jest nazwa trybu). Ta sama wysokość co nagłówek,
// więc pojawienie się sugestii nie przesuwa układu karty.

private val AcceptGreen = Color(0xFF4FC27E)

// Sugestia: "Włączyć Podróż?" + powód, ✓ przełącza, ✗ chowa.
@Composable
fun SuggestionPrompt(
    suggestion: Suggestion,
    modes: List<ModeEntity>,
    activeMode: ModeEntity?,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    autoStatus: String? = null, // np. "automat przełączy za 40 s" — widać, co robi automat
    modifier: Modifier = Modifier,
) {
    // Para (kolor, tytuł, podtekst); ?: return — tryb z sugestii mógł zostać usunięty.
    val (color, title, subtitle) = when (suggestion) {
        is Suggestion.SwitchTo -> modes.firstOrNull { it.id == suggestion.modeId }?.let {
            Triple(it.color, "Włączyć ${it.name}?", suggestion.reason.replaceFirstChar { c -> c.uppercase() })
        }
        is Suggestion.EndMode -> modes.firstOrNull { it.id == suggestion.backToModeId }?.let {
            Triple(it.color, "Zakończyć ${activeMode?.name.orEmpty()}?", "Reguły już nie pasują · wróć do ${it.name}")
        }
    } ?: run {
        Spacer(modifier) // tryb z sugestii usunięto — zachowujemy miejsce, żeby ⋯ nie przeskoczyło
        return
    }

    val fullSubtitle = listOfNotNull(subtitle, autoStatus).joinToString(" · ")
    HeaderPrompt(color, title, fullSubtitle, "Przełącz", onAccept, "Odrzuć sugestię", onDismiss, modifier)
}

// Po automatycznym przełączeniu: ✓ zostaw, ✗ cofnij do poprzedniego trybu.
@Composable
fun AutoSwitchPrompt(
    mode: ModeEntity,
    previous: ModeEntity?,
    reason: String,
    onKeep: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeaderPrompt(
        color = mode.color,
        title = "Włączono: ${mode.name}",
        subtitle = reason.replaceFirstChar { it.uppercase() } + (previous?.let { " · ✕ wraca do ${it.name}" } ?: ""),
        acceptLabel = "Zostaw tryb",
        onAccept = onKeep,
        rejectLabel = "Cofnij",
        onReject = onUndo,
        modifier = modifier,
    )
}

@Composable
private fun HeaderPrompt(
    color: Long,
    title: String,
    subtitle: String,
    acceptLabel: String,
    onAccept: () -> Unit,
    rejectLabel: String,
    onReject: () -> Unit,
    modifier: Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color(color).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(start = 14.dp, end = 4.dp),
    ) {
        ModeDot(color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RoundAction("✓", AcceptGreen, acceptLabel, onAccept)
        Spacer(Modifier.width(4.dp))
        RoundAction("✕", MaterialTheme.colorScheme.error, rejectLabel, onReject)
    }
}

@Composable
private fun RoundAction(symbol: String, color: Color, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text(symbol, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

// Przypomnienie o ręcznych przełącznikach (Wi-Fi, Bluetooth…) jako mała ikonka z licznikiem obok ⋯.
// Dotknięcie rozwija listę; każda pozycja otwiera właściwy panel systemowy.
@Composable
fun ManualTasksChip(
    modeName: String,
    tasks: List<ManualTask>,
    onTask: (ManualTask) -> Unit,
    onDismiss: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { open = true }
                .semantics { contentDescription = "Tryb $modeName prosi o ${tasks.size} zmian w ustawieniach" },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    "${tasks.size}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Tryb $modeName prosi o:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            tasks.forEach { task ->
                DropdownMenuItem(
                    text = { Text(task.label) },
                    onClick = {
                        open = false
                        onTask(task)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Ukryj przypomnienie") },
                onClick = {
                    open = false
                    onDismiss()
                },
            )
        }
    }
}
