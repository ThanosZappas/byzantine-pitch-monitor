package app.pitchmonitor.byz

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/** Pitch maths. Pitches are cents relative to 440 Hz unless stated otherwise. */
object Pitch {
    const val MORIA_PER_OCTAVE = 72
    const val CENTS_PER_MORIO = 1200.0 / MORIA_PER_OCTAVE

    fun hzToCents(hz: Double): Double = 1200.0 * ln(hz / 440.0) / ln(2.0)
    fun centsToHz(cents: Double): Double = 440.0 * 2.0.pow(cents / 1200.0)

    /** Offset of the whole tuning when A4 is calibrated to [a4Hz]. */
    fun calibration(a4Hz: Int): Double = hzToCents(a4Hz.toDouble())
}

/** One horizontal line of the pitch graph. */
class GridLine(
    val cents: Double,
    /** Text on the vertical axis, or null for an unlabelled line. */
    val label: String?,
    /** Name used by the tuner (always set). */
    val name: String,
    val color: Int,
    val widthDp: Float,
    val labelColor: Int,
    val strong: Boolean,
    /** False for the semitone lines between the notes of a European scale. */
    val inScale: Boolean = true,
)

/** Axis label colours: base/tonic, other notes, semitones. */
class LabelColors(val strong: Int, val normal: Int, val faint: Int) {
    companion object {
        val DARK = LabelColors(0xFFD0D0D0.toInt(), 0xFF8A8A8A.toInt(), 0xFF606060.toInt())
    }
}

interface Grid {
    /** Lines with cents in [low, high], ascending. */
    fun lines(low: Double, high: Double): List<GridLine>

    /** Lines the tuner snaps to (scale notes), ascending, in [low, high]. */
    fun tunerLines(low: Double, high: Double): List<GridLine> = lines(low, high).filter { it.inScale }

    /** Byzantine grids print the step sizes in moria between neighbouring degrees. */
    val showsMoria: Boolean get() = false

    /** Byzantine labels are drawn in a fixed-width face, like the 1.6.0 app. */
    val monoLabels: Boolean get() = false
}

// ---------------------------------------------------------------------------------------------
// Byzantine modes (72-moria Chrysanthine system)
// ---------------------------------------------------------------------------------------------

/** A genus: the degree it is built from, that degree's place above Ni, and its seven steps. */
class Genus(val startDegree: Int, val startMoria: Int, val steps: IntArray) {
    /** Moria above Ni of each degree Ni..Zo (0..71). */
    val positions: IntArray = IntArray(7).also { pos ->
        var m = startMoria
        for (i in 0 until 7) {
            pos[(startDegree + i) % 7] = Math.floorMod(m, Pitch.MORIA_PER_OCTAVE)
            m += steps[i]
        }
    }

    companion object {
        val DIATONIC_NI = Genus(0, 0, intArrayOf(12, 10, 8, 12, 12, 10, 8))
        val SOFT_CHROMATIC_NI = Genus(0, 0, intArrayOf(8, 14, 8, 12, 8, 14, 8))
        val HARD_CHROMATIC_PA = Genus(1, 12, intArrayOf(6, 20, 4, 12, 6, 20, 4))
        val ENHARMONIC_GA = Genus(3, 30, intArrayOf(12, 12, 6, 12, 12, 12, 6))

        /** Πλ.Δ' εκ του Γα: Ni's phthora on Ga, so from Ga up the steps are Ni's 12 10 8 12 12 10 8. */
        val DIATONIC_GA = Genus(3, 30, intArrayOf(12, 10, 8, 12, 12, 10, 8))
    }
}

class ByzMode(val title: String, val genus: Genus, val base: Int, val dominants: IntArray)

object Byzantine {
    const val NI = 0; const val PA = 1; const val VOU = 2; const val GA = 3
    const val DI = 4; const val KE = 5; const val ZO = 6

    val NAMES = arrayOf("ΝΗ", "ΠΑ", "ΒΟΥ", "ΓΑ", "ΔΙ", "ΚΕ", "ΖΩ")

    /** Ni at offset 0 is C4 = 261.6255653 Hz (shown in the base box without calibration). */
    const val NI_NATURAL_HZ = 261.6255653

    val MODES = listOf(
        ByzMode("Ήχος Α'", Genus.DIATONIC_NI, PA, intArrayOf(PA, DI, GA)),
        ByzMode("Ήχος Β'", Genus.SOFT_CHROMATIC_NI, DI, intArrayOf(DI, NI, KE)),
        ByzMode("Ήχος Γ'", Genus.ENHARMONIC_GA, GA, intArrayOf(GA, DI, NI)),
        ByzMode("Ήχος Δ'", Genus.DIATONIC_NI, DI, intArrayOf(DI, PA, NI)),
        ByzMode("Ήχος Πλ.Α'", Genus.DIATONIC_NI, PA, intArrayOf(PA, DI, KE)),
        ByzMode("Ήχος Πλ.Β'", Genus.HARD_CHROMATIC_PA, PA, intArrayOf(PA, DI, KE)),
        ByzMode("Ήχος Βαρύς", Genus.ENHARMONIC_GA, GA, intArrayOf(GA, DI, ZO)),
        ByzMode("Ήχος Πλ.Δ'", Genus.DIATONIC_NI, NI, intArrayOf(NI, GA, DI)),
        ByzMode("Ήχος Πλ.Δ' εκ του Γα", Genus.DIATONIC_GA, GA, intArrayOf(GA, DI, ZO)),
    )

    fun mode(title: String): ByzMode? = MODES.firstOrNull { it.title == title }

    /** Cents (re 440 Hz) of Ni at moria [offset] from C4, in the calibrated tuning. */
    fun niCents(offset: Int, a4Hz: Int): Double =
        -900.0 + Pitch.calibration(a4Hz) + offset * Pitch.CENTS_PER_MORIO

    /** Ni shift (-36..35 moria) that puts a degree [baseMoria] above Ni on the sung pitch [cents]. */
    fun voiceShift(cents: Double, baseMoria: Int, a4Hz: Int): Int {
        val d = (cents - niCents(0, a4Hz)) / Pitch.CENTS_PER_MORIO - baseMoria
        var shift = Math.floorMod(Math.round(d).toInt(), Pitch.MORIA_PER_OCTAVE)
        if (shift >= 36) shift -= 72
        return shift
    }
}

class ByzantineGrid(
    private val mode: ByzMode,
    private val niCents: Double,
    private val chromatic: IntArray,
    private val labels: LabelColors = LabelColors.DARK,
) : Grid {
    override val showsMoria get() = true
    override val monoLabels get() = true

    override fun lines(low: Double, high: Double): List<GridLine> {
        val out = ArrayList<GridLine>()
        val first = floor((low - niCents) / 1200.0).toInt() - 1
        val last = floor((high - niCents) / 1200.0).toInt() + 1
        for (oct in first..last) for (d in 0 until 7) {
            val c = niCents + oct * 1200 + mode.genus.positions[d] * Pitch.CENTS_PER_MORIO
            if (c < low || c > high) continue
            val base = d == mode.base
            val dom = !base && mode.dominants.contains(d)
            val color = when {
                base -> chromatic[0]
                dom -> chromatic[7]
                else -> chromatic[2]
            }
            val width = if (base) 1.3f else if (dom) 1.0f else 0.7f
            val name = Byzantine.NAMES[d]
            out += GridLine(c, name, name, color, width, if (base) labels.strong else labels.normal, base)
        }
        out.sortBy { it.cents }
        return out
    }
}

// ---------------------------------------------------------------------------------------------
// European scales
// ---------------------------------------------------------------------------------------------

enum class ScaleKind { MAJOR, MINOR, CHROMATIC }

/** A European scale: tonic letter (0 = C .. 6 = B), its accidental (-1, 0, +1) and kind. */
class WesternScale(val letter: Int, val accidental: Int, val kind: ScaleKind) {
    val title: String = if (kind == ScaleKind.CHROMATIC) "Chromatic" else
        Names.LETTERS[letter] + Names.accidental(accidental) + if (kind == ScaleKind.MAJOR) " Major" else " Minor"

    val tonicPc: Int = Math.floorMod(Names.NATURAL_PC[letter] + accidental, 12)

    /** Steps above the tonic of the seven degrees. */
    val steps: IntArray = when (kind) {
        ScaleKind.MINOR -> intArrayOf(0, 2, 3, 5, 7, 8, 10)
        else -> intArrayOf(0, 2, 4, 5, 7, 9, 11)
    }

    /** Scale degree (0..6) of pitch class [pc], or -1 if it isn't in the scale. */
    fun degreeOf(pc: Int): Int = steps.indexOf(Math.floorMod(pc - tonicPc, 12))

    /** Spelling of degree [d]: letter index and accidental. */
    fun spell(d: Int): Pair<Int, Int> {
        val letter = (this.letter + d) % 7
        val pc = (tonicPc + steps[d]) % 12
        var acc = Math.floorMod(pc - Names.NATURAL_PC[letter], 12)
        if (acc > 6) acc -= 12
        return letter to acc
    }

    /** Flats or sharps for notes outside the scale. */
    val prefersFlats: Boolean = accidental < 0 || (accidental == 0 && kind != ScaleKind.CHROMATIC &&
        (if (kind == ScaleKind.MAJOR) letter == 3 else letter in intArrayOf(0, 1, 3, 4)))

    companion object {
        private fun s(l: Int, a: Int, k: ScaleKind) = WesternScale(l, a, k)
        private val M = ScaleKind.MAJOR
        private val m = ScaleKind.MINOR

        /** The European scales in the order of the 1.6.0 list (C Major first, Chromatic last). */
        val ALL: List<WesternScale> = listOf(
            s(0, 0, M), s(0, 0, m), s(0, -1, M), s(0, 1, M), s(0, 1, m),
            s(1, 0, M), s(1, 0, m), s(1, -1, M), s(1, 1, m),
            s(2, 0, M), s(2, 0, m), s(2, -1, M), s(2, -1, m),
            s(3, 0, M), s(3, 0, m), s(3, 1, M), s(3, 1, m),
            s(4, 0, M), s(4, 0, m), s(4, -1, M), s(4, 1, m),
            s(5, 0, M), s(5, 0, m), s(5, -1, M), s(5, -1, m), s(5, 1, m),
            s(6, 0, M), s(6, 0, m), s(6, -1, M), s(6, -1, m),
            WesternScale(0, 0, ScaleKind.CHROMATIC),
        )

        fun byTitle(title: String): WesternScale? = ALL.firstOrNull { it.title == title }
    }
}

object Names {
    val LETTERS = arrayOf("C", "D", "E", "F", "G", "A", "B")
    val SOLFEGE = arrayOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")
    val NATURAL_PC = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    fun accidental(a: Int) = when (a) {
        -2 -> "♭♭"; -1 -> "♭"; 1 -> "♯"; 2 -> "♯♯"; else -> ""
    }

    /** Note name in the chosen style: 0 = C D E, 1 = Do Re Mi, 2 = ΝΗ ΠΑ ΒΟΥ. */
    fun letter(style: Int, letter: Int): String = when (style) {
        1 -> SOLFEGE[letter]
        2 -> Byzantine.NAMES[letter]
        else -> LETTERS[letter]
    }

    /** Letter and accidental for a chromatic pitch class, spelled with sharps or flats. */
    fun chromatic(pc: Int, flats: Boolean): Pair<Int, Int> {
        val natural = NATURAL_PC.indexOf(pc)
        if (natural >= 0) return natural to 0
        return if (flats) NATURAL_PC.indexOf(pc + 1) to -1 else NATURAL_PC.indexOf(pc - 1) to 1
    }
}

/**
 * The European grid. Lines are placed at sounding pitch; names are written pitch for the chosen
 * transposing instrument ([transpose] semitones written above sounding).
 */
class WesternGrid(
    private val scale: WesternScale,
    private val a4Hz: Int,
    private val transpose: Int,
    private val style: Int,
    private val octaveA3: Boolean,
    private val semitoneLines: Boolean,
    private val semitoneLabels: Boolean,
    private val scaleColors: IntArray,
    private val chromaticColors: IntArray,
    private val labels: LabelColors = LabelColors.DARK,
) : Grid {
    private val ref = Pitch.calibration(a4Hz)

    private fun nameOf(letter: Int, acc: Int, writtenMidi: Int): String {
        // Octave follows the letter (B♯3 and C♭4 keep their letter's octave).
        var oct = Math.floorDiv(writtenMidi - acc, 12) - 1
        if (octaveA3) oct -= 1
        return Names.letter(style, letter) + Names.accidental(acc) + oct
    }

    override fun lines(low: Double, high: Double): List<GridLine> {
        val out = ArrayList<GridLine>()
        val shift = -transpose * 100.0 + ref
        val first = kotlin.math.ceil((low - shift) / 100.0).toInt() + 69
        val last = floor((high - shift) / 100.0).toInt() + 69
        for (written in first..last) {
            val cents = (written - 69) * 100.0 + shift
            val pc = Math.floorMod(written, 12)
            if (scale.kind == ScaleKind.CHROMATIC) {
                val (l, a) = Names.chromatic(pc, false)
                val n = nameOf(l, a, written)
                out += GridLine(cents, n, n, chromaticColors[pc], 0.7f,
                    if (pc == 0) labels.strong else labels.normal, pc == 0)
                continue
            }
            val d = scale.degreeOf(pc)
            if (d >= 0) {
                val (l, a) = scale.spell(d)
                val n = nameOf(l, a, written)
                out += GridLine(cents, n, n, scaleColors[d], 0.7f,
                    if (d == 0) labels.strong else labels.normal, d == 0)
            } else if (semitoneLines || semitoneLabels) {
                val (l, a) = Names.chromatic(pc, scale.prefersFlats)
                val n = nameOf(l, a, written)
                out += GridLine(cents, if (semitoneLabels) n else null, n,
                    if (semitoneLines) scaleColors[7] else 0, 0.7f, labels.faint, false, inScale = false)
            }
        }
        return out
    }
}

/** Every scale name in the picker, in display order, and which ones are visible. */
object Catalog {
    const val C_MAJOR = "C Major"
    const val EU_ALL = "all"
    const val EU_C_MAJOR = "cmajor"
    const val EU_NONE = "none"

    /** C Major, then the Byzantine modes, then the other European scales. */
    val ORDERED: List<String> =
        listOf(C_MAJOR) + Byzantine.MODES.map { it.title } + WesternScale.ALL.map { it.title }.filter { it != C_MAJOR }

    fun isByzantine(name: String) = Byzantine.mode(name) != null

    fun visible(name: String, european: String): Boolean = when (european) {
        EU_NONE -> isByzantine(name)
        EU_C_MAJOR -> isByzantine(name) || name == C_MAJOR
        else -> true
    }

    fun visibleList(european: String) = ORDERED.filter { visible(it, european) }

    /** Instrument transposition in semitones written above sounding: C, B♭, E♭, F. */
    val INSTRUMENT_SHIFT = intArrayOf(0, 2, 9, 7)
}
