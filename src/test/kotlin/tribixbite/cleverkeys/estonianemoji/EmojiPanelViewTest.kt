package tribixbite.cleverkeys.estonianemoji

import java.io.ByteArrayInputStream
import android.widget.Button
import android.widget.GridLayout
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EmojiPanelViewTest {
    private fun panel() = EmojiPanelView(RuntimeEnvironment.getApplication())

    @Test fun compactEmojiPanelShowsScrollableResultsWithoutGrowingIme() {
        val panel = panel()
        val scroll = panel.getChildAt(1) as ScrollView
        assertEquals((56 * panel.resources.displayMetrics.density).toInt(), scroll.layoutParams.height)
        assertTrue(scroll.getChildAt(0) is GridLayout)
    }

    @Test fun emojiTilesUseKeyboardStyledTextRatherThanPlatformButtons() {
        val panel = panel()
        val grid = ((panel.getChildAt(1) as ScrollView).getChildAt(0) as GridLayout)
        assertTrue(grid.childCount > 0)
        assertTrue(grid.getChildAt(0) is TextView)
        assertFalse(grid.getChildAt(0) is Button)
        assertNotNull(grid.getChildAt(0).background)
    }

    @Test fun queryUsesPackagedOfflineIndexAndIsNotAnEditableField() {
        val panel = panel()
        panel.setSearchIndex(OfflineEmojiSearch.load(ByteArrayInputStream(
            "süda\t❤️,💙\nheart\t❤️\n".toByteArray())))
        panel.appendQuery("sü")
        assertEquals("sü", panel.query)
        assertEquals(listOf("❤️", "💙"), panel.visibleEmoji)
        assertFalse(panel.containsEditableTextField())
        panel.backspaceQuery()
        assertEquals("s", panel.query)
        panel.clearQuery()
        assertEquals("", panel.query)
    }

    @Test fun pickedEmojiGoesOnlyThroughCallbackAndCloseCanResetQuery() {
        val panel = panel()
        val picked = mutableListOf<String>()
        var closed = false
        panel.onEmojiPicked = picked::add
        panel.onClose = { closed = true }
        panel.setSearchIndex(OfflineEmojiSearch.load(ByteArrayInputStream("süda\t❤️\n".toByteArray())))
        panel.appendQuery("süda")
        panel.pickForTest("❤️")
        assertEquals(listOf("❤️"), picked)
        panel.close()
        assertTrue(closed)
        assertEquals("", panel.query)
    }
}
