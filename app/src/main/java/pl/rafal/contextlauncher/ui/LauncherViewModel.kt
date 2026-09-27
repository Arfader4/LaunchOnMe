package pl.rafal.contextlauncher.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val repository = AppRepository(application) {
        appsDirty = true
        refresh()
    }
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
            .combine(allApps) { items, apps ->
                // mapNotNull pomija elementy, których nie da się pokazać (np. odinstalowana aplikacja).
                items.mapNotNull { item ->
                    // when ≈ switch z wyrażeniem (C# 8 switch expression).
                    when (item.type) {
                        CardItemEntity.TYPE_APP ->
                            apps.firstOrNull { it.matches(item) }?.let { CardApp(item, it) }
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
    )

    // Co minutę i po każdym powrocie na ekran (np. po ustawieniu budzika) czytamy na nowo.
    val glance: StateFlow<GlanceInfo> =
        combine(minuteTicker, suggestionRefresh) { now, _ -> now }
            .mapLatest { now ->
                val alarm = getApplication<Application>().getSystemService(android.app.AlarmManager::class.java)
                val (next, agenda) = calendar.glance(now) // jedno zapytanie do kalendarza zamiast dwóch
                GlanceInfo(
                    event = next,
                    alarmAt = alarm?.nextAlarmClock?.triggerTime,
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

    // --- Wygląd ---

    val themeMode: StateFlow<ThemeMode> = themePrefs.themeMode
    val defaultPalette: StateFlow<Palette> = themePrefs.defaultPalette

    fun setThemeMode(mode: ThemeMode) = themePrefs.setThemeMode(mode)

    fun setDefaultPalette(palette: Palette) = themePrefs.setDefaultPalette(palette)

    fun updateModeAppearance(mode: ModeEntity, icon: String?, color: Long, palette: Palette?, accent: Long?) {
        viewModelScope.launch {
            modeDao.updateAppearance(mode.id, icon, color, palette?.name, accent)
            ModeTileService.requestUpdate(getApplication()) // kafelek pokazuje ikonę aktywnego trybu
        }
    }

    // --- Zarządzanie trybami ---

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
            cardItemDao.getForMode(mode.id).forEach { item ->
                item.appWidgetId?.let(widgets::deleteId)
                if (item.widgetKind == CustomWidgetKind.STICKER.name) {
                    runCatching { JSONObject(item.config ?: "{}").optString("file") }.getOrNull()
                        ?.takeIf { it.isNotBlank() }?.let(StickerStore::delete)
                }
            }
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
        viewModelScope.launch {
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
            if (toAdd.isNotEmpty()) folderDao.insertApps(toAdd)
        }
    }

    fun moveAppToFolder(app: FolderApp, folderId: Long) {
        viewModelScope.launch { folderDao.moveApp(app.entry.id, folderId) }
    }

    fun removeAppFromFolder(app: FolderApp) {
        viewModelScope.launch { folderDao.deleteApp(app.entry.id) }
    }

    // --- Własne widżety ---

    fun addCustomWidget(kind: CustomWidgetKind, config: String = "{}") {
        val mode = activeMode.value ?: return
        viewModelScope.launch {
            if (!placeCustomWidget(cardItemDao, mode.id, kind, config)) {
                Toast.makeText(getApplication(), "Brak miejsca na karcie na ten widżet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Naklejka: kopia obrazka do pamięci launchera, potem widżet na karcie.
    fun addSticker(uri: Uri) {
        val mode = activeMode.value ?: return
        viewModelScope.launch {
            val result = runCatching {
                val path = StickerStore.import(getApplication(), uri)
                val placed = placeCustomWidget(
                    cardItemDao, mode.id, CustomWidgetKind.STICKER, JSONObject().put("file", path).toString(),
                )
                if (!placed) StickerStore.delete(path) // nie ma miejsca = nie trzymamy niepotrzebnej kopii
                placed
            }
            val message = when {
                result.isFailure -> "Nie udało się dodać naklejki"
                result.getOrNull() == false -> "Brak miejsca na karcie na naklejkę"
                else -> null
            }
            message?.let { Toast.makeText(getApplication(), it, Toast.LENGTH_SHORT).show() }
        }
    }

    fun updateWidgetConfig(widget: CardCustomWidget, config: String) {
        viewModelScope.launch { cardItemDao.updateConfig(widget.item.id, config) }
    }

    // --- Pod ręką ---

    // Elementy "Pod ręką" aktywnego trybu (aktywne i zarchiwizowane; UI je rozdziela).
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
        viewModelScope.launch {
            val existing = cardItemDao.getForMode(mode.id)
            val (w, h) = widgets.cellSize(info) // dekonstrukcja pary, jak var (w, h) = ... w C#
            val spot = CardGrid.findFreeSpot(existing.map { it.toRect() }, w, h)
            if (spot == null) {
                widgets.deleteId(appWidgetId)
                Toast.makeText(getApplication(), "Brak miejsca na karcie na ten widżet", Toast.LENGTH_SHORT).show()
                return@launch
            }
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
                ),
            )
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
        // launch ≈ odpalenie Task bez czekania; viewModelScope anuluje go, gdy ViewModel zniknie.
        if (appsDirty) {
            appsDirty = false
            viewModelScope.launch {
                runCatching { repository.loadApps() }
                    .onSuccess { allApps.value = it }
                    .onFailure { appsDirty = true } // spróbujemy przy następnym powrocie
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
        viewModelScope.launch {
            val existing = cardItemDao.getForMode(mode.id)
            if (existing.any { app.matches(it) }) return@launch // już jest na karcie

            // Szukamy wolnego miejsca 2×2 na siatce 8×12. Gdy karta pełna, nic nie dodajemy.
            val spot = CardGrid.findFreeSpot(
                others = existing.map { it.toRect() },
                w = CardGrid.APP_SIZE,
                h = CardGrid.APP_SIZE,
            ) ?: return@launch

            cardItemDao.insert(
                CardItemEntity(
                    modeId = mode.id,
                    packageName = app.component.packageName,
                    className = app.component.className,
                    userSerial = app.userSerial,
                    x = spot.x,
                    y = spot.y,
                    w = spot.w,
                    h = spot.h,
                ),
            )
        }
    }

    // Upuszczenie ikony z szuflady w konkretne miejsce karty. Zajęte → najbliższe wolne (findFreeSpot).
    fun addToActiveModeAt(app: AppInfo, x: Int, y: Int) {
        val mode = activeMode.value ?: return
        viewModelScope.launch {
            val existing = cardItemDao.getForMode(mode.id)
            if (existing.any { app.matches(it) }) {
                Toast.makeText(getApplication(), "${app.label} już jest na karcie", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val others = existing.map { it.toRect() }
            val wanted = GridRect(x, y, CardGrid.APP_SIZE, CardGrid.APP_SIZE)
            val spot = wanted.takeIf { CardGrid.canPlace(it, others) }
                ?: CardGrid.findFreeSpot(others, CardGrid.APP_SIZE, CardGrid.APP_SIZE)
            if (spot == null) {
                Toast.makeText(getApplication(), "Karta jest pełna", Toast.LENGTH_SHORT).show()
                return@launch
            }
            cardItemDao.insert(
                CardItemEntity(
                    modeId = mode.id,
                    packageName = app.component.packageName,
                    className = app.component.className,
                    userSerial = app.userSerial,
                    x = spot.x, y = spot.y, w = spot.w, h = spot.h,
                ),
            )
        }
    }

    // Ikona z karty wrzucona na widżet folderu: trafia do folderu i znika z karty.
    fun moveCardAppIntoFolder(element: CardApp, folderWidget: CardCustomWidget) {
        val folderId = folderWidget.config.optLong("folderId", -1).takeIf { it > 0 } ?: return
        viewModelScope.launch {
            addAppsToFolder(folderId, listOf(element.app))
            cardItemDao.delete(element.item.id)
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
            cardItemDao.delete(element.item.id)
            // "is" sprawdza typ i od razu rzutuje (smart cast), jak "if (element is CardWidget w)" w C#.
            if (element is CardWidget) widgets.deleteId(element.appWidgetId)
            // Naklejka ma kopię obrazka w pamięci launchera — sprzątamy ją razem z widżetem.
            if (element is CardCustomWidget && element.kind == CustomWidgetKind.STICKER) {
                element.config.optString("file").takeIf { it.isNotBlank() }?.let(StickerStore::delete)
            }
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

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching { Backup.export(getApplication(), uri) }.isSuccess
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
        }
    }

    fun clearWallpaper(modeId: Long?) {
        wallpapers.clear(modeId)
        _wallpaperVersion.value++
        activeMode.value?.let { mode -> viewModelScope.launch { wallpapers.applyFor(mode.id) } }
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
