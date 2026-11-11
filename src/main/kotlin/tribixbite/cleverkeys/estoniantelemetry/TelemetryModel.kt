package tribixbite.cleverkeys.estoniantelemetry

import android.text.InputType
import android.view.inputmethod.EditorInfo
import java.util.Locale

/** Absolute monotonic uptime in milliseconds; coordinates are fractions of the keyboard surface. */
data class TelemetryPoint(val x: Float, val y: Float, val uptimeMs: Long)
data class TelemetryKey(val label: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
data class TelemetryGeometry(val widthPx: Int, val heightPx: Int, val keys: List<TelemetryKey> = emptyList())

data class SwipeTrace(
    val id: String, val language: String, val editor: EditorInfo?,
    val points: List<TelemetryPoint>, val geometry: TelemetryGeometry,
    val candidates: List<String>, val committed: String,
    val startedUptimeMs: Long, val committedUptimeMs: Long,
)

data class TelemetryStats(val enabled: Boolean, val swipes: Int, val finalized: Int, val corrections: Int)

/** Deliberately narrow: no editor contents, non-Estonian words, sensitive variations or private IME modes. */
internal object TelemetryPolicy {
    private val word = Regex("[a-zõäöüšžA-ZÕÄÖÜŠŽ]{1,40}")
    private val id = Regex("[A-Za-z0-9_-]{1,80}")
    fun id(value: String) = id.matches(value)
    fun word(value: String) = word.matches(value)
    fun language(value: String) = value.lowercase(Locale.ROOT) in setOf("et", "et-ee")
    fun editor(info: EditorInfo?): Boolean {
        if (info == null || info.packageName.isNullOrBlank()) return false
        val type = info.inputType
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT ||
            type and InputType.TYPE_MASK_VARIATION != InputType.TYPE_TEXT_VARIATION_NORMAL ||
            type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 ||
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0 ||
            info.privateImeOptions?.contains("incognito", ignoreCase = true) == true ||
            info.privateImeOptions?.contains("private", ignoreCase = true) == true
        ) return false
        return true
    }
    fun trace(value: SwipeTrace): Boolean {
        if (!id(value.id) || !language(value.language) || !editor(value.editor) ||
            !word(value.committed) || value.candidates.size > 5 || value.candidates.any { !word(it) } ||
            value.geometry.widthPx !in 1..4096 || value.geometry.heightPx !in 1..4096 ||
            value.geometry.keys.size > 100 || value.geometry.keys.any {
                !word(it.label) || it.label.length != 1 || it.left < 0 || it.top < 0 ||
                    it.right <= it.left || it.bottom <= it.top ||
                    it.right > value.geometry.widthPx || it.bottom > value.geometry.heightPx
            } || value.points.size !in 2..256 || value.startedUptimeMs < 0 ||
            value.committedUptimeMs < value.startedUptimeMs || value.committedUptimeMs - value.startedUptimeMs > 60_000
        ) return false
        val first = value.points.first().uptimeMs
        if (first < value.startedUptimeMs || first > value.committedUptimeMs) return false
        var prior = first
        for (p in value.points) {
            if (!p.x.isFinite() || !p.y.isFinite() || p.x !in 0f..1f || p.y !in 0f..1f ||
                p.uptimeMs < prior || p.uptimeMs > value.committedUptimeMs) return false
            prior = p.uptimeMs
        }
        return true
    }
}
