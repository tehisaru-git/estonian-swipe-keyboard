package tribixbite.cleverkeys.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tribixbite.cleverkeys.R
import tribixbite.cleverkeys.SettingsActivity
import tribixbite.cleverkeys.ui.settings.CollapsibleSettingsSection
import tribixbite.cleverkeys.ui.settings.openDictionaryManager
import tribixbite.cleverkeys.ui.settings.openLayoutManager

/** Only the navigation needed for typing; no power-user feature carousel. */
@Composable
internal fun SettingsActivity.ActivitiesSection() {
    CollapsibleSettingsSection(
        title = stringResource(R.string.swipe_settings_keyboard),
        expanded = activitiesSectionExpanded,
        onExpandChange = { activitiesSectionExpanded = it }
    ) {
        SettingsDestination(stringResource(R.string.swipe_settings_layouts)) { openLayoutManager() }
        SettingsDestination(stringResource(R.string.swipe_settings_dictionaries)) { openDictionaryManager() }
    }
}

@Composable
private fun SettingsDestination(title: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
