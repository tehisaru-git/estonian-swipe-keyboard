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

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun longSwipeKeepsRecentTailVisibleAndEvictsOldPoints() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 660, 500)
        view.darkMode = false
        val start = view.geometry.rows[1][0].bounds
        val oldX = start.centerX + 75f
        val oldY = start.centerY.toFloat()
        val newX = view.geometry.rows[1][6].bounds.centerX.toFloat()
        val newY = view.geometry.rows[2][3].bounds.centerY.toFloat()
        val baseline = Bitmap.createBitmap(660, 500, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(baseline))
        fun send(action: Int, x: Float, y: Float, time: Long) {
            MotionEvent.obtain(1, time, action, x, y, 0).also {
                view.onTouchEvent(it)
                it.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, start.centerX.toFloat(), oldY, 1)
        send(MotionEvent.ACTION_MOVE, oldX, oldY, 2)
        for (i in 0 until 300) {
            send(MotionEvent.ACTION_MOVE, oldX + 20f + (i % 12) * 8f, oldY, i + 3L)
        }
        send(MotionEvent.ACTION_MOVE, newX, newY, 303)
        val image = Bitmap.createBitmap(660, 500, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        assertTrue("trail memory must stay bounded", view.activeTrailPointCount <= 128)
        assertEquals("old path must be erased", baseline.getPixel(oldX.toInt(), oldY.toInt()), image.getPixel(oldX.toInt(), oldY.toInt()))
        assertNotEquals("new tail must be painted", baseline.getPixel(newX.toInt(), newY.toInt()), image.getPixel(newX.toInt(), newY.toInt()))
        send(MotionEvent.ACTION_CANCEL, newX, newY, 304)
        assertEquals(0, view.activeTrailPointCount)
    }

    @Test fun heldMOpensItsHintWhileDownAndCommitsQuestionMarkOnlyOnRelease() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 660, 500)
        val events = mutableListOf<SurfaceGesture>()
        view.onGesture = events::add
        val m = view.geometry.keys.single { it.label == "m" }.bounds
        fun touch(action: Int, x: Float, y: Float, time: Long) = MotionEvent.obtain(1, time, action, x, y, 0).also {
            view.onTouchEvent(it); it.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, m.centerX, m.centerY, 1)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("?", view.activeLongPressValue)
        assertTrue(events.isEmpty())
        touch(MotionEvent.ACTION_UP, m.centerX, m.centerY, 501)
        assertEquals("?", (events.single() as SurfaceGesture.LongPress).value)
        assertNull(view.activeLongPressValue)
    }

    @Test fun gboardLikeDefaultHoldOpensAt300MsNotBefore() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 660, 500)
        val events = mutableListOf<SurfaceGesture>()
        view.onGesture = events::add
        val m = view.geometry.keys.single { it.label == "m" }.bounds
        MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, m.centerX, m.centerY, 0).also {
            view.onTouchEvent(it); it.recycle()
        }
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        looper.idleFor(299, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNull("must not claim a short tap", view.activeLongPressValue)
        looper.idleFor(1, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("?", view.activeLongPressValue)
        assertTrue(events.isEmpty())
        MotionEvent.obtain(1, 301, MotionEvent.ACTION_UP, m.centerX, m.centerY, 0).also {
            view.onTouchEvent(it); it.recycle()
        }
        assertEquals("?", (events.single() as SurfaceGesture.LongPress).value)
    }

    @Test fun customHoldDurationDelaysPopupWithoutChangingCommittedAlternative() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 660, 500)
        view.holdDurationMs = 500
        val events = mutableListOf<SurfaceGesture>()
        view.onGesture = events::add
        val m = view.geometry.keys.single { it.label == "m" }.bounds
        MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, m.centerX, m.centerY, 0).also {
            view.onTouchEvent(it); it.recycle()
        }
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        looper.idleFor(499, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNull(view.activeLongPressValue)
        looper.idleFor(1, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("?", view.activeLongPressValue)
        MotionEvent.obtain(1, 501, MotionEvent.ACTION_UP, m.centerX, m.centerY, 0).also {
            view.onTouchEvent(it); it.recycle()
        }
        assertEquals("?", (events.single() as SurfaceGesture.LongPress).value)
    }

    @Test fun shiftStateChangesVisibleKeyLabelsAndAccessibleDescription() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 660, 500)
        val provider = view.accessibilityNodeProvider
        val a = view.geometry.keys.indexOfFirst { it.label == "a" }
        val shift = view.geometry.keys.indexOfFirst { it.kind == KeyKind.SHIFT }
        assertEquals("a", provider.createAccessibilityNodeInfo(a)!!.contentDescription.toString())
        view.shiftMode = ShiftMode.ONCE
        assertEquals("A", provider.createAccessibilityNodeInfo(a)!!.contentDescription.toString())
        assertEquals("Shift on", provider.createAccessibilityNodeInfo(shift)!!.contentDescription.toString())
        view.shiftMode = ShiftMode.LOCKED
        assertEquals("Caps lock on", provider.createAccessibilityNodeInfo(shift)!!.contentDescription.toString())
    }

    @Test fun slidingHeldGSelectsAccentWithoutSwipingOrCommittingTwice() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 660, 500)
        val events = mutableListOf<SurfaceGesture>()
        view.onGesture = events::add
        val g = view.geometry.keys.single { it.label == "g" }.bounds
        fun touch(action: Int, x: Float, y: Float, time: Long) = MotionEvent.obtain(1, time, action, x, y, 0).also {
            view.onTouchEvent(it); it.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, g.centerX, g.centerY, 1)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("&", view.activeLongPressValue)
        val left = g.centerX - 54f * view.resources.displayMetrics.density
        touch(MotionEvent.ACTION_MOVE, left, g.centerY, 510)
        assertEquals("ğ", view.activeLongPressValue)
        touch(MotionEvent.ACTION_UP, left, g.centerY, 520)
        assertEquals("ğ", (events.single() as SurfaceGesture.LongPress).value)
    }

    @Test fun movingAcrossLettersBeforeHoldStillProducesSwipeAndNeverPopup() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 660, 500)
        val events = mutableListOf<SurfaceGesture>()
        view.onGesture = events::add
        val g = view.geometry.keys.single { it.label == "g" }.bounds
        val h = view.geometry.keys.single { it.label == "h" }.bounds
        MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, g.centerX, g.centerY, 0).also { view.onTouchEvent(it); it.recycle() }
        MotionEvent.obtain(1, 80, MotionEvent.ACTION_MOVE, h.centerX, h.centerY, 0).also { view.onTouchEvent(it); it.recycle() }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNull(view.activeLongPressValue)
        MotionEvent.obtain(1, 581, MotionEvent.ACTION_UP, h.centerX, h.centerY, 0).also { view.onTouchEvent(it); it.recycle() }
        assertTrue(events.single() is SurfaceGesture.Swipe)
    }

    @Test fun crossingAKeyEdgeBeforeThresholdCannotTriggerOldKeysHold() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 660, 500)
        val emitted = mutableListOf<SurfaceGesture>()
        view.onGesture = emitted::add
        val g = view.geometry.keys.single { it.label == "g" }.bounds
        val y = g.centerY
        fun send(action: Int, x: Float, time: Long) = MotionEvent.obtain(1, time, action, x, y, 0).also {
            view.onTouchEvent(it); it.recycle()
        }
        send(MotionEvent.ACTION_DOWN, g.right - 2f, 1)
        send(MotionEvent.ACTION_MOVE, g.right + 1f, 80)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNull(view.activeLongPressValue)
        send(MotionEvent.ACTION_UP, g.right + 1f, 600)
        assertEquals("g", (emitted.single() as SurfaceGesture.Tap).key.label)
    }

    @Test fun deliberateFarSwipeAfterPopupStillReachesRecognizer() {
        val view = KeyboardSurfaceView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 660, 500)
        val emitted = mutableListOf<SurfaceGesture>()
        view.onGesture = emitted::add
        val g = view.geometry.keys.single { it.label == "g" }.bounds
        val l = view.geometry.keys.single { it.label == "l" }.bounds
        fun send(action: Int, x: Float, time: Long) = MotionEvent.obtain(1, time, action, x, g.centerY, 0).also {
            view.onTouchEvent(it); it.recycle()
        }
        send(MotionEvent.ACTION_DOWN, g.centerX, 1)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("&", view.activeLongPressValue)
        send(MotionEvent.ACTION_MOVE, l.centerX, 510)
        assertNull(view.activeLongPressValue)
        send(MotionEvent.ACTION_UP, l.centerX, 520)
        assertTrue(emitted.single() is SurfaceGesture.Swipe)
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
