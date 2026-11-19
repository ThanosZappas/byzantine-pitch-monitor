package app.byzantine.pitchmonitor.audio

import app.byzantine.pitchmonitor.music.Genus
import app.byzantine.pitchmonitor.music.Moria
import app.byzantine.pitchmonitor.music.Pitch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Debug-only stand-in for the microphone: a voice-like tone singing a short first-mode
 * phrase with vibrato and slight intonation drift. Lets the UI be checked on an emulator.
 * Start with:  adb shell am start -n app.byzantine.pitchmonitor/.MainActivity --ez demo true
 */
class DemoSource(private val rate: Int, niHz: Double = 261.63) {
    private data class Note(val moria: Double, val seconds: Double)

    // Pa Vou Ga Di (slightly sharp) Ga Vou Pa, rest, Ni Pa Vou Pa (a bit flat), rest.
    private val phrase = listOf(
        Note(12.0, 0.6), Note(22.0, 0.45), Note(30.0, 0.45), Note(43.5, 0.9), Note(30.0, 0.4),
        Note(22.0, 0.4), Note(12.0, 1.0), Note(Double.NaN, 0.5),
        Note(0.0, 0.5), Note(12.0, 0.4), Note(22.0, 0.6), Note(10.5, 1.2), Note(Double.NaN, 0.6),
    )
    private val niCents = Pitch.hzToCents(niHz) - 1200 // a male voice range
    private val total = phrase.sumOf { it.seconds }
    private var t = 0.0
    private var phase = 0.0
    private var glideCents = Double.NaN

    init {
        require(Genus.DIATONIC.positions[1] == 12)
    }

    fun fill(out: FloatArray) {
        val dt = 1.0 / rate
        for (i in out.indices) {
            var pos = t % total
            var note = phrase.last()
            for (n in phrase) {
                if (pos < n.seconds) { note = n; break }
                pos -= n.seconds
            }
            if (note.moria.isNaN()) {
                out[i] = 0f
                glideCents = Double.NaN
            } else {
                val target = niCents + Moria.toCents(note.moria)
                glideCents = if (glideCents.isNaN()) target else glideCents + (target - glideCents) * 0.002
                val vibrato = 12 * sin(2 * PI * 5.2 * t)
                val hz = Pitch.centsToHz(glideCents + vibrato)
                phase += 2 * PI * hz * dt
                if (phase > 2 * PI) phase -= 2 * PI
                val s = 0.5 * sin(phase) + 0.9 * sin(2 * phase) + 0.6 * sin(3 * phase) + 0.3 * sin(4 * phase) + 0.15 * sin(5 * phase)
                out[i] = (0.12 * s).toFloat()
            }
            t += dt
        }
    }
}
