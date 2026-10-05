package pl.rafal.onthemes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

// Wszystko, czego potrzebuje podgląd: kolory ról, czy ciemno, styl znaczków, metal i krótka paleta.
// Osobno od ThemeSpec, żeby kreator mógł pokazać jeszcze niezapisany motyw (szkic).
data class PreviewLook(
    val roles: Roles,
    val dark: Boolean,
    val badge: BadgeStyle,
    val metal: Metal?,
    val palette: List<Long>,
)

fun ThemeSpec.look(dark: Boolean): PreviewLook = PreviewLook(roles(dark), isDark(dark), badge, metal, palette)

// Znaczek trybu w podglądach OnThemes — rysowany tak samo jak ModeBadge w launcherze (te same funkcje z Badges.kt).
// iconRes = null → zamiast symbolu kropka (np. kolor z palety, gdy brak trybów).
@Composable
internal fun ThemedBadge(
    look: PreviewLook,
    color: Long,
    @DrawableRes iconRes: Int?,
    size: Dp,
    shape: Shape,
    sheen: Boolean = false,
) {
    val rim = badgeRim(look.badge, look.metal)
    val outline = badgeOutline(look.dark, look.badge)
    val tint = badgeSymbol(look.dark, look.badge, color, look.metal)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(badgeBackground(color, look.dark, look.badge))
            .then(
                if (rim != null) Modifier.border((size * 0.05f).coerceIn(Dp(1f), Dp(3f)), rim, shape)
                else Modifier.border(Dp(1f), outline ?: Color.Transparent, shape),
            )
            .then(if (sheen && rim != null) Modifier.metalSheen() else Modifier),
    ) {
        if (iconRes != null) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.6f))
        } else {
            Box(Modifier.size(size * 0.3f).clip(CircleShape).background(tint))
        }
    }
}
