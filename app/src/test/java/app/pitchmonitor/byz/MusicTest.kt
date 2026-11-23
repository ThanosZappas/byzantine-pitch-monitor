package app.pitchmonitor.byz

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MusicTest {
    @Test fun genusPositions() {
        assertArrayEquals(intArrayOf(0, 12, 22, 30, 42, 54, 64), Genus.DIATONIC_NI.positions)
        assertArrayEquals(intArrayOf(0, 8, 22, 30, 42, 50, 64), Genus.SOFT_CHROMATIC_NI.positions)
        assertArrayEquals(intArrayOf(8, 12, 18, 38, 42, 54, 60), Genus.HARD_CHROMATIC_PA.positions)
        assertArrayEquals(intArrayOf(0, 12, 24, 30, 42, 54, 60), Genus.ENHARMONIC_GA.positions)
        // Πλ.Δ' εκ του Γα: Ke 2 moria and Zo 4 moria lower than plain diatonic.
        assertArrayEquals(intArrayOf(0, 12, 22, 30, 42, 52, 60), Genus.DIATONIC_GA.positions)
    }

    @Test fun plagalFourthFromGa() {
        val m = Byzantine.mode("Ήχος Πλ.Δ' εκ του Γα")!!
        assertEquals(Byzantine.GA, m.base)
        assertArrayEquals(intArrayOf(Byzantine.GA, Byzantine.DI, Byzantine.ZO), m.dominants)
    }

    @Test fun genres() {
        val d4 = Byzantine.mode("Ήχος Δ'")!!
        assertEquals(Byzantine.PA, d4.baseFor(Genre.STICHERARIC))
        assertEquals(Byzantine.VOU, d4.baseFor(Genre.HEIRMOLOGIC))
        val g3 = Byzantine.mode("Ήχος Γ'")!!
        assertArrayEquals(intArrayOf(Byzantine.GA, Byzantine.PA, Byzantine.KE), g3.dominants)
        assertFalse(g3.genresDiffer)
    }

    @Test fun onlyBaseStandsOutByDefault() {
        val m = Byzantine.mode("Ήχος Α'")!!
        val g = ByzantineGrid(m, 0.0, Palette.DARK_CHROMATIC)
        val colors = g.lines(-1.0, 1199.0).filter { !it.strong }.map { it.color }.toSet()
        assertEquals(1, colors.size)
        val hi = ByzantineGrid(m, 0.0, Palette.DARK_CHROMATIC, highlightDominants = true)
        assertEquals(2, hi.lines(-1.0, 1199.0).filter { !it.strong }.map { it.color }.toSet().size)
    }

    @Test fun scaleOrder() {
        val o = Catalog.ORDERED
        assertEquals("C Major", o[0])
        assertEquals("Ήχος Α'", o[1])
        assertEquals("Ήχος Πλ.Δ' εκ του Γα", o[9])
        assertEquals("C Minor", o[10])
        assertEquals("Chromatic", o.last())
        assertEquals(40, o.size)
    }

    @Test fun europeanFilter() {
        assertEquals(40, Catalog.visibleList(Catalog.EU_ALL).size)
        assertEquals(listOf("C Major") + Byzantine.MODES.map { it.title }, Catalog.visibleList(Catalog.EU_C_MAJOR))
        assertEquals(Byzantine.MODES.map { it.title }, Catalog.visibleList(Catalog.EU_NONE))
    }

    @Test fun voiceShift() {
        // Mode A' (base Pa, 12 moria above Ni). Singing D4 exactly → no shift.
        val d4 = -700.0
        assertEquals(0, Byzantine.voiceShift(d4, 12, 440))
        // A semitone lower → 6 moria down; an octave lower is the same shift.
        assertEquals(-6, Byzantine.voiceShift(d4 - 100, 12, 440))
        assertEquals(-6, Byzantine.voiceShift(d4 - 1300, 12, 440))
        // Shifts stay within half an octave.
        for (c in -2400..2400 step 37) {
            val s = Byzantine.voiceShift(c.toDouble(), 30, 440)
            assertTrue(s in -36..35)
        }
    }

    @Test fun westernSpelling() {
        val f = WesternScale.byTitle("F Major")!!
        assertEquals(6 to -1, f.spell(3)) // B♭
        val cFlat = WesternScale.byTitle("C♭ Major")!!
        assertEquals(0 to -1, cFlat.spell(0))
        assertEquals(3 to -1, cFlat.spell(3)) // F♭
        val aSharpMinor = WesternScale.byTitle("A♯ Minor")!!
        assertEquals(6 to 1, aSharpMinor.spell(1)) // B♯
        assertEquals(30, WesternScale.ALL.count { it.kind != ScaleKind.CHROMATIC })
    }

    @Test fun westernGridLabels() {
        val g = WesternGrid(WesternScale.byTitle("C Major")!!, 440, 0, 0, false, true, false,
            Palette.DARK_SCALE, Palette.DARK_CHROMATIC)
        val lines = g.lines(-900.0 - 1, 300.0 + 1) // C4 .. C5
        assertEquals(listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"), lines.mapNotNull { it.label })
        assertEquals(13, lines.size) // with the five semitone lines
        assertFalse(lines.first { it.label == null }.inScale)
        // 440 Hz = A3 numbering.
        val a3 = WesternGrid(WesternScale.byTitle("C Major")!!, 440, 0, 0, true, false, false,
            Palette.DARK_SCALE, Palette.DARK_CHROMATIC)
        assertEquals("A3", a3.lines(-1.0, 1.0).single().label)
    }

    @Test fun detectorFindsSine() {
        val rate = SAMPLE_RATE
        val hz = 220.0
        val x = FloatArray(WINDOW) { (0.5 * sin(2 * PI * hz * it / rate)).toFloat() }
        val found = PitchDetector(rate, WINDOW).detect(x)
        assertEquals(hz, found, 0.5)
    }
}
