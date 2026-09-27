package pl.rafal.contextlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.ModeTemplate
import pl.rafal.contextlauncher.data.ModeTemplates

// Kreator pierwszego uruchomienia: powitanie → wybór trybów z szablonów → ściąga.
// firstRun = true: świeża instalacja (nie da się go zamknąć bez utworzenia trybu).
@Composable
fun OnboardingScreen(
    installedApps: List<AppInfo>,
    existingModeNames: Set<String>,
    firstRun: Boolean,
    onCreate: (List<ModeTemplate>) -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
) {
    // Kroki kreatora; rememberSaveable przeżywa obrót ekranu.
    var step by rememberSaveable { mutableStateOf(if (firstRun) 0 else 1) }
    // Domyślnie zaznaczone: Praca i Dom (o ile jeszcze ich nie ma).
    var selected by remember {
        mutableStateOf(
            listOf(ModeTemplates.Start, ModeTemplates.Work, ModeTemplates.Home)
                .map { it.name }
                .filter { it.lowercase() !in existingModeNames }
                .toSet(),
        )
    }

    // Wstecz: krok do tyłu; z pierwszego kroku zamyka tylko wtedy, gdy to nie pierwsze uruchomienie.
    BackHandler {
        when {
            step == 2 -> onClose()
            step == 1 && firstRun -> step = 0
            step == 1 -> onClose()
            else -> Unit // na powitaniu przy pierwszym uruchomieniu nic nie robimy
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(20.dp),
    ) {
        // AnimatedContent płynnie podmienia zawartość przy zmianie kroku.
        AnimatedContent(targetState = step, label = "krok kreatora", modifier = Modifier.weight(1f)) { current ->
            when (current) {
                0 -> WelcomeStep()
                1 -> ChooseModesStep(
                    installedApps = installedApps,
                    existingModeNames = existingModeNames,
                    selected = selected,
                    onToggle = { name -> selected = if (name in selected) selected - name else selected + name },
                )
                else -> TipsStep()
            }
        }

        Spacer(Modifier.height(12.dp))

        // Przyciski na dole zależne od kroku.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            when (step) {
                0 -> {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { step = 1 }) { Text("Dalej") }
                }
                1 -> {
                    TextButton(onClick = {
                        if (firstRun) {
                            onSkip() // pusty launcher nie ma sensu: tworzymy jeden prosty tryb
                            step = 2
                        } else {
                            onClose()
                        }
                    }) { Text(if (firstRun) "Pomiń" else "Anuluj") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            // Zachowujemy kolejność z listy szablonów, nie kolejność klikania.
                            onCreate(ModeTemplates.All.filter { it.name in selected })
                            step = 2
                        },
                    ) { Text("Utwórz tryby (${selected.size})") }
                }
                else -> {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onClose) { Text("Zaczynamy") }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
    ) {
        Text("Context Launcher", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Ekran główny, który zmienia się razem z Twoim dniem.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Feature("Tryby", "Praca, Dom, Podróż… Każdy ma własną kartę z aplikacjami i widżetami.")
        Feature("Pod ręką", "Bilety, faktury i notatki przypięte do trybu, zawsze na wierzchu.")
        Feature("Podpowiedzi", "Launcher sam zaproponuje tryb o właściwej porze albo przed podróżą z kalendarza.")
    }
}

@Composable
private fun Feature(title: String, text: String) {
    Row {
        Spacer(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChooseModesStep(
    installedApps: List<AppInfo>,
    existingModeNames: Set<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Wybierz tryby", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Launcher sam dobierze aplikacje z Twojego telefonu. Wszystko zmienisz później.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        // Dopasowanie aplikacji liczymy raz (a nie przy każdym zaznaczeniu szablonu).
        val picks = remember(installedApps) { ModeTemplates.All.associateWith { ModeTemplates.pickApps(installedApps, it) } }
        ModeTemplates.All.forEach { template ->
            val alreadyHave = template.name.lowercase() in existingModeNames
            TemplateCard(
                template = template,
                apps = picks[template].orEmpty(),
                checked = template.name in selected,
                alreadyHave = alreadyHave,
                onClick = { if (!alreadyHave) onToggle(template.name) },
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: ModeTemplate,
    apps: List<AppInfo>,
    checked: Boolean,
    alreadyHave: Boolean,
    onClick: () -> Unit,
) {
    val accent = Color(template.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (alreadyHave) 0.5f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (checked) accent else MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clickable(enabled = !alreadyHave, onClick = onClick)
            .padding(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeBadge(ModeIcon.of(template.icon), template.color, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (alreadyHave) {
                    Spacer(Modifier.width(8.dp))
                    Text("już masz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Podgląd: ikony aplikacji, które kreator znalazł w telefonie.
            if (apps.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    apps.take(6).forEach { app ->
                        Image(app.icon, contentDescription = app.label, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
        Checkbox(checked = checked || alreadyHave, onCheckedChange = null, enabled = !alreadyHave)
    }
}

@Composable
private fun TipsStep() {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
    ) {
        Text("Gotowe!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text("Kilka gestów na start:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Feature("Nazwa trybu u góry", "Zmiana trybu, a ⋮ przy trybie to jego reguły i ustawienia.")
        Feature("Przesunięcie w górę", "Szuflada ze wszystkimi aplikacjami i folderami.")
        Feature("Menu ⋯", "Widżety, Pod ręką, foldery i zmiana układu karty.")
        Feature("Przytrzymanie ikony", "Dodanie do trybu albo folderu, usuwanie, informacje o aplikacji.")
    }
}
