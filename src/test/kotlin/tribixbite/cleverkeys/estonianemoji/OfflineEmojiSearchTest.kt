package tribixbite.cleverkeys.estonianemoji

import java.io.File
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class OfflineEmojiSearchTest {
    private val asset = File("src/main/assets/emoji_keywords.tsv")
    private fun index() = asset.inputStream().use { OfflineEmojiSearch.load(it) }

    @Test fun packagedAssetRetainsEveryExactKeywordFirstResult() {
        val search = index()
        var checked = 0
        asset.forEachLine { line ->
            val fields = line.split('\t', limit = 2)
            if (fields.size == 2 && fields[0].isNotEmpty()) {
                assertEquals(fields[0], fields[1].split(',').first(), search.search(fields[0], 1).firstOrNull())
                checked++
            }
        }
        assertTrue(checked > 15000)
    }

    @Test fun packagedEstonianAndEnglishAliasesWorkWithoutNetwork() {
        val search = index()
        assertEquals("❤️", search.search("süda", 1).first())
        assertEquals("❤️", search.search("suda", 1).first())
        assertEquals("🍎", search.search("ÕUN", 1).first())
        assertTrue(search.search("heart", 30).contains("❤️"))
        assertEquals("😺", search.search("cat", 1).first())
    }

    @Test fun exactPrecedesPrefixAndTypoOnlyRunsWhenNoPrefixMatches() {
        val data = "cat\t🐈,😺\ncategory\t📁\ncar\t🚗\n"
        val search = OfflineEmojiSearch.load(ByteArrayInputStream(data.toByteArray()))
        assertEquals(listOf("🐈", "😺", "📁"), search.search("cat", 3))
        assertEquals(listOf("🐈", "😺", "📁", "🚗"), search.search("ca", 4)) // source order, not alphabetical
        assertEquals(0, search.lastFuzzyVisitedNodes)
        assertEquals(emptyList<String>(), search.search("", 10))
        assertEquals(emptyList<String>(), search.search("zzzz", 0))
    }

    @Test fun editDistanceOneSupportsInsertionDeletionSubstitutionAndSwap() {
        val search = index()
        for (typo in listOf("heartt", "hert", "heqrt", "herat")) {
            assertTrue("$typo", search.search(typo, 40).contains("❤️"))
            assertTrue(search.lastFuzzyVisitedNodes in 1..2048)
        }
        assertTrue(search.search("qz").isEmpty())
        assertEquals(0, search.lastFuzzyVisitedNodes)
        repeat(100) {
            assertTrue(search.search("zzzzzzzzzzzzzzzz").isEmpty())
            assertTrue(search.lastFuzzyVisitedNodes in 1..2048)
        }
    }

    @Test fun duplicateHeavyPrefixCannotTraverseEntireIndex() {
        val data = buildString {
            for (i in 0 until 5000) append("a").append(i.toString().padStart(4, '0')).append("\t😀\n")
        }
        val search = OfflineEmojiSearch.load(ByteArrayInputStream(data.toByteArray()))
        assertEquals(listOf("😀"), search.search("a", 50))
        assertTrue(search.lastPrefixVisitedNodes in 1..2048)
    }

    @Test fun duplicatesMergeInFileOrderAndDeduplicateResults() {
        val data = "heart\t❤️,🧡\nheart\t❤️,💙\nhearts\t💙,💚\n"
        val search = OfflineEmojiSearch.load(ByteArrayInputStream(data.toByteArray()))
        assertEquals(listOf("❤️", "🧡", "💙", "💚"), search.search("heart"))
    }
}
