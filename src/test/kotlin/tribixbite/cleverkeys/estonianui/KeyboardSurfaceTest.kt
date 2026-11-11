package tribixbite.cleverkeys.estonianui

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo

class KeyboardSurfaceTest {
    @Test fun estonianRowsHaveDistinctLanguageAndCompleteTargets() {
        val et = KeyboardGeometry(1080, 700, KeyboardLanguage.ESTONIAN)
        assertEquals(listOf("1234567890", "qwertyuiopü", "asdfghjklöä", "⇧zxcvbnmõ⌫", "?123,☺ET Eesti .↵"), et.rows.map { row -> row.joinToString("") { it.label } })
        assertEquals("qwertyuiop", KeyboardGeometry(1080, 700, KeyboardLanguage.ENGLISH).rows[1].joinToString("") { it.label })
        for (key in et.keys) {
            assertSame(key, et.hit(key.bounds.centerX, key.bounds.centerY))
            assertTrue(key.bounds.left >= 0 && key.bounds.right <= 1080)
            assertTrue(key.bounds.top >= 0 && key.bounds.bottom <= 700)
        }
        assertNull(et.hit(-1f, 10f))
        assertNull(et.hit(100f, 701f))
    }

    @Test fun ownershipCoversEdgesAndGapsAtOddSizes() {
        val geometry = KeyboardGeometry(301, 233, KeyboardLanguage.ESTONIAN)
        for (y in 0 until geometry.height) for (x in 0 until geometry.width) {
            val owner = geometry.hit(x.toFloat(), y.toFloat())
            assertNotNull("$x,$y", owner)
            assertTrue(owner!!.bounds.contains(x.toFloat(), y.toFloat()))
        }
        assertNull(geometry.hit(Float.NaN, 10f))
    }

    @Test fun languageAndEmojiAreDistinctTargetsOnBothPages() {
        for (language in KeyboardLanguage.values()) for (symbols in listOf(false, true)) {
            val geometry = KeyboardGeometry(337, 241, language, symbolMode = symbols)
            val bottom = geometry.rows.last()
            val switch = bottom.single { it.kind == KeyKind.LANGUAGE }
            val emoji = bottom.single { it.kind == KeyKind.EMOJI }
            assertEquals(if (language == KeyboardLanguage.ESTONIAN) "ET" else "EN", switch.label)
            assertEquals("Emoji", emoji.accessibilityLabel)
            assertEquals("Switch language", switch.accessibilityLabel)
            assertSame(switch, geometry.hit(switch.bounds.centerX, switch.bounds.centerY))
            assertSame(emoji, geometry.hit(emoji.bounds.centerX, emoji.bounds.centerY))
            assertTrue(switch.bounds.right <= bottom.single { it.kind == KeyKind.SPACE }.bounds.left)
        }
    }

    @Test fun symbolsPageContainsEssentialSymbolsAndHasCompleteGeometry() {
        val geometry = KeyboardGeometry(337, 241, KeyboardLanguage.ESTONIAN, symbolMode = true)
        assertEquals("ABC", geometry.keys.single { it.kind == KeyKind.SYMBOLS }.label)
        for (symbol in listOf("@", "#", "€", "!", "?", ",", ".")) {
            assertTrue("missing $symbol", geometry.keys.any { it.label == symbol })
        }
        assertFalse(geometry.keys.any { it.kind == KeyKind.LETTER })
        for (y in 0 until geometry.height) for (x in 0 until geometry.width) {
            assertNotNull("$x,$y", geometry.hit(x.toFloat(), y.toFloat()))
        }
    }

    @Test fun symbolDragCannotEmitSwipeButSymbolTapRetainsItsPage() {
        val geometry = KeyboardGeometry(337, 241, KeyboardLanguage.ENGLISH, symbolMode = true)
        val emitted = mutableListOf<SurfaceGesture>()
        val controller = SurfaceGestureController(geometry, emitted::add)
        val first = geometry.rows[1][0].bounds
        val second = geometry.rows[1][1].bounds
        controller.down(first.centerX, first.centerY, 1)
        controller.move(second.centerX, second.centerY, 2)
        controller.up(second.centerX, second.centerY, 3)
        assertTrue(emitted.isEmpty())
        controller.down(first.centerX, first.centerY, 4)
        controller.up(first.centerX, first.centerY, 5)
        assertEquals(geometry.rows[1][0], (emitted.single() as SurfaceGesture.Tap).key)
    }

    @Test fun liftDispatchesRawSwipeWithMonotonicTimesAndLanguage() {
        val geometry = KeyboardGeometry(330, 250, KeyboardLanguage.ESTONIAN)
        val emitted = mutableListOf<SurfaceGesture>()
        val input = SurfaceGestureController(geometry, emitted::add)
        val q = geometry.rows[1][0].bounds
        val w = geometry.rows[1][1].bounds
        input.down(q.centerX, q.centerY, 100)
        input.move(w.centerX, w.centerY, 90)
        input.up(w.centerX, w.centerY, 110)
        val swipe = emitted.single() as SurfaceGesture.Swipe
        assertEquals(KeyboardLanguage.ESTONIAN, swipe.language)
        assertEquals(listOf(100L, 100L, 110L), swipe.samples.map { it.timeMs })
        assertEquals(listOf("q", "w", "w"), swipe.samples.map { it.key?.label })
    }

    @Test fun smallSlipAcrossLetterBoundaryIsStillOneTap() {
        val geometry = KeyboardGeometry(330, 250, KeyboardLanguage.ESTONIAN)
        val emitted = mutableListOf<SurfaceGesture>()
        val input = SurfaceGestureController(geometry, emitted::add)
        val q = geometry.rows[1][0].bounds
        val y = q.centerY
        input.down(q.right - 2f, y, 100)
        input.move(q.right + 1f, y, 105)
        input.up(q.right + 2f, y, 108)
        assertEquals("q", (emitted.single() as SurfaceGesture.Tap).key.label)
    }

    @Test fun tapAndCancelDoNotInventSwipeOrDispatch() {
        val geometry = KeyboardGeometry(330, 250, KeyboardLanguage.ENGLISH)
        val emitted = mutableListOf<SurfaceGesture>()
        val input = SurfaceGestureController(geometry, emitted::add)
        val key = geometry.rows[2][0].bounds
        input.down(key.centerX, key.centerY, 1)
        input.up(key.centerX, key.centerY, 2)
        assertEquals("a", (emitted.single() as SurfaceGesture.Tap).key.label)
        input.down(key.centerX, key.centerY, 3)
        input.move(geometry.rows[2][1].bounds.centerX, key.centerY, 4)
        input.cancel()
        input.up(key.centerX, key.centerY, 5)
        assertEquals(1, emitted.size)
    }

    @Test fun longHoldWithWithinKeyFingerWobbleKeepsAlternative() {
        val geometry = KeyboardGeometry(330, 250, KeyboardLanguage.ESTONIAN)
        val emitted = mutableListOf<SurfaceGesture>()
        val input = SurfaceGestureController(geometry, emitted::add)
        val s = geometry.rows[2][1].bounds
        input.down(s.centerX, s.centerY, 1)
        input.move(s.centerX, s.centerY + 20f, 280)
        input.move(s.centerX, s.centerY, 560)
        input.up(s.centerX, s.centerY, 650)
        assertEquals("Hold", emitted.single()::class.simpleName)
    }

    @Test fun holdingEstonianSProducesAnAlternativeInsteadOfOrdinaryTap() {
        val geometry = KeyboardGeometry(330, 250, KeyboardLanguage.ESTONIAN)
        val emitted = mutableListOf<SurfaceGesture>()
        val input = SurfaceGestureController(geometry, emitted::add)
        val s = geometry.rows[2][1].bounds
        input.down(s.centerX, s.centerY, 1)
        input.up(s.centerX, s.centerY, 650)
        assertEquals("Hold", emitted.single()::class.simpleName)
    }

    @Test fun qualifiedSwipeStillDecodesWhenFingerLiftsJustOutsideKeySurface() {
        val geometry = KeyboardGeometry(330, 250, KeyboardLanguage.ESTONIAN)
        val emitted = mutableListOf<SurfaceGesture>()
        val input = SurfaceGestureController(geometry, emitted::add)
        val q = geometry.rows[1][0].bounds
        val w = geometry.rows[1][1].bounds
        input.down(q.centerX, q.centerY, 1)
        input.move(w.centerX, w.centerY, 10)
        input.up(-1f, w.centerY, 20)
        assertTrue(emitted.single() is SurfaceGesture.Swipe)
    }

    @Test fun liftingOutsideSurfaceCancelsAndStartsMustHitKey() {
        val geometry = KeyboardGeometry(330, 250, KeyboardLanguage.ENGLISH)
        val emitted = mutableListOf<SurfaceGesture>()
        val input = SurfaceGestureController(geometry, emitted::add)
        input.down(-1f, 20f, 1)
        input.up(20f, 20f, 2)
        val key = geometry.rows[1][0].bounds
        input.down(key.centerX, key.centerY, 3)
        input.up(-1f, key.centerY, 4)
        assertTrue(emitted.isEmpty())
    }
}
