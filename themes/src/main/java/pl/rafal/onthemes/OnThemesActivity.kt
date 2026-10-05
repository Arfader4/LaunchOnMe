package pl.rafal.onthemes

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// OnThemes — aplikacja motywów z własną ikoną w szufladzie (ten sam APK co launcher).
// T1: galeria motywów (wybór motywu globalnego), jasność i podgląd, jakie motywy mają tryby.
// Podgląd na żywo, motyw per tryb i kreator przychodzą w T4.
class OnThemesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OnThemesText.init(this) // na wypadek startu bez klasy aplikacji launchera (nie powinno się zdarzyć)
        enableEdgeToEdge()
        setContent { OnThemesApp() }
    }

    override fun onResume() {
        super.onResume()
        OnThemes.refreshSystemColors(this) // motyw "Systemowy": tapeta / paleta mogła się zmienić
    }
}

@Composable
private fun OnThemesApp() {
    val store = ThemeStore.get(LocalContext.current)
    val mode by store.themeMode.collectAsState()
    val theme by store.defaultTheme.collectAsState()
    val dark = mode.isDark(isSystemInDarkTheme())
    val target = theme.colorScheme(dark, null)

    // Płynne przejście kolorów po wybraniu innego motywu (jak w launcherze).
    @Composable
    fun animate(c: Color): Color {
        val v by animateColorAsState(c, animationSpec = tween(450), label = "kolor OnThemes")
        return v
    }
    val colors = target.copy(
        primary = animate(target.primary),
        onPrimary = animate(target.onPrimary),
        background = animate(target.background),
        onBackground = animate(target.onBackground),
        surface = animate(target.surface),
        onSurface = animate(target.onSurface),
        surfaceVariant = animate(target.surfaceVariant),
        onSurfaceVariant = animate(target.onSurfaceVariant),
        outline = animate(target.outline),
    )

    // Ikony paska statusu: ciemne na jasnym tle, jasne na ciemnym.
    val view = LocalView.current
    val activity = view.context as? Activity
    if (activity != null && !view.isInEditMode) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).apply {
                val lightBars = target.background.luminance() > 0.5f
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, LocalThemeSpec provides theme) {
            Box(Modifier.fillMaxSize().background(colors.background)) {
                GalleryScreen(store, mode, theme, dark)
            }
        }
    }
}

@Composable
private fun GalleryScreen(store: ThemeStore, mode: ThemeMode, selected: ThemeSpec, dark: Boolean) {
    // Tryby z launchera (przez OnThemesHost). Wczytujemy przy starcie i po zmianie motywu globalnego.
    var modes by remember { mutableStateOf<List<HostMode>?>(null) }
    LaunchedEffect(selected.id) {
        modes = runCatching { OnThemes.host?.modes() }.getOrNull() ?: emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ot_app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.ot_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.ot_brightness)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = m == mode, onClick = { store.setThemeMode(m) }, label = { Text(m.label) })
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle(stringResource(R.string.ot_global_theme))
                Text(
                    stringResource(R.string.ot_global_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(Themes.all, key = { it.id }) { spec ->
            ThemeCard(spec = spec, dark = dark, selected = spec == selected, onClick = { store.setDefaultTheme(spec) })
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle(stringResource(R.string.ot_modes))
                Text(
                    stringResource(R.string.ot_modes_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val list = modes
        if (list != null && list.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.ot_modes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (list != null) {
            items(list, key = { it.id }) { m -> ModeRow(m, dark) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
}

// Karta motywu: miniatura ekranu w jego kolorach + nazwa + krótka paleta (po odcieniu).
@Composable
private fun ThemeCard(spec: ThemeSpec, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val selectedLabel = stringResource(R.string.ot_selected)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
            .semantics { if (selected) contentDescription = selectedLabel },
    ) {
        MiniPreview(spec, dark)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    spec.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                spec.palette.take(8).forEach { c ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
                    )
                }
            }
        }
    }
}

// Miniatura launchera w kolorach motywu: tło, karta z dwiema "liniami tekstu", akcent i trzy znaczki z palety.
@Composable
private fun MiniPreview(spec: ThemeSpec, dark: Boolean) {
    val r = spec.roles(dark)
    val isDark = spec.isDark(dark)
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .size(width = 104.dp, height = 74.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(r.background))
            .border(1.dp, Color(r.outline), RoundedCornerShape(12.dp))
            .padding(7.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(Color(r.surface))
                .padding(5.dp),
        ) {
            Box(Modifier.size(width = 46.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(r.onBackground)))
            Box(Modifier.size(width = 30.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(Color(r.onSurfaceVariant)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            spec.palette.take(3).forEach { c ->
                val outline = badgeOutline(isDark, spec.badge)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(15.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(badgeBackground(c, isDark, spec.badge))
                        .border(1.dp, outline ?: Color.Transparent, RoundedCornerShape(5.dp)),
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(badgeSymbol(isDark, spec.badge, c)))
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 22.dp, height = 10.dp).clip(RoundedCornerShape(5.dp)).background(Color(r.primary)))
        }
    }
}

// Wiersz trybu: znaczek (jak w launcherze), nazwa i motyw, którego tryb używa.
@Composable
private fun ModeRow(mode: HostMode, dark: Boolean) {
    val spec = Themes.find(mode.themeId)
    val badgeDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Znaczek w stylu motywu, którego tryb używa (własny albo globalny).
    val style = (spec ?: LocalThemeSpec.current).badge
    val outline = badgeOutline(badgeDark, style)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(badgeBackground(mode.color, badgeDark, style))
                .border(1.dp, outline ?: Color.Transparent, RoundedCornerShape(11.dp)),
        ) {
            Icon(
                painter = painterResource(mode.iconRes),
                contentDescription = null,
                tint = badgeSymbol(badgeDark, style, mode.color),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(mode.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        if (spec != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                spec.swatches(dark).forEach { c ->
                    Box(Modifier.padding(end = 2.dp).size(10.dp).clip(CircleShape).background(c))
                }
                Spacer(Modifier.width(6.dp))
                Text(spec.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                stringResource(R.string.ot_mode_global),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
