package app.pitchmonitor.byz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What the graph shows besides the grid; rebuilt from [Prefs] whenever settings change. */
class GraphStyle(
    val pitchColor: Int,
    val beatsColor: Int,
    val metronomeColor: Int,
    val beatLines: Boolean,
    val blink: Boolean,
    val bpm: Int,
    val meter: Int,
    val showTuner: Boolean,
    val showHz: Boolean,
    val smoothing: Int,
    val autoScroll: Boolean,
    val scrollSpeed: Int,
)

/** The scrolling pitch graph with the scale lines, the tuner and the pitch trace. */
class PitchView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    var track: PitchTrack = PitchTrack()
    var grid: Grid? = null
    var style: GraphStyle? = null
    var hZoom = 1f
    var vZoom = 1f

    /** Called when a pinch changes the zoom (to save it). */
    var onZoom: ((Float, Float) -> Unit)? = null

    /** Frame shown at the right edge while playing back; null follows the newest reading. */
    var playbackFrame: Double? = null

    /** Frozen display (HOLD, or a loaded recording). */
    var held = false
        set(v) {
            if (v && !field) heldEnd = track.count - 1.0
            field = v
        }
    private var heldEnd = 0.0

    var centerCents = 0.0
    private var manualUntil = 0L

    // Blinking metronome
    private var lastBeat = -1L
    private var flashAt = 0L

    private val linePaint = Paint().apply { strokeWidth = dp }
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.2f * dp; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f * sp; textAlign = Paint.Align.RIGHT }
    private val moriaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * sp; textAlign = Paint.Align.RIGHT; color = 0xFFB0B0B0.toInt()
    }
    private val axisPaint = Paint().apply { color = 0xFFC8C8C8.toInt(); strokeWidth = 1.2f * dp }
    private val tunerName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f * sp; color = Color.WHITE; textAlign = Paint.Align.CENTER
    }
    private val tunerTick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.8f * dp }
    private val tunerLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * sp; color = 0xFFCCCCCC.toInt(); textAlign = Paint.Align.CENTER
    }
    private val hzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f * sp; color = 0xFFCCCCCC.toInt(); textAlign = Paint.Align.RIGHT
    }
    private val fillPaint = Paint()
    private val marker = Path()

    private val pxPerCent get() = 0.22f * dp * vZoom
    private val pxPerFrame get() = (40f * dp * hZoom / FRAMES_PER_SECOND).toFloat()

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postInvalidateOnAnimation()
    }

    /** Frame index at the right edge. */
    private fun endFrame(): Double = playbackFrame ?: if (held) heldEnd else track.count - 1.0

    private fun yOf(cents: Double): Float = (height / 2f - (cents - centerCents) * pxPerCent).toFloat()

    /** Average of the latest voiced readings, for the tuner (NaN when silent). */
    private fun tunerPitch(end: Long, smoothing: Int): Double {
        val n = smoothing * 2 + 1
        var sum = 0.0
        var got = 0
        var i = end
        while (i > end - n * 2 && got < n) {
            val v = track.get(i)
            if (!v.isNaN()) { sum += v; got++ } else if (i > end - 4 && got == 0) return Double.NaN
            i--
        }
        return if (got == 0) Double.NaN else sum / got
    }

    fun recenter() {
        val c = track.lastVoicedCents
        if (!c.isNaN()) centerCents = c
        manualUntil = 0
    }

    override fun onDraw(canvas: Canvas) {
        val g = grid ?: return
        val st = style ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val now = SystemClock.uptimeMillis()
        val end = endFrame()
        val endIdx = floor(end).toLong()
        val live = playbackFrame == null && !held

        // Follow the voice.
        if (st.autoScroll && now > manualUntil && live) {
            val last = track.lastVoicedCents
            if (!last.isNaN() && now - track.lastVoicedAt < 1500) {
                val k = 0.012 * st.scrollSpeed
                centerCents += (last - centerCents) * k
            }
        } else if (playbackFrame != null && st.autoScroll && now > manualUntil) {
            val v = track.get(endIdx)
            if (!v.isNaN()) centerCents += (v - centerCents) * 0.012 * st.scrollSpeed
        }

        val low = centerCents - (h / 2 + 40 * dp) / pxPerCent
        val high = centerCents + (h / 2 + 40 * dp) / pxPerCent
        val lines = g.lines(low, high)

        // Label column.
        labelPaint.typeface = if (g.monoLabels) Typeface.MONOSPACE else Typeface.DEFAULT
        var labelCol = 26f * dp
        for (l in lines) l.label?.let { labelCol = max(labelCol, labelPaint.measureText(it) + 10 * dp) }

        // Metronome flash.
        val framesPerBeat = FRAMES_PER_SECOND * 60.0 / st.bpm.coerceAtLeast(1)
        if (st.blink && live) {
            val beat = floor(track.count / framesPerBeat).toLong()
            if (beat != lastBeat) {
                if (lastBeat >= 0) flashAt = now
                lastBeat = beat
            }
            val age = now - flashAt
            if (age < 150) {
                fillPaint.color = st.metronomeColor
                fillPaint.alpha = (255 * (1 - age / 150f)).toInt()
                canvas.drawRect(0f, 0f, w, h, fillPaint)
            }
        }

        // Scale lines.
        for (l in lines) {
            if (l.color == 0) continue
            val y = yOf(l.cents)
            linePaint.color = l.color
            linePaint.strokeWidth = l.widthDp * dp
            canvas.drawLine(labelCol, y, w, y, linePaint)
        }

        // Beat lines.
        val ppf = pxPerFrame
        val firstFrame = end - (w - labelCol) / ppf
        if (st.beatLines) {
            val perBar = when (st.meter) { 0 -> 4; 1 -> 3; else -> 0 }
            var k = ceil(max(0.0, firstFrame) / framesPerBeat).toLong()
            while (k * framesPerBeat <= end) {
                val x = (w - (end - k * framesPerBeat) * ppf).toFloat()
                linePaint.color = st.beatsColor
                linePaint.strokeWidth = if (perBar > 0 && k % perBar == 0L) 2 * dp else dp
                canvas.drawLine(x, 0f, x, h, linePaint)
                k++
            }
        }

        // Pitch trace.
        tracePaint.color = st.pitchColor
        var prevX = 0f
        var prevY = 0f
        var prev = Float.NaN
        val startIdx = max(0L, floor(firstFrame).toLong() - 1)
        var i = startIdx
        while (i <= endIdx) {
            val v = track.get(i)
            val x = (w - (end - i) * ppf).toFloat()
            if (!v.isNaN()) {
                val y = yOf(v.toDouble())
                if (!prev.isNaN() && abs(v - prev) < 150 && x >= labelCol) canvas.drawLine(prevX, prevY, x, y, tracePaint)
                prevX = x; prevY = y
            }
            prev = v
            i++
        }

        // Axis and labels.
        fillPaint.color = Color.BLACK
        fillPaint.alpha = 255
        canvas.drawRect(0f, 0f, labelCol - 1, h, fillPaint)
        canvas.drawLine(labelCol, 0f, labelCol, h, axisPaint)
        val fm = labelPaint.fontMetrics
        for (l in lines) {
            val text = l.label ?: continue
            labelPaint.color = l.labelColor
            canvas.drawText(text, labelCol - 4 * dp, yOf(l.cents) - (fm.ascent + fm.descent) / 2, labelPaint)
        }

        // Step sizes in moria (Byzantine).
        if (g.showsMoria) {
            val mfm = moriaPaint.fontMetrics
            for (k in 0 until lines.size - 1) {
                val a = lines[k]; val b = lines[k + 1]
                val steps = Math.round((b.cents - a.cents) / Pitch.CENTS_PER_MORIO).toInt()
                val y = yOf((a.cents + b.cents) / 2) - (mfm.ascent + mfm.descent) / 2
                canvas.drawText(steps.toString(), w - 6 * dp, y, moriaPaint)
            }
        }

        // Tuner and frequency.
        val p = tunerPitch(endIdx, st.smoothing)
        if (!p.isNaN()) {
            if (st.showTuner) drawTuner(canvas, g, p, w)
            if (st.showHz) {
                canvas.drawText(String.format(Locale.US, "%4.0fHz", Pitch.centsToHz(p)), w * 4 / 5, 36 * dp, hzPaint)
            }
        }

        postInvalidateOnAnimation()
    }

    private fun drawTuner(canvas: Canvas, g: Grid, p: Double, w: Float) {
        val near = g.tunerLines(p - 800, p + 800).minByOrNull { abs(it.cents - p) } ?: return
        val cx = w / 2
        canvas.drawText(near.name, cx, 36 * dp, tunerName)
        val scale = 1.44f * dp
        val dev = p - near.cents
        val rowY = 46 * dp
        // Marker for the sung pitch.
        marker.reset()
        marker.moveTo(cx - 4 * dp, rowY - 6 * dp)
        marker.lineTo(cx + 4 * dp, rowY - 6 * dp)
        marker.lineTo(cx, rowY - 1.5f * dp)
        marker.close()
        fillPaint.color = Color.WHITE
        canvas.drawPath(marker, fillPaint)
        // Ticks every 10 cents.
        var c = floor((dev - 110) / 10) * 10
        while (c <= dev + 110) {
            val x = cx + ((c - dev) * scale).toFloat()
            canvas.drawLine(x, rowY, x, rowY + 2.5f * dp, tunerTick)
            c += 10
        }
        // The scale notes within reach.
        for (l in g.tunerLines(p - 120, p + 120)) {
            val x = cx + ((l.cents - p) * scale).toFloat()
            canvas.drawLine(x, rowY, x, rowY + 5 * dp, tunerTick)
            canvas.drawText(l.name, x, rowY + 16 * dp, tunerLabel)
        }
    }

    // --- touch --------------------------------------------------------------------------

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val sx = if (d.previousSpanX > 40 * dp) d.currentSpanX / d.previousSpanX else 1f
            val sy = if (d.previousSpanY > 40 * dp) d.currentSpanY / d.previousSpanY else 1f
            hZoom = (hZoom * sx).coerceIn(1f, 4f)
            vZoom = (vZoom * sy).coerceIn(0.5f, 2f)
            return true
        }

        override fun onScaleEnd(d: ScaleGestureDetector) {
            onZoom?.invoke(hZoom, vZoom)
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaler.isInProgress) return true
            if (abs(dx) > abs(dy) && (held || playbackFrame != null)) {
                if (held) {
                    val visible = width / pxPerFrame
                    heldEnd = (heldEnd + dx / pxPerFrame).coerceIn(min(visible.toDouble(), track.count - 1.0), track.count - 1.0)
                }
            } else {
                centerCents -= dy / pxPerCent
                manualUntil = SystemClock.uptimeMillis() + 3000
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            recenter()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }
}
