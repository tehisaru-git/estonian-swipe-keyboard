package tribixbite.cleverkeys.estonianemoji

import android.content.Context
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An immutable, entirely local keyword index. Construct off the IME thread, then share it. */
class OfflineEmojiSearch private constructor(
    private val root: Node,
    private val literal: Map<String, List<String>>
) {
    private class Node {
        val children = LinkedHashMap<Char, Node>()
        val emojis = ArrayList<String>()
    }

    /** Number of trie nodes visited by the most recent fallback (diagnostics only). */
    @Volatile var lastFuzzyVisitedNodes: Int = 0
        private set
    @Volatile var lastPrefixVisitedNodes: Int = 0
        private set

    /** Exact keyword first, then prefixes; one-edit fallback only if those miss. */
    fun search(query: String, limit: Int = 50): List<String> {
        lastFuzzyVisitedNodes = 0
        lastPrefixVisitedNodes = 0
        if (limit <= 0 || query.isBlank()) return emptyList()
        val raw = query.trim().lowercase(Locale.ROOT)
        val folded = fold(raw)
        if (folded.isEmpty()) return emptyList()
        val result = LinkedHashSet<String>()
        fun add(items: List<String>) {
            for (emoji in items) {
                if (result.size >= limit) break
                result.add(emoji)
            }
        }
        // Preserve the packaged English/Estonian exact ranking even if accent-folded
        // aliases collide with one another. Repeated rows retain their original order.
        literal[raw]?.let(::add)
        var node: Node? = root
        for (letter in folded) node = node?.children?.get(letter)
        if (node != null) {
            var remaining = 2048
            fun collect(start: Node) {
                if (result.size >= limit || remaining <= 0) return
                remaining--
                lastPrefixVisitedNodes++
                add(start.emojis)
                for (child in start.children.values) {
                    if (result.size >= limit || remaining <= 0) break
                    collect(child)
                }
            }
            collect(node)
        }
        if (result.isNotEmpty() || folded.length !in 4..64) return result.toList()

        // Explore only one-edit paths, not the full index. Descendant collection
        // shares the same hard node budget (including duplicate-heavy subtrees).
        var remaining = 2048
        fun visitNode(): Boolean {
            if (remaining <= 0 || result.size >= limit) return false
            remaining--
            return true
        }
        fun collect(start: Node) {
            if (!visitNode()) return
            add(start.emojis)
            for (child in start.children.values) {
                if (remaining <= 0 || result.size >= limit) break
                collect(child)
            }
        }
        fun visit(start: Node, position: Int, edits: Int) {
            if (!visitNode()) return
            if (position == folded.length) {
                if (edits == 0) collect(start)
                else for (child in start.children.values) {
                    if (remaining <= 0 || result.size >= limit) break
                    collect(child) // missing final query letter
                }
                return
            }
            val expected = folded[position]
            start.children[expected]?.let { visit(it, position + 1, edits) }
            if (edits == 0 || remaining <= 0 || result.size >= limit) return
            visit(start, position + 1, 0) // extra query letter
            for ((letter, child) in start.children) {
                if (remaining <= 0 || result.size >= limit) break
                if (letter != expected) visit(child, position + 1, 0) // substitution
                visit(child, position, 0) // missing query letter
            }
            if (position + 1 < folded.length) {
                start.children[folded[position + 1]]?.children?.get(expected)
                    ?.let { visit(it, position + 2, 0) } // adjacent transposition
            }
        }
        visit(root, 0, 1)
        lastFuzzyVisitedNodes = 2048 - remaining
        return result.toList()
    }

    companion object {
        private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

        /** Read the packaged UTF-8 TSV once, on a worker thread. Never reads editor text. */
        suspend fun fromAssets(context: Context): OfflineEmojiSearch = withContext(Dispatchers.IO) {
            context.assets.open("emoji_keywords.tsv").use(::load)
        }

        /** InputStream variant supports local tests and alternate licensed TSV assets. */
        fun load(input: InputStream): OfflineEmojiSearch {
            val root = Node()
            val literal = LinkedHashMap<String, MutableList<String>>()
            input.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEachLine
                val keyword = line.substring(0, tab).trim().lowercase(Locale.ROOT)
                if (keyword.isEmpty()) return@forEachLine
                val emojis = line.substring(tab + 1).split(',').filter { it.isNotEmpty() }
                literal.getOrPut(keyword) { ArrayList() }.addAll(emojis)
                var node = root
                for (letter in fold(keyword)) node = node.children.getOrPut(letter) { Node() }
                node.emojis.addAll(emojis)
            }
            return OfflineEmojiSearch(root, literal)
        }
    }
}
