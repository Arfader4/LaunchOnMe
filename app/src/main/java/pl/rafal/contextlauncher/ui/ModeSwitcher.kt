package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.db.ModeEntity

// Rozwijany przycisk trybu na dole ekranu (w zasięgu kciuka).
// Dotknięcie rozwija listę trybów do góry; ⋮ przy trybie otwiera jego ustawienia.
@Composable
fun ModeSwitcherButton(
    active: ModeEntity?,
    modes: List<ModeEntity>,
    closeSignal: Int, // zmiana tej liczby (np. naciśnięcie Home) zamyka listę
    onSelect: (ModeEntity) -> Unit,
    onManage: (ModeEntity) -> Unit,
    onNewMode: () -> Unit,
    onTimed: ((ModeEntity) -> Unit)? = null, // "włącz na czas" (⏱ przy trybie)
    compact: Boolean = false, // sama ikona trybu (nazwa jest już w nagłówku), żeby zmieścić wyszukiwarkę
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(closeSignal) { expanded = false }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { expanded = true }
                .semantics { contentDescription = "Zmień tryb, aktywny: ${active?.name.orEmpty()}" }
                .padding(horizontal = if (compact) 10.dp else 0.dp)
                .padding(start = if (compact) 0.dp else 10.dp, end = if (compact) 0.dp else 16.dp),
        ) {
            if (active != null) ModeBadge(active, size = 36.dp)
            if (!compact) {
                Spacer(Modifier.width(10.dp))
                Text(
                    active?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("▴", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // DropdownMenu sam otwiera się do góry, gdy pod przyciskiem brakuje miejsca.
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEach { mode ->
                val isActive = mode.id == active?.id
                DropdownMenuItem(
                    leadingIcon = { ModeBadge(mode, size = 30.dp) },
                    text = {
                        Text(
                            mode.name,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingIcon = {
                        Row {
                        if (onTimed != null && !isActive) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        expanded = false
                                        onTimed(mode)
                                    }
                                    .semantics { contentDescription = "Włącz ${mode.name} na czas" },
                            ) { Text("⏱", style = MaterialTheme.typography.titleMedium) }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable {
                                    expanded = false
                                    onManage(mode)
                                }
                                .semantics { contentDescription = "Ustawienia trybu ${mode.name}" },
                        ) { Text("⋮", style = MaterialTheme.typography.titleMedium) }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            DropdownMenuItem(
                text = { Text("+ Nowy tryb") },
                onClick = {
                    expanded = false
                    onNewMode()
                },
            )
        }
    }
}
