package app.pitchmonitor.byz

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Colour maths without Android classes, so it can be unit-tested. */
object Colors {
    fun a(c: Int) = (c ushr 24) and 0xFF
    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF
    fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    fun alpha(c: Int, a: Float) = ((a * 255).toInt().coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)

    /** [a] moved towards [b] by [t] (0 = a, 1 = b). */
    fun blend(a: Int, b: Int, t: Float): Int = rgb(
        (r(a) + (r(b) - r(a)) * t).toInt(), (g(a) + (g(b) - g(a)) * t).toInt(), (b(a) + (b(b) - b(a)) * t).toInt(),
    )

    private fun channel(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(c: Int) = 0.2126 * channel(r(c)) + 0.7152 * channel(g(c)) + 0.0722 * channel(b(c))

    /** WCAG contrast ratio (1..21). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** [fg], darkened or lightened as little as needed to reach [ratio] against every colour in [bgs]. */
    fun ensure(fg: Int, ratio: Double, vararg bgs: Int): Int {
        if (bgs.all { contrast(fg, it) >= ratio }) return fg
        val towardWhite = bgs.sumOf { luminance(it) } / bgs.size < 0.35
        val target = if (towardWhite) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        var t = 0.05f
        while (t < 1f) {
            val c = blend(fg, target, t)
            if (bgs.all { contrast(c, it) >= ratio }) return c
            t += 0.05f
        }
        return target
    }

    /** Black or white, whichever reads better on [bg]. */
    fun on(bg: Int): Int = if (contrast(0xFFFFFFFF.toInt(), bg) >= contrast(0xFF000000.toInt(), bg)) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

    const val AA_TEXT = 4.5
    const val AA_UI = 3.0
}

/**
 * Colour roles of one theme + app colour, named after Material 3 roles. Every text role is
 * corrected to at least 4.5:1 against the surfaces it sits on, and every control/graphic role to
 * at least 3:1 (WCAG 2.1 AA).
 */
class Palette private constructor(base: Base, val accent: Int) {
    class Base(
        val dark: Boolean,
        val surface: Int, val surfaceContainer: Int, val surfaceContainerHigh: Int,
        val onSurface: Int, val onSurfaceVariant: Int,
        val graphBg: Int, val bar: Int, val chipFill: Int, val infoFill: Int,
        val label: Int, val labelStrong: Int,
        val defaultPitch: Int, val defaultBeats: Int, val defaultMetronome: Int,
        val defaultScale: IntArray, val defaultChromatic: IntArray,
    )

    val dark = base.dark

    // Surfaces
    val surface = base.surface
    val surfaceContainer = base.surfaceContainer
    val surfaceContainerHigh = base.surfaceContainerHigh
    val onSurface = Colors.ensure(base.onSurface, 7.0, surface, surfaceContainer, surfaceContainerHigh)
    val onSurfaceVariant = Colors.ensure(base.onSurfaceVariant, Colors.AA_TEXT, surface, surfaceContainer, surfaceContainerHigh)
    val outline = Colors.ensure(Colors.blend(onSurface, surface, 0.55f), Colors.AA_UI, surface, surfaceContainer)
    val outlineVariant = Colors.blend(onSurface, surfaceContainer, if (dark) 0.82f else 0.86f)

    // App colour
    val primary = Colors.ensure(if (dark) accent else Colors.blend(accent, 0xFF000000.toInt(), 0.25f),
        Colors.AA_UI, surface, surfaceContainer, base.bar)
    val primaryText = Colors.ensure(primary, Colors.AA_TEXT, surface, surfaceContainer, surfaceContainerHigh)
    val onPrimary = Colors.on(primary)
    val primaryContainer = Colors.blend(accent, surfaceContainer, if (dark) 0.72f else 0.80f)
    val onPrimaryContainer = Colors.ensure(Colors.blend(accent, base.onSurface, 0.45f), Colors.AA_TEXT, primaryContainer)
    val secondaryContainer = Colors.blend(accent, surface, if (dark) 0.78f else 0.84f)
    val onSecondaryContainer = Colors.ensure(Colors.blend(accent, base.onSurface, 0.55f), Colors.AA_TEXT, secondaryContainer)

    // Main screen
    val graphBg = base.graphBg
    val bar = base.bar
    val chipFill = base.chipFill
    val chipStroke = Colors.blend(onSurface, bar, if (dark) 0.80f else 0.80f)
    val chipPressed = Colors.blend(onSurface, chipFill, if (dark) 0.85f else 0.90f)
    val infoFill = base.infoFill
    val text = Colors.ensure(base.onSurface, Colors.AA_TEXT, chipFill, bar)
    val infoText = Colors.ensure(base.onSurfaceVariant, Colors.AA_TEXT, infoFill)
    val accentText = Colors.ensure(primary, Colors.AA_TEXT, bar, chipFill)
    val barLine = Colors.ensure(Colors.blend(accent, bar, 0.5f), Colors.AA_UI - 1.5, bar)
    val accentFill = primaryContainer
    val accentFillPressed = Colors.blend(accent, surfaceContainer, if (dark) 0.55f else 0.65f)
    val accentOnFill = onPrimaryContainer
    val axis = Colors.ensure(base.onSurfaceVariant, Colors.AA_UI, graphBg)
    val labelStrong = Colors.ensure(base.labelStrong, Colors.AA_TEXT, graphBg)
    val label = Colors.ensure(base.label, Colors.AA_TEXT, graphBg)
    val labelFaint = Colors.ensure(Colors.blend(base.label, graphBg, 0.25f), Colors.AA_TEXT, graphBg)
    val moria = Colors.ensure(base.label, Colors.AA_TEXT, graphBg)
    val tuner = Colors.ensure(base.onSurface, Colors.AA_TEXT, graphBg)
    val tick = Colors.ensure(base.onSurfaceVariant, Colors.AA_UI, graphBg)
    val hold = Colors.ensure(base.onSurfaceVariant, Colors.AA_TEXT, graphBg)

    // Default graph colours of this theme (lines are decorative; the pitch trace must stand out).
    val defaultPitch = Colors.ensure(base.defaultPitch, Colors.AA_UI, graphBg)
    val defaultBeats = base.defaultBeats
    val defaultMetronome = base.defaultMetronome
    val defaultScale = base.defaultScale
    val defaultChromatic = base.defaultChromatic

    // Transport icons keep their meaning (red record, green play) at 3:1 on the graph.
    val record = Colors.ensure(0xFFE53935.toInt(), Colors.AA_UI, graphBg)
    val play = Colors.ensure(0xFF43A047.toInt(), Colors.AA_UI, graphBg)
    val stop = Colors.ensure(base.onSurfaceVariant, Colors.AA_UI, graphBg)

    /** A rounded, pressable shape with a ripple in this palette. */
    fun chip(stroke: Int = chipStroke, fill: Int = chipFill, pressed: Int = chipPressed, radiusDp: Float = 10f, dp: Float): Drawable {
        val shape = GradientDrawable().apply {
            cornerRadius = radiusDp * dp
            setColor(fill)
            setStroke(dp.toInt().coerceAtLeast(1), stroke)
        }
        return RippleDrawable(ColorStateList.valueOf(pressed), shape, null)
    }

    /** A rounded rectangle fill. */
    fun rounded(fill: Int, radiusDp: Float, dp: Float, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radiusDp * dp
        setColor(fill)
        if (stroke != 0) setStroke(dp.toInt().coerceAtLeast(1), stroke)
    }

    /** Shape for clipping a ripple (only its outline is used, never its colour). */
    fun mask(radiusDp: Float, dp: Float): GradientDrawable = rounded(0xFF000000.toInt(), radiusDp, dp)

    /** Ripple for borderless touch targets. */
    fun ripple(mask: Drawable? = null): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(Colors.alpha(onSurface, 0.14f)), null, mask)

    companion object {
        const val SYSTEM = "system"
        const val DARK = "dark"
        const val BLACK = "black"
        const val LIGHT = "light"
        const val PARCHMENT = "parchment"
        val THEMES = listOf(SYSTEM, DARK, BLACK, LIGHT, PARCHMENT)

        fun blend(a: Int, b: Int, t: Float) = Colors.blend(a, b, t)

        private fun c(v: Long) = v.toInt()

        /** The palette for [theme] and [accent]; [SYSTEM] follows [night]. */
        fun of(theme: String, accent: Int, night: Boolean): Palette {
            val t = if (theme == SYSTEM) (if (night) DARK else LIGHT) else theme
            return Palette(BASES[t] ?: BASES.getValue(DARK), accent)
        }

        fun of(context: Context, theme: String, accent: Int): Palette {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return of(theme, accent, night)
        }

        private fun chromatic(c: Int, natural: Int, sharp: Int) =
            IntArray(12) { pc -> if (pc == 0) c else if (pc in intArrayOf(2, 4, 5, 7, 9, 11)) natural else sharp }

        /** Tonic, 2nd..7th degree, semitone. */
        val DARK_SCALE = intArrayOf(c(0xFF808080), c(0xFF404040), c(0xFF404040), c(0xFF404040), c(0xFF404040), c(0xFF404040), c(0xFF404040), c(0xFF202020))
        val DARK_CHROMATIC = chromatic(c(0xFF808080), c(0xFF404040), c(0xFF282828))
        val LIGHT_SCALE = intArrayOf(c(0xFF7A7A7A), c(0xFFC4C4C4), c(0xFFC4C4C4), c(0xFFC4C4C4), c(0xFFC4C4C4), c(0xFFC4C4C4), c(0xFFC4C4C4), c(0xFFE6E6E6))
        val LIGHT_CHROMATIC = chromatic(c(0xFF7A7A7A), c(0xFFC4C4C4), c(0xFFE0E0E0))
        val PARCHMENT_SCALE = intArrayOf(c(0xFF8C7A55), c(0xFFD2C3A0), c(0xFFD2C3A0), c(0xFFD2C3A0), c(0xFFD2C3A0), c(0xFFD2C3A0), c(0xFFD2C3A0), c(0xFFE8DDC2))
        val PARCHMENT_CHROMATIC = chromatic(c(0xFF8C7A55), c(0xFFD2C3A0), c(0xFFE4D8BA))

        val BASES = mapOf(
            DARK to Base(
                dark = true, surface = c(0xFF14110E), surfaceContainer = c(0xFF221D18), surfaceContainerHigh = c(0xFF2C2620),
                onSurface = c(0xFFEDE6DD), onSurfaceVariant = c(0xFFB9AFA3),
                graphBg = c(0xFF000000), bar = c(0xFF1C1713), chipFill = c(0xFF241D17), infoFill = c(0xFF15110D),
                label = c(0xFF8A8A8A), labelStrong = c(0xFFD0D0D0),
                defaultPitch = c(0xFFFFFF00), defaultBeats = c(0xFF444444), defaultMetronome = c(0xFF000080),
                defaultScale = DARK_SCALE, defaultChromatic = DARK_CHROMATIC,
            ),
            BLACK to Base(
                dark = true, surface = c(0xFF000000), surfaceContainer = c(0xFF121212), surfaceContainerHigh = c(0xFF1C1C1C),
                onSurface = c(0xFFEEEEEE), onSurfaceVariant = c(0xFFB0B0B0),
                graphBg = c(0xFF000000), bar = c(0xFF000000), chipFill = c(0xFF121212), infoFill = c(0xFF0A0A0A),
                label = c(0xFF8A8A8A), labelStrong = c(0xFFE0E0E0),
                defaultPitch = c(0xFFFFFF00), defaultBeats = c(0xFF444444), defaultMetronome = c(0xFF000080),
                defaultScale = DARK_SCALE, defaultChromatic = DARK_CHROMATIC,
            ),
            LIGHT to Base(
                dark = false, surface = c(0xFFF7F5F1), surfaceContainer = c(0xFFFFFFFF), surfaceContainerHigh = c(0xFFFFFFFF),
                onSurface = c(0xFF1D1B18), onSurfaceVariant = c(0xFF57524B),
                graphBg = c(0xFFFFFFFF), bar = c(0xFFF3F1EC), chipFill = c(0xFFFFFFFF), infoFill = c(0xFFE9E5DC),
                label = c(0xFF5E5E5E), labelStrong = c(0xFF111111),
                defaultPitch = c(0xFFD84315), defaultBeats = c(0xFFBDBDBD), defaultMetronome = c(0xFF90CAF9),
                defaultScale = LIGHT_SCALE, defaultChromatic = LIGHT_CHROMATIC,
            ),
            PARCHMENT to Base(
                dark = false, surface = c(0xFFF6EEDC), surfaceContainer = c(0xFFFCF8EE), surfaceContainerHigh = c(0xFFFFFBF3),
                onSurface = c(0xFF2B1F0E), onSurfaceVariant = c(0xFF5E4E36),
                graphBg = c(0xFFFBF6EA), bar = c(0xFFEFE4CC), chipFill = c(0xFFFBF6EA), infoFill = c(0xFFE7DABD),
                label = c(0xFF6E5E44), labelStrong = c(0xFF2B1F0E),
                defaultPitch = c(0xFFB71C1C), defaultBeats = c(0xFFCDBE9C), defaultMetronome = c(0xFFE8C77A),
                defaultScale = PARCHMENT_SCALE, defaultChromatic = PARCHMENT_CHROMATIC,
            ),
        )
    }
}

/** App language (English by default, or Greek), independent of the phone's language. */
object Lang {
    const val EN = "en"
    const val EL = "el"

    fun of(context: Context): String =
        context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).getString("language", EN) ?: EN

    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(of(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

/** Base of every screen: applies the language, the light/dark look and the system bar colours. */
abstract class ThemedActivity : AppCompatActivity() {
    protected lateinit var prefs: Prefs
    lateinit var palette: Palette
        protected set
    private var styleKey = ""

    /** Colour of the status and navigation bars. */
    protected open val systemBarColor: Int get() = palette.surface

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        palette = prefs.palette()
        setTheme(if (palette.dark) R.style.Theme_Byz else R.style.Theme_Byz_Light)
        styleKey = currentStyleKey()
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(palette.surface)
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
        }
    }

    override fun onResume() {
        super.onResume()
        // Language, theme or app colour changed elsewhere: rebuild this screen.
        if (currentStyleKey() != styleKey) recreate()
    }

    private fun currentStyleKey() = Lang.of(this) + "|" + prefs.theme + "|" + prefs.accent + "|" +
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)

    /** Builds a dialog in this palette (background, title, text and buttons). */
    fun dialog(): AlertDialog.Builder =
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setBackground(palette.rounded(palette.surfaceContainerHigh, 28f, resources.displayMetrics.density))

    /** Colours a shown dialog's buttons and text with the palette. */
    fun style(d: AlertDialog): AlertDialog {
        for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            d.getButton(which)?.setTextColor(palette.primaryText)
        }
        d.findViewById<android.widget.TextView>(android.R.id.message)?.setTextColor(palette.onSurfaceVariant)
        d.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(palette.onSurface)
        d.listView?.let { tintTree(it) }
        return d
    }

    /** Tints plain widgets (edit fields, progress bars, radio buttons) inside [v] with the palette. */
    fun tintTree(v: View) {
        val p = palette
        val checkable = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(p.primary, p.outline))
        when (v) {
            is android.widget.EditText -> {
                v.setTextColor(p.onSurface)
                v.setHintTextColor(p.onSurfaceVariant)
                v.backgroundTintList = ColorStateList.valueOf(p.primary)
                v.highlightColor = Colors.alpha(p.primary, 0.35f)
                if (Build.VERSION.SDK_INT >= 29) v.textCursorDrawable?.setTint(p.primary)
            }
            is android.widget.CompoundButton -> { v.buttonTintList = checkable; v.setTextColor(p.onSurface) }
            is android.widget.ProgressBar -> {
                v.progressTintList = ColorStateList.valueOf(p.primary)
                v.progressBackgroundTintList = ColorStateList.valueOf(p.outlineVariant)
            }
            is android.widget.TextView -> if (v.currentTextColor == 0xFF000000.toInt() || v.currentTextColor == 0xFFFFFFFF.toInt()) v.setTextColor(p.onSurface)
        }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) tintTree(v.getChildAt(i))
    }
}
