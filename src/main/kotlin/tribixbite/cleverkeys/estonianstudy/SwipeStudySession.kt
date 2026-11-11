package tribixbite.cleverkeys.estonianstudy

import android.text.InputType
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import org.json.JSONArray
import org.json.JSONObject
import tribixbite.cleverkeys.estonianui.KeyboardGeometry
import tribixbite.cleverkeys.estonianui.SurfaceSample

/** A bounded, ephemeral study, never backed by preferences, a database or a network client. */
class SwipeStudySession(private val ownPackage: String, val fieldId: Int) {
    companion object {
        const val MAX_TRACES = 100
        const val MAX_POINTS = 256
        // Closed vocabulary: neither editor contents nor arbitrary decoder words enter the export.
        val ESTONIAN_PROMPTS = listOf(
            "tere", "kass", "maja", "päike", "õun", "öö", "tänav", "sõber",
            "kodu", "raamat", "jõgi", "hommik", "majale", "majalt", "käisin",
            "sõnadega", "õpetaja", "päikeseline", "telefon", "kirjutan"
        )
        val ENGLISH_PROMPTS = listOf("hello", "cat", "home", "sun", "keyboard", "morning", "writing", "friend")
        val PROMPTS = ESTONIAN_PROMPTS + ENGLISH_PROMPTS
    }

    private data class Point(val x: Float, val y: Float, val timeMs: Long)
    private data class Key(val label: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
    private data class Trace(
        val expected: String, val language: String, val width: Int, val height: Int,
        val points: List<Point>, val keys: List<Key>, val candidates: List<String>,
        val expectedRank: Int, val decodeDelayMs: Long,
    )

    private val traces = ArrayDeque<Trace>()
    private var promptIndex = 0
    var onChanged: (() -> Unit)? = null
    var isRecording: Boolean = false
        private set
    val count: Int get() = traces.size
    val expected: String get() = PROMPTS[promptIndex % PROMPTS.size]

    @Synchronized fun start() { isRecording = true }
    @Synchronized fun stop() { isRecording = false }
    @Synchronized fun clear() {
        stop()
        traces.clear()
        promptIndex = 0
    }

    /** Only the IME should invoke this, for a completed swipe while its editor is still current. */
    @Synchronized fun record(
        editor: EditorInfo?, samples: List<SurfaceSample>, geometry: KeyboardGeometry,
        candidates: List<String>,
    ): Boolean {
        if (!isRecording || editor == null || editor.packageName != ownPackage ||
            editor.fieldId != fieldId || fieldId <= 0 ||
            editor.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT ||
            editor.inputType and InputType.TYPE_MASK_VARIATION != InputType.TYPE_TEXT_VARIATION_NORMAL ||
            geometry.symbolMode || samples.isEmpty() || samples.size > 4096 ||
            geometry.keys.size > 128 || geometry.width > 4096 || geometry.height > 4096
        ) return false
        if (samples.any { !it.x.isFinite() || !it.y.isFinite() || it.timeMs < 0 ||
                it.x < 0 || it.y < 0 || it.x >= geometry.width || it.y >= geometry.height } ||
            samples.zipWithNext().any { (a, b) -> b.timeMs < a.timeMs }) return false
        val expectedWord = expected
        if ((expectedWord in ESTONIAN_PROMPTS) != (geometry.language.name == "ESTONIAN")) return false
        val kept = if (samples.size <= MAX_POINTS) samples else samples.take(MAX_POINTS - 1) + samples.last()
        val allowed = PROMPTS.toSet()
        val trace = Trace(expectedWord, geometry.language.name, geometry.width, geometry.height,
            kept.map { Point(it.x, it.y, it.timeMs - samples.first().timeMs) },
            geometry.keys.map { Key(it.label, it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom) },
            candidates.filter { it in allowed }.take(5), candidates.take(3).indexOf(expectedWord) + 1,
            (SystemClock.uptimeMillis() - samples.last().timeMs).coerceIn(0L, 60_000L))
        if (traces.size == MAX_TRACES) traces.removeFirst()
        traces.addLast(trace)
        promptIndex++
        onChanged?.invoke()
        return true
    }

    /** JSON is produced only when the user explicitly requests export. No field/editor text. */
    @Synchronized fun exportJson(): String = JSONObject().apply {
        put("schema", "estonian-swipe-study-v1")
        put("traces", JSONArray().apply {
            traces.forEach { trace ->
                put(JSONObject().apply {
                    put("expected", trace.expected)
                    put("expectedRankTop3", trace.expectedRank)
                    put("decodeDelayMs", trace.decodeDelayMs)
                    put("language", trace.language)
                    put("width", trace.width)
                    put("height", trace.height)
                    put("points", JSONArray().apply {
                        trace.points.forEach { point ->
                            put(JSONObject().put("x", point.x.toDouble()).put("y", point.y.toDouble())
                                .put("timeMs", point.timeMs))
                        }
                    })
                    put("keys", JSONArray().apply {
                        trace.keys.forEach { key ->
                            put(JSONObject().put("label", key.label).put("left", key.left)
                                .put("top", key.top).put("right", key.right).put("bottom", key.bottom))
                        }
                    })
                    put("candidates", JSONArray(trace.candidates))
                })
            }
        })
    }.toString()
}

/** In-process explicit callback: attached only while the study Activity is foregrounded. */
object SwipeStudyBridge {
    @Volatile private var active: SwipeStudySession? = null

    @Synchronized fun attach(session: SwipeStudySession) { active = session }
    @Synchronized fun detach(session: SwipeStudySession) {
        if (active === session) active = null
    }

    /** Call after decode, checking the editor/connection are still current in the IME. */
    fun onSwipeDecoded(
        editor: EditorInfo?, samples: List<SurfaceSample>, geometry: KeyboardGeometry,
        candidates: List<String>,
    ): Boolean = active?.record(editor, samples, geometry, candidates) ?: false
}
