package tribixbite.cleverkeys

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** Guards the small, honest setup flow of the Viipio keyboard launcher. */
class LauncherSetupFlowTest {
    private val launcher by lazy {
        File("src/main/kotlin/tribixbite/cleverkeys/activities/LauncherActivity.kt").readText()
    }

    @Test fun setupRetainsSafeInputMethodPickerAndStatusObserver() {
        assertWithMessage("IME enable route").that(launcher).contains("Settings.ACTION_INPUT_METHOD_SETTINGS")
        assertWithMessage("IME picker must check enabled state").that(launcher).contains("if (!isKeyboardEnabledCompat())")
        assertWithMessage("IME picker must wait for focus").that(launcher).contains("if (hasWindowFocus())")
        assertWithMessage("a lost-focus picker should retry instead of reopening setup")
            .that(launcher).contains("if (hasFocus && pickerPending)")
        assertWithMessage("system status must refresh without polling").that(launcher).contains("Settings.Secure.DEFAULT_INPUT_METHOD")
        assertWithMessage("test field must remain").that(launcher).contains("OutlinedTextField(")
    }

    @Test fun bundledEstonianDoesNotPresentDownloadAsRequiredSetup() {
        assertWithMessage("fresh installs have bundled Estonian vocabulary")
            .that(launcher).doesNotContain("ESTONIAN_PACK_URL")
        assertWithMessage("onboarding must not claim a separate pack is required")
            .that(launcher).doesNotContain("swipe_setup_et_pack")
    }

    @Test fun launcherUsesOwnIdentityAndQuietInterface() {
        assertWithMessage("new brand wordmark").that(launcher).contains("R.string.app_name")
        assertWithMessage("correct source link").that(launcher)
            .contains("https://github.com/tehisaru-git/viipio-keyboard")
        assertWithMessage("Compose cannot load an adaptive launcher icon with painterResource")
            .that(launcher).doesNotContain("painterResource(R.mipmap.ic_launcher)")
        assertWithMessage("launcher logo must load its vector foreground")
            .that(launcher).contains("painterResource(R.drawable.ic_launcher_foreground)")
        assertWithMessage("old mascot must not appear").that(launcher).doesNotContain("RaccoonMascot")
        assertWithMessage("old animation must not appear").that(launcher).doesNotContain("SparkleMagicBackground")
        assertWithMessage("old per-key gesture calibration is not part of simple setup")
            .that(launcher).doesNotContain("launcher_step_calibrate")
    }
}
