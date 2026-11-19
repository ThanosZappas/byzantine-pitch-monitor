package app.byzantine.pitchmonitor.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Ison (drone) generator: a soft, voice-like tone built from a few harmonics.
 * Pitch changes glide and starting/stopping fades, so moving the ison never clicks.
 */
class IsonPlayer {
    @Volatile private var targetHz = 130.81
    @Volatile private var targetGain = 0.0
    @Volatile var volume: Double = 0.5
    @Volatile private var running = false
    private var thread: Thread? = null

    val isPlaying get() = running && targetGain > 0

    fun setFrequency(hz: Double) {
        targetHz = hz.coerceIn(30.0, 2000.0)
    }

    @Synchronized
    fun play(hz: Double) {
        setFrequency(hz)
        targetGain = 1.0
        if (running) return
        running = true
        thread = Thread({ loop() }, "ison").also { it.start() }
    }

    /** Fades out; the audio thread ends once silent. */
    fun stop() {
        targetGain = 0.0
    }

    fun release() {
        val t: Thread?
        synchronized(this) {
            targetGain = 0.0
            running = false
            t = thread
        }
        t?.join(500)
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val rate = 44100
        val block = 512
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(block * 4 * 4, AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)))
            .build()

        val buf = FloatArray(block)
        var phase = 0.0
        var hz = targetHz
        var gain = 0.0
        // One-pole smoothing coefficients: ~60 ms pitch glide, ~120 ms fade.
        val glide = exp(-1.0 / (0.06 * rate))
        val fade = exp(-1.0 / (0.12 * rate))
        val harmonics = doubleArrayOf(1.0, 0.55, 0.32, 0.22, 0.12, 0.07, 0.04)
        val norm = 1.0 / harmonics.sum()

        track.play()
        try {
            while (running) {
                val tg = targetGain * volume
                for (i in 0 until block) {
                    hz = targetHz + (hz - targetHz) * glide
                    gain = tg + (gain - tg) * fade
                    phase += 2 * PI * hz / rate
                    if (phase > 2 * PI) phase -= 2 * PI
                    var s = 0.0
                    for (h in harmonics.indices) {
                        // Skip harmonics above ~8 kHz to avoid aliasing on high isons.
                        if (hz * (h + 1) > 8000) break
                        s += harmonics[h] * sin(phase * (h + 1))
                    }
                    buf[i] = (s * norm * gain * 0.8).toFloat()
                }
                track.write(buf, 0, block, AudioTrack.WRITE_BLOCKING)
                if (targetGain == 0.0 && gain < 1e-4) {
                    synchronized(this) { if (targetGain == 0.0) running = false }
                }
            }
        } finally {
            runCatching { track.stop() }
            track.release()
            synchronized(this) { if (thread === Thread.currentThread()) thread = null }
        }
    }
}
