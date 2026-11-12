package tribixbite.cleverkeys.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tribixbite.cleverkeys.R
import tribixbite.cleverkeys.SettingsActivity
import tribixbite.cleverkeys.ViipioSpacingPreference
import tribixbite.cleverkeys.ui.settings.io.importLanguagePack
import tribixbite.cleverkeys.ui.settings.sections.TestKeyboardSection

/** Only describe and expose controls that the independent IME actually implements. */
@Composable
internal fun SettingsActivity.SettingsScreen() {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    SideEffect {
        mainScrollState = scrollState
        composeScope = scope
    }
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding()
            .padding(16.dp)
            .onGloballyPositioned { scrollViewportTop = it.positionInRoot().y }
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.swipe_preview_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        TestKeyboardSection()

        SettingsSwitch(
            title = stringResource(R.string.viipio_auto_space_after_swipe_title),
            description = stringResource(R.string.viipio_auto_space_after_swipe_desc),
            checked = viipioSpaceAfterSwipe,
            onCheckedChange = {
                viipioSpaceAfterSwipe = it
                saveSetting(ViipioSpacingPreference.KEY, it)
            }
        )

        InfoCard(stringResource(R.string.swipe_preview_languages), stringResource(R.string.swipe_preview_languages_detail))
        Card(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.swipe_preview_dictionaries), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.swipe_preview_dictionaries_detail),
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { importLanguagePack() }) { Text(stringResource(R.string.swipe_preview_import)) }
            }
        }
        InfoCard(stringResource(R.string.swipe_preview_emoji), stringResource(R.string.swipe_preview_emoji_detail))
        InfoCard(stringResource(R.string.swipe_preview_appearance), stringResource(R.string.swipe_preview_appearance_detail))
        InfoCard(stringResource(R.string.swipe_preview_privacy), stringResource(R.string.swipe_preview_privacy_detail))
        Text(stringResource(R.string.swipe_preview_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
