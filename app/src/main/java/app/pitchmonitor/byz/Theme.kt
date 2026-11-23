package app.pitchmonitor.byz

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * All colours of one look of the app. Each theme has its own defaults for the graph lines
 * and the pitch trace, chosen so they keep good contrast on that background.
 */
class Palette(
    val dark: Boolean,
    val graphBg: Int,
    val bar: Int,
    val chipFill: Int,
    val chipStroke: Int,
    val chipPressed: Int,
    val infoFill: Int,
    val text: Int,
    val infoText: Int,
    val axis: Int,
    val labelStrong: Int,
    val label: Int,
    val labelFaint: Int,
    val moria: Int,
    val tuner: Int,
    val tick: Int,
    val hold: Int,
    val settingsBg: Int,
    val settingsHeader: Int,
    val settingsText: Int,
    val settingsMuted: Int,
    val defaultPitch: Int,
    val defaultBeats: Int,
    val defaultMetronome: Int,
    val defaultScale: IntArray,
    val defaultChromatic: IntArray,
    /** The chosen app colour, as picked. */
    val accent: Int,
) {
    /** The app colour adjusted for text and lines on this theme's bars. */
    val accentText: Int = if (dark) accent else blend(accent, Color.BLACK, 0.38f)

    /** Hairlines under/over the bars. */
    val barLine: Int = blend(accent, bar, if (dark) 0.55f else 0.45f)

    /** Fill of the highlighted chip (Base = voice). */
    val accentFill: Int = blend(accent, bar, if (dark) 0.72f else 0.78f)
    val accentFillPressed: Int = blend(accent, bar, if (dark) 0.55f else 0.62f)

    /** Text on the highlighted chip. */
    val accentOnFill: Int = if (dark) blend(accent, Color.WHITE, 0.45f) else blend(accent, Color.BLACK, 0.55f)

    fun chip(stroke: Int = chipStroke, fill: Int = chipFill, pressed: Int = chipPressed, radiusDp: Float = 10f, dp: Float): StateListDrawable {
        fun shape(c: Int) = GradientDrawable().apply {
            cornerRadius = radiusDp * dp
            setColor(c)
            setStroke(dp.toInt().coerceAtLeast(1), stroke)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressed))
            addState(intArrayOf(), shape(fill))
        }
    }

    companion object {
        const val SYSTEM = "system"
        const val DARK = "dark"
        const val BLACK = "black"
        const val LIGHT = "light"
        const val PARCHMENT = "parchment"
        val THEMES = listOf(SYSTEM, DARK, BLACK, LIGHT, PARCHMENT)

        fun blend(a: Int, b: Int, t: Float): Int {
            // t = how much of [b]
            val r = Color.red(a) + (Color.red(b) - Color.red(a)) * t
            val g = Color.green(a) + (Color.green(b) - Color.green(a)) * t
            val bl = Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t
            return Color.rgb(r.toInt(), g.toInt(), bl.toInt())
        }

        private fun c(v: Long) = v.toInt()

        /** The palette for [theme] ([SYSTEM] follows the phone's dark mode). */
        fun of(context: Context, theme: String, accent: Int): Palette {
            val t = if (theme == SYSTEM) {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
            } else theme
            return when (t) {
                BLACK -> Palette(
                    dark = true, graphBg = c(0xFF000000), bar = c(0xFF000000), chipFill = c(0xFF101010),
                    chipStroke = c(0xFF2E2E2E), chipPressed = c(0xFF262626), infoFill = c(0xFF0A0A0A),
                    text = c(0xFFEEEEEE), infoText = c(0xFFD0D0D0), axis = c(0xFFB0B0B0),
                    labelStrong = c(0xFFE0E0E0), label = c(0xFF8A8A8A), labelFaint = c(0xFF5A5A5A),
                    moria = c(0xFFA0A0A0), tuner = c(0xFFFFFFFF), tick = c(0xFFBBBBBB), hold = c(0xFFAAAAAA),
                    settingsBg = c(0xFF000000), settingsHeader = c(0xFFD6D6D6), settingsText = c(0xFFEEEEEE),
                    settingsMuted = c(0xFF9A9A9A),
                    defaultPitch = c(0xFFFFFF00), defaultBeats = c(0xFF444444), defaultMetronome = c(0xFF000080),
                    defaultScale = DARK_SCALE, defaultChromatic = DARK_CHROMATIC, accent = accent,
                )
                LIGHT -> Palette(
                    dark = false, graphBg = c(0xFFFFFFFF), bar = c(0xFFF3F1EC), chipFill = c(0xFFFFFFFF),
                    chipStroke = c(0xFFD6D1C6), chipPressed = c(0xFFE9E4D8), infoFill = c(0xFFE9E5DC),
                    text = c(0xFF222222), infoText = c(0xFF333333), axis = c(0xFF555555),
                    labelStrong = c(0xFF111111), label = c(0xFF5E5E5E), labelFaint = c(0xFF9A9A9A),
                    moria = c(0xFF707070), tuner = c(0xFF111111), tick = c(0xFF555555), hold = c(0xFF555555),
                    settingsBg = c(0xFFFAFAFA), settingsHeader = c(0xFF333333), settingsText = c(0xFF1E1E1E),
                    settingsMuted = c(0xFF6B6B6B),
                    defaultPitch = c(0xFFD84315), defaultBeats = c(0xFFBDBDBD), defaultMetronome = c(0xFF90CAF9),
                    defaultScale = LIGHT_SCALE, defaultChromatic = LIGHT_CHROMATIC, accent = accent,
                )
                PARCHMENT -> Palette(
                    dark = false, graphBg = c(0xFFFBF6EA), bar = c(0xFFEFE4CC), chipFill = c(0xFFFBF6EA),
                    chipStroke = c(0xFFD8C7A2), chipPressed = c(0xFFE6D8B8), infoFill = c(0xFFE7DABD),
                    text = c(0xFF3B2F1E), infoText = c(0xFF3B2F1E), axis = c(0xFF6B5A40),
                    labelStrong = c(0xFF2B1F0E), label = c(0xFF6E5E44), labelFaint = c(0xFFA8977A),
                    moria = c(0xFF7E6E52), tuner = c(0xFF2B1F0E), tick = c(0xFF6B5A40), hold = c(0xFF6B5A40),
                    settingsBg = c(0xFFF8F1E1), settingsHeader = c(0xFF4A3B26), settingsText = c(0xFF2B1F0E),
                    settingsMuted = c(0xFF7A6A50),
                    defaultPitch = c(0xFFB71C1C), defaultBeats = c(0xFFCDBE9C), defaultMetronome = c(0xFFE8C77A),
                    defaultScale = PARCHMENT_SCALE, defaultChromatic = PARCHMENT_CHROMATIC, accent = accent,
                )
                else -> Palette(
                    dark = true, graphBg = c(0xFF000000), bar = c(0xFF1C1713), chipFill = c(0xFF241D17),
                    chipStroke = c(0xFF3A3026), chipPressed = c(0xFF3A2F24), infoFill = c(0xFF15110D),
                    text = c(0xFFEEEEEE), infoText = c(0xFFD8CFC0), axis = c(0xFFC8C8C8),
                    labelStrong = c(0xFFD0D0D0), label = c(0xFF8A8A8A), labelFaint = c(0xFF606060),
                    moria = c(0xFFB0B0B0), tuner = c(0xFFFFFFFF), tick = c(0xFFCCCCCC), hold = c(0xFFAAAAAA),
                    settingsBg = c(0xFF303030), settingsHeader = c(0xFFD6D6D6), settingsText = c(0xFFEEEEEE),
                    settingsMuted = c(0xFFB8B8B8),
                    defaultPitch = c(0xFFFFFF00), defaultBeats = c(0xFF444444), defaultMetronome = c(0xFF000080),
                    defaultScale = DARK_SCALE, defaultChromatic = DARK_CHROMATIC, accent = accent,
                )
            }
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
    protected lateinit var palette: Palette
    private var styleKey = ""

    /** True for the main screen (no action bar). */
    protected open val fullScreen = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        palette = prefs.palette()
        setTheme(when {
            fullScreen && palette.dark -> R.style.Theme_Byz_Main
            fullScreen -> R.style.Theme_Byz_Main_Light
            palette.dark -> R.style.Theme_Byz_Settings
            else -> R.style.Theme_Byz_Settings_Light
        })
        styleKey = currentStyleKey()
        super.onCreate(savedInstanceState)
        window.statusBarColor = if (fullScreen) palette.bar else if (palette.dark) Color.BLACK else palette.bar
        window.navigationBarColor = if (fullScreen) palette.bar else palette.settingsBg
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
        }
    }

    override fun onResume() {
        super.onResume()
        // Language or theme changed in Settings: rebuild this screen.
        if (currentStyleKey() != styleKey) recreate()
    }

    private fun currentStyleKey() = Lang.of(this) + "|" + prefs.theme + "|" +
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)

    /** Tints checkboxes, radio buttons, sliders and lists with the app colour. */
    protected fun tintAll(v: View) {
        val c = palette.accentText
        val off = if (palette.dark) 0xFF9A9A9A.toInt() else 0xFF7A7A7A.toInt()
        val checkable = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(c, off))
        val solid = ColorStateList.valueOf(c)
        when (v) {
            is android.widget.CompoundButton -> v.buttonTintList = checkable
            is android.widget.SeekBar -> { v.thumbTintList = solid; v.progressTintList = solid }
            is android.widget.Spinner -> v.backgroundTintList = solid
            is android.widget.ProgressBar -> v.progressTintList = solid
        }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) tintAll(v.getChildAt(i))
    }
}
