# Context Launcher

**Launcher na Androida, który zmienia się razem z sytuacją.** Zamiast przewijanych stron ekranu głównego masz *tryby* — Praca, Dom, Podróż, Studia, Trening albo własne. Każdy tryb ma swoją kartę z widżetami i aplikacjami, swoje „Pod ręką” (bilety, faktury, notatki), swoje ustawienia telefonu, tapetę i blokady aplikacji. Launcher sam podpowiada — albo sam włącza — tryb, który pasuje do chwili.

*An Android launcher built around context modes instead of home-screen pages. Kotlin + Jetpack Compose, learning & portfolio project.*

---

## Najważniejsze funkcje

**Tryby i karta trybu**
- Swobodny układ na siatce 8×12: przeciąganie, zmiana rozmiaru, usuwanie; przeciąganie ikon z szuflady prosto na kartę i do folderów.
- Widżety systemowe (AppWidgetHost) i 14 własnych: *W skrócie* (godzina, data, wydarzenie, pogoda, budzik — każda część otwiera inną aplikację), Dziś, Odliczanie, Lista, Szybkie przełączniki, Ulubione kontakty, Zegar, Pogoda (Open-Meteo), Dwa zegary, Notatka, Pod ręką, Folder, Tarcza trybów, Naklejka z akcją po dotknięciu.
- Szablony trybów i krótki kreator pierwszego uruchomienia.

**Kontekst**
- Reguły: pora dnia, kalendarz (słowo kluczowe), Bluetooth, Wi-Fi, miejsce, ładowanie, słuchawki, niska bateria.
- Podpowiedź w nagłówku (✓ / ✕) albo automatyczne przełączanie z zabezpieczeniami: sugestia musi utrzymać się minutę, ręczny wybór jest szanowany przez 30 min, wyjątek „zawsze pytaj”, cofnięcie jednym dotknięciem.
- Tryb na czas („Podróż na 2 godziny”), po którym launcher sam wraca.

**Ustawienia trybu**
- Motyw (4 schematy × jasny/ciemny, kolor główny), ikona, tapeta (też na ekranie blokady).
- Ustawienia telefonu: Nie przeszkadzać, tryb dzwonka, głośności, jasność, obrót, wygaszanie; przypomnienia o Wi-Fi/BT/NFC/danych, których Android nie pozwala zmienić aplikacjom.
- Blokowanie i ukrywanie aplikacji per tryb (miękka blokada z 5 s na namysł), zaznaczanie grupami (społecznościowe, gry, wideo…), szablon blokad dla nowych trybów.

**Reszta**
- „Pod ręką”: pliki, linki i notatki przypięte do trybu, także z systemowego „Udostępnij”.
- Kafelek w szybkich ustawieniach, skróty „Włącz tryb …” (np. dla procedur Samsunga), kropki powiadomień.
- Eksport i import całej konfiguracji do JSON, obsługa leworęcznych.

## Technologie

| Obszar | Wybór |
|---|---|
| Język / UI | Kotlin 2.2, Jetpack Compose (Material 3), BOM 2026.02 |
| Architektura | MVVM — jeden `LauncherViewModel`, `StateFlow` + `combine` / `flatMapLatest` |
| Dane | Room (SQLite, 10 wersji schematu z migracjami), SharedPreferences |
| Build | AGP 9.2, KSP, minSdk 29, targetSdk 36 |
| Android | LauncherApps, AppWidgetHost, CalendarContract, ContactsContract, NotificationListenerService, TileService, ShortcutManager, WallpaperManager, CameraManager (latarka) |
| Testy | JUnit — czysta logika bez Androida |

## Struktura

```
app/src/main/java/pl/rafal/contextlauncher/
├── data/       repozytoria, encje i DAO (Room), szablony, kopia zapasowa, ustawienia
├── layout/     CardGrid — geometria siatki karty (czysty Kotlin, testowany)
├── suggest/    SuggestionEngine — reguły trybów (czysty Kotlin, testowany)
├── system/     styk z systemem: sygnały, ustawienia telefonu, przełączniki, powiadomienia
└── ui/         ekrany i komponenty Compose, widżety, motywy
```

Logika decyzyjna (siatka karty, silnik sugestii, dopasowanie aplikacji do szablonów) nie zależy od Androida, więc jest testowana zwykłym JUnitem:

```
./gradlew testDebugUnitTest
```

## Uruchomienie

1. Otwórz projekt w aktualnym Android Studio i uruchom na telefonie lub emulatorze z Androidem 10+.
2. Po instalacji naciśnij Home i wybierz **Context Launcher** jako domyślny ekran główny (albo: Ustawienia → Aplikacje → Domyślne → Ekran główny).
3. Kreator zaproponuje tryby z szablonów. Uprawnienia launcher prosi dopiero wtedy, gdy są potrzebne; ich stan widać w Ustawieniach → Uprawnienia.

## Uprawnienia i prywatność

Wszystko działa lokalnie, bez konta i bez serwera. Jedyne połączenie z siecią to prognoza pogody z Open-Meteo (przybliżone współrzędne).

| Uprawnienie | Po co |
|---|---|
| Kalendarz | reguły kalendarza, widżety Dziś i W skrócie |
| Lokalizacja | reguły Wi-Fi i miejsca, pogoda |
| Urządzenia w pobliżu | reguły Bluetooth |
| Kontakty | widżet Ulubione kontakty |
| Nie przeszkadzać, modyfikowanie ustawień | ustawienia telefonu w trybie, przełączniki |
| Dostęp do powiadomień | tylko kropki na ikonach — treści nie są czytane |

## Ograniczenia platformy

- Wi-Fi, Bluetooth, NFC, danych komórkowych i oszczędzania baterii aplikacja nie może przełączyć sama — launcher otwiera panel systemowy.
- Blokada aplikacji działa dla uruchomień przez launcher; pełna blokada wymagałaby usługi ułatwień dostępu.
- Brak bezpośredniej integracji z Trybami i procedurami One UI — zamiast tego skróty „Włącz tryb …”, które procedury mogą wywołać.

## O projekcie

Projekt do nauki Androida i do portfolio, rozwijany etapami od makiety i wywiadu z samym sobą jako użytkownikiem. Komentarze w kodzie porównują konstrukcje Kotlina do C#/.NET (`data class` ≈ `record`, `StateFlow` ≈ `INotifyPropertyChanged`, Room ≈ Entity Framework).
