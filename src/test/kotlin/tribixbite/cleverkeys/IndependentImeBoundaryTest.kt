package tribixbite.cleverkeys

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Release guard: a themed CleverKeys view is not an independent input method. */
class IndependentImeBoundaryTest {
    @Test fun activeInputMethodUsesNewSurfaceInsteadOfOldRenderer() {
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("AndroidManifest.xml"))
        val services = manifest.getElementsByTagName("service")
        val androidName = "android:name"
        val actionName = "android:name"
        val active = (0 until services.length).map { services.item(it) as org.w3c.dom.Element }
            .single { service ->
                val actions = service.getElementsByTagName("action")
                (0 until actions.length).any { index ->
                    (actions.item(index) as org.w3c.dom.Element).getAttribute(actionName) ==
                        "android.view.InputMethod"
                }
            }
        val className = active.getAttribute(androidName)
        assertThat(className).isEqualTo("tribixbite.cleverkeys.EstonianSwipeService")
        val implementation = File("src/main/kotlin/" + className.replace('.', '/') + ".kt")
        assertThat(implementation.exists()).isTrue()
        val source = implementation.readText()
        assertThat(source).doesNotContain("Keyboard2View")
        assertThat(source).doesNotContain("Pointers")
        assertThat(source).doesNotContain("PredictionViewSetup")
    }
}
