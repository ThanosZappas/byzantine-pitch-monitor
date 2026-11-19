package app.byzantine.pitchmonitor.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import app.byzantine.pitchmonitor.music.Pitch

/**
 * Reads the microphone on a dedicated thread, detects pitch every [HOP] samples and writes
 * the result to [history]. The latest values are also exposed for the tuner readout.
 */
class AudioEngine(private val context: Context, private val history: PitchHistory) {

    /** Frames quieter than this (dBFS) are treated as silence. */
    @Volatile var thresholdDb: Double = -50.0
    val smoother = PitchSmoother()

    /** Debug builds only: replace the microphone with [DemoSource]. Set before [start]. */
    @Volatile var demo: Boolean = false

    /** While paused the microphone keeps running but nothing is written to the history. */
    @Volatile var paused: Boolean = false

    @Volatile var latestCents: Double = Double.NaN; private set
    @Volatile var latestLevelDb: Double = -120.0; private set
    /** Last voiced pitch and the uptime (ms) it was heard, for a readout that doesn't flicker. */
    @Volatile var lastVoicedCents: Double = Double.NaN; private set
    @Volatile var lastVoicedAtMs: Long = 0L; private set
    @Volatile var error: String? = null; private set

    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning get() = running

    @SuppressLint("MissingPermission") // The caller checks RECORD_AUDIO first.
    @Synchronized
    fun start() {
        if (running) return
        running = true
        error = null
        thread = Thread({ loop() }, "pitch-audio").also { it.start() }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        if (demo) {
            demoLoop()
            return
        }
        val rate = SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBuf <= 0) {
            fail("This device can't record 32-bit float audio at $rate Hz")
            return
        }
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(chooseSource())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, WINDOW * 4 * 2))
                .build()
        } catch (e: Exception) {
            fail("Microphone unavailable: ${e.message}")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            fail("Microphone could not be opened (is another app using it?)")
            return
        }

        val detector = PitchDetector(rate, WINDOW)
        val window = FloatArray(WINDOW)
        val hop = FloatArray(HOP)
        history.frameSeconds = HOP.toDouble() / rate
        smoother.reset()

        try {
            record.startRecording()
            while (running) {
                var read = 0
                while (read < HOP && running) {
                    val n = record.read(hop, read, HOP - read, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        fail("Microphone read error ($n)")
                        return
                    }
                    read += n
                }
                if (!running) break
                analyse(detector, window, hop)
            }
        } catch (e: Exception) {
            Log.e(TAG, "audio loop failed", e)
            fail("Audio error: ${e.message}")
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    /** Slides [hop] into [window], detects pitch and publishes the result. */
    private fun analyse(detector: PitchDetector, window: FloatArray, hop: FloatArray) {
        System.arraycopy(window, HOP, window, 0, WINDOW - HOP)
        System.arraycopy(hop, 0, window, WINDOW - HOP, HOP)

        // Level of the newest part of the window so the readout reacts quickly.
        val level = PitchDetector.levelDb(window, WINDOW - WINDOW / 2, WINDOW)
        latestLevelDb = level
        var raw = Double.NaN
        if (level >= thresholdDb) {
            val r = detector.detect(window)
            if (r != null && r.confidence >= MIN_CONFIDENCE) raw = Pitch.hzToCents(r.hz)
        }
        val smoothed = smoother.next(raw)
        latestCents = smoothed
        if (!smoothed.isNaN()) {
            lastVoicedCents = smoothed
            lastVoicedAtMs = SystemClock.uptimeMillis()
        }
        if (!paused) history.push(smoothed.toFloat(), level.toFloat())
    }

    private fun demoLoop() {
        val source = DemoSource(SAMPLE_RATE)
        val detector = PitchDetector(SAMPLE_RATE, WINDOW)
        val window = FloatArray(WINDOW)
        val hop = FloatArray(HOP)
        history.frameSeconds = HOP.toDouble() / SAMPLE_RATE
        smoother.reset()
        val hopNanos = HOP * 1_000_000_000L / SAMPLE_RATE
        var next = System.nanoTime()
        while (running) {
            source.fill(hop)
            analyse(detector, window, hop)
            next += hopNanos
            val wait = next - System.nanoTime()
            if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
        }
    }

    private fun chooseSource(): Int {
        val am = context.getSystemService(AudioManager::class.java)
        val unprocessed = am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        // UNPROCESSED/VOICE_RECOGNITION avoid the AGC and noise suppression that bend pitch readings.
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        error = message
        running = false
    }

    companion object {
        private const val TAG = "AudioEngine"
        const val SAMPLE_RATE = 44100
        const val WINDOW = 2048
        const val HOP = 512
        private const val MIN_CONFIDENCE = 0.6
    }
}
