package app.byzantine.pitchmonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.byzantine.pitchmonitor.audio.PitchHistory
import app.byzantine.pitchmonitor.music.ByzantineGrid
import app.byzantine.pitchmonitor.music.GridNote
import app.byzantine.pitchmonitor.music.Moria
import app.byzantine.pitchmonitor.music.Pitch
import app.byzantine.pitchmonitor.music.Role
import app.byzantine.pitchmonitor.music.ScaleGrid
import app.byzantine.pitchmonitor.music.WesternGrid
import kotlin.math.abs
import kotlin.math.floor

/** A state that changes on every display frame, used to drive continuous redraws. */
@Composable
fun rememberFrameTick(): State<Long> {
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { tick.longValue = it }
    }
    return tick
}

/**
 * Vertical view of the graph. Plain fields are read and written while drawing (redrawn every
 * frame anyway); [autoFollow] is snapshot state so the "re-centre" button can react to it.
 */
class GraphViewport(var centerCents: Double, var spanCents: Double) {
    var autoFollow by mutableStateOf(true)
    /** Frames scrolled back into the history while paused. */
    var scrollBackFrames = 0.0

    fun recenter(on: Double?) {
        autoFollow = true
        scrollBackFrames = 0.0
        if (on != null) centerCents = on
    }

    companion object {
        const val DEFAULT_SPAN = 1500.0
        const val MIN_SPAN = 250.0
        const val MAX_SPAN = 4800.0
    }
}

data class GraphStyle(
    val windowSeconds: Float,
    val toleranceMoria: Float,
    val showWestern: Boolean,
    val westernRefCents: Double,
    val isonCents: Double?,
)

@Composable
fun PitchGraph(
    history: PitchHistory,
    grid: ScaleGrid,
    style: GraphStyle,
    paused: Boolean,
    viewport: GraphViewport,
    onNoteTapped: (GridNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick by rememberFrameTick()
    val measurer = rememberTextMeasurer()
    val labels = remember { LabelCache(measurer) }
    val buf = remember { FloatArray(history.capacity) }
    // Latest values captured by the draw pass, used by the gesture handlers.
    val drawn = remember { DrawnState() }

    Canvas(
        modifier
            .pointerInput(paused) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val h = size.height.toDouble()
                    if (zoom != 1f) {
                        viewport.spanCents = (viewport.spanCents / zoom)
                            .coerceIn(GraphViewport.MIN_SPAN, GraphViewport.MAX_SPAN)
                    }
                    if (abs(pan.y) > 0.5f) {
                        viewport.centerCents += pan.y / h * viewport.spanCents
                        viewport.autoFollow = false
                    }
                    if (paused && drawn.pxPerFrame > 0) {
                        viewport.scrollBackFrames = (viewport.scrollBackFrames + pan.x / drawn.pxPerFrame)
                            .coerceIn(0.0, maxOf(0.0, (history.size() - drawn.visibleFrames).toDouble()))
                    }
                }
            }
            .pointerInput(grid) {
                detectTapGestures(
                    onDoubleTap = {
                        viewport.spanCents = GraphViewport.DEFAULT_SPAN
                        viewport.recenter(drawn.latestCents.takeIf { c -> !c.isNaN() })
                    },
                    onTap = { pos ->
                        // Taps on the label column pick the ison note.
                        if (pos.x > drawn.labelColumnPx) return@detectTapGestures
                        val cents = drawn.yToCents(pos.y)
                        val note = grid.nearest(cents)
                        if (abs(drawn.centsToY(note.cents) - pos.y) < 28.dp.toPx()) onNoteTapped(note)
                    },
                )
            }
    ) {
        tick // read so the canvas redraws every frame
        drawGraph(history, grid, style, paused, viewport, labels, buf, drawn)
    }
}

private class DrawnState {
    var pxPerFrame = 0.0
    var visibleFrames = 0
    var latestCents = Double.NaN
    var labelColumnPx = 0f
    var height = 1f
    var center = 0.0
    var span = 1.0
    fun centsToY(c: Double) = (height / 2 - (c - center) / span * height).toFloat()
    fun yToCents(y: Float) = center + (height / 2 - y) / height * span
}

/** Caches measured label text; labels repeat every frame. */
private class LabelCache(private val measurer: TextMeasurer) {
    private val cache = HashMap<String, TextLayoutResult>()
    fun get(text: String, color: Color, sizeSp: Int = 13, bold: Boolean = false): TextLayoutResult =
        cache.getOrPut("$text|$sizeSp|$bold|${color.value}") {
            measurer.measure(
                text,
                TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
            )
        }.also { if (cache.size > 600) cache.clear() }
}

private fun DrawScope.drawGraph(
    history: PitchHistory,
    grid: ScaleGrid,
    style: GraphStyle,
    paused: Boolean,
    vp: GraphViewport,
    labels: LabelCache,
    buf: FloatArray,
    drawn: DrawnState,
) {
    val w = size.width
    val h = size.height
    if (w <= 0 || h <= 0) return

    val frameSec = history.frameSeconds
    val visible = (style.windowSeconds / frameSec).toInt().coerceIn(10, history.capacity)
    val skip = if (paused) vp.scrollBackFrames.toInt() else 0
    if (!paused) vp.scrollBackFrames = 0.0
    val n = history.snapshot(visible, skip, buf)
    val newestAbs = history.totalWritten() - 1 - skip

    val labelCol = 58.dp.toPx()
    val rightX = w - 18.dp.toPx()
    val pxPerFrame = (rightX - labelCol) / visible.toDouble()

    // Latest voiced pitch (within the last ~0.3 s) for auto-follow and the "now" marker.
    var latest = Double.NaN
    for (k in n - 1 downTo maxOf(0, n - 26)) if (!buf[k].isNaN()) { latest = buf[k].toDouble(); break }
    val nowVoiced = n > 0 && !buf[n - 1].isNaN()

    if (vp.autoFollow && !latest.isNaN() && !paused) {
        // Keep the voice inside the middle 60% of the view; glide rather than jump.
        val margin = vp.spanCents * 0.3
        val off = latest - vp.centerCents
        if (abs(off) > margin) vp.centerCents += (off - margin * Math.signum(off)) * 0.12
    }

    val center = vp.centerCents
    val span = vp.spanCents
    drawn.pxPerFrame = pxPerFrame
    drawn.visibleFrames = visible
    drawn.latestCents = latest
    drawn.labelColumnPx = labelCol
    drawn.height = h
    drawn.center = center
    drawn.span = span
    fun y(c: Double) = drawn.centsToY(c)

    val low = center - span / 2
    val high = center + span / 2

    // Time lines, one per second, scrolling with the trace.
    val fps = 1.0 / frameSec
    val firstSecond = floor((newestAbs - visible) / fps).toLong()
    val lastSecond = floor(newestAbs / fps).toLong()
    for (s in firstSecond..lastSecond) {
        val x = (rightX - (newestAbs - s * fps) * pxPerFrame).toFloat()
        if (x < labelCol) continue
        drawLine(Palette.WoodLight, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
    }

    // Western reference lines (faint, dashed) behind a Byzantine grid.
    if (style.showWestern && grid !is WesternGrid) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
        for (note in WesternGrid(style.westernRefCents).notesIn(low, high)) {
            val yy = y(note.cents)
            drawLine(Palette.GridLine.copy(alpha = 0.45f), Offset(labelCol, yy), Offset(w, yy), 1f, pathEffect = dash)
            val t = labels.get(note.label, Palette.Muted.copy(alpha = 0.8f), 10)
            drawText(t, topLeft = Offset(w - t.size.width - 4.dp.toPx(), yy - t.size.height - 1f))
        }
    }

    val notes = grid.notesIn(low - 400, high + 400)
    val nearestNow = if (!latest.isNaN()) grid.nearest(latest) else null
    val tolCents = Moria.toCents(style.toleranceMoria.toDouble())

    // Tolerance band around the note currently being sung.
    if (nearestNow != null) {
        val top = y(nearestNow.cents + tolCents)
        val bottom = y(nearestNow.cents - tolCents)
        drawRect(Palette.Gold.copy(alpha = 0.10f), Offset(labelCol, top), Size(w - labelCol, bottom - top))
    }

    // Scale degree lines.
    for (note in notes) {
        if (note.cents < low - 50 || note.cents > high + 50) continue
        val yy = y(note.cents)
        val (color, stroke) = when (note.role) {
            Role.BASE -> Palette.Gold to 2.5.dp.toPx()
            Role.DOMINANT -> Palette.Rose.copy(alpha = 0.85f) to 1.6.dp.toPx()
            Role.NORMAL -> Palette.GridLine to 1.dp.toPx()
        }
        drawLine(color, Offset(labelCol, yy), Offset(w, yy), stroke)
    }

    // Ison line.
    style.isonCents?.let { c ->
        if (c in low..high) {
            val yy = y(c)
            drawLine(
                Palette.Gold.copy(alpha = 0.7f), Offset(labelCol, yy), Offset(w, yy), 5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f)),
            )
        }
    }

    // The pitch trace, coloured by distance from the nearest scale note.
    val noteCents = DoubleArray(notes.size) { notes[it].cents }
    val traceW = 3.dp.toPx()
    var prevX = 0f
    var prevY = 0f
    var prevC = Double.NaN
    for (k in 0 until n) {
        val c = buf[k].toDouble()
        val x = (rightX - (n - 1 - k) * pxPerFrame).toFloat()
        if (c.isNaN()) { prevC = Double.NaN; continue }
        val yy = y(c)
        if (!prevC.isNaN() && abs(c - prevC) < 120 && x >= labelCol) {
            val dev = abs(c - nearestOf(noteCents, c))
            val color = when {
                dev <= tolCents -> Palette.OnPitch
                dev <= tolCents * 2 -> Palette.NearPitch
                else -> Palette.OffPitch
            }
            drawLine(color, Offset(prevX, prevY), Offset(x, yy), traceW, cap = StrokeCap.Round)
        }
        prevX = x; prevY = yy; prevC = c
    }

    // "Now" marker.
    if (nowVoiced && !paused) {
        val c = buf[n - 1].toDouble()
        val yy = y(c)
        drawCircle(Palette.Parchment, 6.dp.toPx(), Offset(rightX, yy))
        drawCircle(Palette.Ink, 3.dp.toPx(), Offset(rightX, yy))
    }

    // Label column on top, so labels stay readable over the trace.
    drawRect(Palette.Ink.copy(alpha = 0.85f), Offset.Zero, Size(labelCol, h))
    for (note in notes) {
        if (note.cents < low - 30 || note.cents > high + 30) continue
        val yy = y(note.cents)
        val isNow = nearestNow != null && note.cents == nearestNow.cents
        val color = when {
            isNow -> Palette.Ink
            note.role == Role.BASE -> Palette.Gold
            note.role == Role.DOMINANT -> Palette.Rose
            else -> Palette.Parchment
        }
        val t = labels.get(note.label, color, if (note.role == Role.NORMAL) 13 else 15, bold = note.role != Role.NORMAL || isNow)
        val tx = labelCol - t.size.width - 8.dp.toPx()
        val ty = yy - t.size.height / 2f
        if (isNow) {
            val pad = 4.dp.toPx()
            drawRoundRect(
                Palette.Gold, Offset(tx - pad, ty - 1f), Size(t.size.width + 2 * pad, t.size.height + 2f),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
        drawText(t, topLeft = Offset(tx, ty))
    }
    if (style.isonCents != null && style.isonCents in low..high) {
        val t = labels.get("♪", Palette.Gold, 13, bold = true)
        drawText(t, topLeft = Offset(2.dp.toPx(), y(style.isonCents) - t.size.height / 2f))
    }

    // Interval steps (moria) between neighbouring degrees, a reading aid for Byzantine scales.
    if (grid is ByzantineGrid && span <= 2600) {
        for (i in 0 until notes.size - 1) {
            val a = notes[i]; val b = notes[i + 1]
            val mid = (a.cents + b.cents) / 2
            if (mid < low || mid > high) continue
            val steps = Math.round(Moria.fromCents(b.cents - a.cents)).toInt()
            val t = labels.get(steps.toString(), Palette.Muted.copy(alpha = 0.7f), 10)
            drawText(t, topLeft = Offset(labelCol + 4.dp.toPx(), y(mid) - t.size.height / 2f))
        }
    }

    if (n == 0) {
        val t = labels.get("Sing or play a note…", Palette.Muted, 15)
        drawText(t, topLeft = Offset((w - t.size.width) / 2, (h - t.size.height) / 2))
    }
}

/** Nearest value in a sorted array (binary search). */
private fun nearestOf(sorted: DoubleArray, x: Double): Double {
    if (sorted.isEmpty()) return x
    var lo = 0
    var hi = sorted.size - 1
    while (hi - lo > 1) {
        val mid = (lo + hi) ushr 1
        if (sorted[mid] < x) lo = mid else hi = mid
    }
    return if (abs(sorted[lo] - x) <= abs(sorted[hi] - x)) sorted[lo] else sorted[hi]
}

/** Hz of a pitch, for labels. */
fun hzOf(cents: Double) = Pitch.centsToHz(cents)
