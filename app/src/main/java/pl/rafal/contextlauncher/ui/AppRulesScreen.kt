package pl.rafal.contextlauncher.ui

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.rafal.contextlauncher.data.AppInfo
import pl.rafal.contextlauncher.data.appKey
import pl.rafal.contextlauncher.data.db.AppRestrictionEntity.Companion.KIND_BLOCK
import pl.rafal.contextlauncher.data.db.AppRestrictionEntity.Companion.KIND_HIDE
import androidx.compose.ui.res.stringResource
import pl.rafal.contextlauncher.R
import androidx.annotation.StringRes

// Grupy do szybkiego zaznaczania. Kategoria pochodzi z samej aplikacji (sklep), a listy pakietów
// łatają te, które kategorii nie podają (np. Instagram nie zawsze deklaruje "społecznościowa").
private enum class AppGroup(@StringRes val labelRes: Int, val category: Int?, val packages: List<String>) {
    SOCIAL(
        R.string.rules_group_social, ApplicationInfo.CATEGORY_SOCIAL,
        listOf("com.facebook.katana", "com.instagram.android", "com.zhiliaoapp.musically", "com.twitter.android",
            "com.snapchat.android", "com.reddit.frontpage", "com.pinterest", "com.linkedin.android", "com.bereal.ft"),
    ),
    MESSAGING(
        R.string.rules_group_messaging, null,
        listOf("com.whatsapp", "com.facebook.orca", "org.telegram.messenger", "org.thoughtcrime.securesms",
            "com.discord", "com.viber.voip", "com.snapchat.android"),
    ),
    VIDEO(
        R.string.rules_group_video, ApplicationInfo.CATEGORY_VIDEO,
        listOf("com.google.android.youtube", "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus", "com.hbo.hbonow", "com.wbd.stream", "pl.tvn.player", "tv.twitch.android.app"),
    ),
    GAMES(R.string.rules_group_games, ApplicationInfo.CATEGORY_GAME, emptyList()),
    SHOPPING(
        R.string.rules_group_shopping, null,
        listOf("pl.allegro", "com.amazon.mShop.android.shopping", "com.einnovation.temu", "com.zzkko",
            "com.alibaba.aliexpresshd", "pl.tablica", "fr.vinted", "de.zalando.mobile"),
    ),
    NEWS(R.string.rules_group_news, ApplicationInfo.CATEGORY_NEWS, emptyList());

    fun matches(app: AppInfo) = app.category == category || packages.any { app.packageName.startsWith(it) }
}

// Zakres edycji: konkretny tryb albo szablon "nowe tryby" (id = null).
private data class Scope(val modeId: Long?, val label: String)

// Ekran "Blokowanie i ukrywanie". Najpierw wybierasz zakres (tryb), potem zaznaczasz aplikacje
// (pojedynczo albo całymi grupami) i jednym przyciskiem blokujesz / ukrywasz / przywracasz.
// "W wielu trybach…" robi to samo naraz w kilku trybach i w szablonie nowych trybów.
@Composable
fun AppRulesScreen(
    viewModel: LauncherViewModel,
    initialModeId: Long?,
    onClose: () -> Unit,
) {
    val modes by viewModel.modes.collectAsState()
    val installed by viewModel.installedApps.collectAsState()
    val restrictions by viewModel.restrictions.collectAsState()
    val newModeDefaults by viewModel.settings.newModeRestrictions.flow.collectAsState()

    val context = LocalContext.current
    val scopes = listOf(Scope(null, stringResource(R.string.rules_new_modes))) + modes.map { Scope(it.id, it.name) }
    var scopeId by remember { mutableStateOf(initialModeId ?: modes.firstOrNull()?.id) }
    var filter by remember { mutableStateOf("") }
    var onlyRestricted by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) } // klucze appKey
    var multiScopeOpen by remember { mutableStateOf(false) }

    // Jedna pozycja na aplikację (niektóre mają kilka ikon startowych).
    val apps = remember(installed) { installed.distinctBy { it.appKey } }

    // Stan aplikacji w wybranym zakresie: BLOCK, HIDE albo null.
    fun kindIn(scope: Long?, app: AppInfo): String? =
        if (scope == null) {
            newModeDefaults.firstOrNull { it.substringAfter('|') == app.appKey }?.substringBefore('|')
        } else {
            restrictions.firstOrNull { it.modeId == scope && appKey(it.packageName, it.userSerial) == app.appKey }?.kind
        }

    val visible = apps.filter { app ->
        (filter.isBlank() || app.label.contains(filter.trim(), ignoreCase = true)) &&
            (!onlyRestricted || kindIn(scopeId, app) != null)
    }
    val selectedApps = apps.filter { it.appKey in selected }

    val backCd = stringResource(R.string.rules_back_cd)
    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .semantics { contentDescription = backCd },
            ) { Text("←", style = MaterialTheme.typography.titleLarge) }
            Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.titleLarge)
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.rules_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.heightIn(min = 8.dp))

            // Zakres: którego trybu dotyczą zmiany.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                scopes.forEach { scope ->
                    FilterChip(
                        selected = scope.modeId == scopeId,
                        onClick = { scopeId = scope.modeId },
                        label = { Text(scope.label) },
                    )
                }
            }

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text(stringResource(R.string.rules_search)) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            // Szybkie zaznaczanie grup + filtr "tylko z blokadą".
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = onlyRestricted, onClick = { onlyRestricted = !onlyRestricted }, label = { Text(stringResource(R.string.rules_only_changed)) })
                AppGroup.entries.forEach { group ->
                    AssistChip(
                        onClick = { selected = selected + apps.filter(group::matches).map { it.appKey } },
                        label = { Text("+ ${stringResource(group.labelRes)}") },
                    )
                }
            }
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            items(visible, key = { it.appKey }) { app ->
                val kind = kindIn(scopeId, app)
                // Gdzie jeszcze aplikacja jest zablokowana/ukryta — przegląd bez przełączania zakresów.
                val elsewhere = restrictions
                    .filter { appKey(it.packageName, it.userSerial) == app.appKey && it.modeId != scopeId }
                    .mapNotNull { r -> modes.firstOrNull { it.id == r.modeId }?.name?.let { name -> if (r.kind == KIND_BLOCK) "🔒 $name" else context.getString(R.string.rules_hidden_in, name) } }
                AppRuleRow(
                    app = app,
                    kind = kind,
                    elsewhere = elsewhere.joinToString(", "),
                    checked = app.appKey in selected,
                    onToggle = { selected = if (app.appKey in selected) selected - app.appKey else selected + app.appKey },
                )
            }
        }

        // Pasek akcji — tylko gdy coś jest zaznaczone.
        if (selected.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.rules_selected, selected.size, scopes.firstOrNull { it.modeId == scopeId }?.label.orEmpty()),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { multiScopeOpen = true }) { Text(stringResource(R.string.rules_in_multiple_modes)) }
                    TextButton(onClick = { selected = emptySet() }) { Text(stringResource(R.string.rules_deselect)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fun applyKind(kind: String?) {
                        viewModel.setRestriction(selectedApps, listOf(scopeId), kind)
                        selected = emptySet()
                    }
                    FilledTonalButton(onClick = { applyKind(KIND_BLOCK) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.rules_block)) }
                    FilledTonalButton(onClick = { applyKind(KIND_HIDE) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.rules_hide)) }
                    FilledTonalButton(onClick = { applyKind(null) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_restore)) }
                }
            }
        }
    }

    if (multiScopeOpen) {
        MultiScopeDialog(
            scopes = scopes,
            initial = setOf(scopeId),
            count = selected.size,
            onConfirm = { chosen, kind ->
                viewModel.setRestriction(selectedApps, chosen, kind)
                selected = emptySet()
                multiScopeOpen = false
            },
            onDismiss = { multiScopeOpen = false },
        )
    }
}

@Composable
private fun AppRuleRow(app: AppInfo, kind: String?, elsewhere: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (elsewhere.isNotEmpty()) {
                Text(
                    elsewhere,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (kind) {
            KIND_BLOCK -> StatusPill(stringResource(R.string.rules_status_blocked), MaterialTheme.colorScheme.error)
            KIND_HIDE -> StatusPill(stringResource(R.string.rules_status_hidden), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// Wybór wielu trybów + akcji naraz, np. "zablokuj TikToka w Pracy, Studiach i w nowych trybach".
@Composable
private fun MultiScopeDialog(
    scopes: List<Scope>,
    initial: Set<Long?>,
    count: Int,
    onConfirm: (Set<Long?>, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(initial) }
    var kind by remember { mutableStateOf<String?>(KIND_BLOCK) }
    val kinds = listOf<Pair<String?, String>>(KIND_BLOCK to stringResource(R.string.rules_block), KIND_HIDE to stringResource(R.string.rules_hide), null to stringResource(R.string.common_restore))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.rules_apps_count, count)) },
        text = {
            Column {
                kinds.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { kind = value },
                    ) {
                        RadioButton(selected = kind == value, onClick = { kind = value })
                        Text(label)
                    }
                }
                Spacer(Modifier.heightIn(min = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rules_in_modes), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        chosen = if (chosen.size == scopes.size) emptySet() else scopes.map { it.modeId }.toSet()
                    }) { Text(if (chosen.size == scopes.size) stringResource(R.string.rules_select_none) else stringResource(R.string.rules_select_all)) }
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(scopes, key = { it.modeId ?: -1L }) { scope ->
                        val on = scope.modeId in chosen
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chosen = if (on) chosen - scope.modeId else chosen + scope.modeId },
                        ) {
                            Checkbox(checked = on, onCheckedChange = { chosen = if (on) chosen - scope.modeId else chosen + scope.modeId })
                            Text(scope.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen, kind) }, enabled = chosen.isNotEmpty()) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// Okno przy próbie otwarcia zablokowanej aplikacji. Zamiast twardej blokady — chwila namysłu:
// "Otwórz mimo to" staje się aktywne dopiero po 5 sekundach.
@Composable
fun BlockedLaunchDialog(
    app: AppInfo,
    modeName: String,
    onCancel: () -> Unit,
    onOpenAnyway: () -> Unit,
    onUnblock: () -> Unit,
) {
    var secondsLeft by remember(app.key) { mutableIntStateOf(5) }
    LaunchedEffect(app.key) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(48.dp)) },
        title = { Text(stringResource(R.string.rules_blocked_title, app.label, modeName)) },
        text = {
            Column {
                Text(stringResource(R.string.rules_blocked_text))
                TextButton(onClick = onUnblock) { Text(stringResource(R.string.rules_unblock)) }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.rules_dont_open)) } },
        dismissButton = {
            TextButton(onClick = onOpenAnyway, enabled = secondsLeft == 0) {
                Text(if (secondsLeft > 0) stringResource(R.string.rules_open_anyway_countdown, secondsLeft) else stringResource(R.string.rules_open_anyway))
            }
        },
    )
}
