package pl.rafal.contextlauncher.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.db.ModeEntity

// Co przyszło z "Udostępnij". Trzy przypadki, każdy z własnymi danymi.
sealed interface SharedContent {
    val suggestedTitle: String
    val description: String

    data class File(val uri: Uri, val subject: String?, val mimeType: String?) : SharedContent {
        val isImage: Boolean get() = mimeType?.startsWith("image/") == true

        override val suggestedTitle get() = subject.orEmpty() // pusta = weźmiemy nazwę pliku
        override val description get() = AppText.get(R.string.share_file_description)
    }

    data class Link(val url: String, val subject: String?) : SharedContent {
        override val suggestedTitle get() = subject.orEmpty()
        override val description get() = url
    }

    data class Note(val text: String, val subject: String?) : SharedContent {
        override val suggestedTitle get() = subject.orEmpty()
        override val description get() = text
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    content: SharedContent,
    modesFlow: Flow<List<ModeEntity>>,
    onPin: (mode: ModeEntity, title: String, asSticker: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    // Obrazek można przypiąć do OnHand albo położyć na karcie jako naklejkę.
    val canBeSticker = content is SharedContent.File && content.isImage
    var asSticker by remember { mutableStateOf(false) }
    val modes by modesFlow.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf(content.suggestedTitle) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    // Domyślnie aktywny tryb; użytkownik może wybrać inny.
    val selected = modes.firstOrNull { it.id == selectedId } ?: modes.maxByOrNull { it.lastActiveAt }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(20.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
            Text(
                content.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (canBeSticker) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !asSticker, onClick = { asSticker = false }, label = { Text("OnHand") })
                    FilterChip(selected = asSticker, onClick = { asSticker = true }, label = { Text(stringResource(R.string.share_sticker_on_card)) })
                }
            }
            if (asSticker) {
                Text(
                    stringResource(R.string.share_sticker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!asSticker) OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.share_name_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.share_mode), style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                modes.forEach { mode ->
                    val isSelected = mode.id == selected?.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .clickable { selectedId = mode.id }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp),
                    ) {
                        ModeDot(mode.color)
                        Spacer(Modifier.width(12.dp))
                        Text(mode.name, modifier = Modifier.weight(1f))
                        if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = selected != null,
                    onClick = { selected?.let { onPin(it, title.trim(), asSticker) } },
                ) { Text(stringResource(R.string.share_pin)) }
            }
        }
    }
}
