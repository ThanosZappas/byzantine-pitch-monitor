package app.byzantine.pitchmonitor.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import app.byzantine.pitchmonitor.audio.AudioEngine
import app.byzantine.pitchmonitor.audio.IsonPlayer
import app.byzantine.pitchmonitor.audio.PitchHistory
import app.byzantine.pitchmonitor.data.Settings
import app.byzantine.pitchmonitor.data.SettingsStore
import app.byzantine.pitchmonitor.data.buildGrid
import app.byzantine.pitchmonitor.music.ByzantineGrid
import app.byzantine.pitchmonitor.music.Degree
import app.byzantine.pitchmonitor.music.Echos
import app.byzantine.pitchmonitor.music.Genus
import app.byzantine.pitchmonitor.music.GridNote
import app.byzantine.pitchmonitor.music.Moria
import app.byzantine.pitchmonitor.music.Pitch
import app.byzantine.pitchmonitor.music.ScaleGrid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which grid note the ison sits on, kept by degree/octave so it follows mode and Ni changes. */
data class IsonTarget(val index: Int, val octave: Int)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    val history = PitchHistory()
    val engine = AudioEngine(app, history)
    private val ison = IsonPlayer()

    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _grid = MutableStateFlow(_settings.value.buildGrid())
    val grid: StateFlow<ScaleGrid> = _grid.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _ison = MutableStateFlow<IsonTarget?>(null)
    val isonTarget: StateFlow<IsonTarget?> = _ison.asStateFlow()

    /** One-shot user-facing messages (shown in a snackbar). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        applyToEngine(_settings.value)
    }

    fun consumeMessage() { _message.value = null }

    // --- lifecycle -------------------------------------------------------------------------

    fun startListening() {
        engine.start()
    }

    fun stopListening() {
        engine.stop()
        ison.stop()
        _ison.value = null
    }

    override fun onCleared() {
        engine.stop()
        ison.release()
    }

    // --- settings --------------------------------------------------------------------------

    fun update(transform: (Settings) -> Settings) {
        _settings.update(transform)
        val s = _settings.value
        store.save(s)
        _grid.value = s.buildGrid()
        applyToEngine(s)
        refreshIson()
    }

    private fun applyToEngine(s: Settings) {
        engine.thresholdDb = s.thresholdDb.toDouble()
        engine.smoother.smoothing = s.smoothing.toDouble()
        ison.volume = s.isonVolume.toDouble()
    }

    fun selectEchos(echos: Echos?) {
        update { it.copy(echos = echos, genusOverride = null) }
        // A new mode means a new base: move a sounding ison to it.
        if (_ison.value != null) {
            _ison.value = defaultIsonTarget()
            refreshIson()
        }
    }

    fun selectGenus(genus: Genus?) = update { s ->
        s.copy(genusOverride = genus?.takeIf { it != s.echos?.genus })
    }

    /** Transposes the reference Ni by [moria] (6 moria = one semitone). */
    fun transposeMoria(moria: Int) = update {
        it.copy(niHz = it.niHz * Math.pow(2.0, moria / Moria.PER_OCTAVE.toDouble()))
    }

    fun resetNi() = update { it.copy(niHz = Settings().niHz) }

    /**
     * Retunes the grid so the note being sung right now becomes the mode's base.
     * Keeps Ni within half an octave of where it was, so octave marks stay familiar.
     */
    fun setBaseFromVoice() {
        val s = _settings.value
        val echos = s.echos ?: return
        val sung = recentPitch()
        if (sung == null) {
            _message.value = "Sing or play the base note first, then tap again"
            return
        }
        val genus = s.genus ?: return
        var niCents = sung - Moria.toCents(genus.positions[echos.base.ordinal].toDouble())
        val oldNi = Pitch.hzToCents(s.niHz)
        while (niCents - oldNi > 600) niCents -= 1200
        while (oldNi - niCents > 600) niCents += 1200
        update { it.copy(niHz = Pitch.centsToHz(niCents)) }
        val base = echos.base.label(s.latinNames)
        _message.value = "$base set to ${Pitch.describe(sung, Pitch.hzToCents(s.a4Hz))}"
    }

    // --- pause -----------------------------------------------------------------------------

    fun togglePause() {
        val p = !_paused.value
        _paused.value = p
        engine.paused = p
    }

    fun clearHistory() {
        history.clear()
    }

    // --- ison ------------------------------------------------------------------------------

    fun toggleIson() {
        if (_ison.value != null) {
            _ison.value = null
            ison.stop()
        } else {
            _ison.value = defaultIsonTarget()
            refreshIson()
        }
    }

    fun setIsonNote(note: GridNote) {
        _ison.value = IsonTarget(note.index, note.octave)
        refreshIson()
        _message.value = "Ison on ${note.label}"
    }

    private fun refreshIson() {
        val t = _ison.value
        if (t == null) {
            ison.stop()
            return
        }
        val cents = centsOf(t) ?: return
        ison.play(Pitch.centsToHz(cents))
    }

    private fun centsOf(t: IsonTarget): Double? = when (val g = _grid.value) {
        is ByzantineGrid -> g.centsOf(Degree.entries[t.index], t.octave)
        else -> {
            val ref = Pitch.hzToCents(_settings.value.a4Hz)
            ((t.octave + 1) * 12 + t.index - 69) * 100.0 + ref
        }
    }

    /** The base note in the octave nearest the singer's recent pitch (octave 0 if silent). */
    private fun defaultIsonTarget(): IsonTarget {
        val s = _settings.value
        val grid = _grid.value
        val baseIndex = s.echos?.base?.ordinal ?: 0
        val around = recentPitch() ?: engine.lastVoicedCents.takeIf { !it.isNaN() }
        if (around != null) {
            val candidates = grid.notesIn(around - 1300, around + 1300).filter { it.index == baseIndex }
            // Prefer the base at or just below the voice: the ison sits under the melody.
            val below = candidates.filter { it.cents <= around + 100 }.maxByOrNull { it.cents }
            val pick = below ?: candidates.minByOrNull { abs(it.cents - around) }
            if (pick != null) return IsonTarget(pick.index, pick.octave)
        }
        val octave = if (grid is ByzantineGrid) 0 else 3
        return IsonTarget(baseIndex, octave)
    }

    private fun recentPitch(): Double? {
        val c = engine.lastVoicedCents
        val age = SystemClock.uptimeMillis() - engine.lastVoicedAtMs
        return if (!c.isNaN() && age < 1500) c else null
    }

    /** Readable deviation like "+1.4" moria. */
    fun formatMoria(m: Double): String {
        val r = (m * 10).roundToInt() / 10.0
        return (if (r > 0) "+" else if (r < 0) "−" else "±") + abs(r)
    }
}
