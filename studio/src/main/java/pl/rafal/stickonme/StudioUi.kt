package pl.rafal.stickonme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Wspólne klocki nowego wyglądu StickOnMe (makieta „edytor w stylu aparatu”): ikony konturowe, pasek narzędzi
// z podpisami, przyciski-pigułki i przełącznik segmentowy. Bez biblioteki material-icons (ciężka) — ikony to
// ścieżki SVG zamienione na ImageVector (jak Geometry.Parse w WPF).
object StudioIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.White), // kolor i tak nadaje Icon(tint = …)
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Check = icon("check", "M5 12l5 5 9-10")
    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3")
    val Redo = icon("redo", "M15 14l5-5-5-5", "M20 9H10a6 6 0 0 0 0 12h3")
    val Compare = icon("compare", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M12 5v14")
    val Subject = icon("subject", "M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z", "M19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z")
    val Brush = icon("brush", "M9 15l10-10a2.1 2.1 0 0 1 3 3L12 18", "M9 15c-3 0-5 2-5 5h4c2 0 4-2 4-2")
    val Crop = icon("crop", "M6 3v12a3 3 0 0 0 3 3h12", "M3 6h12a3 3 0 0 1 3 3v12")
    val Edge = icon("edge", "M20 4C10 4 5 10 5 19", "M5 19c5 0 11-3 13-9", "M9 14h5")
    val Shape = icon("shape", "M5 3h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M21 15.5a5.5 5.5 0 1 1-11 0a5.5 5.5 0 1 1 11 0z")
    val Frame = icon("frame", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M8 7h8a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z")
    val Rotate = icon("rotate", "M20 12a8 8 0 1 1-2.3-5.7", "M20 4v5h-5")
    val Resize = icon("resize", "M15 3h6v6", "M9 21H3v-6", "M21 3l-7 7", "M3 21l7-7")
}

// Okrągły przycisk z samą ikoną (✕, Cofnij, Ponów…): 44 dp, z opisem dla czytnika ekranu.
@Composable
fun StudioIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (outlined) Modifier.background(colors.surface).border(1.dp, colors.outlineVariant, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) colors.onSurface else colors.outline, modifier = Modifier.size(22.dp))
    }
}

// Główny przycisk ekranu („✓ Gotowe”): pigułka w kolorze akcentu.
@Composable
fun StudioPrimaryButton(label: String, enabled: Boolean = true, icon: ImageVector? = StudioIcons.Check, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.primary)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
        Text(label, color = colors.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// Pozycja dolnego paska narzędzi: ikona + podpis, zaznaczona = fioletowe tło (jak w makiecie).
@Composable
fun StudioRailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = if (selected) colors.onPrimaryContainer else colors.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        modifier = Modifier
            .width(70.dp)
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// Pigułka wyboru (kształt, ramka, poziom wygładzenia…): wyraźne obramowanie także gdy niezaznaczona.
@Composable
fun StudioChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.secondaryContainer else colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.secondary else colors.outline, RoundedCornerShape(20.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

// Przełącznik segmentowy (Dodaj | Usuń | Przesuń): jedna z kilku opcji, jak RadioButton-y w jednym pudełku.
@Composable
fun <T> StudioSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(22.dp))
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) colors.secondaryContainer else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    label,
                    color = if (on) colors.onSecondaryContainer else colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}

// Podpis sekcji w panelu opcji (np. „Wygładzenie”).
@Composable
fun StudioLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

// Dolny pasek otwartego narzędzia: ✕ anuluje zmiany z tego narzędzia, ✓ je zatwierdza (jak w edytorach w telefonie).
// Kolory jak w całym projekcie: ✕ czerwony, ✓ zielony.
@Composable
fun StudioStepBar(cancelLabel: String, applyLabel: String, enabled: Boolean, onCancel: () -> Unit, onApply: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.5f)
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, colors.error, RoundedCornerShape(24.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onCancel),
        ) {
            Icon(StudioIcons.Close, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
            Text(cancelLabel, color = colors.error, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.5f)
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.secondary)
                .clickable(enabled = enabled, role = Role.Button, onClick = onApply),
        ) {
            Icon(StudioIcons.Check, contentDescription = null, tint = colors.onSecondary, modifier = Modifier.size(20.dp))
            Text(applyLabel, color = colors.onSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
