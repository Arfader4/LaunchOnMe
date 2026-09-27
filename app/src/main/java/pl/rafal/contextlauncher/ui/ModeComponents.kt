package pl.rafal.contextlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.db.ModeEntity

// Kolory do wyboru dla nowego trybu (te same co w makiecie).
val ModeColors = listOf(0xFFF0A844, 0xFF8FB2FF, 0xFF7FD6AE, 0xFFFF9A7F, 0xFFB9A5FF, 0xFFD4AF37, 0xFF4FB3BF, 0xFFE0736B, 0xFF8B5A2B)

@Composable
fun ModeDot(color: Long, size: Int = 10) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

// Nazwa trybu u góry karty. Dotknięcie otwiera listę trybów.
@Composable
fun ModeHeader(mode: ModeEntity?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
    ) {
        if (mode != null) ModeDot(mode.color, size = 12)
        Spacer(Modifier.width(10.dp))
        Text(
            text = mode?.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(6.dp))
        Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Arkusz wysuwany od dołu z listą trybów.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModePickerSheet(
    modes: List<ModeEntity>,
    activeId: Long?,
    onSelect: (ModeEntity) -> Unit,
    onNewMode: () -> Unit,
    onManage: (ModeEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "Tryby",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
            modes.forEach { mode ->
                val isActive = mode.id == activeId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { onSelect(mode) }
                        .heightIn(min = 52.dp)
                        .padding(start = 12.dp),
                ) {
                    ModeDot(mode.color)
                    Spacer(Modifier.width(12.dp))
                    Text(mode.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (isActive) {
                        Text("aktywny", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    // Ustawienia trybu: reguły sugestii, nazwa, usuwanie.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onManage(mode) }
                            .semantics { contentDescription = "Ustawienia trybu ${mode.name}" },
                    ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                }
            }
            TextButton(onClick = onNewMode, modifier = Modifier.padding(top = 8.dp)) {
                Text("+ Nowy tryb")
            }
        }
    }
}

// Okno tworzenia trybu: nazwa + kolor.
@Composable
fun NewModeDialog(onConfirm: (name: String, color: Long, icon: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableLongStateOf(ModeColors.first()) }
    var icon by remember { mutableStateOf(ModeIcon.STAR) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Nowy tryb") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa") },
                    singleLine = true,
                )
                IconPicker(selected = icon, color = color, onSelect = { icon = it })
                ColorSwatches(colors = ModeColors, selected = color, onSelect = { c -> if (c != null) color = c })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), color, icon.key) }) {
                Text("Utwórz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
}
