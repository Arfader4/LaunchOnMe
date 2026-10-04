package pl.rafal.contextlauncher.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.appKey
import pl.rafal.contextlauncher.data.db.AppRestrictionEntity
import pl.rafal.contextlauncher.data.AppRepository
import pl.rafal.contextlauncher.data.CardApp
import pl.rafal.contextlauncher.data.CardElement
import pl.rafal.contextlauncher.data.CardWidget
import pl.rafal.contextlauncher.data.CardCustomWidget
import pl.rafal.contextlauncher.data.CustomWidgetKind
import pl.rafal.contextlauncher.data.FolderApp
import pl.rafal.contextlauncher.data.FolderTree
import pl.rafal.contextlauncher.data.CardFolderData
import pl.rafal.contextlauncher.data.SHORTCUT_KEY
import pl.rafal.contextlauncher.data.parseAppKey
import pl.rafal.contextlauncher.data.ModeLayout
import pl.rafal.contextlauncher.data.ModePlan
import pl.rafal.contextlauncher.data.appItemFor
import pl.rafal.contextlauncher.data.STACKED_PAGE
import pl.rafal.contextlauncher.data.StackData
import pl.rafal.contextlauncher.data.PageSpace
import pl.rafal.contextlauncher.data.repairStacks
import pl.rafal.contextlauncher.data.autoFolderName
import pl.rafal.contextlauncher.data.db.FolderAppEntity
import pl.rafal.contextlauncher.data.db.FolderEntity
import pl.rafal.contextlauncher.data.CalendarReader
import pl.rafal.contextlauncher.data.GlanceEvent
import pl.rafal.contextlauncher.data.ContactsReader
import pl.rafal.contextlauncher.data.FavoriteContact
import pl.rafal.contextlauncher.system.QuickToggle
import pl.rafal.contextlauncher.system.QuickStates
import pl.rafal.contextlauncher.system.QuickToggles
import pl.rafal.contextlauncher.data.ModeTemplate
import pl.rafal.contextlauncher.data.ModeTemplates
import pl.rafal.contextlauncher.data.PinnedRepository
import pl.rafal.contextlauncher.data.AppPrefs
import pl.rafal.contextlauncher.data.Backup
import pl.rafal.contextlauncher.data.DismissDuration
import pl.rafal.contextlauncher.data.ModeShortcuts
import pl.rafal.contextlauncher.data.WallpaperStore
import pl.rafal.contextlauncher.suggest.chargingRuleParams
import pl.rafal.contextlauncher.suggest.batteryRuleParams
import kotlinx.coroutines.Job
import java.time.LocalDate
import pl.rafal.contextlauncher.data.Weather
import pl.rafal.contextlauncher.data.WeatherRepository
import pl.rafal.contextlauncher.data.StickerStore
import pl.rafal.contextlauncher.data.placeCustomWidget
import pl.rafal.contextlauncher.data.ModePhoneSettings
import pl.rafal.contextlauncher.system.ManualTask
import pl.rafal.contextlauncher.system.ModeActivation
import pl.rafal.contextlauncher.system.PairedDevice
import pl.rafal.contextlauncher.system.PhoneSettingsApplier
import pl.rafal.contextlauncher.system.SignalsReader
import pl.rafal.contextlauncher.suggest.Rule
import pl.rafal.contextlauncher.suggest.bluetoothRuleParams
import pl.rafal.contextlauncher.suggest.placeRuleParams
import pl.rafal.contextlauncher.suggest.wifiRuleParams
import pl.rafal.contextlauncher.data.ThemePrefs
import pl.rafal.contextlauncher.ModeTileService
import pl.rafal.contextlauncher.ui.theme.Palette
import pl.rafal.contextlauncher.ui.theme.ThemeMode
import pl.rafal.contextlauncher.layout.GridRect
import pl.rafal.contextlauncher.data.db.SuggestionRuleEntity
import pl.rafal.contextlauncher.suggest.ModeRule
import pl.rafal.contextlauncher.suggest.Suggestion
import pl.rafal.contextlauncher.suggest.SuggestionEngine
import pl.rafal.contextlauncher.suggest.calendarRuleParams
import pl.rafal.contextlauncher.suggest.timeRuleParams
import pl.rafal.contextlauncher.suggest.toRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import pl.rafal.contextlauncher.data.db.PinnedItemEntity
import pl.rafal.contextlauncher.data.db.CardItemEntity
import pl.rafal.contextlauncher.data.db.LauncherDatabase
import pl.rafal.contextlauncher.data.db.ModeEntity
import pl.rafal.contextlauncher.data.toRect
import pl.rafal.contextlauncher.data.widgets.LauncherWidgets
import pl.rafal.contextlauncher.data.widgets.WidgetProvider
import pl.rafal.contextlauncher.layout.CardGrid

// ViewModel trzyma stan ekranu i przeżywa obrót ekranu (jak ViewModel z MVVM w WPF/MAUI).
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    // Po zmianie listy aplikacji w systemie: oznacz do ponownego wczytania i od razu odśwież.
    private val repository = AppRepository(
        application,
        onAppsChanged = {
            appsDirty = true
            refresh()
        },
        // Komunikatory zmieniają skróty (ostatnie rozmowy) bardzo często — wtedy doczytujemy tylko skróty.
        onShortcutsChanged = { reloadShortcuts() },
    )
    private val database = LauncherDatabase.get(application)
    private val modeDao = database.modeDao()
    private val cardItemDao = database.cardItemDao()
    private val folderDao = database.folderDao()
    private val suggestionDao = database.suggestionDao()
    private val restrictionDao = database.appRestrictionDao()
    private val calendar = CalendarReader(application)
    private val widgets = LauncherWidgets.get(application)
    private val pinned = PinnedRepository(application)
    private val themePrefs = ThemePrefs.get(application)
    private val signalsReader = SignalsReader(application)
    private val appPrefs = AppPrefs.get(application)

    // Ustawienia ogólne dostępne dla ekranu Ustawień (każde to StateFlow + set()).
    val settings: AppPrefs get() = appPrefs
    private val weatherRepository = WeatherRepository(application)
    private val phoneSettings = PhoneSettingsApplier(application)

    // StateFlow ≈ właściwość z INotifyPropertyChanged: UI subskrybuje i odświeża się sam.
    private val allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    // Skróty aplikacji (tylko gdy jesteśmy domyślnym launcherem). Na karcie i w folderach działają jak ikony.
    private val _shortcuts = MutableStateFlow<List<AppInfo>>(emptyList())
    val shortcuts: StateFlow<List<AppInfo>> = _shortcuts.asStateFlow()
    fun hasShortcutAccess() = repository.hasShortcutAccess()
    private var shortcutsJob: Job? = null
    private var shortcutsLoaded = false

    // Jedno wczytywanie naraz: nowe zgłoszenie anuluje poprzednie, a krótka pauza zbiera serię zmian w jedną.
    private fun reloadShortcuts(debounceMs: Long = 400) {
        shortcutsJob?.cancel()
        shortcutsJob = viewModelScope.launch {
            delay(debounceMs)
            runCatching { repository.loadShortcuts() }.onSuccess {
                _shortcuts.value = it
                shortcutsLoaded = repository.hasShortcutAccess()
            }
        }
    }
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Licznik naciśnięć Home, gdy launcher już jest na wierzchu. UI reaguje na każdą zmianę.
    private val _homePresses = MutableStateFlow(0)
    val homePresses: StateFlow<Int> = _homePresses.asStateFlow()

    // --- Tryby ---

    val modes: StateFlow<List<ModeEntity>> =
        modeDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Aktywny = ostatnio włączony. maxByOrNull ≈ MaxBy z LINQ (.NET 6+).
    val activeMode: StateFlow<ModeEntity?> =
        modes.map { list -> list.maxByOrNull { it.lastActiveAt } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // --- Blokowanie i ukrywanie aplikacji ---

    // Wszystkie blokady ze wszystkich trybów (ekran zarządzania pokazuje też "zablokowana w: Praca, Studia").
    val restrictions: StateFlow<List<AppRestrictionEntity>> =
        restrictionDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Blokady aktywnego trybu jako słownik: klucz aplikacji → rodzaj (jak Dictionary<string, string>).
    private val activeRestrictions: StateFlow<Map<String, String>> =
        combine(activeMode, restrictions) { mode, all ->
            all.filter { it.modeId == mode?.id }.associate { appKey(it.packageName, it.userSerial) to it.kind }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Klucze zablokowanych aplikacji — ekran rysuje przy nich kłódkę.
    val blockedKeys: StateFlow<Set<String>> =
        activeRestrictions.map { map -> map.filterValues { it == AppRestrictionEntity.KIND_BLOCK }.keys }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private fun isHidden(app: AppInfo, map: Map<String, String>) = map[app.appKey] == AppRestrictionEntity.KIND_HIDE

    // Ukryte w aktywnym trybie — szuflada pokazuje je dopiero po "Pokaż ukryte".
    val hiddenApps: StateFlow<List<AppInfo>> =
        combine(allApps, activeRestrictions) { apps, map -> apps.filter { isHidden(it, map) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Aplikacja, którą ktoś próbuje otworzyć mimo blokady (ekran pokazuje wtedy okno z namysłem).
    private val _blockedLaunch = MutableStateFlow<AppInfo?>(null)
    val blockedLaunch: StateFlow<AppInfo?> = _blockedLaunch.asStateFlow()

    fun cancelBlockedLaunch() {
        _blockedLaunch.value = null
    }

    fun launchAnyway(app: AppInfo) {
        _blockedLaunch.value = null
        launch(app, ignoreBlock = true)
    }

    // Zmiana blokad w wielu zakresach naraz. Zakres null = "nowe tryby" (szablon w ustawieniach).
    // kind == null oznacza przywrócenie (usunięcie blokady/ukrycia).
    fun setRestriction(apps: Collection<AppInfo>, scopes: Collection<Long?>, kind: String?) {
        // distinctBy: ta sama aplikacja może mieć kilka aktywności (kilka ikon) — blokujemy ją raz.
        val targets = apps.distinctBy { it.appKey }
        viewModelScope.launch {
            scopes.forEach { scope ->
                if (scope == null) {
                    val keys = targets.map { it.appKey }.toSet()
                    val kept = appPrefs.newModeRestrictions.value.filter { it.substringAfter('|') !in keys }.toSet()
                    appPrefs.newModeRestrictions.set(if (kind == null) kept else kept + keys.map { "$kind|$it" })
                } else if (kind == null) {
                    targets.forEach { restrictionDao.delete(scope, it.packageName, it.userSerial) }
                } else {
                    restrictionDao.upsert(targets.map { AppRestrictionEntity(modeId = scope, packageName = it.packageName, userSerial = it.userSerial, kind = kind) })
                }
            }
        }
    }

    // Nowy tryb dostaje blokady z szablonu "nowe tryby".
    private suspend fun applyNewModeDefaults(modeId: Long) {
        val rows = appPrefs.newModeRestrictions.value.mapNotNull { entry ->
            val kind = entry.substringBefore('|')
            val key = entry.substringAfter('|')
            val serial = key.substringAfterLast('#').toLongOrNull() ?: return@mapNotNull null
            AppRestrictionEntity(modeId = modeId, packageName = key.substringBeforeLast('#'), userSerial = serial, kind = kind)
        }
        if (rows.isNotEmpty()) restrictionDao.upsert(rows)
    }

    // Karta aktywnego trybu: gdy zmienia się tryb, flatMapLatest przełącza się na zapytanie
    // dla nowego trybu, a combine dokleja do wierszy z bazy ikony i nazwy zainstalowanych aplikacji.
    val cardElements: StateFlow<List<CardElement>> =
        activeMode
            .flatMapLatest { mode ->
                if (mode == null) flowOf(emptyList()) else cardItemDao.observeForMode(mode.id)
            }
            .combine(combine(allApps, _shortcuts) { a, sc -> a to sc }) { items, (apps, shortcutList) ->
                // mapNotNull pomija elementy, których nie da się pokazać (np. odinstalowana aplikacja).
                items.mapNotNull { item ->
                    // when ≈ switch z wyrażeniem (C# 8 switch expression).
                    when (item.type) {
                        CardItemEntity.TYPE_APP ->
                            apps.firstOrNull { it.matches(item) }?.let { CardApp(item, it) }
                        CardItemEntity.TYPE_SHORTCUT ->
                            shortcutList.firstOrNull { it.matches(item) }?.let { CardApp(item, it) }
                        CardItemEntity.TYPE_WIDGET ->
                            item.appWidgetId?.let { CardWidget(item, it) }
                        CardItemEntity.TYPE_CUSTOM ->
                            // entries = wszystkie wartości enuma; nieznany rodzaj (np. z nowszej wersji) pomijamy.
                            CustomWidgetKind.entries.firstOrNull { it.name == item.widgetKind }
                                ?.let { CardCustomWidget(item, it) }
                        else -> null
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Strony karty ---
    // Wszystkie strony trybu są w cardElements (każdy element zna swoją stronę); UI pokazuje je w HorizontalPager.
    // currentPage = strona, na którą trafia to, co dodajemy (jak "bieżący arkusz" w Excelu).
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // Czy następną zmianę strony UI ma pokazać przesunięciem (true tylko po przeniesieniu elementu).
    // Zwykła właściwość, nie Flow: UI czyta ją w chwili, gdy strona się zmienia (ustawiamy ją PRZED zmianą).
    var animateNextPage = false
        private set

    fun setPage(page: Int) {
        animateNextPage = false
        _currentPage.value = page.coerceAtLeast(0)
    }

    // Ile stron zajmują elementy aktywnego trybu — z surowych wierszy bazy (także z niewidocznymi,
    // np. odinstalowanymi aplikacjami), żeby liczba stron zgadzała się z porządkowaniem w compactPages.
    val usedPages: StateFlow<Int> = activeMode
        .flatMapLatest { mode -> if (mode == null) flowOf(emptyList()) else cardItemDao.observeForMode(mode.id) }
        .map { items -> (items.maxOfOrNull { it.page } ?: 0) + 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    // Elementy jednej strony — kolizje i wolne miejsca liczymy tylko w jej obrębie.
    private suspend fun pageItems(modeId: Long, page: Int): List<CardItemEntity> =
        cardItemDao.getForMode(modeId).filter { it.page == page }

    // Przeniesienie elementu na sąsiednią stronę (upuszczenie przy lewej/prawej krawędzi w edycji).
    // Na nowej stronie próbujemy to samo miejsce, a gdy zajęte — najbliższe wolne.
    // wanted = miejsce na docelowej stronie (gdzie puszczono element); bez niego — to samo co na starej stronie.
    fun moveToPage(element: CardElement, delta: Int, wanted: GridRect? = null) {
        val item = element.item
        val target = item.page + delta
        val limit = appPrefs.maxPages.value
        if (target < 0) return
        if (target >= limit) {
            Toast.makeText(getApplication(), "Limit stron: $limit (zmienisz w Ustawieniach)", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            val message = database.withTransaction {
                val taken = pageItems(item.modeId, target).map { it.toRect() }
                val want = wanted ?: item.toRect()
                val spot = want.takeIf { CardGrid.canPlace(it, taken) } ?: CardGrid.nearestFreeSpot(want, taken)
                    ?: return@withTransaction "Na stronie ${target + 1} nie ma miejsca"
                cardItemDao.updatePage(item.id, target, spot.x, spot.y)
                null
            }
            if (message == null) {
                if (_currentPage.value != target) { // przeciąganiem już tam jesteśmy — wtedy bez zmian
                    animateNextPage = true
                    _currentPage.value = target // pokazujemy stronę, na którą trafił element
                }
            } else {
                Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
                // Element został na swojej stronie — wracamy do niej, żeby nie wyglądało, jakby zniknął.
                if (_currentPage.value != item.page) {
                    animateNextPage = true
                    _currentPage.value = item.page
                }
            }
        }
    }

    // Po edycji: puste strony w środku znikają (strony 0, 2 → 0, 1), żeby nie było "dziur" przy przesuwaniu.
    private suspend fun compactPages(modeId: Long) {
        val items = cardItemDao.getForMode(modeId).filter { it.page >= 0 } // widżety w stosach (STACKED_PAGE) nie mają strony
        val used = items.map { it.page }.distinct().sorted()
        animateNextPage = false // numery się tylko porządkują — bez przesuwania ekranu
        if (used.withIndex().all { (i, p) -> i == p }) {
            _currentPage.update { cur -> cur.coerceAtMost((used.size - 1).coerceAtLeast(0)) }
            return
        }
        val remap = used.withIndex().associate { (i, p) -> p to i }
        database.withTransaction {
            items.forEach { item ->
                val newPage = remap.getValue(item.page)
                if (newPage != item.page) cardItemDao.updatePage(item.id, newPage, item.x, item.y)
            }
        }
        // Bieżąca strona (aktualna wartość — mogła się zmienić w trakcie zapisu): jej nowy numer, a gdy była pusta — poprzednia.
        _currentPage.update { cur -> remap[cur] ?: (used.count { it < cur } - 1).coerceAtLeast(0) }
    }

    // --- Sugestie trybów ---

    val rules: StateFlow<List<SuggestionRuleEntity>> =
        suggestionDao.observeRules().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Odrzucone sugestie: klucz → do kiedy nie pokazywać (ms). Tylko w pamięci, restart je czyści.
    private val dismissed = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Przelicz przy powrocie na ekran główny (np. po dodaniu wydarzenia w kalendarzu).
    private val suggestionRefresh = MutableStateFlow(0)

    // Zmienia się m.in. przy każdym powrocie na ekran główny — UI używa jej, by odczytać zgody na nowo.
    val refreshTick: StateFlow<Int> = suggestionRefresh.asStateFlow()

    // Zegar tykający co minutę; flow { } ≈ IAsyncEnumerable z yield return.
    private val minuteTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    // Wejścia silnika zebrane w jedną klasę (combine przyjmuje do 5 strumieni).
    private data class SuggestionInputs(
        val rules: List<SuggestionRuleEntity>,
        val modes: List<ModeEntity>,
        val dismissed: Map<String, Long>,
    )

    val suggestion: StateFlow<Suggestion?> =
        combine(minuteTicker, rules, modes, dismissed, suggestionRefresh) { _, rules, modes, dismissed, _ ->
            SuggestionInputs(rules, modes, dismissed)
        }
            // mapLatest: gdy przyjdą nowe dane w trakcie liczenia, poprzednie liczenie jest przerywane.
            .mapLatest { input -> computeSuggestion(input) }
            // Odczyty systemu (bateria, Wi-Fi, lokalizacja, kalendarz) poza wątkiem UI.
            .flowOn(Dispatchers.Default)
            // WhileSubscribed: licz tylko wtedy, gdy ktoś (ekran) słucha; 5 s zapasu na obrót ekranu.
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun computeSuggestion(input: SuggestionInputs): Suggestion? {
        val nowMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)

        // Aktywny = ostatnio włączony. Po zakończeniu trybu wracamy na stronę główną (Start),
        // a gdy jej nie ma albo to ona jest aktywna — do przedostatniego trybu.
        val byRecent = input.modes.sortedByDescending { it.lastActiveAt }
        val active = byRecent.getOrNull(0)
        val home = input.modes.firstOrNull { it.id == appPrefs.homeModeId.value }?.takeIf { appPrefs.returnToHome.value }
        val previous = home?.takeIf { it.id != active?.id } ?: byRecent.getOrNull(1)

        val modeRules = input.rules.mapNotNull { entity -> entity.toRule()?.let { ModeRule(entity.modeId, it) } }
        if (modeRules.isEmpty()) return null

        // Kalendarz i sygnały czytamy tylko, gdy jakaś reguła ich potrzebuje.
        val needsCalendar = modeRules.any { it.rule is Rule.CalendarKeyword }
        val signals = signalsReader.read(
            needBluetooth = modeRules.any { it.rule is Rule.BluetoothDevice },
            needWifi = modeRules.any { it.rule is Rule.WifiNetwork },
            needLocation = modeRules.any { it.rule is Rule.Place },
            needPower = modeRules.any { it.rule is Rule.Charging || it.rule is Rule.BatteryBelow },
            needHeadphones = modeRules.any { it.rule == Rule.Headphones },
        )
        val events = if (needsCalendar) {
            val nowInstant = Instant.ofEpochMilli(nowMs)
            calendar.events(nowInstant.minus(Duration.ofHours(12)), nowInstant.plus(SuggestionEngine.CALENDAR_LOOKAHEAD))
        } else {
            emptyList()
        }

        return SuggestionEngine.evaluate(
            rules = modeRules,
            activeModeId = active?.id,
            previousModeId = previous?.id,
            activeSince = active?.lastActiveAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) },
            now = now,
            events = events,
            dismissed = input.dismissed.filterValues { it > nowMs }.keys,
            signals = signals,
        )
    }

    fun acceptSuggestion(suggestion: Suggestion) {
        val targetId = when (suggestion) {
            is Suggestion.SwitchTo -> suggestion.modeId
            is Suggestion.EndMode -> suggestion.backToModeId
        }
        modes.value.firstOrNull { it.id == targetId }?.let(::selectMode)
    }

    fun dismissSuggestion(suggestion: Suggestion) {
        val zone = ZoneId.systemDefault()
        val until = when (appPrefs.dismissDuration.value) {
            DismissDuration.ONE_HOUR -> System.currentTimeMillis() + Duration.ofHours(1).toMillis()
            DismissDuration.THREE_HOURS -> System.currentTimeMillis() + Duration.ofHours(3).toMillis()
            DismissDuration.UNTIL_TOMORROW -> LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        dismissed.value = dismissed.value + (suggestion.key to until) // nowa mapa z dopisanym wpisem
    }

    private fun dismissByKey(key: String) {
        dismissed.value = dismissed.value + (key to System.currentTimeMillis() + Duration.ofHours(1).toMillis())
    }

    fun addTimeRule(mode: ModeEntity, days: Set<DayOfWeek>, start: LocalTime, end: LocalTime) {
        viewModelScope.launch {
            suggestionDao.insertRule(
                SuggestionRuleEntity(modeId = mode.id, type = SuggestionRuleEntity.TYPE_TIME, params = timeRuleParams(days, start, end)),
            )
        }
    }

    fun addCalendarRule(mode: ModeEntity, keyword: String) {
        viewModelScope.launch {
            suggestionDao.insertRule(
                SuggestionRuleEntity(modeId = mode.id, type = SuggestionRuleEntity.TYPE_CALENDAR, params = calendarRuleParams(keyword)),
            )
        }
    }

    fun addBluetoothRule(mode: ModeEntity, device: PairedDevice) = insertRule(
        mode, SuggestionRuleEntity.TYPE_BLUETOOTH, bluetoothRuleParams(device.address, device.name),
    )

    fun addWifiRule(mode: ModeEntity, ssid: String) = insertRule(mode, SuggestionRuleEntity.TYPE_WIFI, wifiRuleParams(ssid))

    // Miejsce = bieżąca lokalizacja telefonu + wybrany promień.
    fun addChargingRule(mode: ModeEntity, charging: Boolean) =
        insertRule(mode, SuggestionRuleEntity.TYPE_CHARGING, chargingRuleParams(charging))

    fun addHeadphonesRule(mode: ModeEntity) = insertRule(mode, SuggestionRuleEntity.TYPE_HEADPHONES, "{}")

    fun addBatteryRule(mode: ModeEntity, belowPercent: Int) =
        insertRule(mode, SuggestionRuleEntity.TYPE_BATTERY, batteryRuleParams(belowPercent))

    fun addPlaceRule(mode: ModeEntity, label: String, radiusMeters: Int) {
        val location = signalsReader.lastLocation()
        if (location == null) {
            Toast.makeText(getApplication(), "Brak lokalizacji. Włącz lokalizację i spróbuj ponownie.", Toast.LENGTH_LONG).show()
            return
        }
        insertRule(mode, SuggestionRuleEntity.TYPE_PLACE, placeRuleParams(location.latitude, location.longitude, radiusMeters, label))
    }

    // Miejsce wskazane na mapie (nie trzeba w nim być).
    fun addPlaceRule(mode: ModeEntity, label: String, lat: Double, lon: Double, radiusMeters: Int) =
        insertRule(mode, SuggestionRuleEntity.TYPE_PLACE, placeRuleParams(lat, lon, radiusMeters, label))

    // Gdzie otworzyć mapę: ostatnia znana lokalizacja telefonu (null = brak zgody albo brak odczytu).
    fun lastKnownPoint(): Pair<Double, Double>? = signalsReader.lastLocation()?.let { it.latitude to it.longitude }

    private fun insertRule(mode: ModeEntity, type: String, params: String) {
        viewModelScope.launch { suggestionDao.insertRule(SuggestionRuleEntity(modeId = mode.id, type = type, params = params)) }
    }

    fun pairedDevices(): List<PairedDevice> = signalsReader.pairedDevices()

    fun currentSsid(): String? = signalsReader.currentSsid()

    fun hasBluetoothPermission() = signalsReader.hasBluetoothPermission()

    fun bluetoothPermission() = signalsReader.bluetoothPermission()

    fun hasLocationPermission() = signalsReader.hasLocationPermission()

    // --- Ustawienia telefonu w trybie ---

    fun updatePhoneSettings(mode: ModeEntity, settings: ModePhoneSettings) {
        viewModelScope.launch {
            modeDao.updateSettings(mode.id, settings.toJson().takeUnless { settings.isEmpty() })
            // Zmiana ustawień aktywnego trybu działa od razu.
            if (mode.id == activeMode.value?.id && appPrefs.applyPhoneSettings.value) phoneSettings.apply(settings)
            suggestionRefresh.value++
        }
    }

    fun hasDndAccess() = phoneSettings.hasDndAccess()

    fun canWriteSettings() = phoneSettings.canWriteSettings()

    fun dndAccessIntent() = phoneSettings.dndAccessIntent()

    fun writeSettingsIntent() = phoneSettings.writeSettingsIntent()

    fun intentFor(task: ManualTask) = phoneSettings.intentFor(task)

    // Przełączniki do zmiany ręcznej w aktywnym trybie (Wi-Fi, Bluetooth, ...), liczone razem z sugestiami.
    private val manualDismissedFor = MutableStateFlow<Long?>(null) // id trybu, dla którego schowano przypomnienie

    val manualTasks: StateFlow<List<ManualTask>> =
        combine(minuteTicker, activeMode, suggestionRefresh, manualDismissedFor) { _, mode, _, dismissedFor ->
            if (mode == null || mode.id == dismissedFor) emptyList()
            else phoneSettings.pendingManual(ModePhoneSettings.parse(mode.settings))
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dismissManualTasks() {
        manualDismissedFor.value = activeMode.value?.id
    }

    fun deleteRule(rule: SuggestionRuleEntity) {
        viewModelScope.launch { suggestionDao.deleteRule(rule.id) }
    }

    fun hasCalendarPermission(): Boolean = calendar.hasPermission()

    fun onCalendarPermissionResult() {
        suggestionRefresh.value++
    }

    // --- Najczęściej używane w trybie ---

    // Liczba uruchomień w aktywnym trybie, po kluczu aplikacji (do sortowania folderów "najczęściej używane").
    val launchCounts: StateFlow<Map<String, Int>> =
        activeMode
            .flatMapLatest { mode -> if (mode == null) flowOf(emptyList()) else suggestionDao.observeTop(mode.id, 500) }
            .map { stats ->
                stats.associate { "${android.content.ComponentName(it.packageName, it.className).flattenToString()}#${it.userSerial}" to it.count }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val frequentApps: StateFlow<List<AppInfo>> =
        activeMode
            .flatMapLatest { mode -> if (mode == null) flowOf(emptyList()) else suggestionDao.observeTop(mode.id, 12) }
            .combine(allApps) { stats, apps ->
                stats.mapNotNull { stat -> apps.firstOrNull { it.matches(stat.packageName, stat.className, stat.userSerial) } }
            }
            // Ukrytych w tym trybie nie podsuwamy nawet w "Często używanych".
            .combine(activeRestrictions) { list, restricted ->
                list.filterNot { isHidden(it, restricted) }.take(LauncherViewModel.COLUMNS) // jeden rząd
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Strona główna i pogoda ---

    val homeModeId: StateFlow<Long?> = appPrefs.homeModeId

    fun setHomeMode(mode: ModeEntity?) = appPrefs.setHomeModeId(mode?.id)

    val weather: StateFlow<Weather?> = weatherRepository.weather

    fun hasWeatherPermission() = signalsReader.hasApproxLocationPermission()

    // --- Ulubione kontakty i szybkie przełączniki ---

    private val contactsReader = ContactsReader(application)
    fun hasContactsPermission() = contactsReader.hasPermission()

    // Kontakty czytamy przy każdym powrocie na ekran (mogłeś właśnie dodać gwiazdkę w Kontaktach).
    // Osobny licznik: kontakty (ze zdjęciami) czytamy tylko przy powrocie na ekran, a nie przy każdej zmianie trybu czy ładowarki.
    private val contactsRefresh = MutableStateFlow(0)
    val favorites: StateFlow<List<FavoriteContact>> =
        contactsRefresh.mapLatest { contactsReader.starred() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val quickToggles = QuickToggles(application)
    val quickStates: StateFlow<QuickStates> = quickToggles.states

    // Zwraca Intent, gdy trzeba otworzyć panel systemu (Wi-Fi, Bluetooth, zgoda).
    fun quickToggle(toggle: QuickToggle): Intent? = quickToggles.toggle(toggle)

    // Numer wybrany w systemowej liście kontaktów (ACTION_PICK daje jednorazowy dostęp do tego jednego wiersza).
    fun readPickedPhone(uri: Uri): Pair<String, String>? = runCatching {
        getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1).orEmpty() else null }
    }.getOrNull()

    fun onContactsPermissionResult() {
        contactsRefresh.value++
    }

    // --- Widżet "W skrócie": najbliższe wydarzenie i budzik ---

    // agenda = plan na dziś i jutro dla widżetu "Dziś".
    data class GlanceInfo(
        val event: GlanceEvent?,
        val alarmAt: Long?,
        val hasCalendar: Boolean,
        val agenda: List<GlanceEvent> = emptyList(),
        val alarmApp: String? = null, // aplikacja, która ustawiła budzik — gdy to nie Zegar, pokazujemy jej nazwę
    )

    // Budzik z systemu to "najbliższy alarm" dowolnej aplikacji (Zegar, Kalendarz, aplikacja snu…).
    // Stąd "07:00", którego nie ma w Zegarze. Otwieramy więc tę aplikację, która go ustawiła.
    private var alarmShowIntent: android.app.PendingIntent? = null

    fun openAlarmSource(): Boolean {
        val intent = alarmShowIntent ?: return false
        return runCatching {
            // Od Androida 14 wysyłający musi jawnie zezwolić na otwarcie ekranu przez PendingIntent innej aplikacji.
            val options = android.app.ActivityOptions.makeBasic()
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            intent.send(getApplication(), 0, null, null, null, null, options.toBundle())
        }.isSuccess
    }

    // Co minutę i po każdym powrocie na ekran (np. po ustawieniu budzika) czytamy na nowo.
    val glance: StateFlow<GlanceInfo> =
        combine(minuteTicker, suggestionRefresh) { now, _ -> now }
            .mapLatest { now ->
                val alarm = getApplication<Application>().getSystemService(android.app.AlarmManager::class.java)
                val (next, agenda) = calendar.glance(now) // jedno zapytanie do kalendarza zamiast dwóch
                val info = alarm?.nextAlarmClock
                alarmShowIntent = info?.showIntent
                val creator = info?.showIntent?.creatorPackage
                val clockPackage = runCatching {
                    getApplication<Application>().packageManager
                        .resolveActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), 0)?.activityInfo?.packageName
                }.getOrNull()
                val creatorLabel = creator?.takeIf { it != clockPackage }?.let { pkg ->
                    runCatching {
                        val pm = getApplication<Application>().packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrNull()
                }
                GlanceInfo(
                    alarmApp = creatorLabel,
                    event = next,
                    alarmAt = info?.triggerTime,
                    hasCalendar = calendar.hasPermission(),
                    agenda = agenda,
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlanceInfo(null, null, true))

    fun refreshWeather() {
        if (!signalsReader.hasApproxLocationPermission()) return
        viewModelScope.launch { weatherRepository.refreshIfStale(signalsReader.lastLocation()) }
    }

    // --- Kreator pierwszego uruchomienia ---

    // Brak trybów = świeża instalacja → pokazujemy kreator. Wartość startowa false,
    // żeby kreator nie mignął, zanim baza zdąży odpowiedzieć.
    val needsOnboarding: StateFlow<Boolean> =
        modeDao.observeAll().map { it.isEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Tworzy tryby z szablonów: tryb, widżety, dobrane aplikacje i reguły sugestii.
    // Tryby o nazwach, które już istnieją, są pomijane.
    fun createModesFromTemplates(templates: List<ModeTemplate>) {
        viewModelScope.launch {
            val existing = modes.value
            val existingNames = existing.map { it.name.lowercase() }.toSet()
            val installed = allApps.value
            val now = System.currentTimeMillis()

            templates
                .filter { it.name.lowercase() !in existingNames }
                .forEachIndexed { index, template ->
                    val modeId = modeDao.insert(
                        ModeEntity(
                            name = template.name,
                            icon = template.icon,
                            color = template.color,
                            sortOrder = existing.size + index,
                            // Przy pierwszym uruchomieniu aktywny zostaje pierwszy wybrany tryb.
                            lastActiveAt = if (existing.isEmpty() && index == 0) now else 0,
                        ),
                    )
                    applyNewModeDefaults(modeId)

                    // Zajęte miejsca na karcie tego trybu; najpierw widżety, potem aplikacje.
                    val taken = mutableListOf<GridRect>() // lista modyfikowalna, jak List<T> w C#
                    template.widgets.forEach { widget ->
                        val spot = CardGrid.findFreeSpot(taken, widget.w, widget.h) ?: return@forEach
                        taken += spot
                        cardItemDao.insert(
                            CardItemEntity(
                                modeId = modeId, type = CardItemEntity.TYPE_CUSTOM,
                                packageName = "", className = "", userSerial = 0,
                                x = spot.x, y = spot.y, w = spot.w, h = spot.h,
                                widgetKind = widget.kind.name, config = widget.config,
                            ),
                        )
                    }
                    // Aplikacje wypełniają resztę karty; gdy miejsca brak, findFreeSpot zwraca null i pomijamy resztę.
                    ModeTemplates.pickAppsForFill(installed, template).forEach { app ->
                        val spot = CardGrid.findFreeSpot(taken, CardGrid.APP_SIZE, CardGrid.APP_SIZE) ?: return@forEach
                        taken += spot
                        cardItemDao.insert(
                            CardItemEntity(
                                modeId = modeId,
                                packageName = app.component.packageName,
                                className = app.component.className,
                                userSerial = app.userSerial,
                                x = spot.x, y = spot.y, w = spot.w, h = spot.h,
                            ),
                        )
                    }
                    template.rules.forEach { (type, params) ->
                        suggestionDao.insertRule(SuggestionRuleEntity(modeId = modeId, type = type, params = params))
                    }
                    // Szablon "Start" zostaje stroną główną.
                    if (template == ModeTemplates.Start) appPrefs.setHomeModeId(modeId)
                }
        }
    }

    // Kreator nowego trybu (wywiad): tryb z planu, z aplikacjami wybranymi na ostatnim kroku.
    // Nowy tryb od razu staje się aktywny — widać efekt kreatora.
    fun createModeFromPlan(plan: ModePlan, apps: List<AppInfo>) {
        viewModelScope.launch {
            val template = plan.toTemplate()
            val iconSize = plan.iconCells ?: appPrefs.appIconCells.value
            // Wszystko w jednej transakcji: tryb staje się aktywny dopiero z gotową kartą (bez mignięcia pustej).
            database.withTransaction {
                val modeId = modeDao.insert(
                    ModeEntity(
                        name = template.name,
                        icon = template.icon,
                        color = template.color,
                        sortOrder = modes.value.size,
                        lastActiveAt = System.currentTimeMillis(),
                    ),
                )
                applyNewModeDefaults(modeId)
                // Własny rozmiar ikon trybu (ten sam zapis co w Ustawieniach trybu → Układ karty).
                plan.iconCells?.let { cells ->
                    val all = ModeLayout.parseAll(appPrefs.modeLayouts.value)
                    appPrefs.modeLayouts.set(ModeLayout.writeAll(all + (modeId to ModeLayout(iconCells = cells))))
                }
                val taken = mutableListOf<GridRect>()
                val taken2 = mutableListOf<GridRect>() // druga strona: aplikacje, które nie zmieściły się na pierwszej
                template.widgets.forEach { widget ->
                    val spot = CardGrid.findFreeSpot(taken, widget.w, widget.h) ?: return@forEach
                    taken += spot
                    cardItemDao.insert(
                        CardItemEntity(
                            modeId = modeId, type = CardItemEntity.TYPE_CUSTOM,
                            packageName = "", className = "", userSerial = 0,
                            x = spot.x, y = spot.y, w = spot.w, h = spot.h,
                            widgetKind = widget.kind.name, config = widget.config,
                        ),
                    )
                }
                apps.forEach { app ->
                    val spot = CardGrid.findFreeSpot(taken, iconSize, iconSize)
                    if (spot != null) {
                        taken += spot
                        cardItemDao.insert(app.cardItem(modeId, spot))
                    } else if (appPrefs.maxPages.value > 1) {
                        val next = CardGrid.findFreeSpot(taken2, iconSize, iconSize) ?: return@forEach
                        taken2 += next
                        cardItemDao.insert(app.cardItem(modeId, next, page = 1))
                    }
                }
                template.rules.forEach { (type, params) ->
                    suggestionDao.insertRule(SuggestionRuleEntity(modeId = modeId, type = type, params = params))
                }
            }
        }
    }

    // --- Wygląd ---

    val themeMode: StateFlow<ThemeMode> = themePrefs.themeMode
    val defaultPalette: StateFlow<Palette> = themePrefs.defaultPalette

    fun setThemeMode(mode: ThemeMode) = themePrefs.setThemeMode(mode)

    fun setDefaultPalette(palette: Palette) = themePrefs.setDefaultPalette(palette)

    // Kreator motywów: zapis własnych kolorów i od razu użycie ich jako domyślnego schematu.
    fun saveCustomTheme(colors: pl.rafal.contextlauncher.ui.theme.CustomColors) {
        themePrefs.setCustomColors(colors)
        themePrefs.setDefaultPalette(Palette.CUSTOM)
    }

    fun updateModeAppearance(mode: ModeEntity, icon: String?, color: Long, palette: Palette?, accent: Long?) {
        viewModelScope.launch {
            modeDao.updateAppearance(mode.id, icon, color, palette?.name, accent)
            ModeTileService.requestUpdate(getApplication()) // kafelek pokazuje ikonę aktywnego trybu
        }
    }

    // --- Zarządzanie trybami ---

    // Nowa kolejność trybów (przeciąganie na liście przycisku ON) — jedna transakcja, lista odświeży się sama.
    fun reorderModes(ordered: List<ModeEntity>) {
        viewModelScope.launch {
            database.withTransaction {
                ordered.forEachIndexed { index, mode -> modeDao.updateSortOrder(mode.id, index) }
            }
        }
    }

    fun renameMode(mode: ModeEntity, name: String) {
        viewModelScope.launch { modeDao.rename(mode.id, name.trim()) }
    }

    fun deleteMode(mode: ModeEntity) {
        if (modes.value.size <= 1) {
            Toast.makeText(getApplication(), "Musi zostać co najmniej jeden tryb", Toast.LENGTH_SHORT).show()
            return
        }
        if (mode.id == appPrefs.homeModeId.value) appPrefs.setHomeModeId(null) // usunięto stronę główną
        viewModelScope.launch {
            // Kaskada w bazie usunie wiersze, ale nie sprząta poza nią: identyfikatory widżetów u systemu i pliki naklejek.
            cardItemDao.getForMode(mode.id).forEach(::cleanupItem) // widżety systemowe, pliki naklejek (z oryginałami)
            wallpapers.clear(mode.id)
            modeDao.delete(mode.id)
        }
    }

    // --- Foldery ---

    // Drzewo przelicza się, gdy zmienią się foldery, ich zawartość albo lista zainstalowanych aplikacji.
    val folderTree: StateFlow<FolderTree> =
        combine(folderDao.observeFolders(), folderDao.observeApps(), allApps) { folders, apps, installed ->
            FolderTree(folders, apps, installed)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FolderTree.EMPTY)

    // Wszystkie aplikacje (bez filtra wyszukiwania), np. do wyboru aplikacji do folderu.
    val installedApps: StateFlow<List<AppInfo>> = allApps.asStateFlow()

    fun createFolder(parentId: Long?, name: String) {
        viewModelScope.launch { folderDao.insertFolder(FolderEntity(parentId = parentId, name = name.trim())) }
    }

    // Nowy folder od razu położony na karcie jako widżet.
    fun createFolderWithWidget(name: String) {
        viewModelScope.launch {
            val id = folderDao.insertFolder(FolderEntity(name = name.trim())) // insert zwraca nowe id (jak SCOPE_IDENTITY())
            addCustomWidget(CustomWidgetKind.FOLDER, JSONObject().put("folderId", id).toString())
        }
    }

    // Nowy folder od razu z aplikacjami.
    fun createFolderWithApps(name: String, apps: List<AppInfo>) {
        viewModelScope.launch {
            val id = folderDao.insertFolder(FolderEntity(name = name.trim()))
            addAppsToFolder(id, apps)
        }
    }

    fun renameFolder(folder: FolderEntity, name: String) {
        viewModelScope.launch { folderDao.renameFolder(folder.id, name.trim()) }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch { folderDao.deleteFolder(folder.id) } // podfoldery znikną kaskadowo
    }

    fun addAppsToFolder(folderId: Long, apps: List<AppInfo>) {
        viewModelScope.launch { insertIntoFolder(folderId, apps) }
    }

    // Zwraca id nowych wierszy — edycja układu zapamiętuje je, żeby "✕" mógł cofnąć wrzucenie do folderu.
    private suspend fun insertIntoFolder(folderId: Long, apps: List<AppInfo>): List<Long> {
        // toSet() + "!in" ≈ HashSet i !Contains: pomijamy aplikacje, które już są w tym folderze.
        val alreadyThere = folderTree.value.apps(folderId).map { it.app.key }.toSet()
        val toAdd = apps.filter { it.key !in alreadyThere }.map { app ->
            FolderAppEntity(
                folderId = folderId,
                packageName = app.component.packageName,
                className = app.component.className,
                userSerial = app.userSerial,
            )
        }
        return if (toAdd.isNotEmpty()) folderDao.insertApps(toAdd) else emptyList()
    }

    // Wygląd folderu: symbol (null = miniatura z ikon aplikacji) i kolor tła (null = neutralny).
    fun setFolderLook(folder: FolderEntity, icon: String?, color: Long?) {
        viewModelScope.launch { folderDao.setFolderLook(folder.id, icon, color) }
    }

    // Folder na karcie aktywnego trybu w wybranym rozmiarze (w komórkach siatki). Jeśli widżet tego folderu
    // już tam jest, zmieniamy mu rozmiar i rozsuwamy sąsiadów (ta sama logika co przy przeciąganiu w edycji).
    fun placeFolderOnCard(folderId: Long, w: Int, h: Int) {
        val mode = activeMode.value ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            var placedFolderOn: Int? = null // inna strona, gdy bieżąca była pełna
            val message = database.withTransaction {
                val all = cardItemDao.getForMode(mode.id)
                val existing = all.firstOrNull { it.isFolderWidget(folderId) }
                val items = all.filter { it.page == (existing?.page ?: page) } // folder już jest → liczy się jego strona
                if (existing != null) {
                    val target = GridRect(
                        existing.x.coerceAtMost(CardGrid.COLUMNS - w),
                        existing.y.coerceAtMost((CardGrid.rows - h).coerceAtLeast(0)),
                        w, h,
                    )
                    val plan = CardGrid.placeWithPush(existing.id, target, items.associate { it.id to it.toRect() })
                    if (plan == null) return@withTransaction "Za mało miejsca na karcie na ten rozmiar"
                    plan.forEach { (id, r) -> cardItemDao.updateRect(id, r.x, r.y, r.w, r.h) }
                    "Zmieniono rozmiar folderu na karcie"
                } else {
                    val (target, spot) = PageSpace(all, appPrefs.maxPages.value).find(page, w, h)
                        ?: return@withTransaction "Wszystkie strony karty są pełne (limit stron zmienisz w Ustawieniach)"
                    cardItemDao.insert(
                        CardItemEntity(
                            modeId = mode.id, type = CardItemEntity.TYPE_CUSTOM,
                            packageName = "", className = "", userSerial = 0,
                            x = spot.x, y = spot.y, w = w, h = h,
                            widgetKind = CustomWidgetKind.FOLDER.name,
                            config = JSONObject().put("folderId", folderId).toString(),
                            page = target,
                        ),
                    )
                    if (target != page) placedFolderOn = target
                    "Dodano folder do trybu ${mode.name}"
                }
            }
            Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
            placedFolderOn?.let { showPlacedPage(it, page) }
        }
    }

    private fun CardItemEntity.isFolderWidget(folderId: Long): Boolean =
        widgetKind == CustomWidgetKind.FOLDER.name &&
            runCatching { JSONObject(config ?: "{}").optLong("folderId", -1) }.getOrDefault(-1L) == folderId

    fun moveAppToFolder(app: FolderApp, folderId: Long) {
        viewModelScope.launch { folderDao.moveApp(app.entry.id, folderId) }
    }

    fun removeAppFromFolder(app: FolderApp) {
        viewModelScope.launch { folderDao.deleteApp(app.entry.id) }
    }

    // Element trafił na inną stronę niż oglądana (bieżąca była pełna) → przechodzimy tam i mówimy o tym.
    // requested = strona, na którą chcieliśmy dodać (oglądana w chwili dodawania).
    private suspend fun showPlacedPage(page: Int, requested: Int) {
        if (page == requested) return
        // Nowa strona musi najpierw "istnieć" w UI (baza odświeża listę z opóźnieniem) — inaczej ekran
        // przewinąłby się na pustkę. Czekamy chwilę, aż liczba stron ją obejmie.
        withTimeoutOrNull(700) { usedPages.first { it > page } }
        animateNextPage = true
        _currentPage.value = page
        Toast.makeText(getApplication(), "Brak miejsca — dodano na stronie ${page + 1}", Toast.LENGTH_SHORT).show()
    }

    private suspend fun pageSpace(modeId: Long) = PageSpace(cardItemDao.getForMode(modeId), appPrefs.maxPages.value)

    // --- Własne widżety ---

    fun addCustomWidget(kind: CustomWidgetKind, config: String = "{}") {
        val mode = activeMode.value ?: return
        val requested = _currentPage.value
        viewModelScope.launch {
            val placed = placeCustomWidget(cardItemDao, mode.id, kind, config, requested, appPrefs.maxPages.value)
            if (placed == null) Toast.makeText(getApplication(), "Wszystkie strony karty są pełne (limit stron zmienisz w Ustawieniach)", Toast.LENGTH_SHORT).show()
            else showPlacedPage(placed, requested)
        }
    }

    // Naklejka: kopia obrazka do pamięci launchera, potem widżet na karcie.
    fun addSticker(uri: Uri) {
        val mode = activeMode.value ?: return
        val requested = _currentPage.value
        viewModelScope.launch {
            val result = runCatching {
                val path = StickerStore.import(getApplication(), uri)
                val placed = placeCustomWidget(
                    cardItemDao, mode.id, CustomWidgetKind.STICKER, JSONObject().put("file", path).toString(),
                    requested, appPrefs.maxPages.value,
                )
                if (placed == null) StickerStore.delete(path) // nie ma miejsca = nie trzymamy niepotrzebnej kopii
                placed
            }
            when {
                result.isFailure -> Toast.makeText(getApplication(), "Nie udało się dodać naklejki", Toast.LENGTH_SHORT).show()
                result.getOrNull() == null -> Toast.makeText(getApplication(), "Wszystkie strony karty są pełne (limit stron zmienisz w Ustawieniach)", Toast.LENGTH_SHORT).show()
                else -> showPlacedPage(result.getOrNull()!!, requested)
            }
        }
    }

    fun updateWidgetConfig(widget: CardCustomWidget, config: String) {
        viewModelScope.launch { cardItemDao.updateConfig(widget.item.id, config) }
    }

    // --- OnHand ---

    // Elementy OnHand aktywnego trybu (aktywne i zarchiwizowane; UI je rozdziela).
    val pinnedItems: StateFlow<List<PinnedItemEntity>> =
        activeMode
            .flatMapLatest { mode -> if (mode == null) flowOf(emptyList()) else pinned.observe(mode.id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Wspólny wzorzec: weź aktywny tryb, wykonaj akcję w tle, przy błędzie pokaż komunikat.
    // (Long) -> Unit jako "suspend" = parametr-funkcja, która może czekać (jak Func<long, Task>).
    private fun withActiveMode(errorMessage: String, action: suspend (modeId: Long) -> Unit) {
        val mode = activeMode.value ?: return
        viewModelScope.launch {
            runCatching { action(mode.id) }.onFailure {
                Toast.makeText(getApplication(), errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pinFile(uri: Uri) = withActiveMode("Nie udało się przypiąć pliku") { pinned.pinFile(it, uri) }

    fun pinLink(url: String, title: String) = withActiveMode("Nie udało się przypiąć linku") { pinned.pinLink(it, url, title) }

    fun pinNote(title: String, text: String) = withActiveMode("Nie udało się zapisać notatki") { pinned.pinNote(it, title, text) }

    fun updateNote(item: PinnedItemEntity, title: String, text: String) {
        viewModelScope.launch { pinned.updateNote(item, title, text) }
    }

    fun archivePinned(item: PinnedItemEntity) {
        viewModelScope.launch { pinned.archive(item) }
    }

    fun restorePinned(item: PinnedItemEntity) {
        viewModelScope.launch { pinned.restore(item) }
    }

    fun deletePinned(item: PinnedItemEntity) {
        viewModelScope.launch { pinned.delete(item) }
    }

    fun openIntent(item: PinnedItemEntity): Intent? = pinned.openIntent(item)

    // --- Widżety ---

    private val _widgetProviders = MutableStateFlow<List<WidgetProvider>>(emptyList())
    val widgetProviders: StateFlow<List<WidgetProvider>> = _widgetProviders.asStateFlow()

    fun loadWidgetProviders() {
        viewModelScope.launch { _widgetProviders.value = widgets.loadProviders() }
    }

    // Ostatni krok dodawania: widżet ma już id, zgodę i konfigurację, więc kładziemy go na kartę.
    fun placeWidget(appWidgetId: Int) {
        val mode = activeMode.value
        val info = widgets.info(appWidgetId)
        if (mode == null || info == null) {
            widgets.deleteId(appWidgetId) // sprzątamy, żeby nie zostawić "osieroconego" id w systemie
            return
        }
        val page = _currentPage.value
        viewModelScope.launch {
            val (w, h) = widgets.cellSize(info) // dekonstrukcja pary, jak var (w, h) = ... w C#
            val found = pageSpace(mode.id).find(page, w, h)
            if (found == null) {
                widgets.deleteId(appWidgetId)
                Toast.makeText(getApplication(), "Wszystkie strony karty są pełne (limit stron zmienisz w Ustawieniach)", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val (target, spot) = found
            cardItemDao.insert(
                CardItemEntity(
                    modeId = mode.id,
                    type = CardItemEntity.TYPE_WIDGET,
                    packageName = info.provider.packageName,
                    className = info.provider.className,
                    userSerial = widgets.serialOf(info.profile),
                    x = spot.x,
                    y = spot.y,
                    w = spot.w,
                    h = spot.h,
                    appWidgetId = appWidgetId,
                    page = target,
                ),
            )
            showPlacedPage(target, page)
        }
    }

    // --- Szuflada ---

    // combine przelicza listę, gdy zmieni się którekolwiek źródło (aplikacje albo wpisany tekst).
    val drawerApps: StateFlow<List<AppInfo>> =
        combine(allApps, _query, activeRestrictions) { all, q, restricted ->
            val apps = all.filterNot { isHidden(it, restricted) } // ukryte w tym trybie nie trafiają ani do listy, ani do wyszukiwania
            val needle = q.trim()
            if (needle.isEmpty()) apps
            else apps.filter { it.label.contains(needle, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Lista aplikacji z ikonami jest kosztowna (setki bitmap), więc wczytujemy ją ponownie tylko wtedy,
    // gdy system zgłosi instalację/usunięcie/aktualizację (LauncherApps.Callback) — nie przy każdym powrocie.
    @Volatile private var appsDirty = true

    fun refresh() {
        // Launcher właśnie stał się domyślnym (dopiero wtedy system udostępnia skróty) — doczytaj je.
        if (!shortcutsLoaded && !appsDirty && repository.hasShortcutAccess()) reloadShortcuts(debounceMs = 0)
        // launch ≈ odpalenie Task bez czekania; viewModelScope anuluje go, gdy ViewModel zniknie.
        if (appsDirty) {
            appsDirty = false
            viewModelScope.launch {
                runCatching { repository.loadApps() }
                    .onSuccess { allApps.value = it }
                    .onFailure { appsDirty = true } // spróbujemy przy następnym powrocie
                reloadShortcuts(debounceMs = 0)
            }
        }
        suggestionRefresh.value++
        quickToggles.refresh() // stan Wi-Fi, dźwięku itd. mógł się zmienić poza launcherem
        contactsRefresh.value++
        refreshWeather() // najwyżej co 30 minut, więc można wołać przy każdym powrocie
    }

    fun forceRefreshWeather() {
        if (!signalsReader.hasApproxLocationPermission()) return
        viewModelScope.launch { weatherRepository.refreshIfStale(signalsReader.lastLocation(), maxAgeMs = 0) }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun onHomePressed() {
        clearQuery()
        _homePresses.value++
    }

    // ignoreBlock = true: użytkownik świadomie potwierdził otwarcie zablokowanej aplikacji.
    fun launch(app: AppInfo, ignoreBlock: Boolean = false) {
        if (!ignoreBlock && activeRestrictions.value[app.appKey] == AppRestrictionEntity.KIND_BLOCK) {
            _blockedLaunch.value = app
            return
        }
        // Aplikacja mogła zostać właśnie odinstalowana albo wyłączona, a lista jeszcze się nie odświeżyła.
        val started = runCatching { repository.launch(app) }.isSuccess
        if (!started) {
            Toast.makeText(getApplication(), "Nie udało się otworzyć ${app.label}", Toast.LENGTH_SHORT).show()
            appsDirty = true
            refresh()
            return
        }
        clearQuery()
        // Liczymy uruchomienia w aktywnym trybie, żeby podsuwać najczęściej używane aplikacje.
        activeMode.value?.let { mode ->
            viewModelScope.launch {
                suggestionDao.recordLaunch(
                    mode.id, app.component.packageName, app.component.className, app.userSerial, System.currentTimeMillis(),
                )
            }
        }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { repository.openAppInfo(app) }
    }

    // --- Zmiany w trybach ---

    fun selectMode(mode: ModeEntity) {
        viewModelScope.launch {
            ModeActivation.activate(getApplication(), mode, manual = true) // zapis + ustawienia telefonu + kafelek
            _autoNotice.value = null
            manualDismissedFor.value = null
            suggestionRefresh.value++
        }
    }

    fun createMode(name: String, color: Long, icon: String? = null) {
        viewModelScope.launch {
            // Nowy tryb od razu staje się aktywny (najnowszy lastActiveAt).
            val modeId = modeDao.insert(
                ModeEntity(
                    name = name,
                    color = color,
                    icon = icon,
                    sortOrder = modes.value.size,
                    lastActiveAt = System.currentTimeMillis(),
                ),
            )
            applyNewModeDefaults(modeId)
        }
    }

    fun addToActiveMode(app: AppInfo) {
        val mode = activeMode.value ?: return // ?: return ≈ "if (mode == null) return;"
        val page = _currentPage.value
        viewModelScope.launch {
            val existing = cardItemDao.getForMode(mode.id)
            if (existing.any { app.matches(it) }) return@launch // już jest na karcie (na dowolnej stronie)

            // Wolne miejsce na bieżącej stronie, a gdy pełna — na kolejnej (także nowej, w limicie stron).
            val found = PageSpace(existing, appPrefs.maxPages.value).find(page, CardGrid.APP_SIZE, CardGrid.APP_SIZE)
            if (found == null) {
                Toast.makeText(getApplication(), "Wszystkie strony karty są pełne (limit stron zmienisz w Ustawieniach)", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val (target, spot) = found
            cardItemDao.insert(app.cardItem(mode.id, spot, target))
            showPlacedPage(target, page)
        }
    }

    // Upuszczenie ikony z szuflady w konkretne miejsce karty. Zajęte → najbliższe wolne (findFreeSpot).
    fun addToActiveModeAt(app: AppInfo, x: Int, y: Int) {
        val mode = activeMode.value ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            val all = cardItemDao.getForMode(mode.id)
            if (all.any { app.matches(it) }) {
                Toast.makeText(getApplication(), "${app.label} już jest na karcie", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val existing = all.filter { it.page == page }
            val wanted = GridRect(x, y, CardGrid.APP_SIZE, CardGrid.APP_SIZE)
            // Zajęte miejsce: odsuwamy to, co tam leży (id -1 = nowa ikona, jeszcze bez wiersza w bazie).
            val plan = CardGrid.placeWithPush(-1L, wanted, existing.associate { it.id to it.toRect() })
            // Nie da się odsunąć sąsiadów → pierwsze wolne pole na tej stronie albo na kolejnej.
            val found = if (plan != null) page to wanted
                else PageSpace(all, appPrefs.maxPages.value).find(page, CardGrid.APP_SIZE, CardGrid.APP_SIZE)
            if (found == null) {
                Toast.makeText(getApplication(), "Wszystkie strony karty są pełne (limit stron zmienisz w Ustawieniach)", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val (target, spot) = found
            database.withTransaction {
                plan?.filterKeys { it != -1L }?.forEach { (id, r) -> cardItemDao.updateRect(id, r.x, r.y, r.w, r.h) }
                cardItemDao.insert(app.cardItem(mode.id, spot, target))
            }
            showPlacedPage(target, page)
        }
    }

    // Ikona z karty wrzucona na widżet folderu: trafia do folderu i znika z karty.
    fun moveCardAppIntoFolder(element: CardApp, folderWidget: CardCustomWidget) {
        val folderId = folderWidget.config.optLong("folderId", -1).takeIf { it > 0 } ?: return
        val track = editing // odczyt od razu: "✕" może przyjść, zanim zapis się skończy
        folderDropJob = viewModelScope.launch {
            val added = insertIntoFolder(folderId, listOf(element.app))
            if (track) editFolderAdds += added // "✕" usunie je z folderu, a ikona wróci na kartę ze zdjęcia
            cardItemDao.delete(element.item.id)
        }
    }

    // --- Foldery na karcie (powstają z ikon; nie trafiają do szuflady) ---

    // Ikona upuszczona na ikonę: obie lądują w nowym folderze, w miejscu tej, na którą upuszczono.
    // draggedItemId = wiersz przeciąganej ikony na karcie (null, gdy przyszła z szuflady).
    fun mergeIntoCardFolder(target: CardApp, app: AppInfo, draggedItemId: Long?) {
        if (target.app.key == app.key) return
        folderDropJob = viewModelScope.launch {
            database.withTransaction {
                draggedItemId?.let { cardItemDao.delete(it) }
                cardItemDao.delete(target.item.id)
                val apps = listOf(target.app, app)
                cardItemDao.insert(
                    CardItemEntity(
                        modeId = target.item.modeId, type = CardItemEntity.TYPE_CUSTOM,
                        packageName = "", className = "", userSerial = 0,
                        x = target.item.x, y = target.item.y, w = target.item.w, h = target.item.h,
                        widgetKind = CustomWidgetKind.CARD_FOLDER.name,
                        config = CardFolderData(autoFolderName(apps), null, null, apps.map { it.key }).toJson(),
                        page = target.item.page,
                    ),
                )
            }
        }
    }

    // Świeży wiersz folderu z bazy (element z UI mógł się już zmienić, np. po dodaniu innej ikony).
    private suspend fun freshItem(element: CardElement): CardItemEntity? =
        cardItemDao.getForMode(element.item.modeId).firstOrNull { it.id == element.item.id }

    fun addToCardFolder(folder: CardCustomWidget, apps: List<AppInfo>, draggedItemId: Long? = null) {
        folderDropJob = viewModelScope.launch {
            database.withTransaction {
                val item = freshItem(folder) ?: return@withTransaction
                val data = CardFolderData.of(item.config)
                val added = apps.map { it.key }.filter { it !in data.keys }
                cardItemDao.updateConfig(item.id, data.copy(keys = data.keys + added).toJson())
                draggedItemId?.let { cardItemDao.delete(it) } // ikona z karty "wchodzi" do folderu
            }
        }
    }

    // Nazwa, wygląd, kolejność — wszystko, co zmienia sam opis folderu.
    fun updateCardFolder(folder: CardCustomWidget, change: (CardFolderData) -> CardFolderData) {
        viewModelScope.launch {
            database.withTransaction { // odczyt + zapis razem: dwie szybkie zmiany nie nadpiszą się nawzajem
                val item = freshItem(folder) ?: return@withTransaction
                cardItemDao.updateConfig(item.id, change(CardFolderData.of(item.config)).toJson())
            }
        }
    }

    // Wolne pole dla ikony jak najbliżej wskazanego miejsca.
    private fun spotNear(x: Int, y: Int, taken: List<GridRect>): GridRect? {
        val size = CardGrid.APP_SIZE
        val wanted = GridRect(x.coerceAtMost(CardGrid.COLUMNS - size), y.coerceAtMost((CardGrid.rows - size).coerceAtLeast(0)), size, size)
        return wanted.takeIf { CardGrid.canPlace(it, taken) } ?: CardGrid.nearestFreeSpot(wanted, taken)
    }

    // Wyjęcie aplikacji z folderu: na kartę obok folderu (toCard) albo po prostu z folderu.
    // Gdy w folderze zostanie jedna aplikacja, folder znika, a ona wraca na jego miejsce (jak w Androidzie).
    // path = podfolder, z którego wyjmujemy (pusta = główny). Automatyczne znikanie dotyczy tylko głównego folderu.
    fun takeOutOfCardFolder(folder: CardCustomWidget, app: AppInfo, toCard: Boolean, path: List<Int> = emptyList()) {
        viewModelScope.launch {
            val message = database.withTransaction {
                val item = freshItem(folder) ?: return@withTransaction null
                val data = CardFolderData.of(item.config)
                val taken0 = pageItems(item.modeId, item.page).filter { it.id != item.id }.map { it.toRect() }.toMutableList()
                if (path.isNotEmpty()) {
                    // Z podfolderu: tylko usuwamy klucz; folder na karcie zostaje, jak był.
                    cardItemDao.updateConfig(item.id, data.update(path) { it.copy(keys = it.keys - app.key) }.toJson())
                    taken0 += item.toRect()
                    if (!toCard) return@withTransaction null
                    val spot = spotNear(item.x, item.y, taken0) ?: return@withTransaction "Brak miejsca na karcie dla ${app.label}"
                    appItemFor(item.modeId, app.key, spot, item.page)?.let { cardItemDao.insert(it) }
                    return@withTransaction null
                }
                val rest = data.keys - app.key
                // Liczą się tylko zainstalowane (odinstalowane klucze zostają w folderze, ale ich nie widać).
                val installed = (allApps.value + _shortcuts.value).map { it.key }.toSet()
                val visibleRest = rest.filter { it in installed }
                val taken = pageItems(item.modeId, item.page).filter { it.id != item.id }.map { it.toRect() }.toMutableList()
                if (!data.keep && data.children.isEmpty() && visibleRest.size <= 1) {
                    cardItemDao.delete(item.id)
                    visibleRest.firstOrNull()?.let { key ->
                        val spot = spotNear(item.x, item.y, taken)
                        spot?.let { appItemFor(item.modeId, key, it, item.page) }?.let { e ->
                            cardItemDao.insert(e)
                            taken += e.toRect()
                        }
                    }
                } else {
                    cardItemDao.updateConfig(item.id, data.copy(keys = rest).toJson())
                    taken += item.toRect()
                }
                if (!toCard) return@withTransaction null
                val spot = spotNear(item.x, item.y, taken) ?: return@withTransaction "Brak miejsca na karcie dla ${app.label}"
                appItemFor(item.modeId, app.key, spot, item.page)?.let { cardItemDao.insert(it) }
                null
            }
            message?.let { Toast.makeText(getApplication(), it, Toast.LENGTH_SHORT).show() }
        }
    }

    // "Rozwiąż folder": wszystkie aplikacje wracają na kartę wokół miejsca folderu.
    fun dissolveCardFolder(folder: CardCustomWidget) {
        viewModelScope.launch {
            val skipped = database.withTransaction {
                val item = freshItem(folder) ?: return@withTransaction 0
                cardItemDao.delete(item.id)
                val taken = pageItems(item.modeId, item.page).map { it.toRect() }.toMutableList()
                var missing = 0
                val installed = (allApps.value + _shortcuts.value).map { it.key }.toSet()
                CardFolderData.of(item.config).allKeys().distinct().filter { it in installed }.forEach { key -> // razem z podfolderami
                    val spot = spotNear(item.x, item.y, taken)
                    val entity = spot?.let { appItemFor(item.modeId, key, it, item.page) }
                    if (entity == null) {
                        missing++
                    } else {
                        cardItemDao.insert(entity)
                        taken += entity.toRect()
                    }
                }
                missing
            }
            if (skipped > 0) Toast.makeText(getApplication(), "Brak miejsca dla $skipped aplikacji", Toast.LENGTH_SHORT).show()
        }
    }

    // "Zapisz w szufladzie": kopia folderu z karty (z podfolderami) jako folder w zakładce Foldery.
    // Skróty zostają pominięte — foldery szuflady trzymają tylko aplikacje.
    fun saveCardFolderToDrawer(data: CardFolderData) {
        viewModelScope.launch {
            suspend fun insert(folder: CardFolderData, parentId: Long?) {
                val id = folderDao.insertFolder(FolderEntity(parentId = parentId, name = folder.name, icon = folder.icon, color = folder.color))
                val apps = folder.keys.mapNotNull { key ->
                    if (key.startsWith(SHORTCUT_KEY)) return@mapNotNull null
                    parseAppKey(key)?.let { (component, serial) ->
                        FolderAppEntity(folderId = id, packageName = component.packageName, className = component.className, userSerial = serial)
                    }
                }
                if (apps.isNotEmpty()) folderDao.insertApps(apps)
                folder.children.forEach { insert(it, id) }
            }
            database.withTransaction { insert(data, null) } // wszystko albo nic (bez połowy drzewa po błędzie)
            Toast.makeText(getApplication(), "Zapisano „${data.name}” w szufladzie (Foldery)", Toast.LENGTH_SHORT).show()
        }
    }

    // "Kopiuj do trybu…": niezależna kopia folderu z karty na kartę innego trybu (w wolnym miejscu).
    fun copyCardFolderToMode(data: CardFolderData, modeId: Long, w: Int, h: Int) {
        viewModelScope.launch {
            // Kopia trafia na pierwszą stronę tamtego trybu (albo dalszą, gdy pierwsza pełna).
            val found = pageSpace(modeId).find(0, w, h) ?: pageSpace(modeId).find(0, CardGrid.APP_SIZE, CardGrid.APP_SIZE)
            val modeName = modes.value.firstOrNull { it.id == modeId }?.name.orEmpty()
            if (found == null) {
                Toast.makeText(getApplication(), "Karta trybu $modeName jest pełna", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val (targetPage, spot) = found
            cardItemDao.insert(
                CardItemEntity(
                    modeId = modeId, type = CardItemEntity.TYPE_CUSTOM,
                    packageName = "", className = "", userSerial = 0,
                    x = spot.x, y = spot.y, w = spot.w, h = spot.h,
                    widgetKind = CustomWidgetKind.CARD_FOLDER.name,
                    config = data.copy(keep = true).toJson(),
                    page = targetPage,
                ),
            )
            Toast.makeText(getApplication(), "Skopiowano „${data.name}” do trybu $modeName", Toast.LENGTH_SHORT).show()
        }
    }

    // Folder z szuflady jako NIEZALEŻNA kopia na karcie aktywnego trybu (zmiany nie wracają do szuflady).
    fun copyDrawerFolderToCard(folderId: Long) {
        val tree = folderTree.value
        fun build(id: Long): CardFolderData? {
            val f = tree.folder(id) ?: return null
            return CardFolderData(
                name = f.name, icon = f.icon, color = f.color,
                keys = tree.apps(id).map { it.app.key },
                keep = true,
                children = tree.subfolders(id).mapNotNull { build(it.id) },
            )
        }
        val data = build(folderId) ?: return
        addCustomWidget(CustomWidgetKind.CARD_FOLDER, data.toJson())
    }

    // Folder z szuflady przeciągnięty na kartę: kładziemy go w rozmiarze ikony tam, gdzie puszczono palec.
    fun placeFolderAt(folderId: Long, x: Int, y: Int) {
        val mode = activeMode.value ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            var placedOn: Int? = null // inna strona, gdy bieżąca była pełna
            val message = database.withTransaction {
                val all = cardItemDao.getForMode(mode.id)
                if (all.any { it.isFolderWidget(folderId) }) return@withTransaction "Ten folder już jest na karcie"
                val existing = all.filter { it.page == page }
                val size = CardGrid.APP_SIZE
                val wanted = GridRect(x, y, size, size)
                val plan = CardGrid.placeWithPush(-1L, wanted, existing.associate { it.id to it.toRect() })
                val (target, spot) = (if (plan != null) page to wanted else PageSpace(all, appPrefs.maxPages.value).find(page, size, size))
                    ?: return@withTransaction "Wszystkie strony karty są pełne (limit stron zmienisz w Ustawieniach)"
                if (target != page) placedOn = target
                plan?.filterKeys { it != -1L }?.forEach { (id, r) -> cardItemDao.updateRect(id, r.x, r.y, r.w, r.h) }
                cardItemDao.insert(
                    CardItemEntity(
                        modeId = mode.id, type = CardItemEntity.TYPE_CUSTOM,
                        packageName = "", className = "", userSerial = 0,
                        x = spot.x, y = spot.y, w = size, h = size,
                        widgetKind = CustomWidgetKind.FOLDER.name,
                        config = JSONObject().put("folderId", folderId).toString(),
                        page = target,
                    ),
                )
                null
            }
            message?.let { Toast.makeText(getApplication(), it, Toast.LENGTH_SHORT).show() }
            placedOn?.let { showPlacedPage(it, page) }
        }
    }

    // Zmiana rozmiaru ikon aplikacji (1×1 / 2×2) na WSZYSTKICH kartach. Przy powiększaniu ikona, która
    // zaczęłaby na coś nachodzić, przenosi się w najbliższe wolne miejsce.
    fun setAppIconCells(size: Int) {
        if (size == appPrefs.appIconCells.value) return
        appPrefs.appIconCells.set(size)
        val overrides = modeLayouts.value
        viewModelScope.launch {
            // Tryby z własnym rozmiarem ikon (ustawienia trybu) zostają bez zmian.
            cardItemDao.getAll().groupBy { it.modeId }.forEach { (modeId, items) ->
                if (overrides[modeId]?.iconCells == null) resizeIcons(items, size)
            }
        }
    }

    // Przeliczenie ikon jednej karty na nowy rozmiar (1×1 / 2×2), z przenoszeniem kolidujących w wolne miejsca.
    private suspend fun resizeIcons(items: List<CardItemEntity>, size: Int) {
        items.groupBy { it.page }.values.forEach { resizeIconsOnPage(it, size) } // każda strona osobno
    }

    private suspend fun resizeIconsOnPage(items: List<CardItemEntity>, size: Int) {
        val isIcon = { i: CardItemEntity -> i.type == CardItemEntity.TYPE_APP || i.type == CardItemEntity.TYPE_SHORTCUT }
        val placed = items.filterNot(isIcon).map { it.toRect() }.toMutableList()
        items.filter(isIcon).sortedWith(compareBy({ it.y }, { it.x })).forEach { app ->
            val wanted = GridRect(app.x.coerceAtMost(CardGrid.COLUMNS - size), app.y, size, size)
            val spot = wanted.takeIf { CardGrid.canPlace(it, placed) } ?: CardGrid.findFreeSpot(placed, size, size)
            if (spot == null) {
                cardItemDao.delete(app.id) // karta pełna: ikona i tak jest w szufladzie
            } else {
                placed += spot
                cardItemDao.updateRect(app.id, spot.x, spot.y, spot.w, spot.h)
            }
        }
    }

    // --- Układ karty per tryb ---

    val modeLayouts: StateFlow<Map<Long, ModeLayout>> = appPrefs.modeLayouts.flow
        .map { ModeLayout.parseAll(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModeLayout.parseAll(appPrefs.modeLayouts.value))

    // Układ aktywnego trybu (odstępy dla karty); rozmiar ikon liczy się niżej razem z ustawieniem globalnym.
    val activeLayout: StateFlow<ModeLayout> = combine(activeMode, modeLayouts) { mode, all -> mode?.let { all[it.id] } ?: ModeLayout.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModeLayout.DEFAULT)

    fun setModeLayout(mode: ModeEntity, layout: ModeLayout) {
        val all = ModeLayout.parseAll(appPrefs.modeLayouts.value) // prosto z ustawień: dwie szybkie zmiany się nie zgubią
        val before = all[mode.id] ?: ModeLayout.DEFAULT
        appPrefs.modeLayouts.set(ModeLayout.writeAll(all + (mode.id to layout)))
        val global = appPrefs.appIconCells.value
        val oldSize = before.iconCells ?: global
        val newSize = layout.iconCells ?: global
        if (oldSize != newSize) {
            if (activeMode.value?.id == mode.id) CardGrid.appSize = newSize
            viewModelScope.launch { resizeIcons(cardItemDao.getForMode(mode.id), newSize) }
        }
    }

    // Wynik przeciągania / zmiany rozmiaru z wypychaniem: kilka elementów naraz, w jednej transakcji.
    fun applyLayout(changes: Map<Long, GridRect>) {
        viewModelScope.launch {
            database.withTransaction {
                changes.forEach { (id, r) -> cardItemDao.updateRect(id, r.x, r.y, r.w, r.h) }
            }
        }
    }

    // Kilka aplikacji naraz (przycisk "+ Aplikacje" w edycji układu) — każda w pierwsze wolne miejsce.
    fun addAppsToActiveMode(apps: List<AppInfo>) {
        val mode = activeMode.value ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            val existing = cardItemDao.getForMode(mode.id)
            val space = PageSpace(existing, appPrefs.maxPages.value) // pełna strona → dalej na kolejnych
            var skipped = 0
            var lastPage = page
            apps.filter { app -> existing.none { app.matches(it) } }.forEach { app ->
                val found = space.find(page, CardGrid.APP_SIZE, CardGrid.APP_SIZE)
                if (found == null) {
                    skipped++
                    return@forEach
                }
                val (target, spot) = found
                lastPage = maxOf(lastPage, target)
                cardItemDao.insert(app.cardItem(mode.id, spot, target))
            }
            if (skipped > 0) Toast.makeText(getApplication(), "Brak miejsca dla $skipped aplikacji (limit stron)", Toast.LENGTH_SHORT).show()
            if (lastPage != page) {
                if (skipped == 0) Toast.makeText(getApplication(), "Część aplikacji trafiła na stronę ${lastPage + 1}", Toast.LENGTH_SHORT).show()
                withTimeoutOrNull(700) { usedPages.first { it > lastPage } }
                animateNextPage = true
                _currentPage.value = lastPage
            }
        }
    }

    // --- Edycja układu z zapisem / anulowaniem ---
    // Zmiany trafiają do bazy od razu (widać je na żywo), ale przy wejściu w edycję robimy zdjęcie karty.
    // "✕" przywraca zdjęcie; "✓", Wstecz i Home zatwierdzają. Nieodwracalne sprzątanie (identyfikatory
    // widżetów u systemu, pliki naklejek) odkładamy do zatwierdzenia — jak transakcja z COMMIT / ROLLBACK.
    private var editSnapshot: Pair<Long, List<CardItemEntity>>? = null
    private val editCleanups = mutableListOf<CardItemEntity>()
    private val editFolderAdds = mutableListOf<Long>() // wiersze folder_apps dodane upuszczeniem na folder
    private var folderDropJob: Job? = null               // ostatnie upuszczenie na folder — anulowanie na nie czeka
    private var editing = false          // ustawiane od razu (synchronicznie), zanim zdjęcie się wczyta
    private var beginJob: Job? = null     // odczyt zdjęcia z bazy — zapis/anulowanie najpierw na niego czeka

    val isEditing: Boolean get() = editing

    fun beginEdit() {
        val mode = activeMode.value ?: return
        if (editing) return
        editing = true
        editCleanups.clear()
        editFolderAdds.clear()
        beginJob = viewModelScope.launch { editSnapshot = mode.id to cardItemDao.getForMode(mode.id) }
    }

    fun commitEdit() {
        if (!editing) return
        editing = false
        viewModelScope.launch {
            beginJob?.join()
            folderDropJob?.join()
            editJobs.toList().forEach { it.join() } // najpierw dokończone zmiany stosów, potem porządki
            editJobs.clear()
            val modeId = editSnapshot?.first
            editSnapshot = null
            editFolderAdds.clear()
            modeId?.let {
                repairStacks(cardItemDao, it) // stosy z jednym widżetem się rozpadają, sieroty wracają na kartę
                compactPages(it)
            }
            val toClean = editCleanups.toList()
            editCleanups.clear()
            toClean.forEach(::cleanupItem)
        }
    }

    fun cancelEdit() {
        if (!editing) return
        editing = false
        viewModelScope.launch {
            beginJob?.join()
            folderDropJob?.join()
            editJobs.toList().forEach { it.join() }
            editJobs.clear()
            val (modeId, snapshot) = editSnapshot ?: return@launch
            editSnapshot = null
            editCleanups.clear()
            val folderAdds = editFolderAdds.toList()
            editFolderAdds.clear()
            if (folderAdds.isNotEmpty()) folderDao.deleteApps(folderAdds)
            val snapshotIds = snapshot.map { it.id }.toSet()
            // Elementy dodane w trakcie edycji znikają razem ze swoimi zasobami.
            cardItemDao.getForMode(modeId).filter { it.id !in snapshotIds }.forEach(::cleanupItem)
            database.withTransaction {
                cardItemDao.deleteForMode(modeId)
                snapshot.forEach { cardItemDao.insert(it) } // z tymi samymi id, więc widżety i foldery wracają
            }
        }
    }

    private fun cleanupItem(item: CardItemEntity) {
        item.appWidgetId?.let(widgets::deleteId)
        if (item.widgetKind == CustomWidgetKind.STICKER.name) {
            val cfg = runCatching { JSONObject(item.config ?: "{}") }.getOrNull() ?: return
            // Plik naklejki i (jeśli była wycinana) jej oryginał.
            listOf(cfg.optString("file"), cfg.optString("original")).filter { it.isNotBlank() }.forEach(StickerStore::delete)
        }
    }

    // --- Naklejki ---

    // Zmiana ustawień naklejki na ŚWIEŻYM odczycie z bazy (żeby nie nadpisać ścieżki pliku po wycinaniu).
    fun updateStickerConfig(widget: CardCustomWidget, change: (JSONObject) -> Unit) {
        viewModelScope.launch {
            database.withTransaction {
                val item = freshItem(widget) ?: return@withTransaction
                val cfg = runCatching { JSONObject(item.config ?: "{}") }.getOrDefault(JSONObject())
                change(cfg)
                cardItemDao.updateConfig(item.id, cfg.toString())
            }
        }
    }

    // Naklejka poprawiona w StickOnMe: kopiujemy wynik do plików launchera (jak przy dodawaniu),
    // a poprzednią wersję zostawiamy jako "oryginał" — "Przywróć oryginał" dalej działa.
    fun replaceStickerFile(widgetId: Long, source: android.net.Uri) {
        viewModelScope.launch {
            val newPath = runCatching { StickerStore.import(getApplication(), source) }.getOrNull() ?: return@launch
            val item = cardItemDao.getById(widgetId) ?: return@launch StickerStore.delete(newPath)
            val cfg = runCatching { JSONObject(item.config ?: "{}") }.getOrDefault(JSONObject())
            val current = cfg.optString("file")
            val original = cfg.optString("original").ifBlank { current }
            if (current.isNotBlank() && current != original) StickerStore.delete(current)
            cfg.put("file", newPath)
            if (original.isNotBlank()) cfg.put("original", original)
            // Kształt i ramka są teraz "wypalone" w pliku przez StickOnMe — karta już ich nie dokłada.
            // Dawne ustawienia z karty pamiętamy przy pierwszej wersji, żeby "Przywróć" oddało jej wygląd.
            cfg.optString("shape").takeIf { it.isNotBlank() }?.let { if (!cfg.has("originalShape")) cfg.put("originalShape", it) }
            cfg.optString("frame").takeIf { it.isNotBlank() }?.let { if (!cfg.has("originalFrame")) cfg.put("originalFrame", it) }
            cfg.remove("shape")
            cfg.remove("frame")
            cardItemDao.updateConfig(item.id, cfg.toString())
        }
    }

    // Powrót do zdjęcia sprzed wycięcia.
    fun restoreStickerOriginal(widget: CardCustomWidget) {
        viewModelScope.launch {
            val item = freshItem(widget) ?: return@launch
            val cfg = JSONObject(item.config ?: "{}")
            val original = cfg.optString("original").takeIf { it.isNotBlank() } ?: return@launch
            val current = cfg.optString("file")
            if (current != original) StickerStore.delete(current)
            cfg.put("file", original).remove("original")
            cfg.optString("originalShape").takeIf { it.isNotBlank() }?.let { cfg.put("shape", it) }
            cfg.optString("originalFrame").takeIf { it.isNotBlank() }?.let { cfg.put("frame", it) }
            cfg.remove("originalShape")
            cfg.remove("originalFrame")
            cardItemDao.updateConfig(item.id, cfg.toString())
        }
    }

    fun moveItem(element: CardElement, x: Int, y: Int) {
        viewModelScope.launch { cardItemDao.updatePosition(element.item.id, x, y) }
    }

    fun resizeItem(element: CardElement, w: Int, h: Int) {
        viewModelScope.launch { cardItemDao.updateSize(element.item.id, w, h) }
    }

    fun removeFromMode(element: CardElement) {
        viewModelScope.launch {
            // Usunięcie stosu usuwa też jego widżety (inaczej zostałyby niewidoczne, bez strony).
            // Listę bierzemy świeżo z bazy — w UI mogła jeszcze nie dotrzeć ostatnio dorzucona pozycja.
            val removed = database.withTransaction {
                val fresh = freshItem(element) ?: element.item
                val list = listOf(fresh) + if (fresh.widgetKind == CustomWidgetKind.STACK.name) {
                    val ids = StackData.of(fresh.config).members.toSet()
                    cardItemDao.getForMode(fresh.modeId).filter { it.id in ids }
                } else emptyList()
                list.forEach { cardItemDao.delete(it.id) }
                list
            }
            // W edycji układu sprzątanie czeka na "✓" (żeby "✕" mógł przywrócić widżet razem z jego danymi).
            if (editing) editCleanups += removed else {
                removed.forEach(::cleanupItem)
                compactPages(element.item.modeId) // poza edycją pusta strona znika od razu
            }
        }
    }

    // --- Stosy widżetów ---

    // Zmiany stosów w edycji: "✕" i "✓" czekają, aż się zapiszą (inaczej spóźniony zapis nadpisałby przywrócone zdjęcie).
    private val editJobs = mutableListOf<Job>()

    private fun launchStackChange(block: suspend () -> Unit) {
        val job = viewModelScope.launch { block() }
        if (editing) editJobs += job
    }

    // Widżet upuszczony na widżet: powstaje stos (albo widżet dołącza do istniejącego stosu) w miejscu celu.
    fun stackWidgets(dragged: CardElement, target: CardElement) {
        if (dragged.item.id == target.item.id) return
        launchStackChange {
            database.withTransaction {
                val t = freshItem(target) ?: return@withTransaction
                val d = freshItem(dragged) ?: return@withTransaction
                if (t.widgetKind == CustomWidgetKind.STACK.name) {
                    val data = StackData.of(t.config)
                    cardItemDao.updateConfig(t.id, data.copy(members = data.members + d.id, index = data.members.size).toJson()) // nowy na wierzch
                } else {
                    cardItemDao.insert(
                        CardItemEntity(
                            modeId = t.modeId, type = CardItemEntity.TYPE_CUSTOM,
                            packageName = "", className = "", userSerial = 0,
                            x = t.x, y = t.y, w = t.w, h = t.h,
                            widgetKind = CustomWidgetKind.STACK.name,
                            config = StackData(listOf(t.id, d.id), 1).toJson(),
                            page = t.page,
                        ),
                    )
                    cardItemDao.updatePage(t.id, STACKED_PAGE, t.x, t.y)
                }
                cardItemDao.updatePage(d.id, STACKED_PAGE, d.x, d.y)
            }
        }
    }

    // Przewinięcie stosu: zapamiętujemy, który widżet jest na wierzchu (po powrocie na kartę będzie ten sam).
    fun setStackIndex(stack: CardCustomWidget, index: Int) {
        if (index < 0) return
        launchStackChange {
            database.withTransaction {
                val item = freshItem(stack) ?: return@withTransaction
                val data = StackData.of(item.config)
                if (data.index != index) cardItemDao.updateConfig(item.id, data.copy(index = index.coerceIn(0, (data.members.size - 1).coerceAtLeast(0))).toJson())
            }
        }
    }

    // Gdzie przesunięcie w pionie zmienia widżet stosu (okno "Stos"): auto / cała powierzchnia / tylko uchwyt.
    fun setStackSwipe(stack: CardCustomWidget, swipe: String) {
        launchStackChange {
            database.withTransaction {
                val item = freshItem(stack) ?: return@withTransaction
                cardItemDao.updateConfig(item.id, StackData.of(item.config).copy(swipe = swipe).toJson())
            }
        }
    }

    // Kolejność w stosie (okno "Stos"): przesunięcie widżetu o jedno miejsce wyżej/niżej.
    fun moveInStack(stack: CardCustomWidget, memberId: Long, delta: Int) {
        launchStackChange {
            database.withTransaction {
                val item = freshItem(stack) ?: return@withTransaction
                val data = StackData.of(item.config)
                val from = data.members.indexOf(memberId)
                val to = from + delta
                if (from < 0 || to !in data.members.indices) return@withTransaction
                val list = data.members.toMutableList().apply { add(to, removeAt(from)) }
                cardItemDao.updateConfig(item.id, data.copy(members = list, index = to).toJson())
            }
        }
    }

    // "Rozdziel stos": pierwszy widżet zajmuje miejsce stosu, reszta obok. Bez miejsca — na nową stronę za ostatnią.
    fun dissolveStack(stack: CardCustomWidget) {
        launchStackChange {
            val moved = database.withTransaction {
                val s = freshItem(stack) ?: return@withTransaction 0
                val data = StackData.of(s.config)
                val all = cardItemDao.getForMode(s.modeId)
                // Najpierw plan w pamięci (id → strona i miejsce), zapis dopiero, gdy wszystko się mieści.
                val taken = all.filter { it.page == s.page && it.id != s.id }.map { it.toRect() }.toMutableList()
                val extraPage = (all.maxOfOrNull { it.page } ?: 0) + 1
                val extraTaken = mutableListOf<GridRect>()
                val plan = mutableListOf<Pair<Long, Triple<Int, GridRect, Boolean>>>() // id → (strona, miejsce, czy rozmiar stosu)
                var toExtra = 0
                data.members.forEachIndexed { i, id ->
                    val m = all.firstOrNull { it.id == id } ?: return@forEachIndexed
                    if (plan.isEmpty()) {
                        plan += m.id to Triple(s.page, GridRect(s.x, s.y, s.w, s.h), true) // pierwszy na miejsce stosu
                        taken += GridRect(s.x, s.y, s.w, s.h)
                        return@forEachIndexed
                    }
                    val w = m.w.coerceAtMost(CardGrid.COLUMNS)
                    val spot = CardGrid.nearestFreeSpot(GridRect(s.x.coerceAtMost(CardGrid.COLUMNS - w), s.y, w, m.h), taken)
                        ?: CardGrid.findFreeSpot(taken, w, m.h)
                    if (spot != null) {
                        plan += m.id to Triple(s.page, spot, false)
                        taken += spot
                    } else {
                        val extra = CardGrid.findFreeSpot(extraTaken, w, m.h) ?: GridRect(0, 0, w, m.h)
                        plan += m.id to Triple(extraPage, extra, false)
                        extraTaken += extra
                        toExtra++
                    }
                }
                // Nowa strona tylko w granicach limitu z Ustawień — inaczej stos zostaje, jak był.
                if (toExtra > 0 && extraPage >= appPrefs.maxPages.value) return@withTransaction -1
                cardItemDao.delete(s.id)
                plan.forEach { (id, target) ->
                    val (page, rect, stackSize) = target
                    cardItemDao.updatePage(id, page, rect.x, rect.y)
                    if (stackSize) cardItemDao.updateSize(id, rect.w, rect.h)
                }
                toExtra
            }
            when {
                moved < 0 -> Toast.makeText(getApplication(), "Za mało miejsca, żeby rozdzielić stos", Toast.LENGTH_SHORT).show()
                moved > 0 -> Toast.makeText(getApplication(), "Brak miejsca — $moved widżet(y) na nowej stronie", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Wyjęcie widżetu ze stosu na kartę (obok stosu). Gdy zostanie jeden, stos znika, a ten widżet zajmuje jego miejsce.
    fun unstack(stack: CardCustomWidget, memberId: Long) {
        launchStackChange {
            val message = database.withTransaction {
                val s = freshItem(stack) ?: return@withTransaction null
                val data = StackData.of(s.config)
                val all = cardItemDao.getForMode(s.modeId)
                val member = all.firstOrNull { it.id == memberId } ?: return@withTransaction null
                val taken = all.filter { it.page == s.page }.map { it.toRect() }
                val w = member.w.coerceAtMost(CardGrid.COLUMNS)
                val wanted = GridRect(s.x.coerceAtMost(CardGrid.COLUMNS - w), s.y, w, member.h)
                val spot = CardGrid.nearestFreeSpot(wanted, taken) ?: CardGrid.findFreeSpot(taken, w, member.h)
                    ?: return@withTransaction "Brak miejsca na tej stronie karty"
                cardItemDao.updatePage(member.id, s.page, spot.x, spot.y)
                val rest = data.members - memberId
                if (rest.size <= 1) {
                    rest.firstOrNull()?.let { last ->
                        cardItemDao.updatePage(last, s.page, s.x, s.y)
                        cardItemDao.updateSize(last, s.w, s.h)
                    }
                    cardItemDao.delete(s.id)
                } else {
                    cardItemDao.updateConfig(s.id, data.copy(members = rest, index = data.index.coerceAtMost(rest.size - 1)).toJson())
                }
                null
            }
            message?.let { Toast.makeText(getApplication(), it, Toast.LENGTH_SHORT).show() }
        }
    }

    // --- Automatyczne przełączanie ---

    // Kiedy po raz pierwszy zobaczyliśmy daną sugestię — przełączamy dopiero, gdy utrzyma się minutę
    // (np. chwilowe zerwanie Bluetooth nie przerzuci trybu tam i z powrotem).
    private var autoCandidate: Pair<String, Long>? = null
    private var autoRecheck: Job? = null

    // Informacja w nagłówku po automatycznym przełączeniu: ✓ zostaw, ✗ cofnij.
    data class AutoSwitchNotice(
        val modeId: Long,
        val previousModeId: Long?,
        val suggestionKey: String,
        val reason: String = "automatycznie", // np. "koniec czasu"
    )

    private val _autoNotice = MutableStateFlow<AutoSwitchNotice?>(null)
    val autoNotice: StateFlow<AutoSwitchNotice?> = _autoNotice.asStateFlow()

    fun keepAutoSwitch() {
        _autoNotice.value = null
    }

    // Cofnięcie: wracamy do poprzedniego trybu i chowamy tę sugestię (jak "×" na banerze),
    // żeby automat nie przełączył z powrotem po minucie.
    fun undoAutoSwitch() {
        val notice = _autoNotice.value ?: return
        _autoNotice.value = null
        dismissByKey(notice.suggestionKey)
        modes.value.firstOrNull { it.id == notice.previousModeId }?.let(::selectMode)
    }

    // Dlaczego automat jeszcze nie przełączył — pokazujemy to w podpowiedzi, żeby dało się to sprawdzić na telefonie.
    private val _autoStatus = MutableStateFlow<String?>(null)
    val autoStatus: StateFlow<String?> = _autoStatus.asStateFlow()

    private fun maybeAutoSwitch(suggestion: Suggestion?) {
        autoRecheck?.cancel()
        _autoStatus.value = null
        if (suggestion == null || !appPrefs.autoSwitch.value) {
            autoCandidate = null
            return
        }
        val (targetId, leavingId) = when (suggestion) {
            is Suggestion.SwitchTo -> suggestion.modeId to activeMode.value?.id
            is Suggestion.EndMode -> suggestion.backToModeId to suggestion.activeModeId
        }
        // Wyjątek "zawsze pytaj": ani nie włączamy takiego trybu sami, ani sami go nie kończymy.
        val alwaysAsk = appPrefs.alwaysAskModes.value
        if (targetId.toString() in alwaysAsk || (leavingId != null && leavingId.toString() in alwaysAsk)) {
            _autoStatus.value = "automat: ten tryb ma „zawsze pytaj”"
            return
        }

        // W trakcie edycji układu nie zmieniamy trybu pod palcami — sprawdzimy za minutę.
        if (editing) {
            scheduleAutoRecheck(60_000)
            return
        }
        val now = System.currentTimeMillis()
        // Tryb włączony "na czas" ma pierwszeństwo — automat poczeka, aż czas minie.
        val timedUntil = appPrefs.timedUntil.value
        if (timedUntil > now) {
            _autoStatus.value = "automat: tryb na czas do ${formatTime(timedUntil)}"
            scheduleAutoRecheck(timedUntil - now) // po końcu czasu sprawdzimy jeszcze raz (StateFlow sam nie powtórzy)
            return
        }
        val since = autoCandidate?.takeIf { it.first == suggestion.key }?.second ?: now
        autoCandidate = suggestion.key to since

        // Ochrona człowieka: jeśli ta sugestia wisiała już wtedy, gdy ktoś ręcznie wybrał inny tryb,
        // to znaczy, że ją świadomie pominął — nie narzucamy jej przez 30 minut.
        // Nowa sugestia (np. właśnie połączył się Bluetooth) działa od razu.
        val lastManual = appPrefs.lastManualSwitch.value
        val graceLeft = MANUAL_GRACE_MS - (now - lastManual)
        // Dwa przypadki: sugestia wisiała już przy ręcznej zmianie ALBO chce wrócić do trybu, z którego właśnie wyszedłeś.
        val backToLeft = targetId == appPrefs.lastManualLeft.value
        if ((since <= lastManual || backToLeft) && graceLeft > 0) {
            _autoStatus.value = "automat czeka ${graceLeft / 60_000 + 1} min po ręcznej zmianie"
            scheduleAutoRecheck(graceLeft)
            return
        }

        if (now - since < STABLE_MS) {
            // Sugestia może się już nie zmienić (StateFlow nie powtarza tej samej wartości), więc sprawdzamy ponownie sami.
            _autoStatus.value = "automat przełączy za ${(STABLE_MS - (now - since)) / 1000 + 1} s"
            scheduleAutoRecheck(STABLE_MS - (now - since))
            return
        }

        val mode = modes.value.firstOrNull { it.id == targetId } ?: return
        val previousId = activeMode.value?.id
        autoCandidate = null
        viewModelScope.launch {
            ModeActivation.activate(getApplication(), mode, manual = false)
            _autoNotice.value = AutoSwitchNotice(mode.id, previousId, suggestion.key)
            suggestionRefresh.value++
        }
    }

    private fun scheduleAutoRecheck(afterMs: Long) {
        autoRecheck = viewModelScope.launch {
            delay(afterMs + 1_000)
            maybeAutoSwitch(this@LauncherViewModel.suggestion.value) // pole klasy, nie parametr o tej samej nazwie
        }
    }

    // Stan wszystkich zgód na potrzeby listy w Ustawieniach.
    data class PermissionStatus(
        val isDefaultLauncher: Boolean,
        val calendar: Boolean,
        val location: Boolean,
        val bluetooth: Boolean,
        val dnd: Boolean,
        val writeSettings: Boolean,
        val notificationDots: Boolean,
    )

    fun permissionStatus(): PermissionStatus {
        val app = getApplication<Application>()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = runCatching {
            app.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        return PermissionStatus(
            isDefaultLauncher = defaultHome == app.packageName,
            calendar = calendar.hasPermission(),
            location = signalsReader.hasLocationPermission(),
            bluetooth = signalsReader.hasBluetoothPermission(),
            dnd = phoneSettings.hasDndAccess(),
            writeSettings = phoneSettings.canWriteSettings(),
            notificationDots = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName),
        )
    }

    fun setAlwaysAsk(mode: ModeEntity, alwaysAsk: Boolean) {
        val current = appPrefs.alwaysAskModes.value
        appPrefs.alwaysAskModes.set(if (alwaysAsk) current + mode.id.toString() else current - mode.id.toString())
    }

    // --- Kopia, skróty, zaawansowane ---

    fun exportTo(uri: Uri, withFiles: Boolean = false) {
        viewModelScope.launch {
            val ok = runCatching { Backup.export(getApplication(), uri, withFiles) }.isSuccess
            Toast.makeText(getApplication(), if (ok) "Zapisano konfigurację" else "Nie udało się zapisać pliku", Toast.LENGTH_SHORT).show()
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching { Backup.import(getApplication(), uri) }
            val message = result.exceptionOrNull()?.let { "Import nieudany: ${it.message ?: "błędny plik"}" }
                ?: "Wczytano konfigurację"
            Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
            suggestionRefresh.value++
            // Tapety mogły przyjść z kopii — pokazujemy tapetę aktywnego trybu (i odświeżamy stan w ustawieniach).
            if (result.isSuccess) {
                _wallpaperVersion.value++
                if (wallpapers.hasAny()) appPrefs.showWallpaper.set(true)
                // activeMode jeszcze wskazuje stary (usunięty) tryb — bierzemy aktywny prosto z bazy.
                val active = runCatching { modeDao.getAll().maxByOrNull { it.lastActiveAt } }.getOrNull()
                active?.let { wallpapers.applyFor(it.id) }
            }
        }
    }

    fun setModeShortcuts(enabled: Boolean) {
        appPrefs.modeShortcuts.set(enabled)
        if (enabled) ModeShortcuts.publish(getApplication(), modes.value) else ModeShortcuts.clear(getApplication())
    }

    fun clearLaunchStats() {
        viewModelScope.launch {
            suggestionDao.clearStats()
            Toast.makeText(getApplication(), "Wyczyszczono „często używane”", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Tryb na czas ---

    val timedUntil: StateFlow<Long> = appPrefs.timedUntil.flow
    private var timerJob: Job? = null

    // Włącza tryb na określony czas; potem launcher sam wraca tam, gdzie był (albo na stronę główną).
    fun selectModeFor(mode: ModeEntity, durationMs: Long) {
        val returnTo = activeMode.value?.id?.takeIf { it != mode.id } ?: appPrefs.homeModeId.value ?: -1L
        viewModelScope.launch {
            ModeActivation.activate(getApplication(), mode, manual = true) // zapisuje też lastManualSwitch
            _autoNotice.value = null
            appPrefs.timedReturnTo.set(returnTo)
            appPrefs.timedUntil.set(System.currentTimeMillis() + durationMs)
            suggestionRefresh.value++
            scheduleTimer()
        }
    }

    fun extendTimed(extraMs: Long) {
        val until = appPrefs.timedUntil.value
        if (until == 0L) return
        appPrefs.timedUntil.set(maxOf(until, System.currentTimeMillis()) + extraMs)
        scheduleTimer()
    }

    fun endTimedNow() {
        if (appPrefs.timedUntil.value == 0L) return
        appPrefs.timedUntil.set(System.currentTimeMillis())
        checkTimer()
    }

    // Dokładne odliczanie do końca (a minutowy zegar i start aplikacji to zabezpieczenie, gdy proces zginął).
    private fun scheduleTimer() {
        timerJob?.cancel()
        val left = appPrefs.timedUntil.value - System.currentTimeMillis()
        if (appPrefs.timedUntil.value == 0L) return
        timerJob = viewModelScope.launch {
            delay(left.coerceAtLeast(0))
            checkTimer()
        }
    }

    private fun checkTimer() {
        val until = appPrefs.timedUntil.value
        if (until == 0L || System.currentTimeMillis() < until) return
        if (modes.value.isEmpty()) return // baza jeszcze się wczytuje (start aplikacji) — sprawdzimy za minutę
        val endedId = activeMode.value?.id
        val target = modes.value.firstOrNull { it.id == appPrefs.timedReturnTo.value }
            ?: modes.value.firstOrNull { it.id == appPrefs.homeModeId.value }
        appPrefs.timedUntil.set(0)
        if (target == null || target.id == endedId) return
        viewModelScope.launch {
            ModeActivation.activate(getApplication(), target, manual = false)
            _autoNotice.value = AutoSwitchNotice(target.id, endedId, "timer", reason = "koniec czasu")
            suggestionRefresh.value++
        }
    }

    private fun formatTime(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    // --- Tapety i wygląd widżetów ---

    private val wallpapers = WallpaperStore(application)
    private val _wallpaperVersion = MutableStateFlow(0) // zmiana = ekran odczytuje tapety na nowo
    val wallpaperVersion: StateFlow<Int> = _wallpaperVersion.asStateFlow()

    fun wallpaperPath(modeId: Long?): String? = wallpapers.pathFor(modeId)
    fun wallpaperOnLock(modeId: Long?): Boolean = wallpapers.lockScreenFor(modeId)

    // modeId null = tapeta domyślna (dla trybów bez własnej).
    fun setWallpaper(modeId: Long?, uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching { wallpapers.set(modeId, uri) }.isSuccess
            if (!ok) {
                Toast.makeText(getApplication(), "Nie udało się wczytać obrazka", Toast.LENGTH_SHORT).show()
                return@launch
            }
            appPrefs.showWallpaper.set(true) // bez tego tapety nie widać pod kartą
            _wallpaperVersion.value++
            activeMode.value?.let { wallpapers.applyFor(it.id) }
            _wallpaperCrop.value = WallpaperCropRequest(modeId) // od razu okno "Dopasuj" (jak systemowy wybór tapety)
        }
    }

    // --- Kadrowanie tapety (powiększenie i przesunięcie) ---
    private val _wallpaperCrop = MutableStateFlow<WallpaperCropRequest?>(null)
    val wallpaperCrop: StateFlow<WallpaperCropRequest?> = _wallpaperCrop.asStateFlow()

    fun openWallpaperCrop(modeId: Long?) {
        _wallpaperCrop.value = WallpaperCropRequest(modeId)
    }

    fun closeWallpaperCrop() {
        _wallpaperCrop.value = null
    }

    fun wallpaperCropOf(modeId: Long?): android.graphics.RectF? = wallpapers.cropFor(modeId)

    suspend fun wallpaperPreview(modeId: Long?): android.graphics.Bitmap? = wallpapers.preview(modeId)

    fun saveWallpaperCrop(modeId: Long?, crop: android.graphics.RectF?) {
        _wallpaperCrop.value = null
        wallpapers.setCrop(modeId, crop)
        _wallpaperVersion.value++
        activeMode.value?.let { mode -> viewModelScope.launch { wallpapers.applyFor(mode.id) } }
    }

    fun clearWallpaper(modeId: Long?) {
        viewModelScope.launch {
            wallpapers.clear(modeId)
            _wallpaperVersion.value++
            // Jeśli został inny obrazek (np. domyślny dla trybu bez własnego), od razu go ustawiamy.
            activeMode.value?.let { mode -> wallpapers.applyFor(mode.id) }
            Toast.makeText(getApplication(), "Usunięto tapetę", Toast.LENGTH_SHORT).show()
        }
    }

    fun setWallpaperOnLock(modeId: Long?, on: Boolean) {
        wallpapers.setLockScreen(modeId, on)
        _wallpaperVersion.value++
        activeMode.value?.let { mode -> viewModelScope.launch { wallpapers.applyFor(mode.id) } }
    }

    // --- Nasłuch ładowarki i słuchawek: reguły reagują od razu, a nie dopiero po minucie ---

    private val powerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            suggestionRefresh.value++
        }
    }
    private val audioCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) {
            suggestionRefresh.value++
        }
        override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>) {
            suggestionRefresh.value++
        }
    }

    override fun onCleared() {
        super.onCleared()
        val app = getApplication<Application>()
        runCatching { app.unregisterReceiver(powerReceiver) }
        quickToggles.close()
        repository.close()
        runCatching { app.getSystemService(android.media.AudioManager::class.java).unregisterAudioDeviceCallback(audioCallback) }
    }

    init {
        CardGrid.appSize = appPrefs.appIconCells.value // rozmiar ikon z ustawień, zanim cokolwiek się ułoży
        // Rozmiar ikon aktywnej karty: własny z ustawień trybu albo globalny (logika siatki liczy nowe ikony w tym rozmiarze).
        viewModelScope.launch {
            combine(activeMode, modeLayouts, appPrefs.appIconCells.flow) { mode, all, global ->
                mode?.let { all[it.id]?.iconCells } ?: global
            }.collect { CardGrid.appSize = it }
        }
        // Inny tryb = zaczynamy od jego pierwszej strony.
        viewModelScope.launch {
            activeMode.map { it?.id }.distinctUntilChanged().collect { setPage(0) }
        }
        // Ładowarka i poziom baterii: komunikaty systemu (ContextCompat dodaje flagę wymaganą od Androida 14).
        val app = getApplication<Application>()
        androidx.core.content.ContextCompat.registerReceiver(
            app,
            powerReceiver,
            android.content.IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        runCatching {
            app.getSystemService(android.media.AudioManager::class.java)
                .registerAudioDeviceCallback(audioCallback, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        // Tryb na czas: sprawdzamy przy starcie (mógł minąć, gdy launcher był zamknięty) i co minutę.
        viewModelScope.launch {
            minuteTicker.collect { checkTimer() }
        }
        scheduleTimer()

        // Automat słucha sugestii przez cały czas życia launchera.
        // Słuchamy też samych ustawień: włączenie automatu przy wiszącym banerze ma zadziałać od razu,
        // a nie dopiero przy następnej zmianie sugestii (StateFlow nie wysyła drugi raz tej samej wartości).
        viewModelScope.launch {
            combine(suggestion, appPrefs.autoSwitch.flow, appPrefs.alwaysAskModes.flow) { current, _, _ -> current }
                .collect { maybeAutoSwitch(it) }
        }
        // Skróty trybów aktualizują się same przy każdej zmianie trybów (nazwa, ikona, nowy tryb).
        viewModelScope.launch {
            // Pomijamy zmiany samego lastActiveAt (każde przełączenie) — system limituje częstotliwość publikowania skrótów.
            modes.distinctUntilChangedBy { list -> list.map { it.copy(lastActiveAt = 0) } }
                .collect { list -> if (appPrefs.modeShortcuts.value && list.isNotEmpty()) ModeShortcuts.publish(getApplication(), list) }
        }
    }

    companion object {
        const val COLUMNS = 4 // kolumny w szufladzie aplikacji
        private const val MANUAL_GRACE_MS = 30 * 60_000L
        private const val STABLE_MS = 60_000L
    }
}

// Prośba o okno kadrowania tapety. Klasa zamiast samego Long?, bo null znaczy tu "tapeta domyślna", a nie "brak prośby".
data class WallpaperCropRequest(val modeId: Long?)
