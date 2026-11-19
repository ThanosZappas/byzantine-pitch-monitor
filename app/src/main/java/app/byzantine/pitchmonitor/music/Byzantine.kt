package app.byzantine.pitchmonitor.music

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Byzantine music theory in the Chrysanthine 72-moria system
 * (one octave = 72 moria, one moria = 16⅔ cents).
 */
object Moria {
    const val PER_OCTAVE = 72
    const val CENTS = 1200.0 / PER_OCTAVE

    fun toCents(moria: Double) = moria * CENTS
    fun fromCents(cents: Double) = cents / CENTS
}

/** The seven degrees of the Byzantine scale. */
enum class Degree(val greek: String, val latin: String) {
    NI("Νη", "Ni"),
    PA("Πα", "Pa"),
    VOU("Βου", "Vou"),
    GA("Γα", "Ga"),
    DI("Δι", "Di"),
    KE("Κε", "Ke"),
    ZO("Ζω", "Zo");

    fun label(latin: Boolean) = if (latin) this.latin else greek
}

/**
 * A genus (γένος): its intervals in moria, starting from [start] which sits [startMoria]
 * moria above Ni. The tables match the original app's tuning.
 */
enum class Genus(
    val greek: String,
    val english: String,
    val start: Degree,
    val startMoria: Int,
    val intervals: IntArray,
) {
    DIATONIC("Διατονικό", "Diatonic", Degree.NI, 0, intArrayOf(12, 10, 8, 12, 12, 10, 8)),
    SOFT_CHROMATIC("Μαλακό χρωματικό", "Soft chromatic", Degree.NI, 0, intArrayOf(8, 14, 8, 12, 8, 14, 8)),
    HARD_CHROMATIC("Σκληρό χρωματικό", "Hard chromatic", Degree.PA, 12, intArrayOf(6, 20, 4, 12, 6, 20, 4)),
    ENHARMONIC("Εναρμόνιο", "Enharmonic", Degree.GA, 30, intArrayOf(12, 12, 6, 12, 12, 12, 6));

    /** Position of each degree (indexed by [Degree.ordinal]) in moria above Ni, in 0 until 72. */
    val positions: IntArray = IntArray(7).also { pos ->
        var m = startMoria
        for (i in 0 until 7) {
            val degree = (start.ordinal + i) % 7
            pos[degree] = Math.floorMod(m, Moria.PER_OCTAVE)
            m += intervals[i]
        }
    }

    init {
        require(intervals.sum() == Moria.PER_OCTAVE) { "$name does not span an octave" }
    }

    /** Interval in moria from [degree] to the next degree up. */
    fun stepAbove(degree: Degree): Int {
        val next = (degree.ordinal + 1) % 7
        return Math.floorMod(positions[next] - positions[degree.ordinal], Moria.PER_OCTAVE)
    }
}

/** The eight modes (ήχοι), with their default genus, base (βάση) and dominant degrees. */
enum class Echos(
    val greek: String,
    val short: String,
    val english: String,
    val genus: Genus,
    val base: Degree,
    val dominants: List<Degree>,
) {
    FIRST("Ήχος Α'", "Α'", "First mode", Genus.DIATONIC, Degree.PA, listOf(Degree.PA, Degree.DI, Degree.GA)),
    SECOND("Ήχος Β'", "Β'", "Second mode", Genus.SOFT_CHROMATIC, Degree.DI, listOf(Degree.DI, Degree.NI, Degree.KE)),
    THIRD("Ήχος Γ'", "Γ'", "Third mode", Genus.ENHARMONIC, Degree.GA, listOf(Degree.GA, Degree.DI, Degree.NI)),
    FOURTH("Ήχος Δ'", "Δ'", "Fourth mode", Genus.DIATONIC, Degree.DI, listOf(Degree.DI, Degree.PA, Degree.NI)),
    PL_FIRST("Ήχος Πλ.Α'", "Πλ.Α'", "Plagal first", Genus.DIATONIC, Degree.PA, listOf(Degree.PA, Degree.DI, Degree.KE)),
    PL_SECOND("Ήχος Πλ.Β'", "Πλ.Β'", "Plagal second", Genus.HARD_CHROMATIC, Degree.PA, listOf(Degree.PA, Degree.DI, Degree.KE)),
    VARYS("Ήχος Βαρύς", "Βαρύς", "Grave mode", Genus.ENHARMONIC, Degree.GA, listOf(Degree.GA, Degree.DI, Degree.ZO)),
    PL_FOURTH("Ήχος Πλ.Δ'", "Πλ.Δ'", "Plagal fourth", Genus.DIATONIC, Degree.NI, listOf(Degree.NI, Degree.GA, Degree.DI));
}

enum class Role { BASE, DOMINANT, NORMAL }

/** One line of the pitch grid, at an absolute pitch given in cents relative to 440 Hz. */
data class GridNote(
    val cents: Double,
    val label: String,
    val role: Role,
    /** Degree for Byzantine scales, pitch class (0 = C) for the western scale. */
    val index: Int,
    val octave: Int,
)

/** A tuning grid that can list the notes in a pitch range and find the nearest note. */
interface ScaleGrid {
    /** Notes with [GridNote.cents] in [low, high], ascending. */
    fun notesIn(low: Double, high: Double): List<GridNote>
    fun nearest(cents: Double): GridNote
}

/**
 * The Byzantine scale of a genus built on Ni at [niCents] (cents relative to 440 Hz).
 * Octave 0 is the octave starting at that Ni; higher octaves get ' marks, lower ones , marks.
 */
class ByzantineGrid(
    val genus: Genus,
    val niCents: Double,
    val base: Degree?,
    val dominants: List<Degree>,
    val latinNames: Boolean,
) : ScaleGrid {

    private fun noteAt(octave: Int, degree: Int): GridNote {
        val d = Degree.entries[degree]
        val cents = niCents + Moria.toCents((octave * Moria.PER_OCTAVE + genus.positions[degree]).toDouble())
        val role = when {
            d == base -> Role.BASE
            d in dominants -> Role.DOMINANT
            else -> Role.NORMAL
        }
        return GridNote(cents, labelFor(d, octave, latinNames), role, degree, octave)
    }

    override fun notesIn(low: Double, high: Double): List<GridNote> {
        val out = ArrayList<GridNote>()
        val firstOctave = floor((low - niCents) / 1200.0).toInt() - 1
        val lastOctave = floor((high - niCents) / 1200.0).toInt() + 1
        for (o in firstOctave..lastOctave) for (d in 0 until 7) {
            val n = noteAt(o, d)
            if (n.cents in low..high) out += n
        }
        out.sortBy { it.cents }
        return out
    }

    override fun nearest(cents: Double): GridNote {
        val octave = floor((cents - niCents) / 1200.0).toInt()
        var best: GridNote? = null
        for (o in octave - 1..octave + 1) for (d in 0 until 7) {
            val n = noteAt(o, d)
            if (best == null || kotlin.math.abs(n.cents - cents) < kotlin.math.abs(best.cents - cents)) best = n
        }
        return best!!
    }

    /** Pitch of a degree in [octave] (0 = the octave of the reference Ni), cents re 440 Hz. */
    fun centsOf(degree: Degree, octave: Int = 0): Double = noteAt(octave, degree.ordinal).cents

    companion object {
        fun labelFor(d: Degree, octave: Int, latin: Boolean): String {
            val name = d.label(latin)
            return when {
                octave > 0 -> name + "'".repeat(octave)
                octave < 0 -> name + ",".repeat(-octave)
                else -> name
            }
        }
    }
}

/**
 * Equal-tempered 12-note grid (Western notation), tonic marked as base.
 * [refCents] is the tuning of A4 in cents relative to 440 Hz (0 for A4 = 440 Hz).
 */
class WesternGrid(private val refCents: Double = 0.0, private val tonicPitchClass: Int = 0) : ScaleGrid {
    private fun noteFor(midi: Int): GridNote {
        val pc = Math.floorMod(midi, 12)
        return GridNote(
            cents = (midi - 69) * 100.0 + refCents,
            label = Pitch.noteName(midi),
            role = if (pc == tonicPitchClass) Role.BASE else Role.NORMAL,
            index = pc,
            octave = Math.floorDiv(midi, 12) - 1,
        )
    }

    override fun notesIn(low: Double, high: Double): List<GridNote> {
        val first = kotlin.math.ceil((low - refCents) / 100.0).toInt() + 69
        val last = floor((high - refCents) / 100.0).toInt() + 69
        return (first..last).map(::noteFor)
    }

    override fun nearest(cents: Double): GridNote = noteFor(((cents - refCents) / 100.0).roundToInt() + 69)
}

/**
 * Conversions between Hz and absolute pitch. All pitches in the app are cents relative to
 * 440 Hz; [refCents] arguments shift note names for a different A4 calibration.
 */
object Pitch {
    const val A440 = 440.0

    private val NAMES = arrayOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")

    fun hzToCents(hz: Double): Double = 1200.0 * ln(hz / A440) / ln(2.0)
    fun centsToHz(cents: Double): Double = A440 * 2.0.pow(cents / 1200.0)

    fun noteName(midi: Int): String = NAMES[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1)

    /** "D4 +3¢" style description of an absolute pitch. */
    fun describe(cents: Double, refCents: Double = 0.0): String {
        val rel = cents - refCents
        val midi = (rel / 100.0).roundToInt() + 69
        val off = (rel - (midi - 69) * 100.0).roundToInt()
        val sign = if (off >= 0) "+" else "−"
        return "${noteName(midi)} $sign${kotlin.math.abs(off)}¢"
    }

    fun nearestNoteName(cents: Double, refCents: Double = 0.0): String =
        noteName(((cents - refCents) / 100.0).roundToInt() + 69)
}
