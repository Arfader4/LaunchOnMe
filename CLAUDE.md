# LaunchOnMe — wskazówki dla Claude (i każdego, kto przejmuje pracę)

Projekt: launcher na Androida z trybami (LaunchOnMe, pakiet `pl.rafal.contextlauncher`, folder `ContextLauncher`)
+ studio naklejek **StickOnMe** jako moduł biblioteki `:studio` (pakiet `pl.rafal.stickonme`) w tym samym APK,
z własną ikoną w szufladzie. Projekt do nauki i portfolio. Plan prac i historia: `ROADMAP.md`.

## Jak pracujemy
- Rozmawiamy po polsku. Komentarze w kodzie po polsku, z porównaniami do C#/.NET (autor zna .NET, uczy się Kotlina).
- Ciemna estetyka. W ciasnych miejscach ✓ (zielony) / ✕ (czerwony), gdzie jest miejsce — przyciski z tekstem.
- Paczka zmian = jeden commit na gałęzi `dev`. Autor: Arfader4 + `Co-Authored-By` Claude w treści commita.
  Push robi właściciel (Ctrl+Shift+K w Android Studio). Po jego testach `dev` → `master`.
- Kodu nie da się skompilować w środowisku Claude — każdą paczkę przegląda osobny agent „jak kompilator”,
  poprawki przed commitem. Właściciel buduje (Ctrl+F9; Gradle Sync po zmianach w build.gradle / modułach).
- Na koniec paczki: krótki raport po polsku + checklista testów, gdy o nią poprosi.
- Wersje: X.Y.Z (Y = runda / duży temat, Z = hotfix). Przy każdej wersji podbij `versionName` i `versionCode`
  (X·10000 + Y·100 + Z) w `app/build.gradle.kts`, dopisz wiersz w ROADMAP.md („Wersje”) i tag `vX.Y.Z` w gicie.
- Warstwa na cały ekran (przygaszenie, łuk): `ui/ScreenOverlay.kt` (`OnScreen`, `ScreenDim`) — nie Popup,
  bo okno Popup na Androidzie ≤14 nie sięga pod paski systemu.

## Nowy czat — skąd wziąć kod i jak oddać zmiany
Środowisko Claude (kontener w chmurze) jest puste; kod jest na komputerze właściciela (folder `ContextLauncher`,
połączony przez aplikację Claude). GitHub z kontenera bez tokenu nie działa — commity robimy na komputerze,
push robi właściciel.
1. Na start: poproś o zgodę na usuwanie w folderze projektu (git zostawia `.git/index.lock`, pliki tymczasowe w `build/`).
2. Na komputerze: `cd $HOME/mnt/ContextLauncher && git switch <gałąź>` (gałąź podana w prompcie; nowa: `git switch -c <gałąź> dev`),
   potem `git archive --format=tar.gz -o build/src.tgz HEAD` → `device_stage_files` → w kontenerze
   `mkdir w && tar xzf …/src.tgz -C w && cp -r w w-orig`. Nie commitujemy `.idea/`.
3. Praca w `w/`, przegląd „jak kompilator” (osobny agent), poprawki.
4. `bash w/tools/claude-pack.sh N` → `device_commit_files` obu plików do `ContextLauncher\build\` → na komputerze:
   `md5sum -c build/origN.md5 --quiet && t=$(mktemp -d) && tar xzf build/batchN.tgz -C $t && (cd $t && find . -type f) | while read f; do mkdir -p "$(dirname "$f")"; cat "$t/$f" > "$f"; done`
   → `git add -A -- . ':!.idea' && git commit -F <plik z opisem>` (na końcu opisu trailer Co-Authored-By Claude),
   usuń `build/batchN.tgz` i `build/origN.md5`, w kontenerze `rm -rf w-orig && cp -r w w-orig`.
5. Równoległe czaty: każdy na własnej gałęzi `feature/…` od `dev`, z własnym numerem wersji z promptu.
   Wspólne pliki (`LauncherScreen.kt`, `strings.xml`, `build.gradle.kts`, `settings.gradle.kts`, ROADMAP) zmieniaj
   oszczędnie i dopisuj na końcu sekcji — mniej konfliktów przy scalaniu do `dev`.

## Pułapki techniczne (ważne)
- **DEX:** żadnych `return@label` ani nielokalnych `return` w lambdach inline w funkcjach @Composable
  (forEach, key, Row/Column/Box, let…) i żadnego wczesnego `return` w ciele @Composable — tylko if/else.
- Stos: Kotlin 2.2.10 (K2), AGP 9.2.1 (wbudowany Kotlin), Compose BOM 2026.02.01 (Material3),
  activity-compose 1.8.0, core-ktx 1.10.1, Room 2.7.2 (baza v12), KSP, minSdk 29, targetSdk 36,
  ML Kit subject segmentation (tylko w `:studio`), osmdroid.
- Zdjęcia zawsze przez `ImageDecoder` (orientacja z EXIF).
- Teksty: nowe teksty od razu do zasobów (`res/values` = EN, `res/values-pl` = PL); poza Compose przez
  `AppText.get(...)` (launcher) / `StudioText.get(...)` (studio). Etykiety enumów jako getter z `@StringRes`.
- Ruch/animacje: wspólne ustawienia w `ui/Motion.kt` (launcher) i `StudioMotion.kt` (studio).

## Ważne miejsca w kodzie
- `ui/LauncherScreen.kt` (ekran główny, okna), `ui/LauncherViewModel.kt` (logika), `ui/CardGridView.kt` (siatka karty,
  przeciąganie), `ui/ModeSwitcher.kt` + `ui/ModeArc.kt` (klawisz ON, lista i łuk trybów), `data/Backup.kt` (kopia .zip/.json).
- StickOnMe: `StudioActivity.kt` (biblioteka, edytor wycinania), `BoardEditor.kt` + `BoardRenderer.kt` (tablice).
- OnHand (moduł `:onhand`, pakiet `pl.rafal.onhand`, baza `onhand.db`): `OnHandActivity` + `NoteScreens.kt` (lista, edytor),
  `OnHandShareActivity` (cel „Udostępnij”), `NoteExport.kt`, `OnHandBackup.kt`; launcher łączy się przez
  `OnHandHost` ↔ `data/OnHandBridge.kt` (odnośniki `ONHAND` w `pinned_items`, uri = id notatki).
