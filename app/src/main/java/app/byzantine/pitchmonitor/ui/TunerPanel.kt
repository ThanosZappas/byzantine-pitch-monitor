package app.byzantine.pitchmonitor.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.byzantine.pitchmonitor.audio.AudioEngine
import app.byzantine.pitchmonitor.music.ByzantineGrid
import app.byzantine.pitchmonitor.music.Moria
import app.byzantine.pitchmonitor.music.Pitch
import app.byzantine.pitchmonitor.music.ScaleGrid
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Big readout of the note being sung: degree name, deviation (moria for Byzantine scales,
 * cents for western), a needle meter, Hz and the western note name.
 */
@Composable
fun TunerPanel(
    engine: AudioEngine,
    grid: ScaleGrid,
    toleranceMoria: Float,
    westernRefCents: Double,
    showHz: Boolean,
    modifier: Modifier = Modifier,
) {
    val tick by rememberFrameTick()
    tick // re-read the engine every frame
    val now = SystemClock.uptimeMillis()
    val live = !engine.latestCents.isNaN()
    // Hold the last reading briefly so the display doesn't blink between syllables.
    val recent = now - engine.lastVoicedAtMs < 600
    val cents = if (live) engine.latestCents else if (recent) engine.lastVoicedCents else Double.NaN
    val active = !cents.isNaN()

    val byz = grid is ByzantineGrid
    val note = if (active) grid.nearest(cents) else null
    val devCents = if (note != null) cents - note.cents else 0.0
    val devMoria = Moria.fromCents(devCents)
    val tolCents = Moria.toCents(toleranceMoria.toDouble())
    val color = when {
        note == null -> Palette.Muted
        abs(devCents) <= tolCents -> Palette.OnPitch
        abs(devCents) <= tolCents * 2 -> Palette.NearPitch
        else -> Palette.OffPitch
    }

    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note?.label ?: "—",
                color = if (note == null) Palette.Muted else if (live) Palette.Gold else Palette.GoldDim,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 110.dp),
            )
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = when {
                            note == null -> ""
                            byz -> formatSigned(devMoria) + " μ"
                            else -> formatSigned(devCents, decimals = 0) + " ¢"
                        },
                        color = color,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (note != null && byz) {
                        Text(
                            "  (" + formatSigned(devCents, decimals = 0) + "¢)",
                            color = Palette.Muted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                val details = buildList {
                    if (active && showHz) add(String.format(Locale.US, "%.1f Hz", Pitch.centsToHz(cents)))
                    if (active) add(Pitch.describe(cents, westernRefCents))
                }.joinToString("  ·  ")
                Text(
                    details.ifEmpty { engine.error ?: if (engine.isRunning) "Listening…" else "Microphone off" },
                    color = if (engine.error != null) Palette.OffPitch else Palette.Muted,
                    fontSize = 14.sp,
                )
            }
            LevelMeter(engine.latestLevelDb, Modifier.width(10.dp).height(56.dp))
        }
        Spacer(Modifier.height(6.dp))
        DeviationMeter(
            fraction = if (note == null) null else (devCents / (if (byz) Moria.toCents(6.0) else 50.0)).toFloat(),
            toleranceFraction = (tolCents / (if (byz) Moria.toCents(6.0) else 50.0)).toFloat(),
            color = color,
            modifier = Modifier.fillMaxWidth().height(18.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val unit = if (byz) "6 μ" else "50 ¢"
            Text("−$unit", color = Palette.Muted, fontSize = 10.sp)
            Text(if (byz) "μόρια" else "cents", color = Palette.Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
            Text("+$unit", color = Palette.Muted, fontSize = 10.sp)
        }
    }
}

/** Needle meter: centre = on the note; [fraction] in −1..1 of the full scale. */
@Composable
private fun DeviationMeter(fraction: Float?, toleranceFraction: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mid = w / 2
        drawRoundRect(Palette.WoodLight, cornerRadius = CornerRadius(h / 2))
        val tolW = (toleranceFraction.coerceIn(0f, 1f) * w / 2)
        drawRect(Palette.OnPitch.copy(alpha = 0.18f), Offset(mid - tolW, 0f), Size(tolW * 2, h))
        for (i in -6..6) {
            val x = mid + i / 6f * (w / 2 - 2)
            val tickH = if (i == 0) h else if (i % 3 == 0) h * 0.6f else h * 0.35f
            drawLine(Palette.GridLine, Offset(x, (h - tickH) / 2), Offset(x, (h + tickH) / 2), 1.5f)
        }
        drawLine(Palette.Gold, Offset(mid, 0f), Offset(mid, h), 2f)
        if (fraction != null) {
            val x = mid + fraction.coerceIn(-1f, 1f) * (w / 2 - 4)
            drawLine(color, Offset(x, -2f), Offset(x, h + 2f), 6f, cap = StrokeCap.Round)
        }
    }
}

/** Vertical input level bar (−80..0 dBFS). */
@Composable
private fun LevelMeter(levelDb: Double, modifier: Modifier) {
    Canvas(modifier) {
        val f = ((levelDb + 80) / 80).toFloat().coerceIn(0f, 1f)
        drawRoundRect(Palette.WoodLight, cornerRadius = CornerRadius(4f))
        val barH = size.height * f
        drawRoundRect(
            if (f > 0.92f) Palette.OffPitch else Palette.GoldDim,
            topLeft = Offset(0f, size.height - barH), size = Size(size.width, barH), cornerRadius = CornerRadius(4f),
        )
    }
}

fun formatSigned(v: Double, decimals: Int = 1): String {
    val factor = if (decimals == 0) 1.0 else 10.0
    val r = (v * factor).roundToInt() / factor
    val body = if (decimals == 0) abs(r).roundToInt().toString() else String.format(Locale.US, "%.1f", abs(r))
    return when {
        r > 0 -> "+$body"
        r < 0 -> "−$body"
        else -> "±$body"
    }
}
