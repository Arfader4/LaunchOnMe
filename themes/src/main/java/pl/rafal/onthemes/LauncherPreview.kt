package pl.rafal.onthemes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Podgląd launchera "na żywo" w kolorach motywu: nagłówek trybu, karta z widżetem, siatka ikon,
// pasek wyszukiwania, klawisz ON i znaczki Twoich trybów. Wszystko z ról motywu, jak w prawdziwym launcherze.
@Composable
internal fun LauncherPreview(look: PreviewLook, modes: List<HostMode>, sheen: Boolean, modifier: Modifier = Modifier) {
    val r = look.roles
    val bg = Color(r.background)
    val text = Color(r.onBackground)
    val dim = Color(r.onSurfaceVariant)
    val active = modes.firstOrNull()
    val activeColor = active?.color ?: look.palette.firstOrNull() ?: r.primary
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .size(width = 228.dp, height = 420.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .border(1.dp, Color(r.outline), RoundedCornerShape(28.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // pasek statusu
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("9:41", color = text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 22.dp, height = 7.dp).clip(RoundedCornerShape(3.dp)).background(dim))
        }
        // nagłówek trybu
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemedBadge(look, activeColor, active?.iconRes, 24.dp, RoundedCornerShape(8.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    active?.name ?: stringResource(R.string.ot_preview_mode),
                    color = text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.ot_preview_date), color = dim, fontSize = 10.sp)
            }
        }
        // karta z "widżetem"
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(r.surface))
                .padding(12.dp),
        ) {
            Text("12:45", color = text, fontSize = 26.sp, fontWeight = FontWeight.Light)
            Text(stringResource(R.string.ot_preview_event), color = dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(r.primary))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.ot_preview_button), color = Color(r.onPrimary), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // siatka "aplikacji": kolory z palety na powierzchni
        val apps = (look.palette + look.palette).take(8)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { c ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(r.surfaceVariant)),
                        ) {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(c)))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // znaczki trybów (jak lista / łuk trybów)
        if (modes.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.take(6).forEach { m -> ThemedBadge(look, m.color, m.iconRes, 26.dp, CircleShape) }
            }
        }
        // pasek wyszukiwania + klawisz ON
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(r.surfaceVariant))
                    .padding(horizontal = 12.dp),
            ) {
                Text("🔍", fontSize = 12.sp, color = dim)
            }
            Spacer(Modifier.width(10.dp))
            ThemedBadge(look, activeColor, active?.iconRes, 46.dp, RoundedCornerShape(36), sheen = sheen)
        }
    }
}
