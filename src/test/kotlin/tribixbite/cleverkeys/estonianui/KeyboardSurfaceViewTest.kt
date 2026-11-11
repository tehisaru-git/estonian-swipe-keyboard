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
    @Test fun activeSwipeDrawsAVisibleTrailAndReleaseClearsIt() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 330, 250)
        val from = view.geometry.rows[1][0].bounds
        val to = view.geometry.rows[1][5].bounds
        val down = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, from.centerX, from.centerY, 0)
        val move = MotionEvent.obtain(1, 20, MotionEvent.ACTION_MOVE, to.centerX, to.centerY, 0)
        view.onTouchEvent(down); view.onTouchEvent(move)
        assertTrue(view.activeTrailPointCount > 1)
        val up = MotionEvent.obtain(1, 25, MotionEvent.ACTION_UP, to.centerX, to.centerY, 0)
        view.onTouchEvent(up)
        assertEquals(0, view.activeTrailPointCount)
        down.recycle(); move.recycle(); up.recycle()
    }

    @Test fun backspaceHoldRepeatsWhileFingerIsDownAndStopsOnRelease() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 330, 250)
        val events = mutableListOf<SurfaceGesture>()
        view.onGesture = events::add
        val key = view.geometry.keys.first { it.kind == KeyKind.BACKSPACE }.bounds
        val down = MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, key.centerX, key.centerY, 0)
        view.onTouchEvent(down); down.recycle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(650, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertTrue(events.count { it is SurfaceGesture.RepeatBackspace } >= 2)
        val up = MotionEvent.obtain(1, 651, MotionEvent.ACTION_UP, key.centerX, key.centerY, 0)
        view.onTouchEvent(up); up.recycle()
        assertTrue(events.none { it is SurfaceGesture.Tap })
        val count = events.size
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(count, events.size)
    }

    @Test fun drawUsesGeometryAndDarkPaletteWhileTouchAndAccessibilityShareTargets() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 330, 250)
        view.darkMode = true
        val bitmap = Bitmap.createBitmap(330, 250, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertEquals(0xff202127.toInt(), view.palette.surface)
        val key = view.geometry.rows[1][0]
        assertEquals(0xff444851.toInt(), view.palette.letter)
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
        assertEquals(0xffeef3fb.toInt(), view.palette.surface)
    }

    @Test fun pageAndLanguageChangesRebuildTapAndAccessibilityTargets() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 337, 241)
        view.symbolMode = true
        view.darkMode = false
        view.draw(Canvas(Bitmap.createBitmap(337, 241, Bitmap.Config.ARGB_8888)))
        assertEquals(0xffeef3fb.toInt(), view.palette.surface)
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
        assertFalse(view.geometry.keys.any { it.kind == KeyKind.LANGUAGE })
        view.symbolMode = false
        assertEquals("?123", view.geometry.keys.single { it.kind == KeyKind.SYMBOLS }.label)
        assertTrue(view.geometry.keys.any { it.kind == KeyKind.LETTER })
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun lightSurfacePaintsWhiteLettersMutedModifiersAndSaturatedEnter() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 1080, 700)
        view.darkMode = false
        val bitmap = Bitmap.createBitmap(1080, 700, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertEquals(0xffeef3fb.toInt(), bitmap.getPixel(0, 0))
        assertEquals(0xffffffff.toInt(), view.palette.letter)
        assertEquals(0xffdfe3e7.toInt(), view.palette.modifier)
        assertEquals(0xff0b57d0.toInt(), view.palette.enter)
        val shift = view.geometry.keys.single { it.kind == KeyKind.SHIFT }.bounds
        val enter = view.geometry.keys.single { it.kind == KeyKind.ENTER }.bounds
        assertEquals(view.palette.modifier, bitmap.getPixel(shift.left + 15, shift.top + 15))
        assertEquals(view.palette.enter, bitmap.getPixel(enter.centerX.toInt(), enter.top + 20))
        assertEquals(view.palette.surface, bitmap.getPixel(enter.left + 2, enter.top + 2)) // pill corner
    }
}
