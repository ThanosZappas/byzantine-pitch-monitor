package app.byzantine.pitchmonitor.data

import android.content.Context
import androidx.core.content.edit
import app.byzantine.pitchmonitor.music.Echos
import app.byzantine.pitchmonitor.music.Genus
import app.byzantine.pitchmonitor.music.ByzantineGrid
import app.byzantine.pitchmonitor.music.Pitch
import app.byzantine.pitchmonitor.music.ScaleGrid
import app.byzantine.pitchmonitor.music.WesternGrid

/** Everything the user can configure. Persisted in SharedPreferences. */
data class Settings(
    /** Selected mode, or null for the western (12-tone) grid. */
    val echos: Echos? = Echos.FIRST,
    /** Genus override for the selected mode; null uses the mode's own genus. */
    val genusOverride: Genus? = null,
    /** Frequency of the reference Ni (κατά βάσιν). Default C4. */
    val niHz: Double = 261.63,
    /** A4 calibration used for western note names. */
    val a4Hz: Double = 440.0,
    val thresholdDb: Float = -50f,
    val smoothing: Float = 0.45f,
    /** Seconds of history visible across the graph. */
    val windowSeconds: Float = 6f,
    /** Pitches within this many moria of a note count as "on" the note. */
    val toleranceMoria: Float = 2f,
    val showWestern: Boolean = false,
    val showHz: Boolean = true,
    val latinNames: Boolean = false,
    val keepScreenOn: Boolean = true,
    val isonVolume: Float = 0.5f,
) {
    val genus: Genus? get() = echos?.let { genusOverride ?: it.genus }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        val echosName = prefs.getString(K_ECHOS, d.echos?.name)
        return Settings(
            echos = if (echosName == WESTERN) null else Echos.entries.firstOrNull { it.name == echosName } ?: d.echos,
            genusOverride = prefs.getString(K_GENUS, null)?.let { n -> Genus.entries.firstOrNull { it.name == n } },
            niHz = prefs.getFloat(K_NI, d.niHz.toFloat()).toDouble(),
            a4Hz = prefs.getFloat(K_A4, d.a4Hz.toFloat()).toDouble(),
            thresholdDb = prefs.getFloat(K_THRESHOLD, d.thresholdDb),
            smoothing = prefs.getFloat(K_SMOOTHING, d.smoothing),
            windowSeconds = prefs.getFloat(K_WINDOW, d.windowSeconds),
            toleranceMoria = prefs.getFloat(K_TOLERANCE, d.toleranceMoria),
            showWestern = prefs.getBoolean(K_WESTERN, d.showWestern),
            showHz = prefs.getBoolean(K_HZ, d.showHz),
            latinNames = prefs.getBoolean(K_LATIN, d.latinNames),
            keepScreenOn = prefs.getBoolean(K_SCREEN, d.keepScreenOn),
            isonVolume = prefs.getFloat(K_ISON_VOL, d.isonVolume),
        )
    }

    fun save(s: Settings) = prefs.edit {
        putString(K_ECHOS, s.echos?.name ?: WESTERN)
        if (s.genusOverride == null) remove(K_GENUS) else putString(K_GENUS, s.genusOverride.name)
        putFloat(K_NI, s.niHz.toFloat())
        putFloat(K_A4, s.a4Hz.toFloat())
        putFloat(K_THRESHOLD, s.thresholdDb)
        putFloat(K_SMOOTHING, s.smoothing)
        putFloat(K_WINDOW, s.windowSeconds)
        putFloat(K_TOLERANCE, s.toleranceMoria)
        putBoolean(K_WESTERN, s.showWestern)
        putBoolean(K_HZ, s.showHz)
        putBoolean(K_LATIN, s.latinNames)
        putBoolean(K_SCREEN, s.keepScreenOn)
        putFloat(K_ISON_VOL, s.isonVolume)
    }

    private companion object {
        const val WESTERN = "WESTERN"
        const val K_ECHOS = "echos"
        const val K_GENUS = "genus"
        const val K_NI = "niHz"
        const val K_A4 = "a4Hz"
        const val K_THRESHOLD = "thresholdDb"
        const val K_SMOOTHING = "smoothing"
        const val K_WINDOW = "windowSeconds"
        const val K_TOLERANCE = "toleranceMoria"
        const val K_WESTERN = "showWestern"
        const val K_HZ = "showHz"
        const val K_LATIN = "latinNames"
        const val K_SCREEN = "keepScreenOn"
        const val K_ISON_VOL = "isonVolume"
    }
}

/** The pitch grid described by these settings. */
fun Settings.buildGrid(): ScaleGrid {
    val e = echos
    val refCents = Pitch.hzToCents(a4Hz)
    return if (e == null) {
        WesternGrid(refCents)
    } else {
        ByzantineGrid(
            genus = genus!!,
            niCents = Pitch.hzToCents(niHz),
            base = e.base,
            dominants = e.dominants,
            latinNames = latinNames,
        )
    }
}
