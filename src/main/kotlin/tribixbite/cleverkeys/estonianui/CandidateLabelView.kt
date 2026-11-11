package tribixbite.cleverkeys.estonianui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.widget.TextViewCompat

/** Fixed-height candidate: shrinks within reason, then ellipsizes instead of wrapping over keys. */
class CandidateLabelView(context: Context) : TextView(context) {
    init {
        gravity = Gravity.CENTER
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        val pad = (3f * resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, 0)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 11, 18, 1,
            android.util.TypedValue.COMPLEX_UNIT_SP)
    }

    fun bind(word: String?) {
        text = word.orEmpty()
        contentDescription = word.orEmpty() // TalkBack always gets the complete alternative.
        visibility = if (word == null) View.INVISIBLE else View.VISIBLE
    }
}
