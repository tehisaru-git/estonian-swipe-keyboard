package tribixbite.cleverkeys.estonianstudy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import tribixbite.cleverkeys.R

/** Voluntary, foreground-only test field. No permission, persistence, upload or telemetry. */
class SwipeStudyActivity : Activity() {
    private lateinit var session: SwipeStudySession
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var prompt: TextView
    private var foreground = false

    companion object { private const val CREATE_JSON = 1701 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val retainedSession = lastNonConfigurationInstance as? SwipeStudySession
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scroll = ScrollView(this).apply { addView(column) }
        setContentView(scroll)
        column.addView(TextView(this).apply {
            text = getString(R.string.swipe_study_intro)
            textSize = 17f
        })
        prompt = TextView(this).apply { textSize = 24f }
        column.addView(prompt)
        input = EditText(this).apply {
            id = retainedSession?.fieldId ?: View.generateViewId()
            hint = getString(R.string.swipe_study_field)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
            setSingleLine(true)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && ::session.isInitialized) {
                    session.stop()
                    updateStatus()
                }
            }
        }
        column.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        session = retainedSession ?: SwipeStudySession(packageName, input.id)
        status = TextView(this)
        column.addView(status)
        fun button(label: String, action: () -> Unit) {
            column.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        button(getString(R.string.swipe_study_start)) {
            input.requestFocus()
            if (foreground && input.hasFocus()) {
                session.start()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            updateStatus()
        }
        button(getString(R.string.swipe_study_stop)) { session.stop(); updateStatus() }
        button(getString(R.string.swipe_study_clear)) { session.clear(); input.text?.clear(); updateStatus() }
        button(getString(R.string.swipe_study_export)) {
            // No file is written until the user selects a destination in the system picker.
            session.stop()
            updateStatus()
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "swipe-study.json")
            }, CREATE_JSON)
        }
        updateStatus()
    }

    private fun updateStatus() {
        prompt.text = getString(R.string.swipe_study_prompt, session.expected)
        status.text = getString(if (session.isRecording) R.string.swipe_study_recording
            else R.string.swipe_study_stopped, session.count, SwipeStudySession.MAX_TRACES)
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        session.onChanged = { runOnUiThread { if (foreground) updateStatus() } }
        SwipeStudyBridge.attach(session)
    }

    override fun onPause() {
        foreground = false
        session.stop()
        SwipeStudyBridge.detach(session)
        session.onChanged = null
        updateStatus()
        super.onPause()
    }

    override fun onRetainNonConfigurationInstance(): Any = session

    override fun onDestroy() {
        SwipeStudyBridge.detach(session)
        if (!isChangingConfigurations) session.clear()
        super.onDestroy()
    }

    @Deprecated("Activity result retained to avoid extra dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CREATE_JSON || resultCode != RESULT_OK) return
        if (session.count == 0) {
            Toast.makeText(this, R.string.swipe_study_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = data?.data ?: return
        try {
            contentResolver.openOutputStream(uri, "w")?.use { stream ->
                stream.write(session.exportJson().toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("No writable destination")
            Toast.makeText(this, R.string.swipe_study_saved, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.swipe_study_save_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
