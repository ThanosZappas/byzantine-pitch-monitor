package app.byzantine.pitchmonitor.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * YIN pitch detector (de Cheveigné & Kawahara, 2002) with the difference function
 * computed through an FFT autocorrelation, so a 2048-sample window costs one 4096-point
 * forward/inverse FFT pair instead of ~1M multiply-adds.
 *
 * Not thread-safe: use one instance per audio thread.
 */
class PitchDetector(
    private val sampleRate: Int,
    val windowSize: Int = 2048,
    private val minHz: Double = 55.0,
    private val maxHz: Double = 1400.0,
    private val threshold: Double = 0.15,
) {
    data class Result(val hz: Double, val confidence: Double)

    private val half = windowSize / 2
    private val fftSize = windowSize * 2
    private val fft = Fft(fftSize)
    private val aRe = DoubleArray(fftSize)
    private val aIm = DoubleArray(fftSize)
    private val bRe = DoubleArray(fftSize)
    private val bIm = DoubleArray(fftSize)
    private val prefix = DoubleArray(windowSize + 1)
    private val diff = DoubleArray(half)

    private val minTau = maxOf(2, (sampleRate / maxHz).toInt())
    private val maxTau = minOf(half - 1, (sampleRate / minHz).toInt() + 1)

    init {
        require(windowSize and (windowSize - 1) == 0) { "windowSize must be a power of two" }
    }

    /** Detects the fundamental of [frame] (length [windowSize]); null when unvoiced. */
    fun detect(frame: FloatArray): Result? {
        require(frame.size >= windowSize)

        // r(τ) = Σ_{j<N} x_j x_{j+τ}, N = half, via FFT cross-correlation.
        for (i in 0 until fftSize) {
            val x = if (i < windowSize) frame[i].toDouble() else 0.0
            bRe[i] = x; bIm[i] = 0.0
            aRe[i] = if (i < half) x else 0.0; aIm[i] = 0.0
        }
        fft.transform(aRe, aIm, inverse = false)
        fft.transform(bRe, bIm, inverse = false)
        for (i in 0 until fftSize) {
            // conj(A) * B
            val re = aRe[i] * bRe[i] + aIm[i] * bIm[i]
            val im = aRe[i] * bIm[i] - aIm[i] * bRe[i]
            bRe[i] = re; bIm[i] = im
        }
        fft.transform(bRe, bIm, inverse = true) // bRe[τ] = r(τ)

        prefix[0] = 0.0
        for (i in 0 until windowSize) {
            val x = frame[i].toDouble()
            prefix[i + 1] = prefix[i] + x * x
        }
        val e0 = prefix[half]
        if (e0 <= 1e-12) return null

        // Difference function and cumulative mean normalised difference (CMND).
        diff[0] = 1.0
        var running = 0.0
        for (tau in 1 until half) {
            val eTau = prefix[tau + half] - prefix[tau]
            val d = maxOf(0.0, e0 + eTau - 2.0 * bRe[tau])
            running += d
            diff[tau] = if (running > 0) d * tau / running else 1.0
        }

        // First dip under the threshold, then walk down to its local minimum.
        var tau = -1
        var t = minTau
        while (t < maxTau) {
            if (diff[t] < threshold) {
                while (t + 1 < maxTau && diff[t + 1] < diff[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau < 0) {
            // No clear dip: accept the global minimum only if it is reasonably periodic.
            var best = minTau
            for (i in minTau until maxTau) if (diff[i] < diff[best]) best = i
            if (diff[best] > 0.35) return null
            tau = best
        }

        val refined = parabolic(tau)
        if (refined <= 0) return null
        val hz = sampleRate / refined
        if (hz < minHz || hz > maxHz) return null
        return Result(hz, (1.0 - diff[tau]).coerceIn(0.0, 1.0))
    }

    private fun parabolic(tau: Int): Double {
        if (tau <= 0 || tau >= half - 1) return tau.toDouble()
        val s0 = diff[tau - 1]
        val s1 = diff[tau]
        val s2 = diff[tau + 1]
        val denom = s0 - 2 * s1 + s2
        if (denom == 0.0) return tau.toDouble()
        return tau + 0.5 * (s0 - s2) / denom
    }

    companion object {
        /** Root-mean-square level of [frame] in dBFS. */
        fun levelDb(frame: FloatArray, from: Int = 0, to: Int = frame.size): Double {
            var sum = 0.0
            for (i in from until to) sum += frame[i] * frame[i]
            val rms = sqrt(sum / (to - from).coerceAtLeast(1))
            return if (rms <= 1e-9) -120.0 else 20.0 * kotlin.math.log10(rms)
        }
    }
}

/** Iterative in-place radix-2 complex FFT. */
class Fft(private val n: Int) {
    private val cosT = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { sin(2 * PI * it / n) }
    private val rev = IntArray(n)

    init {
        require(n and (n - 1) == 0)
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) rev[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + halfSize) {
                    val wr = cosT[k]
                    val wi = sign * sinT[k]
                    val l = j + halfSize
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr; im[l] = im[j] - ti
                    re[j] += tr; im[j] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
        if (inverse) {
            val inv = 1.0 / n
            for (i in 0 until n) { re[i] *= inv; im[i] *= inv }
        }
    }
}
