package tribixbite.cleverkeys.estonianui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KeyboardSurfaceViewTest {
    @Test fun drawUsesGeometryAndDarkPaletteWhileTouchAndAccessibilityShareTargets() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 330, 250)
        view.darkMode = true
        val bitmap = Bitmap.createBitmap(330, 250, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertEquals(0xff1f1f1f.toInt(), view.palette.surface)
        val key = view.geometry.rows[1][0]
        assertEquals(0xff33383f.toInt(), view.palette.letter)
        val node = view.accessibilityNodeProvider.createAccessibilityNodeInfo(view.geometry.keys.indexOf(key))!!
        assertEquals(key.accessibilityLabel, node.contentDescription)
        assertTrue(node.isClickable)
        val emitted = mutableListOf<SurfaceGesture>()
        view.onGesture = emitted::add
        val down = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, key.bounds.centerX, key.bounds.centerY, 0)
        val up = MotionEvent.obtain(1, 2, MotionEvent.ACTION_UP, key.bounds.centerX, key.bounds.centerY, 0)
        view.onTouchEvent(down); view.onTouchEvent(up)
        down.recycle(); up.recycle()
        assertEquals(key, (emitted.single() as SurfaceGesture.Tap).key)
        emitted.clear()
        assertTrue(view.accessibilityNodeProvider.performAction(view.geometry.keys.indexOf(key), AccessibilityNodeInfo.ACTION_CLICK, null))
        assertEquals(key, (emitted.single() as SurfaceGesture.Tap).key)
    }

    @Test fun languageAndPaletteRebuildIndependentGeometry() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 330, 250)
        view.language = KeyboardLanguage.ENGLISH
        view.darkMode = false
        assertEquals("English", view.geometry.keys.single { it.kind == KeyKind.SPACE }.label.trim())
        assertEquals(0xfff0f1f4.toInt(), view.palette.surface)
    }

    @Test fun pageAndLanguageChangesRebuildTapAndAccessibilityTargets() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 337, 241)
        view.symbolMode = true
        view.darkMode = false
        view.draw(Canvas(Bitmap.createBitmap(337, 241, Bitmap.Config.ARGB_8888)))
        assertEquals(0xfff0f1f4.toInt(), view.palette.surface)
        val toggle = view.geometry.keys.single { it.kind == KeyKind.SYMBOLS }
        assertEquals("ABC", toggle.label)
        assertEquals("Letters", toggle.accessibilityLabel)
        val emitted = mutableListOf<SurfaceGesture>()
        view.onGesture = emitted::add
        val at = view.geometry.keys.single { it.label == "@" }
        val index = view.geometry.keys.indexOf(at)
        val node = view.accessibilityNodeProvider.createAccessibilityNodeInfo(index)!!
        val bounds = android.graphics.Rect()
        node.getBoundsInParent(bounds)
        assertEquals(at.bounds.left, bounds.left)
        assertEquals(at.accessibilityLabel, node.contentDescription)
        assertTrue(view.accessibilityNodeProvider.performAction(index, AccessibilityNodeInfo.ACTION_CLICK, null))
        assertEquals("@", (emitted.single() as SurfaceGesture.Tap).key.label)
        emitted.clear()
        val down = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, toggle.bounds.centerX, toggle.bounds.centerY, 0)
        val up = MotionEvent.obtain(1, 2, MotionEvent.ACTION_UP, toggle.bounds.centerX, toggle.bounds.centerY, 0)
        view.onTouchEvent(down); view.onTouchEvent(up)
        down.recycle(); up.recycle()
        assertEquals(KeyKind.SYMBOLS, (emitted.single() as SurfaceGesture.Tap).key.kind)
        assertTrue(view.symbolMode) // Only the service changes pages.
        view.language = KeyboardLanguage.ENGLISH
        assertEquals("EN", view.geometry.keys.single { it.kind == KeyKind.LANGUAGE }.label)
        view.symbolMode = false
        assertEquals("?123", view.geometry.keys.single { it.kind == KeyKind.SYMBOLS }.label)
        assertTrue(view.geometry.keys.any { it.kind == KeyKind.LETTER })
    }
}
