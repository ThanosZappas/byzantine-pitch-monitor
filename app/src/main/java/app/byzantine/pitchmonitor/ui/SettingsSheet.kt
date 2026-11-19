@file:OptIn(ExperimentalMaterial3Api::class)

package app.byzantine.pitchmonitor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.byzantine.pitchmonitor.data.Settings
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsSheet(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onResetNi: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Wood,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Gold)
            Spacer(Modifier.height(8.dp))

            Section("Detection")
            SliderRow(
                "Volume threshold", "${settings.thresholdDb.roundToInt()} dB",
                "Quieter sounds are ignored. Raise it in noisy rooms.",
                settings.thresholdDb, -75f..-20f,
            ) { v -> onChange { it.copy(thresholdDb = v) } }
            SliderRow(
                "Smoothing", "${(settings.smoothing * 100).roundToInt()}%",
                "Steadier line vs. faster response to ornaments.",
                settings.smoothing, 0f..0.9f,
            ) { v -> onChange { it.copy(smoothing = v) } }
            SliderRow(
                "Tolerance", String.format(Locale.US, "±%.1f μόρια", settings.toleranceMoria),
                "How close counts as on the note (green).",
                settings.toleranceMoria, 0.5f..6f,
            ) { v -> onChange { it.copy(toleranceMoria = (v * 2).roundToInt() / 2f) } }

            Section("Display")
            SliderRow(
                "Time window", "${settings.windowSeconds.roundToInt()} s",
                "Seconds of singing shown across the graph.",
                settings.windowSeconds, 2f..30f,
            ) { v -> onChange { it.copy(windowSeconds = v.roundToInt().toFloat()) } }
            SwitchRow("Latin note names", "Ni, Pa, Vou… instead of Νη, Πα, Βου…", settings.latinNames) { v ->
                onChange { it.copy(latinNames = v) }
            }
            SwitchRow("Western note lines", "Faint C, D, E… lines behind the Byzantine scale", settings.showWestern) { v ->
                onChange { it.copy(showWestern = v) }
            }
            SwitchRow("Show frequency", "Hz next to the note", settings.showHz) { v -> onChange { it.copy(showHz = v) } }
            SwitchRow("Keep screen on", "While the app is open", settings.keepScreenOn) { v ->
                onChange { it.copy(keepScreenOn = v) }
            }

            Section("Tuning")
            SliderRow(
                "A4 calibration", String.format(Locale.US, "%.1f Hz", settings.a4Hz),
                "Only changes the western note names.",
                settings.a4Hz.toFloat(), 415f..466f,
            ) { v -> onChange { it.copy(a4Hz = (v * 2).roundToInt() / 2.0) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format(Locale.US, "Νη = %.2f Hz", settings.niHz),
                    color = Palette.Parchment, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onResetNi) { Text("Reset to C4") }
            }

            Section("Ison")
            SliderRow(
                "Ison volume", "${(settings.isonVolume * 100).roundToInt()}%",
                "Use headphones: the ison is also heard by the microphone.",
                settings.isonVolume, 0.05f..1f,
            ) { v -> onChange { it.copy(isonVolume = v) } }

            Section("How to use")
            Help(
                "• Pick the mode (ήχος) at the top; the genus next to it can be changed for variant scales.\n" +
                    "• Gold line = base (βάση), red lines = dominant degrees. Small numbers between lines are the intervals in μόρια (72 per octave).\n" +
                    "• The trace is green when you are within the tolerance of a degree, amber when close, red when off.\n" +
                    "• Sing the base note and tap “Base = voice” to tune the whole scale to your voice, or move Νη by moria / semitones.\n" +
                    "• Tap a note name on the left to put the ison there. “Ison” toggles it on the base.\n" +
                    "• Drag up/down to move, pinch to zoom, double-tap to reset. Pause, then drag sideways to look back.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(14.dp))
    Text(title.uppercase(), color = Palette.GoldDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    HorizontalDivider(color = Palette.GridLine, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SliderRow(
    title: String,
    value: String,
    help: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(title, color = Palette.Parchment, modifier = Modifier.weight(1f))
            Text(value, color = Palette.Gold, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = current.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Palette.Gold, activeTrackColor = Palette.Gold),
        )
        Help(help)
    }
}

@Composable
private fun SwitchRow(title: String, help: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Parchment)
            Help(help)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Palette.Ink, checkedTrackColor = Palette.Gold),
        )
    }
}

@Composable
private fun Help(text: String) {
    Text(text, color = Palette.Muted, fontSize = 12.sp, lineHeight = 17.sp)
}
