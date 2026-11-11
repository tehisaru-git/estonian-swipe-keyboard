package tribixbite.cleverkeys.estoniantelemetry

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class TelemetryControllerTest {
    private val box = TestCipherBox()
    private fun controller(): TelemetryController = TelemetryController(ApplicationProvider.getApplicationContext<Context>(), "test-${UUID.randomUUID()}", box)
    private fun editor(type: Int = InputType.TYPE_CLASS_TEXT) = EditorInfo().apply {
        packageName = "com.example.notes"
        inputType = type
    }
    private fun trace() = SwipeTrace("id1", "et", editor(), listOf(
        TelemetryPoint(0.1f, 0.2f, 100), TelemetryPoint(0.8f, 0.7f, 130)),
        TelemetryGeometry(300, 200), listOf("tere", "terve"), "tere", 100, 160)
    private fun exported(c: TelemetryController): String = ByteArrayOutputStream().also { c.exportJsonl(it) }.toString("UTF-8")

    @Test fun explicitConsentAndSwipeOnlyCollection() {
        val c = controller()
        assertFalse(c.recordSwipe(trace()))
        assertTrue(c.enable())
        assertTrue(c.recordSwipe(trace()))
        assertFalse(c.recordSwipe(trace())) // duplicate ID cannot overwrite an earlier trace
        assertFalse(c.recordCorrection("unknown", "et", editor(), "tere", "teere", "candidate"))
        assertEquals(1, c.stats().swipes)
        assertTrue(c.disable())
        assertFalse(c.recordCorrection("id1", "et", editor(), "tere", "teere", "candidate"))
        assertEquals(1, c.stats().swipes)
        assertFalse(TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box).isEnabled)
    }

    @Test fun verifiedUndoIsExportedEvenWhenFieldClosesBeforeReplacement() {
        val c = controller()
        assertTrue(c.enable())
        assertTrue(c.recordSwipe(trace()))
        assertTrue(c.recordUndo("id1", "et", editor()))
        val restarted = TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box)
        val before = JSONObject(exported(restarted).trim())
        assertTrue(before.getBoolean("undoObserved"))
        assertFalse(before.has("deleted")) // No final state can be inferred from a lost editor.
        assertTrue(restarted.recordCorrection("id1", "et", editor(), "tere", "terve", "manual"))
        assertTrue(restarted.recordFinalization("id1", "et", editor(), "terve"))
        val after = JSONObject(exported(restarted).trim())
        assertTrue(after.getBoolean("undoObserved"))
        assertEquals("terve", after.getString("finalWord"))
    }

    @Test fun correctionAndFinalizationRequireExistingSwipeAndMatchEditor() {
        val c = controller()
        c.enable()
        assertTrue(c.recordSwipe(trace()))
        assertFalse(c.recordCorrection("id1", "en", editor(), "tere", "teere", "candidate"))
        assertFalse(c.recordCorrection("id1", "et", editor(InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT), "tere", "teere", "candidate"))
        assertTrue(c.recordCorrection("id1", "et", editor(), "tere", "terve", "candidate"))
        assertFalse(c.recordFinalization("id1", "et", editor(), "other"))
        assertTrue(c.recordFinalization("id1", "et", editor(), "terve"))
        assertFalse(c.recordFinalization("id1", "et", editor(), null))
        val row = JSONObject(exported(c).trim())
        assertEquals("terve", row.getString("finalWord"))
        assertFalse(row.has("pkg"))
        assertFalse(row.has("field"))
        assertFalse(row.has("createdMs"))
        assertEquals(0L, row.getJSONArray("points").getJSONObject(0).getLong("timeMs"))
        assertEquals(1, row.getJSONArray("corrections").length())
        assertEquals(1, c.stats().finalized)
    }

    @Test fun rejectsSensitiveEditorsOtherLanguagesAndInvalidText() {
        val c = controller()
        c.enable()
        val sensitive = listOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_NUMBER)
        sensitive.forEach { assertFalse(c.recordSwipe(trace().copy(id = UUID.randomUUID().toString(), editor = editor(it)))) }
        assertFalse(c.recordSwipe(trace().copy(language = "en")))
        assertFalse(c.recordSwipe(trace().copy(editor = editor().apply {
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        })))
        assertFalse(c.recordSwipe(trace().copy(editor = editor().apply {
            privateImeOptions = "incognito"
        })))
        assertFalse(c.recordSwipe(trace().copy(committed = "hello world")))
        assertFalse(c.recordSwipe(trace().copy(points = listOf(TelemetryPoint(Float.NaN, 0f, 0)))))
        assertEquals(0, c.stats().swipes)
    }

    @Test fun persistsEncryptedAndClearRemovesDataAndConsent() {
        val c = controller()
        assertTrue(c.enable())
        assertTrue(c.recordSwipe(trace()))
        val restored = TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box)
        assertTrue(restored.isEnabled)
        assertEquals(1, restored.stats().swipes)
        val data = c.encryptedFileForTest()
        assertTrue(data.exists())
        assertFalse(data.listFiles()!!.any { it.readBytes().toString(Charsets.UTF_8).contains("tere") })
        assertTrue(restored.clear())
        assertFalse(restored.isEnabled)
        assertEquals(0, restored.stats().swipes)
        assertFalse(data.exists())
    }

    @Test fun corruptedCiphertextFailsClosedWithoutReplacingData() {
        val c = controller()
        assertTrue(c.enable())
        assertTrue(c.recordSwipe(trace()))
        val file = c.encryptedFileForTest()
        val segment = file.listFiles()!!.single()
        val damaged = segment.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        segment.writeBytes(damaged)
        val restored = TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box)
        assertFalse(restored.isEnabled)
        assertFalse(restored.enable())
        assertFalse(restored.recordSwipe(trace().copy(id = "new")))
        assertArrayEquals(damaged, segment.readBytes())
    }

    @Test fun boundsRecordCountAndExportsOnlyOnExplicitRequest() {
        val c = controller()
        c.enable()
        repeat(TelemetryController.MAX_SWIPES + 3) { i -> assertTrue(c.recordSwipe(trace().copy(id = "id$i"))) }
        assertEquals(TelemetryController.MAX_SWIPES, c.stats().swipes)
        val rows = exported(c).trim().lines()
        assertEquals(TelemetryController.MAX_SWIPES, rows.size)
        assertFalse(rows.first().contains("id0\""))
    }

    @Test fun fiveThousandSwipesSurviveRestartWithoutQuadraticDiskWrites() {
        var sealedBytes = 0L
        val counting = object : CipherBox {
            override fun seal(bytes: ByteArray): ByteArray {
                sealedBytes += bytes.size
                return box.seal(bytes)
            }
            override fun open(bytes: ByteArray) = box.open(bytes)
            override fun destroy() = box.destroy()
        }
        val c = TelemetryController(ApplicationProvider.getApplicationContext(), "test-${UUID.randomUUID()}", counting)
        assertTrue(c.enable())
        repeat(5_000) { i -> assertTrue("swipe $i", c.recordSwipe(trace().copy(id = "bulk$i"))) }
        val files = c.encryptedFileForTest().listFiles()!!.filter { it.isFile }
        assertTrue(files.isNotEmpty())
        assertTrue(files.sumOf { it.length() } < 20_000_000L)
        assertTrue("historical events must not be resealed: $sealedBytes", sealedBytes < 20_000_000L)
        val restored = TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box)
        assertEquals(5_000, restored.stats().swipes)
        assertEquals(5_000, exported(restored).trim().lines().size)
        assertFalse(files.any { it.readBytes().toString(Charsets.UTF_8).contains("tere") })
    }

    @Test fun correctionsAndFinalizationsReplayAcrossRestart() {
        val c = controller()
        assertTrue(c.enable())
        assertTrue(c.recordSwipe(trace()))
        assertTrue(c.recordCorrection("id1", "et", editor(), "tere", "terve", "candidate"))
        val resumed = TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box)
        assertTrue(resumed.recordFinalization("id1", "et", editor(), "terve"))
        val resumedAgain = TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box)
        assertEquals(1, resumedAgain.stats().corrections)
        assertEquals(1, resumedAgain.stats().finalized)
        assertEquals("terve", JSONObject(exported(resumedAgain).trim()).getString("finalWord"))
    }

    @Test fun truncatedTailRecoversOnlyCompleteAuthenticatedEvents() {
        val c = controller()
        c.enable()
        assertTrue(c.recordSwipe(trace()))
        assertTrue(c.recordSwipe(trace().copy(id = "second")))
        val segment = c.encryptedFileForTest().listFiles()!!.single()
        java.io.RandomAccessFile(segment, "rw").use { it.setLength(it.length() - 5) }
        val restored = TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box)
        assertTrue(restored.isEnabled)
        assertEquals(1, restored.stats().swipes)
        assertTrue(restored.recordSwipe(trace().copy(id = "third")))
        assertEquals(2, TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box).stats().swipes)
    }

    @Test fun corruptCompleteRecordFailsClosedWithoutOverwritingIt() {
        val c = controller()
        c.enable()
        assertTrue(c.recordSwipe(trace()))
        val segment = c.encryptedFileForTest().listFiles()!!.single()
        val damaged = segment.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        segment.writeBytes(damaged)
        val resumed = TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box)
        assertFalse(resumed.isEnabled)
        assertFalse(resumed.enable())
        assertArrayEquals(damaged, segment.readBytes())
    }

    @Test fun boundedSegmentsEvictOldestSwipesAndClearDeletesAllSegments() {
        val c = controller()
        c.enable()
        repeat(TelemetryController.MAX_SWIPES + 3) { i -> assertTrue(c.recordSwipe(trace().copy(id = "id$i"))) }
        assertEquals(TelemetryController.MAX_SWIPES, c.stats().swipes)
        assertTrue(c.encryptedFileForTest().walkTopDown().filter { it.isFile }.sumOf { it.length() } <= TelemetryController.MAX_BYTES)
        assertTrue(c.clear())
        assertFalse(c.encryptedFileForTest().exists())
    }

    @Test fun expiredSwipeIsNotCountedOrExportedAfterRestart() {
        val c = controller()
        assertTrue(c.enable())
        assertTrue(c.recordSwipe(trace()))
        val segment = c.encryptedFileForTest().listFiles()!!.single()
        val bytes = segment.readBytes()
        val length = java.nio.ByteBuffer.wrap(bytes, 0, 4).int
        val event = JSONObject(String(box.open(bytes.copyOfRange(4, length + 4)), Charsets.UTF_8))
        event.getJSONObject("row").put("createdMs", 1L)
        val rewritten = box.seal(event.toString().toByteArray(Charsets.UTF_8))
        segment.outputStream().use { out ->
            out.write(java.nio.ByteBuffer.allocate(4).putInt(rewritten.size).array())
            out.write(rewritten)
        }
        val resumed = TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box)
        assertEquals(0, resumed.stats().swipes)
        assertEquals("", exported(resumed))
        assertFalse(resumed.recordCorrection("id1", "et", editor(), "tere", "terve", "candidate"))
    }

    @Test fun keyDeletionFailureLeavesTombstoneAndBlocksRestart() {
        val faulty = object : CipherBox {
            override fun seal(bytes: ByteArray) = box.seal(bytes)
            override fun open(bytes: ByteArray) = box.open(bytes)
            override fun destroy(): Unit = error("key deletion failed")
        }
        val c = TelemetryController(ApplicationProvider.getApplicationContext(), "test-${UUID.randomUUID()}", faulty)
        assertTrue(c.enable())
        assertTrue(c.recordSwipe(trace()))
        assertFalse(c.clear())
        val resumed = TelemetryController(ApplicationProvider.getApplicationContext(), c.storeName, box)
        assertFalse(resumed.isEnabled)
        assertFalse(resumed.enable())
    }
}
