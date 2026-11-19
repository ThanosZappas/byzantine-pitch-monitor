@file:OptIn(ExperimentalMaterial3Api::class)

package app.byzantine.pitchmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.byzantine.pitchmonitor.data.Settings
import app.byzantine.pitchmonitor.music.ByzantineGrid
import app.byzantine.pitchmonitor.music.Degree
import app.byzantine.pitchmonitor.music.Echos
import app.byzantine.pitchmonitor.music.Genus
import app.byzantine.pitchmonitor.music.Pitch
import java.util.Locale

@Composable
fun MainScreen(vm: MainViewModel, openSettings: Boolean = false) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val grid by vm.grid.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val isonTarget by vm.isonTarget.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(openSettings) }
    val snackbar = remember { SnackbarHostState() }
    val westernRef = Pitch.hzToCents(settings.a4Hz)

    LaunchedEffect(message) {
        message?.let {
            vm.consumeMessage()
            snackbar.showSnackbar(it)
        }
    }

    val viewport = remember {
        // Start centred a little above Ni so the common singing range is in view.
        GraphViewport(Pitch.hzToCents(settings.niHz) + 500, GraphViewport.DEFAULT_SPAN)
    }

    val isonCents = isonTarget?.let { t ->
        when (val g = grid) {
            is ByzantineGrid -> g.centsOf(Degree.entries[t.index], t.octave)
            else -> ((t.octave + 1) * 12 + t.index - 69) * 100.0 + westernRef
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ModeSelector(settings, vm::selectEchos, vm::selectGenus) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TunerPanel(
                engine = vm.engine,
                grid = grid,
                toleranceMoria = settings.toleranceMoria,
                westernRefCents = westernRef,
                showHz = settings.showHz,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            )
            HorizontalDivider(color = Palette.GoldDim.copy(alpha = 0.5f))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PitchGraph(
                    history = vm.history,
                    grid = grid,
                    style = GraphStyle(
                        windowSeconds = settings.windowSeconds,
                        toleranceMoria = settings.toleranceMoria,
                        showWestern = settings.showWestern,
                        westernRefCents = westernRef,
                        isonCents = isonCents,
                    ),
                    paused = paused,
                    viewport = viewport,
                    onNoteTapped = vm::setIsonNote,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!viewport.autoFollow) {
                    SmallFloatingActionButton(
                        onClick = { viewport.recenter(vm.engine.lastVoicedCents.takeIf { !it.isNaN() }) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(AppIcons.CenterFocus, contentDescription = "Follow the voice again")
                    }
                }
                if (paused) {
                    Text(
                        "Paused · drag sideways to look back",
                        color = Palette.Ink,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(Palette.Gold, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            HorizontalDivider(color = Palette.GoldDim.copy(alpha = 0.5f))
            ControlBar(
                settings = settings,
                paused = paused,
                isonOn = isonTarget != null,
                onTranspose = vm::transposeMoria,
                onBaseFromVoice = vm::setBaseFromVoice,
                onToggleIson = vm::toggleIson,
                onTogglePause = vm::togglePause,
                onClear = vm::clearHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            )
        }
    }

    if (showSettings) {
        SettingsSheet(settings = settings, onChange = vm::update, onResetNi = vm::resetNi, onDismiss = { showSettings = false })
    }
}

@Composable
private fun ModeSelector(settings: Settings, onEchos: (Echos?) -> Unit, onGenus: (Genus?) -> Unit) {
    var modeMenu by remember { mutableStateOf(false) }
    var genusMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { modeMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(
                    settings.echos?.greek ?: "Western 12-TET",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Gold,
                    maxLines = 1,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose mode", tint = Palette.Gold)
            }
            DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                for (e in Echos.entries) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(e.greek, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${e.genus.greek} · βάση ${e.base.greek}",
                                    fontSize = 12.sp,
                                    color = Palette.Muted,
                                )
                            }
                        },
                        onClick = { onEchos(e); modeMenu = false },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Western 12-TET") }, onClick = { onEchos(null); modeMenu = false })
            }
        }
        val echos = settings.echos
        if (echos != null) {
            Box {
                TextButton(onClick = { genusMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(
                        settings.genus!!.greek,
                        fontSize = 13.sp,
                        color = if (settings.genusOverride != null) Palette.Rose else Palette.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(expanded = genusMenu, onDismissRequest = { genusMenu = false }) {
                    for (g in Genus.entries) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    g.greek + if (g == echos.genus) "  (default)" else "",
                                    fontWeight = if (g == settings.genus) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = { onGenus(g); genusMenu = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlBar(
    settings: Settings,
    paused: Boolean,
    isonOn: Boolean,
    onTranspose: (Int) -> Unit,
    onBaseFromVoice: () -> Unit,
    onToggleIson: () -> Unit,
    onTogglePause: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (settings.echos != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val ni = if (settings.latinNames) "Ni" else "Νη"
                    Text(
                        "$ni = ${Pitch.nearestNoteName(Pitch.hzToCents(settings.niHz), Pitch.hzToCents(settings.a4Hz))}",
                        color = Palette.Parchment, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    )
                    Text(
                        String.format(Locale.US, "%.2f Hz · %s", settings.niHz, Pitch.describe(Pitch.hzToCents(settings.niHz), Pitch.hzToCents(settings.a4Hz))),
                        color = Palette.Muted, fontSize = 11.sp,
                    )
                }
                StepButton("−½", "Down a semitone") { onTranspose(-6) }
                StepButton("−μ", "Down one moria") { onTranspose(-1) }
                StepButton("+μ", "Up one moria") { onTranspose(1) }
                StepButton("+½", "Up a semitone") { onTranspose(6) }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (settings.echos != null) {
                FilledTonalButton(onClick = onBaseFromVoice, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(AppIcons.Target, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Base = voice", maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = onToggleIson,
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isonOn) Palette.Gold else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (isonOn) Palette.Ink else Palette.Gold,
                ),
            ) {
                Icon(if (isonOn) AppIcons.Volume else AppIcons.VolumeOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ison", maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Refresh, contentDescription = "Clear graph", tint = Palette.Muted)
            }
            FilledTonalIconButton(onClick = onTogglePause) {
                Icon(if (paused) Icons.Default.PlayArrow else AppIcons.Pause, contentDescription = if (paused) "Resume" else "Pause")
            }
        }
    }
}

@Composable
private fun StepButton(label: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = Modifier.width(46.dp).semantics { contentDescription = description },
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Gold)
    }
}
