package app.byzantine.pitchmonitor.audio

/**
 * Ring buffer of analysed frames, written by the audio thread and read by the UI each frame.
 * Pitch is in cents relative to 440 Hz, NaN for silence / unvoiced frames.
 */
class PitchHistory(val capacity: Int = 1 shl 13) {
    private val cents = FloatArray(capacity) { Float.NaN }
    private val levels = FloatArray(capacity) { -120f }
    private var written = 0L

    /** Seconds per frame; set by the engine when it starts. */
    @Volatile var frameSeconds: Double = 512.0 / 44100.0

    @Synchronized
    fun push(pitchCents: Float, levelDb: Float) {
        val i = (written % capacity).toInt()
        cents[i] = pitchCents
        levels[i] = levelDb
        written++
    }

    @Synchronized
    fun clear() {
        cents.fill(Float.NaN)
        levels.fill(-120f)
        written = 0
    }

    /** Total frames ever written (frame index of the next push). */
    @Synchronized
    fun totalWritten(): Long = written

    @Synchronized
    fun size(): Int = minOf(written, capacity.toLong()).toInt()

    /**
     * Copies up to [count] frames ending [skip] frames before the newest one, oldest first,
     * into [outCents]/[outLevels]. Returns the number of frames copied.
     */
    @Synchronized
    fun snapshot(count: Int, skip: Int, outCents: FloatArray, outLevels: FloatArray? = null): Int {
        val available = minOf(written, capacity.toLong()).toInt() - skip
        val n = minOf(count, available, outCents.size)
        if (n <= 0) return 0
        val end = written - skip // exclusive
        for (k in 0 until n) {
            val idx = ((end - n + k) % capacity).toInt()
            outCents[k] = cents[idx]
            outLevels?.set(k, levels[idx])
        }
        return n
    }
}
