package app.pitchmonitor.byz

import android.content.Context
import android.content.SharedPreferences

/** Every setting of the app, with its default. Values are saved as soon as they change. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("byz_settings", Context.MODE_PRIVATE)

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

    // Colours
    var colorPitch by i("color_pitch", 0xFFFFFF00.toInt())
    var colorBeats by i("color_beats", 0xFF444444.toInt())
    var colorMetronome by i("color_metronome", 0xFF000080.toInt())
    var accent by i("accent", DEFAULT_ACCENT)
    var scaleColorsOpen by b("scale_colors_open", true)
    var chromaticColorsOpen by b("chromatic_colors_open", false)

    fun scaleColor(i: Int): Int = sp.getInt("color_scale_$i", DEFAULT_SCALE_COLORS[i])
    fun setScaleColor(i: Int, c: Int) = sp.edit().putInt("color_scale_$i", c).apply()
    fun chromaticColor(i: Int): Int = sp.getInt("color_chromatic_$i", DEFAULT_CHROMATIC_COLORS[i])
    fun setChromaticColor(i: Int, c: Int) = sp.edit().putInt("color_chromatic_$i", c).apply()

    val scaleColors: IntArray get() = IntArray(8) { scaleColor(it) }
    val chromaticColors: IntArray get() = IntArray(12) { chromaticColor(it) }

    /** Written-above-sounding transposition in semitones. */
    val transpose: Int get() = Catalog.INSTRUMENT_SHIFT[instrument.coerceIn(0, 3)] + transposeShift

    /** The DEFAULT button: everything back to its default, except where the base sits. */
    fun resetToDefaults() {
        val keepOffset = niOffset
        sp.edit().clear().apply()
        niOffset = keepOffset
    }

    /** The grid for the selected scale. */
    fun buildGrid(): Grid {
        val mode = Byzantine.mode(scale)
        if (mode != null) return ByzantineGrid(mode, Byzantine.niCents(niOffset, a4), chromaticColors)
        val w = WesternScale.byTitle(scale) ?: WesternScale.ALL[0]
        return WesternGrid(w, a4, transpose, noteNames, octaveA3, semitoneLines, semitoneLabels, scaleColors, chromaticColors)
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

        /** Settings colour swatches: gold, cyan, red, green, blue, purple, white. */
        val ACCENTS = intArrayOf(
            0xFFD9AB3F.toInt(), 0xFF80CBC4.toInt(), 0xFFE3584B.toInt(), 0xFF5FD38A.toInt(),
            0xFF64A0FF.toInt(), 0xFFB48CF0.toInt(), 0xFFE0E0E0.toInt(),
        )

        /** Tonic, 2nd..7th degree, semitone. */
        val DEFAULT_SCALE_COLORS = intArrayOf(
            0xFF808080.toInt(), 0xFF404040.toInt(), 0xFF404040.toInt(), 0xFF404040.toInt(),
            0xFF404040.toInt(), 0xFF404040.toInt(), 0xFF404040.toInt(), 0xFF202020.toInt(),
        )

        /** C, C♯ .. B. Byzantine lines use C for the base, G for dominants and D for the rest. */
        val DEFAULT_CHROMATIC_COLORS = IntArray(12) { pc ->
            when {
                pc == 0 -> 0xFF808080.toInt()
                pc in intArrayOf(2, 4, 5, 7, 9, 11) -> 0xFF404040.toInt()
                else -> 0xFF282828.toInt()
            }
        }
    }
}
