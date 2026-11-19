package app.byzantine.pitchmonitor

import app.byzantine.pitchmonitor.music.ByzantineGrid
import app.byzantine.pitchmonitor.music.Degree
import app.byzantine.pitchmonitor.music.Echos
import app.byzantine.pitchmonitor.music.Genus
import app.byzantine.pitchmonitor.music.Moria
import app.byzantine.pitchmonitor.music.Pitch
import app.byzantine.pitchmonitor.music.Role
import app.byzantine.pitchmonitor.music.WesternGrid
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ByzantineTest {
    @Test fun diatonicPositions() {
        // Ni 0, Pa 12, Vou 22, Ga 30, Di 42, Ke 54, Zo 64
        assertArrayEquals(intArrayOf(0, 12, 22, 30, 42, 54, 64), Genus.DIATONIC.positions)
    }

    @Test fun softChromaticPositions() {
        assertArrayEquals(intArrayOf(0, 8, 22, 30, 42, 50, 64), Genus.SOFT_CHROMATIC.positions)
    }

    @Test fun hardChromaticFromPa() {
        // Pa 12, Vou 18, Ga 38, Di 42, Ke 54, Zo 60, Ni' 80 → Ni 8
        assertArrayEquals(intArrayOf(8, 12, 18, 38, 42, 54, 60), Genus.HARD_CHROMATIC.positions)
    }

    @Test fun enharmonicFromGa() {
        assertArrayEquals(intArrayOf(0, 12, 24, 30, 42, 54, 60), Genus.ENHARMONIC.positions)
    }

    @Test fun everyGenusSpansAnOctave() {
        for (g in Genus.entries) assertEquals(72, Degree.entries.sumOf { g.stepAbove(it) })
    }

    @Test fun moriaConversion() {
        assertEquals(1200.0, Moria.toCents(72.0), 1e-9)
        assertEquals(6.0, Moria.fromCents(100.0), 1e-9)
    }

    @Test fun pitchConversionRoundTrip() {
        assertEquals(0.0, Pitch.hzToCents(440.0), 1e-9)
        assertEquals(1200.0, Pitch.hzToCents(880.0), 1e-9)
        assertEquals(261.63, Pitch.centsToHz(Pitch.hzToCents(261.63)), 1e-9)
        assertEquals("C4 ±0¢".replace("±", "+"), Pitch.describe(Pitch.hzToCents(261.6256)))
        assertEquals("A4 +10¢", Pitch.describe(10.0))
        assertEquals("A4 +0¢", Pitch.describe(10.0, refCents = 10.0))
    }

    @Test fun gridLabelsAndRoles() {
        val ni = Pitch.hzToCents(261.63)
        val e = Echos.FIRST
        val g = ByzantineGrid(e.genus, ni, e.base, e.dominants, latinNames = false)
        val notes = g.notesIn(ni - 1, ni + 1201)
        assertEquals(listOf("Νη", "Πα", "Βου", "Γα", "Δι", "Κε", "Ζω", "Νη'"), notes.map { it.label })
        assertEquals(Role.BASE, notes[1].role) // Pa
        assertEquals(Role.DOMINANT, notes[4].role) // Di
        assertEquals(Role.NORMAL, notes[2].role) // Vou
        val lower = g.notesIn(ni - 200, ni - 1)
        assertEquals("Ζω,", lower.last().label)
    }

    @Test fun nearestNote() {
        val ni = 0.0
        val g = ByzantineGrid(Genus.DIATONIC, ni, Degree.PA, emptyList(), latinNames = true)
        // Di = 42 moria = 700 cents; 690 cents is nearest to Di.
        val n = g.nearest(690.0)
        assertEquals("Di", n.label)
        assertEquals(700.0, n.cents, 1e-9)
        // Just below Ni' wraps to the next octave.
        assertEquals("Ni'", g.nearest(1190.0).label)
        assertEquals("Zo,", g.nearest(-130.0).label)
    }

    @Test fun westernGrid() {
        val g = WesternGrid()
        assertEquals("A4", g.nearest(30.0).label)
        assertEquals("B♭4", g.nearest(70.0).label)
        val notes = g.notesIn(-300.0, 0.0)
        assertEquals(listOf("F♯4", "G4", "A♭4", "A4"), notes.map { it.label })
        assertTrue(g.notesIn(-1200.0, 0.0).any { it.label == "C4" && it.role == Role.BASE })
    }
}
