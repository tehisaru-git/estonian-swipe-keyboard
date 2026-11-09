package tribixbite.cleverkeys

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmojiKeywordIndexTypoTest {
    @Test fun `misspelled English and Estonian queries find emoji after offline index loads`() = runBlocking {
        EmojiKeywordIndex.prewarm(ApplicationProvider.getApplicationContext())
        EmojiKeywordIndex.awaitReady()
        assertThat(EmojiKeywordIndex.isReady()).isTrue()
        assertThat(EmojiKeywordIndex.search("heartt")).contains("❤️") // extra query letter
        assertThat(EmojiKeywordIndex.search("hert")).contains("❤️") // missing query letter
        assertThat(EmojiKeywordIndex.search("herat")).contains("❤️") // adjacent swap
        assertThat(EmojiKeywordIndex.search("heart")).contains("❤️") // exact prefix first
        assertThat(EmojiKeywordIndex.search("südaa")).contains("❤️")
        assertThat(EmojiKeywordIndex.search("koes")).contains("🐕")
        assertThat(EmojiKeywordIndex.search("qz")).isEmpty() // short non-prefix queries do not fuzz
    }

    @Test fun `repeated unmatched searches stay empty without scanning every keyword`() = runBlocking {
        EmojiKeywordIndex.prewarm(ApplicationProvider.getApplicationContext())
        EmojiKeywordIndex.awaitReady()
        repeat(100) { assertThat(EmojiKeywordIndex.search("zzzzzzzzzzzzzzzz")).isEmpty() }
        assertThat(EmojiKeywordIndex.lastFuzzyVisitedNodes).isGreaterThan(0)
        assertThat(EmojiKeywordIndex.lastFuzzyVisitedNodes).isAtMost(2048)
    }
}
