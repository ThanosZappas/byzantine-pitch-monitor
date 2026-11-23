package app.pitchmonitor.byz

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import kotlin.math.pow

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var store: RecordingStore
    private val liveTrack = PitchTrack()
    private val engine = AudioEngine(liveTrack)

    private lateinit var graph: PitchView
    private lateinit var btnHold: TextView
    private lateinit var btnScale: TextView
    private lateinit var baseControls: View
    private lateinit var baseDetails: TextView
    private lateinit var btnReset: TextView
    private lateinit var transport: View
    private lateinit var btnStop: ImageButton
    private lateinit var btnRecord: ImageButton
    private lateinit var btnPlay: ImageButton

    /** The recording last made, loaded or imported (for Play and Send). */
    private var current: File? = null
    private var currentAudio: Audio? = null
    private var player: Player? = null
    private var showingRecording = false

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startInput() else toast("The microphone is needed to show your pitch")
    }

    private val settingsResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        when (r.data?.getStringExtra(SettingsActivity.EXTRA_ACTION)) {
            SettingsActivity.ACTION_LIST -> openRecordings()
            SettingsActivity.ACTION_SEND -> sendCurrent()
            SettingsActivity.ACTION_IMPORT -> importAudio.launch(arrayOf("audio/*"))
        }
    }

    private val recordingsResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringExtra(RecordingsActivity.EXTRA_FILE)?.let { loadRecording(File(it)) }
    }

    private val importAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFrom(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        store = RecordingStore(this)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        graph = findViewById(R.id.graph)
        btnHold = findViewById(R.id.btnHold)
        btnScale = findViewById(R.id.btnScale)
        baseControls = findViewById(R.id.baseControls)
        baseDetails = findViewById(R.id.baseDetails)
        btnReset = findViewById(R.id.btnBaseReset)
        transport = findViewById(R.id.transport)
        btnStop = findViewById(R.id.btnStop)
        btnRecord = findViewById(R.id.btnRecord)
        btnPlay = findViewById(R.id.btnPlay)

        findViewById<TextView>(R.id.title).typeface = Typeface.createFromAsset(assets, "fonts/GFSDidot.otf")
        graph.track = liveTrack
        graph.onZoom = { h, v -> prefs.hZoom = h; prefs.vZoom = v }
        graph.centerCents = Byzantine.niCents(prefs.niOffset, prefs.a4) + 600

        findViewById<View>(R.id.btnSettings).setOnClickListener { openSettings() }
        btnScale.setOnClickListener { pickScale() }
        findViewById<View>(R.id.btnDown1).setOnClickListener { setOffset(prefs.niOffset - 1) }
        findViewById<View>(R.id.btnDown6).setOnClickListener { setOffset(prefs.niOffset - 6) }
        findViewById<View>(R.id.btnUp1).setOnClickListener { setOffset(prefs.niOffset + 1) }
        findViewById<View>(R.id.btnUp6).setOnClickListener { setOffset(prefs.niOffset + 6) }
        btnReset.setOnClickListener { setOffset(0) }
        findViewById<View>(R.id.btnBaseVoice).setOnClickListener { baseFromVoice() }
        btnHold.setOnClickListener { toggleHold() }
        btnRecord.setOnClickListener { record() }
        btnStop.setOnClickListener { stopAll() }
        btnPlay.setOnClickListener { play() }
        debugExtras()
    }

    /**
     * Debug builds only: lets screens be checked over adb on phones that block simulated taps.
     * `--es scale "Ήχος Α'"`, `--ez demo true` (a synthetic voice instead of the microphone),
     * `--es open settings|recordings`.
     */
    private var demo = false

    private fun debugExtras() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        intent.getStringExtra("scale")?.let { if (Catalog.ORDERED.contains(it)) prefs.scale = it }
        demo = intent.getBooleanExtra("demo", false)
        if (demo) Thread({ demoVoice() }, "demo-voice").start()
        when (intent.getStringExtra("open")) {
            "settings" -> openSettings()
            "recordings" -> openRecordings()
        }
    }

    /** A voice holding Pa with vibrato, sliding through Vou and Ga, with short breaths. */
    private fun demoVoice() {
        val pa = Byzantine.niCents(prefs.niOffset, prefs.a4) + 12 * Pitch.CENTS_PER_MORIO
        val notes = doubleArrayOf(0.0, 0.0, 167.0, 300.0, 167.0, 0.0, -200.0, 0.0)
        var t = 0
        while (!isDestroyed) {
            val i = (t / 140) % notes.size
            val inNote = t % 140
            val v = if (inNote > 128) Float.NaN
            else (pa + notes[i] - 40 + 18 * kotlin.math.sin(t / 6.0)).toFloat()
            liveTrack.add(v)
            t++
            Thread.sleep(11)
        }
    }

    override fun onResume() {
        super.onResume()
        applySettings()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startInput()
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onPause() {
        super.onPause()
        player?.stop()
        finishRecording()
        engine.stop()
    }

    private fun startInput() {
        if (player == null && !demo) engine.start()
    }

    // --- settings -----------------------------------------------------------------------

    private fun applySettings() {
        if (!Catalog.visible(prefs.scale, prefs.european)) {
            prefs.scale = Catalog.visibleList(prefs.european).first()
        }
        engine.thresholdRms = thresholdRms(prefs.threshold)
        graph.grid = prefs.buildGrid()
        graph.hZoom = prefs.hZoom
        graph.vZoom = prefs.vZoom
        graph.style = GraphStyle(
            pitchColor = prefs.colorPitch, beatsColor = prefs.colorBeats, metronomeColor = prefs.colorMetronome,
            beatLines = prefs.beatLines, blink = prefs.blink, bpm = prefs.bpm, meter = prefs.meter,
            showTuner = prefs.showTuner, showHz = prefs.showHz, smoothing = prefs.smoothing,
            autoScroll = prefs.autoScroll, scrollSpeed = prefs.scrollSpeed,
        )
        btnScale.text = prefs.scale
        btnScale.visibility = if (prefs.showScale) View.VISIBLE else View.INVISIBLE
        btnHold.visibility = if (prefs.showHold) View.VISIBLE else View.GONE
        transport.visibility = if (prefs.hideTransport) View.GONE else View.VISIBLE
        refreshBase()
        refreshTransport()
    }

    private fun openSettings() {
        settingsResult.launch(Intent(this, SettingsActivity::class.java).putExtra("scrollTo", intent.getIntExtra("scrollTo", 0)))
    }

    private fun pickScale() {
        val names = Catalog.visibleList(prefs.european)
        AlertDialog.Builder(this)
            .setTitle(R.string.scale)
            .setSingleChoiceItems(names.toTypedArray(), names.indexOf(prefs.scale).coerceAtLeast(0)) { d, which ->
                prefs.scale = names[which]
                applySettings()
                d.dismiss()
            }
            .show()
    }

    // --- base (Ni) ------------------------------------------------------------------------

    private fun setOffset(moria: Int) {
        prefs.niOffset = moria.coerceIn(-72, 72)
        graph.grid = prefs.buildGrid()
        refreshBase()
    }

    private fun refreshBase() {
        val byz = Byzantine.mode(prefs.scale) != null
        baseControls.visibility = if (byz) View.VISIBLE else View.GONE
        val o = prefs.niOffset
        val hz = Byzantine.NI_NATURAL_HZ * 2.0.pow(o / 72.0)
        baseDetails.text = String.format(Locale.US, "ΝΗ %+dμ\n%.1f Hz", o, hz)
        btnReset.isEnabled = o != 0
        btnReset.alpha = if (o != 0) 1f else 0.4f
    }

    /** Moves Ni by whole moria so that the mode's base lands on the note being sung now. */
    private fun baseFromVoice() {
        val mode = Byzantine.mode(prefs.scale) ?: return
        val cents = liveTrack.lastVoicedCents
        if (cents.isNaN() || SystemClock.uptimeMillis() - liveTrack.lastVoicedAt > 1500) {
            toast("Sing the base note, then tap “Base = voice” while singing")
            return
        }
        val shift = Byzantine.voiceShift(cents, mode.genus.positions[mode.base], prefs.a4)
        setOffset(shift)
        toast(String.format(Locale.US, "%s = voice  ·  ΝΗ %+dμ", Byzantine.NAMES[mode.base], shift))
    }

    // --- hold -----------------------------------------------------------------------------

    private fun toggleHold() {
        if (showingRecording && player == null) {
            // Leave the recording and go back to the live voice.
            showLive()
            return
        }
        graph.held = !graph.held
        refreshHold()
    }

    private fun refreshHold() {
        val on = graph.held
        btnHold.isActivated = on
        btnHold.setTextColor(if (on) getColor(R.color.gold) else 0xFFAAAAAA.toInt())
    }

    private fun showLive() {
        showingRecording = false
        graph.track = liveTrack
        graph.playbackFrame = null
        graph.held = false
        refreshHold()
        refreshTransport()
    }

    // --- recordings -----------------------------------------------------------------------

    private fun refreshTransport() {
        val recording = engine.recording
        val playing = player != null
        btnRecord.isEnabled = !playing
        btnRecord.alpha = if (playing) 0.35f else 1f
        btnRecord.isActivated = recording
        btnStop.isEnabled = recording || playing || showingRecording
        btnStop.alpha = if (btnStop.isEnabled) 1f else 0.35f
        val canPlay = !recording && !playing && current != null
        btnPlay.isEnabled = canPlay
        btnPlay.alpha = if (canPlay) 1f else 0.35f
    }

    private fun record() {
        if (engine.recording) {
            finishRecording()
            return
        }
        if (!engine.running) { toast("The microphone is not available"); return }
        showLive()
        engine.startRecording(store.newFile())
        toast("Recording…")
        refreshTransport()
    }

    private fun finishRecording() {
        val f = engine.stopRecording() ?: return
        current = f
        currentAudio = null
        refreshTransport()
    }

    private fun stopAll() {
        when {
            engine.recording -> { finishRecording(); toast("Saved ${current?.nameWithoutExtension}") }
            player != null -> player?.stop()
            showingRecording -> showLive()
        }
        refreshTransport()
    }

    private fun play() {
        val f = current ?: return
        val audio = currentAudio
        if (audio == null || !showingRecording) {
            loadRecording(f, playAfter = true)
            return
        }
        startPlayback(audio)
    }

    private fun startPlayback(audio: Audio) {
        engine.stop()
        val p = Player(audio) { runOnUiThread { playbackEnded() } }
        player = p
        graph.held = true
        p.start()
        tickPlayback()
        refreshTransport()
    }

    private fun tickPlayback() {
        val p = player ?: return
        graph.playbackFrame = p.positionSeconds * FRAMES_PER_SECOND
        graph.postDelayed({ tickPlayback() }, 16)
    }

    private fun playbackEnded() {
        player = null
        graph.playbackFrame = null
        refreshHold()
        refreshTransport()
        startInput()
    }

    /** Analyses [file] and shows its pitch on the graph (held, at its end). */
    private fun loadRecording(file: File, playAfter: Boolean = false) {
        toast("Loading ${file.nameWithoutExtension}…")
        val threshold = thresholdRms(prefs.threshold)
        Thread {
            val result = runCatching {
                val audio = Audio.readWav(file)
                audio to analyseAudio(audio, threshold)
            }
            runOnUiThread {
                result.onSuccess { (audio, frames) ->
                    current = file
                    currentAudio = audio
                    showingRecording = true
                    graph.track = PitchTrack.of(frames)
                    graph.playbackFrame = null
                    graph.held = false
                    graph.held = true
                    refreshHold()
                    refreshTransport()
                    if (playAfter) startPlayback(audio)
                }.onFailure { toast("Could not open ${file.name}: ${it.message}") }
            }
        }.start()
    }

    private fun openRecordings() {
        recordingsResult.launch(Intent(this, RecordingsActivity::class.java))
    }

    private fun sendCurrent() {
        val f = current ?: store.list().firstOrNull()
        if (f == null) { toast("No recording to send"); return }
        shareFile(this, f)
    }

    private fun importFrom(uri: Uri) {
        toast("Importing…")
        val name = displayName(uri)?.substringBeforeLast('.') ?: "import"
        Thread {
            val result = runCatching {
                val audio = Audio.decode(this, uri)
                val file = store.unique(name)
                WavWriter.writeAll(file, audio.sampleRate, audio.samples)
                file
            }
            runOnUiThread {
                result.onSuccess { loadRecording(it) }.onFailure { toast("Could not import: ${it.message}") }
            }
        }.start()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        /** Shares a recording with another app (mail, messengers, Drive…). */
        fun shareFile(activity: AppCompatActivity, file: File) {
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("audio/wav")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.send)))
        }
    }
}
