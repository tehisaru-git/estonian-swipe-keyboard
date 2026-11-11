package tribixbite.cleverkeys.estonianemoji

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import tribixbite.cleverkeys.R

/** Inline emoji browser with virtualized, labeled rows. It never focuses an editor inside the IME. */
class EmojiPanelView(context: Context) : LinearLayout(context) {
    /** Return true only after the editor confirms commit; failed picks are not recent. */
    var onEmojiPicked: ((String) -> Boolean)? = null
    var onClose: (() -> Unit)? = null
    /** Host resizes panel/keyboard: browse 334dp, search 100dp, stable total envelope. */
    var onSearchModeChanged: ((Boolean) -> Unit)? = null
    var isSearchMode = false
        private set
    var query: String = ""
        private set
    var visibleEmoji: List<String> = emptyList()
        private set
    internal val recentEmoji: List<String> get() = recent.toList()
    private var index: OfflineEmojiSearch? = null
    private val catalog = loadCatalog(context)
    private val preferences = context.getSharedPreferences("emoji_browser_recents", Context.MODE_PRIVATE)
    private val recent = preferences.getString("entries", "").orEmpty().split('\n')
        .filter { it.isNotEmpty() && it.length <= 32 && '\r' !in it }.distinct().take(48).toMutableList()
    private var category = 0
    private val list = ListView(context)
    private val categoryBar = HorizontalScrollView(context)
    private val browseBar = LinearLayout(context)
    private val queryBar = LinearLayout(context)
    private val footer = LinearLayout(context)
    private val queryLabel = TextView(context)
    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    private val surfaceColor = if (dark) 0xff202127.toInt() else 0xffeef3fb.toInt()
    private val tileColor = if (dark) 0xff303239.toInt() else Color.WHITE
    private val selectedColor = if (dark) 0xff586d98.toInt() else 0xffd3e3fd.toInt()
    private val labelColor = if (dark) 0xfff0f1f5.toInt() else 0xff202124.toInt()
    private val categories = listOf("◷" to "Recently used", "☻" to "Smileys and emotions", "♙" to "People",
        "♧" to "Animals and nature", "◉" to "Food and drink", "⚽" to "Activities",
        "▣" to "Travel and places", "♢" to "Objects", "⚑" to "Symbols",
        "▦" to "Flags", "✦" to "New emoji", ":-)" to "Emoticons", "(＾)" to "Kaomoji")

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Emojis(val entries: List<String>) : Row()
    }
    private var rows: List<Row> = emptyList()
    internal fun browseSectionTitles() = rows.filterIsInstance<Row.Header>().map { it.title }
    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = when (val row = rows[position]) {
            is Row.Header -> ((convertView as? TextView) ?: TextView(context).apply {
                textSize = 12f
                letterSpacing = 0.12f
                setTextColor(labelColor)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, 0, 0)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28))
            }).apply { text = row.title.uppercase() }
            is Row.Emojis -> ((convertView as? LinearLayout) ?: LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45))
                repeat(9) {
                    addView(TextView(context).apply {
                        textSize = 27f
                        gravity = Gravity.CENTER
                        background = rounded(tileColor)
                    }, LayoutParams(0, dp(43), 1f))
                }
            }).apply {
                for (i in 0 until 9) {
                    (getChildAt(i) as TextView).apply {
                        val emoji = row.entries.getOrNull(i)
                        text = emoji.orEmpty()
                        background = if (emoji == null) null else rounded(tileColor)
                        contentDescription = emoji?.let { "Emoji $it" }
                        isFocusable = emoji != null
                        isClickable = emoji != null
                        setOnClickListener(if (emoji == null) null else OnClickListener { pickForTest(emoji) })
                    }
                }
            }
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(surfaceColor)
        isFocusable = false
        browseBar.gravity = Gravity.CENTER_VERTICAL
        browseBar.addView(action("⌕  " + context.getString(R.string.swipe_emoji_browse_search), "Search emoji") {
            setSearchMode(true)
        }.apply { textSize = 16f; background = rounded(tileColor) }, LayoutParams(0, dp(42), 1.2f))
        categoryBar.isHorizontalScrollBarEnabled = false
        browseBar.addView(categoryBar, LayoutParams(0, dp(44), 1f))
        queryBar.gravity = Gravity.CENTER_VERTICAL
        queryLabel.setTextColor(labelColor)
        queryLabel.textSize = 16f
        queryLabel.gravity = Gravity.CENTER_VERTICAL
        queryLabel.setPadding(dp(12), 0, 0, 0)
        queryLabel.contentDescription = context.getString(R.string.swipe_emoji_query_description)
        queryBar.addView(queryLabel, LayoutParams(0, dp(44), 1f))
        queryBar.addView(action("⌫", context.getString(R.string.swipe_emoji_backspace)) { backspaceQuery() },
            LayoutParams(dp(48), dp(44)))
        queryBar.addView(action("×", "Browse emoji") { setSearchMode(false) }, LayoutParams(dp(48), dp(44)))
        addView(browseBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        list.divider = null
        list.setSelector(android.R.color.transparent)
        list.isVerticalScrollBarEnabled = false
        list.adapter = adapter
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        footer.addView(action("ABC", "Return to keyboard") { close() }, LayoutParams(dp(64), dp(38)))
        footer.addView(action("⌕", "Search emoji") { setSearchMode(true) }, LayoutParams(dp(60), dp(38)))
        footer.addView(action("Clear", "Clear recently used emoji") { clearRecent() }, LayoutParams(dp(74), dp(38)))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, dp(38)))
        render()
    }

    fun setSearchIndex(search: OfflineEmojiSearch?) { index = search; render() }
    fun selectCategory(number: Int) {
        if (number !in categories.indices) return
        if (isSearchMode) setSearchMode(false)
        category = number
        render()
        list.setSelection(0)
    }
    fun setSearchMode(enabled: Boolean) {
        if (isSearchMode == enabled) return
        isSearchMode = enabled
        query = ""
        removeViewAt(0)
        addView(if (enabled) queryBar else browseBar, 0,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        footer.visibility = if (enabled) GONE else VISIBLE
        list.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, if (enabled) dp(56) else 0,
            if (enabled) 0f else 1f)
        onSearchModeChanged?.invoke(enabled)
        render()
        list.setSelection(0)
    }
    fun appendQuery(text: String) {
        if (text.isEmpty()) return
        if (!isSearchMode) setSearchMode(true)
        query = (query + text.replace('\n', ' ').replace('\r', ' ')).take(64)
        render()
    }
    fun backspaceQuery() {
        if (query.isEmpty()) return
        query = query.substring(0, query.offsetByCodePoints(query.length, -1))
        render()
    }
    fun clearQuery() { query = ""; render() }
    fun close() { setSearchMode(false); clearQuery(); onClose?.invoke() }
    /** Only emoji choices, never surrounding editor text. */
    fun clearRecent() {
        recent.clear()
        preferences.edit().remove("entries").apply()
        if (category == 0) render()
    }
    internal fun pickForTest(emoji: String) {
        if (emoji !in visibleEmoji || onEmojiPicked?.invoke(emoji) != true) return
        if (emoji.length <= 32 && '\n' !in emoji && '\r' !in emoji) {
            recent.remove(emoji)
            recent.add(0, emoji)
            while (recent.size > 48) recent.removeAt(recent.lastIndex)
            preferences.edit().putString("entries", recent.joinToString("\n")).apply()
        }
        if (!isSearchMode && category == 0) render()
    }
    internal fun containsEditableTextField(): Boolean {
        fun scan(view: View): Boolean = when (view) {
            is EditText -> true
            is ViewGroup -> (0 until view.childCount).any { scan(view.getChildAt(it)) }
            else -> false
        }
        return scan(this)
    }
    private fun render() {
        queryLabel.text = if (query.isEmpty()) context.getString(R.string.swipe_emoji_search_hint) else query
        rows = if (isSearchMode) {
            visibleEmoji = if (query.isBlank()) recent.ifEmpty { catalog.getOrElse(1) { emptyList() } }.take(36)
                else index?.search(query, 36).orEmpty()
            visibleEmoji.chunked(9).map(Row::Emojis)
        } else {
            val sections = if (category == 0) {
                buildList {
                    if (recent.isNotEmpty()) add("Recently used" to recent.toList())
                    add("Smileys and emotions" to catalog.getOrElse(1) { emptyList() })
                }
            } else listOf(categories[category].second to emojisForCategory(category))
            visibleEmoji = sections.flatMap { it.second }
            sections.flatMap { (name, emojis) -> listOf(Row.Header(name)) + emojis.chunked(9).map(Row::Emojis) }
        }
        adapter.notifyDataSetChanged()
        if (!isSearchMode) renderCategories()
    }
    private fun emojisForCategory(number: Int): List<String> = when (number) {
        11 -> catalog.getOrElse(11) { emptyList() }.takeWhile { item -> item.all { it.code < 128 } }
        12 -> catalog.getOrElse(11) { emptyList() }.dropWhile { item -> item.all { it.code < 128 } }
        else -> catalog.getOrElse(number) { emptyList() }
    }
    private fun renderCategories() {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        categories.forEachIndexed { index, (icon, description) ->
            row.addView(action(icon, description) { selectCategory(index) }.apply {
                background = rounded(if (category == index) selectedColor else surfaceColor)
                isSelected = category == index
            }, LayoutParams(dp(43), dp(42)))
        }
        categoryBar.removeAllViews()
        categoryBar.addView(row)
    }
    private fun action(label: String, description: String, click: () -> Unit) = TextView(context).apply {
        text = label
        textSize = if (label.length > 3) 14f else 21f
        gravity = Gravity.CENTER
        setTextColor(labelColor)
        background = rounded(surfaceColor)
        contentDescription = description
        isFocusable = true
        setOnClickListener { click() }
    }
    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(8).toFloat()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun loadCatalog(context: Context): List<List<String>> {
        val lines = context.resources.openRawResource(R.raw.emojis).bufferedReader(Charsets.UTF_8)
            .use { it.readLines() }
        val bounds = lines.last().trim().split(Regex("\\s+")).map { it.toInt() }
        return listOf(emptyList<String>()) + bounds.indices.map { group ->
            lines.subList(bounds[group], bounds.getOrElse(group + 1) { lines.lastIndex })
                .filter { it.isNotBlank() }
        }
    }
}
