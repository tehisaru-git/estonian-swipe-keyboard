package tribixbite.cleverkeys.estonianstudy

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONObject
import tribixbite.cleverkeys.estonianui.KeyboardGeometry
import tribixbite.cleverkeys.estonianui.KeyboardLanguage
import tribixbite.cleverkeys.estonianui.SurfaceSample

@RunWith(RobolectricTestRunner::class)
class SwipeStudySessionTest {
    private val geometry = KeyboardGeometry(320, 200, KeyboardLanguage.ESTONIAN)
    private fun editor(id: Int = 123, pkg: String = "io.github.tehisaru.estonianswipe") = EditorInfo().apply {
        packageName = pkg
        fieldId = id
        inputType = InputType.TYPE_CLASS_TEXT
    }
    private fun samples() = listOf(
        SurfaceSample(12f, 40f, 100L, null),
        SurfaceSample(70f, 45f, 120L, null),
    )

    @Test fun recordingRequiresExplicitStartAndMatchingEditor() {
        val session = SwipeStudySession("io.github.tehisaru.estonianswipe", 123)
        assertFalse(session.record(editor(), samples(), geometry, listOf("tere")))
        session.start()
        assertFalse(session.record(editor(pkg = "other.app"), samples(), geometry, listOf("tere")))
        assertFalse(session.record(editor(id = 124), samples(), geometry, listOf("tere")))
        assertEquals(0, session.count)
        assertTrue(session.record(editor(), samples(), geometry, listOf("tere")))
        assertEquals(1, JSONObject(session.exportJson()).getJSONArray("traces").getJSONObject(0)
            .getInt("expectedRankTop3"))
        session.stop()
        assertFalse(session.record(editor(), samples(), geometry, listOf("tere")))
        assertEquals(1, session.count)
    }

    @Test fun passwordAndMalformedSamplesNeverEnterSession() {
        val session = SwipeStudySession("io.github.tehisaru.estonianswipe", 123)
        session.start()
        val password = editor().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        assertFalse(session.record(password, samples(), geometry, listOf("tere")))
        assertFalse(session.record(editor(), listOf(SurfaceSample(Float.NaN, 0f, 1L, null)), geometry, listOf("tere")))
        assertFalse(session.record(editor(), listOf(SurfaceSample(1f, 1f, 2L, null), SurfaceSample(2f, 2f, 1L, null)), geometry, listOf("tere")))
        assertEquals(0, session.count)
    }

    @Test fun exportContainsOnlyFixedPromptKnownCandidatesAndBoundedPoints() {
        val session = SwipeStudySession("io.github.tehisaru.estonianswipe", 123)
        session.start()
        val many = (0..600).map { SurfaceSample((it % 300).toFloat(), 40f, it.toLong(), null) }
        assertTrue(session.record(editor(), many, geometry, listOf("private phrase", "tere", "kass")))
        val json = JSONObject(session.exportJson())
        val traces = json.getJSONArray("traces")
        assertEquals(1, traces.length())
        val trace = traces.getJSONObject(0)
        assertEquals("tere", trace.getString("expected"))
        assertEquals(256, trace.getJSONArray("points").length())
        assertEquals("tere", trace.getJSONArray("candidates").getString(0))
        assertFalse(session.exportJson().contains("private phrase"))
        assertFalse(session.exportJson().contains("fieldId"))
        assertTrue(trace.getJSONArray("keys").length() > 0)
    }

    @Test fun sessionEvictsOldestAndClearResetsPrompt() {
        val session = SwipeStudySession("io.github.tehisaru.estonianswipe", 123)
        session.start()
        repeat(110) {
            val language = if (session.expected in SwipeStudySession.ESTONIAN_PROMPTS)
                KeyboardLanguage.ESTONIAN else KeyboardLanguage.ENGLISH
            assertTrue(session.record(editor(), samples(), KeyboardGeometry(320, 200, language), emptyList()))
        }
        assertEquals(100, session.count)
        session.clear()
        assertEquals(0, session.count)
        assertEquals("tere", session.expected)
        assertFalse(session.isRecording)
    }
}
