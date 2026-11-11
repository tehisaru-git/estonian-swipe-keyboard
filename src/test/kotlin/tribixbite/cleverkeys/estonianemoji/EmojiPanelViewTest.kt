package tribixbite.cleverkeys.estonianemoji

import android.widget.ListView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EmojiPanelViewTest {
    private fun panel() = EmojiPanelView(RuntimeEnvironment.getApplication())

    @Before fun resetRecents() {
        RuntimeEnvironment.getApplication().getSharedPreferences("emoji_browser_recents", 0)
            .edit().clear().commit()
    }

    @Test fun browseStartsWithSearchCategoriesAndScrollableLabeledEmojiRows() {
        val panel = panel()
        assertFalse(panel.isSearchMode)
        assertTrue(panel.getChildAt(0) is LinearLayout)
        val list = panel.getChildAt(1) as ListView
        assertTrue(list.adapter.getView(0, null, list) is TextView) // section header
        assertTrue(list.adapter.getView(1, null, list) is LinearLayout) // nine emoji cells
        assertEquals(listOf("Smileys and emotions"), panel.browseSectionTitles())
        assertTrue(panel.visibleEmoji.size > 40)
        assertFalse(panel.containsEditableTextField())
    }

    @Test fun switchingCategoriesShowsDifferentBrowsableEmoji() {
        val panel = panel()
        val faces = panel.visibleEmoji
        panel.selectCategory(2)
        assertNotEquals(faces, panel.visibleEmoji)
        assertTrue(panel.visibleEmoji.size > 10)
        panel.selectCategory(1)
        assertTrue(panel.visibleEmoji.size > 100)
        panel.selectCategory(11)
        assertTrue(panel.visibleEmoji.size > 50)
        assertTrue(":)" in panel.visibleEmoji)
        assertFalse(panel.visibleEmoji.any { it.contains('ツ') })
        panel.selectCategory(12)
        assertTrue(panel.visibleEmoji.size > 30)
        assertTrue(panel.visibleEmoji.any { it.contains('ツ') })
        assertFalse(":)" in panel.visibleEmoji)
        panel.selectCategory(10)
        assertEquals(7, panel.visibleEmoji.size)
    }

    @Test fun searchToggleCompactsPanelAndRetainsOfflineLookup() {
        val panel = panel()
        val modes = mutableListOf<Boolean>()
        panel.onSearchModeChanged = modes::add
        panel.setSearchIndex(OfflineEmojiSearch.load(ByteArrayInputStream("süda\t❤️,💙\n".toByteArray())))
        panel.setSearchMode(true)
        panel.appendQuery("sü")
        assertEquals(listOf("❤️", "💙"), panel.visibleEmoji)
        assertEquals("sü", panel.query)
        assertEquals((56 * panel.resources.displayMetrics.density).toInt(), panel.getChildAt(1).layoutParams.height)
        panel.backspaceQuery()
        assertEquals("s", panel.query)
        panel.setSearchMode(false)
        assertEquals(listOf(true, false), modes)
        assertEquals("", panel.query)
        assertFalse(panel.containsEditableTextField())
    }

    @Test fun pickedEmojiIsSavedLocallyAndRecentCanBeCleared() {
        val panel = panel()
        val picked = mutableListOf<String>()
        panel.onEmojiPicked = picked::add
        panel.pickForTest("😀")
        assertEquals(listOf("😀"), picked)
        panel.selectCategory(0)
        assertEquals("😀", panel.recentEmoji.first())
        assertEquals(listOf("Recently used", "Smileys and emotions"), panel.browseSectionTitles())
        val recreated = panel()
        assertEquals("😀", recreated.recentEmoji.first())
        recreated.clearRecent()
        assertTrue(recreated.recentEmoji.isEmpty())
        assertEquals(listOf("Smileys and emotions"), recreated.browseSectionTitles())
    }

    @Test fun failedEditorCommitDoesNotPolluteRecents() {
        val panel = panel()
        panel.onEmojiPicked = { false }
        panel.pickForTest("😀")
        assertTrue(panel.recentEmoji.isEmpty())
        panel.selectCategory(0)
        assertEquals(listOf("Smileys and emotions"), panel.browseSectionTitles())
    }

    @Test fun closeResetsSearchButDoesNotDiscardRecents() {
        val panel = panel()
        var closed = false
        panel.onClose = { closed = true }
        panel.setSearchMode(true)
        panel.appendQuery("cat")
        panel.close()
        assertTrue(closed)
        assertEquals("", panel.query)
        assertFalse(panel.isSearchMode)
    }

    @Test fun searchResultsAreOriginalEmojiRowsNotPlatformButtons() {
        val panel = panel()
        panel.setSearchMode(true)
        val list = panel.getChildAt(1) as ListView
        val row = list.adapter.getView(0, null, list) as LinearLayout
        assertTrue(row.getChildAt(0) is TextView)
    }
}
