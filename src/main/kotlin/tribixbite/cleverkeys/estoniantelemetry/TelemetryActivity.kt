package tribixbite.cleverkeys.estoniantelemetry

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tribixbite.cleverkeys.R

/** Explicit consent, status, export and erase. No silent telemetry upload or public-file write. */
class TelemetryActivity : Activity() {
    private lateinit var status: TextView
    private var active = true
    companion object { private const val CREATE_DOCUMENT = 1832 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(column) })
        column.addView(TextView(this).apply {
            text = getString(R.string.telemetry_title)
            textSize = 24f
        })
        column.addView(TextView(this).apply {
            text = getString(R.string.telemetry_intro)
            textSize = 16f
        })
        status = TextView(this).apply {
            text = getString(R.string.telemetry_off, 0, 0)
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL
        }
        column.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (52 * resources.displayMetrics.density).toInt()))
        fun button(id: Int, click: () -> Unit) {
            column.addView(Button(this).apply {
                text = getString(id)
                isAllCaps = false
                setOnClickListener { click() }
            })
        }
        button(R.string.telemetry_start) {
            AlertDialog.Builder(this)
                .setTitle(R.string.telemetry_consent_title)
                .setMessage(R.string.telemetry_consent_body)
                .setNegativeButton(R.string.telemetry_cancel, null)
                .setPositiveButton(R.string.telemetry_confirm) { _, _ ->
                    TelemetryRuntime.enable(this) { result -> operationResult(result) }
                }.show()
        }
        button(R.string.telemetry_stop) {
            TelemetryRuntime.disable(this) { result -> operationResult(result) }
        }
        button(R.string.telemetry_export) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/x-ndjson"
                putExtra(Intent.EXTRA_TITLE, "estonian-swipe-diagnostics.jsonl")
            }, CREATE_DOCUMENT)
        }
        button(R.string.telemetry_clear) {
            AlertDialog.Builder(this)
                .setTitle(R.string.telemetry_clear_title)
                .setMessage(R.string.telemetry_clear_body)
                .setNegativeButton(R.string.telemetry_cancel, null)
                .setPositiveButton(R.string.telemetry_confirm) { _, _ ->
                    TelemetryRuntime.clear(this) { result -> operationResult(result) }
                }.show()
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        TelemetryRuntime.stats(this) { stats ->
            if (!active) return@stats
            status.text = if (stats == null) getString(R.string.telemetry_unavailable)
                else getString(if (stats.enabled) R.string.telemetry_on else R.string.telemetry_off,
                    stats.swipes, stats.corrections)
        }
    }

    private fun operationResult(ok: Boolean) {
        if (!active) return
        if (!ok) Toast.makeText(this, R.string.telemetry_failed, Toast.LENGTH_LONG).show()
        refresh()
    }

    @Deprecated("Activity result retained to avoid another dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CREATE_DOCUMENT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        TelemetryRuntime.export(this, uri) { ok ->
            if (active) Toast.makeText(this, if (ok) R.string.telemetry_exported
                else R.string.telemetry_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        active = false
        super.onDestroy()
    }
}
