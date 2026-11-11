package tribixbite.cleverkeys.estonianui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider

/** Standalone surface: emits gestures only. No decoder, dictionary or editor dependency. */
class KeyboardSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {
    data class Palette(val surface: Int, val letter: Int, val modifier: Int, val label: Int, val enter: Int, val enterLabel: Int) {
        companion object {
            val DARK = Palette(0xff1f1f1f.toInt(), 0xff33383f.toInt(), 0xff444746.toInt(),
                0xffe3e3e3.toInt(), 0xffa8c7fa.toInt(), 0xff152c4b.toInt())
            val LIGHT = Palette(0xfff0f1f4.toInt(), 0xffffffff.toInt(), 0xffd9dce3.toInt(),
                0xff252932.toInt(), 0xffa8c7fa.toInt(), 0xff152c4b.toInt())
        }
    }
    var onGesture: ((SurfaceGesture) -> Unit)? = null
    var language = KeyboardLanguage.ESTONIAN
        set(value) {
            if (field != value) { field = value; rebuild() }
        }
    /** The service owns page transitions: set this after handling a SYMBOLS tap. */
    var symbolMode = false
        set(value) {
            if (field != value) { field = value; rebuild() }
        }
    var darkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        set(value) { field = value; invalidate() }
    val palette get() = if (darkMode) Palette.DARK else Palette.LIGHT
    // Layout on a 0x0 view is deliberately absent; the first measure/layout supplies the geometry.
    var geometry: KeyboardGeometry = KeyboardGeometry(1, 1, language)
        private set
    private var controller = SurfaceGestureController(geometry) { onGesture?.invoke(it) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var focusId = View.NO_ID
    private val nodeProvider = object : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            if (virtualViewId == View.NO_ID) {
                return AccessibilityNodeInfo.obtain(this@KeyboardSurfaceView).apply {
                    className = KeyboardSurfaceView::class.java.name
                    for (index in geometry.keys.indices) addChild(this@KeyboardSurfaceView, index)
                }
            }
            val key = geometry.keys.getOrNull(virtualViewId) ?: return null
            val b = key.bounds
            val location = IntArray(2)
            getLocationOnScreen(location)
            return AccessibilityNodeInfo.obtain().apply {
                setSource(this@KeyboardSurfaceView, virtualViewId)
                setParent(this@KeyboardSurfaceView)
                packageName = context.packageName
                className = "android.widget.Button"
                contentDescription = key.accessibilityLabel
                isEnabled = true
                isClickable = true
                isFocusable = true
                isAccessibilityFocused = focusId == virtualViewId
                setBoundsInParent(Rect(b.left, b.top, b.right, b.bottom))
                setBoundsInScreen(Rect(location[0] + b.left, location[1] + b.top, location[0] + b.right, location[1] + b.bottom))
                addAction(AccessibilityNodeInfo.ACTION_CLICK)
                addAction(if (focusId == virtualViewId) AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                    else AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            }
        }
        override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            val key = geometry.keys.getOrNull(virtualViewId) ?: return false
            return when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    onGesture?.invoke(SurfaceGesture.Tap(key, language))
                    sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                    true
                }
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    if (focusId != View.NO_ID) sendVirtualEvent(focusId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    focusId = virtualViewId
                    sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    invalidate(); true
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    if (focusId != virtualViewId) false else {
                        focusId = View.NO_ID
                        sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                        invalidate(); true
                    }
                }
                else -> false
            }
        }
    }
    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = nodeProvider
    private fun sendVirtualEvent(id: Int, type: Int) {
        val parent = parent ?: return
        val event = AccessibilityEvent.obtain(type)
        event.packageName = context.packageName
        event.className = "android.widget.Button"
        event.contentDescription = geometry.keys.getOrNull(id)?.accessibilityLabel
        event.setSource(this, id)
        parent.requestSendAccessibilityEvent(this, event)
    }
    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (!accessibilityManagerEnabled()) return super.dispatchHoverEvent(event)
        val key = geometry.hit(event.x, event.y) ?: return super.dispatchHoverEvent(event)
        val id = geometry.keys.indexOf(key)
        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            if (focusId != id) sendVirtualEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
            sendVirtualEvent(id, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            return true
        }
        return super.dispatchHoverEvent(event)
    }
    private fun accessibilityManagerEnabled(): Boolean =
        (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager).isTouchExplorationEnabled

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ideal = (260f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(ideal, heightMeasureSpec))
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }
    private fun rebuild() {
        controller.cancel()
        if (width > 0 && height > 0) {
            geometry = KeyboardGeometry(width, height, language, symbolMode)
            controller = SurfaceGestureController(geometry) { onGesture?.invoke(it) }
        }
        focusId = View.NO_ID
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val colors = palette
        canvas.drawColor(colors.surface)
        val inset = (3f * resources.displayMetrics.density).coerceAtMost(geometry.width / 120f)
        for (key in geometry.keys) {
            val b = key.bounds
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
            paint.color = when (key.kind) {
                KeyKind.ENTER -> colors.enter
                KeyKind.SHIFT, KeyKind.BACKSPACE, KeyKind.SYMBOLS, KeyKind.COMMA, KeyKind.EMOJI, KeyKind.LANGUAGE, KeyKind.PERIOD -> colors.modifier
                else -> colors.letter
            }
            canvas.drawRoundRect(rect, 6f * resources.displayMetrics.density, 6f * resources.displayMetrics.density, paint)
            paint.color = if (key.kind == KeyKind.ENTER) colors.enterLabel else colors.label
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = (if (key.kind == KeyKind.SPACE || key.kind == KeyKind.SYMBOLS || key.kind == KeyKind.LANGUAGE) 15f else 22f) * resources.displayMetrics.scaledDensity
            paint.typeface = android.graphics.Typeface.DEFAULT
            val baseline = b.centerY - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(key.label.trim(), b.centerX, baseline, paint)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> controller.down(event.x, event.y, event.eventTime)
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) controller.move(event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i))
                controller.move(event.x, event.y, event.eventTime)
            }
            MotionEvent.ACTION_UP -> controller.up(event.x, event.y, event.eventTime)
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> controller.cancel()
            else -> return false
        }
        return true
    }
    override fun onDetachedFromWindow() { controller.cancel(); super.onDetachedFromWindow() }
}
