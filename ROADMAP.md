# LaunchOnMe — plan prac (runda uwag z 28.09)

Każdy etap to jedna paczka zmian, którą da się zbudować i przetestować osobno. Kolejny etap można zacząć
w nowej sesji: wystarczy napisać „robimy etap N z ROADMAP.md”. Po skończeniu etapu zaznaczamy go [x].

## Wersje (od 05.10)
Numer X.Y.Z: Y = runda zmian / duży temat, Z = poprawka (hotfix). Numer widać w Ustawieniach,
w gicie jest tag `vX.Y.Z` (Android Studio: przy pushu zaznacz „Push tags”).
| Wersja | Co | Paczek |
|---|---|---|
| 1.0 | V1 — pierwsza wersja (`master`) | 1 |
| 1.1 | Etapy 1–11, runda 29.09, StickOnMe S0–S4, porządki X/Y/Z (animacje) | 21 |
| 1.2 | Runda uwag 04.10: paczki 1–3 (OnHand, klawisz ON, pasek czuwania, tłumaczenie EN/PL) | 3 |
| 1.2.1 | Hotfix: łuk trybów i lista trybów na przygaszonym całym ekranie | 1 |
| 1.3.0 | StickOnMe: wydajność (tablica, biblioteka, edytor) — gałąź `feature/stickonme-perf` | 3 |
| 1.4.0 | OnHand jako osobna aplikacja notatek (`feature/onhand`): On1–On4 + nowa ikona | 5 |
| 1.5.0 | OnThemes — aplikacja motywów (`feature/onthemes`): T1–T5 | 5 |
| 1.6.0 | Scalenie 1.3 + 1.4 + 1.5 do `dev` (StickOnMe wydajność, OnHand, OnThemes w jednym buildzie) | 1 |
| 1.6.1 | Runda 18.10, paczka 1: OnHand przy wyjściu, rozmycie pod menu, tapeta po cudzej zmianie, „lepkie” tryby | 1 |
| 1.6.2 | Runda 18.10, paczka 2a: czas oczekiwania automatu (0–60 s) i odliczanie (brak / pasek / kółko) | 1 |
| 1.6.3 | Runda 18.10, paczka 2b: OnModes — wbudowane tryby „… On Mode”, tytuły OnModes | 1 |
| 1.7.0 | Szuflada aplikacji: dwa widoki, sortowanie (A–Z / najczęściej / własna), sekcja „Nowe”, ustawienia per OnMode | 1 |
| 1.7.1 | Szuflada: sortowanie i własna kolejność w folderach, „najczęściej używane” z danych systemu | 1 |
| 1.8.0 | StickOnMe: dopasowanie obrazu w kształcie — przesuwanie, powiększanie i obrót pod maską kształtu | 1 |
| 1.8.1 | StickOnMe: czytelny tryb ciemny — pełna paleta ról (przyciski, zaznaczone chipy, obramowania) | 1 |
| 1.8.2 | StickOnMe: biały tekst domyślnie (LocalContentColor), „Zapisz” zawsze widoczny, auto-wycięcie tylko na żądanie („Wytnij obiekt” + ✓ / Cofnij), limit 20 s ML Kit | 1 |
| 1.9.0 | StickOnMe wydzielone do osobnej aplikacji (moduł `:stickonme`, StickOnMe 1.0.0); launcher woła je intencjami, przeniesienie biblioteki w Ustawieniach | 1 |
| SOM 1.1.0 | StickOnMe: nowy edytor naklejki wg makiety (pasek narzędzi z ikonami, bez suwaków, ✓ Gotowe, Ponów, porównanie z oryginałem) | 1 |
| 1.9.1 | Hotfix automatu: „Włączono …” znika po 15 s i ustępuje nowej sugestii; automat nie kończy trybów sam (powrót do Start = tylko propozycja) | 1 |
| SOM 1.2.0 | StickOnMe: edytor naklejki jak w telefonie — narzędzia z ✓ Zastosuj / ✕ Anuluj, kadr z ramką i rogami + kształt w jednym, próg maski ML Kit (koniec „prostokątów”), krawędź względem rozmiaru zdjęcia, obrót na żywo | 1 |
Razem od V1: 51 paczek. Kolejne duże tematy = 1.7, 1.8…; poprawki do nich = 1.7.1, 1.7.2…

## Etap 1 — szybkie poprawki i ikona aplikacji
- [x] Ikony aplikacji i folderów na karcie wyrównane do tej samej linii (środek komórki).
- [x] Uchwyt zmiany rozmiaru: pogrubiony narożnik z podwójną linią zamiast kółka; mniejszy ✕ na małych widżetach.
- [x] Pasek „Usuń z karty / Odinstaluj” niżej (nie pod notchem), półprzezroczysty, nad górnym rzędem karty.
- [x] Usuwanie domyślnej tapety w Ustawieniach głównych.
- [x] Szuflada: przesunięcie w lewo/prawo przełącza „Wszystkie” ↔ „Foldery”.
- [x] Szuflada: skrót do systemowych ustawień „Aplikacje”.
- [x] Pusty folder na karcie z listy widżetów.
- [x] Oficjalna ikona aplikacji (adaptacyjna, z pliku ikona.svg).

## Etap 2 — ikony i kolory
- [x] Więcej ikon trybów (nowe motywy, osobny zestaw od folderów).
- [x] Ikony folderów: pieniądze ($, €, zł, monety, banknot) i kilka innych kategorii.
- [x] Ikona tekstowa: własny napis do 3 znaków (zamiast siatki liter i cyfr).
- [x] Więcej kolorów + dowolny kolor z palety (HSV) wszędzie, gdzie wybiera się kolor.
- [x] Kreator motywów (tło, powierzchnie, akcent, tekst → własny schemat).
- [x] (dodatkowo) Naklejka: przytrzymanie = tylko edycja układu; w edycji ⚙ otwiera ustawienia widżetu.
- [x] (dodatkowo) Akcje naklejki: kilka po kolei, skrót aplikacji, akcje systemowe; wybór aplikacji w siatce.

## Etap 3 — wygląd widżetów i układ per tryb
- [x] Kolor tła i przezroczystość ustawiane dla każdego widżetu osobno.
- [x] Margines/odstęp elementów karty w ustawieniach trybu.
- [x] Rozmiar ikon aplikacji (1×1 / 2×2) w ustawieniach trybu (zamiast tylko globalnie).
- [x] (dodatkowo) Drgnięcie (haptyka) przy złapaniu elementu: w oknie „Ułóż” folderu, na karcie w edycji, przy wyciąganiu z szuflady.
- [ ] (później) Tło/krycie dla widżetów systemowych (innych aplikacji) — mają własne tło, do sprawdzenia.
- [x] Układ karty per tryb w kopii zapasowej (od paczki Y).

## Etap 4 — foldery
- [x] Otwieranie folderu bliżej kciuka: domyślnie u dołu ekranu, przełącznik w Ustawieniach (u dołu / na środku).
- [x] Folder w folderze na karcie (dowolnie głęboko, ‹ wstecz, przenoszenie aplikacji między poziomami).
- [x] „Zapisz w szufladzie” (folder z karty → Foldery), „Kopiuj do trybu…”, w szufladzie „Kopia na kartę”.
- [x] Swobodne rozmieszczenie ikon w folderze — decyzja: NIE teraz. Powód: folder ma sortowanie (A–Z, częstość),
      miniaturę z pierwszych ikon i skaluje się do rozmiaru; wolne pozycje kłóciłyby się z tym wszystkim.
      Zostaje „Ułóż” (przeciąganie). Ewentualny kompromis na później: „puste miejsce” jako element folderu.

## Etap 5 — strony karty
- [x] Kilka stron w karcie trybu (przesuwanie w bok), wskaźnik stron (kropki nad dolnym paskiem). Home = pierwsza strona.
- [x] Globalny limit liczby stron (Ustawienia), domyślnie 3. W edycji jedna pusta strona „na zapas”.
- [x] Przenoszenie elementu na inną stronę w edycji (upuszczenie przy lewej/prawej krawędzi, podświetlony pas).
- [x] Puste strony w środku znikają po zakończeniu edycji (numeracja bez dziur). Baza v12 (`card_items.page`), strona w kopii zapasowej.
- [x] (dodatkowo) Folder na karcie: wyrównanie ikon (lewo / środek / prawo) i rzędy od góry / od dołu — menu ⋮ folderu,
      działa w oknie folderu i w dużym widżecie folderu.
- [x] Przewijanie strony, gdy przeciągany element dłużej „wisi” przy krawędzi — zrobione w etapie 9.

## Między etapami 5 i 6 (runda 29.09)
- [x] Tarcza trybów jak „fidget spinner”: kręcenie z rozpędem, zapadka (drgnięcia), tryb pod ▲ włącza się po zatrzymaniu.
- [x] Ikony trybów zależne od motywu: w ciemnym zawsze jasny symbol, w jasnym zawsze ciemny; tło znaczka dopasowuje
      jasność (także kolory własne). Kółka wyboru koloru trybu pokazują kolor tak, jak wyjdzie na znaczku.
- [x] Dopasowanie tapety (powiększenie i przesunięcie w ramce ekranu) — po wybraniu obrazu i przyciskiem „Dopasuj”.

## Etap 6 — stos widżetów
- [x] Stos widżetów: w edycji upuść widżet środkiem na inny widżet (także systemowy) → stos w miejscu celu;
      kolejne widżety można dorzucać na istniejący stos. Przewijanie palcem w górę/dół (w kółko), kropki z prawej
      (dotknięcie = następny). ⚙ na stosie: kolejność, który na wierzchu, ustawienia widżetu, „Wyjmij”, „Rozdziel stos”.
      Widżety w stosie to zwykłe wiersze karty ze stroną -1 (bez migracji bazy); stos w kopii zapasowej.
- [ ] (później) Automatyczne przełączanie stosu (np. rano pogoda, wieczorem lista) — jak Smart Stack.

## Etap 7 — kreator trybu i samouczek
- [x] Kreator nowego trybu („+ Nowy tryb”): cel (11 do wyboru, m.in. auto, wieczór, rodzina, zakupy, pusty) →
      ile na karcie (minimalny / zrównoważony / pełny) + rozmiar ikon + podpowiadanie → nazwa, ikona, kolor →
      podgląd widżetów i aplikacji (dotknięcie pomija aplikację) → tryb od razu aktywny.
- [x] Samouczek gestów (8 kroków, animowana dłoń): po pierwszym kreatorze; ponownie z Ustawień.

---

# Runda uwag z 30.09 — plan

## Etap 8 — szybkie poprawki
- [x] Menu ⋮ folderu za długie → w menu zostaje ok. 5 pozycji (Dodaj aplikacje, Dodaj skrót, Nowy podfolder, Ułóż, Opcje…).
      „Opcje folderu” = arkusz z sekcjami: Wygląd (nazwa, symbol, kolor, miniatura 2×2/3×3), Układ (kolejność,
      wyrównanie ←/↔/→, rzędy ↑/↓ jako przełączniki-chipy zamiast osobnych pozycji), Udostępnij (szuflada, inny tryb),
      Usuń / Rozwiąż.
- [x] Wybór widżetów: grupa „LaunchOnMe” z prawdziwą ikoną aplikacji zamiast litery „L”.
- [x] Brak miejsca na stronie → element trafia na następną stronę z miejscem (albo nową, w granicach limitu)
      i ekran tam przechodzi; komunikat tylko przy osiągniętym limicie stron.
- [x] Pełna strona w edycji: przełączanie stron przez kropki (dotknięcie / przesunięcie po pasku kropek),
      pusta strona „na zapas” ma w edycji znak +.
- [x] Subtelniejsza ramka zmiany rozmiaru: cieńsza linia, łagodniejsze zaokrąglenie, mniejsze krycie.
- [x] Zdjęcia obrócone o 90° (naklejki, tapety): odczyt przez ImageDecoder, który uwzględnia orientację z EXIF.

## Etap 9 — przeciąganie między stronami
- [x] Przeciągany element „jedzie” z palcem na kolejne strony (strona źródłowa zostaje narysowana na wierzchu).
- [x] Przytrzymanie przy krawędzi ok. 0,6 s → strona przewija się, a element dalej jest pod palcem;
      za ostatnią stroną powstaje nowa (w limicie). Węższy pas krawędzi.
- Uwaga: największa przebudowa gestów karty (dziś przeciąganie żyje w jednej stronie) — osobna sesja.

## Etap 10 — stos: przewijanie treści vs przełączanie widżetów
- Odrzucone: „najpierw treść” (lista, która nie ma końca — np. kalendarz z powtarzanymi wydarzeniami —
  nigdy nie oddałaby gestu stosowi).
- [x] Uchwyt: szerszy pasek z kropkami przy prawej krawędzi stosu — przesunięcie po nim zawsze przełącza widżet.
- [x] Sam wybór zachowania reszty powierzchni stosu (automatycznie, do zmiany w oknie „Stos”):
      stos bez przewijanych widżetów (zegar, pogoda, naklejka) → przesunięcie w dowolnym miejscu przełącza;
      stos z przewijanym widżetem (lista, poczta, kalendarz, widżety systemowe z listą) → treść przewija się,
      a przełącza tylko uchwyt.
- [x] Dwa palce w pionie w dowolnym miejscu stosu = przełączenie (skrót).

## Etap 11 — Klawisz ON (przełącznik trybów): menu w ćwierć okręgu
- [x] Przytrzymanie Klawisza ON → wokół niego rozwija się łuk (ćwierć okręgu w stronę środka ekranu)
      ze znaczkami trybów. Palec nie odrywa się: przesuwasz po łuku, podświetla się tryb najbliżej palca
      (z drgnięciem), puszczenie = włączenie. Puszczenie na klawiszu / poza łukiem = anuluj.
      Krótkie dotknięcie działa jak dziś (lista trybów). Leworęczni: łuk lustrzany.
- [x] Wiele trybów (więcej niż 5): łuk przewija się, gdy palec stoi przy jego końcu.
- Potwierdzone 30.09: tak, dokładnie ten gest. Przy wielu trybach łuk przewija się, gdy palec dojedzie do jego końca.

## StickOnMe — studio naklejek (osobny tor, po etapach 8–11)
- Nazwa: StickOnMe. Logo: „ON” z logo LaunchOnMe w kolorowym gradiencie, z odklejanym rogiem naklejki
  (zawinięty róg z cieniem). Projekt ikony: Claude przygotuje wersję w SVG (adaptacyjna ikona), do akceptacji.
- „Tablica naklejek” (płótno kolaży/tapet): naklejki mogą wychodzić poza krawędź obszaru roboczego
  (przycinane dopiero przy eksporcie, poza obszarem widoczne półprzezroczyście); rozmiar tablicy do wyboru:
  ekran telefonu (tapeta), kwadrat 1:1, 4:5, 9:16, A-format do druku, własny (szer. × wys.).
Decyzja 30.09: w pakiecie z launcherem, ale jako osobny moduł Gradle (`:studio`)
z własną ikoną w szufladzie (activity-alias). Jedna instalacja, wspólne pliki naklejek bez ContentProvidera;
kod odseparowany, więc później da się go wydzielić jako osobną aplikację. Widżet naklejki tylko wstawia gotową
naklejkę (obrót, akcje po dotknięciu), a cała obróbka jest w Studiu.
- [x] S0 — Moduł `:studio` (biblioteka w tym samym APK), aktywność StickOnMe z własną ikoną w szufladzie
      (logo: „ON” w tęczowym gradiencie na naklejce z odklejonym rogiem), biblioteka naklejek (stickers/library).
      Launcher: dodając naklejkę wybierasz „StickOnMe” (gotowa albo nowa) albo „Zdjęcie z galerii”.
- [x] S1 (część 1) — Edytor: automatyczna maska ML Kit, pędzel Dodaj / Usuń z rozmiarem, dwa palce = zoom i przesuwanie,
      Cofnij, Auto, Całe zdjęcie; zakładka „Obrót i rozmiar”: obrót (suwak ±180°, ±90°), rozmiar 512/768/1024 px;
      zapis przycina naklejkę do widocznej części. Zdjęcia wczytywane z orientacją EXIF.
- [x] S1 (część 2) — Narzędzia „Kadr” i „Owal” (przeciągnięta ramka, reszta znika, Cofnij działa), zakładka
      „Wykończenie”: obrót, wygładzenie i zwężenie krawędzi maski (podgląd na żywo), rozmiar naklejki.
      „Edytuj w StickOnMe” w oknie naklejki na karcie: edytor startuje z gotową naklejką, wynik wraca na kartę,
      poprzednia wersja zostaje jako „oryginał” (Przywróć oryginał).
- [x] S2 — Warstwy na tablicy: teksty (czcionki systemowe, pogrubienie, kursywa, kolor, obrys z grubością,
      podkreślenie z grubością, cień, zakreślacz), emoji, ramki (kontur, cień, instax, polaroid, taśma),
      kolejność warstw, duplikowanie, „prosto”, Cofnij; eksport „Naklejka” spłaszcza warstwy w jedną naklejkę.
- [x] S3 — Tablice (kolaże i tapety): rozmiary gotowe (ekran, 1:1, 4:5, 9:16, A4, przezroczysta) i własny,
      tło: przezroczyste, kolor, gradient, zdjęcie z galerii; warstwy mogą wyjeżdżać poza krawędź (półprzezroczyste),
      przycinane przy eksporcie; jeden palec przesuwa, dwa palce skalują i obracają; zapis sam przy wyjściu,
      eksport do Galerii (Pictures/StickOnMe) albo jako naklejka.
- [x] S4 — „Z klawiatury”: okno emoji przyjmuje naklejki / GIF-y / Emoji Kitchen z klawiatury (contentReceiver),
      zapis do biblioteki i od razu jako warstwa. Brak systemowego API do paczek naklejek innych aplikacji —
      z czatów działa „Udostępnij → LaunchOnMe”.

## Porządki 30.09 (paczki X, Y) — przed testami
- [x] X/R1 — Okno naklejki na karcie tylko od karty: obrót, odbicie, akcja, „Edytuj w StickOnMe”, „Przywróć pierwszą wersję”.
      Wycinanie, kształt i ramka przeniesione do StickOnMe (zakładka „Wykończenie”: 9 kształtów, 8 ramek z kolorem,
      podgląd gotowej naklejki). Stare naklejki z kształtem/ramką z karty dalej tak wyglądają, a przy edycji
      te ustawienia przechodzą do edytora. Usunięte `StickerCutout.kt` i ML Kit z modułu `app` (został w `:studio`).
- [x] X/R2 — Dodawanie naklejki = od razu StickOnMe (bez osobnej ścieżki „Zdjęcie z galerii”).
- [x] X/R3 — Porządki w ROADMAP.
- [x] X/P1+D1 — Biblioteka StickOnMe: dotknięcie naklejki otwiera menu: Edytuj, Na nową tablicę, Udostępnij (FileProvider), Usuń.
- [x] X/P3 — Ramki na tablicy liczone raz (pamięć podręczna obrazu z ramką), nowe ramki: Znaczek, Klisza.
- [x] X/P4 — „Udostępnij → LaunchOnMe → Naklejka na kartę” otwiera edytor StickOnMe (gotowa przezroczysta naklejka
      bez automatycznego wycinania), wynik trafia na kartę wybranego trybu.
- [x] Y/P2 — Eksport: „Pełna kopia (.zip)” (JSON + naklejki z kart, tapety trybów z kadrem, biblioteka i tablice
      StickOnMe; ścieżki względne) albo „Same ustawienia (.json)”. Import rozpoznaje format sam. W kopii są też
      układy kart per tryb. Po imporcie nieużywane pliki naklejek są sprzątane.
- [x] Y/D2 — Tapeta trybu „Z tablicy StickOnMe”: studio otwiera Tablice, „✓ Na tapetę” oddaje obraz, dalej „Dopasuj”.
- [x] Z1 — Ruch: aplikacje „wyrastają” z ikony (albo miejsca dotknięcia), zmiana trybu = karta „oddycha”,
      szuflada na sprężynie z ikonami falą i płynnym przestawianiem przy wyszukiwaniu, folder wyrasta z ikony
      i się do niej kurczy, strony i kropki (pigułka) na sprężynie, ikony wciskają się pod palcem.
      Wspólne ustawienia ruchu: `ui/Motion.kt`.
- [x] Z2 — Edycja: podniesiony element (powiększenie, cień pod widżetem), odłożony „dopływa” od palca do kratki,
      wypychani sąsiedzi przesuwają się sprężyście. Stos: głębia (odjeżdżający widżet maleje i gaśnie).
      Łuk trybów rozkłada się jak wachlarz (znaczki wysuwają się po kolei). StickOnMe: przejścia ekranów
      (biblioteka ↔ edytor ↔ tablica), miniatury falą, zapisana naklejka „odkleja się”, cofanie z podglądem (Android 14+).

## Runda uwag z 04.10 (po kilku dniach używania)
Kolejność ustalona 04.10: paczka 1 → paczka 2 → tłumaczenie → duże tematy (każdy najlepiej w osobnym czacie, kolejno).

### Paczka 1 — szybkie poprawki
- [x] „Pod ręką” → **OnHand** (nazwa w całej aplikacji).
- [x] Miniatura zagnieżdżonego folderu w widżecie folderu (ikona, pasek, miniatura 2×2/3×3).
- [x] Okno notatki OnHand nie chowa się pod klawiaturą (własne okno z imePadding, treść przewijana).
- [x] Widżet folderu 1×n mieści jedną aplikację więcej; nowy układ n×1 (wąski i wysoki: znaczek + kolumna ikon).
- [x] Łuk trybów: przy >5 trybach widać przygaszony kawałek kolejnego znaczka i strzałki na końcach łuku.
- [x] Kolor ikony trybu: znaczek mniej przekształca kolor (kontrast 3:1 zamiast 5:1), własny kolor widać;
      w oknie „Własny kolor” podgląd znaczka.
- [x] Lista trybów (dotknięcie klawisza ON): przytrzymaj tryb i przesuń, żeby zmienić kolejność.

### Paczka 2 — klawisz ON i wyszukiwarka
- [x] Klawisz ON: superelipsa („squircle”) zamiast kwadratu, sprężyste powiększenie przy dotyku, fala w kolorze
      trybu po zmianie trybu. Lista trybów: osobne owalne klawisze z cieniem, pojawiające się falą; pod nimi
      mniejsze przyciski (+ Nowy tryb, OnHand, Ustawienia…).
- [x] Przytrzymanie klawisza: przygaszony ekran, duży znaczek i nazwa trybu pod palcem na środku.
- [x] Pasek wyszukiwania w trybie czuwania (`ui/SmartSearchBar.kt`): co 5 s kolejno ostatnio używane aplikacje
      (ostatnia godzina), nowe powiadomienia (po jednej aplikacji), najbliższe wydarzenie (do 3 h), budzik (do 12 h),
      słaba bateria. Propozycja / komunikat zmiany trybu jest przypięta w pasku (✓ / ✕) zamiast okienka nad menu.
      Lupa = wyszukiwarka z klawiaturą. Przewijanie tylko, gdy launcher jest widoczny.

### Paczka 3 — tłumaczenie
- [x] Wszystkie teksty do zasobów: `res/values` = angielski (domyślny), `res/values-pl` = polski — launcher ok. 1100,
      StickOnMe ok. 150 tekstów, też etykiety w manifeście. Daty i nazwy miast z pogody w języku telefonu.
      Kolejne języki = po jednym pliku `values-xx/strings.xml` na moduł.

### Hotfix 1.2.1 (05.10)
- [x] Lista trybów (dotknięcie klawisza ON): pod listą przygaszony cały ekran, owalne klawisze odcinają się od karty.
- [x] Łuk trybów (przytrzymanie): przygaszenie na cały ekran razem z paskami systemu — warstwa rysowana w głównym
      oknie (`ui/ScreenOverlay.kt`) zamiast dwóch okien Popup. Bez tła „ćwierćkola”, linii toru i kropki kierunku —
      wystarczą znaczki (wybrany powiększony z obwódką) i strzałki przy >5 trybach. Mniej kodu i rysowania.
- [x] Numeracja wersji (versionName / versionCode, tagi w gicie).

### Duże tematy (osobne czaty, kolejno)
- [ ] **1.4 — OnHand** (osobna aplikacja jak StickOnMe, gałąź `feature/onhand`) — pełne notatki: przyjmowanie z „Udostępnij” (Keep, Samsung Notes…), wysyłanie do nich, eksport do pliku.
      (Keep ma API tylko dla firmowych kont Workspace, Samsung Notes nie ma API — synchronizacji nie robimy.)
- [ ] **1.5 — OnThemes** (aplikacja motywów jak StickOnMe, gałąź `feature/onthemes`): High Contrast (czarne/białe tło ikon + kolorowy symbol), Luxury (złoto,
      srebro, miedź, brąz + butelkowa zieleń, purpura…, połysk metalu), Black & White, Vintage, Standard, Night,
      Własny (+ propozycje: Pastel, AMOLED). Uporządkowana lista kolorów i krótka paleta na motyw. Kolory systemu:
      Good Lock nie ma API — pośrednio przez tapetę (Android 12+/One UI biorą kolory z tapety).
- [x] **1.3 — StickOnMe: wydajność** (gałąź `feature/stickonme-perf`) przy wielu zdjęciach — 3 paczki:
      1) tablica: nieruszane warstwy wypalone w dwa obrazy (pod / nad ruszaną), tablica czytana dopiero przy rysowaniu,
      cache tekstu, obrazy i ramki wczytywane w tle; podgląd na kopiach ≤1024 px z limitem pamięci, eksport osobnym
      rendererem w pełnej jakości; rozmiar logiczny obrazu warstwy (max 1400 px) — skala nie zależy od kopii.
      2) biblioteka: miniatury w pamięci (LruCache) i na dysku (cache), max 3 dekodowania naraz, onTrimMemory.
      3) edytor: ML Kit na kopii ≤1024 px, wynik i maska liczone w tle; podgląd „Wykończenia” na kopii 800 px;
      zwalnianie bitmap (Cofnij, kroki pośrednie, eksport); szachownica jako wzór (shader).
- [ ] KeepMeOn (oszczędzanie energii): tryb włączany przy słabej baterii, blokada zmiany trybu (odblokowanie odciskiem),
      najważniejsze aplikacje, bez animacji/tapety/odświeżania widżetów, ciemny motyw; propozycja w samouczku.
      Systemowego oszczędzania baterii aplikacja sama nie włączy (wymaga uprawnienia nadanego przez adb).
- [ ] Optymalizacja baterii i procesora w całym launcherze.
- [ ] Blokada folderów w szufladzie odciskiem palca / PIN-em (systemowy BiometricPrompt).

## 1.4 — OnHand jako osobna aplikacja (gałąź `feature/onhand`, plan z 05.10)
Bez synchronizacji (Keep / Samsung Notes nie mają publicznego API) — zamiast niej „Udostępnij” w obie strony i eksport.
Nowy czat: „robimy paczkę OnN z ROADMAP.md” (gałąź `feature/onhand`).

**Podział (wzór: `:studio` / obiekt `StickOnMe`):**
- Moduł `:onhand` (biblioteka, pakiet `pl.rafal.onhand`), `:app` zależy od niego, nie odwrotnie. Własna aktywność
  z ikoną w szufladzie (activity-alias / `taskAffinity`), teksty przez `OnHandText`.
- Baza: osobna Room `onhand.db` w module — `notes` (tytuł, treść = zwykły tekst, createdAt, updatedAt, archivedAt)
  i `attachments` (noteId, ścieżka, MIME, nazwa); pliki w `filesDir/onhand/`.
- Komunikacja: obiekt `OnHand` z intencjami (`openIntent(noteId)`, `pickIntent()` → wynik `noteId`, `newIntent(text)`)
  + interfejs gospodarza `OnHandHost` (lista trybów, `pinToMode(noteId, modeId)`), który `:app` rejestruje
  w `LaunchOnMeApp` (jak `StudioText.init`) — moduł nie zna bazy launchera.
- Launcher: `pinned_items` dostaje rodzaj `ONHAND` (`uri` = noteId); wiersz pokazuje tytuł żywej notatki,
  dotknięcie otwiera ją w OnHand. Pliki i linki bez zmian.
- Decyzje 05.10: stare notatki (`KIND_NOTE`) przenosimy do OnHand (migracja, na karcie zostaje odnośnik `ONHAND`);
  „Udostępnij → LaunchOnMe → Przypnij do trybu” dla tekstu tworzy notatkę OnHand przypiętą do trybu;
  treść = zwykły tekst (checklisty/Markdown ewentualnie później); przegląd „jak kompilator” po każdej paczce.

**Ekrany:** lista (wyszukiwanie, zakładki Notatki / Archiwum, zaznaczanie wielu), edytor (zapis sam, załączniki,
⋮: Udostępnij, Eksport .txt/.md, Przypnij do trybu…, Archiwizuj, Usuń), okno przyjmowania z „Udostępnij”
(Nowa notatka / Dopisz do…, opcjonalnie przypięcie do trybu); w launcherze w `HandySheet`: „Notatka z OnHand”, „Otwórz OnHand”.

- [x] On1 — szkielet: moduł `:onhand` (settings/build.gradle, manifest, ikona adaptacyjna SVG), baza `onhand.db`,
      `OnHandText`, `OnHand` (intencje), lista z wyszukiwaniem, prosty edytor (bez załączników).
- [x] On2 — na zewnątrz: archiwum, zaznaczanie wielu, Udostępnij (ACTION_SEND / SEND_MULTIPLE przez FileProvider),
      eksport .txt / .md (jedna) i .zip (kilka) przez systemowe okno zapisu (CreateDocument).
      Lista: zakładki Notatki / Archiwum, długie przytrzymanie = zaznaczanie (dolny pasek: Udostępnij, Eksport,
      Archiwizuj/Przywróć, Usuń); kilka notatek → „jako jeden tekst” albo „jako pliki .txt”. Edytor ⋮: Udostępnij,
      Eksport .txt / .md, Archiwizuj (zamyka edytor) / Przywróć, Usuń. `NoteExport.kt`, `OnHandFileProvider`.
- [x] On3 — do środka: cel „Udostępnij → OnHand” (tekst, link, obraz/y, pliki), Nowa / Dopisz do…, załączniki
      w edytorze (galeria, plik) i w eksporcie.
      `OnHandShareActivity` (okno nad nadawcą: tytuł z tematu, podgląd, Nowa notatka / Dopisz do… przez listę
      w trybie wyboru, „Otwórz po zapisaniu”); pliki kopiowane od razu do `files/onhand/<id>/` (ścieżka względna
      w bazie). Edytor: „📎 Załącz” (galeria / pliki), miniatury zdjęć, wiersze plików, otwieranie, ✕ usuwa.
      Lista: 📎 N. Eksport notatki z załącznikami → .zip (plik + folder; w .md odnośniki), Udostępnij wysyła też pliki.
- [x] On4 — launcher: `OnHandHost`, przypinanie z OnHand i z `HandySheet`, rodzaj `ONHAND`, migracja starych notatek
      (baza v13), ShareActivity tekst → notatka OnHand, `Backup.kt` (`OnHandBackup` + pliki w .zip), wersja 1.4.0 + tag,
      checklista testów całości.
      Zmiana planu: bez bazy v13 — `kind` to tekst, więc `ONHAND` nie zmienia schematu, a przeniesienie notatek
      między dwiema bazami robi `OnHandBridge.migrateLegacyNotes` przy starcie (powtarzalne; też po imporcie starej
      kopii). Odnośnik pokazuje żywy tytuł z OnHand; usunięcie notatki w OnHand usuwa odnośniki (`notesDeleted`).
      OnHand: ⋮ → „Przypnij do trybu…” (pola wyboru trybów), w „Udostępnij → OnHand” chipy trybów, w trybie wyboru
      „+ Nowa notatka” wraca jako wybrana. Kopia zapasowa v3: sekcja `onhand` (notatki zawsze, załączniki w .zip).
## 1.5 — OnThemes jako osobna aplikacja motywów (gałąź `feature/onthemes`, plan z 05.10)
Nowy czat: „robimy paczkę TN z ROADMAP.md” (gałąź `feature/onthemes`).

**Podział (wzór: `:studio` / `:onhand`):** moduł `:themes` (biblioteka, pakiet `pl.rafal.onthemes`), `:app` zależy od niego.
Cały model motywów mieszka w module: `ThemeSpec` (id, rodzina, role jasne/ciemne, krótka paleta po odcieniu, styl znaczka,
wykończenie), katalog `Themes` (+ aliasy starych nazw: `CONTRAST` → `HIGH_CONTRAST`, `ELEGANT` → `LUXURY`), `ThemeStore`
(ten sam plik `appearance` i klucze co dawne `ThemePrefs` — bez migracji), `Badges.kt` (kolory znaczka trybu).
Launcher tylko czyta (`ThemeStore`, `Themes.find`), `modes.palette` trzyma id motywu. W drugą stronę `OnThemesHost`
(lista trybów, `setModeTheme`) — implementacja `data/OnThemesBridge.kt`, rejestrowana w `LaunchOnMeApp`.
Kolory systemu: Good Lock / Theme Park nie mają API — tapeta z palety (One UI / Material You liczą z niej kolory),
motyw „System” (kolory dynamiczne), „Kopiuj HEX”.

- [x] T1 — szkielet i przeniesienie: moduł `:themes`, `OnThemesActivity` z ikoną (jasna / ciemna przez `drawable-night`,
      trzy płaskie cienie w kolorach logo + THEMES, warstwa monochromatyczna), `OnThemesText`, model z `Palettes.kt` /
      `ThemePrefs.kt` przeniesiony do modułu, `OnThemesHost` + `OnThemesBridge`. OnThemes: jasność, motyw globalny
      (karty z miniaturą i paletą), lista trybów z ich motywami. W launcherze: Ustawienia → „Otwórz OnThemes”; ikona
      OnThemes w szufladzie idzie za jasnością launchera (Jasny / Ciemny), przy „Auto” za systemem.
      Wygląd launchera bez zmian (Elegancki → Luxury, Kontrast → High Contrast tylko z nazwy).
- [x] T2 — katalog: Standard, AMOLED (zawsze ciemny, czysta czerń), High Contrast (znaczki INVERTED: czarne / białe tło,
      kolorowy symbol z kontrastem ≥ 4,5:1, cienka obwódka), Czarno-biały (MONO: szare znaczki), Pastelowy, Systemowy
      (kolory Material You / One UI z tapety, Android 12+, odświeżane po powrocie na ekran). Styl znaczka idzie z motywu
      przez `LocalThemeSpec` (ustawia go `ContextLauncherTheme`). Wybór koloru trybu i akcentu: rząd „Z motywu”
      (krótka paleta) + „Wszystkie kolory” (po odcieniu) + „+”.
- [x] T3 — Luxury: metale (złoto, srebro, miedź, brąz) × bazy (butelkowa zieleń, purpura, granat, bordo, czerń) —
      20 połączeń, id `LUXURY:<METAL>:<BAZA>` (złoto / butelkowa zieleń = stare `LUXURY`). Znaczki METAL: tło w kolorze
      trybu (mocniej przyciemnione / rozjaśnione), symbol w kolorze metalu, metalowa obwódka z refleksami (też klawisz ON).
      „Połysk w ruchu” (domyślnie wyłączony): wolny refleks na klawiszu ON i dużych znaczkach (`metalSheen`).
      Wybór metalu i bazy w OnThemes pod kartą Luxury; w launcherze karta Luxury pokazuje wybrany wariant.
- [x] T4 — aplikacja: galeria (podgląd bieżącego wyglądu z Twoimi trybami, jasność, karty motywów, tryby),
      ekran motywu (podgląd launchera na żywo z przełącznikiem jasny/ciemny, paleta, Luxury do przymierzenia,
      „Ustaw jako globalny”, „Ustaw dla trybów…”, „Duplikuj i edytuj”, edycja/usuwanie własnych), wybór motywu
      dla trybu po dotknięciu trybu (też z okna trybu w launcherze: „Więcej w OnThemes”). Wiele motywów własnych
      (`CustomThemes`, JSON `custom_themes`; pierwszy ma stare id `CUSTOM`, kolejne `custom:N`) z nazwą i stylem
      znaczków. Kreator przeniesiony do modułu (własny wybór koloru HSV + HEX); z launchera usunięty `ThemeCreator.kt`,
      Ustawienia → „Kreator…” otwiera kreator w OnThemes.
- [x] T5 — kolory systemu: „Tapeta z palety…” (style Światła / Gradient / Pasy, podgląd, opcjonalnie ekran blokady;
      One UI / Material You liczy z niej paletę) i „Kopiuj kolory (HEX)” (np. do Theme Park) na ekranie motywu.
      Kopia zapasowa: wszystkie motywy własne (`customThemes`) i połysk Luxury. Wersja 1.5.0 + tag `v1.5.0`.
      Uwaga: dev (1.2.1) nie ma jeszcze OnHand 1.4.0 — przy scalaniu obu gałęzi do dev konflikty w
      `settings.gradle.kts`, `app/build.gradle.kts`, `LaunchOnMeApp.kt`, `Backup.kt`, `strings.xml`, ROADMAP.
- [ ] (opcjonalnie T6) barwione ikony aplikacji z warstwy monochromatycznej (Android 13+).

---

# Runda uwag z 18.10 (po kilkunastu dniach używania)
Stan wyjściowy: 1.6.0 = gałęzie `feature/stickonme-perf`, `feature/onhand`, `feature/onthemes` scalone do `dev`.

### Paczka 1 — szybkie poprawki (1.6.1)
- [x] OnHand: arkusz zamyka się, gdy otwierasz z niego notatkę / plik / link / aplikację OnHand (po powrocie od razu karta).
      Home zamyka też okienka, które dotąd zostawały: link, notatka, „+ Aplikacje”, „+ Skrót”, nowy tryb, ustawienia widżetów.
- [x] Menu trybów (klawisz ON) i łuk: przygaszenie 0,6 + rozmycie karty pod spodem (Android 12+, RenderEffect).
      Warstwa `ScreenOverlayLayer` przeniesiona nad treść (`LauncherApp`), treść ma `Modifier.screenBackdrop`,
      a przygaszenia zgłaszają rozmycie przez `BackdropBlur { postęp }`. Tapety systemu (za oknem) to nie rozmywa.
- [x] Tapeta trybu nie wracała: `WallpaperStore.applyFor` pomijał ustawianie, gdy podpis się zgadzał — nawet jeśli
      w międzyczasie tapetę zmienił ktoś inny (OnThemes „Tapeta z palety”, ustawienia telefonu). Teraz porównuje też
      numer tapety w systemie (`getWallpaperId`). Skutek uboczny: tapeta z palety zostaje tylko do najbliższej zmiany
      trybu, jeśli tryb (albo domyślna) ma własną tapetę.
- [x] „Lepkie” tryby: koniec trybu (powrót do Start) tylko gdy jego reguła pasowała, a potem przestała na ≥10 min
      (`END_DEBOUNCE`, `ActiveContext` w ViewModelu); tryb wybrany ręcznie bez pasującej reguły zostaje.
      Sugestia, która wisiała przy ręcznej zmianie, nie przełącza sama nigdy (dawniej po 30 min) — tylko propozycja.
      Testy w `SuggestionEngineTest`.

### Paczka 2 — odliczanie automatu (2a, 1.6.2) i OnModes (2b)
- [x] 2a: Ustawienia → Tryby: „Przełączaj sam po” 0 / 5 / 10 / 15 / 30 / 45 / 60 s (dawniej stałe 60 s, `autoDelaySec`)
      i „Odliczanie przed przełączeniem”: Brak / Pasek / Kółko wokół ✓ (`AutoCountdown`). Pasek = kolor docelowego trybu
      wypełnia pasek wyszukiwania od lewej; kółko = obwód wokół zielonego ✓. Postęp liczony z zegara co klatkę
      (`countdownProgress` w `SmartSearchBar.kt`), stan z `LauncherViewModel.autoCountdown`.
- [x] 2b: **OnModes** — wbudowane szablony mają nazwy „… On Mode” (Start / Work / Home / Travel / Study / Workout /
      Everyday On Mode, w obu językach). Istniejące tryby: zmiana tylko, gdy mają starą domyślną nazwę (EN lub PL),
      raz na instalację (`renameBuiltInModesOnce`, flaga `onmodes_names_v1`); własne nazwy zostają, zmiana nazwy działa jak dotąd.
      Tytuły (lista, sekcja Ustawień, „+ Nowy OnMode”, kreator, samouczek, widżet tarczy) → OnModes; w zdaniach zostaje „tryb”.

### Szuflada aplikacji (osobny czat)
- [x] 1.7.0: dwa widoki do wyboru w Ustawieniach: „Zakładki: Wszystkie | Foldery” albo „Jedna lista z folderami”
      (foldery z korzenia między aplikacjami, opcja „Foldery na początku”; dotknięcie otwiera folder w szufladzie,
      „‹ Wszystkie aplikacje” / Wstecz wraca; „Nowy folder” w menu ⇅). Model: `data/DrawerSettings.kt` (`DrawerView`).
- [x] 1.7.0: sortowanie: A–Z (domyślnie), najczęściej używane (własny licznik uruchomień w aktywnym trybie; folder = suma
      jego aplikacji), własna kolejność — menu ⇅ → „Ułóż ręcznie…”: przytrzymaj i przeciągnij, „Gotowe” zapisuje.
      Kolejność zostaje zapamiętana po przełączeniu na inne sortowanie; nowe aplikacje dopisują się na końcu (A–Z).
      Logika czysta i z testami: `data/DrawerArrange.kt` + `DrawerArrangeTest`.
- [x] 1.7.1: „Najczęściej używane” z danych systemu — Ustawienia → „Sortowanie »najczęściej używane« liczy”: uruchomienia
      w tym OnMode (licznik launchera) albo czas użycia z 30 dni (UsageStatsManager, `PACKAGE_USAGE_STATS`, dostęp
      nadawany ręcznie; bez niego zostaje licznik). `LauncherViewModel.usageScores`; wiersz w „Uprawnieniach”.
- [x] 1.7.0: sekcja „Nowe”: zainstalowane w ostatnich 7 dniach i jeszcze nieotwarte (w żadnym trybie); znika po
      otwarciu albo z menu „Usuń z »Nowych«” (`LauncherViewModel.newApps`, `AppInfo.installedAt`).
- [x] 1.7.1: folder w szufladzie: ⇅ w pasku akcji folderu — A–Z / najczęściej / własna kolejność („Ułóż ręcznie…”,
      podfoldery zostają na górze). Jedno ustawienie na folder (`FolderSort`, prefs `folder_sorts`), w kopii zapasowej
      z przepięciem id folderów. Siatka układania wspólna dla szuflady i folderów: `ui/ReorderGrid.kt` (`ReorderGrid`, `SortChip`).
- [x] 1.7.0: ustawienia ogólne (Ustawienia → Szuflada aplikacji) + opcjonalnie własne w OnMode (arkusz trybu →
      „Własna szuflada w tym OnMode”; własna kolejność też per tryb). Zmiana trybu przy otwartej szufladzie przestawia
      ją dopiero po zamknięciu. Rząd „Często w …” można wyłączyć (chowa się sam przy sortowaniu „najczęściej”).

### StickOnMe (osobny czat)
- [ ] Rozbudowa edycji obrazów: przesuwanie / skalowanie / obrót obrazu wewnątrz kształtu (np. serce) — transformacja
      obrazu oddzielona od maski. Reszta listy do ustalenia po testach wersji z 1.3.

### Hotfix 1.9.1 (20.10)
- [x] Informacja „Włączono …” po automatycznej zmianie wisiała na pasku do ✓ / ✕ i zasłaniała kolejną sugestię
      (z odliczaniem). Teraz znika sama po 15 s (`NOTICE_VISIBLE_MS`), a nowa sugestia od razu ją zastępuje.
- [x] Ciągłe powroty do Start: automat sam kończył tryb (EndMode → strona główna), gdy jego reguła przestała pasować.
      Teraz automat tylko włącza tryby z pasującymi regułami; zakończenie trybu jest wyłącznie propozycją ✓ / ✕.

