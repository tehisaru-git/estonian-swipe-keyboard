package tribixbite.cleverkeys.estonianstudy

import android.content.Intent
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import tribixbite.cleverkeys.estonianui.KeyboardGeometry
import tribixbite.cleverkeys.estonianui.KeyboardLanguage
import tribixbite.cleverkeys.estonianui.SurfaceSample

@RunWith(RobolectricTestRunner::class)
class SwipeStudyActivityTest {
    @Test fun rotationRetainsRecordedTracesAndFieldIdentityWithoutDiskPersistence() {
        val controller = Robolectric.buildActivity(SwipeStudyActivity::class.java).setup()
        val activity = controller.get()
        fun all(root: android.view.View): List<android.view.View> =
            listOf(root) + (root as? ViewGroup)?.let { group ->
                (0 until group.childCount).flatMap { all(group.getChildAt(it)) }
            }.orEmpty()
        val views = all(activity.window.decorView)
        val field = views.filterIsInstance<EditText>().single()
        views.filterIsInstance<Button>().first { it.text == "Start" }.performClick()
        val editor = android.view.inputmethod.EditorInfo().apply {
            packageName = activity.packageName
            fieldId = field.id
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val geometry = KeyboardGeometry(320, 200, KeyboardLanguage.ESTONIAN)
        assertTrue(SwipeStudyBridge.onSwipeDecoded(editor,
            listOf(SurfaceSample(10f, 10f, 1L, null)), geometry, listOf("tere")))
        controller.recreate()
        val rotated = controller.get()
        assertNotSame(activity, rotated)
        val rotatedViews = all(rotated.window.decorView)
        assertEquals(field.id, rotatedViews.filterIsInstance<EditText>().single().id)
        assertTrue(rotatedViews.filterIsInstance<android.widget.TextView>()
            .any { it.text.toString().startsWith("1 / 100 traces") })
        controller.pause().stop().destroy()
    }

    @Test fun onlyStartEnablesOwnFieldAndExportOpensDocumentPicker() {
        val controller = Robolectric.buildActivity(SwipeStudyActivity::class.java).create().start().resume()
        val activity = controller.get()
        fun all(root: android.view.View): List<android.view.View> =
            listOf(root) + (root as? ViewGroup)?.let { group ->
                (0 until group.childCount).flatMap { all(group.getChildAt(it)) }
            }.orEmpty()
        val views = all(activity.window.decorView)
        val field = views.filterIsInstance<EditText>().single()
        val buttons = views.filterIsInstance<Button>()
        val editor = android.view.inputmethod.EditorInfo().apply {
            packageName = activity.packageName
            fieldId = field.id
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val geometry = KeyboardGeometry(320, 200, KeyboardLanguage.ESTONIAN)
        val samples = listOf(SurfaceSample(10f, 10f, 1L, null))
        assertFalse(SwipeStudyBridge.onSwipeDecoded(editor, samples, geometry, listOf("tere")))
        buttons.single { it.text == "Start" }.performClick()
        assertTrue(SwipeStudyBridge.onSwipeDecoded(editor, samples, geometry, listOf("tere")))
        assertTrue(views.filterIsInstance<android.widget.TextView>().any { it.text == "Swipe: kass" })
        buttons.single { it.text == "Stop" }.performClick()
        assertFalse(SwipeStudyBridge.onSwipeDecoded(editor, samples, geometry, listOf("tere")))
        buttons.single { it.text == "Export JSON" }.performClick()
        val intent = Shadows.shadowOf(activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/json", intent.type)
        controller.pause().stop().destroy()
        assertFalse(SwipeStudyBridge.onSwipeDecoded(editor, samples, geometry, listOf("tere")))
    }
}
