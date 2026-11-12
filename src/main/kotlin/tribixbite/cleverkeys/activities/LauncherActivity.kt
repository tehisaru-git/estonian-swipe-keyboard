package tribixbite.cleverkeys

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import tribixbite.cleverkeys.theme.EstonianSwipeTheme

/** Minimal setup shell. The IME's swipe backend remains separate from this Compose UI. */
class LauncherActivity : ComponentActivity() {
    companion object {
        private const val TAG = "LauncherActivity"
        private const val GITHUB_URL = "https://github.com/tehisaru-git/viipio-keyboard"
    }

    private var pickerPending = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pickerPending) {
            pickerPending = false
            window.decorView.post { launchInputMethodPicker() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        window.findViewById<android.view.View>(android.R.id.content)
            ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        setContent {
            EstonianSwipeTheme {
                LauncherScreen(
                    onEnableKeyboard = ::launchKeyboardSettings,
                    onSelectKeyboard = ::launchInputMethodPicker,
                    onOpenGitHub = ::openGitHub
                )
            }
        }
    }

    private fun launchKeyboardSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open input method settings", e)
        }
    }

    private fun isKeyboardEnabledCompat(): Boolean = try {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.any { it.packageName == packageName }
    } catch (e: Exception) {
        Log.e(TAG, "Cannot read enabled keyboards", e)
        false
    }

    private fun launchInputMethodPicker() {
        // Only ask system_server to show its picker while the IME is enabled and this window
        // has focus. Forcing an unfocused picker can destabilize some Android builds.
        try {
            if (!isKeyboardEnabledCompat()) {
                launchKeyboardSettings()
                return
            }
            window.decorView.post {
                if (hasWindowFocus()) {
                    try {
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .showInputMethodPicker()
                    } catch (e: Exception) {
                        Log.e(TAG, "Cannot show input method picker", e)
                        launchKeyboardSettings()
                    }
                } else {
                    // Do not send an already-enabled user back to setup. Retry only when
                    // this Activity regains focus; system_server owns the picker window.
                    pickerPending = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot select input method", e)
        }
    }

    private fun openGitHub() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open project source", e)
        }
    }
}

private fun keyboardEnabled(context: Context): Boolean = try {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.enabledInputMethodList.any { it.packageName == context.packageName }
} catch (_: Exception) { false }

private fun keyboardSelected(context: Context): Boolean = try {
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    current?.substringBefore('/') == context.packageName
} catch (_: Exception) { false }

@Composable
fun LauncherScreen(
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by remember { mutableStateOf(keyboardEnabled(context)) }
    var selected by remember { mutableStateOf(keyboardSelected(context)) }
    var testText by remember { mutableStateOf("") }

    // Keep the two setup states fresh when the user returns from Android's IME picker.
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = keyboardEnabled(context)
                selected = keyboardSelected(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS), false, observer)
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp)
                    .background(Color(0xFF22364C), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                        modifier = Modifier.size(52.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.swipe_setup_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            SetupStep(number = "01", title = stringResource(R.string.swipe_setup_enable),
                detail = stringResource(R.string.swipe_setup_enable_detail),
                completed = enabled, onClick = onEnableKeyboard)
            SetupStep(number = "02", title = stringResource(R.string.swipe_setup_select),
                detail = stringResource(R.string.swipe_setup_select_detail),
                completed = selected, onClick = onSelectKeyboard)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.swipe_setup_try),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = testText, onValueChange = { testText = it },
                placeholder = { Text(stringResource(R.string.swipe_setup_try_hint)) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(18.dp)
            )
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.swipe_preview_languages),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.swipe_preview_languages_detail),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.swipe_preview_dictionaries),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.swipe_preview_dictionaries_detail),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onOpenGitHub) {
                Text(stringResource(R.string.swipe_setup_github))
            }
            Text(stringResource(R.string.swipe_setup_privacy),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupStep(
    number: String, title: String, detail: String, completed: Boolean, onClick: () -> Unit
) {
    val color = MaterialTheme.colorScheme
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(42.dp)
                .background(if (completed) color.secondaryContainer else color.primaryContainer,
                    RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center) {
                if (completed) Icon(Icons.Default.Check,
                    contentDescription = stringResource(R.string.swipe_setup_done),
                    tint = color.onSecondaryContainer)
                else Text(number, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = color.onPrimaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(if (completed) stringResource(R.string.swipe_setup_done) else detail,
                    color = color.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
