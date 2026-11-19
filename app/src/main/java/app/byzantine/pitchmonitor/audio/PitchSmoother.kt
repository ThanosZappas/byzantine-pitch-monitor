package app.byzantine.pitchmonitor.audio

import kotlin.math.abs

/**
 * Cleans up the raw detector output:
 *  - a 3-frame median removes single-frame octave errors,
 *  - a short onset hold drops one-frame blips after silence,
 *  - an exponential filter (strength [smoothing], 0..0.95) steadies the line, but lets
 *    real note changes (larger than [jumpCents]) through quickly.
 */
class PitchSmoother(
    @Volatile var smoothing: Double = 0.4,
    private val jumpCents: Double = 70.0,
    private val maxGapFrames: Int = 4,
) {
    private val recent = DoubleArray(3)
    private var recentCount = 0
    private var gap = Int.MAX_VALUE
    private var value = Double.NaN

    fun reset() {
        recentCount = 0
        gap = Int.MAX_VALUE
        value = Double.NaN
    }

    /** Feeds one frame (NaN for unvoiced) and returns the smoothed pitch, or NaN. */
    fun next(rawCents: Double): Double {
        if (rawCents.isNaN()) {
            gap = if (gap == Int.MAX_VALUE) gap else gap + 1
            if (gap > maxGapFrames) reset()
            return Double.NaN
        }
        // A short dropout inside a note (gap <= maxGapFrames) keeps the median history.
        gap = 0
        // Shift into the median window.
        if (recentCount < 3) {
            recent[recentCount++] = rawCents
        } else {
            recent[0] = recent[1]; recent[1] = recent[2]; recent[2] = rawCents
        }
        if (recentCount < 2) return Double.NaN // onset hold: need two voiced frames

        val median = if (recentCount == 2) {
            // With two frames prefer the newer one unless they disagree wildly.
            if (abs(recent[0] - recent[1]) > 600) return Double.NaN else recent[1]
        } else median3(recent[0], recent[1], recent[2])

        value = if (value.isNaN() || abs(median - value) > jumpCents) {
            median
        } else {
            val s = smoothing.coerceIn(0.0, 0.95)
            value + (1 - s) * (median - value)
        }
        return value
    }

    private fun median3(a: Double, b: Double, c: Double): Double =
        maxOf(minOf(a, b), minOf(maxOf(a, b), c))
}
