package app.byzantine.pitchmonitor

import app.byzantine.pitchmonitor.audio.Fft
import app.byzantine.pitchmonitor.audio.PitchDetector
import app.byzantine.pitchmonitor.audio.PitchSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

class PitchDetectorTest {
    private val rate = 44100
    private val detector = PitchDetector(rate, 2048)

    private fun tone(hz: Double, harmonics: DoubleArray = doubleArrayOf(1.0), noise: Double = 0.0, phase: Double = 0.3): FloatArray {
        val rnd = Random(42)
        return FloatArray(2048) { i ->
            var s = 0.0
            for (h in harmonics.indices) s += harmonics[h] * sin(2 * PI * hz * (h + 1) * i / rate + phase * h)
            (0.3 * s + noise * (rnd.nextDouble() * 2 - 1)).toFloat()
        }
    }

    private fun centsError(expected: Double, actual: Double) = abs(1200 * ln(actual / expected) / ln(2.0))

    @Test fun fftRoundTrip() {
        val n = 64
        val fft = Fft(n)
        val re = DoubleArray(n) { sin(it * 0.7) + it % 3 }
        val im = DoubleArray(n)
        val orig = re.copyOf()
        fft.transform(re, im, inverse = false)
        fft.transform(re, im, inverse = true)
        for (i in 0 until n) assertEquals(orig[i], re[i], 1e-9)
    }

    @Test fun pureSinesAcrossVoiceRange() {
        for (hz in doubleArrayOf(65.0, 98.0, 130.81, 196.0, 261.63, 349.2, 440.0, 659.3, 987.8)) {
            val r = detector.detect(tone(hz))
            assertNotNull("no pitch for $hz", r)
            val err = centsError(hz, r!!.hz)
            assertTrue("$hz Hz detected as ${r.hz} ($err cents)", err < 3.0)
        }
    }

    @Test fun voiceLikeToneWithWeakFundamental() {
        // Strong 2nd/3rd harmonics are the classic cause of octave errors.
        val harmonics = doubleArrayOf(0.4, 1.0, 0.8, 0.5, 0.3, 0.2)
        for (hz in doubleArrayOf(110.0, 146.83, 220.0, 293.66)) {
            val r = detector.detect(tone(hz, harmonics))
            assertNotNull(r)
            assertTrue("$hz Hz detected as ${r!!.hz}", centsError(hz, r.hz) < 5.0)
        }
    }

    @Test fun toleratesNoise() {
        val r = detector.detect(tone(220.0, doubleArrayOf(1.0, 0.5, 0.3), noise = 0.05))
        assertNotNull(r)
        assertTrue(centsError(220.0, r!!.hz) < 5.0)
    }

    @Test fun silenceAndNoiseAreUnvoiced() {
        assertNull(detector.detect(FloatArray(2048)))
        val rnd = Random(1)
        val noise = FloatArray(2048) { (rnd.nextDouble() * 2 - 1).toFloat() * 0.3f }
        val r = detector.detect(noise)
        assertTrue("white noise gave ${r?.hz} @ ${r?.confidence}", r == null || r.confidence < 0.6)
    }

    @Test fun moriaResolution() {
        // Two pitches one moria (16.7 cents) apart must be told apart clearly.
        val a = detector.detect(tone(200.0))!!.hz
        val b = detector.detect(tone(200.0 * Math.pow(2.0, 1 / 72.0)))!!.hz
        val diff = 1200 * ln(b / a) / ln(2.0)
        assertEquals(16.67, diff, 1.5)
    }

    @Test fun levelDb() {
        val full = FloatArray(1000) { if (it % 2 == 0) 1f else -1f }
        assertEquals(0.0, PitchDetector.levelDb(full), 1e-6)
        assertEquals(-120.0, PitchDetector.levelDb(FloatArray(100)), 1e-6)
    }

    @Test fun smootherRejectsSingleOctaveGlitch() {
        val s = PitchSmoother(smoothing = 0.0)
        val out = listOf(100.0, 100.0, 100.0, 1300.0, 100.0, 100.0).map { s.next(it) }
        // The 1300 glitch is removed by the 3-frame median.
        assertTrue(out.drop(2).all { abs(it - 100.0) < 1e-9 })
    }

    @Test fun smootherHoldsOnsetAndFollowsNoteChanges() {
        val s = PitchSmoother(smoothing = 0.8)
        assertTrue(s.next(500.0).isNaN()) // single frame after silence is held back
        assertEquals(500.0, s.next(500.0), 1e-9)
        s.next(500.0)
        // A real jump of a fourth passes immediately once the median agrees.
        s.next(1000.0)
        assertEquals(1000.0, s.next(1000.0), 1e-9)
        // A small, sustained shift is approached gradually rather than jumped to.
        s.next(1010.0)
        val v = s.next(1010.0)
        assertTrue("got $v", v > 1000.0 && v < 1010.0)
    }

    @Test fun smootherResetsAfterSilence() {
        val s = PitchSmoother(smoothing = 0.5)
        s.next(0.0); s.next(0.0)
        repeat(10) { s.next(Double.NaN) }
        assertTrue(s.next(700.0).isNaN())
        assertEquals(700.0, s.next(700.0), 1e-9)
    }
}
