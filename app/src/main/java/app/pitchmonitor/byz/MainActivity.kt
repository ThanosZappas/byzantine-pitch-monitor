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
import android.view.ViewGroup
import android.widget.LinearLayout
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale
import kotlin.math.pow

class MainActivity : ThemedActivity() {
    override val systemBarColor: Int get() = palette.bar
    private lateinit var store: RecordingStore
    private val liveTrack = PitchTrack()
    private val engine = AudioEngine(liveTrack)

    private lateinit var graph: PitchView
    private lateinit var btnHold: TextView
    private lateinit var btnLive: TextView
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
        if (ok) startInput() else toast(getString(R.string.mic_needed))
    }

    /** Results from Settings / Appearance / Recordings: open a recording or import one. */
    private val hubResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data ?: return@registerForActivityResult
        data.getStringExtra(HubActivity.EXTRA_FILE)?.let { loadRecording(File(it)) }
        if (data.getStringExtra(HubActivity.EXTRA_ACTION) == HubActivity.ACTION_IMPORT) importAudio.launch(arrayOf("audio/*"))
    }

    private val importAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFrom(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RecordingStore(this)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        graph = findViewById(R.id.graph)
        btnHold = findViewById(R.id.btnHold)
        btnLive = findViewById(R.id.btnLive)
        btnLive.setOnClickListener { player?.stop(); showLive() }
        btnScale = findViewById(R.id.btnScale)
        baseControls = findViewById(R.id.baseControls)
        baseDetails = findViewById(R.id.baseDetails)
        btnReset = findViewById(R.id.btnBaseReset)
        transport = findViewById(R.id.transport)
        btnStop = findViewById(R.id.btnStop)
        btnRecord = findViewById(R.id.btnRecord)
        btnPlay = findViewById(R.id.btnPlay)

        findViewById<TextView>(R.id.title).typeface = Typeface.createFromAsset(assets, "fonts/GFSDidot.otf")
        applyPalette()
        baseDetails.setOnClickListener { setOffset(0) }
        graph.track = liveTrack
        graph.onZoom = { h, v -> prefs.hZoom = h; prefs.vZoom = v }
        graph.centerCents = Byzantine.niCents(prefs.niOffset, prefs.a4) + 600

        findViewById<View>(R.id.btnMenu).setOnClickListener { openMenu() }
        findViewById<com.google.android.material.navigationrail.NavigationRailView>(R.id.mainRail).apply {
            inflateMenu(R.menu.nav)
            labelVisibilityMode = com.google.android.material.navigationrail.NavigationRailView.LABEL_VISIBILITY_LABELED
            menuGravity = android.view.Gravity.CENTER
            setOnItemSelectedListener { openHub(pageOf(it.itemId)); false }
        }
        applyNavMode()
        setupSwipeToMenu()
        btnReset.maxLines = 1
        btnReset.setAutoSizeTextTypeUniformWithConfiguration(7, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
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
     * `--es scale "Ήχος Α'"` or `--ei scaleIndex 1`, `--ez demo true` (a synthetic voice instead of the microphone),
     * `--es open settings|recordings`.
     */
    private var demo = false

    private fun debugExtras() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        // `--es theme light|dark|black|parchment|system`, `--es lang en|el`, `--ei accent 0..6`
        var restyle = false
        intent.getStringExtra("theme")?.let { if (it in Palette.THEMES && it != prefs.theme) { prefs.theme = it; restyle = true } }
        intent.getStringExtra("lang")?.let { if (it != prefs.language) { prefs.language = it; restyle = true } }
        intent.getIntExtra("accent", -1).takeIf { it in Prefs.ACCENTS.indices }?.let {
            if (Prefs.ACCENTS[it] != prefs.accent) { prefs.accent = Prefs.ACCENTS[it]; restyle = true }
        }
        intent.removeExtra("theme"); intent.removeExtra("lang"); intent.removeExtra("accent")
        if (restyle) { recreate(); return }
        intent.getStringExtra("scale")?.let { if (Catalog.ORDERED.contains(it)) prefs.scale = it }
        intent.getIntExtra("scaleIndex", -1).takeIf { it in Catalog.ORDERED.indices }?.let { prefs.scale = Catalog.ORDERED[it] }
        demo = intent.getBooleanExtra("demo", false)
        if (demo) Thread({ demoVoice() }, "demo-voice").start()
        intent.getStringExtra("importFile")?.let { importFrom(Uri.fromFile(File(it))) }
        when (intent.getStringExtra("open")) {
            "settings" -> openHub(HubActivity.PAGE_SETTINGS)
            "appearance" -> openHub(HubActivity.PAGE_APPEARANCE)
            "recordings" -> openHub(HubActivity.PAGE_RECORDINGS)
            "menu" -> openMenu()
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
            // Picked colours keep their hue but are darkened/lightened until visible (3:1) on this theme's graph.
            pitchColor = Colors.ensure(prefs.colorPitch, Colors.AA_UI, palette.graphBg),
            beatsColor = Colors.ensure(prefs.colorBeats, 1.5, palette.graphBg), metronomeColor = prefs.colorMetronome,
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

    /** Colours every bar, button and the graph from the theme and app colour. */
    private fun applyPalette() {
        val p = palette
        val d = resources.displayMetrics.density
        findViewById<View>(R.id.topBar).setBackgroundColor(p.bar)
        findViewById<View>(R.id.bottomBar).setBackgroundColor(p.bar)
        findViewById<View>(R.id.lineTop).setBackgroundColor(p.barLine)
        findViewById<View>(R.id.lineBottom).setBackgroundColor(p.barLine)
        findViewById<TextView>(R.id.title).setTextColor(p.accentText)
        findViewById<View>(R.id.root).setBackgroundColor(p.bar)
        findViewById<ImageButton>(R.id.btnMenu).apply {
            imageTintList = android.content.res.ColorStateList.valueOf(p.accentText)
            background = p.ripple(android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()))
        }
        findViewById<View>(R.id.railLine).setBackgroundColor(p.outlineVariant)
        findViewById<com.google.android.material.navigationrail.NavigationRailView>(R.id.mainRail).apply {
            setBackgroundColor(p.bar)
            HubActivity.styleRail(this, p, checkable = false)
        }
        graph.setPalette(p)
        btnHold.background = p.ripple(p.mask(6f, d))
        btnStop.imageTintList = android.content.res.ColorStateList.valueOf(p.stop)
        btnRecord.imageTintList = android.content.res.ColorStateList.valueOf(p.record)
        btnPlay.imageTintList = android.content.res.ColorStateList.valueOf(p.play)
        for (b in listOf(btnStop, btnRecord, btnPlay)) b.background = p.ripple(android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()))
        val scaleStroke = Palette.blend(p.accent, p.bar, if (p.dark) 0.4f else 0.3f)
        btnScale.background = p.chip(stroke = scaleStroke, dp = d)
        btnScale.setTextColor(p.accentText)
        btnLive.background = p.chip(stroke = scaleStroke, dp = d)
        btnLive.setTextColor(p.accentText)
        findViewById<TextView>(R.id.btnBaseVoice).apply {
            background = p.chip(stroke = p.accent, fill = p.accentFill, pressed = p.accentFillPressed, dp = d)
            setTextColor(p.accentOnFill)
        }
        btnReset.background = p.chip(dp = d)
        btnReset.setTextColor(p.text)
        for (id in intArrayOf(R.id.btnDown1, R.id.btnDown6, R.id.btnUp6, R.id.btnUp1)) {
            findViewById<TextView>(id).apply { background = p.chip(dp = d); setTextColor(p.accentText) }
        }
        baseDetails.background = p.chip(stroke = p.infoFill, fill = p.infoFill, pressed = p.chipPressed, dp = d)
        baseDetails.setTextColor(p.infoText)
        refreshHold()
    }

    // --- navigation -------------------------------------------------------------------------

    private fun pageOf(itemId: Int) = when (itemId) {
        R.id.nav_appearance -> HubActivity.PAGE_APPEARANCE
        R.id.nav_recordings -> HubActivity.PAGE_RECORDINGS
        else -> HubActivity.PAGE_SETTINGS
    }

    /** Opens a destination with a slide-in from the right (the rail's side). */
    private fun openHub(page: String) {
        val i = Intent(this, HubActivity::class.java).putExtra(HubActivity.EXTRA_PAGE, page)
        for (k in listOf("scrollTo", "picker", "selectAll")) intent.extras?.get(k)?.let { v ->
            when (v) { is Int -> i.putExtra(k, v); is Boolean -> i.putExtra(k, v) }
        }
        hubResult.launch(i, androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(this, R.anim.nav_enter, R.anim.nav_hold))
    }

    /**
     * Phones (< 600 dp wide): a menu button opens a side panel from the right.
     * Wide screens (landscape, tablets): a permanent navigation rail on the right instead.
     */
    private fun applyNavMode() {
        val wide = isWide()
        findViewById<View>(R.id.mainRail).visibility = if (wide) View.VISIBLE else View.GONE
        findViewById<View>(R.id.railLine).visibility = if (wide) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnMenu).visibility = if (wide) View.GONE else View.VISIBLE
    }

    /**
     * Swipe to open the navigation panel (phones): in from the right edge of the graph, or a
     * left swipe on the title bar. The middle of the right edge is excluded from the system Back
     * gesture so the swipe reaches the app.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToMenu() {
        val d = resources.displayMetrics.density
        val edge = View(this).apply { contentDescription = getString(R.string.menu) }
        (graph.parent as android.widget.FrameLayout).addView(edge,
            android.widget.FrameLayout.LayoutParams((20 * d).toInt(), ViewGroup.LayoutParams.MATCH_PARENT, android.view.Gravity.END))
        edge.addOnLayoutChangeListener { v, _, top, _, bottom, _, _, _, _ ->
            val band = (200 * d).toInt().coerceAtMost(bottom - top)
            val y0 = (bottom - top - band) / 2
            v.systemGestureExclusionRects = listOf(android.graphics.Rect(0, y0, v.width, y0 + band))
        }
        var downX = 0f
        var fired = false
        edge.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX = e.rawX; fired = false; true }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!fired && downX - e.rawX > 32 * d && !isWide()) { fired = true; openMenu() }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> { if (!fired) v.performClick(); true }
                else -> true
            }
        }
        val fling = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent) = true
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 != null && e1.x - e2.x > 48 * d && kotlin.math.abs(vx) > kotlin.math.abs(vy) && !isWide()) { openMenu(); return true }
                return false
            }
        })
        findViewById<View>(R.id.topBar).setOnTouchListener { v, e -> fling.onTouchEvent(e); if (e.action == android.view.MotionEvent.ACTION_UP) v.performClick(); true }
    }

    private fun isWide() = resources.configuration.screenWidthDp >= 600

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyNavMode()
    }

    /** The side panel: the three destinations, then name, version and email pinned at the bottom. */
    private fun openMenu() {
        val p = palette
        val d = resources.displayMetrics.density
        fun px(v: Float) = (v * d).toInt()
        val sheet = com.google.android.material.sidesheet.SideSheetDialog(this)
        val kit = Kit(this, p)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12f), px(20f), px(12f), px(12f))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(p.surfaceContainerHigh)
                val r = 28 * d
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
        }
        content.addView(TextView(this).apply {
            setText(R.string.app_name)
            typeface = Typeface.createFromAsset(assets, "fonts/GFSDidot.otf")
            textSize = 22f
            setTextColor(p.primaryText)
            setPadding(px(16f), 0, px(16f), px(16f))
        })
        val items = listOf(
            Triple(R.drawable.ic_settings_tune, R.string.settings, R.string.nav_settings_desc) to HubActivity.PAGE_SETTINGS,
            Triple(R.drawable.ic_palette, R.string.appearance, R.string.nav_appearance_desc) to HubActivity.PAGE_APPEARANCE,
            Triple(R.drawable.ic_library_music, R.string.recordings, R.string.nav_recordings_desc) to HubActivity.PAGE_RECORDINGS,
        )
        for ((t, page) in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(px(16f), px(14f), px(16f), px(14f))
                background = p.ripple(p.mask(28f, d))
                minimumHeight = px(56f)
                setOnClickListener { sheet.dismiss(); openHub(page) }
            }
            row.addView(kit.icon(t.first, p.onSurfaceVariant), LinearLayout.LayoutParams(px(24f), px(24f)).apply { marginEnd = px(16f) })
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(kit.titleText(getString(t.second)))
            texts.addView(kit.bodyText(getString(t.third)))
            row.addView(texts)
            content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        content.addView(View(this).apply { setBackgroundColor(p.outlineVariant) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            setMargins(px(16f), 0, px(16f), px(8f))
        })
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        content.addView(kit.bodyText(HubActivity.DEVELOPER).apply { setPadding(px(16f), px(4f), px(16f), 0) })
        content.addView(kit.bodyText(getString(R.string.version_only, version)).apply { setPadding(px(16f), 0, px(16f), px(4f)) })
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(px(16f), px(10f), px(16f), px(10f))
            background = p.ripple(p.mask(20f, d))
            addView(kit.icon(R.drawable.ic_mail, p.primaryText), LinearLayout.LayoutParams(px(18f), px(18f)).apply { marginEnd = px(10f) })
            addView(TextView(this@MainActivity).apply { setText(R.string.feedback); textSize = 13f; setTextColor(p.primaryText) })
            setOnClickListener { HubActivity.sendFeedback(this@MainActivity, version) }
        })
        sheet.setContentView(content)
        sheet.show()
        // Let our rounded panel show instead of the sheet's default square background.
        (content.parent as? View)?.background = null
    }

    private fun pickScale() {
        val names = Catalog.visibleList(prefs.european)
        dialog()
            .setTitle(R.string.scale)
            .setSingleChoiceItems(names.toTypedArray(), names.indexOf(prefs.scale).coerceAtLeast(0)) { d, which ->
                prefs.scale = names[which]
                applySettings()
                d.dismiss()
            }
            .show().also { style(it) }
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
            toast(getString(R.string.sing_base))
            return
        }
        val shift = Byzantine.voiceShift(cents, mode.genus.positions[mode.base], prefs.a4)
        setOffset(shift)
        toast(getString(R.string.base_set, Byzantine.NAMES[mode.base], shift))
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
        btnHold.setTextColor(if (on) palette.accentText else palette.hold)
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
        btnLive.visibility = if (showingRecording) View.VISIBLE else View.GONE
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
        if (!engine.running) { toast(getString(R.string.mic_unavailable)); return }
        showLive()
        engine.startRecording(store.newFile())
        toast(getString(R.string.recording_started))
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
            engine.recording -> { finishRecording(); toast(getString(R.string.saved, current?.nameWithoutExtension ?: "")) }
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

    /**
     * Runs [work] on a background thread behind a progress dialog with Cancel. [work] gets a
     * progress callback (0..1, returns false once cancelled). Errors are shown in a dialog and
     * the graph goes back to the live voice.
     */
    private fun <T> withProgress(title: String, work: ((Float) -> Boolean) -> T, done: (T) -> Unit) {
        val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(bar) }
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val dialog = dialog()
            .setTitle(title)
            .setView(box)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled.set(true) }
            .show()
        style(dialog); tintTree(box)
        Thread({
            val result = runCatching {
                work { f -> runOnUiThread { bar.progress = (f * 1000).toInt() }; !cancelled.get() }
            }
            runOnUiThread {
                if (!isFinishing) runCatching { dialog.dismiss() }
                result.onSuccess(done).onFailure { e ->
                    if (e !is CancelledException) {
                        showLive()
                        style(dialog().setTitle(R.string.app_name)
                            .setMessage(getString(R.string.error_open, e.message ?: e.javaClass.simpleName))
                            .setPositiveButton(android.R.string.ok, null).show())
                    }
                }
            }
        }, "load").start()
    }

    /** Analyses [file] and shows its pitch on the graph (held, at its end). */
    private fun loadRecording(file: File, playAfter: Boolean = false) {
        val threshold = thresholdRms(prefs.threshold)
        withProgress(getString(R.string.loading, file.nameWithoutExtension), { progress ->
            val audio = Audio.readWav(file)
            audio to analyseAudio(audio, threshold, progress)
        }) { (audio, frames) ->
            if (frames.none { !it.isNaN() }) {
                showLive()
                style(dialog().setMessage(R.string.no_pitch).setPositiveButton(android.R.string.ok, null).show())
                return@withProgress
            }
            current = file
            currentAudio = audio
            showingRecording = true
            graph.track = PitchTrack.of(frames)
            graph.playbackFrame = null
            graph.held = false
            graph.held = true
            // Centre on the end of the recording.
            for (i in frames.indices.reversed()) if (!frames[i].isNaN()) { graph.centerCents = frames[i].toDouble(); break }
            refreshHold()
            refreshTransport()
            if (playAfter) startPlayback(audio)
        }
    }

    private fun importFrom(uri: Uri) {
        val name = displayName(uri)?.substringBeforeLast('.') ?: "import"
        withProgress(getString(R.string.importing, name), { progress ->
            val audio = Audio.decode(this, uri, progress)
            val file = store.unique(name)
            WavWriter.writeAll(file, audio.sampleRate, audio.samples)
            file
        }) { loadRecording(it) }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

}
