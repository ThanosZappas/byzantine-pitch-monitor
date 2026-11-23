package app.pitchmonitor.byz

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.AppCompatSpinner
import java.util.Locale

/** Settings, ordered from the most used to the least used. Changes are saved at once. */
class SettingsActivity : ThemedActivity() {
    private lateinit var root: LinearLayout
    private val dp by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.settings)
        build()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Debug builds: `--ei scrollTo <dp>` on the main screen's intent is passed on here.
            val y = intent.getIntExtra("scrollTo", 0)
            if (y > 0) root.post { (root.parent as ScrollView).scrollTo(0, px(y)) }
            if (intent.getBooleanExtra("picker", false)) root.post {
                ColorPicker.show(this, getString(R.string.color_pitch), prefs.colorPitch, prefs.recentColors, palette.dark,
                    onDefault = {}) {}
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { finish(); return true }
            R.id.action_default -> { prefs.resetToDefaults(); recreate(); return true }
        }
        return super.onOptionsItemSelected(item)
    }

    /** Closes Settings and lets the main screen run a recordings action. */
    private fun finishWith(action: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ACTION, action))
        finish()
    }

    // ------------------------------------------------------------------------------------
    // The screen
    // ------------------------------------------------------------------------------------

    private fun build() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(root); setBackgroundColor(palette.settingsBg) }
        setContentView(scroll)

        // Detection and view
        slider(R.string.threshold, 0, 5000, Math.round(prefs.threshold * 100), { String.format(Locale.US, "%.2f", it / 100f) }) {
            prefs.threshold = it / 100f
        }
        slider(R.string.h_zoom, 0, 300, Math.round((prefs.hZoom - 1f) * 100), { zoom(1f + it / 100f) }) {
            prefs.hZoom = 1f + it / 100f
        }
        slider(R.string.v_zoom, 0, 150, Math.round((prefs.vZoom - 0.5f) * 100), { zoom(0.5f + it / 100f) }) {
            prefs.vZoom = 0.5f + it / 100f
        }

        header(R.string.tuner)
        check(R.string.show_tuner, prefs.showTuner, left = 15) { prefs.showTuner = it }
        labelledSeek(R.string.smoothing, 0, 4, prefs.smoothing - 1, { (it + 1).toString() }) { prefs.smoothing = it + 1 }
        check(R.string.show_hz, prefs.showHz, left = 15) { prefs.showHz = it }

        header(R.string.auto_scroll)
        check(R.string.enable_auto_scroll, prefs.autoScroll, left = 15) { prefs.autoScroll = it }
        labelledSeek(R.string.scroll_speed, 0, 9, prefs.scrollSpeed - 1, { (it + 1).toString() }) { prefs.scrollSpeed = it + 1 }

        buildTempo()

        header(R.string.button_display)
        row(left = 25) {
            addView(checkBox(R.string.hold, prefs.showHold) { prefs.showHold = it })
            addView(checkBox(R.string.scale, prefs.showScale) { prefs.showScale = it })
            addView(checkBox(R.string.tempo_button, prefs.showTempo) { prefs.showTempo = it })
        }
        check(R.string.hide_transport, prefs.hideTransport, left = 15) { prefs.hideTransport = it }

        header(R.string.note_name)
        root.addView(radios(listOf(getString(R.string.names_english), getString(R.string.names_solfege), "ΝΗ, ΠΑ, ΒΟΥ, ΓΑ, ΔΙ, ΚΕ, ΖΩ"),
            prefs.noteNames, horizontal = false) { prefs.noteNames = it }, params(left = 15))

        header(R.string.european_scales)
        val modes = listOf(Catalog.EU_ALL, Catalog.EU_C_MAJOR, Catalog.EU_NONE)
        root.addView(radios(listOf(getString(R.string.eu_all), getString(R.string.eu_cmajor), getString(R.string.eu_none)),
            modes.indexOf(prefs.european).coerceAtLeast(0), horizontal = false) {
            prefs.european = modes[it]
            if (!Catalog.visible(prefs.scale, prefs.european)) prefs.scale = Catalog.visibleList(prefs.european).first()
        }, params(left = 15))

        header(R.string.recordings)
        row(left = 15, right = 10, bottom = 5) {
            for ((label, action) in listOf(R.string.list_button to ACTION_LIST, R.string.send to ACTION_SEND, R.string.import_ to ACTION_IMPORT)) {
                addView(AppCompatButton(this@SettingsActivity).apply {
                    setText(label)
                    isAllCaps = false
                    textSize = 15f
                    maxLines = 1
                    setPadding(px(4), paddingTop, px(4), paddingBottom)
                    setOnClickListener { finishWith(action) }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

        buildLinesAndTuning()
        buildAppearance()
        buildColors()

        header(R.string.language)
        val langs = listOf(Lang.EN, Lang.EL)
        root.addView(radios(listOf("English", "Ελληνικά"), langs.indexOf(prefs.language).coerceAtLeast(0), horizontal = true) {
            if (prefs.language != langs[it]) { prefs.language = langs[it]; recreate() }
        }, params(left = 15))

        buildFooter()
        applyAccentMarks()
        tintAll(root)
    }

    private fun buildTempo() {
        header(R.string.tempo)
        check(R.string.beat_lines, prefs.beatLines, left = 15) { prefs.beatLines = it }
        root.addView(label(R.string.metronome), params(left = 15, top = 5))
        check(R.string.blink, prefs.blink, left = 25) { prefs.blink = it }

        // Byzantine tempo (αγωγή) presets; the BPM slider stays for any other value.
        val bpmValue = value(prefs.bpm.toString())
        lateinit var bpmSeek: SeekBar
        val presetNames = listOf(getString(R.string.tempo_custom)) +
            Prefs.TEMPO_PRESETS.map { (res, bpm) -> getString(res) + "  ·  ♩=" + bpm }
        val presets = AppCompatSpinner(this)
        var settingFromPreset = false
        fun presetIndex(bpm: Int) = Prefs.TEMPO_PRESETS.indexOfFirst { it.second == bpm } + 1
        root.addView(label(R.string.byz_tempo), params(left = 15, top = 10))
        presets.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presetNames).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        presets.setSelection(presetIndex(prefs.bpm))
        presets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos == 0) return
                settingFromPreset = true
                bpmSeek.progress = Prefs.TEMPO_PRESETS[pos - 1].second - 20
                settingFromPreset = false
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(presets, params(left = 15, right = 10))

        row(left = 15, right = 10, top = 10) {
            addView(label(R.string.bpm), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(bpmValue)
        }
        row(left = 15, right = 10) {
            addView(stepButton("<") { bpmSeek.progress = (bpmSeek.progress - 1).coerceAtLeast(0) })
            bpmSeek = seekBar(0, 240, prefs.bpm - 20) {
                prefs.bpm = 20 + it
                bpmValue.text = prefs.bpm.toString()
                if (!settingFromPreset) presets.setSelection(presetIndex(prefs.bpm))
            }
            addView(bpmSeek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(stepButton(">") { bpmSeek.progress = (bpmSeek.progress + 1).coerceAtMost(240) })
        }
        row(left = 15, top = 5) {
            addView(label(R.string.meter), LinearLayout.LayoutParams(px(60), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(radios(listOf("4/4", "3/4", getString(R.string.meter_none)), prefs.meter, horizontal = true) { prefs.meter = it },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun buildLinesAndTuning() {
        header(R.string.lines_tuning)
        check(R.string.semitone_lines, prefs.semitoneLines, left = 15) { prefs.semitoneLines = it }
        check(R.string.semitone_labels, prefs.semitoneLabels, left = 15) { prefs.semitoneLabels = it }

        root.addView(label(R.string.octave_number), params(left = 15, top = 8))
        row(left = 25) {
            addView(label(R.string.octave_440), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(radios(listOf("A4", "A3"), if (prefs.octaveA3) 1 else 0, horizontal = true) { prefs.octaveA3 = it == 1 },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        }

        val a4Value = value("${prefs.a4}Hz")
        row(left = 15, right = 10, top = 8) {
            addView(label(R.string.calibration), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(label(R.string.a4_eq), LinearLayout.LayoutParams(px(60), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(a4Value)
        }
        seek(0, 50, prefs.a4 - 415, left = 15, right = 15) { prefs.a4 = 415 + it; a4Value.text = "${prefs.a4}Hz" }

        root.addView(label(R.string.transpose), params(left = 15, top = 8))
        root.addView(radios(listOf(getString(R.string.inst_c), getString(R.string.inst_bb), getString(R.string.inst_eb), getString(R.string.inst_f)),
            prefs.instrument, horizontal = true) { prefs.instrument = it }, params(left = 20))
        val tValue = value(prefs.transposeShift.toString())
        row(left = 0, right = 10) {
            addView(seekBar(0, 24, prefs.transposeShift + 12) { prefs.transposeShift = it - 12; tValue.text = (it - 12).toString() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(tValue)
        }
    }

    private fun buildAppearance() {
        header(R.string.appearance)
        root.addView(label(R.string.theme), params(left = 15, top = 4))
        val names = listOf(R.string.theme_system, R.string.theme_dark, R.string.theme_black, R.string.theme_light, R.string.theme_parchment)
        root.addView(radios(names.map { getString(it) }, Palette.THEMES.indexOf(prefs.theme).coerceAtLeast(0), horizontal = false) {
            if (prefs.theme != Palette.THEMES[it]) { prefs.theme = Palette.THEMES[it]; recreate() }
        }, params(left = 20))

        root.addView(label(R.string.app_colour), params(left = 15, top = 8))
        row(left = 20, top = 8, bottom = 6) {
            Prefs.ACCENTS.forEachIndexed { i, c ->
                addView(View(this@SettingsActivity).apply {
                    tag = "swatch$i"
                    contentDescription = getString(R.string.app_colour) + " ${i + 1}"
                    setOnClickListener { if (prefs.accent != c) { prefs.accent = c; recreate() } }
                }, LinearLayout.LayoutParams(px(34), px(34)).apply { rightMargin = px(10) })
            }
        }
    }

    private fun buildColors() {
        header(R.string.color)
        colorRow(R.string.color_pitch, "color_pitch", { prefs.colorPitch }) { prefs.colorPitch = it }
        colorRow(R.string.color_beats, "color_beats", { prefs.colorBeats }) { prefs.colorBeats = it }
        colorRow(R.string.color_metronome, "color_metronome", { prefs.colorMetronome }) { prefs.colorMetronome = it }
        val degrees = listOf(R.string.deg_1, R.string.deg_2, R.string.deg_3, R.string.deg_4, R.string.deg_5, R.string.deg_6,
            R.string.deg_7, R.string.deg_semitone)
        collapsible(R.string.scale, { prefs.scaleColorsOpen }, { prefs.scaleColorsOpen = it }) { box ->
            degrees.forEachIndexed { i, name ->
                colorRow(name, "color_scale_$i", { prefs.scaleColor(i) }, into = box) { prefs.setScaleColor(i, it) }
            }
        }
        collapsible(R.string.chromatic, { prefs.chromaticColorsOpen }, { prefs.chromaticColorsOpen = it }) { box ->
            listOf("C", "C♯ (D♭)", "D", "D♯ (E♭)", "E", "F", "F♯ (G♭)", "G", "G♯ (A♭)", "A", "A♯ (B♭)", "B").forEachIndexed { i, name ->
                colorRow(0, "color_chromatic_$i", { prefs.chromaticColor(i) }, into = box, title = name) { prefs.setChromaticColor(i, it) }
            }
        }
    }

    /** A quiet line at the bottom: send an idea or a problem by email. */
    private fun buildFooter() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        root.addView(View(this).apply { setBackgroundColor(palette.settingsMuted and 0x33FFFFFF) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { setMargins(px(15), px(28), px(15), 0) })
        root.addView(TextView(this).apply {
            setText(R.string.feedback)
            textSize = 13f
            setTextColor(palette.settingsMuted)
            setPadding(px(15), px(14), px(15), px(4))
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { sendFeedback(version) }
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.version_line, version, DEVELOPER)
            textSize = 12f
            setTextColor(palette.settingsMuted and 0xB3FFFFFF.toInt())
            setPadding(px(15), 0, px(15), px(28))
        })
    }

    private fun sendFeedback(version: String) {
        val subject = "Byzantine Pitch Monitor $version"
        val body = "\n\n—\nVersion $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(mail)
        } catch (e: ActivityNotFoundException) {
            // No email app: open Gmail's compose page in the browser instead.
            val web = Uri.parse("https://mail.google.com/mail/?view=cm&fs=1&to=$FEEDBACK_EMAIL&su=" +
                Uri.encode(subject) + "&body=" + Uri.encode(body))
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, web)) }
        }
    }

    // ------------------------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------------------------

    private fun px(v: Int) = (v * dp).toInt()

    private fun params(left: Int = 0, right: Int = 0, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(px(left), px(top), px(right), px(bottom))
        }

    private fun headerText(@StringRes text: Int) = TextView(this).apply {
        setText(text)
        textSize = 20f
        setTextColor(palette.settingsHeader)
    }

    private fun header(@StringRes text: Int) {
        root.addView(headerText(text), params(left = 5, right = 5, top = 14, bottom = 4))
    }

    private fun label(@StringRes text: Int) = label(getString(text))

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(palette.settingsMuted)
    }

    private fun value(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(palette.settingsText)
    }

    private fun row(left: Int = 0, right: Int = 0, top: Int = 0, bottom: Int = 0, fill: LinearLayout.() -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        r.fill()
        root.addView(r, params(left, right, top, bottom))
    }

    private fun checkBox(@StringRes text: Int, checked: Boolean, onChange: (Boolean) -> Unit) = AppCompatCheckBox(this).apply {
        setText(text)
        textSize = 16f
        setTextColor(palette.settingsText)
        isChecked = checked
        setPadding(px(6), px(10), px(14), px(10))
        setOnCheckedChangeListener { _, b -> onChange(b) }
    }

    private fun check(@StringRes text: Int, checked: Boolean, left: Int, onChange: (Boolean) -> Unit) {
        root.addView(checkBox(text, checked, onChange), params(left = left, top = 2, bottom = 2))
    }

    private fun radios(labels: List<String>, selected: Int, horizontal: Boolean, onSelect: (Int) -> Unit): RadioGroup {
        val g = RadioGroup(this)
        g.orientation = if (horizontal) RadioGroup.HORIZONTAL else RadioGroup.VERTICAL
        val ids = labels.map { text ->
            AppCompatRadioButton(this).apply {
                id = View.generateViewId()
                this.text = text
                textSize = 16f
                setTextColor(palette.settingsText)
                setPadding(px(6), px(10), px(6), px(10))
                g.addView(this, if (horizontal) RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                else RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }.id
        }
        if (selected in ids.indices) g.check(ids[selected])
        g.setOnCheckedChangeListener { _, id -> ids.indexOf(id).takeIf { it >= 0 }?.let(onSelect) }
        return g
    }

    private fun seekBar(min: Int, max: Int, value: Int, onChange: (Int) -> Unit) = AppCompatSeekBar(this).apply {
        this.max = max - min
        progress = (value - min).coerceIn(0, max - min)
        setPadding(px(16), px(12), px(16), px(12))
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) = onChange(min + p)
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun seek(min: Int, max: Int, value: Int, left: Int, right: Int, onChange: (Int) -> Unit) {
        root.addView(seekBar(min, max, value, onChange), params(left = left, right = right))
    }

    /** "Title ......... value" with a slider underneath. */
    private fun slider(@StringRes title: Int, min: Int, max: Int, value: Int, format: (Int) -> String, onChange: (Int) -> Unit) {
        val v = value(format(value))
        row(left = 5, right = 10, top = 10) {
            addView(headerText(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(v)
        }
        seek(min, max, value, left = 15, right = 15) { onChange(it); v.text = format(it) }
    }

    /** "Label  ---slider---  value" on one line. */
    private fun labelledSeek(@StringRes title: Int, min: Int, max: Int, value: Int, format: (Int) -> String, onChange: (Int) -> Unit) {
        val v = value(format(value))
        row(left = 15, right = 10) {
            addView(label(title), LinearLayout.LayoutParams(px(110), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(seekBar(min, max, value) { onChange(it); v.text = format(it) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(v)
        }
    }

    private fun stepButton(text: String, onClick: () -> Unit) = AppCompatButton(this).apply {
        this.text = text
        minWidth = 0; minimumWidth = px(36)
        setOnClickListener { onClick() }
    }

    private fun colorRow(
        @StringRes name: Int,
        key: String,
        get: () -> Int,
        into: LinearLayout = root,
        title: String = getString(name),
        set: (Int) -> Unit,
    ) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(palette.settingsMuted) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val swatch = View(this)
        fun paint() {
            swatch.background = GradientDrawable().apply {
                cornerRadius = 6 * dp
                setColor(get())
                setStroke(px(1), if (palette.dark) 0x33FFFFFF else 0x33000000)
            }
        }
        paint()
        swatch.contentDescription = title
        swatch.setOnClickListener {
            ColorPicker.show(this, title, get(), prefs.recentColors, palette.dark,
                onDefault = { prefs.clearColor(key); paint() }) { c -> set(c); paint() }
        }
        r.addView(swatch, LinearLayout.LayoutParams(px(96), px(26)))
        into.addView(r, params(left = 32, right = 32, top = 4, bottom = 4))
    }

    private fun collapsible(@StringRes title: Int, isOpen: () -> Boolean, setOpen: (Boolean) -> Unit, fill: (LinearLayout) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fill(box)
        val chevron = AppCompatImageView(this).apply {
            setImageResource(R.drawable.ic_chevron)
            setColorFilter(palette.settingsMuted)
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(24), px(14), px(24), px(14))
            addView(chevron, LinearLayout.LayoutParams(px(24), px(24)))
            addView(TextView(this@SettingsActivity).apply {
                setText(title); textSize = 16f; setTextColor(palette.settingsMuted); alpha = 0.7f; setPadding(px(22), 0, 0, 0)
            })
        }
        fun show(open: Boolean) {
            box.visibility = if (open) View.VISIBLE else View.GONE
            chevron.rotation = if (open) 0f else 180f
        }
        show(isOpen())
        head.setOnClickListener { val o = !isOpen(); setOpen(o); show(o) }
        root.addView(head, params(top = 8))
        root.addView(box, params())
    }

    private fun zoom(v: Float): String {
        val s = String.format(Locale.US, "%.2f", v).trimEnd('0')
        return if (s.endsWith('.')) s + "0" else s
    }

    /** Rings the chosen app colour. */
    private fun applyAccentMarks() {
        Prefs.ACCENTS.forEachIndexed { i, color ->
            root.findViewWithTag<View>("swatch$i")?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                val ring = if (palette.dark) 0xFFFFFFFF.toInt() else 0xFF222222.toInt()
                if (color == prefs.accent) setStroke(px(3), ring) else setStroke(px(1), if (palette.dark) 0x55FFFFFF else 0x33000000)
            }
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_LIST = "list"
        const val ACTION_SEND = "send"
        const val ACTION_IMPORT = "import"
        const val FEEDBACK_EMAIL = "thanoszapp@gmail.com"
        const val DEVELOPER = "Athanasios Zappas"
    }
}
