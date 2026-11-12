package tribixbite.cleverkeys.estonianui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import kotlin.math.ceil

/** Distribute complete words when they fit; use natural widths only on real overflow. */
internal class AdaptiveCandidateScrollView(context: Context) : HorizontalScrollView(context) {
    init { isFillViewport = true }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val track = getChildAt(0) as? LinearLayout
        val viewport = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        if (track != null && viewport > 0) {
            val labels = (0 until track.childCount).mapNotNull { track.getChildAt(it) as? CandidateLabelView }
            val naturalWidths = labels.associateWith { label ->
                maxOf(label.minimumWidth,
                    ceil(label.paint.measureText(label.text.toString()).toDouble()).toInt() +
                        label.paddingLeft + label.paddingRight)
            }
            val natural = (0 until track.childCount).sumOf { index ->
                val child = track.getChildAt(index)
                if (child.visibility == View.GONE) 0
                else if (child is CandidateLabelView) naturalWidths.getValue(child)
                else maxOf(0, child.layoutParams?.width ?: 0)
            }
            val fits = natural <= viewport
            val visibleLabels = labels.filter { it.visibility != View.GONE }
            val fixedWidth = natural - visibleLabels.sumOf { naturalWidths.getValue(it) }
            val evenSlot = (viewport - fixedWidth) / visibleLabels.size.coerceAtLeast(1)
            val equalSlotsFit = fits && visibleLabels.all { naturalWidths.getValue(it) <= evenSlot }
            val trackWidth = if (fits) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            val trackParams = track.layoutParams as FrameLayout.LayoutParams
            if (trackParams.width != trackWidth) {
                trackParams.width = trackWidth
                track.layoutParams = trackParams
            }
            labels.forEach { label ->
                val params = label.layoutParams as LinearLayout.LayoutParams
                // Equal slots for genuinely short words; otherwise reserve each natural width
                // before distributing leftover space. Never clip a long word merely to stay even.
                val wantedWidth = when {
                    !fits -> ViewGroup.LayoutParams.WRAP_CONTENT
                    equalSlotsFit -> 0
                    else -> naturalWidths.getValue(label)
                }
                val wantedWeight = if (fits) 1f else 0f
                if (params.width != wantedWidth || params.weight != wantedWeight) {
                    params.width = wantedWidth
                    params.weight = wantedWeight
                    label.layoutParams = params
                }
            }
            if (fits && scrollX != 0) scrollTo(0, 0)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
