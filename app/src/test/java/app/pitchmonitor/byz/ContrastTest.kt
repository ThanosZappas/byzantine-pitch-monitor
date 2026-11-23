package app.pitchmonitor.byz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every theme with every app colour meets WCAG 2.1 AA for text (4.5:1) and controls (3:1). */
class ContrastTest {
    private val themes = listOf(Palette.DARK, Palette.BLACK, Palette.LIGHT, Palette.PARCHMENT)

    private fun check(name: String, fg: Int, bg: Int, min: Double, failures: MutableList<String>) {
        val c = Colors.contrast(fg, bg)
        if (c < min) failures += String.format("%s: %.2f < %.1f (#%06X on #%06X)", name, c, min, fg and 0xFFFFFF, bg and 0xFFFFFF)
    }

    @Test fun contrastMeetsAA() {
        val failures = ArrayList<String>()
        for (t in themes) for (accent in Prefs.ACCENTS) {
            val p = Palette.of(t, accent, night = false)
            val tag = "$t/#%06X".format(accent and 0xFFFFFF)
            val text = Colors.AA_TEXT
            val ui = Colors.AA_UI
            // Settings, Appearance, Recordings
            for ((bgName, bg) in listOf("surface" to p.surface, "card" to p.surfaceContainer, "dialog" to p.surfaceContainerHigh)) {
                check("$tag onSurface/$bgName", p.onSurface, bg, text, failures)
                check("$tag onSurfaceVariant/$bgName", p.onSurfaceVariant, bg, text, failures)
            }
            check("$tag primaryText/surface", p.primaryText, p.surface, text, failures)
            check("$tag primaryText/card", p.primaryText, p.surfaceContainer, text, failures)
            check("$tag primary/card (switch, slider)", p.primary, p.surfaceContainer, ui, failures)
            check("$tag outline/card (switch off)", p.outline, p.surfaceContainer, ui, failures)
            check("$tag onPrimary/primary", p.onPrimary, p.primary, text, failures)
            check("$tag onPrimaryContainer", p.onPrimaryContainer, p.primaryContainer, text, failures)
            check("$tag onSecondaryContainer", p.onSecondaryContainer, p.secondaryContainer, text, failures)
            // Main screen
            check("$tag accentText/bar", p.accentText, p.bar, text, failures)
            check("$tag accentText/chip", p.accentText, p.chipFill, text, failures)
            check("$tag text/chip", p.text, p.chipFill, text, failures)
            check("$tag infoText/info", p.infoText, p.infoFill, text, failures)
            check("$tag accentOnFill/accentFill", p.accentOnFill, p.accentFill, text, failures)
            // Graph
            for ((n, c) in listOf("label" to p.label, "labelStrong" to p.labelStrong, "labelFaint" to p.labelFaint,
                "moria" to p.moria, "tuner" to p.tuner, "hold" to p.hold)) check("$tag $n/graph", c, p.graphBg, text, failures)
            for ((n, c) in listOf("axis" to p.axis, "tick" to p.tick, "pitch" to p.defaultPitch, "record" to p.record,
                "play" to p.play, "stop" to p.stop)) check("$tag $n/graph", c, p.graphBg, ui, failures)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun contrastFormula() {
        assertEquals(21.0, Colors.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, Colors.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
        // Known pair: #767676 on white is 4.54:1.
        assertEquals(4.54, Colors.contrast(0xFF767676.toInt(), 0xFFFFFFFF.toInt()), 0.02)
    }

    @Test fun ensureReachesTarget() {
        val bg = 0xFFFFFFFF.toInt()
        val fixed = Colors.ensure(0xFFFFE082.toInt(), Colors.AA_TEXT, bg)
        assertTrue(Colors.contrast(fixed, bg) >= Colors.AA_TEXT)
    }
}
