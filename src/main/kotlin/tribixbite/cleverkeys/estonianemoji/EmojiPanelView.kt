package tribixbite.cleverkeys.estonianemoji

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tribixbite.cleverkeys.R

/** Local emoji picker. The host keyboard supplies query taps; this view never opens an IME. */
class EmojiPanelView(context: Context) : LinearLayout(context) {
    var onEmojiPicked: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var query: String = ""
        private set
    var visibleEmoji: List<String> = emptyList()
        private set
    private var index: OfflineEmojiSearch? = null
    private val queryLabel = TextView(context)
    private val grid = GridLayout(context)
    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    private val surfaceColor = if (dark) 0xff1f1f1f.toInt() else 0xfff0f1f4.toInt()
    private val keyColor = if (dark) 0xff33383f.toInt() else Color.WHITE
    private val actionColor = if (dark) 0xff444746.toInt() else 0xffd9dce3.toInt()
    private val labelColor = if (dark) 0xffe3e3e3.toInt() else 0xff252932.toInt()
    private val common = listOf("🙂", "😂", "❤️", "👍", "🙏", "🎉", "😍", "😢", "😮", "🤔", "🔥", "✅",
        "👋", "🥰", "😊", "👏", "💙", "🤝", "🎂", "🐈", "🌞", "🍎", "💐", "💡")

    init {
        orientation = VERTICAL
        setBackgroundColor(surfaceColor)
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        queryLabel.setTextColor(labelColor)
        queryLabel.textSize = 16f
        queryLabel.setPadding(dp(12), 0, 0, 0)
        queryLabel.gravity = Gravity.CENTER_VERTICAL
        queryLabel.contentDescription = context.getString(R.string.swipe_emoji_query_description)
        top.addView(queryLabel, LayoutParams(0, dp(44), 1f))
        top.addView(action("⌫", context.getString(R.string.swipe_emoji_backspace)) { backspaceQuery() },
            LayoutParams(dp(50), dp(44)))
        top.addView(action("×", context.getString(R.string.swipe_emoji_close)) { close() },
            LayoutParams(dp(50), dp(44)))
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        grid.columnCount = 6
        addView(ScrollView(context).apply { addView(grid) },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(150)))
        render()
    }

    fun setSearchIndex(search: OfflineEmojiSearch?) { index = search; render() }

    fun appendQuery(text: String) {
        if (text.isEmpty()) return
        val safe = text.replace('\n', ' ').replace('\r', ' ')
        query = (query + safe).take(64)
        render()
    }

    fun backspaceQuery() {
        if (query.isEmpty()) return
        query = query.substring(0, query.offsetByCodePoints(query.length, -1))
        render()
    }

    fun clearQuery() { query = ""; render() }
    fun close() { clearQuery(); onClose?.invoke() }

    internal fun pickForTest(emoji: String) {
        if (emoji in visibleEmoji) onEmojiPicked?.invoke(emoji)
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
        visibleEmoji = if (query.isEmpty()) common else index?.search(query, 36).orEmpty()
        grid.removeAllViews()
        for (emoji in visibleEmoji) {
            grid.addView(TextView(context).apply {
                text = emoji
                textSize = 26f
                gravity = Gravity.CENTER
                background = tile(keyColor)
                contentDescription = emoji
                setOnClickListener { pickForTest(emoji) }
            }, GridLayout.LayoutParams().apply {
                width = resources.displayMetrics.widthPixels / 6 - dp(4)
                height = dp(48)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
    }

    private fun action(label: String, description: String, click: () -> Unit) =
        TextView(context).apply {
            text = label
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(labelColor)
            background = tile(actionColor)
            contentDescription = description
            setOnClickListener { click() }
        }

    private fun tile(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(5).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
