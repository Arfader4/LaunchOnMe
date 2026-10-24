package pl.rafal.contextlauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.rafal.contextlauncher.AppText
import pl.rafal.contextlauncher.R

// Jak długo ukrywać odrzuconą sugestię.
enum class DismissDuration(@StringRes private val labelRes: Int) {
    ONE_HOUR(R.string.data_dismiss_one_hour), THREE_HOURS(R.string.data_dismiss_three_hours), UNTIL_TOMORROW(R.string.data_dismiss_until_tomorrow);

    val label: String get() = AppText.get(labelRes)
}

// 1.6.2: jak pokazać odliczanie automatu (ile zostało do samodzielnego przełączenia trybu).
enum class AutoCountdown(@StringRes private val labelRes: Int) {
    NONE(R.string.data_countdown_none), BAR(R.string.data_countdown_bar), RING(R.string.data_countdown_ring);

    val label: String get() = AppText.get(labelRes)
}

// 1.7.1: skąd sortowanie "najczęściej używane" bierze dane: własny licznik uruchomień (per OnMode)
// albo czas użycia z systemu (UsageStatsManager, 30 dni; wymaga ręcznie nadanego dostępu).
enum class UsageSource(@StringRes private val labelRes: Int) {
    LAUNCHER(R.string.data_usage_launcher), SYSTEM(R.string.data_usage_system);

    val label: String get() = AppText.get(labelRes)
}

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
    val onModesNamesDone = bool("onmodes_names_v1", false) // 1.6.3: wbudowane tryby przemianowane na "… On Mode"
    // 1.6.2: ile sekund warunki muszą pasować, zanim automat sam przełączy (0 = od razu; dawniej stałe 60 s).
    val autoDelaySec = Setting("auto_delay_sec", 60, { k, d -> getInt(k, d) }, { k, v -> putInt(k, v) })
    val autoCountdown = Setting(
        "auto_countdown",
        AutoCountdown.BAR,
        { k, d -> runCatching { AutoCountdown.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) },
    )
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
    // 1.7.0: szuflada — ustawienia ogólne, nadpisania per OnMode (DrawerSettings) i własna kolejność (DrawerOrder), JSON.
    val drawerDefault = Setting("drawer_default", "", { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) })
    val drawerSettings = Setting("drawer_settings", "{}", { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) })
    val drawerOrders = Setting("drawer_orders", "{}", { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) })
    val folderSorts = Setting("folder_sorts", "{}", { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) }) // 1.7.1: FolderSort
    val usageSource = Setting(
        "usage_source",
        UsageSource.LAUNCHER,
        { k, d -> runCatching { UsageSource.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) },
    )
    // Aplikacje ręcznie usunięte z sekcji "Nowe" (AppInfo.key).
    val drawerNewDismissed = Setting(
        "drawer_new_dismissed",
        emptySet<String>(),
        { k, d -> getStringSet(k, d).orEmpty().toSet() },
        { k, v -> putStringSet(k, v) },
    )
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

    // --- 1.11.0: KeepMeOn (oszczędzanie energii) ---
    // Tryb pełniący rolę KeepMeOn (-1 = jeszcze nie utworzony) i co się dzieje, gdy jest aktywny.
    val keepMeOnModeId = Setting("keepmeon_mode_id", -1L, { k, d -> getLong(k, d) }, { k, v -> putLong(k, v) })
    val keepMeOnBattery = Setting("keepmeon_battery", 20, { k, d -> getInt(k, d) }, { k, v -> putInt(k, v) }) // próg %, 0 = bez reguły
    val keepMeOnAuto = bool("keepmeon_auto", true)            // włącz sam przy słabej baterii, nawet bez automatu trybów
    val keepMeOnLock = bool("keepmeon_lock", true)            // wyjście z KeepMeOn wymaga odcisku palca / PIN-u
    val keepMeOnNoMotion = bool("keepmeon_no_motion", true)   // bez animacji
    val keepMeOnNoWallpaper = bool("keepmeon_no_wallpaper", true) // czarne tło zamiast tapety
    val keepMeOnPauseWidgets = bool("keepmeon_pause_widgets", true) // widżety innych aplikacji i pogoda nie odświeżają się

    // --- Kopia i skróty ---
    val modeShortcuts = bool("mode_shortcuts", true)      // skróty "Włącz tryb …" dla innych aplikacji i procedur

    companion object {
        val AUTO_DELAY_OPTIONS = listOf(0, 5, 10, 15, 30, 45, 60) // sekundy do wyboru w Ustawieniach
        val KEEPMEON_BATTERY_OPTIONS = listOf(0, 10, 15, 20, 30) // próg baterii KeepMeOn (%), 0 = bez reguły
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
