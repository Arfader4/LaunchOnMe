package pl.rafal.contextlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pl.rafal.contextlauncher.R
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.ModePlan
import pl.rafal.contextlauncher.data.WizardPurpose
import pl.rafal.contextlauncher.data.WizardStyle
import pl.rafal.contextlauncher.data.db.SuggestionRuleEntity
import pl.rafal.contextlauncher.suggest.SuggestionEngine
import pl.rafal.contextlauncher.suggest.toRule

private val WizardSteps = listOf(R.string.wiz_step_purpose, R.string.wiz_step_card, R.string.wiz_step_look, R.string.wiz_step_preview)

// Kreator nowego trybu: 4 krótkie kroki zamiast pustego okna "nazwa + kolor".
// 1) cel (praca, auto, wieczór…), 2) ile rzeczy na karcie i jak duże ikony, 3) nazwa, ikona, kolor,
// 4) podgląd: widżety, aplikacje (można odznaczyć), kiedy tryb będzie podpowiadany.
@Composable
fun ModeWizardDialog(
    installedApps: List<AppInfo>,
    existingNames: Set<String>,
    onCreate: (ModePlan, List<AppInfo>) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var plan by remember { mutableStateOf<ModePlan?>(null) }
    // Aplikacje odznaczone na podglądzie (po kluczu) — reszta trafi na kartę.
    var removed by remember { mutableStateOf(setOf<String>()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Wstecz: poprzedni krok, a z pierwszego — zamknięcie (jak w każdym kreatorze instalacji).
        BackHandler { if (step > 0) step-- else onDismiss() }
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .padding(20.dp),
        ) {
            // Nagłówek: numer kroku i kreski postępu.
            Text(stringResource(R.string.wiz_header, stringResource(WizardSteps[step])), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                WizardSteps.indices.forEach { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val current = plan
                when {
                    step == 0 || current == null -> PurposeStep(plan?.purpose) { purpose ->
                        // Ten sam cel po "Wstecz" — zostawiamy wszystko, co już ustawiono.
                        if (purpose != current?.purpose) {
                            // Nowy cel = nowa propozycja (nazwa i kolor z celu); nazwa zajęta → z numerem.
                            val base = ModePlan.start(purpose)
                            val name = generateSequence(1) { it + 1 }
                                .map { if (it == 1) base.name else "${base.name} $it" }
                                .first { it.lowercase() !in existingNames }
                            plan = base.copy(name = name)
                            removed = emptySet()
                        }
                        step = 1
                    }
                    step == 1 -> StyleStep(current) { plan = it }
                    step == 2 -> LookStep(current, existingNames) { plan = it }
                    else -> PreviewStep(current, installedApps, removed) { key ->
                        removed = if (key in removed) removed - key else removed + key
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                TextButton(onClick = { if (step > 0) step-- else onDismiss() }) { Text(if (step > 0) stringResource(R.string.common_back) else stringResource(R.string.common_cancel)) }
                Spacer(Modifier.weight(1f))
                val current = plan
                when {
                    step == 0 -> Unit // cel wybiera się dotknięciem karty
                    step < WizardSteps.lastIndex -> Button(
                        onClick = { step++ },
                        enabled = current != null && current.name.isNotBlank() && current.name.trim().lowercase() !in existingNames,
                    ) { Text(stringResource(R.string.common_next)) }
                    current != null -> Button(onClick = {
                        val apps = current.suggestApps(installedApps).filter { it.key !in removed }
                        onCreate(current.copy(name = current.name.trim()), apps)
                    }) { Text(stringResource(R.string.wiz_create_mode)) }
                }
            }
        }
    }
}

// Krok 1: kafelki celów (2 kolumny), każdy z krótkim opisem z szablonu.
@Composable
private fun PurposeStep(selected: WizardPurpose?, onPick: (WizardPurpose) -> Unit) {
    Text(stringResource(R.string.wiz_purpose_question), style = MaterialTheme.typography.bodyLarge)
    WizardPurpose.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { purpose ->
                OptionCard(
                    title = "${purpose.emoji}  ${purpose.label}",
                    description = purpose.template.description,
                    selected = purpose == selected,
                    onClick = { onPick(purpose) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

// Krok 2: ile rzeczy na karcie, rozmiar ikon i czy podpowiadać tryb.
@Composable
private fun StyleStep(plan: ModePlan, onChange: (ModePlan) -> Unit) {
    Text(stringResource(R.string.wiz_style_question), style = MaterialTheme.typography.bodyLarge)
    WizardStyle.entries.forEach { style ->
        OptionCard(
            title = style.label,
            description = style.description,
            selected = plan.style == style,
            onClick = { onChange(plan.copy(style = style)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(stringResource(R.string.wiz_app_icons), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            null to stringResource(R.string.wiz_icons_default),
            2 to stringResource(R.string.wiz_icons_large),
            1 to stringResource(R.string.wiz_icons_small),
        ).forEach { (cells, label) ->
            FilterChip(selected = plan.iconCells == cells, onClick = { onChange(plan.copy(iconCells = cells)) }, label = { Text(label) })
        }
    }
    // Reguły z propozycji opisane słowami (np. "pon.–pt. 08:00–16:00").
    val rules = plan.purpose.template.rules.mapNotNull { (type, params) ->
        SuggestionRuleEntity(modeId = 0, type = type, params = params).toRule()?.let(SuggestionEngine::describe)
    }
    if (rules.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onChange(plan.copy(useRules = !plan.useRules)) }
                .padding(vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.wiz_suggest_mode), style = MaterialTheme.typography.bodyLarge)
                Text(rules.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = plan.useRules, onCheckedChange = { onChange(plan.copy(useRules = it)) })
        }
    }
}

// Krok 3: nazwa, ikona, kolor (te same kontrolki co w ustawieniach trybu).
@Composable
private fun LookStep(plan: ModePlan, existingNames: Set<String>, onChange: (ModePlan) -> Unit) {
    val taken = plan.name.trim().lowercase() in existingNames
    OutlinedTextField(
        value = plan.name,
        onValueChange = { onChange(plan.copy(name = it.take(24))) },
        label = { Text(stringResource(R.string.mode_name_label)) },
        singleLine = true,
        isError = taken,
        supportingText = if (taken) ({ Text(stringResource(R.string.wiz_name_taken)) }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        ModeBadge(ModeIcon.of(plan.icon), plan.color, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Text(plan.name.ifBlank { "…" }, style = MaterialTheme.typography.titleMedium)
    }
    IconPicker(selected = ModeIcon.of(plan.icon), color = plan.color, onSelect = { onChange(plan.copy(icon = it.key)) })
    ColorSwatches(colors = ModeColors, selected = plan.color, modeBadge = true, onSelect = { c -> if (c != null) onChange(plan.copy(color = c)) })
}

// Krok 4: co powstanie. Aplikacje można odznaczyć dotknięciem (przyciemniona = nie trafi na kartę).
@Composable
private fun PreviewStep(plan: ModePlan, installedApps: List<AppInfo>, removed: Set<String>, onToggle: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ModeBadge(ModeIcon.of(plan.icon), plan.color, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${plan.style.label} · ${plan.purpose.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val widgets = plan.widgets
    Text(stringResource(R.string.wiz_widgets), style = MaterialTheme.typography.labelLarge)
    Text(
        if (widgets.isEmpty()) stringResource(R.string.wiz_no_widgets)
        else widgets.joinToString("\n") { "• ${it.kind.title}" },
        style = MaterialTheme.typography.bodyMedium,
    )
    val apps = remember(plan.purpose, plan.style, installedApps) { plan.suggestApps(installedApps) }
    Text(stringResource(R.string.wiz_apps_title), style = MaterialTheme.typography.labelLarge)
    if (apps.isEmpty()) {
        Text(stringResource(R.string.wiz_no_apps), style = MaterialTheme.typography.bodyMedium)
    } else {
        BoxWithConstraints {
            val columns = ((maxWidth + 8.dp) / 72.dp).toInt().coerceIn(3, 6)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { app ->
                            val skipped = app.key in removed
                            val appDescription = if (skipped) stringResource(R.string.wiz_app_skipped_cd, app.label) else app.label
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onToggle(app.key) }
                                    .alpha(if (skipped) 0.35f else 1f)
                                    .padding(4.dp)
                                    .semantics { contentDescription = appDescription },
                            ) {
                                Box {
                                    Image(app.icon, contentDescription = null, modifier = Modifier.size(44.dp))
                                    if (skipped) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.error),
                                        ) { Text("✕", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                                Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    Text(
        if (plan.useRules && plan.purpose.template.rules.isNotEmpty()) stringResource(R.string.wiz_rules_auto)
        else stringResource(R.string.wiz_rules_manual),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OptionCard(title: String, description: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}
