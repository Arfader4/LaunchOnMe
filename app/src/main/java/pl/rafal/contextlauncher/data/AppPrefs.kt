package pl.rafal.contextlauncher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Jak długo ukrywać odrzuconą sugestię.
enum class DismissDuration(val label: String) { ONE_HOUR("1 godz."), THREE_HOURS("3 godz."), UNTIL_TOMORROW("Do jutra") }

// Ogólne ustawienia launchera (ekran Ustawienia). Każde ustawienie to StateFlow, więc UI odświeża się samo.
// Jedna instancja na proces (singleton): kafelek, skróty i automat muszą widzieć te same wartości co ekran.
// Wcześniej każdy tworzył własną kopię i np. launcher nie wiedział, że kafelek właśnie zmienił tryb.
class AppPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("general", Context.MODE_PRIVATE)

    // Mała klasa pomocnicza: jedno ustawienie = klucz + StateFlow + zapis. Unikamy powtarzania tego samego kodu 10 razy.
    inner class Setting<T>(
        private val key: String,
        default: T,
        read: SharedPreferences.(String, T) -> T,
        private val write: SharedPreferences.Editor.(String, T) -> Unit,
    ) {
        private val state = MutableStateFlow(prefs.read(key, default))
        val flow: StateFlow<T> = state.asStateFlow()
        val value: T get() = state.value

        fun set(value: T) {
            prefs.edit().also { it.write(key, value) }.apply()
            state.value = value
        }
    }

    private fun bool(key: String, default: Boolean) =
        Setting(key, default, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

    // Tryb pełniący rolę strony głównej (np. "Start"); -1 = brak.
    private val home = Setting(KEY_HOME, NONE, { k, d -> getLong(k, d) }, { k, v -> putLong(k, v) })
    val homeModeId: StateFlow<Long?> get() = homeIdFlow
    private val homeIdFlow = MutableStateFlow(home.value.takeIf { it != NONE })

    fun setHomeModeId(id: Long?) {
        home.set(id ?: NONE)
        homeIdFlow.value = id
    }

    // --- Ogólne ---
    val leftHanded = bool("left_handed", false)           // przełącznik trybu po lewej zamiast po prawej
    val returnToHome = bool("return_to_home", true)       // po zakończeniu trybu: strona główna (true) albo poprzedni tryb

    // --- Tryby i sugestie ---
    val autoSwitch = bool("auto_switch", false)           // przełączaj sam zamiast tylko podpowiadać
    val applyPhoneSettings = bool("apply_phone_settings", true)
    val dismissDuration = Setting(
        "dismiss_duration",
        DismissDuration.THREE_HOURS,
        { k, d -> runCatching { DismissDuration.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) },
    )

    // Tryby z wyjątkiem "zawsze pytaj" (automat ich nie włącza, tylko podpowiada).
    val alwaysAskModes = Setting(
        "always_ask_modes",
        emptySet<String>(),
        { k, d -> getStringSet(k, d).orEmpty().toSet() },
        { k, v -> putStringSet(k, v) },
    )

    // --- Wygląd ---
    val uniformLook = bool("uniform_look", false)         // wszystkie tryby w domyślnym motywie
    val showLabels = bool("show_labels", true)            // podpisy pod ikonami aplikacji
    val showFolderLabels = bool("show_folder_labels", true) // nazwy folderów na widżetach folderów
    val tutorialDone = bool("tutorial_done", false)       // samouczek gestów obejrzany (albo pominięty)
    val folderAtBottom = bool("folder_at_bottom", true)   // folder z karty otwiera się u dołu (przy kciuku) zamiast na środku
    private fun intPref(key: String, default: Int) = Setting(key, default, { k, d -> getInt(k, d) }, { k, v -> putInt(k, v) })
    val appIconCells = intPref("app_icon_cells", 2)      // ikona aplikacji na karcie: 1 = kratka 1×1, 2 = 2×2
    val maxPages = intPref("max_pages", 3)              // ile stron może mieć karta trybu (przesuwanie w bok)
    // Układ kart per tryb (odstępy, własny rozmiar ikon) — JSON, patrz ModeLayout.
    val modeLayouts = Setting("mode_layouts", "{}", { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) })
    val labelScale = intPref("label_scale", 100)         // rozmiar podpisów w %: 85 / 100 / 115
    val notificationDots = bool("notification_dots", true) // kropka na ikonie, gdy aplikacja ma powiadomienie
    val showWallpaper = bool("show_wallpaper", false)     // tapeta systemu w tle launchera zamiast jednolitego koloru
    private fun int(key: String, default: Int) = Setting(key, default, { k, d -> getInt(k, d) }, { k, v -> putInt(k, v) })
    val wallpaperDim = int("wallpaper_dim", 35)           // przyciemnienie tapety w % (czytelność ikon)
    val widgetOpacity = int("widget_opacity", 100)
    val iconShape = Setting("icon_shape", "ROUNDED", { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) }) // kształt ikon trybów
    val widgetCorner = int("widget_corner", 20)          // zaokrąglenie rogów widżetów w dp (0 = kwadratowe)        // tło widżetów launchera: 100 pełne, 60 półprzezroczyste, 0 brak

    // --- Tryb na czas ---
    // Do kiedy trwa tryb włączony "na czas" (0 = nie trwa) i do którego trybu potem wrócić (-1 = strona główna).
    val timedUntil = Setting("timed_until", 0L, { k, d -> getLong(k, d) }, { k, v -> putLong(k, v) })
    val timedReturnTo = Setting("timed_return_to", -1L, { k, d -> getLong(k, d) }, { k, v -> putLong(k, v) })

    // Kiedy użytkownik ostatnio sam zmienił tryb — automat czeka potem 30 minut, żeby nie "walczyć" z człowiekiem.
    val lastManualSwitch = Setting("last_manual_switch", 0L, { k, d -> getLong(k, d) }, { k, v -> putLong(k, v) })
    // Tryb, z którego użytkownik ręcznie wyszedł — automat nie wciska go z powrotem przez 30 minut.
    val lastManualLeft = Setting("last_manual_left", -1L, { k, d -> getLong(k, d) }, { k, v -> putLong(k, v) })

    // Blokady i ukrycia kopiowane do każdego NOWEGO trybu. Wpisy "BLOCK|pakiet#profil" albo "HIDE|pakiet#profil".
    val newModeRestrictions = Setting(
        "new_mode_restrictions",
        emptySet<String>(),
        { k, d -> getStringSet(k, d).orEmpty().toSet() },
        { k, v -> putStringSet(k, v) },
    )

    // --- Kopia i skróty ---
    val modeShortcuts = bool("mode_shortcuts", true)      // skróty "Włącz tryb …" dla innych aplikacji i procedur

    companion object {
        private const val KEY_HOME = "home_mode_id"
        private const val NONE = -1L

        @Volatile private var instance: AppPrefs? = null

        // Leniwy singleton z podwójnym sprawdzeniem — jak Lazy<T> w C#.
        fun get(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context.applicationContext).also { instance = it }
            }
    }
}
