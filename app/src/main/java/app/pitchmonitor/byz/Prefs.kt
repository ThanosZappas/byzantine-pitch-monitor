package app.pitchmonitor.byz

import android.content.Context
import android.content.SharedPreferences

/** Every setting of the app, with its default. Values are saved as soon as they change. */
class Prefs(private val context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun f(key: String, def: Float) = FloatPref(key, def)
    private fun i(key: String, def: Int) = IntPref(key, def)
    private fun b(key: String, def: Boolean) = BoolPref(key, def)
    private fun s(key: String, def: String) = StringPref(key, def)

    // Detection and view
    var threshold by f("threshold", 1.0f)          // 0..50, shown with two decimals
    var hZoom by f("h_zoom", 1.0f)                  // 1..4
    var vZoom by f("v_zoom", 1.0f)                  // 0.5..2
    var semitoneLines by b("semitone_lines", true)
    var semitoneLabels by b("semitone_labels", false)
    var octaveA3 by b("octave_a3", false)           // false: 440 Hz = A4
    var a4 by i("a4", 440)                           // 415..465
    var instrument by i("instrument", 0)            // 0 C, 1 B♭, 2 E♭, 3 F
    var transposeShift by i("transpose_shift", 0)   // -12..12
    var autoScroll by b("auto_scroll", true)
    var scrollSpeed by i("scroll_speed", 2)         // 1..10
    var showHz by b("show_hz", false)
    var showTuner by b("show_tuner", true)
    var smoothing by i("smoothing", 1)              // 1..5
    var noteNames by i("note_names", 0)             // 0 C D E, 1 Do Re Mi, 2 ΝΗ ΠΑ ΒΟΥ

    // Tempo
    var beatLines by b("beat_lines", false)
    var blink by b("blink", false)
    var bpm by i("bpm", 120)                         // 20..260
    var meter by i("meter", 0)                       // 0 4/4, 1 3/4, 2 none

    // Buttons
    var showHold by b("show_hold", true)
    var showScale by b("show_scale", true)
    var showTempo by b("show_tempo", true)
    var hideTransport by b("hide_transport", false)

    // Scale
    var scale by s("scale", Catalog.C_MAJOR)
    var european by s("european", Catalog.EU_ALL)
    var niOffset by i("ni_offset", 0)                // moria, -72..72

    // Look
    var theme by s("theme", Palette.DARK)
    var language by s("language", Lang.EN)
    var accent by i("accent", DEFAULT_ACCENT)
    var scaleColorsOpen by b("scale_colors_open", true)
    var chromaticColorsOpen by b("chromatic_colors_open", false)

    fun palette(): Palette = Palette.of(context, theme, accent)

    // Colours: a colour the user picked is kept; otherwise the theme's default is used.
    private fun color(key: String, def: Int) = if (sp.contains(key)) sp.getInt(key, def) else def
    private fun setColor(key: String, c: Int) {
        sp.edit().putInt(key, c).apply()
        addRecent(c)
    }

    var colorPitch: Int
        get() = color("color_pitch", palette().defaultPitch)
        set(v) = setColor("color_pitch", v)
    var colorBeats: Int
        get() = color("color_beats", palette().defaultBeats)
        set(v) = setColor("color_beats", v)
    var colorMetronome: Int
        get() = color("color_metronome", palette().defaultMetronome)
        set(v) = setColor("color_metronome", v)

    fun scaleColor(i: Int): Int = color("color_scale_$i", palette().defaultScale[i])
    fun setScaleColor(i: Int, c: Int) = setColor("color_scale_$i", c)
    fun chromaticColor(i: Int): Int = color("color_chromatic_$i", palette().defaultChromatic[i])
    fun setChromaticColor(i: Int, c: Int) = setColor("color_chromatic_$i", c)

    val scaleColors: IntArray get() { val p = palette(); return IntArray(8) { color("color_scale_$it", p.defaultScale[it]) } }
    val chromaticColors: IntArray get() { val p = palette(); return IntArray(12) { color("color_chromatic_$it", p.defaultChromatic[it]) } }

    /** Back to the theme's own graph colours. */
    fun resetColors() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("color_") }.forEach { e.remove(it) }
        e.apply()
    }

    /** Forgets a picked colour so the theme's default is used again. */
    fun clearColor(key: String) = sp.edit().remove(key).apply()

    /** Colours picked lately, newest first (for the colour picker). */
    val recentColors: List<Int>
        get() = (sp.getString("recent_colors", "") ?: "").split(',').mapNotNull { it.toLongOrNull()?.toInt() }

    private fun addRecent(c: Int) {
        val list = (listOf(c) + recentColors.filter { it != c }).take(8)
        sp.edit().putString("recent_colors", list.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() }).apply()
    }

    /** Written-above-sounding transposition in semitones. */
    val transpose: Int get() = Catalog.INSTRUMENT_SHIFT[instrument.coerceIn(0, 3)] + transposeShift

    /** The DEFAULT button: everything back to its default, except where the base sits. */
    fun resetToDefaults() {
        val keepOffset = niOffset
        val keepLanguage = language
        sp.edit().clear().apply()
        niOffset = keepOffset
        language = keepLanguage
    }

    /** The grid for the selected scale. */
    fun buildGrid(): Grid {
        val mode = Byzantine.mode(scale)
        val p = palette()
        val labels = LabelColors(p.labelStrong, p.label, p.labelFaint)
        if (mode != null) return ByzantineGrid(mode, Byzantine.niCents(niOffset, a4), chromaticColors, labels)
        val w = WesternScale.byTitle(scale) ?: WesternScale.ALL[0]
        return WesternGrid(w, a4, transpose, noteNames, octaveA3, semitoneLines, semitoneLabels, scaleColors, chromaticColors, labels)
    }

    // --- delegates -----------------------------------------------------------------------

    inner class FloatPref(private val key: String, private val def: Float) {
        operator fun getValue(r: Any?, p: Any?): Float = sp.getFloat(key, def)
        operator fun setValue(r: Any?, p: Any?, v: Float) = sp.edit().putFloat(key, v).apply()
    }

    inner class IntPref(private val key: String, private val def: Int) {
        operator fun getValue(r: Any?, p: Any?): Int = sp.getInt(key, def)
        operator fun setValue(r: Any?, p: Any?, v: Int) = sp.edit().putInt(key, v).apply()
    }

    inner class BoolPref(private val key: String, private val def: Boolean) {
        operator fun getValue(r: Any?, p: Any?): Boolean = sp.getBoolean(key, def)
        operator fun setValue(r: Any?, p: Any?, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    }

    inner class StringPref(private val key: String, private val def: String) {
        operator fun getValue(r: Any?, p: Any?): String = sp.getString(key, def) ?: def
        operator fun setValue(r: Any?, p: Any?, v: String) = sp.edit().putString(key, v).apply()
    }

    companion object {
        const val DEFAULT_ACCENT = 0xFFD9AB3F.toInt()

        const val FILE = "byz_settings"

        /** App colour swatches: gold, cyan, red, green, blue, purple, white. */
        val ACCENTS = intArrayOf(
            0xFFD9AB3F.toInt(), 0xFF80CBC4.toInt(), 0xFFE3584B.toInt(), 0xFF5FD38A.toInt(),
            0xFF64A0FF.toInt(), 0xFFB48CF0.toInt(), 0xFFE0E0E0.toInt(),
        )

        /** Byzantine tempo (αγωγή) presets: string resource and BPM. Approximate teaching values. */
        val TEMPO_PRESETS = listOf(
            R.string.tempo_papadic to 56,
            R.string.tempo_slow_sticheraric to 72,
            R.string.tempo_short_sticheraric to 92,
            R.string.tempo_heirmologic to 112,
            R.string.tempo_short_heirmologic to 132,
        )
    }
}
