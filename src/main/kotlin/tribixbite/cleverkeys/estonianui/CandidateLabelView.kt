package tribixbite.cleverkeys.estonianui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** Full single-line word; its parent scrolls horizontally rather than hiding the ending. */
class CandidateLabelView(context: Context) : TextView(context) {
    init {
        gravity = Gravity.CENTER
        setSingleLine(true)
        includeFontPadding = false
        textSize = 18f
        val pad = (12f * resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, 0)
        minWidth = (48f * resources.displayMetrics.density).toInt()
    }

    fun bind(word: String?) {
        text = word.orEmpty()
        contentDescription = word.orEmpty() // TalkBack always gets the complete alternative.
        visibility = if (word == null) View.GONE else View.VISIBLE
    }
}
