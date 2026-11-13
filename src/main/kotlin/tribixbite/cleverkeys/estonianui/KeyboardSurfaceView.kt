package tribixbite.cleverkeys.estonianui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
            val DARK = Palette(0xff202127.toInt(), 0xff444851.toInt(), 0xff5b606b.toInt(),
                0xfff0f1f5.toInt(), 0xffa8c7fa.toInt(), 0xff102b50.toInt())
            val LIGHT = Palette(0xffeef3fb.toInt(), 0xffffffff.toInt(), 0xffdfe3e7.toInt(),
                0xff202124.toInt(), 0xff0b57d0.toInt(), 0xffffffff.toInt())
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
    var showCornerSymbols = true
        set(value) {
            if (field != value) { field = value; clearHold(); invalidate() }
        }
    var holdDurationMs = 300
        set(value) { field = value.coerceIn(100, 700); clearHold() }
    internal var shiftMode = ShiftMode.OFF
        set(value) { if (field != value) { field = value; invalidate() } }
    val palette get() = if (darkMode) Palette.DARK else Palette.LIGHT
    // Layout on a 0x0 view is deliberately absent; the first measure/layout supplies the geometry.
    var geometry: KeyboardGeometry = KeyboardGeometry(1, 1, language)
        private set
    private var controller = SurfaceGestureController(geometry) { onGesture?.invoke(it) }
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatArmed = false
    private var repeatStarted = false
    private val repeatTick = object : Runnable {
        override fun run() {
            if (!repeatArmed) return
            repeatStarted = true
            onGesture?.invoke(SurfaceGesture.RepeatBackspace(language))
            repeatHandler.postDelayed(this, 85L)
        }
    }
    private fun stopRepeat() {
        repeatArmed = false
        repeatHandler.removeCallbacks(repeatTick)
    }
    private val holdHandler = Handler(Looper.getMainLooper())
    private var heldKey: SurfaceKey? = null
    private var heldOptions: LongPressOptions? = null
    private var heldIndex = 0
    private var holdStartX = 0f
    private var holdStartY = 0f
    private var holdStartTimeMs = 0L
    private var holdPointerX = 0f
    private var holdPointerY = 0f
    private val holdTick = Runnable {
        val key = heldKey
        val options = key?.takeIf { geometry.hit(holdPointerX, holdPointerY) == it }
            ?.let { LongPressOptions.forKey(it, language, symbolMode, showCornerSymbols) }
        if (key != null && options != null) {
            heldOptions = options
            heldIndex = options.defaultIndex
            controller.cancel() // A held symbol must not become a swipe or ordinary tap.
            clearTrail()
            invalidate()
        }
    }
    internal val activeLongPressValue: String? get() = heldOptions?.values?.get(heldIndex)
    private fun clearHold() {
        holdHandler.removeCallbacks(holdTick)
        heldKey = null
        heldOptions = null
        heldIndex = 0
        invalidate()
    }
    private fun popupBounds(key: SurfaceKey, count: Int): RectF {
        val d = resources.displayMetrics.density
        val w = minOf(width - 8f * d, count * 54f * d).coerceAtLeast(1f)
        val left = (key.bounds.centerX - w / 2).coerceIn(4f * d, (width - w - 4f * d).coerceAtLeast(4f * d))
        val bottom = key.bounds.top + 15f * d
        return RectF(left, (bottom - 64f * d).coerceAtLeast(4f * d), left + w, bottom)
    }
    private fun selectHeldChoice(x: Float, y: Float) {
        val key = heldKey ?: return
        val options = heldOptions ?: return
        if (kotlin.math.abs(x - holdStartX) < 16f * resources.displayMetrics.density &&
            y >= key.bounds.top) return
        val box = popupBounds(key, options.values.size)
        val index = ((x - box.left) / (box.width() / options.values.size)).toInt()
            .coerceIn(options.values.indices)
        if (index != heldIndex) { heldIndex = index; invalidate() }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private companion object { const val MAX_TRAIL_POINTS = 128 }
    // Visual samples only; the gesture controller separately receives every raw historical/move point.
    private val trailX = FloatArray(MAX_TRAIL_POINTS)
    private val trailY = FloatArray(MAX_TRAIL_POINTS)
    private val trailSegment = Path()
    private var trailHead = 0
    private var trailPoints = 0
    private var trailStartX = 0f
    private var trailStartY = 0f
    internal val activeTrailPointCount get() = if (trailVisible) trailPoints else 0
    private var trailVisible = false
    private fun clearTrail() {
        trailPoints = 0
        trailHead = 0
        trailVisible = false
        invalidate()
    }
    private fun appendTrailPoint(x: Float, y: Float) {
        val index = (trailHead + trailPoints) % MAX_TRAIL_POINTS
        trailX[index] = x
        trailY[index] = y
        if (trailPoints == MAX_TRAIL_POINTS) trailHead = (trailHead + 1) % MAX_TRAIL_POINTS
        else trailPoints++
    }
    private fun addTrailPoint(x: Float, y: Float) {
        if (trailPoints == 0) return
        val threshold = (geometry.width / geometry.rows[1].size * .55f).coerceAtLeast(12f)
        if (!trailVisible && Math.hypot((x - trailStartX).toDouble(), (y - trailStartY).toDouble()) >= threshold) {
            trailVisible = true
        }
        val last = (trailHead + trailPoints - 1) % MAX_TRAIL_POINTS
        val dx = x - trailX[last]
        val dy = y - trailY[last]
        val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (!distance.isFinite()) return
        // Long event jumps get evenly spaced segments; coalesce tiny jitter without growing memory.
        val spacing = 10f * resources.displayMetrics.density
        val steps = (distance / spacing).toInt().coerceIn(1, MAX_TRAIL_POINTS)
        if (distance < spacing / 3f) return
        val fromX = trailX[last]
        val fromY = trailY[last]
        for (step in 1..steps) {
            appendTrailPoint(fromX + dx * step / steps, fromY + dy * step / steps)
        }
        if (trailVisible) invalidate()
    }
    private val rect = RectF()
    private val iconPath = Path()
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
                contentDescription = accessibleKeyLabel(key)
                isEnabled = true
                isClickable = true
                isFocusable = true
                isAccessibilityFocused = focusId == virtualViewId
                setBoundsInParent(Rect(b.left, b.top, b.right, b.bottom))
                setBoundsInScreen(Rect(location[0] + b.left, location[1] + b.top, location[0] + b.right, location[1] + b.bottom))
                addAction(AccessibilityNodeInfo.ACTION_CLICK)
                LongPressOptions.forKey(key, language, symbolMode, showCornerSymbols)?.let { options ->
                    addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    options.values.forEachIndexed { index, value ->
                        addAction(AccessibilityNodeInfo.AccessibilityAction(0x01020000 + index,
                            "Insert $value"))
                    }
                }
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
                AccessibilityNodeInfo.ACTION_LONG_CLICK -> {
                    val options = LongPressOptions.forKey(key, language, symbolMode, showCornerSymbols) ?: return false
                    onGesture?.invoke(SurfaceGesture.LongPress(options.defaultValue, language))
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
                else -> {
                    val index = action - 0x01020000
                    val options = LongPressOptions.forKey(key, language, symbolMode, showCornerSymbols)
                    val value = options?.values?.getOrNull(index) ?: return false
                    onGesture?.invoke(SurfaceGesture.LongPress(value, language))
                    sendVirtualEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                    true
                }
            }
        }
    }
    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = nodeProvider
    private fun accessibleKeyLabel(key: SurfaceKey): String = when {
        key.kind == KeyKind.SHIFT && shiftMode == ShiftMode.LOCKED -> "Caps lock on"
        key.kind == KeyKind.SHIFT && shiftMode == ShiftMode.ONCE -> "Shift on"
        key.kind == KeyKind.LETTER && shiftMode != ShiftMode.OFF && !symbolMode ->
            key.label.uppercase(java.util.Locale.ROOT)
        else -> key.accessibilityLabel
    }
    private fun sendVirtualEvent(id: Int, type: Int) {
        val parent = parent ?: return
        val event = AccessibilityEvent.obtain(type)
        event.packageName = context.packageName
        event.className = "android.widget.Button"
        event.contentDescription = geometry.keys.getOrNull(id)?.let(::accessibleKeyLabel)
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
        stopRepeat()
        clearHold()
        clearTrail()
        repeatStarted = false
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
        val density = resources.displayMetrics.density
        val inset = (3f * density).coerceAtMost(geometry.width / 120f)
        for (key in geometry.keys) {
            val b = key.bounds
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
            paint.color = when (key.kind) {
                KeyKind.ENTER -> colors.enter
                KeyKind.SHIFT -> if (shiftMode == ShiftMode.OFF) colors.modifier else colors.enter
                KeyKind.BACKSPACE, KeyKind.SYMBOLS, KeyKind.COMMA, KeyKind.PERIOD -> colors.modifier
                else -> colors.letter
            }
            val radius = if (key.kind == KeyKind.ENTER || key.kind == KeyKind.SYMBOLS) rect.height() / 2f
                else 7f * density
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.color = if (key.kind == KeyKind.ENTER ||
                (key.kind == KeyKind.SHIFT && shiftMode != ShiftMode.OFF)) colors.enterLabel else colors.label
            paint.textAlign = Paint.Align.CENTER
            paint.style = Paint.Style.FILL
            if (key.kind == KeyKind.SHIFT || key.kind == KeyKind.BACKSPACE ||
                key.kind == KeyKind.EMOJI || key.kind == KeyKind.ENTER) {
                drawOriginalIcon(canvas, key, density)
            } else {
                paint.textSize = (if (key.kind == KeyKind.SPACE) 15f else if (key.kind == KeyKind.SYMBOLS) 19f else 24f) * resources.displayMetrics.scaledDensity
                paint.typeface = Typeface.DEFAULT
                val baseline = b.centerY - (paint.ascent() + paint.descent()) / 2f
                val label = if (key.kind == KeyKind.LETTER && shiftMode != ShiftMode.OFF && !symbolMode)
                    key.label.uppercase(java.util.Locale.ROOT) else key.label.trim()
                canvas.drawText(label, b.centerX, baseline, paint)
                key.secondarySymbol?.takeIf { showCornerSymbols }?.let { hint ->
                    paint.color = if (darkMode) 0xffc3c6cc.toInt() else 0xff50545a.toInt()
                    paint.textSize = 10f * resources.displayMetrics.scaledDensity
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(hint, rect.right - 4f * density, rect.top + 12f * density, paint)
                }
            }
        }
        if (trailVisible) {
            val glow = if (darkMode) 0x558ebaff else 0x557398d6
            val core = if (darkMode) 0xffa8c7fa.toInt() else 0xff4c7dcc.toInt()
            // Draw oldest first so the moving head stays bright and the evicted tail vanishes.
            for (i in 1 until trailPoints) {
                val previous = (trailHead + i - 1) % MAX_TRAIL_POINTS
                val current = (trailHead + i) % MAX_TRAIL_POINTS
                trailSegment.reset()
                trailSegment.moveTo(trailX[previous], trailY[previous])
                trailSegment.lineTo(trailX[current], trailY[current])
                val fade = (i.toFloat() / (trailPoints - 1)).coerceIn(0f, 1f)
                trailPaint.color = glow
                trailPaint.alpha = (0x55 * fade).toInt()
                trailPaint.strokeWidth = 11f * density
                canvas.drawPath(trailSegment, trailPaint)
                trailPaint.color = core
                trailPaint.alpha = (0xff * fade).toInt()
                trailPaint.strokeWidth = 4f * density
                canvas.drawPath(trailSegment, trailPaint)
            }
        }
        val key = heldKey
        val options = heldOptions
        if (key != null && options != null) {
            val box = popupBounds(key, options.values.size)
            paint.style = Paint.Style.FILL
            paint.color = if (darkMode) 0xff343941.toInt() else 0xffdfe5ee.toInt()
            canvas.drawRoundRect(box, box.height() / 2, box.height() / 2, paint)
            val slot = box.width() / options.values.size
            options.values.forEachIndexed { index, value ->
                val x = box.left + (index + .5f) * slot
                val y = box.centerY()
                if (index == heldIndex) {
                    paint.color = colors.enter
                    canvas.drawCircle(x, y, minOf(slot * .43f, box.height() * .44f), paint)
                }
                paint.color = if (index == heldIndex) colors.enterLabel else colors.label
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 25f * resources.displayMetrics.scaledDensity
                canvas.drawText(value, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
            }
        }
    }
    /** Geometry-only artwork: no copied vendor image assets or font-dependent glyphs. */
    private fun drawOriginalIcon(canvas: Canvas, key: SurfaceKey, density: Float) {
        val b = key.bounds
        val scale = minOf(26f * density, (b.right - b.left) * .34f, (b.bottom - b.top) * .40f) / 24f
        canvas.save()
        canvas.translate(b.centerX - 12f * scale, b.centerY - 12f * scale)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        iconPath.reset()
        when (key.kind) {
            KeyKind.SHIFT -> {
                paint.style = if (shiftMode == ShiftMode.OFF) Paint.Style.STROKE else Paint.Style.FILL
                iconPath.moveTo(12f, 2f); iconPath.lineTo(22f, 12f)
                iconPath.lineTo(17f, 12f); iconPath.lineTo(17f, 21f)
                iconPath.lineTo(7f, 21f); iconPath.lineTo(7f, 12f)
                iconPath.lineTo(2f, 12f); iconPath.close()
                canvas.drawPath(iconPath, paint)
                if (shiftMode == ShiftMode.LOCKED) {
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(7f, 23f, 17f, 23f, paint)
                }
            }
            KeyKind.BACKSPACE -> {
                iconPath.moveTo(9f, 4f); iconPath.lineTo(22f, 4f)
                iconPath.lineTo(22f, 20f); iconPath.lineTo(9f, 20f)
                iconPath.lineTo(2f, 12f); iconPath.close()
                canvas.drawPath(iconPath, paint)
                canvas.drawLine(12f, 9f, 18f, 15f, paint)
                canvas.drawLine(18f, 9f, 12f, 15f, paint)
            }
            KeyKind.EMOJI -> {
                paint.strokeWidth = 2f
                canvas.drawCircle(12f, 12f, 9f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(8.5f, 10f, 1f, paint)
                canvas.drawCircle(15.5f, 10f, 1f, paint)
                paint.style = Paint.Style.STROKE
                canvas.drawArc(7f, 10f, 17f, 18f, 20f, 140f, false, paint)
            }
            KeyKind.ENTER -> {
                paint.strokeWidth = 2.3f
                iconPath.moveTo(20f, 6f); iconPath.lineTo(20f, 14f)
                iconPath.lineTo(5f, 14f); iconPath.moveTo(10f, 9f)
                iconPath.lineTo(5f, 14f); iconPath.lineTo(10f, 19f)
                canvas.drawPath(iconPath, paint)
            }
            else -> Unit
        }
        paint.style = Paint.Style.FILL
        canvas.restore()
    }
    private fun movePointer(x: Float, y: Float, timeMs: Long) {
        holdPointerX = x
        holdPointerY = y
        val key = heldKey
        val options = heldOptions
        if (key != null && options != null) {
            val box = popupBounds(key, options.values.size)
            val margin = 18f * resources.displayMetrics.density
            val crossKey = geometry.hit(x, y)?.let { it != key && it.kind == KeyKind.LETTER } == true
            val beyondPopup = x < box.left - margin || x > box.right + margin
            if (crossKey && beyondPopup) {
                // A far cross-key move after a pause is a real swipe, not a popup choice.
                clearHold()
                controller.down(holdStartX, holdStartY, holdStartTimeMs)
                controller.move(x, y, timeMs)
                trailStartX = holdStartX
                trailStartY = holdStartY
                appendTrailPoint(holdStartX, holdStartY)
                addTrailPoint(x, y)
            } else selectHeldChoice(x, y)
            return
        }
        controller.move(x, y, timeMs)
        addTrailPoint(x, y)
        if (key != null && geometry.hit(x, y) != key) clearHold()
        if (repeatArmed && geometry.hit(x, y)?.kind != KeyKind.BACKSPACE) stopRepeat()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopRepeat()
                clearHold()
                clearTrail()
                repeatStarted = false
                controller.down(event.x, event.y, event.eventTime)
                val touched = geometry.hit(event.x, event.y)
                if (touched != null && LongPressOptions.forKey(touched, language, symbolMode,
                        showCornerSymbols) != null) {
                    heldKey = touched
                    holdStartX = event.x
                    holdStartY = event.y
                    holdPointerX = event.x
                    holdPointerY = event.y
                    holdStartTimeMs = event.eventTime
                    holdHandler.postDelayed(holdTick, holdDurationMs.toLong())
                }
                if (!symbolMode && touched?.kind == KeyKind.LETTER) {
                    trailStartX = event.x
                    trailStartY = event.y
                    appendTrailPoint(event.x, event.y)
                }
                if (geometry.hit(event.x, event.y)?.kind == KeyKind.BACKSPACE) {
                    repeatArmed = true
                    repeatHandler.postDelayed(repeatTick, 380L)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) {
                    movePointer(event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i))
                }
                movePointer(event.x, event.y, event.eventTime)
            }
            MotionEvent.ACTION_UP -> {
                stopRepeat()
                val options = heldOptions
                val key = heldKey
                if (options != null && key != null) {
                    selectHeldChoice(event.x, event.y)
                    val box = popupBounds(key, options.values.size)
                    val margin = 18f * resources.displayMetrics.density
                    if (event.x in (box.left - margin)..(box.right + margin) &&
                        event.y in (box.top - margin)..(key.bounds.bottom + margin)) {
                        onGesture?.invoke(SurfaceGesture.LongPress(options.values[heldIndex], language))
                    }
                    controller.cancel()
                } else if (repeatStarted) controller.cancel() else controller.up(event.x, event.y, event.eventTime)
                clearHold()
                repeatStarted = false
                clearTrail()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                stopRepeat()
                clearHold()
                repeatStarted = false
                controller.cancel()
                clearTrail()
            }
            else -> return false
        }
        return true
    }
    override fun onDetachedFromWindow() {
        stopRepeat()
        clearHold()
        clearTrail()
        controller.cancel()
        super.onDetachedFromWindow()
    }
}
