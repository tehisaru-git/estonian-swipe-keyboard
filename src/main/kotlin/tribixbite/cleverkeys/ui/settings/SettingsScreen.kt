package tribixbite.cleverkeys.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tribixbite.cleverkeys.R
import tribixbite.cleverkeys.LauncherActivity
import tribixbite.cleverkeys.SettingsActivity
import tribixbite.cleverkeys.ViipioSpacingPreference
import tribixbite.cleverkeys.ViipioDoubleSpacePreference
import tribixbite.cleverkeys.ViipioBehaviorPreference
import tribixbite.cleverkeys.ViipioPersonalWords
import tribixbite.cleverkeys.estoniantelemetry.TelemetryActivity
import tribixbite.cleverkeys.ui.settings.io.importLanguagePack

/** Single English-language home for the active IME; no inherited, inert CleverKeys controls. */
@Composable
internal fun SettingsActivity.SettingsScreen() {
    var page by rememberSaveable { mutableStateOf("home") }
    BackHandler(page != "home") { page = "home" }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    SideEffect {
        mainScrollState = scrollState
        composeScope = scope
    }
    val diagnosticsDescription = stringResource(when {
        diagnosticsUnavailable -> R.string.viipio_diagnostics_unavailable
        diagnosticsEnabled == null -> R.string.viipio_diagnostics_checking
        diagnosticsEnabled == true -> R.string.viipio_diagnostics_on
        else -> R.string.viipio_diagnostics_off
    })
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding()
            .padding(16.dp)
            .onGloballyPositioned { scrollViewportTop = it.positionInRoot().y }
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (page != "home") TextButton(onClick = { page = "home" }) {
            Text(stringResource(R.string.viipio_settings_back))
        }
        Text(stringResource(when (page) {
            "preferences" -> R.string.viipio_settings_preferences
            "corrections" -> R.string.viipio_settings_corrections
            "glide" -> R.string.viipio_settings_glide
            "dictionary" -> R.string.viipio_settings_dictionary
            "privacy" -> R.string.viipio_settings_privacy
            else -> R.string.app_name
        }), fontSize = 28.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        when (page) {
            "home" -> {
                Text(stringResource(R.string.swipe_preview_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsDestination(stringResource(R.string.viipio_settings_preferences),
                    stringResource(R.string.viipio_settings_preferences_detail)) { page = "preferences" }
                SettingsDestination(stringResource(R.string.viipio_settings_corrections),
                    stringResource(R.string.viipio_settings_corrections_detail)) { page = "corrections" }
                SettingsDestination(stringResource(R.string.viipio_settings_glide),
                    stringResource(R.string.viipio_settings_glide_detail)) { page = "glide" }
                SettingsDestination(stringResource(R.string.viipio_settings_dictionary),
                    stringResource(R.string.viipio_settings_dictionary_detail)) { page = "dictionary" }
                SettingsDestination(stringResource(R.string.viipio_settings_privacy),
                    diagnosticsDescription) { page = "privacy" }
                SettingsDestination(stringResource(R.string.viipio_setup_and_try_title),
                    stringResource(R.string.viipio_setup_and_try_detail)) {
                    startActivity(Intent(this@SettingsScreen, LauncherActivity::class.java))
                }
            }
            "preferences" -> {
                SettingsSwitch(
                    title = stringResource(R.string.viipio_double_space_period_title),
                    description = stringResource(R.string.viipio_double_space_period_desc),
                    checked = viipioDoubleSpacePeriod,
                    onCheckedChange = {
                        viipioDoubleSpacePeriod = it
                        saveSetting(ViipioDoubleSpacePreference.KEY, it)
                    }
                )
                SettingsSwitch(
                    title = stringResource(R.string.viipio_auto_space_after_swipe_title),
                    description = stringResource(R.string.viipio_auto_space_after_swipe_desc),
                    checked = viipioSpaceAfterSwipe,
                    onCheckedChange = {
                        viipioSpaceAfterSwipe = it
                        saveSetting(ViipioSpacingPreference.KEY, it)
                    }
                )
                SettingsSwitch(
                    title = stringResource(R.string.viipio_corner_symbols_title),
                    description = stringResource(R.string.viipio_corner_symbols_desc),
                    checked = viipioCornerSymbols,
                    onCheckedChange = {
                        viipioCornerSymbols = it
                        saveSetting(ViipioBehaviorPreference.CORNER_SYMBOLS, it)
                    }
                )
            }
            "corrections" -> {
                SettingsSwitch(
                    title = stringResource(R.string.viipio_typed_suggestions_title),
                    description = stringResource(R.string.viipio_typed_suggestions_desc),
                    checked = viipioTypedSuggestions,
                    onCheckedChange = {
                        viipioTypedSuggestions = it
                        saveSetting(ViipioBehaviorPreference.TYPED_SUGGESTIONS, it)
                    }
                )
                SettingsSwitch(
                    title = stringResource(R.string.viipio_backspace_word_undo_title),
                    description = stringResource(R.string.viipio_backspace_word_undo_desc),
                    checked = viipioBackspaceWordUndo,
                    onCheckedChange = {
                        viipioBackspaceWordUndo = it
                        saveSetting(ViipioBehaviorPreference.BACKSPACE_WORD_UNDO, it)
                    }
                )
            }
            "glide" -> SettingsSwitch(
                title = stringResource(R.string.viipio_glide_delete_title),
                description = stringResource(R.string.viipio_glide_delete_desc),
                checked = viipioGlideDelete,
                onCheckedChange = {
                    viipioGlideDelete = it
                    saveSetting(ViipioBehaviorPreference.GLIDE_DELETE, it)
                }
            )
            "dictionary" -> {
                InfoCard(stringResource(R.string.swipe_preview_languages),
                    stringResource(R.string.swipe_preview_languages_detail))
                PersonalWordsControls()
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(R.string.swipe_preview_dictionaries),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.swipe_preview_dictionaries_detail),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { importLanguagePack() }) {
                            Text(stringResource(R.string.swipe_preview_import))
                        }
                    }
                }
            }
            "privacy" -> {
                InfoCard(stringResource(R.string.swipe_preview_privacy),
                    stringResource(R.string.swipe_preview_privacy_detail))
                SettingsDestination(
                    title = stringResource(R.string.telemetry_title),
                    detail = diagnosticsDescription,
                    onClick = { startActivity(Intent(this@SettingsScreen, TelemetryActivity::class.java)) }
                )
                Text(stringResource(R.string.swipe_preview_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsActivity.PersonalWordsControls() {
    val personal = remember { ViipioPersonalWords(this@PersonalWordsControls) }
    var language by rememberSaveable { mutableStateOf("et") }
    var draft by rememberSaveable { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val entries = remember(language, revision) { personal.words(language) }
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.viipio_personal_words_title),
                style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.viipio_personal_words_detail),
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language == "et",
                    onClick = { language = "et"; draft = ""; feedback = "" },
                    label = { Text(stringResource(R.string.viipio_language_estonian)) })
                FilterChip(selected = language == "en",
                    onClick = { language = "en"; draft = ""; feedback = "" },
                    label = { Text(stringResource(R.string.viipio_language_english)) })
            }
            OutlinedTextField(value = draft, onValueChange = { draft = it.take(49) },
                label = { Text(stringResource(R.string.viipio_personal_word_label)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val result = personal.add(language, draft)
                feedback = getString(when (result) {
                    ViipioPersonalWords.AddResult.ADDED -> R.string.viipio_word_added
                    ViipioPersonalWords.AddResult.INVALID -> R.string.viipio_word_invalid
                    ViipioPersonalWords.AddResult.EXISTS -> R.string.viipio_word_exists
                    ViipioPersonalWords.AddResult.FULL -> R.string.viipio_word_full
                    ViipioPersonalWords.AddResult.UNAVAILABLE -> R.string.viipio_word_unavailable
                })
                if (result == ViipioPersonalWords.AddResult.ADDED) { draft = ""; revision++ }
            }) { Text(stringResource(R.string.viipio_personal_word_add)) }
            if (feedback.isNotEmpty()) Text(feedback, style = MaterialTheme.typography.bodySmall)
            if (entries.isEmpty()) Text(stringResource(R.string.viipio_personal_words_empty),
                style = MaterialTheme.typography.bodySmall)
            entries.forEach { word ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(word, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = {
                        if (personal.remove(language, word)) {
                            revision++
                            feedback = getString(R.string.viipio_word_removed)
                        } else feedback = getString(R.string.viipio_word_unavailable)
                    }) { Text(stringResource(R.string.viipio_personal_word_remove, word)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsDestination(title: String, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoCard(title: String, detail: String) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
