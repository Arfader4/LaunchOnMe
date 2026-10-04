# LaunchOnMe — plan prac (runda uwag z 28.09)

Każdy etap to jedna paczka zmian, którą da się zbudować i przetestować osobno. Kolejny etap można zacząć
w nowej sesji: wystarczy napisać „robimy etap N z ROADMAP.md”. Po skończeniu etapu zaznaczamy go [x].

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
- [ ] Klawisz ON: bardziej organiczny wygląd, animacja; lista trybów jako owalne „klawisze”, OnHand i Ustawienia
      jako mniejsze przyciski pod spodem.
- [ ] Przytrzymanie klawisza: zamiast etykiety pod kciukiem — przygaszona karta, duży znaczek i nazwa trybu na środku.
- [ ] Pasek wyszukiwania w trybie czuwania: przewija ostatnio używane aplikacje (np. z ostatniej godziny),
      nieprzeczytane powiadomienia, propozycję zmiany trybu (zamiast okienka nad menu), najbliższe wydarzenie,
      budzik, niską baterię. Dotknięcie lupy = klawiatura i wyszukiwanie.

### Paczka 3 — tłumaczenie
- [ ] Wszystkie teksty do zasobów (res/values = angielski, res/values-pl = polski). Fundament gotowy:
      `LaunchOnMeApp`, `AppText`, `StudioText`. Kolejne języki potem = po jednym pliku.

### Duże tematy (osobne czaty, kolejno)
- [ ] OnHand jako pełne notatki: przyjmowanie z „Udostępnij” (Keep, Samsung Notes…), wysyłanie do nich, eksport do pliku.
      (Keep ma API tylko dla firmowych kont Workspace, Samsung Notes nie ma API — synchronizacji nie robimy.)
- [ ] Motywy (aplikacja jak StickOnMe): High Contrast (czarne/białe tło ikon + kolorowy symbol), Luxury (złoto,
      srebro, miedź, brąz + butelkowa zieleń, purpura…, połysk metalu), Black & White, Vintage, Standard, Night,
      Własny (+ propozycje: Pastel, AMOLED). Uporządkowana lista kolorów i krótka paleta na motyw. Kolory systemu:
      Good Lock nie ma API — pośrednio przez tapetę (Android 12+/One UI biorą kolory z tapety).
- [ ] StickOnMe: wydajność przy wielu zdjęciach (mniejsze kopie robocze, dekodowanie w tle, limit pamięci).
- [ ] KeepMeOn (oszczędzanie energii): tryb włączany przy słabej baterii, blokada zmiany trybu (odblokowanie odciskiem),
      najważniejsze aplikacje, bez animacji/tapety/odświeżania widżetów, ciemny motyw; propozycja w samouczku.
      Systemowego oszczędzania baterii aplikacja sama nie włączy (wymaga uprawnienia nadanego przez adb).
- [ ] Optymalizacja baterii i procesora w całym launcherze.
- [ ] Blokada folderów w szufladzie odciskiem palca / PIN-em (systemowy BiometricPrompt).
