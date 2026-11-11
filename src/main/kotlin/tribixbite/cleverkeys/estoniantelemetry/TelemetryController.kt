package tribixbite.cleverkeys.estoniantelemetry

import android.content.Context
import android.view.inputmethod.EditorInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/** Opt-in, local-only telemetry. Invoke on one serial worker, never on the IME main thread.
 * Every event is separately AES-GCM authenticated; only length framing and segment names are cleartext.
 * Exports are plaintext ONLY to a stream explicitly selected by the user via ACTION_CREATE_DOCUMENT.
 */
class TelemetryController internal constructor(
    context: Context, internal val storeName: String, private val cipher: CipherBox,
) {
    constructor(context: Context) : this(context, "estonian-swipe", AndroidKeystoreBox("estonian-swipe-telemetry-v1"))

    companion object {
        const val MAX_SWIPES = 5_000
        const val MAX_BYTES = 48L * 1024 * 1024
        private const val SEGMENT_BYTES = 1024 * 1024L
        private const val MAX_EVENT_BYTES = 128 * 1024
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }

    init { require(storeName.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) }
    private val directory = context.applicationContext.noBackupFilesDir
    private val file = File(directory, "$storeName.segments")
    private val legacy = File(directory, "$storeName.aes")
    private val consent = File(directory, "$storeName.enabled")
    private val clearing = File(directory, "$storeName.clearing")
    private val records = LinkedHashMap<String, JSONObject>()
    private val owners = HashMap<String, Long>()
    private var healthy = true
    var isEnabled: Boolean = false
        private set

    init {
        try {
            check(!clearing.exists() && !legacy.exists()) { "Incomplete deletion or legacy store" }
            if (file.exists()) {
                check(file.isDirectory)
                val segments = segments()
                check(segments.sumOf { it.length() } <= MAX_BYTES)
                segments.forEachIndexed { index, segment -> replay(segment, index == segments.lastIndex) }
                trim()
            }
            isEnabled = consent.exists()
        } catch (_: Exception) {
            healthy = false
            isEnabled = false
            records.clear()
            owners.clear()
        }
    }

    @Synchronized fun enable(): Boolean {
        if (!healthy || clearing.exists() || legacy.exists()) return false
        return try {
            cipher.seal(byteArrayOf()).fill(0)
            if (!consent.exists()) consent.writeText("1")
            isEnabled = true
            true
        } catch (_: Exception) { isEnabled = false; false }
    }

    @Synchronized fun disable(): Boolean {
        isEnabled = false
        return !consent.exists() || consent.delete()
    }

    @Synchronized fun recordSwipe(trace: SwipeTrace): Boolean {
        if (!isEnabled || !healthy || !TelemetryPolicy.trace(trace) || records.containsKey(trace.id)) return false
        val row = JSONObject().apply {
            put("schema", "estonian-swipe-v1")
            put("id", trace.id)
            put("lang", "et")
            put("pkg", trace.editor!!.packageName)
            put("field", trace.editor.fieldId)
            put("createdMs", System.currentTimeMillis())
            put("widthPx", trace.geometry.widthPx)
            put("heightPx", trace.geometry.heightPx)
            put("keys", JSONArray().apply { trace.geometry.keys.forEach { k ->
                put(JSONObject().put("label", k.label).put("left", k.left).put("top", k.top)
                    .put("right", k.right).put("bottom", k.bottom))
            } })
            put("points", JSONArray().apply { trace.points.forEach { p ->
                put(JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble())
                    .put("timeMs", p.uptimeMs - trace.startedUptimeMs))
            } })
            put("durationMs", trace.committedUptimeMs - trace.startedUptimeMs)
            put("candidates", JSONArray(trace.candidates))
            put("committed", trace.committed)
            put("corrections", JSONArray())
        }
        if (!append(JSONObject().put("type", "swipe").put("row", row))) return false
        trim()
        return true
    }

    /** A verified owned-word deletion. This is not yet a final deletion: a replacement may follow. */
    @Synchronized fun recordUndo(id: String, language: String, editor: EditorInfo?): Boolean {
        val row = matchingRow(id, language, editor) ?: return false
        if (row.has("finalWord") || row.has("deleted") || row.optBoolean("undoObserved")) return false
        return append(JSONObject().put("type", "undo").put("id", id))
    }

    @Synchronized fun recordCorrection(
        id: String, language: String, editor: EditorInfo?, from: String, to: String, kind: String,
    ): Boolean {
        val row = matchingRow(id, language, editor) ?: return false
        if (!TelemetryPolicy.word(from) || !TelemetryPolicy.word(to) ||
            kind !in setOf("candidate", "autocorrect", "manual", "backspace") ||
            row.has("finalWord") || row.has("deleted")) return false
        val corrections = row.getJSONArray("corrections")
        val current = if (corrections.length() == 0) row.getString("committed")
            else corrections.getJSONObject(corrections.length() - 1).getString("to")
        if (current != from || from == to || corrections.length() >= 20) return false
        return append(JSONObject().put("type", "correction").put("id", id)
            .put("from", from).put("to", to).put("kind", kind))
    }

    @Synchronized fun recordFinalization(id: String, language: String, editor: EditorInfo?, finalWord: String?): Boolean {
        val row = matchingRow(id, language, editor) ?: return false
        if (row.has("finalWord") || row.has("deleted") ||
            (finalWord != null && !TelemetryPolicy.word(finalWord))) return false
        val corrections = row.getJSONArray("corrections")
        val current = if (corrections.length() == 0) row.getString("committed")
            else corrections.getJSONObject(corrections.length() - 1).getString("to")
        if (finalWord != null && current != finalWord) return false
        return append(JSONObject().put("type", "finalization").put("id", id)
            .put("deleted", finalWord == null).apply { if (finalWord != null) put("finalWord", finalWord) })
    }

    @Synchronized fun stats(): TelemetryStats {
        trim()
        val rows = records.values
        return TelemetryStats(isEnabled, rows.size, rows.count { it.has("finalWord") || it.has("deleted") },
            rows.sumOf { it.getJSONArray("corrections").length() })
    }

    @Synchronized fun exportJsonl(output: OutputStream) {
        check(healthy) { "Encrypted telemetry unavailable" }
        trim()
        records.values.forEach { stored ->
            val exported = JSONObject(stored.toString())
            exported.remove("pkg")
            exported.remove("field")
            exported.remove("createdMs")
            output.write(exported.toString().toByteArray(StandardCharsets.UTF_8))
            output.write('\n'.code)
        }
        output.flush()
    }

    /** Tombstone first: failed deletion cannot silently resume collection after restart. */
    @Synchronized fun clear(): Boolean {
        isEnabled = false
        return try {
            if (!clearing.exists()) clearing.writeText("1")
            healthy = false
            check(disable())
            if (file.exists()) {
                check(file.isDirectory)
                file.listFiles()?.forEach { check(it.isFile && it.delete()) } ?: error("Cannot list telemetry")
                check(file.delete())
            }
            if (legacy.exists()) check(legacy.delete())
            cipher.destroy()
            check(clearing.delete())
            records.clear()
            owners.clear()
            healthy = true
            true
        } catch (_: Exception) { healthy = false; false }
    }

    internal fun encryptedFileForTest(): File = file

    private fun matchingRow(id: String, language: String, editor: EditorInfo?): JSONObject? {
        if (!isEnabled || !healthy || !TelemetryPolicy.id(id) || !TelemetryPolicy.language(language) ||
            !TelemetryPolicy.editor(editor)) return null
        trim()
        return records[id]?.takeIf { it.optString("pkg") == editor!!.packageName && it.optInt("field") == editor.fieldId }
    }

    private fun trim() {
        val now = System.currentTimeMillis()
        val iterator = records.entries.iterator()
        while (iterator.hasNext()) {
            val row = iterator.next().value
            val age = now - row.optLong("createdMs", 0)
            if (age !in 0L..RETENTION_MS) { owners.remove(row.getString("id")); iterator.remove() }
        }
        while (records.size > MAX_SWIPES) {
            val first = records.keys.first()
            records.remove(first)
            owners.remove(first)
        }
    }

    private fun segments(): List<File> {
        val entries = file.listFiles() ?: error("Cannot list telemetry segments")
        check(entries.all { it.isFile && it.name.matches(Regex("[0-9]{16}\\.seg")) })
        return entries.sortedBy { it.name }
    }

    private fun replay(segment: File, newest: Boolean) {
        val sequence = segment.name.substringBefore('.').toLong()
        RandomAccessFile(segment, "rw").use { input ->
            check(input.length() <= SEGMENT_BYTES && input.length() >= 0)
            while (input.filePointer < input.length()) {
                val offset = input.filePointer
                if (input.length() - offset < 4) {
                    check(newest) { "Incomplete historical segment" }
                    input.setLength(offset)
                    input.fd.sync()
                    break
                }
                val length = input.readInt()
                check(length in 30..MAX_EVENT_BYTES)
                if (input.length() - input.filePointer < length) {
                    check(newest) { "Incomplete historical event" }
                    input.setLength(offset)
                    input.fd.sync()
                    break
                }
                val encrypted = ByteArray(length)
                input.readFully(encrypted)
                val plaintext = cipher.open(encrypted)
                try { apply(JSONObject(String(plaintext, StandardCharsets.UTF_8)), sequence) }
                finally { plaintext.fill(0); encrypted.fill(0) }
            }
        }
    }

    private fun apply(event: JSONObject, sequence: Long) {
        when (event.getString("type")) {
            "swipe" -> {
                val row = event.getJSONObject("row")
                val id = row.getString("id")
                check(TelemetryPolicy.id(id) && row.getString("schema") == "estonian-swipe-v1" && !records.containsKey(id))
                records[id] = row
                owners[id] = sequence
            }
            "undo" -> {
                val row = records[event.getString("id")] ?: return
                check(!row.has("finalWord") && !row.has("deleted") && !row.optBoolean("undoObserved"))
                row.put("undoObserved", true)
            }
            "correction" -> {
                val row = records[event.getString("id")] ?: return // parent segment was evicted
                check(!row.has("finalWord") && !row.has("deleted"))
                val corrections = row.getJSONArray("corrections")
                check(corrections.length() < 20)
                val current = if (corrections.length() == 0) row.getString("committed")
                    else corrections.getJSONObject(corrections.length() - 1).getString("to")
                check(current == event.getString("from") && TelemetryPolicy.word(event.getString("to")))
                corrections.put(JSONObject().put("from", current).put("to", event.getString("to"))
                    .put("kind", event.getString("kind")))
            }
            "finalization" -> {
                val row = records[event.getString("id")] ?: return
                check(!row.has("finalWord") && !row.has("deleted"))
                if (event.getBoolean("deleted")) row.put("deleted", true)
                else row.put("finalWord", event.getString("finalWord"))
            }
            else -> error("Unknown telemetry event")
        }
    }

    private fun append(event: JSONObject): Boolean {
        val plaintext = event.toString().toByteArray(StandardCharsets.UTF_8)
        val encrypted = try { cipher.seal(plaintext) }
            catch (_: Exception) { plaintext.fill(0); return false }
        plaintext.fill(0)
        try {
            check(encrypted.size in 30..MAX_EVENT_BYTES && encrypted.size + 4 <= SEGMENT_BYTES)
            if (!file.exists()) check(file.mkdir())
            val segments = segments()
            val last = segments.lastOrNull()
            val sequence = last?.name?.substringBefore('.')?.toLong() ?: 0L
            val next = if (last == null || last.length() + encrypted.size + 4 > SEGMENT_BYTES) sequence + 1 else sequence
            val target = File(file, "%016d.seg".format(java.util.Locale.ROOT, next))
            val existing = segments.sumOf { it.length() }
            // Reserve room before writing; whole oldest segments are evicted, never rewritten.
            var used = existing
            for (old in segments) {
                if (used + encrypted.size + 4 <= MAX_BYTES) break
                val bytes = old.length()
                check(old != target && old.delete())
                used -= bytes
                val evicted = old.name.substringBefore('.').toLong()
                val iterator = owners.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.value == evicted) { records.remove(entry.key); iterator.remove() }
                }
            }
            check(segments().sumOf { it.length() } + encrypted.size + 4 <= MAX_BYTES)
            // Append with one fsync. On an uncertain write failure, fail closed until explicit clear.
            RandomAccessFile(target, "rw").use { out ->
                out.seek(out.length())
                out.writeInt(encrypted.size)
                out.write(encrypted)
                out.fd.sync()
            }
            apply(event, next)
            trim()
            return true
        } catch (_: Exception) {
            healthy = false
            isEnabled = false
            return false
        } finally { encrypted.fill(0) }
    }
}
