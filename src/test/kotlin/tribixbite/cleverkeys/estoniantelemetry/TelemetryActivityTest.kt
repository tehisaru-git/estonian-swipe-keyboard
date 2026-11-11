package tribixbite.cleverkeys.estoniantelemetry

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class TelemetryActivityTest {
    private fun all(root: View): List<View> = listOf(root) +
        (root as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { all(g.getChildAt(it)) } }.orEmpty()

    @Test fun disabledByDefaultAndExportRequiresUserChosenDocument() {
        val controller = Robolectric.buildActivity(TelemetryActivity::class.java).setup()
        val activity = controller.get()
        val views = all(activity.window.decorView)
        val buttons = views.filterIsInstance<Button>()
        assertTrue(buttons.any { it.text.toString().contains("Start") })
        assertTrue(buttons.any { it.text.toString().contains("Export") })
        assertTrue(views.filterIsInstance<TextView>().any {
            it.text.toString().contains("OFF") || it.text.toString().contains("Off")
        })
        buttons.first { it.text.toString().contains("Export") }.performClick()
        val started = Shadows.shadowOf(activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.action)
        assertTrue(started.type!!.contains("json"))
        controller.pause().stop().destroy()
    }
}
