package app.pitchmonitor.byz

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.AppCompatSpinner
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private val dp by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        build()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { finish(); return true }
            R.id.action_default -> { prefs.resetToDefaults(); build(); return true }
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
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        header("Recordings")
        row(left = 15, right = 10, bottom = 5) {
            for ((label, action) in listOf("Recordings list" to ACTION_LIST, "Send" to ACTION_SEND, "Import" to ACTION_IMPORT)) {
                addView(AppCompatButton(this@SettingsActivity).apply {
                    text = label
                    isAllCaps = false
                    textSize = 16f
                    setOnClickListener { finishWith(action) }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

        header("Settings colour")
        row(left = 15, top = 5, bottom = 10) {
            Prefs.ACCENTS.forEachIndexed { i, c ->
                addView(View(this@SettingsActivity).apply {
                    tag = "swatch$i"
                    contentDescription = "Colour ${i + 1}"
                    setOnClickListener { prefs.accent = c; applyAccent() }
                }, LinearLayout.LayoutParams(px(34), px(34)).apply { rightMargin = px(10) })
            }
        }

        slider("Threshold of volume", 0, 5000, Math.round(prefs.threshold * 100), { String.format(Locale.US, "%.2f", it / 100f) }) {
            prefs.threshold = it / 100f
        }
        slider("Horizontal zooming", 0, 300, Math.round((prefs.hZoom - 1f) * 100), { zoom(1f + it / 100f) }) {
            prefs.hZoom = 1f + it / 100f
        }
        slider("Vertical zooming", 0, 150, Math.round((prefs.vZoom - 0.5f) * 100), { zoom(0.5f + it / 100f) }) {
            prefs.vZoom = 0.5f + it / 100f
        }
        check("Indicate lines of a semitone", prefs.semitoneLines, left = 5) { prefs.semitoneLines = it }
        check("Display semitones on the vertical axis", prefs.semitoneLabels, left = 5) { prefs.semitoneLabels = it }

        header("Octave number")
        row(left = 15) {
            addView(label("440Hz ="), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(radios(listOf("A4", "A3"), if (prefs.octaveA3) 1 else 0, horizontal = true) { prefs.octaveA3 = it == 1 },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        }

        val a4Value = TextView(this).apply { textSize = 16f; setTextColor(0xFFE0E0E0.toInt()) }
        row(top = 10) {
            addView(headerText("Calibration"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(label("A4 ="), LinearLayout.LayoutParams(px(80), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(a4Value, LinearLayout.LayoutParams(px(80), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        a4Value.text = "${prefs.a4}Hz"
        seek(0, 50, prefs.a4 - 415, left = 15, right = 15) { prefs.a4 = 415 + it; a4Value.text = "${prefs.a4}Hz" }

        header("Transpose")
        root.addView(radios(listOf("C Inst.", "B♭ Inst.", "E♭ Inst.", "F Inst."), prefs.instrument, horizontal = true) {
            prefs.instrument = it
        }, params(left = 15))
        val tValue = label("")
        row(left = 0, right = 10) {
            addView(seekBar(0, 24, prefs.transposeShift + 12) { prefs.transposeShift = it - 12; tValue.text = (it - 12).toString() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(tValue)
        }
        tValue.text = prefs.transposeShift.toString()

        header("Auto scroll")
        check("Enable auto scrolling", prefs.autoScroll, left = 15) { prefs.autoScroll = it }
        labelledSeek("Scroll speed", 0, 9, prefs.scrollSpeed - 1, { (it + 1).toString() }) { prefs.scrollSpeed = it + 1 }
        check("Display frequency in Hz", prefs.showHz, left = 5) { prefs.showHz = it }

        header("Tuner")
        check("Display tuner", prefs.showTuner, left = 15) { prefs.showTuner = it }
        labelledSeek("Smoothing", 0, 4, prefs.smoothing - 1, { (it + 1).toString() }) { prefs.smoothing = it + 1 }

        header("Note name")
        root.addView(radios(listOf("C, D, E, F, G, A, B", "Do, Re, Mi, Fa, Sol, La, Si", "ΝΗ, ΠΑ, ΒΟΥ, ΓΑ, ΔΙ, ΚΕ, ΖΩ"),
            prefs.noteNames, horizontal = false) { prefs.noteNames = it }, params(left = 15))

        header("Tempo")
        check("Indicate beats with lines", prefs.beatLines, left = 15) { prefs.beatLines = it }
        root.addView(label("Metronome"), params(left = 15, top = 5))
        check("Blink screen", prefs.blink, left = 25) { prefs.blink = it }
        val bpmValue = label(prefs.bpm.toString())
        row(left = 15, right = 10, top = 10) {
            addView(label("BPM"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(bpmValue)
        }
        lateinit var bpmSeek: SeekBar
        row(left = 15, right = 10) {
            addView(stepButton("<") { bpmSeek.progress = (bpmSeek.progress - 1).coerceAtLeast(0) })
            bpmSeek = seekBar(0, 240, prefs.bpm - 20) { prefs.bpm = 20 + it; bpmValue.text = prefs.bpm.toString() }
            addView(bpmSeek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(stepButton(">") { bpmSeek.progress = (bpmSeek.progress + 1).coerceAtMost(240) })
        }
        row(left = 15, top = 5) {
            addView(label("Meter"), LinearLayout.LayoutParams(px(60), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(radios(listOf("4/4", "3/4", "None"), prefs.meter, horizontal = true) { prefs.meter = it },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        header("Button display")
        row(left = 25) {
            addView(checkBox("HOLD", prefs.showHold) { prefs.showHold = it })
            addView(checkBox("Scale", prefs.showScale) { prefs.showScale = it })
            addView(checkBox("Tempo", prefs.showTempo) { prefs.showTempo = it })
        }
        check("Hide record / play / stop buttons", prefs.hideTransport, left = 15) { prefs.hideTransport = it }

        header("Scale")
        val spinner = AppCompatSpinner(this)
        root.addView(spinner, params(left = 15, right = 5, top = 5, bottom = 5))
        fillScales(spinner, prefs.european, prefs.scale)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.scale = spinner.adapter.getItem(pos) as String
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        header("European scales")
        val modes = listOf(Catalog.EU_ALL, Catalog.EU_C_MAJOR, Catalog.EU_NONE)
        root.addView(radios(listOf("Show all European scales", "Only C Major", "Hide all European scales"),
            modes.indexOf(prefs.european).coerceAtLeast(0), horizontal = false) {
            prefs.european = modes[it]
            fillScales(spinner, prefs.european, prefs.scale)
            prefs.scale = spinner.selectedItem as String
        }, params(left = 15))

        header("Color")
        colorRow("Pitch", { prefs.colorPitch }) { prefs.colorPitch = it }
        colorRow("Beats", { prefs.colorBeats }) { prefs.colorBeats = it }
        colorRow("Metronome", { prefs.colorMetronome }) { prefs.colorMetronome = it }
        collapsible("Scale", { prefs.scaleColorsOpen }, { prefs.scaleColorsOpen = it }) { box ->
            listOf("1st Tonic", "2nd Supertonic", "3rd Mediant", "4th Subdominant", "5th Dominant", "6th Subtonic",
                "7th Leading", "Semitone").forEachIndexed { i, name ->
                colorRow(name, { prefs.scaleColor(i) }, into = box) { prefs.setScaleColor(i, it) }
            }
        }
        collapsible("Chromatic", { prefs.chromaticColorsOpen }, { prefs.chromaticColorsOpen = it }) { box ->
            listOf("C", "C♯ (D♭)", "D", "D♯ (E♭)", "E", "F", "F♯ (G♭)", "G", "G♯ (A♭)", "A", "A♯ (B♭)", "B")
                .forEachIndexed { i, name -> colorRow(name, { prefs.chromaticColor(i) }, into = box) { prefs.setChromaticColor(i, it) } }
        }
        root.addView(View(this), LinearLayout.LayoutParams(1, px(24)))

        applyAccent()
    }

    private fun fillScales(spinner: Spinner, european: String, keep: String) {
        val names = Catalog.visibleList(european)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(names.indexOf(keep).coerceAtLeast(0))
        tintAll(spinner)
    }

    // ------------------------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------------------------

    private fun px(v: Int) = (v * dp).toInt()

    private fun params(left: Int = 0, right: Int = 0, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(px(left), px(top), px(right), px(bottom))
        }

    private fun headerText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(0xFFD6D6D6.toInt())
    }

    private fun header(text: String) {
        root.addView(headerText(text), params(left = 5, right = 5, top = 10, bottom = 5))
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(0xFFB8B8B8.toInt())
    }

    private fun row(left: Int = 0, right: Int = 0, top: Int = 0, bottom: Int = 0, fill: LinearLayout.() -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        r.fill()
        root.addView(r, params(left, right, top, bottom))
    }

    private fun checkBox(text: String, value: Boolean, onChange: (Boolean) -> Unit) = AppCompatCheckBox(this).apply {
        this.text = text
        textSize = 16f
        isChecked = value
        setPadding(px(6), px(10), px(14), px(10))
        setOnCheckedChangeListener { _, b -> onChange(b) }
    }

    private fun check(text: String, value: Boolean, left: Int, onChange: (Boolean) -> Unit) {
        root.addView(checkBox(text, value, onChange), params(left = left, top = 2, bottom = 2))
    }

    private fun radios(labels: List<String>, selected: Int, horizontal: Boolean, onSelect: (Int) -> Unit): RadioGroup {
        val g = RadioGroup(this)
        g.orientation = if (horizontal) RadioGroup.HORIZONTAL else RadioGroup.VERTICAL
        val ids = labels.mapIndexed { i, text ->
            AppCompatRadioButton(this).apply {
                id = View.generateViewId()
                this.text = text
                textSize = 16f
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
    private fun slider(title: String, min: Int, max: Int, value: Int, format: (Int) -> String, onChange: (Int) -> Unit) {
        val v = TextView(this).apply { textSize = 16f; setTextColor(0xFFE0E0E0.toInt()); text = format(value) }
        row(left = 5, right = 10, top = 10) {
            addView(headerText(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(v)
        }
        seek(min, max, value, left = 15, right = 15) { onChange(it); v.text = format(it) }
    }

    /** "Label  ---slider---  value" on one line. */
    private fun labelledSeek(title: String, min: Int, max: Int, value: Int, format: (Int) -> String, onChange: (Int) -> Unit) {
        val v = label(format(value))
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

    private fun colorRow(name: String, get: () -> Int, into: LinearLayout = root, set: (Int) -> Unit) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r.addView(TextView(this).apply { text = name; textSize = 16f; setTextColor(0xFFC8C8C8.toInt()) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val swatch = View(this).apply { setBackgroundColor(get()); contentDescription = "$name colour" }
        swatch.setOnClickListener {
            ColorPicker.show(this, name, get()) { c -> set(c); swatch.setBackgroundColor(c) }
        }
        r.addView(swatch, LinearLayout.LayoutParams(px(96), px(24)))
        into.addView(r, params(left = 32, right = 32, top = 4, bottom = 4))
    }

    private fun collapsible(title: String, isOpen: () -> Boolean, setOpen: (Boolean) -> Unit, fill: (LinearLayout) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fill(box)
        val chevron = AppCompatImageView(this).apply {
            setImageResource(R.drawable.ic_chevron)
            setColorFilter(0xFF8A8A8A.toInt())
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(24), px(14), px(24), px(14))
            addView(chevron, LinearLayout.LayoutParams(px(24), px(24)))
            addView(TextView(this@SettingsActivity).apply {
                text = title; textSize = 16f; setTextColor(0xFF5E5E5E.toInt()); setPadding(px(22), 0, 0, 0)
            })
        }
        fun show(open: Boolean) {
            box.visibility = if (open) View.VISIBLE else View.GONE
            chevron.rotation = if (open) 0f else 180f
        }
        show(isOpen())
        head.setOnClickListener { val o = !isOpen(); setOpen(o); show(o) }
        root.addView(head, params(top = 12))
        root.addView(box, params())
    }

    private fun zoom(v: Float): String {
        val s = String.format(Locale.US, "%.2f", v).trimEnd('0')
        return if (s.endsWith('.')) s + "0" else s
    }

    // ------------------------------------------------------------------------------------
    // Settings colour
    // ------------------------------------------------------------------------------------

    private fun applyAccent() {
        val c = prefs.accent
        Prefs.ACCENTS.forEachIndexed { i, color ->
            root.findViewWithTag<View>("swatch$i")?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (color == c) setStroke(px(3), 0xFFFFFFFF.toInt()) else setStroke(px(1), 0x55FFFFFF)
            }
        }
        tintAll(root)
    }

    private fun tintAll(v: View) {
        val c = prefs.accent
        val checkable = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(c, 0xFF9A9A9A.toInt()))
        val solid = ColorStateList.valueOf(c)
        when (v) {
            is CompoundButton -> v.buttonTintList = checkable
            is SeekBar -> { v.thumbTintList = solid; v.progressTintList = solid }
            is Spinner -> v.backgroundTintList = solid
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) tintAll(v.getChildAt(i))
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_LIST = "list"
        const val ACTION_SEND = "send"
        const val ACTION_IMPORT = "import"
    }
}

/** A small colour picker: hue, saturation and brightness sliders plus common colours. */
object ColorPicker {
    private val PRESETS = intArrayOf(
        0xFFFFFF00.toInt(), 0xFFE8A000.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(),
        0xFF0000FF.toInt(), 0xFF000080.toInt(), 0xFFFF00FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFC0C0C0.toInt(),
        0xFF808080.toInt(), 0xFF404040.toInt(), 0xFF202020.toInt(), 0xFF000000.toInt(),
    )

    fun show(context: Context, title: String, initial: Int, onPick: (Int) -> Unit) {
        val dp = context.resources.displayMetrics.density
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initial, hsv)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), 0)
        }
        val preview = View(context).apply { setBackgroundColor(initial) }
        box.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (40 * dp).toInt()))
        val bars = ArrayList<SeekBar>()
        fun current() = android.graphics.Color.HSVToColor(floatArrayOf(bars[0].progress.toFloat(), bars[1].progress / 100f, bars[2].progress / 100f))
        for ((label, max, value) in listOf(Triple("Hue", 360, hsv[0].toInt()), Triple("Saturation", 100, (hsv[1] * 100).toInt()),
            Triple("Brightness", 100, (hsv[2] * 100).toInt()))) {
            box.addView(TextView(context).apply { text = label; setPadding(0, (10 * dp).toInt(), 0, 0) })
            val s = AppCompatSeekBar(context).apply { this.max = max; progress = value }
            bars += s
            box.addView(s)
        }
        bars.forEach { b ->
            b.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) = preview.setBackgroundColor(current())
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        val presets = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val presets2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        PRESETS.forEachIndexed { i, c ->
            val v = View(context).apply {
                setBackgroundColor(c)
                setOnClickListener {
                    val h = FloatArray(3); android.graphics.Color.colorToHSV(c, h)
                    bars[0].progress = h[0].toInt(); bars[1].progress = (h[1] * 100).toInt(); bars[2].progress = (h[2] * 100).toInt()
                    preview.setBackgroundColor(c)
                }
            }
            (if (i < 7) presets else presets2).addView(v, LinearLayout.LayoutParams(0, (32 * dp).toInt(), 1f).apply {
                setMargins((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
            })
        }
        box.addView(presets, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (12 * dp).toInt()
        })
        box.addView(presets2)
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> onPick(current()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
