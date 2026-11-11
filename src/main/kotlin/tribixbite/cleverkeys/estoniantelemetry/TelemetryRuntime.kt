package tribixbite.cleverkeys.estoniantelemetry

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import java.util.concurrent.Executors

/** Serial, non-main-thread owner of the encrypted store. No background upload or export. */
object TelemetryRuntime {
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "estonian-swipe-local-telemetry").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    private var controller: TelemetryController? = null // worker thread only
    @Volatile var collecting: Boolean = false
        private set

    private fun store(context: Context): TelemetryController = controller
        ?: TelemetryController(context.applicationContext).also {
            controller = it
            collecting = it.isEnabled
        }
    private fun editorSnapshot(info: EditorInfo?): EditorInfo? = info?.let { source ->
        EditorInfo().apply {
            packageName = source.packageName
            fieldId = source.fieldId
            inputType = source.inputType
            imeOptions = source.imeOptions
            privateImeOptions = source.privateImeOptions
        }
    }
    private fun <T> deliver(callback: ((T) -> Unit)?, result: T) {
        if (callback != null) main.post { callback(result) }
    }

    /** Loads persistent consent off the IME thread on process startup. */
    fun initialize(context: Context, callback: ((TelemetryStats?) -> Unit)? = null) {
        val app = context.applicationContext
        worker.execute {
            val result = try { store(app).stats() } catch (_: Exception) { collecting = false; null }
            deliver(callback, result)
        }
    }

    fun enable(context: Context, callback: (Boolean) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val ok = try { store(app).enable() } catch (_: Exception) { false }
            collecting = ok
            deliver(callback, ok)
        }
    }

    fun disable(context: Context, callback: (Boolean) -> Unit) {
        collecting = false
        val app = context.applicationContext
        worker.execute {
            val ok = try { store(app).disable() } catch (_: Exception) { false }
            collecting = false
            deliver(callback, ok)
        }
    }

    fun clear(context: Context, callback: (Boolean) -> Unit) {
        collecting = false
        val app = context.applicationContext
        worker.execute {
            val ok = try { store(app).clear() } catch (_: Exception) { false }
            collecting = false
            deliver(callback, ok)
        }
    }

    fun stats(context: Context, callback: (TelemetryStats?) -> Unit) = initialize(context, callback)

    /** The user must have selected this URI in ACTION_CREATE_DOCUMENT first. */
    fun export(context: Context, chosenUri: Uri, callback: (Boolean) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val ok = try {
                val out = app.contentResolver.openOutputStream(chosenUri, "w") ?: error("No output stream")
                out.use { store(app).exportJsonl(it) }
                true
            } catch (_: Exception) { false }
            deliver(callback, ok)
        }
    }

    fun recordSwipe(context: Context, trace: SwipeTrace) {
        if (!collecting) return
        val app = context.applicationContext
        val snapshot = trace.copy(editor = editorSnapshot(trace.editor),
            points = trace.points.toList(), candidates = trace.candidates.toList(),
            geometry = trace.geometry.copy(keys = trace.geometry.keys.toList()))
        worker.execute { if (collecting) try { store(app).recordSwipe(snapshot) } catch (_: Exception) { collecting = false } }
    }

    fun recordUndo(context: Context, id: String, info: EditorInfo?) {
        if (!collecting) return
        val app = context.applicationContext
        val editor = editorSnapshot(info)
        worker.execute { if (collecting) try { store(app).recordUndo(id, "et", editor) } catch (_: Exception) { collecting = false } }
    }

    fun recordCorrection(context: Context, id: String, info: EditorInfo?, from: String, to: String, kind: String) {
        if (!collecting) return
        val app = context.applicationContext
        val editor = editorSnapshot(info)
        worker.execute { if (collecting) try { store(app).recordCorrection(id, "et", editor, from, to, kind) } catch (_: Exception) { collecting = false } }
    }

    fun recordFinalization(context: Context, id: String, info: EditorInfo?, finalWord: String?) {
        if (!collecting) return
        val app = context.applicationContext
        val editor = editorSnapshot(info)
        worker.execute { if (collecting) try { store(app).recordFinalization(id, "et", editor, finalWord) } catch (_: Exception) { collecting = false } }
    }
}
