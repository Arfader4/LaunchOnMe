package pl.rafal.onthemes

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// OnThemes — aplikacja motywów z własną ikoną w szufladzie (ten sam APK co launcher).
// Ekrany: galeria (podgląd, jasność, motywy, tryby) → ekran motywu (podgląd na żywo, globalny / dla trybów,
// duplikuj) → kreator motywu własnego. Nawigacja to prosty stos ekranów w stanie Compose (bez biblioteki).
class OnThemesActivity : ComponentActivity() {
    // Prośba z launchera: otwórz wybór motywu dla trybu albo kreator (extras w intencji).
    private val request = mutableStateOf<OnThemesRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OnThemesText.init(this) // na wypadek startu bez klasy aplikacji launchera (nie powinno się zdarzyć)
        enableEdgeToEdge()
        request.value = OnThemesRequest.from(intent)
        setContent {
            val req by request
            OnThemesApp(req, onRequestHandled = { request.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request.value = OnThemesRequest.from(intent)
    }

    override fun onResume() {
        super.onResume()
        OnThemes.refreshSystemColors(this) // motyw "Systemowy": tapeta / paleta mogła się zmienić
    }
}

// Co otworzyć po starcie z launchera.
internal data class OnThemesRequest(val modeId: Long?, val editId: String?) {
    companion object {
        fun from(intent: Intent?): OnThemesRequest? {
            if (intent == null) return null
            val modeId = intent.getLongExtra(OnThemes.EXTRA_MODE_ID, -1L).takeIf { it >= 0 }
            val editId = intent.getStringExtra(OnThemes.EXTRA_EDIT_ID)
            return if (modeId == null && editId == null) null else OnThemesRequest(modeId, editId)
        }
    }
}

// Ekran na stosie nawigacji.
private sealed interface Screen {
    data object Gallery : Screen
    data class Detail(val id: String) : Screen
    data class Editor(val def: CustomThemeDef) : Screen
}

@Composable
private fun OnThemesApp(request: OnThemesRequest?, onRequestHandled: () -> Unit) {
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

    // Tryby z launchera (przez OnThemesHost). Wczytujemy na starcie i po każdej zmianie (modesVersion++).
    var modes by remember { mutableStateOf<List<HostMode>?>(null) }
    var modesVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(modesVersion, theme.id) {
        modes = try {
            OnThemes.host?.modes() ?: emptyList()
        } catch (e: CancellationException) {
            throw e // anulowanie korutyny przepuszczamy dalej (jak OperationCanceledException w .NET)
        } catch (e: Exception) {
            emptyList()
        }
    }

    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Gallery)) }
    var modeForPicker by remember { mutableStateOf<HostMode?>(null) }
    val scope = rememberCoroutineScope()
    fun push(s: Screen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { pop() }

    // Prośba z launchera: wybór motywu trybu (gdy tryby już wczytane) albo kreator.
    val copyName = stringResource(R.string.ot_copy_name)
    LaunchedEffect(request, modes) {
        val req = request
        val list = modes
        if (req != null && list != null) {
            if (req.modeId != null) modeForPicker = list.firstOrNull { it.id == req.modeId }
            if (req.editId != null) {
                val def = CustomThemes.def(req.editId) ?: CustomThemeDef(req.editId, null, CustomTheme.colors)
                stack = listOf(Screen.Gallery, Screen.Editor(def))
            }
            onRequestHandled()
        }
    }

    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, LocalThemeSpec provides theme) {
            Box(Modifier.fillMaxSize().background(colors.background)) {
                when (val screen = stack.last()) {
                    is Screen.Gallery -> GalleryScreen(
                        store = store,
                        mode = mode,
                        global = theme,
                        dark = dark,
                        modes = modes,
                        onOpen = { push(Screen.Detail(it)) },
                        onNewCustom = {
                            val base = CustomTheme.colors
                            push(Screen.Editor(CustomThemeDef(CustomThemes.nextId(), null, base)))
                        },
                        onPickForMode = { modeForPicker = it },
                    )
                    is Screen.Detail -> ThemeDetailScreen(
                        store = store,
                        startId = screen.id,
                        dark = dark,
                        modes = modes,
                        onBack = { pop() },
                        onEdit = { id -> CustomThemes.def(id)?.let { push(Screen.Editor(it)) } },
                        onDuplicate = { spec, previewDark ->
                            // Kopia jako motyw własny: 4 kolory z wersji, którą widać na podglądzie.
                            val r = spec.roles(previewDark)
                            val def = CustomThemeDef(
                                id = CustomThemes.nextId(),
                                name = copyName.format(spec.label).take(30),
                                colors = CustomColors(r.background, r.surface, r.primary, r.onBackground),
                                badge = if (spec.badge == BadgeStyle.METAL) BadgeStyle.TINTED else spec.badge,
                            )
                            push(Screen.Editor(def))
                        },
                        onModesChanged = { modesVersion++ },
                    )
                    is Screen.Editor -> CustomEditorScreen(
                        store = store,
                        initial = screen.def,
                        modes = modes,
                        onBack = { pop() },
                        onSaved = { id ->
                            // Po zapisie: ekran tego motywu (zamiast kreatora) — od razu "Użyj" / "Dla trybów".
                            stack = stack.dropLast(1).filterNot { it is Screen.Detail && it.id == id } + Screen.Detail(id)
                        },
                    )
                }
            }
            val picked = modeForPicker
            if (picked != null) {
                ModeThemeDialog(
                    mode = picked,
                    global = theme,
                    onPick = { themeId ->
                        modeForPicker = null
                        scope.launch {
                            OnThemes.host?.setModeTheme(picked.id, themeId)
                            modesVersion++
                        }
                    },
                    onDismiss = { modeForPicker = null },
                )
            }
        }
    }
}
