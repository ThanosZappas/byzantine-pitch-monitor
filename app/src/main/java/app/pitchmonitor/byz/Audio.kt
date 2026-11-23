package app.pitchmonitor.byz

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.net.Uri
import android.os.SystemClock
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val SAMPLE_RATE = 44100
const val WINDOW = 2048
const val HOP = 512

/** Readings per second of the pitch track (same for live input and analysed files). */
const val FRAMES_PER_SECOND = SAMPLE_RATE.toDouble() / HOP

/** YIN pitch detector (de Cheveigné & Kawahara 2002) on a window of [size] samples. */
class PitchDetector(private val sampleRate: Int, private val size: Int) {
    private val half = size / 2
    private val diff = FloatArray(half)

    /** Fundamental in Hz of [x] (at least [size] samples), or NaN when there is no clear pitch. */
    fun detect(x: FloatArray): Double {
        val tauMin = max(2, sampleRate / 1600)
        val tauMax = min(half - 1, sampleRate / 55)
        // Difference function.
        for (tau in 1..tauMax) {
            var sum = 0f
            for (i in 0 until half) {
                val d = x[i] - x[i + tau]
                sum += d * d
            }
            diff[tau] = sum
        }
        // Cumulative mean normalised difference.
        diff[0] = 1f
        var running = 0f
        for (tau in 1..tauMax) {
            running += diff[tau]
            diff[tau] = if (running == 0f) 1f else diff[tau] * tau / running
        }
        // First dip under the threshold, then its local minimum.
        var tau = -1
        var t = tauMin
        while (t <= tauMax) {
            if (diff[t] < THRESHOLD) {
                while (t + 1 <= tauMax && diff[t + 1] < diff[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau < 0) return Double.NaN
        // Parabolic interpolation around the minimum.
        val better = if (tau in 1 until tauMax) {
            val a = diff[tau - 1]; val b = diff[tau]; val c = diff[tau + 1]
            val den = a + c - 2 * b
            if (den != 0f) tau + 0.5 * (a - c) / den else tau.toDouble()
        } else tau.toDouble()
        return sampleRate / better
    }

    companion object {
        const val THRESHOLD = 0.15f
    }
}

/** RMS (0..1) of [n] samples of [x]. */
fun rms(x: FloatArray, n: Int): Double {
    var s = 0.0
    for (i in 0 until n) s += x[i] * x[i]
    return sqrt(s / n)
}

/** Volume threshold setting (0..50) to an RMS level. */
fun thresholdRms(setting: Float): Double = setting / 1000.0

/** The pitch readings over time: cents re 440 Hz, NaN where there was no pitch. */
class PitchTrack(capacity: Int = 1 shl 15) {
    private val data = FloatArray(capacity) { Float.NaN }
    private val mask = capacity - 1

    /** Number of readings ever added. */
    @Volatile var count: Long = 0
        private set

    @Volatile var lastVoicedCents: Double = Double.NaN
        private set
    @Volatile var lastVoicedAt: Long = 0
        private set

    val capacity = capacity

    @Synchronized fun add(cents: Float) {
        data[(count and mask.toLong()).toInt()] = cents
        count++
        if (!cents.isNaN()) {
            lastVoicedCents = cents.toDouble()
            lastVoicedAt = SystemClock.uptimeMillis()
        }
    }

    /** Reading [index] (0 = first ever), NaN if it is too old or not there yet. */
    fun get(index: Long): Float {
        val c = count
        if (index < 0 || index >= c || index < c - capacity) return Float.NaN
        return data[(index and mask.toLong()).toInt()]
    }

    @Synchronized fun clear() {
        data.fill(Float.NaN)
        count = 0
        lastVoicedCents = Double.NaN
    }

    companion object {
        fun of(values: FloatArray): PitchTrack {
            var cap = 1
            while (cap < values.size) cap = cap shl 1
            val t = PitchTrack(max(cap, 2))
            for (v in values) t.add(v)
            return t
        }
    }
}

/** Microphone input: fills [track] with readings and, while recording, writes a WAV file. */
class AudioEngine(private val track: PitchTrack) {
    @Volatile var thresholdRms = 0.001
    @Volatile var running = false
        private set
    @Volatile private var writer: WavWriter? = null
    private var thread: Thread? = null

    val recording: Boolean get() = writer != null

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "pitch-input").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        stopRecording()
    }

    fun startRecording(file: File) {
        writer = WavWriter(file, SAMPLE_RATE)
    }

    /** Finishes the WAV file being written and returns it. */
    fun stopRecording(): File? {
        val w = writer ?: return null
        writer = null
        synchronized(w) { w.close() }
        return w.file
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuf, WINDOW * 4))
        } catch (t: Throwable) {
            running = false
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); running = false; return
        }
        val detector = PitchDetector(SAMPLE_RATE, WINDOW)
        val hop = ShortArray(HOP)
        val window = FloatArray(WINDOW)
        rec.startRecording()
        try {
            while (running) {
                var got = 0
                while (got < HOP && running) {
                    val n = rec.read(hop, got, HOP - got)
                    if (n <= 0) break
                    got += n
                }
                if (got < HOP) continue
                writer?.let { w -> synchronized(w) { if (writer === w) w.write(hop, HOP) } }
                System.arraycopy(window, HOP, window, 0, WINDOW - HOP)
                for (i in 0 until HOP) window[WINDOW - HOP + i] = hop[i] / 32768f
                track.add(analyse(detector, window, thresholdRms))
            }
        } finally {
            rec.stop()
            rec.release()
        }
    }

    companion object {
        /** One reading: cents re 440 Hz, or NaN when too quiet or unpitched. */
        fun analyse(detector: PitchDetector, window: FloatArray, threshold: Double): Float {
            if (rms(window, window.size) < threshold) return Float.NaN
            val hz = detector.detect(window)
            return if (hz.isNaN()) Float.NaN else (1200.0 * ln(hz / 440.0) / ln(2.0)).toFloat()
        }
    }
}

// -------------------------------------------------------------------------------------------
// WAV files
// -------------------------------------------------------------------------------------------

class WavWriter(val file: File, private val sampleRate: Int) {
    private val out = RandomAccessFile(file, "rw")
    private var bytes = 0L
    private val buf = ByteBuffer.allocate(HOP * 2 * 8).order(ByteOrder.LITTLE_ENDIAN)

    init {
        out.setLength(0)
        out.write(ByteArray(44))
    }

    fun write(samples: ShortArray, n: Int) {
        buf.clear()
        for (i in 0 until n) buf.putShort(samples[i])
        out.write(buf.array(), 0, n * 2)
        bytes += n * 2
    }

    fun close() {
        out.seek(0)
        out.write(header(sampleRate, 1, bytes))
        out.close()
    }

    companion object {
        fun header(rate: Int, channels: Int, dataBytes: Long): ByteArray {
            val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()); b.putInt((36 + dataBytes).toInt()); b.put("WAVE".toByteArray())
            b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(channels.toShort())
            b.putInt(rate); b.putInt(rate * channels * 2); b.putShort((channels * 2).toShort()); b.putShort(16)
            b.put("data".toByteArray()); b.putInt(dataBytes.toInt())
            return b.array()
        }

        fun writeAll(file: File, rate: Int, mono: ShortArray) {
            val w = WavWriter(file, rate)
            var i = 0
            val chunk = ShortArray(HOP)
            while (i < mono.size) {
                val n = min(HOP, mono.size - i)
                System.arraycopy(mono, i, chunk, 0, n)
                w.write(chunk, n)
                i += n
            }
            w.close()
        }
    }
}

/** A decoded recording: mono 16-bit samples. */
class Audio(val sampleRate: Int, val samples: ShortArray) {
    val seconds: Double get() = samples.size.toDouble() / sampleRate

    companion object {
        /** Reads a 16-bit PCM WAV file (mono or stereo, mixed down to mono). */
        fun readWav(file: File): Audio {
            DataInputStream(FileInputStream(file).buffered()).use { inp ->
                val all = inp.readBytes()
                val b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN)
                require(String(all, 0, 4) == "RIFF" && String(all, 8, 4) == "WAVE") { "Not a WAV file" }
                var pos = 12
                var rate = SAMPLE_RATE
                var channels = 1
                var bits = 16
                while (pos + 8 <= all.size) {
                    val id = String(all, pos, 4)
                    val len = b.getInt(pos + 4)
                    val body = pos + 8
                    if (id == "fmt ") {
                        channels = b.getShort(body + 2).toInt()
                        rate = b.getInt(body + 4)
                        bits = b.getShort(body + 14).toInt()
                    } else if (id == "data") {
                        require(bits == 16) { "Only 16-bit WAV is supported" }
                        val end = min(all.size, body + if (len <= 0) all.size else len)
                        val frames = (end - body) / (2 * channels)
                        val out = ShortArray(frames)
                        for (f in 0 until frames) {
                            var s = 0
                            for (c in 0 until channels) s += b.getShort(body + (f * channels + c) * 2)
                            out[f] = (s / channels).toShort()
                        }
                        return Audio(rate, out)
                    }
                    pos = body + len + (len and 1)
                }
                error("No audio data")
            }
        }

        /**
         * Decodes any audio file Android can read (mp3, m4a, ogg, wav…) into mono 16-bit samples.
         * [progress] gets 0..1 and returns false to cancel (then this throws [CancelledException]).
         */
        fun decode(context: Context, uri: Uri, progress: (Float) -> Boolean = { true }): Audio {
            val ex = MediaExtractor()
            ex.setDataSource(context, uri, null)
            val trackIndex = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: run { ex.release(); error("The file has no audio") }
            ex.selectTrack(trackIndex)
            val inFormat = ex.getTrackFormat(trackIndex)
            val durationUs = if (inFormat.containsKey(MediaFormat.KEY_DURATION)) inFormat.getLong(MediaFormat.KEY_DURATION) else 0L
            val codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
            var rate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            var out = ShortArray(if (durationUs > 0) (durationUs * rate / 1_000_000 + rate).toInt().coerceAtMost(60_000_000) else rate * 60)
            var n = 0
            try {
                codec.configure(inFormat, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val buf = codec.getInputBuffer(i)!!
                            val size = ex.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0)
                                if (durationUs > 0 && !progress(ex.sampleTime.toFloat() / durationUs)) throw CancelledException()
                                ex.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o >= 0) {
                        val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                        val bytesPer = if (float) 4 else 2
                        val frames = info.size / (bytesPer * channels)
                        if (n + frames > out.size) out = out.copyOf(maxOf(out.size * 3 / 2, n + frames))
                        for (f in 0 until frames) {
                            var sum = 0.0
                            for (c in 0 until channels) {
                                val at = info.offset + (f * channels + c) * bytesPer
                                sum += if (float) buf.getFloat(at) * 32767.0 else buf.getShort(at).toDouble()
                            }
                            out[n++] = (sum / channels).coerceIn(-32768.0, 32767.0).toInt().toShort()
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
                ex.release()
            }
            if (n == 0) error("No sound could be decoded from the file")
            return Audio(rate, out.copyOf(n))
        }
    }
}

class CancelledException : Exception("Cancelled")

/**
 * Pitch readings of a whole recording, at the same rate as live input. The audio is first
 * reduced to about 11 kHz (enough for voices up to ~1500 Hz), which makes songs analyse
 * about 16 times faster. [progress] gets 0..1 and returns false to cancel.
 */
fun analyseAudio(audio: Audio, threshold: Double, progress: (Float) -> Boolean = { true }): FloatArray {
    val factor = max(1, audio.sampleRate / 11025)
    val rate = audio.sampleRate / factor
    val src = audio.samples
    val len = src.size / factor
    val small = FloatArray(len)
    for (k in 0 until len) {
        var s = 0
        val base = k * factor
        for (j in 0 until factor) s += src[base + j]
        small[k] = s / (factor * 32768f)
    }
    val hop = rate / FRAMES_PER_SECOND
    val size = max(256, Math.round(WINDOW.toDouble() * rate / SAMPLE_RATE).toInt())
    val det = PitchDetector(rate, size)
    val window = FloatArray(size)
    val n = (len / hop).toInt()
    val out = FloatArray(n)
    for (k in 0 until n) {
        if (k % 200 == 0 && !progress(k.toFloat() / n)) throw CancelledException()
        val end = Math.round((k + 1) * hop).toInt()
        val start = end - size
        for (i in 0 until size) {
            val j = start + i
            window[i] = if (j >= 0 && j < len) small[j] else 0f
        }
        out[k] = AudioEngine.analyse(det, window, threshold)
    }
    return out
}

/** Plays a recording; [positionSeconds] follows the sound that is heard. */
class Player(private val audio: Audio, private val onDone: () -> Unit) {
    private var track: AudioTrack? = null
    @Volatile private var stopped = false

    fun start() {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(audio.sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT), 8192))
            .build()
        track = t
        t.play()
        Thread({
            var i = 0
            val chunk = 4096
            while (!stopped && i < audio.samples.size) {
                val n = min(chunk, audio.samples.size - i)
                val w = t.write(audio.samples, i, n)
                if (w <= 0) break
                i += w
            }
            if (!stopped) {
                // Let the tail play out before reporting the end.
                val total = audio.samples.size
                while (!stopped && t.playbackHeadPosition < total - 64) Thread.sleep(20)
            }
            val wasStopped = stopped
            release()
            if (!wasStopped) onDone()
        }, "playback").start()
    }

    val positionSeconds: Double get() = (track?.playbackHeadPosition ?: 0).toDouble() / audio.sampleRate

    fun stop() {
        stopped = true
    }

    @Synchronized private fun release() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}

/** Recordings are WAV files in the app's own storage. */
class RecordingStore(context: Context) {
    val dir = File(context.filesDir, "recordings").apply { mkdirs() }

    fun list(): List<File> = (dir.listFiles { f -> f.isFile && f.name.endsWith(".wav") }?.toList() ?: emptyList())
        .sortedByDescending { it.lastModified() }

    fun newFile(): File {
        val name = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date())
        return unique(name)
    }

    fun unique(base: String): File {
        val clean = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "recording" }
        var f = File(dir, "$clean.wav")
        var n = 2
        while (f.exists()) f = File(dir, "$clean ($n).wav").also { n++ }
        return f
    }

    fun rename(file: File, newName: String): File? {
        val target = File(dir, newName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().removeSuffix(".wav") + ".wav")
        if (target.exists()) return null
        return if (file.renameTo(target)) target else null
    }
}
