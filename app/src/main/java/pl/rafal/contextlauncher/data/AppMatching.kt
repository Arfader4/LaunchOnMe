package pl.rafal.contextlauncher.data

// Dobór aplikacji do szablonu trybu po "podpowiedziach": fragmentach nazwy pakietu albo nazwy aplikacji.
// Funkcja generyczna (<T> jak w C#), żeby test mógł podać zwykłe napisy zamiast prawdziwych aplikacji.
// Kolejność wyniku = kolejność podpowiedzi, więc najważniejsze aplikacje trafiają na kartę pierwsze.
fun <T> matchApps(
    apps: List<T>,
    hints: List<String>,
    packageOf: (T) -> String,
    labelOf: (T) -> String,
    limit: Int = 8,
): List<T> =
    hints
        .flatMap { hint ->
            apps.filter { packageOf(it).contains(hint, ignoreCase = true) || labelOf(it).contains(hint, ignoreCase = true) }
        }
        .distinct() // ta sama aplikacja pasująca do dwóch podpowiedzi tylko raz
        // Dwie aplikacje o tej samej nazwie (np. "Wiadomości" Google i Samsunga) to ta sama rola — bierzemy pierwszą.
        .distinctBy { labelOf(it).lowercase() }
        .take(limit)
